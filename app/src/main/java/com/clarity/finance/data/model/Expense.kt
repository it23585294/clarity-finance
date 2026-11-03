package com.clarity.finance.data.model

data class Expense(
    val id: String,
    val amountMinor: Long,
    val currency: String = "LKR",
    val categoryId: String,
    val paymentChannelId: String,
    val spendingType: String,
    val expenseDate: String, // yyyy-MM-dd
    val note: String? = null,
    val updatedAt: com.google.firebase.Timestamp? = null,
    
    // UI-only metadata (not saved explicitly to Firestore via Map)
    val isPending: Boolean = false,
    val syncError: String? = null,
    val isConflict: Boolean = false,
    val recurringOccurrenceId: String? = null, // Immutable origin link; confirmation is retained after correction/deletion.
    val isFromCache: Boolean = false,
    val queuedChange: String? = null
)
