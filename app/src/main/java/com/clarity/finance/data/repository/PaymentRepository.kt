package com.clarity.finance.data.repository

import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.Milestone
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>>
    fun getMilestones(userId: String, paymentId: String): Flow<List<Milestone>>
    suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment>

    suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit>
    suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit>
    suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit>

    suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit>
    suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit>
    suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit>

    suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit>
    fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income)
}
