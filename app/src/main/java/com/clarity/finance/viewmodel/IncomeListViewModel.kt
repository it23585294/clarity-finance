package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.IncomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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
                    kotlinx.coroutines.flow.flow<IncomeListUiState> {
                    emit(IncomeListUiState.Loading) // Neither a new owner nor a new month inherits old rows/totals.
                    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    try { incomeRepository.getIncomes(user.uid, com.clarity.finance.data.model.ReportingMonth(month)).collect { allIncomes ->
                            val monthStartStr = month.atDay(1).toString()
                            val nextMonthStartStr = month.plusMonths(1).atDay(1).toString()
                            
                            val filteredIncomes = allIncomes.filter { 
                                it.receiptDate >= monthStartStr && it.receiptDate < nextMonthStartStr 
                            }

                            val confirmedTotal = filteredIncomes.filter { !it.isPending && it.syncError == null && !it.isConflict }.fold(0L) { total, row -> Math.addExact(total, row.lkrAmountMinor) }
                            val pendingTotal = filteredIncomes.filter { it.isPending }.fold(0L) { total, row -> Math.addExact(total, row.lkrAmountMinor) }
                            
                            val bySource = filteredIncomes.filter { !it.isPending && it.syncError == null && !it.isConflict }
                                .groupBy { it.sourceId }
                                .mapValues { entry -> entry.value.fold(0L) { total, row -> Math.addExact(total, row.lkrAmountMinor) } }

                            emit(IncomeListUiState.Success(
                                incomes = filteredIncomes,
                                confirmedTotalLkr = confirmedTotal,
                                pendingTotalLkr = pendingTotal,
                                totalsBySource = bySource,
                                currentMonth = month
                            ))
                        } } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { emit(IncomeListUiState.Error(e.message ?: "Income unavailable; reconnect to retry.")) }
                        kotlinx.coroutines.delay(5000)
                    }
                    }
                }
            } else {
                flowOf(IncomeListUiState.SignedOut)
            }
        }
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
            val user = authRepository.authState.first()
            if (user != null) incomeRepository.retryFailedIncome(user.uid, income)
        }
    }
}
