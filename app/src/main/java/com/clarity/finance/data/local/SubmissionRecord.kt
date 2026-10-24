package com.clarity.finance.data.local

import com.clarity.finance.data.model.Expense
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class SubmissionRecord(
    val uid: String,
    @SerializedName("expenseId") val recordId: String,
    val payloadJson: String,
    val status: String, // "PENDING", "FAILED", "CONFLICT"
    val errorReason: String? = null,
    val type: String? = null // Defaults to null for backward compatibility
) {
    val resolvedType: String get() = type ?: "EXPENSE"

    fun toExpense(): Expense? {
        if (resolvedType != "EXPENSE") return null
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
        fun create(uid: String, recordId: String, payload: Any, status: String, type: String, errorReason: String? = null): SubmissionRecord {
            return SubmissionRecord(
                uid = uid,
                recordId = recordId,
                payloadJson = Gson().toJson(payload),
                status = status,
                errorReason = errorReason,
                type = type
            )
        }

        fun fromExpense(uid: String, expense: Expense, status: String, errorReason: String? = null): SubmissionRecord {
            return create(uid, expense.id, expense, status, "EXPENSE", errorReason)
        }
    }
}
