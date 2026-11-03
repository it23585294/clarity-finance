package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
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
class EditExpenseViewModelTest {

    @Test fun `recurring actual correction rejects future date scientific notation and overlength note before repository writes`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "fixture@example.com")
        val clock = java.time.Clock.fixed(java.time.Instant.parse("2026-01-31T00:00:00Z"), java.time.ZoneOffset.UTC)
        val vm = EditExpenseViewModel(authRepository, expenseRepository, clock)
        val expense = Expense("rec_rent_2026-01", 3400000, categoryId = "housing", paymentChannelId = "cash", spendingType = "committed",
            expenseDate = "2026-01-31", updatedAt = Timestamp(1, 0), recurringOccurrenceId = "rent_2026-01")
        `when`(expenseRepository.getExpense("uid1", expense.id)).thenReturn(Result.success(expense))
        vm.loadExpense(expense.id); advanceUntilIdle()
        vm.updateForm(expenseDate = "2026-02-01"); vm.saveChanges(); advanceUntilIdle()
        assertTrue(vm.formState.value.error!!.contains("nonfuture"))
        vm.updateForm(expenseDate = "2025-12-31", amountStr = "1e3"); vm.saveChanges(); advanceUntilIdle()
        assertNotNull(vm.formState.value.error)
        vm.updateForm(amountStr = "1", note = "x".repeat(501)); vm.saveChanges(); advanceUntilIdle()
        assertTrue(vm.formState.value.error!!.contains("500"))
        org.mockito.Mockito.verify(expenseRepository, org.mockito.Mockito.never()).updateExpense(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    private lateinit var viewModel: EditExpenseViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val expenseRepository = mock(ExpenseRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = EditExpenseViewModel(authRepository, expenseRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadExpense success populates form state`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        
        val timestamp = Timestamp.now()
        val expense = Expense(
            id = "e1",
            amountMinor = 1550L,
            currency = "LKR",
            categoryId = "food_dining",
            paymentChannelId = "cash",
            spendingType = "discretionary",
            expenseDate = "2023-10-23",
            note = "Test note",
            updatedAt = timestamp
        )
        
        `when`(expenseRepository.getExpense("uid1", "e1")).thenReturn(Result.success(expense))
        
        viewModel.loadExpense("e1")
        advanceUntilIdle()
        
        val state = viewModel.formState.value
        assertFalse(state.isLoading)
        assertEquals("15.50", state.amountStr)
        assertEquals("food_dining", state.categoryId)
        assertEquals("Test note", state.note)
    }

    @Test
    fun `history edit preserves every minor unit when loading a large amount`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@example.com")
        val expense = Expense("large", Long.MAX_VALUE, categoryId = "other", paymentChannelId = "cash",
            spendingType = "committed", expenseDate = "2023-10-23", updatedAt = Timestamp(1, 0))
        `when`(expenseRepository.getExpense("uid1", "large")).thenReturn(Result.success(expense))
        viewModel.loadExpense("large")
        advanceUntilIdle()
        assertEquals("92233720368547758.07", viewModel.formState.value.amountStr)
    }

    @Test
    fun `saveChanges aborted resolves to stale conflict state`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val timestamp = Timestamp.now()
        val expense = Expense(id = "e1", amountMinor = 1550L, categoryId = "food_dining", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2023-10-23", updatedAt = timestamp)
        `when`(expenseRepository.getExpense("uid1", "e1")).thenReturn(Result.success(expense))
        
        viewModel.loadExpense("e1")
        advanceUntilIdle()
        
        // Mock a failure due to abort (staleness)
        val abortException = Exception("ABORTED")
        `when`(expenseRepository.updateExpense(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )).thenReturn(Result.failure(abortException))
        
        viewModel.updateForm(amountStr = "10.00") // Form values preserved?
        viewModel.saveChanges()
        advanceUntilIdle()
        
        val state = viewModel.formState.value
        assertTrue(state.isStale)
        assertEquals("10.00", state.amountStr) // Form not blown away
        assertEquals("Expense was modified elsewhere. Review changes and reload.", state.error)
    }
    
    @Test
    fun `deleteExpense aborted resolves to stale conflict state`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val timestamp = Timestamp.now()
        val expense = Expense(id = "e1", amountMinor = 1550L, categoryId = "food_dining", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2023-10-23", updatedAt = timestamp)
        `when`(expenseRepository.getExpense("uid1", "e1")).thenReturn(Result.success(expense))
        
        viewModel.loadExpense("e1")
        advanceUntilIdle()
        
        val abortException = Exception("ABORTED")
        `when`(expenseRepository.deleteExpense(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )).thenReturn(Result.failure(abortException))
        
        viewModel.deleteExpense()
        advanceUntilIdle()
        
        val state = viewModel.formState.value
        assertTrue(state.isStale)
        assertEquals("Expense was modified elsewhere. Cannot delete. Reload first.", state.error)
    }

    @Test
    fun `operation aborted if account changes`() = runTest(testDispatcher) {
        mockAuthState.value = User("uid1", "test@test.com")
        val timestamp = Timestamp.now()
        val expense = Expense(id = "e1", amountMinor = 1550L, categoryId = "food_dining", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2023-10-23", updatedAt = timestamp)
        `when`(expenseRepository.getExpense("uid1", "e1")).thenReturn(Result.success(expense))
        
        viewModel.loadExpense("e1")
        advanceUntilIdle()
        
        // Switch account
        mockAuthState.value = User("uid2", "hacker@test.com")
        
        viewModel.saveChanges()
        advanceUntilIdle()
        
        val state = viewModel.formState.value
        assertFalse(state.isSuccess)
        assertEquals("Account changed. Operation aborted.", state.error)
    }
}
