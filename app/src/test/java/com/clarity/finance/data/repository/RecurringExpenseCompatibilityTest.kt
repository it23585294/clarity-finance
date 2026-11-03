package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.Expense
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringExpenseCompatibilityTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val users = mock(CollectionReference::class.java)
    private val owner = mock(DocumentReference::class.java)
    private val expenses = mock(CollectionReference::class.java)
    private val document = mock(DocumentReference::class.java)
    private val snapshot = mock(DocumentSnapshot::class.java)
    private val tx = mock(Transaction::class.java)
    private val recovery = mock(SubmissionRecoveryStore::class.java)
    private val repo = FirestoreExpenseRepository(firestore, recovery)
    private val timestamp = Timestamp(1, 0)
    @Suppress("UNCHECKED_CAST") private fun setup() {
        `when`(firestore.collection("users")).thenReturn(users); `when`(users.document("alice")).thenReturn(owner)
        `when`(owner.collection("expenses")).thenReturn(expenses); `when`(expenses.document("rec_rent_2026-01")).thenReturn(document)
        `when`(tx.get(document)).thenReturn(snapshot); `when`(snapshot.exists()).thenReturn(true)
        `when`(snapshot.getTimestamp("updatedAt")).thenReturn(timestamp)
        doAnswer { args ->
            args.getArgument<Transaction.Function<Unit>>(0).apply(tx)
            (mock(Task::class.java) as Task<Unit>).also { task ->
                `when`(task.isComplete).thenReturn(true); `when`(task.isSuccessful).thenReturn(true); `when`(task.result).thenReturn(Unit)
            }
        }.`when`(firestore).runTransaction(any<Transaction.Function<Unit>>())
    }
    @Test fun `existing production expense update backdates corrects fields clears note and preserves immutable origin and createdAt`() = runTest {
        setup()
        val edited = Expense("rec_rent_2026-01", 3300000, categoryId = "utilities", paymentChannelId = "primary_bank_card",
            spendingType = "discretionary", expenseDate = "2025-12-31", note = "", recurringOccurrenceId = "rent_2026-01")
        assertTrue(repo.updateExpense("alice", edited, timestamp).isSuccess)
        val fields = argumentCaptor<Map<String, Any>>()
        verify(tx).update(eq(document), fields.capture())
        assertEquals(3300000L, fields.firstValue["amountMinor"]); assertEquals("2025-12-31", fields.firstValue["expenseDate"])
        assertEquals("", fields.firstValue["note"]); assertEquals("utilities", fields.firstValue["categoryId"])
        assertFalse(fields.firstValue.containsKey("createdAt")); assertFalse(fields.firstValue.containsKey("recurringOccurrenceId"))
        verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        verify(owner, never()).collection("recurringOccurrences"); verify(owner, never()).collection("recurringOperations")
    }
    @Test fun `deletion removes only actual expense not immutable occurrence history`() = runTest {
        setup(); assertTrue(repo.deleteExpense("alice", "rec_rent_2026-01", timestamp).isSuccess)
        verify(tx).delete(document); verify(owner, never()).collection("recurringOccurrences")
    }
    @Test fun `cancelled update await propagates and never returns false success`() = runTest {
        setup(); val source = TaskCompletionSource<Unit>()
        doReturn(source.task).`when`(firestore).runTransaction(any<Transaction.Function<Unit>>())
        val job = backgroundScope.launch {
            repo.updateExpense("alice", Expense("rec_rent_2026-01", 1, categoryId = "housing", paymentChannelId = "cash", spendingType = "committed", expenseDate = "2026-01-31"), timestamp)
            fail("Cancellation must propagate")
        }
        runCurrent(); job.cancelAndJoin(); assertTrue(job.isCancelled); assertFalse(source.task.isComplete)
    }
}
