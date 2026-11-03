package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.IncomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
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
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeListViewModelTest {

    private lateinit var viewModel: IncomeListViewModel
    private val authRepository = mock(AuthRepository::class.java)
    private val incomeRepository = mock(IncomeRepository::class.java)
    private val testDispatcher = StandardTestDispatcher()
    private val mockAuthState = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(authRepository.authState).thenReturn(mockAuthState)
        viewModel = IncomeListViewModel(authRepository, incomeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calculates combined total correctly`() = runTest(testDispatcher) {
        val user1 = User("uid1", "test@example.com")
        
        // USD 41 * 300 = 12,300.00 LKR = 1,230,000 cents
        val income1 = Income("1", "freelance", "2026-10-20", "USD", "41", 1230000L, "confirmed_rate", "300", "")
        // LKR 128,000.00 = 12,800,000 cents
        val income2 = Income("2", "salary", "2026-10-21", "LKR", "128000.00", 12800000L, "native_lkr", "1", "")
        
        val currentMonthStr = YearMonth.now(ZoneId.of("Asia/Colombo"))
        val i1 = income1.copy(receiptDate = "${currentMonthStr}-20")
        val i2 = income2.copy(receiptDate = "${currentMonthStr}-21")
        
        `when`(incomeRepository.getIncomes(org.mockito.kotlin.eq("uid1"), org.mockito.kotlin.any<com.clarity.finance.data.model.ReportingMonth>())).thenReturn(MutableStateFlow(listOf(i1, i2)))
        mockAuthState.value = user1
        
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as IncomeListUiState.Success
        // 1230000 + 12800000 = 14030000
        assertEquals(14030000L, state.confirmedTotalLkr)
        collectJob.cancel()
    }
    
    @Test
    fun `month boundaries restrict items`() = runTest(testDispatcher) {
        val user1 = User("uid1", "test@example.com")
        
        val now = YearMonth.now(ZoneId.of("Asia/Colombo"))
        
        val i1 = Income("1", "salary", now.atDay(1).toString(), "LKR", "100", 10000L, "native_lkr", "1", "")
        val i2 = Income("2", "salary", now.atEndOfMonth().toString(), "LKR", "100", 10000L, "native_lkr", "1", "")
        val i3 = Income("3", "salary", now.minusMonths(1).atEndOfMonth().toString(), "LKR", "100", 10000L, "native_lkr", "1", "")
        val i4 = Income("4", "salary", now.plusMonths(1).atDay(1).toString(), "LKR", "100", 10000L, "native_lkr", "1", "")

        `when`(incomeRepository.getIncomes(org.mockito.kotlin.eq("uid1"), org.mockito.kotlin.any<com.clarity.finance.data.model.ReportingMonth>())).thenReturn(MutableStateFlow(listOf(i1, i2, i3, i4)))
        mockAuthState.value = user1
        
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as IncomeListUiState.Success
        assertEquals(2, state.incomes.size)
        assertTrue(state.incomes.contains(i1))
        assertTrue(state.incomes.contains(i2))
        assertFalse(state.incomes.contains(i3))
        assertFalse(state.incomes.contains(i4))
        collectJob.cancel()
    }
    
    @Test
    fun `account isolation resets state`() = runTest(testDispatcher) {
        val user1 = User("uid1", "test@example.com")
        val now = YearMonth.now(ZoneId.of("Asia/Colombo"))
        val i1 = Income("1", "salary", now.atDay(1).toString(), "LKR", "100", 10000L, "native_lkr", "1", "")
        
        `when`(incomeRepository.getIncomes(org.mockito.kotlin.eq("uid1"), org.mockito.kotlin.any<com.clarity.finance.data.model.ReportingMonth>())).thenReturn(MutableStateFlow(listOf(i1)))
        val secondAccount = MutableSharedFlow<List<Income>>(replay = 1)
        `when`(incomeRepository.getIncomes(org.mockito.kotlin.eq("uid2"), org.mockito.kotlin.any<com.clarity.finance.data.model.ReportingMonth>())).thenReturn(secondAccount)
        
        mockAuthState.value = user1
        
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        
        val state1 = viewModel.uiState.value as IncomeListUiState.Success
        assertEquals(1, state1.incomes.size)
        
        mockAuthState.value = User("uid2", "user2@example.com")
        runCurrent()
        assertTrue(viewModel.uiState.value is IncomeListUiState.Loading)
        secondAccount.emit(emptyList())
        advanceUntilIdle()
        
        val state2 = viewModel.uiState.value as IncomeListUiState.Success
        assertEquals(0, state2.incomes.size)
        collectJob.cancel()
    }
}
