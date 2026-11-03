package com.clarity.finance.data.repository

import com.clarity.finance.data.local.*
import com.clarity.finance.data.model.*
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePaymentRepositoryTest {
    @get:Rule val files = TemporaryFolder()
    private val uid = "alice"
    private val fixture = FinancialRemoteFixture(uid)
    private val store = mock(SubmissionRecoveryStore::class.java)
    private val queue = FinancialQueue(fixture.remote, store)
    private val repository = FirestorePaymentRepository(fixture.db, queue)
    private val stamp = Timestamp(1, 0)
    private val project = ExpectedPayment("p", "project", "Project", "freelance", "LKR", "2026-12-31", 100000, updatedAt = stamp)
    private val income = Income("r", "freelance", "2026-01-31", "LKR", "150.00", 15000, "native_lkr", "1", "Receipt",
        originalAmountMinor = 15000, linkedPaymentId = "p")
    private fun projectMap() = mapOf<String, Any?>("type" to "project", "title" to "Project", "sourceId" to "freelance", "currency" to "LKR",
        "dueDate" to "2026-12-31", "agreedAmountMinor" to 100000L, "allocatedAmountMinor" to 50000L,
        "directReceivedAmountMinor" to 15000L, "milestoneReceivedAmountMinor" to 10000L, "isCancelled" to false, "note" to "", "updatedAt" to stamp)
    private fun milestoneMap(cancelled: Boolean = false) = mapOf<String, Any?>("title" to "M", "dueDate" to "2026-12-31", "agreedAmountMinor" to 50000L,
        "receivedAmountMinor" to 10000L, "isCancelled" to cancelled, "updatedAt" to stamp)
    @Test fun `getUnresolvedPayment returns matching income payload from recoveryStore`() = runTest {
        val record = SubmissionRecord.create(uid, "r", PaymentConfirmationPayload("p", null, income), "FAILED", "PAYMENT_DIRECT")
        `when`(store.getAllRecords(uid)).thenReturn(listOf(record))
        assertEquals(income, repository.getUnresolvedPayment(uid, "p", null))
        assertNull(repository.getUnresolvedPayment(uid, "other", null))
    }
    @Test fun `getUnresolvedPayment returns null when no matching record exists`() = runTest {
        `when`(store.getAllRecords(uid)).thenReturn(emptyList())
        assertNull(repository.getUnresolvedPayment(uid, "p", null))
    }
    @Test fun `getExpectedPayment returns payment when document exists`() = runTest {
        fixture.documents[fixture.path("expectedPayments", "p")] = projectMap()
        val result = repository.getExpectedPayment(uid, "p").getOrThrow()
        assertEquals(100000L, result.agreedAmountMinor); assertEquals(25000L, result.totalReceivedAmountMinor)
    }
    @Test fun `confirmPayment with matching existing receipt reconciles successfully with zero new writes`() = runTest {
        val op = FinancialOperation("r", "RECEIPT", "CREATE", "r", "p", income = income, legacyPayload = "legacy")
        fixture.documents[fixture.path("incomeReceipts", "r")] = op.payload()
        fixture.remote.apply(uid, op)
        assertTrue(fixture.writes.isEmpty()) // Does not inspect a now-cancelled/unavailable parent.
    }
    @Test fun `confirmPayment with conflicting existing receipt throws conflict and retains recovery record`() = runTest {
        val op = FinancialOperation("r", "RECEIPT", "CREATE", "r", "p", income = income)
        fixture.documents[fixture.path("incomeReceipts", "r")] = op.payload() + ("lkrAmountMinor" to 99L)
        val actualStore = SubmissionRecoveryStore(androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = backgroundScope, produceFile = { files.newFile("conflict.preferences_pb") }))
        val actualQueue = FinancialQueue(fixture.remote, actualStore)
        val actualRepository = FirestorePaymentRepository(fixture.db, actualQueue)
        val unrelated = SubmissionRecord("other-owner", "untouched", "{}", "PENDING", type = "INCOME")
        actualStore.saveOperation(unrelated)
        assertTrue(actualRepository.confirmPayment(uid, "p", null, income).isSuccess) // Local acceptance only.
        val immutable = actualStore.getAllRecords(uid).single().payloadJson
        val worker = backgroundScope.launch { actualQueue.synchronize(uid) }
        withTimeout(10000) { while (!actualQueue.pending(uid).single().rejected) yield() }
        assertEquals(op, actualQueue.pending(uid).single().operation)
        assertEquals(immutable, actualStore.getAllRecords(uid).single().payloadJson)
        assertEquals(listOf(unrelated), actualStore.getAllRecords("other-owner"))
        assertTrue(fixture.documents.containsKey(fixture.path("syncFences", "r")))
        assertEquals(99L, fixture.documents[fixture.path("incomeReceipts", "r")]!!["lkrAmountMinor"])
        worker.cancelAndJoin()
    }
    @Test fun `creation retry reconciles existing project without resetting paid balances`() = runTest {
        fixture.documents[fixture.path("expectedPayments", "p")] = projectMap()
        fixture.remote.apply(uid, FinancialOperation("old", "PROJECT", "CREATE", "p", project = project, legacyPayload = "legacy"))
        assertTrue(fixture.writes.isEmpty()); assertEquals(50000L, fixture.documents[fixture.path("expectedPayments", "p")]!!["allocatedAmountMinor"])
    }
    @Test fun `matching milestone creation retry performs no allocation or document writes`() = runTest {
        fixture.documents[fixture.path("expectedPayments/p/milestones", "m")] = milestoneMap()
        fixture.remote.apply(uid, FinancialOperation("old", "MILESTONE", "CREATE", "m", "p",
            milestone = Milestone("m", "p", "M", "2026-12-31", 50000), legacyPayload = "legacy"))
        assertTrue(fixture.writes.isEmpty())
    }
    @Test fun `partially paid cancellation releases only unpaid allocation and retains agreement and receipts`() = runTest {
        fixture.documents[fixture.path("expectedPayments", "p")] = projectMap()
        fixture.documents[fixture.path("expectedPayments/p/milestones", "m")] = milestoneMap()
        val op = FinancialOperation("cancel", "MILESTONE", "CANCEL", "m", "p", stamp)
        fixture.remote.apply(uid, op)
        val after = fixture.documents[fixture.path("expectedPayments/p/milestones", "m")]!!
        assertEquals(50000L, after["agreedAmountMinor"]); assertEquals(10000L, after["receivedAmountMinor"])
        assertEquals(true, after["isCancelled"]); assertEquals(10000L, fixture.documents[fixture.path("expectedPayments", "p")]!!["allocatedAmountMinor"])
        val writes = fixture.writes.size; fixture.remote.apply(uid, op); assertEquals(writes, fixture.writes.size)
    }
    @Test fun `already cancelled milestone reconciles without releasing allocation again`() = runTest {
        fixture.documents[fixture.path("expectedPayments/p/milestones", "m")] = milestoneMap(true)
        fixture.remote.apply(uid, FinancialOperation("old", "MILESTONE", "CANCEL", "m", "p", stamp, legacyPayload = "legacy"))
        assertTrue(fixture.writes.isEmpty())
    }
    @Test fun `cancelling a suspended creation preserves recovery and propagates cancellation`() = runTest {
        `when`(store.getAllRecords(uid)).thenReturn(emptyList())
        assertTrue(repository.createExpectedPayment(uid, project).isSuccess)
        verify(store).saveOperation(org.mockito.kotlin.argThat { this.uid == "alice" && resolvedType == "FINANCIAL_PROJECT_CREATE" })
        val source = TaskCompletionSource<Any>(); fixture.taskGate = source
        val job = backgroundScope.launch { fixture.remote.apply(uid, FinancialOperation("p", "PROJECT", "CREATE", "p", project = project)); fail("Cancellation must propagate") }
        runCurrent(); job.cancelAndJoin(); assertFalse(source.task.isComplete)
        verify(store, never()).removeRecord(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
