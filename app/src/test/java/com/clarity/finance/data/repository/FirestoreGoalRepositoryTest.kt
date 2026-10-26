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
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreGoalRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val operation = GoalOperation("create", "CREATE", goal = GoalConfig("Fixture", 49000000, 1120000, "2025-01-01", "2027-01-01"))
    private fun accessError(message: String): FirebaseFirestoreException = org.mockito.Mockito.mock(FirebaseFirestoreException::class.java).also {
        org.mockito.Mockito.`when`(it.message).thenReturn(message)
    }
    private class TestClock(var day: LocalDate) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun instant() = day.atStartOfDay(ZoneOffset.UTC).toInstant()
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
    }
    private class ExpiryServer(private val clock: TestClock) : GoalRemote {
        val snapshot = MutableStateFlow(GoalSnapshot(known = true, entries = emptyList(), fromCache = false))
        val receipts = mutableMapOf<String, GoalOperation>()
        val fences = mutableMapOf<String, GoalOperation>()
        var online = false
        var loseAcknowledgement = false
        var mutations = 0
        override fun observe(uid: String) = snapshot
        override suspend fun apply(uid: String, operation: GoalOperation): GoalRemoteOutcome {
            if (!online) throw IOException("Offline")
            receipts[operation.operationId]?.let { check(it == operation); return GoalRemoteOutcome.Confirmed }
            fences[operation.operationId]?.let { check(it == operation); return GoalRemoteOutcome.Rejected("Server-fenced expired deadline", true) }
            val before = snapshot.value.goal
            checkGoalVersion(operation, before)
            if (operation.goal != null && SavingsMath.date(operation.goal.deadline) <= clock.day &&
                (operation.kind == "CREATE" || operation.goal.deadline != before!!.config.deadline)) {
                fences[operation.operationId] = operation
                return GoalRemoteOutcome.Rejected("Server-fenced expired deadline", true)
            }
            operation.validate(today = clock.day)
            val saved = goalMutationBalance(operation, before, snapshot.value.entries.orEmpty().find { it.id == operation.entry?.id })
            receipts[operation.operationId] = operation; mutations++
            snapshot.value = snapshot.value.copy(goal = SavingsGoal(operation.goal ?: before!!.config, saved, operation.nextVersion, operation.operationId),
                entries = if (operation.entry == null) snapshot.value.entries else snapshot.value.entries.orEmpty().filterNot { it.id == operation.entry.id } + operation.entry)
            if (loseAcknowledgement) throw IOException("Acknowledgement lost")
            return GoalRemoteOutcome.Confirmed
        }
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
    @Test fun `offline CREATE expires before any commit then durable deadline revision creates savings once`() = runTest {
        val clock = TestClock(LocalDate.parse("2026-01-31"))
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("expired-create.preferences_pb") }))
        val server = ExpiryServer(clock)
        var repo = FirestoreGoalRepository(server, store, clock)
        val original = operation.copy(goal = operation.goal!!.copy(startDate = "2026-01-31", deadline = "2026-02-28"))
        original.validate(today = clock.day); repo.submit("a", original)
        var sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertEquals(0, server.mutations)
        clock.day = LocalDate.parse("2026-03-01"); server.online = true
        advanceTimeBy(5000); runCurrent()
        assertEquals("EXPIRED", store.getRecords("a", original.type).single().status)
        assertNull(server.snapshot.value.goal); assertEquals(0, server.mutations)
        sync.cancelAndJoin()
        val replacement = original.copy(operationId = "revised-create", goal = original.goal!!.copy(deadline = "2026-05-31"))
        repo.reviseExpired("a", original, replacement); repo.reviseExpired("a", original, replacement)
        // New repository reads the exact replacement from durable storage before submission.
        repo = FirestoreGoalRepository(server, store, clock)
        assertEquals(replacement, repo.observe("a").first { it.attempts.isNotEmpty() }.attempts.single().operation)
        sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        assertEquals(1, server.mutations); assertEquals(1120000L, server.snapshot.value.goal!!.savedMinor)
        assertEquals(original.goal.startDate, server.snapshot.value.goal!!.config.startDate)
        assertTrue(store.getRecords("a", original.type).isEmpty()); assertEquals(setOf(replacement.operationId), server.receipts.keys)
        assertTrue((server.apply("a", original) as GoalRemoteOutcome.Rejected).deadlineExpired)
        assertEquals(1, server.mutations); sync.cancelAndJoin()
    }
    @Test fun `expired deadline changing EDIT revises without resetting contributions or opening delta`() = runTest {
        val clock = TestClock(LocalDate.parse("2026-01-31")); val server = ExpiryServer(clock)
        val config = operation.goal!!.copy(startDate = "2025-12-31", deadline = "2026-06-30")
        val contribution = SavingsEntry("c", "contribution", 1000000, "2026-01-01")
        server.snapshot.value = GoalSnapshot(SavingsGoal(config, 2120000, 1, "previous"), listOf(contribution), true, false)
        val original = GoalOperation("expired-edit", "EDIT", 1, goal = config.copy(openingMinor = 1200000, deadline = "2026-02-28"), previousDeadline = config.deadline)
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("expired-edit.preferences_pb") }))
        val repo = FirestoreGoalRepository(server, store, clock)
        original.validate(today = clock.day); repo.submit("a", original)
        val sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
        clock.day = LocalDate.parse("2026-03-01"); server.online = true; advanceTimeBy(5000); runCurrent()
        assertEquals("EXPIRED", store.getRecords("a", original.type).single().status)
        assertEquals(2120000L, server.snapshot.value.goal!!.savedMinor); assertEquals(listOf(contribution), server.snapshot.value.entries)
        val replacement = original.copy(operationId = "revised-edit", goal = original.goal!!.copy(deadline = "2026-07-31"))
        try { repo.reviseExpired("a", original, replacement.copy(goal = replacement.goal!!.copy(openingMinor = 0))); fail() } catch (_: IllegalArgumentException) {}
        repo.reviseExpired("a", original, replacement); advanceTimeBy(5000); runCurrent()
        assertEquals(2200000L, server.snapshot.value.goal!!.savedMinor); assertEquals(listOf(contribution), server.snapshot.value.entries)
        assertEquals(1, server.mutations); assertTrue(store.getRecords("a", original.type).isEmpty()); sync.cancelAndJoin()
    }
    @Test fun `earlier committed CREATE and EDIT reconcile after expiry instead of permitting replacement`() = runTest {
        for (kind in listOf("CREATE", "EDIT")) {
            val clock = TestClock(LocalDate.parse("2026-01-31")); val server = ExpiryServer(clock)
            val config = operation.goal!!.copy(startDate = "2025-12-31", deadline = "2026-06-30")
            if (kind == "EDIT") server.snapshot.value = GoalSnapshot(SavingsGoal(config, 2120000, 1, "previous"),
                listOf(SavingsEntry("c", "contribution", 1000000, "2026-01-01")), true, false)
            val original = GoalOperation("accepted-$kind", kind, if (kind == "EDIT") 1 else null,
                goal = config.copy(deadline = "2026-02-28"), previousDeadline = if (kind == "EDIT") config.deadline else null)
            val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("accepted-$kind.preferences_pb") }))
            val repo = FirestoreGoalRepository(server, store, clock); repo.submit("a", original)
            server.online = true; server.loseAcknowledgement = true
            val sync = backgroundScope.launch { repo.synchronize("a") }; runCurrent()
            assertEquals(1, server.mutations)
            clock.day = LocalDate.parse("2026-03-01")
            try { repo.reviseExpired("a", original, original.copy(operationId = "forbidden", goal = original.goal!!.copy(deadline = "2026-08-31"))); fail() } catch (_: IllegalStateException) {}
            advanceTimeBy(5000); runCurrent()
            assertEquals(1, server.mutations); assertTrue(server.fences.isEmpty()); assertTrue(store.getRecords("a", original.type).isEmpty())
            assertEquals(if (kind == "CREATE") 1120000L else 2120000L, server.snapshot.value.goal!!.savedMinor)
            sync.cancelAndJoin()
        }
    }
    @Test fun `failed atomic deadline replacement retains original and a cancelled post persistence await retries idempotently`() = runTest {
        val clock = TestClock(LocalDate.parse("2026-03-01"))
        val actual = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("replace.preferences_pb") })
        var failReplacement = false; var waitAfterPersist: CompletableDeferred<Unit>? = null
        val wrapped = object : DataStore<Preferences> {
            override val data = actual.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failReplacement) throw IOException("Local write failed")
                val result = actual.updateData(transform); waitAfterPersist?.await(); return result
            }
        }
        val store = SubmissionRecoveryStore(wrapped)
        val original = operation.copy(goal = operation.goal!!.copy(deadline = "2026-02-28"))
        store.saveOperation(SubmissionRecord.create("a", original.recordId, original, "EXPIRED", original.type))
        val replacement = original.copy(operationId = "revised", goal = original.goal!!.copy(deadline = "2026-05-31"))
        val repo = FirestoreGoalRepository(Remote(), store, clock)
        failReplacement = true
        try { repo.reviseExpired("a", original, replacement); fail() } catch (_: IOException) {}
        assertEquals("EXPIRED", store.getRecords("a", original.type).single().status)
        failReplacement = false; waitAfterPersist = CompletableDeferred()
        val job = backgroundScope.launch { withTimeoutOrNull(10000) { repo.reviseExpired("a", original, replacement) } }
        runCurrent(); advanceTimeBy(10001); runCurrent(); job.join()
        val saved = store.getRecords("a", original.type).single()
        assertEquals(SubmissionRecord.create("a", replacement.recordId, replacement, "PENDING", replacement.type).payloadJson, saved.payloadJson)
        waitAfterPersist!!.complete(Unit); waitAfterPersist = null
        repo.reviseExpired("a", original, replacement)
        assertEquals(saved, store.getRecords("a", original.type).single())
    }
}
