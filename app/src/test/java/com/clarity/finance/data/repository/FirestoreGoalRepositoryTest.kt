package com.clarity.finance.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.clarity.finance.data.local.*
import com.clarity.finance.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import com.google.firebase.firestore.FirebaseFirestoreException

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreGoalRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val operation = GoalOperation("create", "CREATE", goal = GoalConfig("Fixture", 49000000, 1120000, "2025-01-01", "2027-01-01"))
    private fun accessError(message: String): FirebaseFirestoreException = org.mockito.Mockito.mock(FirebaseFirestoreException::class.java).also {
        org.mockito.Mockito.`when`(it.message).thenReturn(message)
    }
    private class Remote : GoalRemote {
        val snapshots = MutableStateFlow(GoalSnapshot(known = true, entries = emptyList(), fromCache = false))
        var failure: Exception? = IOException("Offline")
        var gate: CompletableDeferred<Unit>? = null
        var beforeGate = false
        var writes = 0
        var outcome: GoalRemoteOutcome? = null
        val receipts = mutableMapOf<Pair<String, String>, GoalOperation>()
        val calls = mutableListOf<Pair<String, GoalOperation>>()
        override fun observe(uid: String) = snapshots
        private fun commit(uid: String, op: GoalOperation) {
            val key = uid to op.operationId
            if (receipts.containsKey(key)) { check(receipts[key] == op); return }
            receipts[key] = op; writes++
        }
        override suspend fun apply(uid: String, operation: GoalOperation): GoalRemoteOutcome {
            calls += uid to operation
            if (beforeGate) commit(uid, operation)
            gate?.await(); failure?.let { throw it }
            outcome?.let { return it }
            commit(uid, operation)
            return GoalRemoteOutcome.Confirmed
        }
    }
    @Test fun `offline operation survives store reopen scoped by owner type and record while observation remains read only`() = runTest {
        val file = temp.newFile("restart.preferences_pb")
        val storeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file }))
        val remote = Remote()
        var repo = FirestoreGoalRepository(remote, store)
        store.saveRecord(SubmissionRecord("a", "active", "{}", "PENDING"))
        repo.submit("a", operation); repo.submit("a", operation); repo.submit("b", operation)
        var snapshot = GoalSnapshot()
        val watch = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.observe("a").collect { snapshot = it } }
        runCurrent(); assertEquals(1, snapshot.attempts.size); assertTrue(remote.calls.isEmpty())
        watch.cancelAndJoin(); storeScope.cancel(); storeScope.coroutineContext[Job]!!.join()
        store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }))
        repo = FirestoreGoalRepository(remote, store)
        remote.failure = null
        val sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertEquals(1, remote.writes); assertTrue(store.getRecords("a", operation.type).isEmpty())
        assertEquals(operation.operationId, withTimeout(1000) { repo.observe("a").first { it.acknowledgedOperationId != null } }.acknowledgedOperationId)
        assertNull(repo.observe("b").first().acknowledgedOperationId)
        assertEquals(1, store.getRecords("b", operation.type).size)
        assertEquals(1, store.getRecords("a", "EXPENSE").size)
        assertTrue(remote.calls.all { it == "a" to operation }); sync.cancelAndJoin()
    }
    @Test fun `timeout and cancelled await retain identity and reconcile acknowledged write once`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("timeout.preferences_pb") }))
        val remote = Remote().apply { failure = null; beforeGate = true; gate = CompletableDeferred() }
        val repo = FirestoreGoalRepository(remote, store)
        repo.submit("a", operation)
        var sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        advanceTimeBy(10001); runCurrent()
        assertEquals("UNCERTAIN", store.getRecords("a", operation.type).single().status)
        try { repo.submit("a", operation.copy(operationId = "different")); fail() } catch (_: IllegalStateException) {}
        try { repo.dismissRejected("a", operation); fail() } catch (_: IllegalStateException) {}
        sync.cancelAndJoin()
        remote.gate!!.complete(Unit)
        sync = backgroundScope.launch { FirestoreGoalRepository(remote, store).synchronize("a") }; runCurrent()
        assertEquals(1, remote.writes); assertTrue(store.getRecords("a", operation.type).isEmpty())
        assertTrue(remote.calls.all { it.second == operation }); sync.cancelAndJoin()
    }
    @Test fun `rejection remains inspectable and immutable while different goal operations are blocked`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("failed.preferences_pb") }))
        val remote = Remote().apply { failure = null; outcome = GoalRemoteOutcome.Rejected("Verified stale version conflict") }
        val repo = FirestoreGoalRepository(remote, store)
        repo.submit("a", operation)
        val sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertEquals("REJECTED", store.getRecords("a", operation.type).single().status)
        try { repo.submit("a", operation.copy(kind = "EDIT", expectedVersion = 0)); fail() } catch (_: IllegalStateException) {}
        try { repo.retry("a", operation.copy(goal = operation.goal!!.copy(openingMinor = 1))); fail() } catch (_: IllegalStateException) {}
        repo.retry("a", operation); advanceTimeBy(5000); runCurrent()
        repo.dismissRejected("a", operation); assertTrue(store.getRecords("a", operation.type).isEmpty())
        val corrected = operation.copy(operationId = "corrected", goal = operation.goal!!.copy(name = "Corrected proposal"))
        repo.submit("a", corrected)
        assertEquals(SubmissionRecord.create("a", corrected.recordId, corrected, "PENDING", corrected.type).payloadJson,
            store.getRecords("a", corrected.type).single().payloadJson)
        sync.cancelAndJoin()
    }
    @Test fun `receipt payload match is strict and cancelled work propagates`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("cancel.preferences_pb") }))
        val remote = Remote().apply { failure = CancellationException("Stop") }
        val repo = FirestoreGoalRepository(remote, store); repo.submit("a", operation)
        val job = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertTrue(job.isCancelled); assertEquals("UNCERTAIN", store.getRecords("a", operation.type).single().status)
        val wrong = operation.copy(goalId = "other")
        try { repo.submit("a", wrong); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun `ledger deletion recovery retains exact timestamped immutable entry payload`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("entry.preferences_pb") }))
        val remote = Remote()
        val entry = SavingsEntry("entry", "withdrawal", 120000, "2025-01-01", "Original note", true, 1, "original",
            com.google.firebase.Timestamp(1, 2), com.google.firebase.Timestamp(3, 4))
        val op = GoalOperation("delete", "ENTRY_DELETE", 1, entry = entry)
        FirestoreGoalRepository(remote, store).submit("a", op)
        val recovered = FirestoreGoalRepository(remote, store).observe("a").first { it.attempts.isNotEmpty() }.attempts.single().operation
        assertEquals(op, recovered); assertEquals(entry.values(), recovered.payload())
        try { op.copy(expectedVersion = Long.MAX_VALUE).validate(false); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun `late attempt A commit survives timeout then B access error and reconciles exactly one mutation`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("late.preferences_pb") }))
        val commitA = CompletableDeferred<Unit>()
        val receipt = mutableMapOf<Pair<String, String>, GoalOperation>()
        val calls = mutableListOf<Pair<String, GoalOperation>>()
        var mutations = 0
        val remote = object : GoalRemote {
            override fun observe(uid: String) = MutableStateFlow(GoalSnapshot())
            override suspend fun apply(uid: String, operation: GoalOperation): GoalRemoteOutcome {
                calls += uid to operation
                when (calls.size) {
                    1 -> {
                        // Models Firebase's Task lifetime, independent of the coroutine awaiting it.
                        backgroundScope.launch { commitA.await(); receipt[uid to operation.operationId] = operation; mutations++ }
                        awaitCancellation()
                    }
                    2 -> throw accessError("PERMISSION_DENIED before receipt reconciliation")
                    else -> {
                        check(receipt[uid to operation.operationId] == operation) { "Exact matching receipt required" }
                        return GoalRemoteOutcome.Confirmed
                    }
                }
            }
        }
        var repo = FirestoreGoalRepository(remote, store)
        repo.submit("a", operation)
        val payload = store.getRecords("a", operation.type).single().payloadJson
        var sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        advanceTimeBy(10001); runCurrent()
        assertEquals(0, mutations); assertEquals("UNCERTAIN", store.getRecords("a", operation.type).single().status)
        sync.cancelAndJoin()
        // A repository restart must not turn a later access error into a rejection of the logical operation.
        repo = FirestoreGoalRepository(remote, store)
        sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertEquals(2, calls.size)
        val retained = store.getRecords("a", operation.type).single()
        assertEquals(payload, retained.payloadJson); assertEquals("UNCERTAIN", retained.status)
        assertFalse(repo.observe("a").first { it.attempts.isNotEmpty() }.attempts.single().isRejected)
        try { repo.dismissRejected("a", operation); fail("Uncertain proposal must not be discardable") } catch (_: IllegalStateException) {}
        repo.retry("a", operation); assertEquals("UNCERTAIN", store.getRecords("a", operation.type).single().status)
        commitA.complete(Unit); runCurrent(); assertEquals(1, mutations)
        advanceTimeBy(5000); runCurrent()
        assertTrue(store.getRecords("a", operation.type).isEmpty()); assertEquals(1, receipt.size); assertEquals(1, mutations)
        assertTrue(calls.all { it == "a" to operation }); assertEquals(3, calls.size)
        sync.cancelAndJoin()
    }
    @Test fun `legacy failure and raw argument state or auth errors are uncertain and never dismissible`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("legacy.preferences_pb") }))
        val remote = Remote()
        val repo = FirestoreGoalRepository(remote, store)
        store.saveOperation(SubmissionRecord.create("a", operation.recordId, operation, "FAILED", operation.type, "Old unproven failure"))
        try { repo.dismissRejected("a", operation); fail() } catch (_: IllegalStateException) {}
        val sync = backgroundScope.launch { repo.synchronize("a") }
        for (failure in listOf(IllegalArgumentException("Unknown argument failure"), IllegalStateException("Account changed"),
            accessError("UNAUTHENTICATED: signed out"))) {
            remote.failure = failure; runCurrent(); advanceTimeBy(5000); runCurrent()
            assertEquals("UNCERTAIN", store.getRecords("a", operation.type).single().status)
            try { repo.dismissRejected("a", operation); fail() } catch (_: IllegalStateException) {}
        }
        sync.cancelAndJoin()
    }
    @Test fun `confirmed remote result with local cleanup failure remains retained and retries only bookkeeping`() = runTest {
        val actual = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("cleanup.preferences_pb") })
        var failUpdates = false
        val faulty = object : DataStore<Preferences> {
            override val data = actual.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failUpdates) throw IllegalStateException("Local disk bookkeeping failure")
                return actual.updateData(transform)
            }
        }
        val store = SubmissionRecoveryStore(faulty)
        var remoteCalls = 0
        val remote = object : GoalRemote {
            override fun observe(uid: String) = MutableStateFlow(GoalSnapshot())
            override suspend fun apply(uid: String, operation: GoalOperation): GoalRemoteOutcome {
                remoteCalls++; failUpdates = true; return GoalRemoteOutcome.Confirmed
            }
        }
        val repo = FirestoreGoalRepository(remote, store); repo.submit("a", operation)
        val sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertEquals("UNCERTAIN", store.getRecords("a", operation.type).single().status)
        val snapshot = repo.observe("a").first { it.recoveryError != null }
        assertTrue(snapshot.recoveryError!!.contains("Local goal recovery bookkeeping"))
        assertEquals(operation.operationId, snapshot.acknowledgedOperationId)
        assertNull(repo.observe("b").first().recoveryError)
        try { repo.dismissRejected("a", operation); fail() } catch (_: IllegalStateException) {}
        failUpdates = false; advanceTimeBy(5000); runCurrent()
        assertEquals(1, remoteCalls); assertTrue(store.getRecords("a", operation.type).isEmpty())
        assertNull(repo.observe("a").first().recoveryError); sync.cancelAndJoin()
    }
}
