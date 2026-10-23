package com.clarity.finance.data.local

import com.clarity.finance.data.model.Expense
import com.google.gson.Gson

data class SubmissionRecord(
    val uid: String,
    val expenseId: String,
    val payloadJson: String,
    val status: String, // "PENDING", "FAILED", "CONFLICT"
    val errorReason: String? = null
) {
    fun toExpense(): Expense? {
        return try {
            val expense = Gson().fromJson(payloadJson, Expense::class.java)
            expense.copy(
                isPending = status == "PENDING",
                syncError = if (status == "FAILED" || status == "CONFLICT") errorReason ?: "Failed" else null,
                isConflict = status == "CONFLICT"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    companion object {
        fun fromExpense(uid: String, expense: Expense, status: String, errorReason: String? = null): SubmissionRecord {
            return SubmissionRecord(
                uid = uid,
                expenseId = expense.id,
                payloadJson = Gson().toJson(expense),
                status = status,
                errorReason = errorReason
            )
        }
    }
}
