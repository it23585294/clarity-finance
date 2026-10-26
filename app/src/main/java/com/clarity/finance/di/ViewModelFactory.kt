package com.clarity.finance.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.clarity.finance.data.repository.IncomeRepository
import com.clarity.finance.data.repository.PaymentRepository
import com.clarity.finance.data.repository.CryptoRepository
import com.clarity.finance.viewmodel.CryptoViewModel
import com.clarity.finance.data.repository.ReportingRepository
import com.clarity.finance.viewmodel.DashboardViewModel
import com.clarity.finance.viewmodel.AddExpenseViewModel
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.viewmodel.EditExpenseViewModel
import com.clarity.finance.viewmodel.ExpenseListViewModel
import com.clarity.finance.viewmodel.HomeViewModel
import com.clarity.finance.viewmodel.IncomeListViewModel
import com.clarity.finance.viewmodel.AddEditIncomeViewModel
import com.clarity.finance.viewmodel.PaymentListViewModel
import com.clarity.finance.viewmodel.AddPaymentViewModel
import com.clarity.finance.viewmodel.PaymentDetailViewModel
import com.clarity.finance.viewmodel.PaymentConfirmationViewModel

class ViewModelFactory(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val paymentRepository: PaymentRepository,
    private val cryptoRepository: CryptoRepository,
    private val reportingRepository: ReportingRepository? = null,
    private val goalRepository: com.clarity.finance.data.repository.GoalRepository? = null
) : ViewModelProvider.Factory {
    val hasGoals get() = goalRepository != null
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass == com.clarity.finance.viewmodel.GoalCardViewModel::class.java)
            return com.clarity.finance.viewmodel.GoalCardViewModel(authRepository, requireNotNull(goalRepository)) as T
        if (modelClass == com.clarity.finance.viewmodel.GoalViewModel::class.java)
            return com.clarity.finance.viewmodel.GoalViewModel(authRepository, requireNotNull(goalRepository)) as T
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(authRepository, requireNotNull(reportingRepository)) as T
        }
        if (modelClass.isAssignableFrom(CryptoViewModel::class.java)) {
            return CryptoViewModel(authRepository, cryptoRepository) as T
        }
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
        if (modelClass.isAssignableFrom(PaymentListViewModel::class.java)) {
            return PaymentListViewModel(authRepository, paymentRepository) as T
        }
        if (modelClass.isAssignableFrom(AddPaymentViewModel::class.java)) {
            return AddPaymentViewModel(authRepository, paymentRepository) as T
        }
        if (modelClass.isAssignableFrom(PaymentDetailViewModel::class.java)) {
            return PaymentDetailViewModel(authRepository, paymentRepository) as T
        }
        if (modelClass.isAssignableFrom(PaymentConfirmationViewModel::class.java)) {
            return PaymentConfirmationViewModel(authRepository, paymentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
