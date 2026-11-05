package com.clarity.finance.data.model

import com.clarity.finance.data.repository.CryptoOperation
import com.clarity.finance.data.repository.FinancialOperation
import com.google.firebase.Timestamp
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class TransactionHistoryOrderTest {
    private fun income(id: String, date: String = "2026-11-05", created: Long? = null) = Income(id, "salary", date,
        "LKR", "100", 10000, "native_lkr", "1", "", createdAt = created?.let { Timestamp(it, 0) })
    private fun crypto(id: String, date: String = "2026-11-05", created: Long? = null) = CryptoResult(id, "gain", 10000,
        date, createdAt = created?.let { Timestamp(it, 0) })

    @Test fun `income date leads creation with descending within date and deterministic missing or equal fallback`() {
        val rows = listOf(income("missing-z"), income("a-old", created = 1).copy(updatedAt = Timestamp(999, 0)),
            income("z-new", created = 2), income("missing-a"), income("equal-z", created = 1),
            income("backdated", "2026-10-31", 999), income("equal-a", created = 1))
        assertEquals(listOf("z-new", "a-old", "equal-a", "equal-z", "missing-a", "missing-z", "backdated"),
            rows.sortedWith(TransactionHistoryOrder.income).map { it.id })
        assertEquals(listOf("z-new", "a-old"), listOf(rows[1].copy(note = "Corrected", updatedAt = Timestamp(9999, 0)), rows[2])
            .sortedWith(TransactionHistoryOrder.income).map { it.id })
        assertEquals("z-new", listOf(rows[1], rows[2].copy(receiptDate = "2026-11-06")).sortedWith(TransactionHistoryOrder.income).first().id)
    }

    @Test fun `crypto uses same date creation and fallback policy independently of edit and backdate`() {
        val rows = listOf(crypto("missing-z"), crypto("a-old", created = 1), crypto("z-new", created = 2),
            crypto("missing-a"), crypto("equal-z", created = 1), crypto("backdated", "2025-12-31", 999), crypto("equal-a", created = 1))
        assertEquals(listOf("z-new", "a-old", "equal-a", "equal-z", "missing-a", "missing-z", "backdated"),
            rows.sortedWith(TransactionHistoryOrder.crypto).map { it.id })
        assertEquals(listOf("z-new", "a-old"), listOf(rows[1].copy(amountMinor = 20000, updatedAt = Timestamp(9999, 0)), rows[2])
            .sortedWith(TransactionHistoryOrder.crypto).map { it.id })
        assertEquals(listOf("a-old", "z-new"), listOf(rows[1], rows[2].copy(resultDate = "2026-10-31"))
            .sortedWith(TransactionHistoryOrder.crypto).map { it.id })
    }

    @Test fun `read sorting does not rewrite existing metadata operation bytes identity or business payloads`() {
        val gson = Gson()
        val receipt = income("linked", created = 100).copy(linkedPaymentId = "project", linkedMilestoneId = "milestone", originalAmountMinor = 10000)
        val result = crypto("c", created = 100)
        val financial = FinancialOperation("receipt-op", "RECEIPT", "CREATE", receipt.id, "project", income = receipt)
        val legacyCrypto = CryptoOperation("legacy-op", "CREATE", result, protocolVersion = 1)
        val receiptBytes = gson.toJson(financial)
        val legacyBytes = gson.toJson(legacyCrypto)
        val sortedIncome = listOf(income("new", created = 200), receipt).sortedWith(TransactionHistoryOrder.income)
        val sortedCrypto = listOf(crypto("new", created = 200), result).sortedWith(TransactionHistoryOrder.crypto)
        assertSame(receipt, sortedIncome.last()); assertSame(result, sortedCrypto.last())
        assertEquals(receiptBytes, gson.toJson(financial)); assertEquals(legacyBytes, gson.toJson(legacyCrypto))
        assertEquals(financial, gson.fromJson(receiptBytes, FinancialOperation::class.java))
        assertEquals(legacyCrypto, gson.fromJson(legacyBytes, CryptoOperation::class.java))
        assertFalse(financial.payload().containsKey("createdAt"))
        assertFalse(FinancialOperation("c-op", "CRYPTO", "CREATE", result.id, crypto = result).payload().containsKey("createdAt"))
        assertEquals("receipt-op", financial.operationId); assertEquals("legacy-op", legacyCrypto.operationId)
    }
}
