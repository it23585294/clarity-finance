package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getExpenses(userId: String): Flow<List<Expense>>
    suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit>
    fun retryFailedExpense(userId: String, expense: Expense)
}
