package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("alice", "fixture@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private var instant = Instant.parse("2026-03-30T18:29:59Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun instant() = instant
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant, zone)
    }
    private val template = RecurringTemplate("rent", RecurringConfig("Rent", "housing", "committed", "cash", 3400000, "2026-01-31", 31), 0)
    private class Fake : RecurringRepository {
        val lists = mutableMapOf<String, MutableStateFlow<ReportingBatch<RecurringTemplate>>>()
        val histories = mutableMapOf<Triple<String, String, String>, MutableStateFlow<ReportingBatch<ConfirmedOccurrence>>>()
        val recoveries = mutableMapOf<String, MutableStateFlow<RecurringRecovery>>()
        val submissions = mutableListOf<Pair<String, RecurringOperation>>()
        val syncOwners = mutableListOf<String>()
        var saveGate: CompletableDeferred<Unit>? = null
        var oldListGate: CompletableDeferred<Unit>? = null
        var oldHistoryGate: CompletableDeferred<Unit>? = null
        var emitSaved = true
        var failNextList = false
        fun list(uid: String) = lists.getOrPut(uid) { MutableStateFlow(ReportingBatch(emptyList(), false)) }
        fun history(uid: String, id: String, start: String) = histories.getOrPut(Triple(uid, id, start)) { MutableStateFlow(ReportingBatch(emptyList(), false)) }
        fun attempts(uid: String) = recoveries.getOrPut(uid) { MutableStateFlow(RecurringRecovery()) }
        override fun templates(uid: String, limit: Int) = object : Flow<ReportingBatch<RecurringTemplate>> {
            override suspend fun collect(collector: FlowCollector<ReportingBatch<RecurringTemplate>>) {
                if (failNextList) { failNextList = false; throw java.io.IOException("Listener unavailable") }
                if (uid == "alice" && oldListGate != null) {
                    val old = list(uid).value
                    withContext(NonCancellable) { oldListGate!!.await(); collector.emit(old) }
                } else list(uid).collect(collector)
            }
        }
        override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = object : Flow<ReportingBatch<ConfirmedOccurrence>> {
            override suspend fun collect(collector: FlowCollector<ReportingBatch<ConfirmedOccurrence>>) {
                if (templateId == "rent" && oldHistoryGate != null) {
                    val old = history(uid, templateId, start).value
                    withContext(NonCancellable) { oldHistoryGate!!.await(); collector.emit(old) }
                } else history(uid, templateId, start).collect(collector)
            }
        }
        override fun recovery(uid: String) = attempts(uid)
        override suspend fun submit(uid: String, operation: RecurringOperation) {
            submissions += uid to operation
            saveGate?.await()
            if (emitSaved) attempts(uid).value = attempts(uid).value.copy(attempts = attempts(uid).value.attempts.filterNot {
                it.operation.type == operation.type && it.operation.recordId == operation.recordId
            } + RecurringAttempt(operation, "PENDING"))
        }
        override suspend fun dismiss(uid: String, operation: RecurringOperation) {
            attempts(uid).value = attempts(uid).value.copy(attempts = attempts(uid).value.attempts.filterNot { it.operation == operation })
        }
        override suspend fun synchronize(uid: String) { syncOwners += uid; awaitCancellation() }
    }
    private val repo = Fake()
    private lateinit var vm: RecurringViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo.list("alice").value = ReportingBatch(listOf(template), false)
        vm = RecurringViewModel(auth, repo, clock); store.put("recurring", vm)
    }
    @After fun teardown() { repo.saveGate?.complete(Unit); repo.oldListGate?.complete(Unit); repo.oldHistoryGate?.complete(Unit); store.clear(); Dispatchers.resetMain() }
    private fun check(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { repo.saveGate?.complete(Unit); repo.oldListGate?.complete(Unit); repo.oldHistoryGate?.complete(Unit); store.clear() }
    }
    @Test fun `AT09 review captures actual overrides and duplicate taps produce one saved immutable proposal`() = check {
        runCurrent(); vm.select(template); runCurrent()
        val slot = vm.state.value.slots.first(); vm.confirm(slot)
        assertEquals("34000.00", vm.state.value.form!!.amount)
        vm.update(vm.state.value.form!!.copy(amount = "6250.50", actualDate = "2025-12-31", categoryId = "utilities", spendingType = "discretionary",
            paymentChannelId = "primary_bank_card", note = "Actual amount"))
        vm.save(); vm.save(); runCurrent()
        val op = repo.submissions.single().second
        assertEquals(625050L, op.confirmation!!.actual.amountMinor); assertEquals(slot.dueDate, op.confirmation.dueDate)
        assertEquals("2025-12-31", op.confirmation.actual.expenseDate); assertEquals("utilities", op.confirmation.actual.categoryId)
        assertEquals(1, vm.state.value.recovery!!.attempts.size); assertNull(vm.state.value.form)
        vm.confirm(slot); assertNull(vm.state.value.form)
    }
    @Test fun `invalid zero scientific overflow and future actual dates never reach persistence`() = check {
        runCurrent(); vm.select(template); runCurrent(); vm.confirm(vm.state.value.slots.first())
        for (amount in listOf("0", "-1", "1e2", "1.001", "92233720368547758.08")) {
            vm.update(vm.state.value.form!!.copy(amount = amount)); vm.save(); runCurrent(); assertTrue(repo.submissions.isEmpty()); assertNotNull(vm.state.value.error)
        }
        vm.update(vm.state.value.form!!.copy(amount = "1", actualDate = "2026-03-31")); vm.save(); runCurrent()
        assertTrue(repo.submissions.isEmpty()); assertTrue(vm.state.value.error!!.contains("future"))
    }
    @Test fun `templates alone do not save expenses and unconfirmed ignored slots never submit automatically`() = check {
        runCurrent(); vm.select(template); runCurrent(); assertEquals(2, vm.state.value.slots.size)
        vm.closeTemplate(); runCurrent(); assertTrue(repo.submissions.isEmpty())
        vm.newTemplate(); vm.update(vm.state.value.form!!.copy(name = "Utilities", amount = "5000")); vm.save(); runCurrent()
        assertEquals("CREATE", repo.submissions.single().second.kind); assertNull(repo.submissions.single().second.confirmation)
    }
    @Test fun `pending rejected and confirmed proposals keep operation-derived feedback independent of each other`() = check {
        runCurrent(); vm.select(template); runCurrent(); vm.confirm(vm.state.value.slots.first()); vm.save(); runCurrent()
        val a = repo.submissions.single().second
        vm.confirm(vm.state.value.slots.last()); vm.save(); runCurrent()
        val b = repo.submissions.last().second
        assertEquals(2, vm.state.value.recovery!!.attempts.size)
        repo.attempts("alice").value = RecurringRecovery(listOf(RecurringAttempt(b, "REJECTED", "Verified version conflict")), acknowledgedIds = setOf(a.operationId)); runCurrent()
        assertEquals(listOf(b), vm.state.value.recovery!!.attempts.map { it.operation }); assertTrue(vm.state.value.local.isEmpty())
        vm.dismiss(vm.state.value.recovery!!.attempts.single()); runCurrent(); assertTrue(vm.state.value.recovery!!.attempts.isEmpty())
    }
    @Test fun `local timeout retains stable identity across target change and a fast acknowledgement clears only its feedback`() = check {
        runCurrent(); vm.select(template); runCurrent(); repo.saveGate = CompletableDeferred(); repo.emitSaved = false
        vm.confirm(vm.state.value.slots.first()); vm.save(); runCurrent(); advanceTimeBy(10001); runCurrent()
        val op = vm.state.value.unknown!!; vm.closeTemplate(); assertEquals(op, vm.state.value.unknown)
        repo.saveGate!!.complete(Unit); vm.retryLocalSave(); runCurrent()
        assertTrue(repo.submissions.all { it.second == op }); assertEquals(setOf(op.operationId), vm.state.value.local.keys)
        repo.attempts("alice").value = RecurringRecovery(acknowledgedIds = setOf(op.operationId)); runCurrent()
        assertTrue(vm.state.value.local.isEmpty()); assertNull(vm.state.value.unknown)
    }
    @Test fun `editing and cancelling while confirmation is pending preserves original proposal and version`() = check {
        runCurrent(); vm.select(template); runCurrent(); vm.confirm(vm.state.value.slots.first()); vm.save(); runCurrent()
        val original = repo.submissions.single().second
        vm.editTemplate(); assertNotNull(vm.state.value.form); vm.closeForm()
        vm.requestCancel(); vm.keepTemplate(); assertEquals(1, repo.submissions.size)
        vm.requestCancel(); vm.cancelTemplate(); runCurrent()
        assertEquals("CANCEL", repo.submissions.last().second.kind); assertEquals(0L, repo.submissions.last().second.expectedVersion)
        assertEquals(original, vm.state.value.recovery!!.attempts.first().operation)
    }
    @Test fun `open confirmation detects template edit or cancellation rather than silently rebasing actual payload`() = check {
        runCurrent(); vm.select(template); runCurrent(); vm.confirm(vm.state.value.slots.first())
        repo.list("alice").value = ReportingBatch(listOf(template.copy(version = 1, config = template.config.copy(anchorDay = 5))), false); runCurrent()
        vm.save(); runCurrent(); assertTrue(repo.submissions.isEmpty()); assertTrue(vm.state.value.error!!.contains("changed"))
        vm.closeForm(); repo.list("alice").value = ReportingBatch(listOf(template.copy(version = 1, isCancelled = true)), false); runCurrent()
        assertTrue(vm.state.value.slots.isEmpty()); vm.editTemplate(); assertNull(vm.state.value.form)
    }
    @Test fun `account switch clears old forms proposals errors and rejects noncancellable delayed old list`() = check {
        repo.oldListGate = CompletableDeferred(); runCurrent()
        auth.authState.value = User("bob", "other@example.com"); runCurrent()
        assertNull(vm.state.value.selected); assertTrue(vm.state.value.templates.records!!.isEmpty())
        repo.oldListGate!!.complete(Unit); runCurrent()
        assertTrue(vm.state.value.templates.records!!.isEmpty()); assertNull(vm.state.value.form); assertNull(vm.state.value.error)
        assertTrue(vm.state.value.recovery!!.attempts.isEmpty()); assertNull(vm.state.value.unknown)
    }
    @Test fun `account switch during suspended local save cannot restore old-user form or feedback`() = check {
        runCurrent(); vm.select(template); runCurrent(); vm.confirm(vm.state.value.slots.first()); repo.saveGate = CompletableDeferred(); vm.save(); runCurrent()
        auth.authState.value = User("bob", "other@example.com"); runCurrent(); repo.saveGate!!.complete(Unit); runCurrent()
        assertNull(vm.state.value.form); assertNull(vm.state.value.unknown); assertTrue(vm.state.value.local.isEmpty())
        assertNull(vm.state.value.selected); assertNull(vm.state.value.error); assertEquals("alice", repo.submissions.single().first)
    }
    @Test fun `target and month pages ignore delayed old history and retain access to oldest missed occurrences`() = check {
        val utilities = template.copy(id = "utilities", config = template.config.copy(name = "Utilities"))
        repo.list("alice").value = ReportingBatch(listOf(template, utilities), false); repo.oldHistoryGate = CompletableDeferred()
        val accepted = RecurringConfirmation("rent", "2026-01", "2026-01-31", 0, "Rent", ActualRecurringExpense(3400000, "2026-01-31", "housing", "committed", "cash"))
        repo.history("alice", "rent", "2026-01").value = ReportingBatch(listOf(ConfirmedOccurrence(accepted, "old")), false)
        runCurrent(); vm.select(template); runCurrent(); vm.select(utilities); runCurrent(); repo.oldHistoryGate!!.complete(Unit); runCurrent()
        assertEquals("utilities", vm.state.value.selectedId); assertTrue(vm.state.value.slots.all { it.confirmed == null && it.id.startsWith("utilities_") })
        val ancient = template.copy(config = template.config.copy(firstDueDate = "2024-01-31"))
        repo.list("alice").value = ReportingBatch(listOf(ancient), false); runCurrent(); vm.select(ancient); runCurrent()
        vm.page(1); runCurrent(); assertEquals("2025-01", vm.state.value.windowStart)
        vm.page(-1); runCurrent(); assertEquals("2024-01", vm.state.value.windowStart)
    }
    @Test fun `uncached missing history stays unavailable while cached template permits explicitly reconciled candidates`() = check {
        repo.history("alice", "rent", "2026-01").value = ReportingBatch(error = "Unavailable offline")
        repo.list("alice").value = ReportingBatch(listOf(template), true); runCurrent(); vm.select(template); runCurrent()
        assertNull(vm.state.value.occurrences.records); assertNotNull(vm.state.value.occurrences.error)
        assertTrue(vm.state.value.templates.fromCache); assertEquals(2, vm.state.value.slots.size)
        vm.confirm(vm.state.value.slots.first()); assertNotNull(vm.state.value.form)
        repo.attempts("alice").value = RecurringRecovery(error = "Recovery cannot be read"); runCurrent()
        assertFalse(vm.state.value.canPrepare); vm.save(); runCurrent(); assertTrue(repo.submissions.isEmpty())
    }
    @Test fun `Colombo rollover recomputes due page without reopening and preserves prior monthly IDs`() = check {
        runCurrent(); vm.select(template); runCurrent(); val previous = vm.state.value.slots.map { it.id }
        instant = Instant.parse("2026-03-30T18:30:00Z"); advanceTimeBy(60001); runCurrent()
        assertEquals("2026-03-31", vm.state.value.today); assertEquals(3, vm.state.value.slots.size)
        assertEquals(previous, vm.state.value.slots.take(2).map { it.id }); assertFalse(vm.state.value.slots.last().overdue)
    }
    @Test fun `listener errors retain known templates and reattach instead of turning missing data into an empty account`() = check {
        repo.failNextList = true; runCurrent()
        assertNull(vm.state.value.templates.records); assertNotNull(vm.state.value.templates.error)
        advanceTimeBy(5000); runCurrent(); assertEquals(listOf(template), vm.state.value.templates.records); assertNull(vm.state.value.templates.error)
        repo.failNextList = true; vm.loadMore(); runCurrent()
        assertEquals(listOf(template), vm.state.value.templates.records); assertTrue(vm.state.value.templates.fromCache); assertNotNull(vm.state.value.templates.error)
        advanceTimeBy(5000); runCurrent(); assertNull(vm.state.value.templates.error); assertEquals(listOf(template), vm.state.value.templates.records)
    }
}
