package com.clarity.finance.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.clarity.finance.data.repository.IncomeRepository
import com.clarity.finance.viewmodel.AddExpenseViewModel
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.viewmodel.EditExpenseViewModel
import com.clarity.finance.viewmodel.ExpenseListViewModel
import com.clarity.finance.viewmodel.HomeViewModel
import com.clarity.finance.viewmodel.IncomeListViewModel
import com.clarity.finance.viewmodel.AddEditIncomeViewModel

class ViewModelFactory(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository
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
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(authRepository) as T
        }
        if (modelClass.isAssignableFrom(IncomeListViewModel::class.java)) {
            return IncomeListViewModel(authRepository, incomeRepository) as T
        }
        if (modelClass.isAssignableFrom(AddEditIncomeViewModel::class.java)) {
            return AddEditIncomeViewModel(authRepository, incomeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
