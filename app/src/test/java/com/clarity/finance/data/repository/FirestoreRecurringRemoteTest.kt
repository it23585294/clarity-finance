package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.catch
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRecurringRemoteTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val users = mock(CollectionReference::class.java)
    private val owner = mock(DocumentReference::class.java)
    private val templates = mock(CollectionReference::class.java)
    private val operations = mock(CollectionReference::class.java)
    private val occurrences = mock(CollectionReference::class.java)
    private val expenses = mock(CollectionReference::class.java)
    private val templateRef = mock(DocumentReference::class.java)
    private val receiptRef = mock(DocumentReference::class.java)
    private val occurrenceRef = mock(DocumentReference::class.java)
    private val expenseRef = mock(DocumentReference::class.java)
    private val tx = mock(Transaction::class.java)
    private val templateDoc = mock(DocumentSnapshot::class.java)
    private val receiptDoc = mock(DocumentSnapshot::class.java)
    private val occurrenceDoc = mock(DocumentSnapshot::class.java)
    private val expenseDoc = mock(DocumentSnapshot::class.java)
    private var uid = "alice"
    private val clock = Clock.fixed(Instant.parse("2026-03-31T00:00:00Z"), ZoneOffset.UTC)
    private val remote = FirestoreRecurringRemote(firestore, clock) { uid }
    private val config = RecurringConfig("Rent", "housing", "committed", "cash", 3400000, "2026-01-31", 31)
    private val confirmation = RecurringConfirmation("rent", "2026-03", "2026-03-31", 0, "Rent", ActualRecurringExpense(3400000, "2026-03-30", "housing", "committed", "cash"))
    private val op = RecurringOperation("confirm", "CONFIRM", "rent", 0, confirmation = confirmation)
    @Suppress("UNCHECKED_CAST") private fun setup() {
        `when`(firestore.collection("users")).thenReturn(users); `when`(users.document("alice")).thenReturn(owner)
        for ((name, collection) in mapOf("recurringTemplates" to templates, "recurringOperations" to operations, "recurringOccurrences" to occurrences, "expenses" to expenses))
            `when`(owner.collection(name)).thenReturn(collection)
        `when`(templates.document(any())).thenReturn(templateRef); `when`(operations.document(any())).thenReturn(receiptRef)
        `when`(occurrences.document(any())).thenReturn(occurrenceRef); `when`(expenses.document(any())).thenReturn(expenseRef)
        `when`(tx.get(templateRef)).thenReturn(templateDoc); `when`(tx.get(receiptRef)).thenReturn(receiptDoc)
        `when`(tx.get(occurrenceRef)).thenReturn(occurrenceDoc); `when`(tx.get(expenseRef)).thenReturn(expenseDoc)
        `when`(templateDoc.id).thenReturn("rent")
        doAnswer { args ->
            val value = args.getArgument<Transaction.Function<RecurringOutcome>>(0).apply(tx)
            val task = mock(Task::class.java) as Task<RecurringOutcome>
            `when`(task.isComplete).thenReturn(true); `when`(task.isSuccessful).thenReturn(true); `when`(task.result).thenReturn(value); task
        }.`when`(firestore).runTransaction(any<Transaction.Function<RecurringOutcome>>())
    }
    private fun fields(doc: DocumentSnapshot, values: Map<String, Any>) {
        `when`(doc.exists()).thenReturn(true)
        `when`(doc.get(any<String>())).thenAnswer { values[it.getArgument<String>(0)] }
    }
    private fun existing(version: Long = 0, cancelled: Boolean = false, c: RecurringConfig = config) {
        fields(templateDoc, c.values() + mapOf("version" to version, "isCancelled" to cancelled, "lastOperationId" to "create",
            "createdAt" to Timestamp(1, 0), "updatedAt" to Timestamp(2, 0)))
    }
    private fun accepted(c: RecurringConfirmation = confirmation) {
        `when`(occurrenceDoc.id).thenReturn(c.id)
        fields(occurrenceDoc, c.values() + mapOf("operationId" to "another-client", "createdAt" to Timestamp(2, 0)))
    }
    @Test fun `production AT09 confirmation atomically writes one exact ordinary expense confirmation and receipt`() = runTest {
        setup(); existing()
        assertEquals(RecurringOutcome.Confirmed, remote.apply("alice", op))
        val data = argumentCaptor<Map<String, Any>>()
        verify(tx).set(eq(expenseRef), data.capture())
        assertEquals(3400000L, data.firstValue["amountMinor"])
        assertEquals("2026-03-30", data.firstValue["expenseDate"])
        assertEquals("rent_2026-03", data.firstValue["recurringOccurrenceId"])
        assertEquals(FieldValue.serverTimestamp(), data.firstValue["createdAt"])
        verify(tx).set(eq(occurrenceRef), eq(confirmation.values() + mapOf("operationId" to "confirm", "createdAt" to FieldValue.serverTimestamp())))
        verify(tx).set(eq(receiptRef), any<Map<String, Any>>()); verify(tx, never()).update(eq(templateRef), any<Map<String, Any>>())
        verify(owner, never()).collection("incomeReceipts"); verify(owner, never()).collection("cryptoResults"); verify(owner, never()).collection("savingsGoals")
        verify(expenses).document(confirmation.expenseId)
    }
    @Test fun `variable actual classifications date note and amount replace suggested values without changing due identity`() = runTest {
        setup(); existing(c = config.copy(suggestedMinor = 500000))
        val actual = confirmation.copy(actual = confirmation.actual.copy(amountMinor = 625050, categoryId = "utilities", spendingType = "discretionary",
            paymentChannelId = "primary_bank_card", note = "Actual utility bill", expenseDate = "2026-02-28"))
        remote.apply("alice", op.copy(confirmation = actual))
        val data = argumentCaptor<Map<String, Any>>(); verify(tx).set(eq(expenseRef), data.capture())
        assertEquals(actual.actual.values(), data.firstValue.filterKeys { it in actual.actual.values() })
        assertEquals(confirmation.id, actual.id); assertEquals("2026-03-31", actual.dueDate)
    }
    @Test fun `matching confirmation reconciles before changed or cancelled template and corrected or deleted actual expense`() = runTest {
        setup(); accepted()
        assertEquals(RecurringOutcome.Confirmed, remote.apply("alice", op.copy(operationId = "new-client")))
        verify(tx, never()).get(templateRef); verify(tx, never()).get(expenseRef); verify(tx, never()).get(receiptRef)
        verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
    }
    @Test fun `different accepted payload is a server-verified conflict and cannot create another expense`() = runTest {
        setup(); accepted(confirmation.copy(actual = confirmation.actual.copy(amountMinor = 1)))
        assertTrue(remote.apply("alice", op) is RecurringOutcome.Rejected)
        verify(tx, never()).get(templateRef); verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
    }
    @Test fun `creation edit and cancellation write immutable receipts preserve first date and never create spending`() = runTest {
        setup()
        remote.apply("alice", RecurringOperation("create", "CREATE", "rent", config = config))
        val data = argumentCaptor<Map<String, Any>>(); verify(tx).set(eq(templateRef), data.capture())
        assertEquals(false, data.firstValue["isCancelled"]); assertEquals(0L, data.firstValue["version"])
        existing(); clearInvocations(tx)
        remote.apply("alice", RecurringOperation("edit", "EDIT", "rent", 0, config.copy(anchorDay = 5, suggestedMinor = 3500000)))
        verify(tx).update(eq(templateRef), data.capture()); assertEquals(config.firstDueDate, data.lastValue["firstDueDate"])
        assertFalse(data.lastValue.containsKey("createdAt"))
        clearInvocations(tx)
        remote.apply("alice", RecurringOperation("cancel", "CANCEL", "rent", 0, config))
        verify(tx).update(eq(templateRef), data.capture()); assertEquals(true, data.lastValue["isCancelled"])
        verify(tx, never()).set(eq(expenseRef), any<Map<String, Any>>())
    }
    @Test fun `monotonic template changes prove rejection but missing state and raw failures do not`() = runTest {
        setup(); existing(version = 1)
        assertTrue(remote.apply("alice", op) is RecurringOutcome.Rejected)
        existing(cancelled = true); assertTrue(remote.apply("alice", op) is RecurringOutcome.Rejected)
        `when`(templateDoc.exists()).thenReturn(false)
        try { remote.apply("alice", op); fail() } catch (_: IllegalStateException) {}
        verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
    }
    @Test fun `timed out Task survives account error is reused and refuses a changed immutable payload`() = runTest {
        setup()
        val source = TaskCompletionSource<RecurringOutcome>()
        doReturn(source.task).`when`(firestore).runTransaction(any<Transaction.Function<RecurringOutcome>>())
        val first = backgroundScope.launch { assertNull(withTimeoutOrNull(10000) { remote.apply("alice", op) }) }
        runCurrent(); advanceTimeBy(10001); runCurrent(); first.join(); assertFalse(source.task.isComplete)
        try { remote.apply("alice", op.copy(confirmation = confirmation.copy(actual = confirmation.actual.copy(amountMinor = 1)))); fail() } catch (_: IllegalStateException) {}
        uid = "bob"; try { remote.apply("alice", op); fail() } catch (_: IllegalStateException) {}
        uid = "alice"
        var result: RecurringOutcome? = null
        val later = backgroundScope.launch { result = remote.apply("alice", op) }; runCurrent()
        verify(firestore, times(1)).runTransaction(any<Transaction.Function<RecurringOutcome>>())
        source.setResult(RecurringOutcome.Confirmed); runCurrent(); later.join(); assertEquals(RecurringOutcome.Confirmed, result)
    }
    @Test fun `bounded template and twelve-month queries include metadata and unregister listeners`() = runTest {
        setup()
        val templateQuery = mock(Query::class.java); val historyQuery = mock(Query::class.java)
        val a = mock(ListenerRegistration::class.java); val b = mock(ListenerRegistration::class.java)
        `when`(templates.orderBy(any<FieldPath>())).thenReturn(templateQuery); `when`(templateQuery.limit(26)).thenReturn(templateQuery)
        `when`(occurrences.whereEqualTo("templateId", "rent")).thenReturn(historyQuery)
        `when`(historyQuery.whereGreaterThanOrEqualTo("month", "2024-01")).thenReturn(historyQuery)
        `when`(historyQuery.whereLessThan("month", "2025-01")).thenReturn(historyQuery); `when`(historyQuery.orderBy("month")).thenReturn(historyQuery)
        `when`(templateQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(a)
        `when`(historyQuery.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(b)
        val t = backgroundScope.launch { remote.templates("alice", 25).collect {} }
        val h = backgroundScope.launch { remote.occurrences("alice", "rent", "2024-01", "2025-01").collect {} }
        runCurrent(); t.cancelAndJoin(); h.cancelAndJoin(); verify(a).remove(); verify(b).remove()
        verify(templateQuery).limit(26); verify(historyQuery, never()).limit(any<Long>())
    }
    @Test fun `production listener retains clean cached values across pending snapshots and reports uncached absence and errors honestly`() = runTest {
        setup(); existing()
        val query = mock(Query::class.java); val registration = mock(ListenerRegistration::class.java)
        `when`(templates.orderBy(any<FieldPath>())).thenReturn(query); `when`(query.limit(26)).thenReturn(query)
        `when`(query.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(registration)
        val docs = mock(QuerySnapshot::class.java); val meta = mock(SnapshotMetadata::class.java)
        `when`(docs.metadata).thenReturn(meta); `when`(docs.documents).thenReturn(emptyList())
        `when`(templateDoc.metadata).thenReturn(mock(SnapshotMetadata::class.java))
        var latest = ReportingBatch<RecurringTemplate>(); var failure: Throwable? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            remote.templates("alice", 25).catch { failure = it }.collect { latest = it }
        }
        runCurrent(); val listener = argumentCaptor<EventListener<QuerySnapshot>>()
        verify(query).addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture())
        `when`(meta.isFromCache).thenReturn(true)
        listener.firstValue.onEvent(docs, null); runCurrent(); assertNull(latest.records); assertNotNull(latest.error)
        `when`(meta.isFromCache).thenReturn(false); `when`(docs.documents).thenReturn(listOf(templateDoc))
        listener.firstValue.onEvent(docs, null); runCurrent(); assertEquals(3400000L, latest.records!!.single().config.suggestedMinor)
        `when`(meta.hasPendingWrites()).thenReturn(true); `when`(docs.documents).thenReturn(emptyList())
        listener.firstValue.onEvent(docs, null); runCurrent(); assertTrue(latest.awaitingWrites); assertEquals(1, latest.records!!.size)
        val denied = mock(FirebaseFirestoreException::class.java)
        listener.firstValue.onEvent(null, denied); runCurrent(); assertSame(denied, failure); verify(registration).remove()
        job.cancelAndJoin()
    }
}
