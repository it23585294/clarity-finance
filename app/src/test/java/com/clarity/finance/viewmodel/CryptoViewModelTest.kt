package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CryptoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "a@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private val repository = FakeCrypto()
    private val owners = ViewModelStore()
    private lateinit var vm: CryptoViewModel
    private val gain = CryptoResult("g", "gain", 6000000, "2026-10-01", createdAt = Timestamp(1, 0), updatedAt = Timestamp(2, 0))
    private val loss = gain.copy(id = "l", direction = "loss", amountMinor = 2500000)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = CryptoViewModel(auth, repository, Clock.fixed(Instant.parse("2026-10-25T19:00:00Z"), ZoneOffset.UTC))
        owners.put("crypto", vm)
    }
    @After fun cleanup() { owners.clear(); Dispatchers.resetMain() }

    @Test fun `empty signed in month and past year cutoffs are clear with no future years`() = runTest(dispatcher) {
        runCurrent()
        assertTrue(vm.state.value.history.isEmpty())
        assertFalse(vm.state.value.loading)
        assertEquals("0.00", vm.state.value.monthNet)
        assertEquals("2026-10-26", vm.state.value.cutoff)
        vm.changeYear(1)
        assertEquals("2026-10", vm.state.value.month)
        vm.changeYear(-1); runCurrent()
        assertEquals("2025-12-31", vm.state.value.cutoff)
        assertEquals(2025, repository.queries.last().second)
    }

    @Test fun `AT03 net and inspected editing preserve exact input and expected version`() = runTest(dispatcher) {
        runCurrent()
        repository.results.value = listOf(gain, loss)
        runCurrent()
        assertEquals("+35000.00", vm.state.value.monthNet)
        assertEquals("+35000.00", vm.state.value.ytdNet)
        vm.inspect(gain)
        assertEquals(gain, vm.state.value.selected)
        vm.edit(gain)
        assertEquals("60000.00", vm.state.value.form?.amount)
        vm.updateForm(date = "2025-12-31", direction = "loss")
        vm.save(); runCurrent()
        val operation = repository.submissions.single().second
        assertEquals("UPDATE", operation.kind)
        assertEquals(gain.updatedAt, operation.expectedUpdatedAt)
        assertEquals(gain.createdAt, operation.result.createdAt)
        assertEquals("2025-12-31", operation.result.resultDate)
        assertEquals("loss", operation.result.direction)
    }

    @Test fun `date edit across years moves contribution and deletion removes it once`() = runTest(dispatcher) {
        runCurrent()
        repository.results.value = listOf(gain, loss); runCurrent()
        repository.results.value = listOf(gain.copy(resultDate = "2025-12-31"), loss); runCurrent()
        assertEquals("-25000.00", vm.state.value.monthNet)
        assertEquals("-25000.00", vm.state.value.ytdNet)
        vm.changeYear(-1); runCurrent()
        vm.changeMonth(2); runCurrent()
        assertEquals("2025-12", vm.state.value.month)
        assertEquals("+60000.00", vm.state.value.monthNet)
        repository.results.value = listOf(loss); runCurrent()
        assertEquals("0.00", vm.state.value.monthNet)
        repository.results.value = listOf(loss); runCurrent()
        assertEquals("0.00", vm.state.value.ytdNet)
    }

    @Test fun `new default date uses Colombo and invalid forms cannot submit`() = runTest(dispatcher) {
        runCurrent(); vm.add()
        assertEquals("2026-10-26", vm.state.value.form?.date)
        listOf("", "0", "-1", "1e3", "1.001", "92233720368547758.08").forEach {
            vm.updateForm(amount = it); vm.save(); runCurrent()
            assertNotNull(vm.state.value.form?.error)
        }
        vm.updateForm(amount = "1", date = "2026-10-27"); vm.save(); runCurrent()
        assertTrue(repository.submissions.isEmpty())
        vm.updateForm(date = "2026-02-30"); vm.save(); runCurrent()
        assertTrue(repository.submissions.isEmpty())
    }

    @Test fun `duplicate taps and timed out local saves retain exact retry identity`() = runTest(dispatcher) {
        runCurrent(); vm.add(); vm.updateForm(amount = "60000")
        repository.gate = CompletableDeferred()
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, repository.submissions.size)
        advanceTimeBy(10001); runCurrent()
        assertFalse(vm.state.value.busy)
        val original = repository.submissions.single().second
        vm.updateForm(amount = "2")
        assertEquals("60000", vm.state.value.form?.amount)
        repository.gate!!.complete(Unit)
        vm.save(); runCurrent()
        assertEquals(original, repository.submissions.last().second)
        assertNull(vm.state.value.form)
        // This fake emits no recovery snapshot: local acceptance alone must not fabricate a pending banner.
        assertNull(vm.state.value.message)
    }

    @Test fun `switching account clears records form and ignores an old suspended completion`() = runTest(dispatcher) {
        runCurrent(); repository.results.value = listOf(gain); runCurrent()
        vm.add(); vm.updateForm(amount = "1")
        repository.gate = CompletableDeferred()
        repository.ignoreCancellation = true
        vm.save(); runCurrent()
        auth.authState.value = User("b", "b@example.com"); runCurrent()
        assertNull(vm.state.value.form)
        assertTrue(vm.state.value.history.isEmpty())
        assertNull(vm.state.value.message)
        repository.gate!!.complete(Unit); runCurrent()
        assertNull(vm.state.value.message)
        assertEquals(listOf("a"), repository.submissions.map { it.first })
        assertEquals("b", repository.queries.last().first)
    }

    @Test fun `pending failed and cached records are distinguished and never falsely confirmed`() = runTest(dispatcher) {
        runCurrent()
        repository.results.value = listOf(gain.copy(sync = CryptoSync.PENDING), loss.copy(sync = CryptoSync.FAILED, error = "Denied"))
        runCurrent()
        assertEquals(2, vm.state.value.unsettled.size)
        assertEquals("0.00", vm.state.value.monthNet)
        vm.edit(vm.state.value.history.first())
        assertNull(vm.state.value.form)
        repository.results.value = listOf(loss.copy(sync = CryptoSync.CACHED)); runCurrent()
        assertEquals("-25000.00", vm.state.value.monthNet)
    }

    @Test fun `delete double taps submit once and dismissing its dialog does not submit`() = runTest(dispatcher) {
        runCurrent(); vm.requestDelete(gain); vm.dismissDelete(); vm.confirmDelete(); runCurrent()
        assertTrue(repository.submissions.isEmpty())
        vm.requestDelete(gain); vm.confirmDelete(); vm.confirmDelete(); runCurrent()
        assertEquals(1, repository.submissions.size)
        assertEquals("DELETE", repository.submissions.single().second.kind)
    }

    @Test fun `listener failure is visible and retry creates a fresh owner listener`() = runTest(dispatcher) {
        runCurrent()
        repository.failListener = true
        vm.retryLoad(); runCurrent()
        assertEquals("Listener denied", vm.state.value.error)
        repository.failListener = false
        vm.retryLoad(); runCurrent()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `overflow never wraps totals or hides the records needed for correction`() = runTest(dispatcher) {
        runCurrent()
        repository.results.value = listOf(gain.copy(amountMinor = Long.MAX_VALUE), gain.copy(id = "extra", amountMinor = 1))
        runCurrent()
        assertEquals("Unavailable", vm.state.value.monthNet)
        assertTrue(vm.state.value.error!!.contains("integer range"))
        assertEquals(2, vm.state.value.history.size)
        repository.results.value = listOf(loss); runCurrent()
        assertEquals("-25000.00", vm.state.value.monthNet)
        assertNull(vm.state.value.error)
    }

    @Test fun `resuming after Colombo new year refreshes the selected past year cutoff`() = runTest(dispatcher) {
        var instant = Instant.parse("2026-12-31T18:29:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = Clock.fixed(instant, zone)
            override fun instant() = instant
        }
        val boundary = CryptoViewModel(auth, repository, clock)
        owners.put("boundary", boundary)
        runCurrent()
        assertEquals("2026-12", boundary.state.value.month)
        instant = Instant.parse("2027-01-01T18:30:00Z")
        boundary.refreshReportingDate(); runCurrent()
        assertEquals("2026-12-31", boundary.state.value.cutoff)
        assertTrue(boundary.state.value.canNextYear)
        assertEquals("2026-12-31", repository.queries.last().third)
    }

    @Test fun `failed backdated edit and pending delete retain confirmed baseline until acknowledged`() = runTest(dispatcher) {
        runCurrent()
        repository.confirmed = listOf(gain)
        val proposal = gain.copy(direction = "loss", resultDate = "2025-12-31", sync = CryptoSync.FAILED)
        repository.attempts = listOf(CryptoOperation("edit", "UPDATE", proposal, gain.updatedAt))
        repository.results.value = listOf(proposal); runCurrent()
        assertEquals("+60000.00", vm.state.value.monthNet)
        assertEquals(listOf(gain), vm.state.value.history)
        assertEquals(listOf(proposal), vm.state.value.unsettled)
        vm.inspect(gain)
        assertTrue(vm.state.value.selectedHasAttempt)
        vm.edit(gain); vm.requestDelete(gain)
        assertNull(vm.state.value.form)
        assertNull(vm.state.value.deleteTarget)
        repository.attempts = listOf(CryptoOperation("delete", "DELETE", gain, gain.updatedAt))
        repository.results.value = listOf(gain.copy(sync = CryptoSync.PENDING, deleting = true)); runCurrent()
        assertEquals("+60000.00", vm.state.value.monthNet)
        repository.confirmed = emptyList(); repository.attempts = emptyList()
        repository.results.value = emptyList(); runCurrent()
        assertEquals("0.00", vm.state.value.monthNet)
    }

    private class FakeCrypto : CryptoRepository {
        val results = MutableStateFlow<List<CryptoResult>>(emptyList())
        val submissions = mutableListOf<Pair<String, CryptoOperation>>()
        val queries = mutableListOf<Triple<String, Int, String>>()
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false
        var failListener = false
        var confirmed: List<CryptoResult>? = null
        var attempts: List<CryptoOperation> = emptyList()
        override fun observe(uid: String, year: Int, cutoff: String): Flow<CryptoSnapshot> {
            queries += Triple(uid, year, cutoff)
            return flow {
                if (failListener) error("Listener denied")
                results.collect { rows -> emit(CryptoSnapshot(results = if (uid == "a") rows.filter {
                    it.sync in listOf(CryptoSync.PENDING, CryptoSync.FAILED) ||
                        (it.resultDate >= "$year-01-01" && it.resultDate <= cutoff)
                } else emptyList(), fromCache = false, loaded = true,
                    confirmedResults = if (uid == "a") confirmed?.filter { it.resultDate >= "$year-01-01" && it.resultDate <= cutoff } else emptyList(),
                    attempts = if (uid == "a") attempts else emptyList())) }
            }
        }
        override suspend fun submit(uid: String, operation: CryptoOperation) {
            submissions += uid to operation
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
        }
        override suspend fun retry(uid: String, operation: CryptoOperation) = submit(uid, operation)
        override suspend fun dismissRejected(uid: String, operation: CryptoOperation) = Unit
    }
}
