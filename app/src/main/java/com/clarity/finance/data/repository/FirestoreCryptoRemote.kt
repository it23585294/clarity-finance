package com.clarity.finance.data.repository

import com.clarity.finance.data.model.CryptoReporting
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class CryptoRemoteSnapshot(val results: List<CryptoResult>, val fromCache: Boolean)

interface CryptoRemote {
    fun observe(uid: String, year: Int, cutoff: String): Flow<CryptoRemoteSnapshot>
    suspend fun apply(uid: String, operation: CryptoOperation)
    suspend fun resolve(uid: String, operation: CryptoOperation) { error("Online resolution unavailable.") }
}

/** No access to income, expenses, project payments or goal collections. */
class FirestoreCryptoRemote(
    private val firestore: FirebaseFirestore,
    private val activeUid: () -> String?,
    private val financial: FinancialRemote
) : CryptoRemote {
    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("cryptoResults")

    override fun observe(uid: String, year: Int, cutoff: String): Flow<CryptoRemoteSnapshot> = callbackFlow {
        val listener = collection(uid).whereGreaterThanOrEqualTo("resultDate", "%04d-01-01".format(java.util.Locale.ROOT, year))
            .whereLessThanOrEqualTo("resultDate", cutoff).orderBy("resultDate")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) {
                    try {
                        trySend(CryptoRemoteSnapshot(snapshot.documents.map { it.crypto() }, snapshot.metadata.isFromCache))
                    } catch (e: Exception) { close(e) }
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun apply(uid: String, operation: CryptoOperation) {
        check(activeUid() == uid) { "Account changed. Saved operation belongs to the original account." }
        financial.apply(uid, FinancialOperation(operation.operationId, "CRYPTO", operation.kind, operation.result.id,
            expectedUpdatedAt = operation.expectedUpdatedAt,
            crypto = operation.result.takeUnless { operation.kind == "DELETE" },
            legacyPayload = if (operation.protocolVersion < 2) com.google.gson.Gson().toJson(operation) else null))
    }

    override suspend fun resolve(uid: String, operation: CryptoOperation) {
        check(activeUid() == uid) { "Account changed. Saved proposal retained." }
        financial.fence(uid, FinancialOperation(operation.operationId, "CRYPTO", operation.kind, operation.result.id,
            expectedUpdatedAt = operation.expectedUpdatedAt, crypto = operation.result.takeUnless { operation.kind == "DELETE" },
            legacyPayload = if (operation.protocolVersion < 2) com.google.gson.Gson().toJson(operation) else null))
    }

    private fun DocumentSnapshot.crypto(): CryptoResult {
        val pending = metadata.hasPendingWrites()
        val created = getTimestamp("createdAt")
        val updated = getTimestamp("updatedAt")
        return CryptoResult(id, getString("direction") ?: error("Missing direction"),
            getLong("amountMinor") ?: error("Missing amount"), getString("resultDate") ?: error("Missing result date"),
            getString("note") ?: "", created, updated, getString("lastOperationId") ?: "",
            when {
                pending || created == null || updated == null -> CryptoSync.PENDING
                metadata.isFromCache -> CryptoSync.CACHED
                else -> CryptoSync.CONFIRMED
            }).also { CryptoReporting.validate(it.direction, it.amountMinor, it.resultDate, it.note, CryptoReporting.today()) }
    }
}

internal fun CryptoResult.sameValues(other: CryptoResult): Boolean =
    direction == other.direction && amountMinor == other.amountMinor && resultDate == other.resultDate && note == other.note

/** A committed operation may have outlived a timed-out/cancelled await. Never write again on a matching replay. */
internal fun CryptoOperation.requiresWrite(existing: CryptoResult?): Boolean {
    require(kind in setOf("CREATE", "UPDATE", "DELETE"))
    if (kind == "DELETE" && existing == null) return false
    if (existing?.lastOperationId == operationId) {
        check(kind != "DELETE" && existing.sameValues(result)) { "Operation identity conflict." }
        return false
    }
    when (kind) {
        "CREATE" -> check(existing == null) { "A different result already uses this ID." }
        "UPDATE", "DELETE" -> check(existing != null && expectedUpdatedAt != null && existing.updatedAt == expectedUpdatedAt) {
            "Result changed elsewhere. Reopen it before editing or deleting."
        }
    }
    return true
}
