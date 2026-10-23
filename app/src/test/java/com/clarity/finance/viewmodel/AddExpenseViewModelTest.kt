package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class AddExpenseViewModelTest {

    private lateinit var viewModel: AddExpenseViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val expenseRepository = mock(ExpenseRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val mockAuthState = MutableStateFlow<User?>(User("test-uid", "test@test.com"))
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = AddExpenseViewModel(authRepository, expenseRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parseAmount handles valid values correctly`() {
        assertEquals(1050L, viewModel.parseAmount("10.50").getOrNull())
        assertEquals(1050L, viewModel.parseAmount("10.5").getOrNull())
        assertEquals(1000L, viewModel.parseAmount("10").getOrNull())
        assertEquals(50L, viewModel.parseAmount("0.50").getOrNull())
    }

    @Test
    fun `parseAmount rejects negative, zero, and too many decimal places`() {
        assertTrue(viewModel.parseAmount("0.00").isFailure)
        assertTrue(viewModel.parseAmount("-10.00").isFailure)
        assertTrue(viewModel.parseAmount("10.123").isFailure)
        assertTrue(viewModel.parseAmount("abc").isFailure)
    }

    @Test
    fun `addExpense rejects invalid money string`() = runTest {
        viewModel.addExpense("abc", "Other", "Cash", "discretionary", "2023-01-01", "")
        
        val state = viewModel.formState.value
        assertFalse(state.isSubmitting)
        assertEquals("Invalid amount", state.error)
    }
    
    @Test
    fun `addExpense rejects invalid date`() = runTest {
        viewModel.addExpense("10.00", "Other", "Cash", "discretionary", "2023/01/01", "")
        
        val state = viewModel.formState.value
        assertFalse(state.isSubmitting)
        assertEquals("Invalid date format. Use real calendar date (yyyy-MM-dd)", state.error)
    }
}
