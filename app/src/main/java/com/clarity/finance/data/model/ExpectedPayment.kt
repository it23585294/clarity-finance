package com.clarity.finance.data.model

import com.google.firebase.Timestamp

data class ExpectedPayment(
    val id: String,
    val type: String, // "standalone" or "project"
    val title: String,
    val sourceId: String,
    val currency: String,
    val dueDate: String,
    val agreedAmountMinor: Long,
    val allocatedAmountMinor: Long = 0L,
    val directReceivedAmountMinor: Long = 0L,
    val milestoneReceivedAmountMinor: Long = 0L,
    val isCancelled: Boolean = false,
    val note: String = "",
    val lastReceiptId: String? = null,
    val lastMilestoneId: String? = null,
    val lastMilestoneOpId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val totalReceivedAmountMinor: Long
        get() = Math.addExact(directReceivedAmountMinor, milestoneReceivedAmountMinor)
        
    val outstandingAmountMinor: Long
        get() = Math.subtractExact(agreedAmountMinor, totalReceivedAmountMinor)

    val unallocatedAmountMinor: Long
        get() = Math.subtractExact(agreedAmountMinor, allocatedAmountMinor)
}

data class Milestone(
    val id: String,
    val paymentId: String,
    val title: String,
    val dueDate: String,
    val agreedAmountMinor: Long,
    val receivedAmountMinor: Long = 0L,
    val isCancelled: Boolean = false,
    val lastReceiptId: String? = null,
    val lastMilestoneOpId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val outstandingAmountMinor: Long
        get() = Math.subtractExact(agreedAmountMinor, receivedAmountMinor)
}

data class PaymentConfirmationPayload(
    val paymentId: String,
    val milestoneId: String?,
    val income: Income
)
