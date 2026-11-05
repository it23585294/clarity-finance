package com.clarity.finance.data.model

import com.google.firebase.Timestamp

/** Display order only. Never feeds totals, immutable operations, receipts or reconciliation. */
object TransactionHistoryOrder {
    private fun <T> order(date: (T) -> String, created: (T) -> Timestamp?, id: (T) -> String): Comparator<T> =
        compareByDescending(date).thenByDescending(created).thenBy(id)

    val expenses: Comparator<Expense> = order({ it.expenseDate }, { if (it.isPending) null else it.createdAt }, { it.id })
    val income: Comparator<Income> = order({ it.receiptDate }, { if (it.isPending) null else it.createdAt }, { it.id })
    val crypto: Comparator<CryptoResult> = order({ it.resultDate }, {
        if (it.sync in setOf(CryptoSync.CONFIRMED, CryptoSync.CACHED)) it.createdAt else null
    }, { it.id })
}
