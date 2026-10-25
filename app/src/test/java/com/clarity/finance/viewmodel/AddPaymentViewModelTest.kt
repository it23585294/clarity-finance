package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class AddPaymentViewModelTest {

    private lateinit var viewModel: AddPaymentViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private lateinit var fakePaymentRepository: FakePaymentRepository
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        fakePaymentRepository = FakePaymentRepository()
        viewModel = AddPaymentViewModel(authRepository, fakePaymentRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid payment creation sets zero initial counters and calls repository`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        advanceUntilIdle()
        assertNull(viewModel.formState.value.error)

        viewModel.updateForm(
            type = "project",
            title = "Website Project",
            sourceId = "freelance",
            currency = "USD",
            amountStr = "1000.00",
            dueDate = "2023-11-01",
            note = "Project note"
        )

        viewModel.savePayment()
        advanceUntilIdle()

        assertTrue(viewModel.formState.value.isSuccess)
        val created = fakePaymentRepository.createdPayment
        assertNotNull(created)
        assertEquals("project", created?.type)
        assertEquals("Website Project", created?.title)
        assertEquals(100000L, created?.agreedAmountMinor)
        assertEquals(0L, created?.allocatedAmountMinor)
        assertEquals(0L, created?.directReceivedAmountMinor)
        assertEquals(0L, created?.milestoneReceivedAmountMinor)
        assertFalse(created?.isCancelled ?: true)
    }

    @Test
    fun `money validation rejects zero, negative, scientific notation, and above maximum`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        advanceUntilIdle()

        // Zero amount
        viewModel.updateForm(title = "Title", amountStr = "0.00")
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals("Amount must be positive", viewModel.formState.value.error)

        // Negative amount
        viewModel.updateForm(title = "Title", amountStr = "-50.00")
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals("Amount must be positive", viewModel.formState.value.error)

        // Scientific notation (lowercase 'e')
        viewModel.updateForm(title = "Title", amountStr = "1e2")
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals("Scientific notation not allowed", viewModel.formState.value.error)

        // Scientific notation (uppercase 'E')
        viewModel.updateForm(title = "Title", amountStr = "1E2")
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals("Scientific notation not allowed", viewModel.formState.value.error)

        // Above 100 Billion ceiling
        viewModel.updateForm(title = "Title", amountStr = "1000000000.01")
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals("Amount exceeds maximum limit", viewModel.formState.value.error)
    }

    @Test
    fun `duplicate submission is rejected while submitting`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        advanceUntilIdle()

        fakePaymentRepository.createDeferred = CompletableDeferred()

        viewModel.updateForm(title = "Title", amountStr = "100.00")
        viewModel.savePayment()
        testScheduler.runCurrent()

        assertTrue(viewModel.formState.value.isSubmitting)

        // Second click while submitting should be ignored
        viewModel.savePayment()
        testScheduler.runCurrent()

        fakePaymentRepository.createDeferred.complete(Result.success(Unit))
        advanceUntilIdle()

        assertTrue(viewModel.formState.value.isSuccess)
    }

    @Test
    fun `account switch invalidates operation token and resets state`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        advanceUntilIdle()

        viewModel.updateForm(title = "Title", amountStr = "100.00")

        fakePaymentRepository.createDeferred = CompletableDeferred()
        viewModel.savePayment()
        testScheduler.runCurrent()

        // Account switches while submission is pending in repo
        mockAuthState.value = User("uid2", "other@test.com")
        testScheduler.runCurrent()

        fakePaymentRepository.createDeferred.complete(Result.success(Unit))
        advanceUntilIdle()

        assertFalse(viewModel.formState.value.isSuccess)
        assertEquals("Account changed.", viewModel.formState.value.error)
    }

    private class FakePaymentRepository : PaymentRepository {
        var createdPayment: ExpectedPayment? = null
        val creations = mutableListOf<Pair<String, ExpectedPayment>>()
        val recovery = mutableMapOf<String, CompletableDeferred<ExpectedPayment?>>()
        var createDeferred = CompletableDeferred(Result.success(Unit))

        override suspend fun getUnresolvedProjectCreation(userId: String): ExpectedPayment? = recovery[userId]?.await()

        override fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>> = flowOf(emptyList())
        override fun getMilestones(userId: String, paymentId: String): Flow<List<com.clarity.finance.data.model.Milestone>> = flowOf(emptyList())
        override suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment> = Result.failure(Exception("Not found"))

        override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> {
            createdPayment = payment
            creations.add(userId to payment)
            return if (createDeferred.isCompleted) createDeferred.getCompleted() else createDeferred.await()
        }

        override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = Result.success(Unit)
        override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = Result.success(Unit)
        override suspend fun createMilestone(userId: String, milestone: com.clarity.finance.data.model.Milestone, opId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateMilestone(userId: String, milestone: com.clarity.finance.data.model.Milestone, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> = Result.success(Unit)
        override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> = Result.success(Unit)
        override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: com.clarity.finance.data.model.Income): Result<Unit> = Result.success(Unit)
        override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: com.clarity.finance.data.model.Income): Result<Unit> = Result.success(Unit)
        override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): com.clarity.finance.data.model.Income? = null
    }

    @Test
    fun `timeout ends spinner and retry keeps identical project`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        advanceUntilIdle()
        fakePaymentRepository.createDeferred = CompletableDeferred()
        viewModel.updateForm(type = "project", title = "Original", amountStr = "100.00")
        viewModel.savePayment()
        advanceUntilIdle()
        assertFalse(viewModel.formState.value.isSubmitting)
        assertFalse(viewModel.formState.value.isSuccess)
        assertTrue(viewModel.formState.value.isRetry)
        val first = fakePaymentRepository.createdPayment
        viewModel.updateForm(title = "Changed", amountStr = "200.00")
        fakePaymentRepository.createDeferred.complete(Result.success(Unit))
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals(first, fakePaymentRepository.createdPayment)
        assertTrue(viewModel.formState.value.isSuccess)
    }

    @Test
    fun `account switch cancels suspended recovery and restores new account attempt`() = runTest(testDispatcher) {
        fakePaymentRepository.recovery["uid1"] = CompletableDeferred()
        val recovered = ExpectedPayment("p2", "project", "Account two", "freelance", "LKR", "2026-10-30", 10000L)
        fakePaymentRepository.recovery["uid2"] = CompletableDeferred(recovered)
        mockAuthState.value = User("uid1", "one@test.com")
        testScheduler.runCurrent()
        assertTrue(viewModel.formState.value.isLoading)
        mockAuthState.value = User("uid2", "two@test.com")
        testScheduler.runCurrent()
        assertEquals("Account two", viewModel.formState.value.title)
        fakePaymentRepository.recovery["uid1"]!!.complete(recovered.copy(id = "old", title = "Stale"))
        advanceUntilIdle()
        assertEquals("Account two", viewModel.formState.value.title)
        viewModel.savePayment()
        advanceUntilIdle()
        assertEquals("uid2" to recovered, fakePaymentRepository.creations.single())
    }
}
