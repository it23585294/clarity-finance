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

/** Durable proposals; one process coordinator synchronizes all years independently of screens. */
class FirestoreCryptoRepository(
    private val remote: CryptoRemote,
    private val store: SubmissionRecoveryStore
) : CryptoRepository {
    private val gson = Gson()
    private val mutex = Mutex()
    private val sweep = Mutex()
    private val acknowledged = mutableSetOf<Pair<String, String>>()
    private val types = setOf("CRYPTO_CREATE", "CRYPTO_UPDATE", "CRYPTO_DELETE")

    override suspend fun submit(uid: String, operation: CryptoOperation) = mutex.withLock {
        require(uid.isNotBlank())
        require(operation.kind in setOf("CREATE", "UPDATE", "DELETE"))
        CryptoReporting.validate(operation.result.direction, operation.result.amountMinor,
            operation.result.resultDate, operation.result.note, CryptoReporting.today())
        require(operation.result.id.isNotBlank() && operation.operationId.isNotBlank())
        val existing = types.flatMap { store.getRecords(uid, it) }.filter { it.recordId == operation.result.id }
        val record = record(uid, operation)
        check(existing.all { it.resolvedType == record.resolvedType && decode(it) == operation }) {
            "Resolve the saved operation for this result before making another change."
        }
        if (existing.isEmpty()) store.saveOperation(record) // Preserve legacy JSON that predates protocolVersion.
    }

    override suspend fun retry(uid: String, operation: CryptoOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.result.id }
            ?: error("Saved operation no longer exists. Refresh the list.")
        check(decode(saved) == operation) { "Retry must use the original saved details." }
        store.updateOperationStatus(saved.copy(status = "PENDING", errorReason = null))
    }

    override suspend fun dismissRejected(uid: String, operation: CryptoOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.result.id }
            ?: error("Saved operation no longer exists.")
        check(saved.status == "REJECTED" && decode(saved) == operation) {
            "Only a definitively rejected operation can be dismissed. Pending outcomes must be reconciled."
        }
        store.removeRecord(uid, saved.recordId, saved.resolvedType)
    }

    override suspend fun resolve(uid: String, operation: CryptoOperation) {
        val saved = mutex.withLock {
            store.getRecords(uid, operation.type).single { it.recordId == operation.result.id }.also {
                check(decode(it) == operation) { "Saved proposal changed; reopen recovery." }
            }
        }
        val rejected = try { remote.resolve(uid, operation); false }
        catch (e: DefinitiveMutationRejection) { true }
        catch (e: CancellationException) { throw e }
        mutex.withLock {
            if (rejected) store.updateOperationStatus(saved.copy(status = "REJECTED", errorReason = "Server fenced this proposal. Confirmed history is retained."))
            else store.removeRecord(uid, saved.recordId, saved.resolvedType)
        }
    }

    override fun observe(uid: String, year: Int, cutoff: String): Flow<CryptoSnapshot> = channelFlow {
        // These records include other years so offline backdated operations also recover.
        val records = store.getOperationRecordsFlow(uid, types)
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
                    operation.result.copy(sync = if (record.status != "REJECTED") CryptoSync.PENDING else CryptoSync.FAILED,
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

    override suspend fun synchronize(uid: String) {
        while (currentCoroutineContext().isActive) {
            sweep.withLock {
                for (saved in types.flatMap { store.getRecords(uid, it) }.filter { it.status != "REJECTED" }) {
                    val operation = decode(saved)
                    val uncertain = saved.copy(status = "UNCERTAIN", errorReason = "Awaiting server reconciliation. The original proposal is retained.")
                    store.updateOperationStatus(uncertain)
                    var reason = "Timed out awaiting reconciliation; a Firebase Task may still complete. Original proposal retained."
                    val outcome = if ((uid to operation.operationId) in acknowledged) 1 else try {
                        if (withTimeoutOrNull(10000) { remote.apply(uid, operation); true } == true) 1 else 0
                    } catch (e: DefinitiveMutationRejection) { reason = e.message ?: "Server fenced this proposal."; -1 }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { reason = uncertainSyncMessage(e); 0 }
                    // Local recovery failures must not be confused with a remote rejection.
                    mutex.withLock {
                        val latest = store.getRecords(uid, operation.type).singleOrNull { it.recordId == saved.recordId }
                        if (latest == null) { acknowledged.remove(uid to operation.operationId); return@withLock }
                        check(latest.payloadJson == saved.payloadJson) { "Saved proposal changed during reconciliation." }
                        if (latest.status == "REJECTED" && outcome != 1) return@withLock
                        if (outcome == 1) {
                            acknowledged += uid to operation.operationId
                            store.removeRecord(uid, saved.recordId, saved.resolvedType)
                            acknowledged.remove(uid to operation.operationId)
                        } else if (outcome == -1) store.updateOperationStatus(uncertain.copy(status = "REJECTED", errorReason = "$reason Confirmed history is unchanged."))
                        else store.updateOperationStatus(uncertain.copy(errorReason = reason))
                    }
                }
            }
            delay(5000)
        }
    }
}
