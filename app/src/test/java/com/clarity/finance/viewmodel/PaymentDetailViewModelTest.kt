package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentDetailViewModelTest {

    private lateinit var viewModel: PaymentDetailViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val paymentRepository = mock(PaymentRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = PaymentDetailViewModel(authRepository, paymentRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancel payment failure resolves to error`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val p = ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2023-11-01", 1000L, updatedAt = Timestamp.now())
        
        `when`(paymentRepository.cancelExpectedPayment(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )).thenReturn(Result.failure(Exception("Cancel failed")))

        viewModel.cancelPayment(p)
        advanceUntilIdle()

        assertEquals("Cancel failed", viewModel.cancelError.value)
    }
}
