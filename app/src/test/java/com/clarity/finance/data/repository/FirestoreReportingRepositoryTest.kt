package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.SubmissionRecord
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreReportingRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val firestore = mock(FirebaseFirestore::class.java)
    private val users = mock(CollectionReference::class.java)
    private val owner = mock(DocumentReference::class.java)
    private val collection = mock(CollectionReference::class.java)
    private val query = mock(Query::class.java)
    private val registration = mock(ListenerRegistration::class.java)
    private val recovery = mock(SubmissionRecoveryStore::class.java)
    private val repository = FirestoreReportingRepository(firestore, recovery)
    private val period = ReportingMonth(YearMonth.of(2026, 1))

    private fun setup(name: String, field: String?): EventListener<QuerySnapshot> {
        `when`(firestore.collection("users")).thenReturn(users)
        `when`(users.document("owner")).thenReturn(owner)
        `when`(owner.collection(name)).thenReturn(collection)
        if (field != null) {
            `when`(collection.whereGreaterThanOrEqualTo(field, "2026-01-01")).thenReturn(query)
            `when`(query.whereLessThan(field, "2026-02-01")).thenReturn(query)
            `when`(query.orderBy(field)).thenReturn(query)
        }
        val target = if (field == null) collection else query
        `when`(target.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(registration)
        return object : EventListener<QuerySnapshot> {
            override fun onEvent(value: QuerySnapshot?, error: FirebaseFirestoreException?) {
                val listener = argumentCaptor<EventListener<QuerySnapshot>>()
                verify(target).addSnapshotListener(eq(MetadataChanges.INCLUDE), listener.capture())
                listener.firstValue.onEvent(value, error)
            }
        }
    }
    private fun snapshot(documents: List<DocumentSnapshot>, cached: Boolean = false, pending: Boolean = false): QuerySnapshot {
        val metadata = mock(SnapshotMetadata::class.java)
        `when`(metadata.isFromCache).thenReturn(cached)
        `when`(metadata.hasPendingWrites()).thenReturn(pending)
        return mock(QuerySnapshot::class.java).also { `when`(it.documents).thenReturn(documents); `when`(it.metadata).thenReturn(metadata) }
    }
    private fun doc(fields: Map<String, Any?> = emptyMap()): DocumentSnapshot {
        val result = mock(DocumentSnapshot::class.java)
        `when`(result.id).thenReturn("fixture")
        `when`(result.metadata).thenReturn(mock(SnapshotMetadata::class.java))
        `when`(result.get("createdAt")).thenReturn(Timestamp(1, 0))
        `when`(result.get("updatedAt")).thenReturn(Timestamp(2, 0))
        `when`(result.getTimestamp("createdAt")).thenReturn(Timestamp(1, 0))
        `when`(result.getTimestamp("updatedAt")).thenReturn(Timestamp(2, 0))
        fields.forEach { (field, value) ->
            `when`(result.contains(field)).thenReturn(true)
            `when`(result.get(field)).thenReturn(value)
            if (value is String) `when`(result.getString(field)).thenReturn(value)
            if (value is Boolean) `when`(result.getBoolean(field)).thenReturn(value)
        }
        return result
    }
    private val expenseFields = mapOf("amountMinor" to 150000L, "currency" to "LKR", "categoryId" to "transport",
        "paymentChannelId" to "cash", "spendingType" to "committed", "expenseDate" to "2026-01-01", "note" to "Fixture")

    @Test fun `linked recurring actual expense retains origin and optional note and contributes exactly once`() = runTest {
        val listener = setup("expenses", "expenseDate")
        var latest = ReportingBatch<Expense>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.expenses("owner", period).collect { latest = it } }
        val linked = doc(expenseFields - "note" + ("recurringOccurrenceId" to "rent_2026-01"))
        `when`(linked.id).thenReturn("rec_rent_2026-01")
        runCurrent(); listener.onEvent(snapshot(listOf(linked)), null); runCurrent()
        assertEquals("rent_2026-01", latest.records!!.single().recurringOccurrenceId)
        assertEquals("", latest.records!!.single().note)
        assertEquals(150000L, MonthlyReporting.expenses(latest.records!!, period).totalMinor)
        verify(owner, never()).collection("recurringTemplates"); verify(owner, never()).collection("recurringOccurrences")
        job.cancelAndJoin()
    }

    @Test fun `recurring uncertain and rejected proposals are feedback only and templates never count as actual expenses`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { temp.newFile("recurring-report.preferences_pb") }))
        store.saveOperation(SubmissionRecord("owner", "rent_2026-01", "immutable-confirm", "UNCERTAIN", type = "RECURRING_CONFIRM"))
        store.saveOperation(SubmissionRecord("owner", "rent_2026-02", "rejected-confirm", "REJECTED", type = "RECURRING_CONFIRM"))
        store.saveOperation(SubmissionRecord("owner", "rent", "template-proposal", "PENDING", type = "RECURRING_CREATE"))
        store.saveOperation(SubmissionRecord("other", "rent_2026-03", "other-owner", "PENDING", type = "RECURRING_CONFIRM"))
        assertEquals(ReportingRecovery(1, 1), FirestoreReportingRepository(firestore, store).recovery("owner").first())
        assertEquals("immutable-confirm", store.getRecords("owner", "RECURRING_CONFIRM").first().payloadJson)
        verifyNoInteractions(firestore)
    }

    @Test fun `omitted optional expense note decodes as empty and contributes to reporting`() = runTest {
        val listener = setup("expenses", "expenseDate")
        var latest = ReportingBatch<Expense>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.expenses("owner", period).collect { latest = it } }
        runCurrent()
        listener.onEvent(snapshot(listOf(doc(expenseFields - "note"))), null); runCurrent()
        assertEquals("", latest.records!!.single().note)
        val spending = MonthlyReporting.expenses(latest.records!!, period)
        assertEquals(150000L, spending.totalMinor)
        assertEquals(150000L, spending.committedMinor)
        assertEquals("Transport", spending.categories.single().label)
        for (note in listOf("", "x".repeat(500))) {
            listener.onEvent(snapshot(listOf(doc(expenseFields + ("note" to note)))), null); runCurrent()
            assertEquals(note, latest.records!!.single().note)
        }
        job.cancelAndJoin()
    }

    @Test fun `present null nonstring and overlength expense notes remain invalid`() = runTest {
        for (note in listOf(null, 42L, true, listOf("note"), "x".repeat(501))) {
            val listener = setup("expenses", "expenseDate")
            var failure: Throwable? = null
            var latest = ReportingBatch<Expense>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.expenses("owner", period).catch { failure = it }.collect { latest = it }
            }
            runCurrent()
            listener.onEvent(snapshot(listOf(doc(expenseFields + ("note" to note)))), null); runCurrent()
            assertNull(latest.records)
            assertTrue(failure!!.message!!.contains("Invalid expense note"))
            job.cancelAndJoin()
            clearInvocations(query, registration)
        }
    }

    @Test fun `optional expense note does not relax required expense fields`() = runTest {
        val listener = setup("expenses", "expenseDate")
        var failure: Throwable? = null
        var latest = ReportingBatch<Expense>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.expenses("owner", period).catch { failure = it }.collect { latest = it }
        }
        runCurrent()
        listener.onEvent(snapshot(listOf(doc(expenseFields - "note" - "currency"))), null); runCurrent()
        assertNull(latest.records)
        assertTrue(failure!!.message!!.contains("Missing currency"))
        job.cancelAndJoin()
    }

    @Test fun `reporting recovery reads only the owner and supports legacy types without changing saved payloads`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { temp.newFile("reporting.preferences_pb") }))
        val legacy = SubmissionRecord("a", "same", "legacy-payload", "PENDING")
        val rejected = SubmissionRecord("a", "same", "immutable-proposal", "FAILED", "Denied", "CRYPTO_UPDATE")
        store.saveRecord(legacy)
        store.saveRecord(rejected)
        store.saveRecord(SubmissionRecord("b", "same", "other-owner", "PENDING", type = "INCOME"))
        val reports = FirestoreReportingRepository(firestore, store)
        assertEquals(ReportingRecovery(2, 0), reports.recovery("a").first()) // Legacy access failure is unresolved, not proof of rejection.
        assertEquals(ReportingRecovery(1, 0), reports.recovery("b").first())
        assertEquals(listOf(legacy), store.getRecords("a", "EXPENSE"))
        assertEquals(listOf(rejected), store.getRecords("a", "CRYPTO_UPDATE"))
        verifyNoInteractions(firestore)
    }

    @Test fun `all transaction listeners use inclusive month start exclusive next month without truncation and clean up`() = runTest {
        for ((name, field) in listOf("expenses" to "expenseDate", "incomeReceipts" to "receiptDate", "cryptoResults" to "resultDate")) {
            val listener = setup(name, field)
            val source = when (name) {
                "expenses" -> repository.expenses("owner", period)
                "incomeReceipts" -> repository.income("owner", period)
                else -> repository.crypto("owner", period)
            }
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.collect { } }
            runCurrent()
            listener.onEvent(snapshot(emptyList()), null)
            verify(collection).whereGreaterThanOrEqualTo(field, "2026-01-01")
            verify(query).whereLessThan(field, "2026-02-01")
            verify(query, never()).limit(anyLong())
            job.cancelAndJoin()
            verify(registration).remove()
            clearInvocations(query, collection, registration)
        }
    }

    @Test fun `pending SDK edit or deletion retains last clean data and rejection rollback or acknowledgement updates once`() = runTest {
        val listener = setup("expenses", "expenseDate")
        var latest = ReportingBatch<Expense>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.expenses("owner", period).collect { latest = it } }
        runCurrent()
        listener.onEvent(snapshot(listOf(doc(expenseFields))), null); runCurrent()
        assertEquals(150000L, latest.records!!.single().amountMinor)
        listener.onEvent(snapshot(emptyList(), cached = true, pending = true), null); runCurrent()
        assertTrue(latest.awaitingWrites)
        assertEquals(150000L, latest.records!!.single().amountMinor)
        listener.onEvent(snapshot(listOf(doc(expenseFields))), null); runCurrent()
        assertFalse(latest.awaitingWrites)
        listener.onEvent(snapshot(emptyList()), null); runCurrent()
        assertTrue(latest.records!!.isEmpty())
        job.cancelAndJoin()
    }

    @Test fun `invalid fields fail entire section rather than dropping a document or coercing fractional money`() = runTest {
        val listener = setup("expenses", "expenseDate")
        var failure: Throwable? = null
        var latest = ReportingBatch<Expense>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.expenses("owner", period).catch { failure = it }.collect { latest = it }
        }
        runCurrent()
        listener.onEvent(snapshot(listOf(doc(expenseFields + ("amountMinor" to 1.5)))), null); runCurrent()
        assertNull(latest.records)
        assertTrue(failure!!.message!!.contains("Invalid integer"))
        verify(registration).remove()
        job.cancelAndJoin()
    }

    @Test fun `linked receipt identities survive reporting decode and are never converted to editable standalone income`() = runTest {
        val listener = setup("incomeReceipts", "receiptDate")
        var latest = ReportingBatch<Income>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.income("owner", period).collect { latest = it } }
        runCurrent()
        listener.onEvent(snapshot(listOf(doc(mapOf("sourceId" to "freelance", "receiptDate" to "2026-01-01", "currency" to "USD",
            "originalAmountStr" to "10", "lkrAmountMinor" to 300000L, "conversionBasis" to "actual_credit", "note" to "Fixture",
            "linkedPaymentId" to "parent", "linkedMilestoneId" to "milestone")))), null); runCurrent()
        assertEquals("parent", latest.records!!.single().linkedPaymentId)
        assertEquals("milestone", latest.records!!.single().linkedMilestoneId)
        job.cancelAndJoin()
    }

    @Test fun `current outstanding query reads only owner parents with no month filter or milestone listener`() = runTest {
        val listener = setup("expectedPayments", null)
        var latest = ReportingBatch<ExpectedPayment>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.projects("owner").collect { latest = it } }
        runCurrent()
        listener.onEvent(snapshot(emptyList()), null); runCurrent()
        assertEquals(emptyList<ExpectedPayment>(), latest.records)
        verify(owner, never()).collection("milestones")
        verify(collection, never()).whereGreaterThanOrEqualTo(anyString(), any())
        job.cancelAndJoin()
        verify(registration).remove()
    }
}
