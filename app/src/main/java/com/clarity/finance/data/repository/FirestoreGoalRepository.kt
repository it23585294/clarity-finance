package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecord
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FirestoreGoalRepository(private val remote: GoalRemote, private val store: SubmissionRecoveryStore) : GoalRepository {
    private val gson = Gson()
    private val mutex = Mutex()
    private val acknowledged = MutableStateFlow<Pair<String, String>?>(null)
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
        store.updateOperationStatus(saved.copy(status = "PENDING", errorReason = null))
    }
    override suspend fun dismissRejected(uid: String, operation: GoalOperation) = mutex.withLock {
        val saved = store.getRecords(uid, operation.type).singleOrNull { it.recordId == operation.recordId } ?: error("Saved operation no longer exists.")
        check(saved.status == "FAILED" && saved.payloadJson == record(uid, operation).payloadJson) { "Only a definitively rejected saved proposal can be dismissed." }
        store.removeRecord(uid, saved.recordId, saved.resolvedType)
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
        combine(server, saved, acknowledged) { snapshot, (attempts, error), accepted -> snapshot.copy(attempts = attempts,
            recoveryError = error, acknowledgedOperationId = accepted?.takeIf { it.first == uid }?.second) }.collect { emit(it) }
    }
    override suspend fun synchronize(uid: String) {
        while (currentCoroutineContext().isActive) {
            val pending = types.flatMap { store.getRecords(uid, it) }.filter { it.status == "PENDING" }
            for (saved in pending) {
                try {
                    val operation = decode(saved)
                    val acknowledged = withTimeoutOrNull(10000L) { remote.apply(uid, operation); true } ?: false
                    if (acknowledged) mutex.withLock {
                        this@FirestoreGoalRepository.acknowledged.value = uid to operation.operationId
                        store.removeRecord(uid, saved.recordId, saved.resolvedType)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is IllegalArgumentException || e is IllegalStateException || (e is FirebaseFirestoreException && e.code in setOf(
                            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED,
                            FirebaseFirestoreException.Code.ALREADY_EXISTS, FirebaseFirestoreException.Code.NOT_FOUND))) {
                        mutex.withLock { store.updateOperationStatus(saved.copy(status = "FAILED", errorReason = e.message ?: "Goal change rejected.")) }
                    }
                    // Transient errors and timed-out awaits retain the same pending payload; the transaction may still commit.
                }
            }
            delay(5000L)
        }
    }
    private fun decode(record: SubmissionRecord): GoalOperation = gson.fromJson(record.payloadJson, GoalOperation::class.java).also {
        check(it.type == record.resolvedType && it.recordId == record.recordId && it.goalId == "active") { "Invalid saved goal operation." }
        it.validate(temporal = false)
    }
}
