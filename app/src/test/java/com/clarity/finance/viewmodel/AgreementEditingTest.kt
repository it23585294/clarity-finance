package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AgreementEditingTest {
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "fixture@example.test"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private val project = ExpectedPayment("p", "project", "P", "freelance", "USD", "2026-12-31", 100000,
        allocatedAmountMinor = 40000, directReceivedAmountMinor = 10000, milestoneReceivedAmountMinor = 10000, updatedAt = Timestamp(1, 0), isFromCache = true)
    private val milestone = Milestone("m", "p", "M", "2026-12-31", 40000, 10000, updatedAt = Timestamp(1, 0), isFromCache = true)
    private class Data(private val project: ExpectedPayment, private val milestone: Milestone) : PaymentRepository by unused() {
        val edits = mutableListOf<ExpectedPayment>(); val milestoneEdits = mutableListOf<Pair<Milestone,String>>()
        var gate: CompletableDeferred<Unit>? = null
        override fun getExpectedPayments(userId: String) = flowOf(listOf(project))
        override fun getMilestones(userId: String, paymentId: String) = flowOf(listOf(milestone))
        override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: Timestamp): Result<Unit> {
            assertEquals(project.updatedAt, originalUpdatedAt); edits += payment
            gate?.let { withContext(NonCancellable) { it.await() } }; return Result.success(Unit)
        }
        override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: Timestamp, opId: String): Result<Unit> {
            assertEquals(this.milestone.updatedAt, originalUpdatedAt); milestoneEdits += milestone to opId; return Result.success(Unit)
        }
    }
    @Test fun `cached agreements and milestones can queue exact corrections without resetting received allocation or immutable metadata`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val data = Data(project, milestone)
        val vm = PaymentDetailViewModel(auth, data); val owners = ViewModelStore().also { it.put("detail", vm) }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        try {
            vm.load("p"); runCurrent(); vm.editProject(); assertNotNull(vm.editForm.value)
            vm.updateEdit(title = "Corrected", amount = "499", due = "2025-12-31"); vm.saveEdit(); runCurrent()
            assertTrue(vm.editForm.value!!.error!!.contains("below")); assertTrue(data.edits.isEmpty())
            vm.updateEdit(amount = "1200"); vm.saveEdit(); runCurrent(); assertNull(vm.editForm.value)
            val edited = data.edits.single()
            assertEquals(120000L, edited.agreedAmountMinor); assertEquals(40000L, edited.allocatedAmountMinor)
            assertEquals(10000L, edited.directReceivedAmountMinor); assertEquals(10000L, edited.milestoneReceivedAmountMinor)
            assertEquals("freelance", edited.sourceId); assertEquals("USD", edited.currency); assertEquals("2025-12-31", edited.dueDate)
            vm.editMilestone(milestone); vm.updateEdit(amount = "99"); vm.saveEdit(); runCurrent()
            assertTrue(vm.editForm.value!!.error!!.contains("below")); assertTrue(data.milestoneEdits.isEmpty())
            vm.updateEdit(amount = "450", title = "Corrected M"); vm.saveEdit(); runCurrent()
            assertEquals(45000L, data.milestoneEdits.single().first.agreedAmountMinor)
            assertEquals(10000L, data.milestoneEdits.single().first.receivedAmountMinor)
            assertEquals("p", data.milestoneEdits.single().first.paymentId)
        } finally { observer.cancelAndJoin(); owners.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun `account switching clears an agreement edit and ignores its noncooperative old result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val gate = CompletableDeferred<Unit>()
        val data = Data(project, milestone).apply { this.gate = gate }; val vm = PaymentDetailViewModel(auth, data)
        val owners = ViewModelStore().also { it.put("detail", vm) }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        try {
            vm.load("p"); runCurrent(); vm.editProject(); vm.updateEdit(title = "Private A"); vm.saveEdit(); runCurrent()
            assertTrue(vm.editForm.value!!.busy)
            auth.authState.value = User("b", "other@example.test"); runCurrent(); assertNull(vm.editForm.value)
            gate.complete(Unit); runCurrent(); assertNull(vm.editForm.value); assertEquals(1, data.edits.size)
        } finally { observer.cancelAndJoin(); owners.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    companion object {
        private fun unused(): PaymentRepository = Proxy.newProxyInstance(PaymentRepository::class.java.classLoader,
            arrayOf(PaymentRepository::class.java)) { _, method, _ -> error("Unexpected fixture call: ${method.name}") } as PaymentRepository
    }
}
