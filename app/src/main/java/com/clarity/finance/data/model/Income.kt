package com.clarity.finance.data.model

data class Income(
    val id: String,
    val sourceId: String,
    val receiptDate: String, // yyyy-MM-dd
    val currency: String,
    val originalAmountStr: String,
    val lkrAmountMinor: Long,
    val conversionBasis: String,
    val conversionRateStr: String?,
    val note: String,
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null,
    
    // UI-only metadata
    val isPending: Boolean = false,
    val syncError: String? = null,
    val isConflict: Boolean = false
)
