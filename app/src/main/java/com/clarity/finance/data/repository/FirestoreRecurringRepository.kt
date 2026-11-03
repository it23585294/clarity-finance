package com.clarity.finance.data.repository

import com.clarity.finance.data.local.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirestoreRecurringRepository(private val remote: RecurringRemote, private val store: SubmissionRecoveryStore) : RecurringRepository {
    private val gson = Gson()
    private val changes = Mutex()
    private val sweeps = Mutex()
    private val acknowledged = MutableStateFlow<Map<Triple<String, String, String>, String>>(emptyMap())
    private val bookkeeping = MutableStateFlow<Pair<String, String>?>(null)
    private val types = setOf("RECURRING_CREATE", "RECURRING_EDIT", "RECURRING_CANCEL", "RECURRING_CONFIRM")
    private fun row(uid: String, op: RecurringOperation) = SubmissionRecord.create(uid, op.recordId, op, "PENDING", op.type)
    override fun templates(uid: String, limit: Int) = remote.templates(uid, limit)
    override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = remote.occurrences(uid, templateId, start, endExclusive)
    override suspend fun submit(uid: String, operation: RecurringOperation) = changes.withLock {
        require(uid.isNotBlank()); operation.validate()
        if (operation.kind != "CONFIRM") {
            val others = types.filter { it != "RECURRING_CONFIRM" }.flatMap { store.getRecords(uid, it) }
            check(others.none { it.recordId == operation.recordId && (it.resolvedType != operation.type || it.payloadJson != row(uid, operation).payloadJson) }) {
                "Resolve this template's saved change before editing or cancelling it again. Pending confirmations retain their own details."
            }
        }
        store.saveOperation(row(uid, operation))
    }
    override suspend fun dismiss(uid: String, operation: RecurringOperation) = changes.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.recordId }
        check(saved?.status == "REJECTED" && saved.payloadJson == row(uid, operation).payloadJson) { "Only a server-established rejection can be dismissed. Uncertain proposals must reconcile." }
        store.removeRecord(uid, operation.recordId, operation.type)
    }
    override fun recovery(uid: String): Flow<RecurringRecovery> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                combine(store.getOperationRecordsFlow(uid, types), acknowledged, bookkeeping) { records, accepted, local ->
                    RecurringRecovery(records.map { RecurringAttempt(decode(it), it.status, it.errorReason) },
                        local?.takeIf { it.first == uid }?.second, accepted.filterKeys { it.first == uid }.values.toSet())
                }.collect { emit(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { emit(RecurringRecovery(error = "Recovery unavailable: ${e.message}. Saved proposals retained.")) }
            delay(5000)
        }
    }
    override suspend fun synchronize(uid: String) {
        while (currentCoroutineContext().isActive) {
            try {
                sweeps.withLock {
                    val localErrors = mutableListOf<String>()
                    for (saved in types.flatMap { store.getRecords(uid, it) }.filter { it.status != "REJECTED" }) {
                        try { synchronizeRecord(uid, saved) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { localErrors += "Local recovery bookkeeping unavailable: ${e.message}. Proposal retained." }
                    }
                    bookkeeping.value = localErrors.takeIf { it.isNotEmpty() }?.let { uid to it.joinToString("\n") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { bookkeeping.value = uid to "Local recovery bookkeeping unavailable: ${e.message}. Proposals retained." }
            delay(5000)
        }
    }
    private suspend fun synchronizeRecord(uid: String, saved: SubmissionRecord) {
        val op = decode(saved)
        val current = changes.withLock {
            val latest = store.getRecords(uid, saved.resolvedType).singleOrNull { it.recordId == saved.recordId } ?: return
            check(latest.payloadJson == saved.payloadJson)
            if (latest.status == "REJECTED") return
            latest.copy(status = "UNCERTAIN", errorReason = "Awaiting server reconciliation; an earlier Task may still complete.")
                .also { store.updateOperationStatus(it) }
        }
        val key = Triple(uid, op.type, op.recordId)
        val result = if (acknowledged.value[key] == op.operationId) RecurringOutcome.Confirmed else try {
            withTimeoutOrNull(10000) { remote.apply(uid, op) } ?: RecurringOutcome.Uncertain("Timed out; the Firebase operation may still complete.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { RecurringOutcome.Uncertain("Reconciliation unavailable: ${e.message}") }
        // Store/decode/cleanup errors never enter the remote outcome classifier.
        changes.withLock {
            when (result) {
                RecurringOutcome.Confirmed -> {
                    // Retain remote acknowledgement even when local cleanup fails.
                    acknowledged.value = acknowledged.value + (key to op.operationId)
                    store.removeRecord(uid, saved.recordId, saved.resolvedType)
                    // A small latest acknowledgement per target also handles fast observe/persist races.
                    if (acknowledged.value.size > 128) acknowledged.value = acknowledged.value.entries.toList().takeLast(128).associate { it.toPair() }
                }
                is RecurringOutcome.Rejected -> store.updateOperationStatus(current.copy(status = "REJECTED", errorReason = result.reason))
                is RecurringOutcome.Uncertain -> store.updateOperationStatus(current.copy(errorReason = result.reason))
            }
        }
    }
    private fun decode(record: SubmissionRecord): RecurringOperation = gson.fromJson(record.payloadJson, RecurringOperation::class.java).also {
        it.validate(); check(it.type == record.resolvedType && it.recordId == record.recordId) { "Invalid saved recurring proposal." }
    }
}
