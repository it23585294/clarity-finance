package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
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
        
        // 100000 agreed - 40000 allocated - 0 directReceived = 60000 max
        // The milestone receipt of 15000 has NO impact on direct received capacity!
        
        viewModel.updateForm(amountStr = "600.01") // 60001 cents
        viewModel.confirmPayment()
        advanceUntilIdle()
        
        assertEquals("Cannot exceed outstanding balance: 600.0 LKR", viewModel.formState.value.error)
    }
}
