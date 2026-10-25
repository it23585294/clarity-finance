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
    
    private val recordsKey = stringPreferencesKey("submission_records")
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<SubmissionRecord>>() {}.type

    fun getRecordsFlow(uid: String, type: String): Flow<List<SubmissionRecord>> {
        return dataStore.data.map { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val allRecords: List<SubmissionRecord> = try {
                gson.fromJson(json, typeToken) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            allRecords.filter { it.uid == uid && it.resolvedType == type }
        }
    }

    suspend fun getRecords(uid: String, type: String): List<SubmissionRecord> {
        val preferences = dataStore.data.first()
        val json = preferences[recordsKey] ?: "[]"
        val allRecords: List<SubmissionRecord> = try {
            gson.fromJson(json, typeToken) ?: emptyList()
        } catch (e: Exception) {
            throw IllegalStateException("Saved operations could not be read. Keep app data and retry recovery.", e)
        }
        return allRecords.filter { it.uid == uid && it.resolvedType == type }
    }

    suspend fun saveRecord(record: SubmissionRecord) {
        dataStore.edit { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val current: MutableList<SubmissionRecord> = try {
                gson.fromJson(json, typeToken) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            
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
            val current: MutableList<SubmissionRecord> = gson.fromJson(json, typeToken) ?: mutableListOf()
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

    suspend fun removeRecord(uid: String, recordId: String, type: String) {
        dataStore.edit { preferences ->
            val json = preferences[recordsKey] ?: "[]"
            val current: MutableList<SubmissionRecord> = try {
                gson.fromJson(json, typeToken) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            if (current.removeAll { it.uid == uid && it.recordId == recordId && it.resolvedType == type }) {
                preferences[recordsKey] = gson.toJson(current)
            }
        }
    }
}
