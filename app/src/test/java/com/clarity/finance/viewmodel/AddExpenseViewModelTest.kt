package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
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
class AddExpenseViewModelTest {

    @Test fun `UI draft is owner scoped ignores stale edits and is not restored into a fresh submission owner`() = runTest(testDispatcher) {
        val session = MutableStateFlow<User?>(User("draft-a", "fictional"))
        `when`(authRepository.authState).thenReturn(session)
        val model = AddExpenseViewModel(authRepository, expenseRepository)
        advanceUntilIdle()
        model.updateDraft("draft-a", amountStr = "875.25", note = "Unsaved draft")
        assertEquals("875.25", model.formState.value.draft.amountStr)
        session.value = User("draft-b", "fictional")
        advanceUntilIdle()
        assertEquals("", model.formState.value.draft.amountStr)
        assertEquals("", model.formState.value.draft.note)
        model.updateDraft("draft-a", amountStr = "999")
        assertEquals("", model.formState.value.draft.amountStr)
        val fresh = AddExpenseViewModel(authRepository, expenseRepository)
        advanceUntilIdle()
        assertEquals("", fresh.formState.value.draft.amountStr)
        org.mockito.Mockito.verifyNoInteractions(expenseRepository)
    }

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
        testDispatcher.scheduler.runCurrent()
        viewModel.addExpense("abc", "other", "cash", "discretionary", "2023-01-01", "")
        
        val state = viewModel.formState.value
        assertFalse(state.isSubmitting)
        assertEquals("Enter a positive LKR amount with at most two decimal places; no sign or exponent.", state.error)
    }
    
    @Test
    fun `addExpense rejects invalid date`() = runTest {
        testDispatcher.scheduler.runCurrent()
        viewModel.addExpense("10.00", "other", "cash", "discretionary", "2023/01/01", "")
        
        val state = viewModel.formState.value
        assertFalse(state.isSubmitting)
        assertEquals("Use YYYY-MM-DD.", state.error)
    }
}
