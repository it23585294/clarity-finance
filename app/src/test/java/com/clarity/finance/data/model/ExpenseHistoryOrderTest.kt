package com.clarity.finance.data.model

import com.clarity.finance.data.repository.FinancialOperation
import com.google.firebase.Timestamp
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ExpenseHistoryOrderTest {
    private fun row(id: String, day: String = "2026-11-05", created: Long? = null) = Expense(id, 10000,
        categoryId = "other", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = day,
        createdAt = created?.let { Timestamp(it, 0) })

    @Test fun `same date uses newest creation not id or last edit and unknown time stays last`() {
        val older = row("a", created = 100).copy(updatedAt = Timestamp(9999, 0))
        val newer = row("z", created = 200).copy(updatedAt = Timestamp(200, 0))
        val pending = row("pending", created = 300).copy(isPending = true)
        val rows = listOf(row("unknown"), older, pending, newer).sortedWith(ExpenseHistoryOrder.newestFirst)
        assertEquals(listOf("z", "a", "pending", "unknown"), rows.map { it.id })
        assertEquals(listOf("z", "a"), listOf(newer, older.copy(updatedAt = Timestamp(20000, 0)))
            .sortedWith(ExpenseHistoryOrder.newestFirst).map { it.id })
    }

    @Test fun `actual dates lead despite late backdated uploads and timestamp ties have stable identity fallback`() {
        val backdated = row("backdated", "2026-10-31", 999)
        val today = row("today", created = 1)
        assertEquals(listOf("today", "backdated"), listOf(backdated, today).sortedWith(ExpenseHistoryOrder.newestFirst).map { it.id })
        assertEquals(listOf("a", "z"), listOf(row("z", created = 1), row("a", created = 1))
            .sortedWith(ExpenseHistoryOrder.newestFirst).map { it.id })
    }

    @Test fun `read metadata never enters financial payload or durable immutable JSON`() {
        val bare = row("e")
        val decorated = bare.copy(createdAt = Timestamp(100, 0))
        val before = FinancialOperation("op", "EXPENSE", "CREATE", bare.id, expense = bare)
        val after = before.copy(expense = decorated)
        assertEquals(before.payload(), after.payload())
        assertFalse(after.payload().containsKey("createdAt"))
        assertEquals(Gson().toJson(before), Gson().toJson(after))
        assertNull(Gson().fromJson(Gson().toJson(decorated), Expense::class.java).createdAt)
    }
}
