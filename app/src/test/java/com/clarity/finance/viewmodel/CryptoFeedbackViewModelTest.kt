package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CryptoFeedbackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "a@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private val repository = FeedbackRepository()
    private val store = ViewModelStore()
    private lateinit var vm: CryptoViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = CryptoViewModel(auth, repository, Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC))
        store.put("crypto", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun save() { vm.add(); vm.updateForm(amount = "100"); vm.save() }
    private fun confirmed(op: CryptoOperation) = op.result.copy(sync = CryptoSync.CONFIRMED,
        lastOperationId = op.operationId, createdAt = Timestamp(1, 0), updatedAt = Timestamp(2, 0))
    private fun pending(op: CryptoOperation) = op.result.copy(sync = CryptoSync.PENDING, deleting = op.kind == "DELETE")
    private fun rejected(op: CryptoOperation) = op.result.copy(sync = CryptoSync.FAILED, error = "Changed elsewhere")
    private fun assertAwaiting() = assertTrue(vm.state.value.message.orEmpty().contains("awaiting server confirmation", ignoreCase = true))

    @Test fun `pending feedback disappears when its operation is confirmed`() = runTest(dispatcher) {
        runCurrent(); save(); runCurrent()
        val op = repository.submissions.single()
        repository.emit(listOf(pending(op)), listOf(op)); runCurrent()
        assertAwaiting()
        repository.emit(listOf(confirmed(op))); runCurrent()
        assertNull(vm.state.value.message)
    }

    @Test fun `pending becomes rejected feedback rather than continuing to await confirmation`() = runTest(dispatcher) {
        runCurrent(); save(); runCurrent()
        val op = repository.submissions.single()
        repository.emit(listOf(pending(op)), listOf(op)); runCurrent()
        assertAwaiting()
        repository.emit(listOf(rejected(op)), listOf(op)); runCurrent()
        assertTrue(vm.state.value.message.orEmpty().contains("rejected", ignoreCase = true))
        assertFalse(vm.state.value.message.orEmpty().contains("awaiting", ignoreCase = true))
        assertEquals("Changed elsewhere", vm.state.value.unsettled.single().error)
    }

    @Test fun `matching server confirmation clears pending feedback before recovery cleanup and never hides a later operation`() = runTest(dispatcher) {
        runCurrent(); save(); runCurrent()
        val op = repository.submissions.single()
        repository.emit(listOf(pending(op)), listOf(op)); runCurrent()
        assertAwaiting()
        repository.emit(listOf(pending(op)), listOf(op), confirmedRows = listOf(confirmed(op))); runCurrent()
        assertNull(vm.state.value.message)
        repository.emit(listOf(pending(op)), listOf(op), fromCache = true,
            confirmedRows = listOf(confirmed(op).copy(sync = CryptoSync.CACHED))); runCurrent()
        assertNull(vm.state.value.message) // A cache transition cannot undo known confirmation.
        val later = op.copy(operationId = "later-operation")
        repository.emit(listOf(pending(later)), listOf(later), confirmedRows = listOf(confirmed(op))); runCurrent()
        assertAwaiting() // Same record ID, different operation ID: still unresolved.
    }

    @Test fun `completing or dismissing one operation leaves feedback for every other unresolved operation`() = runTest(dispatcher) {
        runCurrent(); save(); runCurrent()
        val first = repository.submissions.single()
        repository.emit(listOf(pending(first)), listOf(first)); runCurrent()
        vm.add()
        assertAwaiting() // Opening another form must not clear the first operation's feedback.
        vm.updateForm(amount = "200"); vm.save(); runCurrent()
        val second = repository.submissions.last()
        repository.emit(listOf(pending(first), pending(second)), listOf(first, second)); runCurrent()
        repository.emit(listOf(confirmed(first), pending(second)), listOf(second)); runCurrent()
        assertAwaiting()
        assertEquals(listOf(second.result.id), vm.state.value.unsettled.map { it.id })
        val third = second.copy(operationId = "third-op", result = second.result.copy(id = "third"))
        repository.emit(listOf(confirmed(first), rejected(second), pending(third)), listOf(second, third)); runCurrent()
        assertAwaiting()
        assertTrue(vm.state.value.message.orEmpty().contains("rejected", ignoreCase = true))
        vm.dismissRejected(rejected(second)); runCurrent()
        assertAwaiting()
        assertFalse(vm.state.value.message.orEmpty().contains("rejected", ignoreCase = true))
        repository.emit(listOf(confirmed(first), confirmed(third))); runCurrent()
        assertNull(vm.state.value.message)
    }

    @Test fun `confirmation before a suspended save returns cannot reinstall a stale pending banner`() = runTest(dispatcher) {
        runCurrent(); repository.gate = CompletableDeferred()
        save(); runCurrent()
        val op = repository.submissions.single()
        repository.emit(listOf(pending(op)), listOf(op)); runCurrent()
        repository.emit(listOf(confirmed(op))); runCurrent()
        assertNull(vm.state.value.message)
        repository.gate!!.complete(Unit); runCurrent()
        assertNull(vm.state.value.message)
        assertNull(vm.state.value.form)
    }

    @Test fun `reconciled deletion clears feedback without requiring a replacement document`() = runTest(dispatcher) {
        runCurrent()
        val result = CryptoResult("deleted", "gain", 100, "2026-10-01", updatedAt = Timestamp(2, 0))
        repository.emit(listOf(result)); runCurrent()
        vm.requestDelete(result); vm.confirmDelete(); runCurrent()
        val op = repository.submissions.single()
        repository.emit(listOf(pending(op)), listOf(op)); runCurrent()
        assertAwaiting()
        repository.emit(emptyList()); runCurrent()
        assertNull(vm.state.value.message)
        assertTrue(vm.state.value.unsettled.isEmpty())
    }

    @Test fun `timeout remains unknown through unrelated or empty snapshots until matching evidence arrives`() = runTest(dispatcher) {
        runCurrent(); repository.gate = CompletableDeferred()
        save(); runCurrent()
        val op = repository.submissions.single()
        advanceTimeBy(10001); runCurrent()
        assertTrue(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        vm.closeForm()
        repository.emit(emptyList(), fromCache = true); runCurrent()
        assertTrue(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        repository.emit(listOf(confirmed(op).copy(lastOperationId = "different-operation"))); runCurrent()
        assertTrue(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        repository.emit(listOf(confirmed(op))); runCurrent()
        assertNull(vm.state.value.message)
    }

    @Test fun `account switch clears pending and rejected feedback and ignores late completion`() = runTest(dispatcher) {
        runCurrent(); repository.gate = CompletableDeferred(); repository.ignoreCancellation = true
        save(); runCurrent()
        val op = repository.submissions.single()
        try {
            repository.emit(listOf(pending(op), rejected(op.copy(result = op.result.copy(id = "other"))))); runCurrent()
            assertNotNull(vm.state.value.message)
            auth.authState.value = User("b", "b@example.com"); runCurrent()
            assertNull(vm.state.value.message)
            assertTrue(vm.state.value.unsettled.isEmpty())
            repository.gate!!.complete(Unit); runCurrent()
            repository.emit(listOf(rejected(op)), listOf(op)); runCurrent()
            assertNull(vm.state.value.message)
            assertNull(vm.state.value.form)
        } finally { repository.gate!!.complete(Unit); runCurrent() }
    }

    @Test fun `account switch clears an unknown save outcome before another user loads`() = runTest(dispatcher) {
        runCurrent(); repository.gate = CompletableDeferred()
        save(); runCurrent()
        advanceTimeBy(10001); runCurrent()
        assertTrue(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        auth.authState.value = User("b", "b@example.com"); runCurrent()
        assertNull(vm.state.value.message)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.form)
    }

    @Test fun `another operation completing cannot hide an unknown save outcome`() = runTest(dispatcher) {
        runCurrent(); repository.gate = CompletableDeferred()
        save(); runCurrent()
        val unknown = repository.submissions.single()
        advanceTimeBy(10001); runCurrent()
        vm.closeForm()
        repository.gate = null
        save(); runCurrent()
        val other = repository.submissions.last()
        repository.emit(listOf(pending(other)), listOf(other)); runCurrent()
        assertAwaiting()
        assertTrue(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        repository.emit(listOf(confirmed(other))); runCurrent()
        assertFalse(vm.state.value.message.orEmpty().contains("awaiting", ignoreCase = true))
        assertTrue(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        repository.emit(listOf(confirmed(other), pending(unknown)), listOf(unknown)); runCurrent()
        assertAwaiting()
        assertFalse(vm.state.value.message.orEmpty().contains("unknown", ignoreCase = true))
        repository.emit(listOf(confirmed(other), confirmed(unknown))); runCurrent()
        assertNull(vm.state.value.message)
    }

    private class FeedbackRepository : CryptoRepository {
        private val a = MutableStateFlow(CryptoSnapshot(loaded = true, fromCache = false))
        private val b = MutableStateFlow(CryptoSnapshot(loaded = true, fromCache = false))
        val submissions = mutableListOf<CryptoOperation>()
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false
        override fun observe(uid: String, year: Int, cutoff: String) = if (uid == "a") a else b
        fun emit(rows: List<CryptoResult>, attempts: List<CryptoOperation> = emptyList(), fromCache: Boolean = false,
                 confirmedRows: List<CryptoResult>? = null) {
            a.value = CryptoSnapshot(results = rows, attempts = attempts, loaded = true, fromCache = fromCache, confirmedResults = confirmedRows)
        }
        override suspend fun submit(uid: String, operation: CryptoOperation) {
            submissions += operation
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
        }
        override suspend fun retry(uid: String, operation: CryptoOperation) = submit(uid, operation)
        override suspend fun dismissRejected(uid: String, operation: CryptoOperation) {
            a.value = a.value.copy(results = a.value.results.filterNot { it.id == operation.result.id },
                attempts = a.value.attempts.filterNot { it.operationId == operation.operationId })
        }
    }
}
