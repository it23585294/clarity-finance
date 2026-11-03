package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Expense
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringExpenseCompatibilityTest {
    private val fixture = FinancialRemoteFixture()
    private val timestamp = Timestamp(1, 0)
    private val path = fixture.path("expenses", "rec_rent_2026-01")
    private fun setup() { fixture.documents[path] = mapOf("amountMinor" to 3400000L, "currency" to "LKR", "categoryId" to "housing",
        "paymentChannelId" to "cash", "spendingType" to "committed", "expenseDate" to "2026-01-31", "note" to "Old note",
        "createdAt" to timestamp, "updatedAt" to timestamp, "recurringOccurrenceId" to "rent_2026-01") }
    private fun operation() = FinancialOperation("edit", "EXPENSE", "UPDATE", "rec_rent_2026-01", expectedUpdatedAt = timestamp,
        expense = Expense("rec_rent_2026-01", 3300000, categoryId = "utilities", paymentChannelId = "primary_bank_card",
            spendingType = "discretionary", expenseDate = "2025-12-31", note = "", recurringOccurrenceId = "rent_2026-01"))
    @Test fun `existing production expense update backdates corrects fields clears note and preserves immutable origin and createdAt`() = runTest {
        setup(); fixture.remote.apply("alice", operation())
        val after = fixture.documents[path]!!
        assertEquals(3300000L, after["amountMinor"]); assertEquals("2025-12-31", after["expenseDate"])
        assertEquals("", after["note"]); assertEquals("utilities", after["categoryId"])
        assertEquals(timestamp, after["createdAt"]); assertEquals("rent_2026-01", after["recurringOccurrenceId"])
        assertTrue(fixture.writes.none { it.second.contains("recurringOccurrences") || it.second.contains("recurringOperations") })
    }
    @Test fun `deletion removes only actual expense not immutable occurrence history`() = runTest {
        setup(); fixture.remote.apply("alice", FinancialOperation("delete", "EXPENSE", "DELETE", "rec_rent_2026-01", expectedUpdatedAt = timestamp))
        assertFalse(fixture.documents.containsKey(path)); assertEquals(true, fixture.documents[fixture.path("syncStates/EXPENSE/records", "rec_rent_2026-01")]!!["deleted"])
        assertTrue(fixture.writes.none { it.second.contains("recurringOccurrences") || it.second.contains("recurringOperations") })
    }
    @Test fun `cancelled update await propagates and never returns false success`() = runTest {
        setup(); val source = TaskCompletionSource<Any>(); fixture.taskGate = source
        val job = backgroundScope.launch { fixture.remote.apply("alice", operation()); fail("Cancellation must propagate") }
        runCurrent(); job.cancelAndJoin(); assertTrue(job.isCancelled); assertFalse(source.task.isComplete)
    }
}
