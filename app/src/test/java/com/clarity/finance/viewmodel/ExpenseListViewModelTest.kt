package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
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
class ExpenseListViewModelTest {

    private lateinit var viewModel: ExpenseListViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val expenseRepository = mock(ExpenseRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = ExpenseListViewModel(authRepository, expenseRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state reflects SignedOut initially`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.expensesState.collect {} }
        advanceUntilIdle()
        assertEquals(ExpensesUiState.SignedOut, viewModel.expensesState.value)
        collectJob.cancel()
    }

    @Test
    fun `state updates when user signs in`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.expensesState.collect {} }
        
        val user1 = User("uid1", "user1@example.com")
        val user1Expenses = listOf(Expense("1", 1000L, "LKR", "Other", "Cash", "discretionary", "2023-01-01"))
        
        `when`(expenseRepository.getExpenses("uid1")).thenReturn(MutableStateFlow(user1Expenses))
        
        mockAuthState.value = user1
        advanceUntilIdle()
        
        assertTrue(viewModel.expensesState.value is ExpensesUiState.Success)
        val successState = viewModel.expensesState.value as ExpensesUiState.Success
        assertEquals(1, successState.expenses.size)
        
        // Switch user
        val user2 = User("uid2", "user2@example.com")
        val user2Expenses = emptyList<Expense>()
        `when`(expenseRepository.getExpenses("uid2")).thenReturn(MutableStateFlow(user2Expenses))
        
        mockAuthState.value = user2
        advanceUntilIdle()
        
        val successState2 = viewModel.expensesState.value as ExpensesUiState.Success
        assertTrue(successState2.expenses.isEmpty())
        collectJob.cancel()
    }
}
