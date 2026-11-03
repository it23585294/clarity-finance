package com.clarity.finance.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "expense_recovery") // Kept for backwards compatibility

class SubmissionRecoveryStore(private val dataStore: DataStore<Preferences>) {
    private fun catalogueKey(uid: String): Preferences.Key<String> {
        require(uid.isNotBlank())
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return stringPreferencesKey("confirmed_catalogue_v1_$hash")
    }
    suspend fun confirmedCatalogue(uid: String): List<com.clarity.finance.data.model.CatalogueEntry>? {
        val json = dataStore.data.first()[catalogueKey(uid)] ?: return null
        val type = object : TypeToken<List<com.clarity.finance.data.model.CatalogueEntry>>() {}.type
        val rows: List<com.clarity.finance.data.model.CatalogueEntry> = gson.fromJson(json, type)
            ?: error("Confirmed catalogue cache is corrupt; reconnect to load it.")
        com.clarity.finance.data.model.CatalogueDefaults.validateComplete(rows)
        return rows
    }
    suspend fun saveConfirmedCatalogue(uid: String, rows: List<com.clarity.finance.data.model.CatalogueEntry>) {
        com.clarity.finance.data.model.CatalogueDefaults.validateComplete(rows)
        dataStore.edit { it[catalogueKey(uid)] = gson.toJson(rows) } // Never touches submission_records.
    }
    
    private val recordsKey = stringPreferencesKey("submission_records")
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<SubmissionRecord>>() {}.type

    fun getRecordsFlow(uid: String, type: String): Flow<List<SubmissionRecord>> {
        return dataStore.data.map { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val allRecords = decode(json)
            allRecords.filter { it.uid == uid && it.resolvedType == type }
        }
    }

    suspend fun getRecords(uid: String, type: String): List<SubmissionRecord> {
        val preferences = dataStore.data.first()
        val json = preferences[recordsKey] ?: "[]"
        val allRecords = decode(json)
        return allRecords.filter { it.uid == uid && it.resolvedType == type }
    }

    suspend fun saveRecord(record: SubmissionRecord) {
        dataStore.edit { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val current = decode(json)
            
            // Remove existing record for this uid, recordId, and type if it exists
            current.removeAll { it.uid == record.uid && it.recordId == record.recordId && it.resolvedType == record.resolvedType }
            current.add(record)
            
            preferences[recordsKey] = gson.toJson(current)
        }
    }

    /** Persist an operation before sending it; an existing retry payload is immutable. */
    suspend fun saveOperation(record: SubmissionRecord) {
        dataStore.edit { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val current = decode(json)
            val existing = current.firstOrNull {
                it.uid == record.uid && it.recordId == record.recordId && it.resolvedType == record.resolvedType
            }
            if (existing != null) {
                check(existing.payloadJson == record.payloadJson) { "Operation conflict: retry must use the original saved details." }
            } else {
                current.add(record)
                preferences[recordsKey] = gson.toJson(current)
            }
        }
    }

    /** Strict reads/status changes for durable operations; legacy record handling is unchanged. */
    fun getOperationRecordsFlow(uid: String, types: Set<String>): Flow<List<SubmissionRecord>> =
        dataStore.data.map { preferences ->
            val records = decode(preferences[recordsKey] ?: "[]")
            records.filter { it.uid == uid && it.resolvedType in types }
        }

    suspend fun updateOperationStatus(record: SubmissionRecord) {
        dataStore.edit { preferences ->
            val current = decode(preferences[recordsKey] ?: "[]")
            val index = current.indexOfFirst {
                it.uid == record.uid && it.recordId == record.recordId && it.resolvedType == record.resolvedType
            }
            check(index >= 0 && current[index].payloadJson == record.payloadJson) { "Saved operation changed. Reopen recovery." }
            current[index] = record
            preferences[recordsKey] = gson.toJson(current)
        }
    }

    /** Atomically replace a server-fenced expired proposal; ordinary retry payloads remain immutable. */
    suspend fun replaceExpiredOperation(expected: SubmissionRecord, replacement: SubmissionRecord) {
        require(expected.status == "EXPIRED" && replacement.status == "PENDING")
        require(expected.resolvedType in setOf("GOAL_CREATE", "GOAL_EDIT"))
        require(expected.uid == replacement.uid && expected.recordId == replacement.recordId && expected.resolvedType == replacement.resolvedType)
        require(expected.payloadJson != replacement.payloadJson)
        dataStore.edit { preferences ->
            val current = decode(preferences[recordsKey] ?: "[]")
            val index = current.indexOfFirst { it.uid == expected.uid && it.recordId == expected.recordId && it.resolvedType == expected.resolvedType }
            check(index >= 0) { "Expired proposal no longer exists." }
            if (current[index].payloadJson != replacement.payloadJson) {
                check(current[index].status == "EXPIRED" && current[index].payloadJson == expected.payloadJson) { "Expired proposal changed; reopen recovery." }
                current[index] = replacement
                preferences[recordsKey] = gson.toJson(current)
            } // Idempotent if persistence succeeded before its await timed out.
        }
    }

    suspend fun removeRecord(uid: String, recordId: String, type: String) {
        dataStore.edit { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val current = decode(json)
            
            if (current.removeAll { it.uid == uid && it.recordId == recordId && it.resolvedType == type }) {
                preferences[recordsKey] = gson.toJson(current)
            }
        }
    }
    /** All legacy and modern queues, scoped before a worker ever sees a record. */
    fun getAllRecordsFlow(uid: String): Flow<List<SubmissionRecord>> = dataStore.data.map {
        decode(it[recordsKey] ?: "[]").filter { record -> record.uid == uid }
    }

    suspend fun getAllRecords(uid: String): List<SubmissionRecord> =
        decode(dataStore.data.first()[recordsKey] ?: "[]").filter { it.uid == uid }

    /** Upgrade only an exact legacy envelope, atomically; keep its original payload inside the replacement. */
    suspend fun promoteLegacy(expected: SubmissionRecord, replacement: SubmissionRecord) {
        require(expected.uid == replacement.uid && !expected.resolvedType.startsWith("FINANCIAL_") && replacement.resolvedType.startsWith("FINANCIAL_"))
        dataStore.edit { preferences ->
            val current = decode(preferences[recordsKey] ?: "[]")
            val old = current.indexOfFirst { it.uid == expected.uid && it.recordId == expected.recordId && it.resolvedType == expected.resolvedType }
            val newer = current.firstOrNull { it.uid == replacement.uid && it.recordId == replacement.recordId && it.resolvedType == replacement.resolvedType }
            if (old < 0) { check(newer?.payloadJson == replacement.payloadJson); return@edit }
            check(current[old].payloadJson == expected.payloadJson)
            check(newer == null || newer.payloadJson == replacement.payloadJson)
            if (newer == null) current[old] = replacement else current.removeAt(old)
            preferences[recordsKey] = gson.toJson(current)
        }
    }

    private fun decode(json: String): MutableList<SubmissionRecord> = try {
        val records: MutableList<SubmissionRecord> = gson.fromJson(json, typeToken)
            ?: error("Recovery data is null, not a record list.")
        check(records.all { !it.uid.isNullOrBlank() && !it.recordId.isNullOrBlank() && it.payloadJson != null && !it.status.isNullOrBlank() }) {
            "Recovery data contains an incomplete identity or payload."
        }
        records
    } catch (e: Exception) {
        throw IllegalStateException("Saved operations could not be read. Keep app data and retry recovery; nothing was discarded.", e)
    }

}
