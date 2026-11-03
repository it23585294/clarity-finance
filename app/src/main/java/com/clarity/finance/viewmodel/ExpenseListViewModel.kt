package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
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
    val expensesState: StateFlow<ExpensesUiState> = authRepository.authState.flatMapLatest { user ->
        if (user == null) flowOf(ExpensesUiState.SignedOut)
        else kotlinx.coroutines.flow.flow<ExpensesUiState> {
            emit(ExpensesUiState.Loading) // Clear the old owner's rows before waiting for a new listener.
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                try { expenseRepository.getExpenses(user.uid).collect { emit(ExpensesUiState.Success(it)) } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { emit(ExpensesUiState.Error(e.message ?: "Expenses unavailable; reconnect to retry.")) }
                kotlinx.coroutines.delay(5000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpensesUiState.Loading)

    fun retryExpense(expense: Expense) {
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (user != null) {
                expenseRepository.retryFailedExpense(user.uid, expense)
            }
        }
    }
}
