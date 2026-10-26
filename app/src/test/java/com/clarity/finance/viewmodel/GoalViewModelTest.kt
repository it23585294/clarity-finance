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
class GoalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "test@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private val config = GoalConfig("Fixture", 49000000, 1120000, "2026-01-31", "2027-01-31")
    private val confirmed = GoalSnapshot(SavingsGoal(config, 1120000, 0, "original"), emptyList(), true, false)
    private val repo = Fake()
    private lateinit var vm: GoalViewModel
    private var instant = Instant.parse("2026-01-31T00:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant, zone)
        override fun instant() = instant
    }
    private class Fake : GoalRepository {
        val sources = mutableMapOf<String, MutableStateFlow<GoalSnapshot>>()
        fun source(uid: String) = sources.getOrPut(uid) { MutableStateFlow(GoalSnapshot(known = true, entries = emptyList(), fromCache = false)) }
        val submissions = mutableListOf<Pair<String, GoalOperation>>()
        var gate: CompletableDeferred<Unit>? = null
        var staleGate: CompletableDeferred<Unit>? = null
        var synchronization = 0
        var emitSaved = true
        override fun observe(uid: String) = object : Flow<GoalSnapshot> {
            override suspend fun collect(collector: FlowCollector<GoalSnapshot>) {
                if (uid == "a" && staleGate != null) withContext(NonCancellable) { staleGate!!.await(); collector.emit(source(uid).value) }
                else source(uid).collect(collector)
            }
        }
        override suspend fun submit(uid: String, operation: GoalOperation) {
            submissions += uid to operation
            gate?.await()
            if (emitSaved) source(uid).value = source(uid).value.copy(attempts = listOf(GoalAttempt(operation, "PENDING")))
        }
        override suspend fun synchronize(uid: String) { synchronization++; awaitCancellation() }
        override suspend fun retry(uid: String, operation: GoalOperation) { source(uid).value = source(uid).value.copy(attempts = listOf(GoalAttempt(operation, "PENDING"))) }
        override suspend fun dismissRejected(uid: String, operation: GoalOperation) { source(uid).value = source(uid).value.copy(attempts = emptyList()) }
        override suspend fun reviseExpired(uid: String, original: GoalOperation, replacement: GoalOperation) {
            check(source(uid).value.attempts.single().operation == original)
            submit(uid, replacement)
        }
    }
    private fun runGoalTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { repo.gate?.complete(Unit); repo.staleGate?.complete(Unit); store.clear() }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo.source("a").value = confirmed
        vm = GoalViewModel(auth, repo, clock)
        store.put("goal", vm)
    }
    @After fun teardown() { repo.gate?.complete(Unit); repo.staleGate?.complete(Unit); store.clear(); Dispatchers.resetMain() }
    @Test fun `pending rejected and confirmed mutations retain truthful savings and operation feedback`() = runGoalTest {
        runCurrent(); vm.openEntry(); vm.update(vm.state.value.form!!.copy(amount = "10000")); vm.save(); vm.save(); runCurrent()
        assertEquals(1, repo.submissions.size)
        assertEquals(1120000L, vm.state.value.progress!!.savedMinor)
        assertEquals("PENDING", vm.state.value.snapshot.attempts.single().status)
        val op = repo.submissions.single().second
        repo.source("a").value = confirmed.copy(attempts = listOf(GoalAttempt(op, "REJECTED", "Conflict"))); runCurrent()
        assertEquals(1120000L, vm.state.value.progress!!.savedMinor); assertTrue(vm.state.value.blocked)
        vm.dismiss(vm.state.value.snapshot.attempts.single()); runCurrent()
        assertFalse(vm.state.value.blocked)
        repo.source("a").value = confirmed.copy(goal = confirmed.goal!!.copy(savedMinor = 2120000, version = 1, lastOperationId = op.operationId),
            entries = listOf(op.entry!!)); runCurrent()
        assertEquals(2120000L, vm.state.value.progress!!.savedMinor); assertTrue(vm.state.value.snapshot.attempts.isEmpty())
    }
    @Test fun `withdrawal correction and deletion cannot erase spent contributions`() = runGoalTest {
        val c = SavingsEntry("c", "contribution", 10000, "2026-01-31")
        val w = SavingsEntry("w", "withdrawal", 9999, "2026-01-31")
        repo.source("a").value = confirmed.copy(goal = confirmed.goal!!.copy(config = config.copy(openingMinor = 0), savedMinor = 1), entries = listOf(c, w))
        runCurrent(); vm.requestDelete(c); vm.confirmDelete(); runCurrent()
        assertTrue(vm.state.value.error!!.contains("negative")); assertTrue(repo.submissions.isEmpty())
        vm.cancelDelete(); vm.openEntry(c); vm.update(vm.state.value.form!!.copy(amount = "99.98")); vm.save(); runCurrent()
        assertTrue(repo.submissions.isEmpty()); assertEquals(1L, vm.state.value.progress!!.savedMinor)
    }
    @Test fun `uncached unknown is unavailable but cached confirmed balance remains labelled`() = runGoalTest {
        repo.source("a").value = GoalSnapshot(); runCurrent()
        assertNull(vm.state.value.progress); assertFalse(vm.state.value.canChange)
        advanceTimeBy(10001); runCurrent(); assertNotNull(vm.state.value.snapshot.error)
        repo.source("a").value = confirmed.copy(fromCache = true); runCurrent()
        assertEquals(1120000L, vm.state.value.progress!!.savedMinor); assertTrue(vm.state.value.snapshot.fromCache)
        assertNull(vm.state.value.snapshot.error)
    }
    @Test fun `account switch clears forms feedback and rejects delayed old listener`() = runGoalTest {
        runCurrent(); vm.openGoal(); assertNotNull(vm.state.value.form)
        auth.authState.value = User("b", "b@example.com"); runCurrent()
        assertNull(vm.state.value.form); assertNull(vm.state.value.progress); assertNull(vm.state.value.error)
        repo.source("a").value = confirmed.copy(error = "Old private error"); runCurrent()
        assertNull(vm.state.value.snapshot.goal); assertNull(vm.state.value.snapshot.error)
    }
    @Test fun `non cancellable delayed old account callback cannot restore private goal`() = runGoalTest {
        repo.staleGate = CompletableDeferred(); runCurrent()
        auth.authState.value = User("b", "b@example.com"); runCurrent()
        assertNull(vm.state.value.snapshot.goal)
        repo.staleGate!!.complete(Unit); runCurrent()
        assertNull(vm.state.value.snapshot.goal); assertNull(vm.state.value.form); assertNull(vm.state.value.error)
    }
    @Test fun `cancelled local save retains identity for stable retry after timeout`() = runGoalTest {
        runCurrent(); repo.gate = CompletableDeferred()
        vm.openEntry(); vm.update(vm.state.value.form!!.copy(amount = "1")); vm.save(); runCurrent()
        advanceTimeBy(10001); runCurrent()
        val unknown = vm.state.value.unknown!!
        assertTrue(vm.state.value.blocked); assertEquals(1120000L, vm.state.value.progress!!.savedMinor)
        repo.gate!!.complete(Unit); vm.retryUnknown(); runCurrent()
        assertTrue(repo.submissions.all { it.second == unknown }); assertNull(vm.state.value.unknown)
        assertEquals(1, vm.state.value.snapshot.attempts.size)
    }
    @Test fun `dashboard goal observer never starts recovery or touches ordinary financial repositories`() = runGoalTest {
        runCurrent(); val previous = repo.synchronization
        val card = GoalCardViewModel(auth, repo); store.put("card", card); runCurrent()
        assertEquals(previous, repo.synchronization)
        assertEquals(1120000L, card.state.value.snapshot.goal!!.savedMinor)
    }
    @Test fun `goal opening edit preserves start and validates resulting ledger balance`() = runGoalTest {
        repo.source("a").value = confirmed.copy(goal = confirmed.goal!!.copy(savedMinor = 100))
        runCurrent(); vm.openGoal(); vm.update(vm.state.value.form!!.copy(opening = "0")); vm.save(); runCurrent()
        assertTrue(repo.submissions.isEmpty()); assertTrue(vm.state.value.error!!.contains("negative"))
        vm.update(vm.state.value.form!!.copy(opening = "11200", target = "500000")); vm.save(); runCurrent()
        assertEquals(config.startDate, repo.submissions.single().second.goal!!.startDate)
    }
    @Test fun `open draft and deletion reject changed target version rather than silently rebasing`() = runGoalTest {
        runCurrent(); vm.openGoal()
        repo.source("a").value = confirmed.copy(goal = confirmed.goal!!.copy(version = 1, savedMinor = 1130000)); runCurrent()
        vm.save(); runCurrent(); assertTrue(repo.submissions.isEmpty()); assertTrue(vm.state.value.error!!.contains("changed"))
        vm.closeForm()
        val entry = SavingsEntry("c", "contribution", 100, "2026-01-31")
        repo.source("a").value = confirmed.copy(entries = listOf(entry)); runCurrent(); vm.requestDelete(entry)
        repo.source("a").value = confirmed.copy(goal = confirmed.goal!!.copy(version = 1), entries = listOf(entry)); runCurrent()
        vm.confirmDelete(); runCurrent(); assertTrue(repo.submissions.isEmpty()); assertTrue(vm.state.value.error!!.contains("changed"))
    }
    @Test fun `Colombo rollover recalculates opportunities without reopening`() = runGoalTest {
        runCurrent(); assertEquals(12, vm.state.value.progress!!.opportunities)
        instant = Instant.parse("2026-02-27T18:30:00Z"); vm.refreshDate()
        assertEquals(12, vm.state.value.progress!!.opportunities)
        instant = Instant.parse("2026-02-28T18:30:00Z"); advanceTimeBy(60001); runCurrent()
        assertEquals(11, vm.state.value.progress!!.opportunities); assertEquals("2026-03-31", vm.state.value.progress!!.nextDate)
    }
    @Test fun `local persistence before observer emission blocks duplicate taps and receipt acknowledgement clears feedback`() = runGoalTest {
        runCurrent(); repo.emitSaved = false
        vm.openEntry(); vm.update(vm.state.value.form!!.copy(amount = "1")); vm.save(); runCurrent()
        val op = repo.submissions.single().second
        assertNotNull(vm.state.value.localPending); assertTrue(vm.state.value.blocked)
        vm.openEntry(); vm.save(); runCurrent(); assertEquals(1, repo.submissions.size)
        // A fast accepted operation may disappear from recovery before its pending emission is observed.
        repo.source("a").value = confirmed.copy(acknowledgedOperationId = op.operationId); runCurrent()
        assertNull(vm.state.value.localPending); assertNull(vm.state.value.unknown); assertFalse(vm.state.value.blocked)
    }
    @Test fun `uncertain and legacy failed proposals cannot be dismissed through the ViewModel`() = runGoalTest {
        runCurrent()
        val op = GoalOperation("uncertain", "EDIT", 0, goal = config)
        for (status in listOf("UNCERTAIN", "FAILED")) {
            repo.source("a").value = confirmed.copy(attempts = listOf(GoalAttempt(op, status, "Access unavailable"))); runCurrent()
            val attempt = vm.state.value.snapshot.attempts.single()
            assertTrue(attempt.isUncertain); assertFalse(attempt.isRejected)
            vm.dismiss(attempt); runCurrent()
            assertEquals(1, vm.state.value.snapshot.attempts.size)
            assertEquals(1120000L, vm.state.value.progress!!.savedMinor)
        }
    }
    @Test fun `server expired proposal opens deadline only revision preserving its values and start date`() = runGoalTest {
        val original = GoalOperation("expired", "CREATE", goal = config.copy(startDate = "2026-01-01", deadline = "2026-01-15"))
        repo.source("a").value = GoalSnapshot(known = true, entries = emptyList(), fromCache = false, attempts = listOf(GoalAttempt(original, "EXPIRED")))
        runCurrent(); vm.reviseDeadline(vm.state.value.snapshot.attempts.single())
        assertFalse(vm.state.value.canChange); assertTrue(vm.state.value.canSaveForm)
        assertEquals("11200.00", vm.state.value.form!!.opening); assertEquals(original.goal!!.startDate, vm.state.value.form!!.startDate)
        vm.save(); runCurrent(); assertTrue(repo.submissions.isEmpty()); assertEquals("EXPIRED", vm.state.value.snapshot.attempts.single().status)
        vm.update(vm.state.value.form!!.copy(deadline = "2026-12-31")); vm.save(); vm.save(); runCurrent()
        val replacement = repo.submissions.single().second
        assertNotEquals(original.operationId, replacement.operationId)
        assertEquals(original.goal, replacement.goal!!.copy(deadline = original.goal.deadline))
        assertEquals(original.expectedVersion, replacement.expectedVersion)
        assertEquals("PENDING", vm.state.value.snapshot.attempts.single().status)
    }
    @Test fun `cancelled local deadline revision retains its source and stable replacement identity for retry`() = runGoalTest {
        val original = GoalOperation("expired", "CREATE", goal = config.copy(startDate = "2026-01-01", deadline = "2026-01-15"))
        repo.source("a").value = GoalSnapshot(known = true, entries = emptyList(), fromCache = false, attempts = listOf(GoalAttempt(original, "EXPIRED")))
        runCurrent(); vm.reviseDeadline(vm.state.value.snapshot.attempts.single()); vm.update(vm.state.value.form!!.copy(deadline = "2026-12-31"))
        repo.gate = CompletableDeferred(); vm.save(); runCurrent(); advanceTimeBy(10001); runCurrent()
        val uncertain = vm.state.value.unknown!!
        assertEquals(original, vm.state.value.replacementSource); assertEquals("EXPIRED", vm.state.value.snapshot.attempts.single().status)
        vm.closeForm(); repo.gate!!.complete(Unit); vm.retryUnknown(); runCurrent()
        assertTrue(repo.submissions.all { it.second == uncertain }); assertNull(vm.state.value.unknown); assertNull(vm.state.value.replacementSource)
        assertEquals(uncertain, vm.state.value.snapshot.attempts.single().operation)
    }
}
