package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentConfirmationViewModelTest {

    private lateinit var viewModel: PaymentConfirmationViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val paymentRepository = mock(PaymentRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = PaymentConfirmationViewModel(authRepository, paymentRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `direct payment capacity bounded correctly`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")

        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 100000L, 40000L, 0L, 15000L)
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(p))

        viewModel.load("p1", null)
        advanceUntilIdle()

        viewModel.updateForm(amountStr = "600.01")
        viewModel.confirmPayment()
        advanceUntilIdle()

        assertEquals("Cannot exceed outstanding balance: 600.00 LKR", viewModel.formState.value.error)
    }

    @Test
    fun `scientific notation is rejected`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "standalone", "Title", "freelance", "LKR", "2023-11-01", 100000L, 0L, 0L, 0L)
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(p))

        viewModel.load("p1", null)
        advanceUntilIdle()

        viewModel.updateForm(amountStr = "1e2")
        viewModel.confirmPayment()
        advanceUntilIdle()

        assertEquals("Scientific notation not allowed", viewModel.formState.value.error)
    }

    @Test
    fun `overlong note is rejected`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "standalone", "Title", "freelance", "LKR", "2023-11-01", 100000L, 0L, 0L, 0L)
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(p))

        viewModel.load("p1", null)
        advanceUntilIdle()

        val longNote = "a".repeat(501)
        viewModel.updateForm(amountStr = "100.00", note = longNote)
        viewModel.confirmPayment()
        advanceUntilIdle()

        assertEquals("Note cannot exceed 500 characters", viewModel.formState.value.error)
    }

    @Test
    fun `load restores complete unresolved income payload and ID`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val unresolved = Income(
            id = "r_saved",
            sourceId = "freelance",
            receiptDate = "2023-10-23",
            currency = "USD",
            originalAmountStr = "150.00",
            originalAmountMinor = 15000L,
            lkrAmountMinor = 4500000L,
            conversionBasis = "confirmed_rate",
            conversionRateStr = "300",
            note = "Saved note",
            linkedPaymentId = "p1"
        )
        `when`(paymentRepository.getUnresolvedPayment("uid1", "p1", null)).thenReturn(unresolved)
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(
            ExpectedPayment("p1", "standalone", "Title", "freelance", "USD", "2023-11-01", 100000L, 0L, 0L, 0L)
        ))

        viewModel.load("p1", null)
        advanceUntilIdle()

        val state = viewModel.formState.value
        assertTrue(state.isRetry)
        assertEquals("150.00", state.amountStr)
        assertEquals("Saved note", state.note)
        assertEquals("Unresolved payment found. Please retry.", state.error)
    }

    @Test
    fun `retry sends exact saved payload bypassing fresh capacity checks`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val unresolved = Income(
            id = "r_saved",
            sourceId = "freelance",
            receiptDate = "2023-10-23",
            currency = "LKR",
            originalAmountStr = "1000.00",
            originalAmountMinor = 100000L,
            lkrAmountMinor = 100000L,
            conversionBasis = "native_lkr",
            conversionRateStr = null,
            note = "Saved note",
            linkedPaymentId = "p1"
        )
        `when`(paymentRepository.getUnresolvedPayment("uid1", "p1", null)).thenReturn(unresolved)

        val p = ExpectedPayment("p1", "standalone", "Title", "freelance", "LKR", "2023-11-01", 100000L, 0L, 100000L, 0L)
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(p))

        viewModel.load("p1", null)
        advanceUntilIdle()

        `when`(paymentRepository.retryFailedPayment("uid1", "p1", null, unresolved)).thenReturn(Result.success(Unit))

        viewModel.confirmPayment()
        advanceUntilIdle()

        assertTrue(viewModel.formState.value.isSuccess)
    }

    @Test
    fun `initial failure retains payload and subsequent form edits do not overwrite saved operation`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "standalone", "Title", "freelance", "LKR", "2023-11-01", 100000L, 0L, 0L, 0L)
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(p))

        viewModel.load("p1", null)
        advanceUntilIdle()

        viewModel.updateForm(amountStr = "100.00", note = "Initial attempt")

        `when`(paymentRepository.confirmPayment(
            org.mockito.kotlin.eq("uid1"),
            org.mockito.kotlin.eq("p1"),
            org.mockito.kotlin.eq(null),
            org.mockito.kotlin.any()
        )).thenReturn(Result.failure(Exception("Network error")))

        viewModel.confirmPayment()
        advanceUntilIdle()

        assertTrue(viewModel.formState.value.isRetry)
        assertEquals("Network error", viewModel.formState.value.error)

        viewModel.updateForm(amountStr = "999.00", note = "Edited note")

        `when`(paymentRepository.retryFailedPayment(
            org.mockito.kotlin.eq("uid1"),
            org.mockito.kotlin.eq("p1"),
            org.mockito.kotlin.eq(null),
            org.mockito.kotlin.check { income ->
                assertEquals("100.00", income.originalAmountStr)
                assertEquals("Initial attempt", income.note)
            }
        )).thenReturn(Result.success(Unit))

        viewModel.confirmPayment()
        advanceUntilIdle()

        assertTrue(viewModel.formState.value.isSuccess)
    }

    @Test
    fun `switching milestones within same project completes out of order and preserves second milestone data`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 100000L, 40000L, 0L, 0L)
        val ms1 = Milestone("m1", "p1", "Milestone 1", "2023-11-01", 20000L)
        val ms2 = Milestone("m2", "p1", "Milestone 2", "2023-11-01", 10000L)

        val ms1Signal = CompletableDeferred<List<Milestone>>()
        val ms2Signal = CompletableDeferred<List<Milestone>>()

        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenReturn(Result.success(p))

        `when`(paymentRepository.getMilestones("uid1", "p1"))
            .thenAnswer { flow { emit(ms1Signal.await()) } }
            .thenAnswer { flow { emit(ms2Signal.await()) } }

        // Start m1 load (suspends on ms1Signal)
        viewModel.load("p1", "m1")
        testScheduler.runCurrent()

        // Start m2 load (suspends on ms2Signal)
        viewModel.load("p1", "m2")
        testScheduler.runCurrent()

        // Emit m2 FIRST out of order
        ms2Signal.complete(listOf(ms1, ms2))
        testScheduler.runCurrent()

        // Emit m1 LATER
        ms1Signal.complete(listOf(ms1, ms2))
        advanceUntilIdle()

        val state = viewModel.formState.value
        assertEquals("m2", state.milestoneId)
        assertEquals("Payment for: Milestone 2", state.note)
        assertFalse(state.isLoading)
    }

    @Test
    fun `switching accounts while submission is pending invalidates result without calling load`() = runTest(testDispatcher) {
        val fakeRepo = FakePaymentRepository()
        fakeRepo.expectedPayments["p1"] = ExpectedPayment("p1", "standalone", "Title", "freelance", "LKR", "2023-11-01", 100000L, 0L, 0L, 0L)

        val vm = PaymentConfirmationViewModel(authRepository, fakeRepo)
        mockAuthState.value = User("uid1", "test@test.com")

        vm.load("p1", null)
        advanceUntilIdle()

        vm.updateForm(amountStr = "100.00")

        vm.confirmPayment()
        testScheduler.runCurrent()

        assertTrue(fakeRepo.confirmStarted.isCompleted)
        assertTrue(vm.formState.value.isSubmitting)

        // Switch user to uid2 WITHOUT calling vm.load()
        mockAuthState.value = User("uid2", "other@test.com")
        testScheduler.runCurrent()

        // Release old request
        fakeRepo.confirmResult.complete(Result.success(Unit))
        advanceUntilIdle()

        // Old request cannot set success on new account
        assertFalse(vm.formState.value.isSuccess)
    }

    @Test
    fun `old submission coroutine starting after new load returns silently without modifying new form state`() = runTest(testDispatcher) {
        val fakeRepo = FakePaymentRepository()
        fakeRepo.expectedPayments["p1"] = ExpectedPayment("p1", "standalone", "Title 1", "freelance", "LKR", "2023-11-01", 100000L, 0L, 0L, 0L)
        fakeRepo.expectedPayments["p2"] = ExpectedPayment("p2", "standalone", "Title 2", "freelance", "LKR", "2023-11-01", 100000L, 0L, 0L, 0L)

        val vm = PaymentConfirmationViewModel(authRepository, fakeRepo)
        mockAuthState.value = User("uid1", "test@test.com")

        vm.load("p1", null)
        advanceUntilIdle()

        vm.updateForm(amountStr = "100.00")

        vm.confirmPayment()
        testScheduler.runCurrent()
        assertTrue(fakeRepo.confirmStarted.isCompleted)

        // Load new form p2 BEFORE old submission completes!
        vm.load("p2", null)
        advanceUntilIdle()

        assertEquals("p2", vm.formState.value.paymentId)
        assertFalse(vm.formState.value.isSubmitting)

        fakeRepo.confirmResult.complete(Result.failure(Exception("Old error")))
        advanceUntilIdle()

        assertEquals("p2", vm.formState.value.paymentId)
        assertNull(vm.formState.value.error)
        assertFalse(vm.formState.value.isSuccess)
    }

    private class FakePaymentRepository : PaymentRepository {
        val confirmStarted = CompletableDeferred<Unit>()
        val confirmResult = CompletableDeferred<Result<Unit>>()
        val expectedPayments = mutableMapOf<String, ExpectedPayment>()
        val unresolvedPayments = mutableMapOf<String, Income>()
        val attempts = mutableListOf<Income>()

        override fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>> = flowOf(expectedPayments.values.toList())
        override fun getMilestones(userId: String, paymentId: String): Flow<List<Milestone>> = flowOf(emptyList())

        override suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment> {
            val p = expectedPayments[paymentId]
            return if (p != null) Result.success(p) else Result.failure(Exception("Not found"))
        }

        override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> = Result.success(Unit)
        override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = Result.success(Unit)
        override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = Result.success(Unit)
        override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> = Result.success(Unit)
        override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> = Result.success(Unit)

        override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> {
            attempts.add(income)
            confirmStarted.complete(Unit)
            return confirmResult.await()
        }

        override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> {
            attempts.add(income)
            confirmStarted.complete(Unit)
            return confirmResult.await()
        }

        override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? {
            return unresolvedPayments["$paymentId:$milestoneId"]
        }
    }

    @Test
    fun `confirmation timeout keeps receipt identity and immutable retry amount`() = runTest(testDispatcher) {
        val repo = FakePaymentRepository()
        repo.expectedPayments["p1"] = ExpectedPayment("p1", "project", "Project", "freelance", "LKR", "2026-10-30", 100000L)
        val vm = PaymentConfirmationViewModel(authRepository, repo)
        mockAuthState.value = User("uid1", "test@test.com")
        vm.load("p1", null)
        advanceUntilIdle()
        vm.updateForm(amountStr = "100.00")
        vm.confirmPayment()
        advanceUntilIdle()
        assertFalse(vm.formState.value.isSubmitting)
        assertFalse(vm.formState.value.isSuccess)
        assertTrue(vm.formState.value.isRetry)
        val original = repo.attempts.single()
        vm.updateForm(amountStr = "200.00")
        repo.confirmResult.complete(Result.success(Unit))
        vm.confirmPayment()
        advanceUntilIdle()
        assertEquals(listOf(original, original), repo.attempts)
        assertTrue(vm.formState.value.isSuccess)
    }

    @Test
    fun `load failure ends spinner and leaves confirmation unavailable`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        `when`(paymentRepository.getExpectedPayment("uid1", "p1")).thenThrow(IllegalStateException("UNAVAILABLE"))
        viewModel.load("p1", null)
        advanceUntilIdle()
        assertFalse(viewModel.formState.value.isLoading)
        assertEquals("UNAVAILABLE", viewModel.formState.value.error)
        viewModel.confirmPayment()
        assertFalse(viewModel.formState.value.isSubmitting)
    }
}
