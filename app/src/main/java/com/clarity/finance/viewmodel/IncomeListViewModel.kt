package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.IncomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

sealed interface IncomeListUiState {
    object Loading : IncomeListUiState
    object SignedOut : IncomeListUiState
    data class Success(
        val incomes: List<Income>,
        val confirmedTotalLkr: Long,
        val pendingTotalLkr: Long,
        val totalsBySource: Map<String, Long>,
        val currentMonth: YearMonth
    ) : IncomeListUiState
    data class Error(val message: String) : IncomeListUiState
}

class IncomeListViewModel(
    private val authRepository: AuthRepository,
    private val incomeRepository: IncomeRepository
) : ViewModel() {

    private val timeZone = ZoneId.of("Asia/Colombo")
    private val _currentMonth = MutableStateFlow(YearMonth.now(timeZone))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IncomeListUiState> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                _currentMonth.flatMapLatest { month ->
                    incomeRepository.getIncomes(user.uid)
                        .map { allIncomes ->
                            val monthStartStr = month.atDay(1).toString()
                            val nextMonthStartStr = month.plusMonths(1).atDay(1).toString()
                            
                            val filteredIncomes = allIncomes.filter { 
                                it.receiptDate >= monthStartStr && it.receiptDate < nextMonthStartStr 
                            }

                            val confirmedTotal = filteredIncomes.filter { !it.isPending && it.syncError == null && !it.isConflict }.sumOf { it.lkrAmountMinor }
                            val pendingTotal = filteredIncomes.filter { it.isPending }.sumOf { it.lkrAmountMinor }
                            
                            val bySource = filteredIncomes.filter { !it.isPending && it.syncError == null && !it.isConflict }
                                .groupBy { it.sourceId }
                                .mapValues { entry -> entry.value.sumOf { it.lkrAmountMinor } }

                            IncomeListUiState.Success(
                                incomes = filteredIncomes,
                                confirmedTotalLkr = confirmedTotal,
                                pendingTotalLkr = pendingTotal,
                                totalsBySource = bySource,
                                currentMonth = month
                            )
                        }
                }
            } else {
                flowOf(IncomeListUiState.SignedOut)
            }
        }
        .catch { emit(IncomeListUiState.Error(it.message ?: "Failed to load income")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IncomeListUiState.Loading
        )

    fun changeMonth(offset: Long) {
        _currentMonth.value = _currentMonth.value.plusMonths(offset)
    }

    fun retryIncome(income: Income) {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                if (user != null) {
                    incomeRepository.retryFailedIncome(user.uid, income)
                }
            }
        }
    }
}
