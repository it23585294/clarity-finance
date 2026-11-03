package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface IncomeRepository {
    fun getIncomes(userId: String): Flow<List<Income>>
    fun getIncomes(userId: String, period: com.clarity.finance.data.model.ReportingMonth): Flow<List<Income>> =
        getIncomes(userId).map { rows -> rows.filter { period.contains(it.receiptDate) } }
    suspend fun enqueueIncome(userId: String, income: Income): Result<Unit>
    fun retryFailedIncome(userId: String, income: Income)
    suspend fun getIncome(userId: String, incomeId: String): Result<Income>
    suspend fun updateIncome(userId: String, income: Income, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit>
    suspend fun deleteIncome(userId: String, incomeId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit>
}
