package com.clarity.finance.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.clarity.finance.viewmodel.AddExpenseViewModel
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.viewmodel.EditExpenseViewModel
import com.clarity.finance.viewmodel.ExpenseListViewModel

class ViewModelFactory(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(authRepository) as T
        }
        if (modelClass.isAssignableFrom(ExpenseListViewModel::class.java)) {
            return ExpenseListViewModel(authRepository, expenseRepository) as T
        }
        if (modelClass.isAssignableFrom(AddExpenseViewModel::class.java)) {
            return AddExpenseViewModel(authRepository, expenseRepository) as T
        }
        if (modelClass.isAssignableFrom(EditExpenseViewModel::class.java)) {
            return EditExpenseViewModel(authRepository, expenseRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
