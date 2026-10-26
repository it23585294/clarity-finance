package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecord
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.CryptoReporting
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the existing durable store. Only the active screen/account runs its scoped synchronizer. */
class FirestoreCryptoRepository(
    private val remote: CryptoRemote,
    private val store: SubmissionRecoveryStore
) : CryptoRepository {
    private val gson = Gson()
    private val mutex = Mutex()
    private val types = setOf("CRYPTO_CREATE", "CRYPTO_UPDATE", "CRYPTO_DELETE")

    override suspend fun submit(uid: String, operation: CryptoOperation) = mutex.withLock {
        require(uid.isNotBlank())
        require(operation.kind in setOf("CREATE", "UPDATE", "DELETE"))
        CryptoReporting.validate(operation.result.direction, operation.result.amountMinor,
            operation.result.resultDate, operation.result.note, CryptoReporting.today())
        require(operation.result.id.isNotBlank() && operation.operationId.isNotBlank())
        val existing = types.flatMap { store.getRecords(uid, it) }.filter { it.recordId == operation.result.id }
        val record = record(uid, operation)
        check(existing.all { it.resolvedType == record.resolvedType && it.payloadJson == record.payloadJson }) {
            "Resolve the saved operation for this result before making another change."
        }
        store.saveOperation(record)
    }

    override suspend fun retry(uid: String, operation: CryptoOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.result.id }
            ?: error("Saved operation no longer exists. Refresh the list.")
        check(saved.payloadJson == record(uid, operation).payloadJson) { "Retry must use the original saved details." }
        store.updateOperationStatus(saved.copy(status = "PENDING", errorReason = null))
    }

    override suspend fun dismissRejected(uid: String, operation: CryptoOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.result.id }
            ?: error("Saved operation no longer exists.")
        check(saved.status == "FAILED" && saved.payloadJson == record(uid, operation).payloadJson) {
            "Only a definitively rejected operation can be dismissed. Pending outcomes must be reconciled."
        }
        store.removeRecord(uid, saved.recordId, saved.resolvedType)
    }

    override fun observe(uid: String, year: Int, cutoff: String): Flow<CryptoSnapshot> = channelFlow {
        // These records include other years so offline backdated operations also recover.
        val records = store.getOperationRecordsFlow(uid, types)
        launch {
            try {
                while (isActive) {
                    val pending = types.flatMap { store.getRecords(uid, it) }.filter { it.status == "PENDING" }
                    for (saved in pending) {
                        try {
                            val operation = decode(saved)
                            val acknowledged = withTimeoutOrNull(10000L) { remote.apply(uid, operation); true } ?: false
                            if (acknowledged) mutex.withLock { store.removeRecord(uid, saved.recordId, saved.resolvedType) }
                            // Timeout leaves the original operation pending; the transaction may still commit.
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (rejected(e)) mutex.withLock {
                                store.updateOperationStatus(saved.copy(status = "FAILED", errorReason = e.message ?: "Write rejected."))
                            }
                        }
                    }
                    delay(5000L)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { send(CryptoSnapshot(error = "Recovery unavailable: ${e.message}")) }
        }
        val server = flow {
            var last = CryptoSnapshot()
            emit(last)
            while (currentCoroutineContext().isActive) {
                try {
                    remote.observe(uid, year, cutoff).collect {
                        last = CryptoSnapshot(results = it.results, fromCache = it.fromCache, loaded = true)
                        emit(last)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { emit(last.copy(error = "Results unavailable: ${e.message}")) }
                delay(5000L)
            }
        }
        try {
            combine(server, records) { snapshot, saved ->
                val operations = saved.map { it to decode(it) }
                val overlays = operations.map { (record, operation) ->
                    operation.result.copy(sync = if (record.status == "PENDING") CryptoSync.PENDING else CryptoSync.FAILED,
                        error = record.errorReason, deleting = operation.kind == "DELETE")
                }
                val ids = overlays.map { it.id }.toSet()
                snapshot.copy(results = (snapshot.results.filter { it.id !in ids } + overlays)
                    .sortedWith(compareByDescending<CryptoResult> { it.resultDate }.thenBy { it.id }),
                    attempts = operations.map { it.second }, confirmedResults = snapshot.results)
            }.collect { send(it) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { send(CryptoSnapshot(error = "Saved results could not be read: ${e.message}")) }
    }

    private fun record(uid: String, operation: CryptoOperation) =
        SubmissionRecord.create(uid, operation.result.id, operation, "PENDING", operation.type)

    private fun decode(record: SubmissionRecord): CryptoOperation {
        val operation = gson.fromJson(record.payloadJson, CryptoOperation::class.java)
        check(operation.type == record.resolvedType && operation.result.id == record.recordId) { "Invalid saved crypto operation." }
        return operation
    }

    private fun rejected(error: Exception): Boolean = error is IllegalStateException || error is IllegalArgumentException ||
        (error is FirebaseFirestoreException && error.code in setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT, FirebaseFirestoreException.Code.NOT_FOUND,
            FirebaseFirestoreException.Code.ALREADY_EXISTS
        ))
}
