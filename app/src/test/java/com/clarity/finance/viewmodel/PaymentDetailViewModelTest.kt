package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class PaymentDetailViewModelTest {

    private lateinit var viewModel: PaymentDetailViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private lateinit var fakePaymentRepository: FakePaymentRepository
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        fakePaymentRepository = FakePaymentRepository()
        viewModel = PaymentDetailViewModel(authRepository, fakePaymentRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancel payment failure resolves to error`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = Timestamp.now())

        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        viewModel.load("p1")
        advanceUntilIdle()

        fakePaymentRepository.cancelDeferred.complete(Result.failure(Exception("Cancel failed")))

        viewModel.cancelPayment(p)
        advanceUntilIdle()

        assertEquals("Cancel failed", viewModel.cancelError.value)
    }

    @Test
    fun `cancel payment with missing timestamp fails gracefully`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = null)

        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        viewModel.load("p1")
        advanceUntilIdle()

        viewModel.cancelPayment(p)
        advanceUntilIdle()

        assertTrue(viewModel.cancelError.value!!.contains("missing its server timestamp"))
    }

    @Test
    fun `switching accounts rejects cancellation of previous account record`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = Timestamp.now())

        val uid1Payments = MutableSharedFlow<List<ExpectedPayment>>(replay = 1)
        uid1Payments.emit(listOf(p))
        fakePaymentRepository.expectedPaymentsMap["uid1"] = uid1Payments

        viewModel.load("p1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PaymentDetailUiState.Success)
        assertEquals("uid1", (viewModel.uiState.value as PaymentDetailUiState.Success).ownerUid)

        val uid2Payments = MutableSharedFlow<List<ExpectedPayment>>()
        fakePaymentRepository.expectedPaymentsMap["uid2"] = uid2Payments

        mockAuthState.value = User("uid2", "other@test.com")
        advanceUntilIdle()

        viewModel.cancelPayment(p)
        advanceUntilIdle()

        assertEquals("Account changed", viewModel.cancelError.value)
    }

    @Test
    fun `cancel milestone with missing timestamp fails gracefully`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = Timestamp.now())
        val ms = Milestone("m1", "p1", "M1", "2023-11-01", 1000L, updatedAt = null)

        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        fakePaymentRepository.milestonesMap["uid1:p1"] = flowOf(listOf(ms))
        viewModel.load("p1")
        advanceUntilIdle()

        viewModel.cancelMilestone("p1", ms)
        advanceUntilIdle()

        assertTrue(viewModel.cancelError.value!!.contains("missing its server timestamp"))
    }

    @Test
    fun `clears old detail data immediately when new account data is delayed`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = Timestamp.now())

        val uid1Payments = MutableSharedFlow<List<ExpectedPayment>>(replay = 1)
        uid1Payments.emit(listOf(p))
        fakePaymentRepository.expectedPaymentsMap["uid1"] = uid1Payments

        viewModel.load("p1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PaymentDetailUiState.Success)

        val uid2Payments = MutableSharedFlow<List<ExpectedPayment>>()
        fakePaymentRepository.expectedPaymentsMap["uid2"] = uid2Payments

        mockAuthState.value = User("uid2", "other@test.com")
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value is PaymentDetailUiState.Loading)

        collectJob.cancel()
    }

    @Test
    fun `cancellation throwing after account switch does not set error on new account`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = Timestamp.now())

        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))

        viewModel.load("p1")
        advanceUntilIdle()

        viewModel.cancelPayment(p)
        testScheduler.runCurrent()

        fakePaymentRepository.expectedPaymentsMap["uid2"] = flowOf(emptyList())
        mockAuthState.value = User("uid2", "other@test.com")
        testScheduler.runCurrent()

        fakePaymentRepository.cancelDeferred.completeExceptionally(RuntimeException("Delayed cancel error"))
        advanceUntilIdle()

        assertNull(viewModel.cancelError.value)

        collectJob.cancel()
    }

    @Test
    fun `create milestone validates remaining allocation capacity`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Project 1", "freelance", "LKR", "2023-11-01", 100000L, allocatedAmountMinor = 80000L, updatedAt = Timestamp.now())

        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))

        viewModel.load("p1")
        advanceUntilIdle()

        // Unallocated capacity is 20000L (200.00 LKR)
        viewModel.createMilestone(p, "New Milestone", "300.00", "2023-11-15")
        advanceUntilIdle()

        assertNotNull(viewModel.milestoneState.value.error)
        assertTrue(viewModel.milestoneState.value.error!!.contains("Amount exceeds unallocated balance"))

        fakePaymentRepository.createMilestoneDeferred = CompletableDeferred()
        fakePaymentRepository.createMilestoneDeferred.complete(Result.success(Unit))

        viewModel.createMilestone(p, "Valid Milestone", "200.00", "2023-11-15")
        advanceUntilIdle()

        assertTrue(viewModel.milestoneState.value.isSuccess)
        assertNotNull(fakePaymentRepository.createdMilestone)
        assertEquals("Valid Milestone", fakePaymentRepository.createdMilestone?.title)
        assertEquals(20000L, fakePaymentRepository.createdMilestone?.agreedAmountMinor)

        collectJob.cancel()
    }

    @Test
    fun `cancel partially paid milestone calls repository cancelMilestone`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Project 1", "freelance", "LKR", "2023-11-01", 100000L, allocatedAmountMinor = 40000L, updatedAt = Timestamp.now())
        val ms = Milestone("m1", "p1", "M1", "2023-11-01", 40000L, receivedAmountMinor = 15000L, isCancelled = false, updatedAt = Timestamp.now())

        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        fakePaymentRepository.milestonesMap["uid1:p1"] = flowOf(listOf(ms))

        viewModel.load("p1")
        advanceUntilIdle()

        fakePaymentRepository.cancelDeferred.complete(Result.success(Unit))

        viewModel.cancelMilestone("p1", ms)
        advanceUntilIdle()

        assertNull(viewModel.cancelError.value)
        assertEquals("m1", fakePaymentRepository.cancelledMilestoneId)

        collectJob.cancel()
    }

    private class FakePaymentRepository : PaymentRepository {
        val expectedPaymentsMap = mutableMapOf<String, Flow<List<ExpectedPayment>>>()
        val milestonesMap = mutableMapOf<String, Flow<List<Milestone>>>()
        val cancelDeferred = CompletableDeferred<Result<Unit>>()
        var createMilestoneDeferred = CompletableDeferred<Result<Unit>>()
        var createdMilestone: Milestone? = null
        var cancelledMilestoneId: String? = null
        val milestoneAttempts = mutableListOf<Pair<Milestone, String>>()
        var restoredMilestone: Milestone? = null
        override suspend fun getUnresolvedMilestoneCreation(userId: String, paymentId: String): Milestone? = restoredMilestone

        override fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>> =
            expectedPaymentsMap[userId] ?: flowOf(emptyList())

        override fun getMilestones(userId: String, paymentId: String): Flow<List<Milestone>> =
            milestonesMap["$userId:$paymentId"] ?: flowOf(emptyList())

        override suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment> = Result.failure(Exception("Not found"))
        override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> = Result.success(Unit)
        override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: Timestamp): Result<Unit> = Result.success(Unit)

        override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: Timestamp): Result<Unit> {
            return cancelDeferred.await()
        }

        override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit> {
            createdMilestone = milestone
            milestoneAttempts.add(milestone to opId)
            return if (createMilestoneDeferred.isCompleted) createMilestoneDeferred.getCompleted() else createMilestoneDeferred.await()
        }

        override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: Timestamp, opId: String): Result<Unit> = Result.success(Unit)

        override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: Timestamp, opId: String): Result<Unit> {
            cancelledMilestoneId = milestoneId
            return cancelDeferred.await()
        }

        override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> = Result.success(Unit)
        override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> = Result.success(Unit)
        override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? = null
    }

    @Test
    fun `milestone timeout retries same ID operation and original payload`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Project", "freelance", "LKR", "2026-10-30", 100000L, updatedAt = Timestamp(1, 0))
        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        viewModel.load("p1")
        advanceUntilIdle()
        viewModel.createMilestone(p, "Original", "400.00", "2026-10-30")
        advanceUntilIdle()
        assertFalse(viewModel.milestoneState.value.isSubmitting)
        assertFalse(viewModel.milestoneState.value.isSuccess)
        val original = fakePaymentRepository.milestoneAttempts.single()
        fakePaymentRepository.createMilestoneDeferred.complete(Result.success(Unit))
        viewModel.createMilestone(p, "Changed", "500.00", "2026-11-01")
        advanceUntilIdle()
        assertEquals(listOf(original, original), fakePaymentRepository.milestoneAttempts)
        assertTrue(viewModel.milestoneState.value.isSuccess)
    }

    @Test
    fun `restored milestone reconciles before validating a new form even with zero free capacity`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Project", "freelance", "LKR", "2026-10-30", 40000L,
            allocatedAmountMinor = 40000L, updatedAt = Timestamp(1, 0))
        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        val original = Milestone("old", "p1", "Saved milestone", "2026-10-30", 40000L, lastMilestoneOpId = "saved-op")
        fakePaymentRepository.restoredMilestone = original
        fakePaymentRepository.createMilestoneDeferred.complete(Result.success(Unit))
        viewModel.load("p1")
        advanceUntilIdle()
        viewModel.createMilestone(p, "", "", "")
        advanceUntilIdle()
        assertEquals(original, viewModel.milestoneState.value.retryMilestone)
        assertTrue(fakePaymentRepository.milestoneAttempts.isEmpty())
        viewModel.createMilestone(p, "Changed", "999999", "invalid")
        advanceUntilIdle()
        assertEquals(listOf(original to "saved-op"), fakePaymentRepository.milestoneAttempts)
        assertTrue(viewModel.milestoneState.value.isSuccess)
    }

    @Test
    fun `cancellation result from old account visit is ignored after switching away and back`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val p = ExpectedPayment("p1", "project", "Project", "freelance", "LKR", "2026-10-30", 100000L, updatedAt = Timestamp(1, 0))
        fakePaymentRepository.expectedPaymentsMap["uid1"] = flowOf(listOf(p))
        mockAuthState.value = User("uid1", "one@test.com")
        viewModel.load("p1")
        advanceUntilIdle()
        viewModel.cancelPayment(p)
        testScheduler.runCurrent()
        mockAuthState.value = User("uid2", "two@test.com")
        testScheduler.runCurrent()
        mockAuthState.value = User("uid1", "one@test.com")
        testScheduler.runCurrent()
        fakePaymentRepository.cancelDeferred.complete(Result.failure(Exception("Stale error")))
        advanceUntilIdle()
        assertNull(viewModel.cancelError.value)
    }
}
