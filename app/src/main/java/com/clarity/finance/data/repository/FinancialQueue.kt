package com.clarity.finance.data.repository

import com.clarity.finance.data.local.*
import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Shared durable acceptance/recovery for legacy financial actions. No screen owns this worker. */
class FinancialQueue(private val remote: FinancialRemote, private val store: SubmissionRecoveryStore) : FinancialRecoveryAccess {
    private val gson = Gson()
    private val changes = Mutex()
    private val sweep = Mutex()
    private val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val acknowledged = mutableSetOf<Pair<String, String>>()
    private val legacy = setOf("EXPENSE", "INCOME", "CREATE_PROJECT", "CREATE_MILESTONE", "CANCEL_MILESTONE", "PAYMENT_DIRECT", "PAYMENT_MILESTONE")
    private fun supported(record: SubmissionRecord) = record.resolvedType.startsWith("FINANCIAL_") || record.resolvedType in legacy
    private fun record(uid: String, op: FinancialOperation) = SubmissionRecord.create(uid, op.recordKey, op, "PENDING", op.type)

    override suspend fun submit(uid: String, operation: FinancialOperation) = changes.withLock {
        val op = operation
        require(uid.isNotBlank()); op.validate()
        listOfNotNull(op.expense?.expenseDate, op.income?.receiptDate, op.crypto?.resultDate).forEach {
            require(SavingsMath.date(it) <= SavingsMath.today()) { "Actual transaction dates cannot be future dates." }
        }
        val saved = store.getAllRecords(uid).filter(::supported).map(::decode)
        val family = op.projectFamily()
        val related = saved.filter { it.recordKey == op.recordKey || (family != null && it.projectFamily() == family) }
        if (related.isNotEmpty()) {
            check(related.size == 1 && related.single().let {
                it.group == op.group && it.kind == op.kind && it.recordKey == op.recordKey &&
                    it.expectedUpdatedAt == op.expectedUpdatedAt && it.payload() == op.payload()
            }) { "Resolve the saved change for this record/project before another change. Its original details are retained." }
            return@withLock // Reuse the original operation ID, including after interrupted local acceptance.
        }
        check(saved.none { it.operationId == op.operationId }) { "An operation identity cannot be shared by different records." }
        store.saveOperation(record(uid, op))
    }

    override fun observe(uid: String): Flow<FinancialRecovery> = flow {
        var last = FinancialRecovery()
        while (currentCoroutineContext().isActive) {
            try {
                combine(store.getAllRecordsFlow(uid), errors) { rows, error ->
                    FinancialRecovery(rows.filter(::supported).map { FinancialAttempt(decode(it), it.status, it.errorReason) },
                        error[uid])
                }.collect { last = it; emit(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { emit(last.copy(error = "Saved changes unavailable: ${e.message}. Keep app data; nothing was discarded.")) }
            delay(5000)
        }
    }

    suspend fun pending(uid: String): List<FinancialAttempt> = store.getAllRecords(uid).filter(::supported)
        .map { FinancialAttempt(decode(it), it.status, it.errorReason) }

    /** Legacy non-suspending Retry buttons use the process scope; bookkeeping errors must stay recoverable. */
    suspend fun retryRecord(uid: String, group: String, targetId: String) {
        try { pending(uid).filter { it.operation.group == group && it.operation.targetId == targetId }
            .forEach { retry(uid, it.operation) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { errors.update { it + (uid to "Retry unavailable: ${e.message}. Original proposals are retained; keep app data.") } }
    }

    /** A manual check changes status only. It never regenerates identity or changes the payload. */
    suspend fun retry(uid: String, op: FinancialOperation) = changes.withLock {
        val saved = saved(uid, op)
        store.updateOperationStatus(saved.copy(status = "UNCERTAIN", errorReason = "Waiting for server reconciliation; the original details are retained."))
    }

    /** Online server fencing wins atomically against any earlier in-flight attempt, or reconciles its receipt. */
    override suspend fun resolve(uid: String, operation: FinancialOperation) {
        val op = operation
        val row = changes.withLock { saved(uid, op) }
        val rejected = try {
            withTimeout(10000) { remote.fence(uid, op) }; false
        } catch (e: DefinitiveMutationRejection) { true }
        catch (e: CancellationException) { throw e }
        if (rejected) changes.withLock {
            store.updateOperationStatus(row.copy(status = "REJECTED", errorReason = "Server fenced this proposal. Review current history before correcting or dismissing it."))
        } else changes.withLock { store.removeRecord(uid, row.recordId, row.resolvedType) }
    }

    override suspend fun dismissRejected(uid: String, operation: FinancialOperation) = changes.withLock {
        val op = operation
        val saved = saved(uid, op)
        check(saved.status == "REJECTED") { "Only an authoritatively rejected/fenced proposal can be dismissed." }
        store.removeRecord(uid, saved.recordId, saved.resolvedType)
    }

    suspend fun synchronize(uid: String) {
        while (currentCoroutineContext().isActive) {
            try {
                sweep.withLock {
                    val localErrors = mutableListOf<String>()
                    for (candidate in store.getAllRecords(uid).filter(::supported).filter { it.status != "REJECTED" }) {
                        try { synchronizeOne(uid, candidate) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { localErrors += "Local recovery unavailable: ${e.message}. Proposal retained." }
                    }
                    errors.update { if (localErrors.isEmpty()) it - uid else it + (uid to localErrors.joinToString("\n")) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors.update { it + (uid to "Recovery store unavailable: ${e.message}. Keep app data.") } }
            delay(5000)
        }
    }

    private suspend fun synchronizeOne(uid: String, original: SubmissionRecord) {
        val op = decode(original)
        val row = if (original.resolvedType.startsWith("FINANCIAL_")) original else record(uid, op).also {
            store.promoteLegacy(original, it)
        }
        val current = changes.withLock {
            val latest = saved(uid, op)
            if (latest.status == "REJECTED") return
            latest.copy(status = "UNCERTAIN", errorReason = "Awaiting server reconciliation; a Firebase Task may still complete.")
                .also { store.updateOperationStatus(it) }
        }
        // Remote outcome classification excludes decode, persistence and cleanup failures.
        var reason = "Timed out awaiting reconciliation; a Firebase Task may still complete. Original proposal retained."
        val result = if ((uid to op.operationId) in acknowledged) 1 else try {
            if (withTimeoutOrNull(10000) { remote.apply(uid, op); true } == true) 1 else 0
        } catch (e: DefinitiveMutationRejection) { reason = e.message ?: "Server fenced this proposal."; -1 }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { reason = uncertainSyncMessage(e); 0 }
        changes.withLock {
            val latest = store.getRecords(uid, op.type).singleOrNull { it.recordId == op.recordKey }
            if (latest == null) { acknowledged.remove(uid to op.operationId); return@withLock }
            check(latest.payloadJson == current.payloadJson) { "Saved proposal changed during reconciliation." }
            if (latest.status == "REJECTED" && result != 1) return@withLock // A late uncertain await cannot undo an acknowledged fence.
            when (result) {
                1 -> {
                    acknowledged += uid to op.operationId
                    store.removeRecord(uid, row.recordId, row.resolvedType)
                    acknowledged.remove(uid to op.operationId)
                }
                -1 -> store.updateOperationStatus(current.copy(status = "REJECTED", errorReason = "$reason Confirmed history was retained; review it before correction."))
                else -> store.updateOperationStatus(current.copy(errorReason = reason)) // Access errors do not prove rejection.
            }
        }
    }

    private suspend fun saved(uid: String, op: FinancialOperation): SubmissionRecord = store.getRecords(uid, op.type)
        .singleOrNull { it.recordId == op.recordKey }?.also { check(it.payloadJson == record(uid, op).payloadJson) }
        ?: error("Saved proposal no longer exists. Reopen the record.")

    private fun FinancialOperation.projectFamily(): String? = when (group) {
        "PROJECT" -> targetId
        "MILESTONE", "RECEIPT" -> parentId
        else -> null
    }

    private data class LegacyCancellation(val paymentId: String, val milestoneId: String, val updatedAt: Timestamp, val opId: String)

    internal fun decode(row: SubmissionRecord): FinancialOperation {
        val nonce = UUID.nameUUIDFromBytes("${row.uid}/${row.resolvedType}/${row.recordId}".toByteArray(Charsets.UTF_8)).toString()
        val op = when (row.resolvedType) {
            "EXPENSE" -> FinancialOperation(nonce, "EXPENSE", "CREATE", row.recordId,
                expense = gson.fromJson(row.payloadJson, Expense::class.java).copy(isPending = false, syncError = null, isConflict = false), legacyPayload = row.payloadJson)
            "INCOME" -> FinancialOperation(nonce, "INCOME", "CREATE", row.recordId,
                income = gson.fromJson(row.payloadJson, Income::class.java).copy(isPending = false, syncError = null, isConflict = false), legacyPayload = row.payloadJson)
            "CREATE_PROJECT" -> FinancialOperation(nonce, "PROJECT", "CREATE", row.recordId,
                project = gson.fromJson(row.payloadJson, ExpectedPayment::class.java), legacyPayload = row.payloadJson)
            "CREATE_MILESTONE" -> gson.fromJson(row.payloadJson, Milestone::class.java).let {
                FinancialOperation(it.lastMilestoneOpId ?: nonce, "MILESTONE", "CREATE", it.id, it.paymentId, milestone = it, legacyPayload = row.payloadJson)
            }
            "CANCEL_MILESTONE" -> gson.fromJson(row.payloadJson, LegacyCancellation::class.java).let {
                FinancialOperation(it.opId, "MILESTONE", "CANCEL", it.milestoneId, it.paymentId, it.updatedAt, legacyPayload = row.payloadJson)
            }
            "PAYMENT_DIRECT", "PAYMENT_MILESTONE" -> gson.fromJson(row.payloadJson, PaymentConfirmationPayload::class.java).let {
                FinancialOperation(it.income.id, "RECEIPT", "CREATE", it.income.id, it.paymentId, income = it.income, legacyPayload = row.payloadJson)
            }
            else -> gson.fromJson(row.payloadJson, FinancialOperation::class.java).also {
                check(it.type == row.resolvedType && it.recordKey == row.recordId) { "Invalid saved operation scope." }
            }
        }
        op.validate(); return op
    }
}
