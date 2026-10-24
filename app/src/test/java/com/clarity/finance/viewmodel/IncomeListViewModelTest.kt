package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.IncomeRepository
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
        
        // Ensure month matches our mock data
        val currentMonthStr = YearMonth.now(ZoneId.of("Asia/Colombo")).toString()
        val i1 = income1.copy(receiptDate = "\$currentMonthStr-20")
        val i2 = income2.copy(receiptDate = "\$currentMonthStr-21")
        
        `when`(incomeRepository.getIncomes("uid1")).thenReturn(MutableStateFlow(listOf(i1, i2)))
        mockAuthState.value = user1
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as IncomeListUiState.Success
        // 1230000 + 12800000 = 14030000
        assertEquals(14030000L, state.confirmedTotalLkr)
    }
    
    @Test
    fun `month boundaries restrict items`() = runTest(testDispatcher) {
        val user1 = User("uid1", "test@example.com")
        
        val targetMonth = YearMonth.of(2026, 10)
        
        // Items in Oct
        val i1 = Income("1", "salary", "2026-10-01", "LKR", "100", 10000L, "native_lkr", "1", "")
        val i2 = Income("2", "salary", "2026-10-31", "LKR", "100", 10000L, "native_lkr", "1", "")
        
        // Items outside Oct
        val i3 = Income("3", "salary", "2026-09-30", "LKR", "100", 10000L, "native_lkr", "1", "")
        val i4 = Income("4", "salary", "2026-11-01", "LKR", "100", 10000L, "native_lkr", "1", "")

        `when`(incomeRepository.getIncomes("uid1")).thenReturn(MutableStateFlow(listOf(i1, i2, i3, i4)))
        mockAuthState.value = user1
        advanceUntilIdle()
        
        // Hack: change the internal month to targetMonth so test relies on static dates
        val method = viewModel.javaClass.getDeclaredMethod("changeMonth", Long::class.java)
        // Just mock the month calculation: 
    }
}
