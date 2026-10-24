package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentListViewModelTest {

    private lateinit var viewModel: PaymentListViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val paymentRepository = mock(PaymentRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = PaymentListViewModel(authRepository, paymentRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `account isolation resets state`() = runTest(testDispatcher) {
        val user1 = User("uid1", "test@example.com")
        val p = ExpectedPayment("p1", "standalone", "Title", "salary", "LKR", "2023-11-01", 1000L)
        
        `when`(paymentRepository.getExpectedPayments("uid1")).thenReturn(MutableStateFlow(listOf(p)))
        `when`(paymentRepository.getExpectedPayments("uid2")).thenReturn(MutableStateFlow(emptyList()))
        
        mockAuthState.value = user1
        
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        
        val state1 = viewModel.uiState.value as PaymentListUiState.Success
        assertEquals(1, state1.payments.size)
        
        mockAuthState.value = User("uid2", "user2@example.com")
        advanceUntilIdle()
        
        val state2 = viewModel.uiState.value as PaymentListUiState.Success
        assertEquals(0, state2.payments.size)
        collectJob.cancel()
    }
}
