package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ExpensesUiState {
    object Loading : ExpensesUiState
    object SignedOut : ExpensesUiState
    data class Success(val expenses: List<Expense>) : ExpensesUiState
    data class Error(val message: String) : ExpensesUiState
}

class ExpenseListViewModel(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val expensesState: StateFlow<ExpensesUiState> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                expenseRepository.getExpenses(user.uid)
                    .map { ExpensesUiState.Success(it) }
            } else {
                flowOf(ExpensesUiState.SignedOut)
            }
        }
        .catch { emit(ExpensesUiState.Error(it.message ?: "Failed to load expenses")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExpensesUiState.Loading
        )

    fun retryExpense(expense: Expense) {
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (user != null) {
                expenseRepository.retryFailedExpense(user.uid, expense)
            }
        }
    }
}
