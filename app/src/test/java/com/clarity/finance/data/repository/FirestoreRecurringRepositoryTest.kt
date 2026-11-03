package com.clarity.finance.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRecurringRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val config = RecurringConfig("Rent", "housing", "committed", "cash", 3400000, "2026-01-31", 31)
    private fun confirmation(id: String = "rent", month: String = "2026-01", amount: Long = 3400000) = RecurringOperation("pay-$id-$month", "CONFIRM", id, 0,
        confirmation = RecurringConfirmation(id, month, "$month-31", 0, "Rent", ActualRecurringExpense(amount, "2026-01-31", "housing", "committed", "cash")))
    private class Remote : RecurringRemote {
        var offline = true
        var gate: CompletableDeferred<Unit>? = null
        var outcome: RecurringOutcome? = null
        val receipts = mutableMapOf<Pair<String, String>, RecurringOperation>()
        val calls = mutableListOf<Pair<String, RecurringOperation>>()
        var mutations = 0
        override fun templates(uid: String, limit: Int) = MutableStateFlow(ReportingBatch<RecurringTemplate>(emptyList(), false))
        override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = MutableStateFlow(ReportingBatch<ConfirmedOccurrence>(emptyList(), false))
        override suspend fun apply(uid: String, operation: RecurringOperation): RecurringOutcome {
            calls += uid to operation
            if (offline) throw IOException("Offline")
            gate?.await()
            outcome?.let { return it }
            val key = uid to operation.recordId
            val previous = receipts[key]
            if (previous != null) return if (previous.payload() == operation.payload()) RecurringOutcome.Confirmed else RecurringOutcome.Rejected("Immutable accepted payload conflict")
            receipts[key] = operation; mutations++; return RecurringOutcome.Confirmed
        }
    }
    @Test fun `three immutable offline occurrences survive DataStore close restart and reconcile once by UID type and record`() = runTest {
        val file = temp.newFile("restart.preferences_pb")
        val diskScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = diskScope, produceFile = { file }))
        val remote = Remote(); var repo = FirestoreRecurringRepository(remote, store)
        val proposals = listOf(confirmation(), confirmation("utilities", amount = 625050), confirmation("insurance", amount = 250000))
        val legacy = SubmissionRecord("alice", "legacy", "{}", "PENDING")
        store.saveRecord(legacy)
        for (op in proposals) { repo.submit("alice", op); repo.submit("alice", op) }
        repo.submit("bob", proposals.first())
        assertEquals(3, repo.recovery("alice").first().attempts.size); assertTrue(remote.calls.isEmpty())
        val payloads = store.getRecords("alice", "RECURRING_CONFIRM").associate { it.recordId to it.payloadJson }
        diskScope.cancel(); diskScope.coroutineContext[Job]!!.join()
        store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }))
        repo = FirestoreRecurringRepository(remote, store); remote.offline = false; remote.gate = CompletableDeferred()
        val sync = backgroundScope.launch { repo.synchronize("alice") }; runCurrent()
        assertEquals(0, remote.mutations); assertEquals(payloads, store.getRecords("alice", "RECURRING_CONFIRM").associate { it.recordId to it.payloadJson })
        remote.gate!!.complete(Unit); runCurrent()
        assertEquals(3, remote.mutations); assertTrue(store.getRecords("alice", "RECURRING_CONFIRM").isEmpty())
        assertEquals(1, store.getRecords("bob", "RECURRING_CONFIRM").size); assertEquals(listOf(legacy), store.getRecords("alice", "EXPENSE"))
        assertTrue(remote.calls.all { it.first == "alice" }); sync.cancelAndJoin()
    }
    @Test fun `timeout A then access error B cannot dismiss earlier outstanding operation and late commit reconciles once`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("late.preferences_pb") }))
        val commit = CompletableDeferred<Unit>(); val op = confirmation(); var accepted: RecurringOperation? = null
        var mutations = 0; var calls = 0
        val remote = object : RecurringRemote {
            override fun templates(uid: String, limit: Int) = emptyFlow<ReportingBatch<RecurringTemplate>>()
            override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = emptyFlow<ReportingBatch<ConfirmedOccurrence>>()
            override suspend fun apply(uid: String, operation: RecurringOperation): RecurringOutcome {
                assertEquals(op, operation); calls++
                when (calls) {
                    1 -> { backgroundScope.launch { commit.await(); accepted = operation; mutations++ }; awaitCancellation() }
                    2 -> throw IOException("Account/access denied before reconciliation")
                    else -> { check(accepted == operation); return RecurringOutcome.Confirmed }
                }
            }
        }
        var repo = FirestoreRecurringRepository(remote, store); repo.submit("alice", op)
        val payload = store.getRecords("alice", op.type).single().payloadJson
        var sync = backgroundScope.launch { repo.synchronize("alice") }; runCurrent(); advanceTimeBy(10001); runCurrent()
        assertEquals(0, mutations); sync.cancelAndJoin()
        repo = FirestoreRecurringRepository(remote, store)
        sync = backgroundScope.launch { repo.synchronize("alice") }; runCurrent()
        assertEquals(2, calls); assertEquals("UNCERTAIN", store.getRecords("alice", op.type).single().status)
        assertEquals(payload, store.getRecords("alice", op.type).single().payloadJson)
        try { repo.dismiss("alice", op); fail() } catch (_: IllegalStateException) {}
        commit.complete(Unit); runCurrent(); advanceTimeBy(5000); runCurrent()
        assertEquals(1, mutations); assertTrue(store.getRecords("alice", op.type).isEmpty()); assertEquals(3, calls); sync.cancelAndJoin()
    }
    @Test fun `only proven rejection can be dismissed and corrected with a new immutable proposal`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("reject.preferences_pb") }))
        val remote = Remote().apply { offline = false; outcome = RecurringOutcome.Rejected("Verified stale template version") }
        val repo = FirestoreRecurringRepository(remote, store); val op = confirmation(); repo.submit("alice", op)
        val sync = backgroundScope.launch { repo.synchronize("alice") }; runCurrent()
        assertTrue(repo.recovery("alice").first().attempts.single().rejected)
        try { repo.submit("alice", op.copy(operationId = "different")); fail() } catch (_: IllegalStateException) {}
        repo.dismiss("alice", op)
        repo.submit("alice", op.copy(operationId = "corrected", confirmation = op.confirmation!!.copy(actual = op.confirmation.actual.copy(amountMinor = 1))))
        assertEquals("PENDING", store.getRecords("alice", op.type).single().status); sync.cancelAndJoin()
    }
    @Test fun `pending confirmation does not trap template editing or cancellation but template mutation payload stays immutable`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("edit.preferences_pb") }))
        val repo = FirestoreRecurringRepository(Remote(), store)
        val confirm = confirmation(); repo.submit("alice", confirm)
        val edit = RecurringOperation("edit", "EDIT", "rent", 0, config.copy(anchorDay = 5))
        repo.submit("alice", edit)
        try { repo.submit("alice", RecurringOperation("cancel", "CANCEL", "rent", 0, config)); fail() } catch (_: IllegalStateException) {}
        assertEquals(2, repo.recovery("alice").first().attempts.size)
        assertEquals(confirm, repo.recovery("alice").first().attempts.single { it.operation.kind == "CONFIRM" }.operation)
    }
    @Test fun `cancelled remote await propagates while legacy failures stay uncertain rather than dismissible`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("cancel.preferences_pb") }))
        val remote = Remote().apply { offline = false; gate = CompletableDeferred() }
        val repo = FirestoreRecurringRepository(remote, store); val op = confirmation()
        store.saveOperation(SubmissionRecord.create("alice", op.recordId, op, "FAILED", op.type))
        try { repo.dismiss("alice", op); fail() } catch (_: IllegalStateException) {}
        val sync = backgroundScope.launch { repo.synchronize("alice") }; runCurrent(); sync.cancelAndJoin()
        assertTrue(sync.isCancelled); assertEquals("UNCERTAIN", store.getRecords("alice", op.type).single().status)
    }
    @Test fun `confirmed outcome with local cleanup error is retained and repeats bookkeeping without another mutation`() = runTest {
        val actual = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temp.newFile("cleanup.preferences_pb") })
        var fail = false
        val disk = object : DataStore<Preferences> {
            override val data = actual.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (fail) throw IOException("Disk unavailable")
                return actual.updateData(transform)
            }
        }
        val store = SubmissionRecoveryStore(disk); var calls = 0
        val remote = object : RecurringRemote {
            override fun templates(uid: String, limit: Int) = emptyFlow<ReportingBatch<RecurringTemplate>>()
            override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = emptyFlow<ReportingBatch<ConfirmedOccurrence>>()
            override suspend fun apply(uid: String, operation: RecurringOperation): RecurringOutcome { calls++; fail = true; return RecurringOutcome.Confirmed }
        }
        val repo = FirestoreRecurringRepository(remote, store); val op = confirmation(); repo.submit("alice", op)
        val sync = backgroundScope.launch { repo.synchronize("alice") }; runCurrent()
        val result = repo.recovery("alice").first { it.error != null }
        assertTrue(op.operationId in result.acknowledgedIds); assertEquals(1, result.attempts.size)
        assertNull(repo.recovery("bob").first().error)
        fail = false; advanceTimeBy(5000); runCurrent(); assertEquals(1, calls)
        assertTrue(store.getRecords("alice", op.type).isEmpty()); sync.cancelAndJoin()
    }
}
