package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecord
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.SavingsMath
import java.time.Clock
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirestoreGoalRepository(private val remote: GoalRemote, private val store: SubmissionRecoveryStore, private val clock: Clock = Clock.systemUTC()) : GoalRepository {
    private val gson = Gson()
    private val mutex = Mutex()
    private val synchronization = Mutex()
    private val acknowledged = MutableStateFlow<Pair<String, String>?>(null)
    private val bookkeepingError = MutableStateFlow<Pair<String, String>?>(null)
    private val types = setOf("GOAL_CREATE", "GOAL_EDIT", "GOAL_ENTRY_CREATE", "GOAL_ENTRY_UPDATE", "GOAL_ENTRY_DELETE")
    private fun record(uid: String, operation: GoalOperation) = SubmissionRecord.create(uid, operation.recordId, operation, "PENDING", operation.type)
    override suspend fun submit(uid: String, operation: GoalOperation) = mutex.withLock {
        require(uid.isNotBlank()); operation.validate(temporal = false)
        val saved = types.flatMap { store.getRecords(uid, it) }
        val next = record(uid, operation)
        check(saved.all { it.recordId == next.recordId && it.resolvedType == next.resolvedType && it.payloadJson == next.payloadJson }) {
            "Resolve the saved goal change before starting another. Its original details are retained."
        }
        store.saveOperation(next)
    }
    override suspend fun retry(uid: String, operation: GoalOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.recordId } ?: error("Saved operation no longer exists.")
        check(saved.payloadJson == record(uid, operation).payloadJson) { "Retry must use the immutable saved payload." }
        store.updateOperationStatus(saved.copy(status = if (saved.status in setOf("PENDING", "REJECTED", "EXPIRED")) "PENDING" else "UNCERTAIN", errorReason = null))
    }
    override suspend fun dismissRejected(uid: String, operation: GoalOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.recordId } ?: error("Saved operation no longer exists.")
        check(saved.status in setOf("REJECTED", "EXPIRED") && saved.payloadJson == record(uid, operation).payloadJson) { "Only a server-established rejection can be dismissed; uncertain and legacy failed operations must be reconciled." }
        store.removeRecord(uid, saved.recordId, saved.resolvedType)
    }
    override suspend fun reviseExpired(uid: String, original: GoalOperation, replacement: GoalOperation) = mutex.withLock {
        original.validate(false); replacement.validate(today = SavingsMath.today(clock))
        require(original.goal != null && replacement.goal != null && original.kind in setOf("CREATE", "EDIT"))
        require(replacement.operationId != original.operationId && replacement.kind == original.kind && replacement.expectedVersion == original.expectedVersion &&
            replacement.previousDeadline == original.previousDeadline && replacement.goalId == original.goalId && replacement.entry == null &&
            replacement.goal.copy(deadline = original.goal.deadline) == original.goal) { "Deadline revision must preserve every other original proposal value." }
        require(SavingsMath.date(replacement.goal.deadline) > SavingsMath.today(clock)) { "Choose a future deadline." }
        val saved = types.flatMap { store.getRecords(uid, it) }
        check(saved.size == 1 && saved.single().recordId == original.recordId && saved.single().resolvedType == original.type) { "Resolve the saved proposal before revising it." }
        val old = record(uid, original).copy(status = "EXPIRED")
        store.replaceExpiredOperation(old, record(uid, replacement))
    }
    override fun observe(uid: String): Flow<GoalSnapshot> = flow {
        var last = GoalSnapshot()
        val server = flow {
            emit(last)
            while (currentCoroutineContext().isActive) {
                try { remote.observe(uid).collect { last = it; emit(it) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { emit(last.copy(fromCache = true, error = "Goal unavailable: ${e.message}")) }
                delay(5000L)
            }
        }
        val saved = flow<Pair<List<GoalAttempt>, String?>> {
          while (currentCoroutineContext().isActive) {
            try { store.getOperationRecordsFlow(uid, types).collect { emit(it.map { row -> GoalAttempt(decode(row), row.status, row.errorReason) } to null) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { emit(emptyList<GoalAttempt>() to "Recovery unavailable: ${e.message}") }
            delay(5000L)
          }
        }
        combine(server, saved, acknowledged, bookkeepingError) { snapshot, (attempts, error), accepted, local -> snapshot.copy(attempts = attempts,
            recoveryError = listOfNotNull(error, local?.takeIf { it.first == uid }?.second).takeIf { it.isNotEmpty() }?.joinToString("\n"),
            acknowledgedOperationId = accepted?.takeIf { it.first == uid }?.second) }.collect { emit(it) }
    }
    override suspend fun synchronize(uid: String) {
        while (currentCoroutineContext().isActive) {
            try {
                synchronization.withLock {
                    // Multiple savings ViewModels may be alive; only one recovery sweep submits at a time.
                    val pending = types.flatMap { store.getRecords(uid, it) }.filter { it.status !in setOf("REJECTED", "EXPIRED") }
                    for (saved in pending) synchronizeRecord(uid, saved)
                }
                if (bookkeepingError.value?.first == uid) bookkeepingError.value = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Decode/store/status/cleanup failures are local errors, never evidence of remote rejection.
                bookkeepingError.value = uid to "Local goal recovery bookkeeping unavailable: ${e.message}. Saved proposals are retained."
            }
            delay(5000L)
        }
    }
    private suspend fun synchronizeRecord(uid: String, saved: SubmissionRecord) {
        val operation = decode(saved)
        val current = mutex.withLock {
            val row = store.getRecords(uid, saved.resolvedType).singleOrNull { it.recordId == saved.recordId } ?: return
            check(row.payloadJson == saved.payloadJson) { "Saved operation changed. Reopen recovery." }
            if (row.status in setOf("REJECTED", "EXPIRED")) return
            // Persist uncertainty BEFORE starting a Firebase Task, including across process death/cancelled awaits.
            row.copy(status = "UNCERTAIN", errorReason = "Awaiting server reconciliation; an earlier transaction may still complete.")
                .also { store.updateOperationStatus(it) }
        }
        val result = if (acknowledged.value == (uid to operation.operationId)) GoalRemoteOutcome.Confirmed else try {
            withTimeoutOrNull(10000L) { remote.apply(uid, operation) }
                ?: GoalRemoteOutcome.Uncertain("Timed out awaiting Firebase; the transaction may still complete.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // In particular, account/auth/access errors cannot disprove a previous commit.
            GoalRemoteOutcome.Uncertain("Reconciliation unavailable: ${e.message ?: "Remote outcome unknown"}")
        }
        // Keep all local bookkeeping outside the remote catch/classification boundary.
        mutex.withLock {
            when (result) {
                GoalRemoteOutcome.Confirmed -> {
                    acknowledged.value = uid to operation.operationId
                    store.removeRecord(uid, saved.recordId, saved.resolvedType)
                }
                is GoalRemoteOutcome.Rejected -> store.updateOperationStatus(current.copy(status = if (result.deadlineExpired) "EXPIRED" else "REJECTED", errorReason = result.reason))
                is GoalRemoteOutcome.Uncertain -> store.updateOperationStatus(current.copy(errorReason = result.reason))
            }
        }
    }
    private fun decode(record: SubmissionRecord): GoalOperation = gson.fromJson(record.payloadJson, GoalOperation::class.java).also {
        check(it.type == record.resolvedType && it.recordId == record.recordId && it.goalId == "active") { "Invalid saved goal operation." }
        it.validate(temporal = false)
    }
}
