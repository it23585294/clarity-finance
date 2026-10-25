package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecord
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.PaymentConfirmationPayload
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePaymentRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val firestore = mock(FirebaseFirestore::class.java)
    private val recoveryStore = mock(SubmissionRecoveryStore::class.java)
    private lateinit var repository: FirestorePaymentRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FirestorePaymentRepository(firestore, recoveryStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getUnresolvedPayment returns matching income payload from recoveryStore`() = runTest(testDispatcher) {
        val income = Income(
            id = "r1",
            sourceId = "freelance",
            receiptDate = "2023-10-23",
            currency = "USD",
            originalAmountStr = "150.00",
            originalAmountMinor = 15000L,
            lkrAmountMinor = 4500000L,
            conversionBasis = "confirmed_rate",
            conversionRateStr = "300",
            note = "Test note",
            linkedPaymentId = "p1"
        )
        val payload = PaymentConfirmationPayload("p1", null, income)
        val record = SubmissionRecord.create("uid1", "r1", payload, "FAILED", "PAYMENT_DIRECT")

        `when`(recoveryStore.getRecords("uid1", "PAYMENT_DIRECT")).thenReturn(listOf(record))

        val result = repository.getUnresolvedPayment("uid1", "p1", null)

        assertNotNull(result)
        assertEquals("r1", result?.id)
        assertEquals(15000L, result?.originalAmountMinor)
        assertEquals("150.00", result?.originalAmountStr)
        assertEquals("Test note", result?.note)
    }

    @Test
    fun `getUnresolvedPayment returns null when no matching record exists`() = runTest(testDispatcher) {
        `when`(recoveryStore.getRecords("uid1", "PAYMENT_DIRECT")).thenReturn(emptyList())

        val result = repository.getUnresolvedPayment("uid1", "p1", null)

        assertNull(result)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `getExpectedPayment returns payment when document exists`() = runTest(testDispatcher) {
        val usersCol = mock(CollectionReference::class.java)
        val userDoc = mock(DocumentReference::class.java)
        val paymentsCol = mock(CollectionReference::class.java)
        val paymentDocRef = mock(DocumentReference::class.java)
        val snapshotTask = mock(Task::class.java) as Task<DocumentSnapshot>
        val snap = mock(DocumentSnapshot::class.java)
        val meta = mock(SnapshotMetadata::class.java)

        `when`(firestore.collection("users")).thenReturn(usersCol)
        `when`(usersCol.document("uid1")).thenReturn(userDoc)
        `when`(userDoc.collection("expectedPayments")).thenReturn(paymentsCol)
        `when`(paymentsCol.document("p1")).thenReturn(paymentDocRef)
        `when`(paymentDocRef.get(com.google.firebase.firestore.Source.SERVER)).thenReturn(snapshotTask)
        `when`(snapshotTask.isComplete).thenReturn(true)
        `when`(snapshotTask.isSuccessful).thenReturn(true)
        `when`(snapshotTask.result).thenReturn(snap)
        `when`(snap.exists()).thenReturn(true)
        `when`(snap.id).thenReturn("p1")
        `when`(snap.metadata).thenReturn(meta)
        `when`(meta.hasPendingWrites()).thenReturn(false)
        `when`(snap.getString("title")).thenReturn("Test Project")
        `when`(snap.getString("type")).thenReturn("project")
        `when`(snap.getString("currency")).thenReturn("USD")
        `when`(snap.getLong("agreedAmountMinor")).thenReturn(100000L)

        val result = repository.getExpectedPayment("uid1", "p1")

        assertTrue(result.isSuccess)
        val payment = result.getOrThrow()
        assertEquals("p1", payment.id)
        assertEquals("Test Project", payment.title)
        assertEquals("project", payment.type)
        assertEquals("USD", payment.currency)
        assertEquals(100000L, payment.agreedAmountMinor)
        assertFalse(payment.hasPendingWrites)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `confirmPayment with matching existing receipt reconciles successfully with zero new writes`() = runTest(testDispatcher) {
        val usersCol = mock(CollectionReference::class.java)
        val userDoc = mock(DocumentReference::class.java)
        val incomeCol = mock(CollectionReference::class.java)
        val incomeDoc = mock(DocumentReference::class.java)
        val expectedPaymentsCol = mock(CollectionReference::class.java)
        val expectedPaymentDoc = mock(DocumentReference::class.java)
        val tx = mock(Transaction::class.java)
        val incomeSnap = mock(DocumentSnapshot::class.java)

        `when`(firestore.collection("users")).thenReturn(usersCol)
        `when`(usersCol.document("uid1")).thenReturn(userDoc)
        `when`(userDoc.collection("incomeReceipts")).thenReturn(incomeCol)
        `when`(incomeCol.document("r1")).thenReturn(incomeDoc)
        `when`(userDoc.collection("expectedPayments")).thenReturn(expectedPaymentsCol)
        `when`(expectedPaymentsCol.document("p1")).thenReturn(expectedPaymentDoc)

        `when`(firestore.runTransaction(any<Transaction.Function<Unit>>())).thenAnswer { invocation ->
            val function = invocation.getArgument<Transaction.Function<Unit>>(0)
            `when`(tx.get(incomeDoc)).thenReturn(incomeSnap)
            `when`(incomeSnap.exists()).thenReturn(true)
            `when`(incomeSnap.getLong("originalAmountMinor")).thenReturn(15000L)
            `when`(incomeSnap.getString("linkedPaymentId")).thenReturn("p1")
            `when`(incomeSnap.getString("linkedMilestoneId")).thenReturn(null)
            `when`(incomeSnap.getString("sourceId")).thenReturn("freelance")
            `when`(incomeSnap.getString("currency")).thenReturn("USD")
            `when`(incomeSnap.getString("receiptDate")).thenReturn("2023-10-23")
            `when`(incomeSnap.getString("originalAmountStr")).thenReturn("150.00")
            `when`(incomeSnap.getLong("lkrAmountMinor")).thenReturn(4500000L)
            `when`(incomeSnap.getString("conversionBasis")).thenReturn("confirmed_rate")
            `when`(incomeSnap.getString("conversionRateStr")).thenReturn("300")
            `when`(incomeSnap.getString("note")).thenReturn("Test note")

            function.apply(tx)
            Tasks.forResult(Unit)
        }

        val income = Income(
            id = "r1",
            sourceId = "freelance",
            receiptDate = "2023-10-23",
            currency = "USD",
            originalAmountStr = "150.00",
            originalAmountMinor = 15000L,
            lkrAmountMinor = 4500000L,
            conversionBasis = "confirmed_rate",
            conversionRateStr = "300",
            note = "Test note",
            linkedPaymentId = "p1"
        )

        val result = repository.confirmPayment("uid1", "p1", null, income)

        assertTrue(result.isSuccess)
        verify(tx, never()).update(any<DocumentReference>(), any<Map<String, Any>>())
        verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        verify(recoveryStore).removeRecord("uid1", "r1", "PAYMENT_DIRECT")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `confirmPayment with conflicting existing receipt throws conflict and retains recovery record`() = runTest(testDispatcher) {
        val usersCol = mock(CollectionReference::class.java)
        val userDoc = mock(DocumentReference::class.java)
        val incomeCol = mock(CollectionReference::class.java)
        val incomeDoc = mock(DocumentReference::class.java)
        val expectedPaymentsCol = mock(CollectionReference::class.java)
        val expectedPaymentDoc = mock(DocumentReference::class.java)
        val tx = mock(Transaction::class.java)
        val incomeSnap = mock(DocumentSnapshot::class.java)

        `when`(firestore.collection("users")).thenReturn(usersCol)
        `when`(usersCol.document("uid1")).thenReturn(userDoc)
        `when`(userDoc.collection("incomeReceipts")).thenReturn(incomeCol)
        `when`(incomeCol.document("r1")).thenReturn(incomeDoc)
        `when`(userDoc.collection("expectedPayments")).thenReturn(expectedPaymentsCol)
        `when`(expectedPaymentsCol.document("p1")).thenReturn(expectedPaymentDoc)

        `when`(firestore.runTransaction(any<Transaction.Function<Unit>>())).thenAnswer { invocation ->
            val function = invocation.getArgument<Transaction.Function<Unit>>(0)
            `when`(tx.get(incomeDoc)).thenReturn(incomeSnap)
            `when`(incomeSnap.exists()).thenReturn(true)
            `when`(incomeSnap.getLong("originalAmountMinor")).thenReturn(99999L)

            try {
                function.apply(tx)
            } catch (e: Exception) {
                return@thenAnswer Tasks.forException<Unit>(e)
            }
            Tasks.forException<Unit>(Exception("Conflict"))
        }

        val income = Income(
            id = "r1",
            sourceId = "freelance",
            receiptDate = "2023-10-23",
            currency = "USD",
            originalAmountStr = "150.00",
            originalAmountMinor = 15000L,
            lkrAmountMinor = 4500000L,
            conversionBasis = "confirmed_rate",
            conversionRateStr = "300",
            note = "Test note",
            linkedPaymentId = "p1"
        )

        val result = repository.confirmPayment("uid1", "p1", null, income)

        assertTrue(result.isFailure)
        verify(recoveryStore).saveRecord(argThat {
            uid == "uid1" && recordId == "r1" && status == "CONFLICT"
        })
    }

    @Test
    fun `creation retry reconciles existing project without resetting paid balances`() = runTest(testDispatcher) {
        val users = mock(CollectionReference::class.java)
        val user = mock(DocumentReference::class.java)
        val payments = mock(CollectionReference::class.java)
        val ref = mock(DocumentReference::class.java)
        val snap = mock(DocumentSnapshot::class.java)
        val tx = mock(Transaction::class.java)
        `when`(firestore.collection("users")).thenReturn(users)
        `when`(users.document("uid1")).thenReturn(user)
        `when`(user.collection("expectedPayments")).thenReturn(payments)
        `when`(payments.document("p1")).thenReturn(ref)
        `when`(tx.get(ref)).thenReturn(snap)
        `when`(snap.exists()).thenReturn(true)
        `when`(snap.getString("type")).thenReturn("project")
        `when`(snap.getString("title")).thenReturn("Test")
        `when`(snap.getString("sourceId")).thenReturn("freelance")
        `when`(snap.getString("currency")).thenReturn("LKR")
        `when`(snap.getString("dueDate")).thenReturn("2026-10-30")
        `when`(snap.getString("note")).thenReturn("")
        `when`(snap.getLong("agreedAmountMinor")).thenReturn(100000L)
        `when`(snap.getLong("directReceivedAmountMinor")).thenReturn(15000L)
        `when`(firestore.runTransaction(any<Transaction.Function<Unit>>())).thenAnswer { invocation ->
            invocation.getArgument<Transaction.Function<Unit>>(0).apply(tx)
            Tasks.forResult(Unit)
        }
        val payment = ExpectedPayment("p1", "project", "Test", "freelance", "LKR", "2026-10-30", 100000L)
        assertTrue(repository.createExpectedPayment("uid1", payment).isSuccess)
        verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        verify(tx, never()).update(any<DocumentReference>(), any<Map<String, Any>>())
    }

    private data class MilestoneFixture(val parent: DocumentReference, val milestone: DocumentReference,
        val parentSnapshot: DocumentSnapshot, val milestoneSnapshot: DocumentSnapshot, val tx: Transaction)

    private fun milestoneFixture(): MilestoneFixture {
        val users = mock(CollectionReference::class.java)
        val user = mock(DocumentReference::class.java)
        val payments = mock(CollectionReference::class.java)
        val parent = mock(DocumentReference::class.java)
        val milestones = mock(CollectionReference::class.java)
        val milestone = mock(DocumentReference::class.java)
        val parentSnapshot = mock(DocumentSnapshot::class.java)
        val milestoneSnapshot = mock(DocumentSnapshot::class.java)
        val tx = mock(Transaction::class.java)
        `when`(firestore.collection("users")).thenReturn(users)
        `when`(users.document("uid1")).thenReturn(user)
        `when`(user.collection("expectedPayments")).thenReturn(payments)
        `when`(payments.document("p1")).thenReturn(parent)
        `when`(parent.collection("milestones")).thenReturn(milestones)
        `when`(milestones.document("m1")).thenReturn(milestone)
        `when`(tx.get(parent)).thenReturn(parentSnapshot)
        `when`(tx.get(milestone)).thenReturn(milestoneSnapshot)
        `when`(firestore.runTransaction(any<Transaction.Function<Unit>>())).thenAnswer { invocation ->
            invocation.getArgument<Transaction.Function<Unit>>(0).apply(tx)
            Tasks.forResult(Unit)
        }
        return MilestoneFixture(parent, milestone, parentSnapshot, milestoneSnapshot, tx)
    }

    @Test fun `matching milestone creation retry performs no allocation or document writes`() = runTest(testDispatcher) {
        val f = milestoneFixture()
        `when`(f.milestoneSnapshot.exists()).thenReturn(true)
        `when`(f.milestoneSnapshot.getString("title")).thenReturn("Original")
        `when`(f.milestoneSnapshot.getString("dueDate")).thenReturn("2026-10-30")
        `when`(f.milestoneSnapshot.getLong("agreedAmountMinor")).thenReturn(40000L)
        val milestone = com.clarity.finance.data.model.Milestone("m1", "p1", "Original", "2026-10-30", 40000L)
        assertTrue(repository.createMilestone("uid1", milestone, "stable-op").isSuccess)
        verify(f.tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        verify(f.tx, never()).update(any<DocumentReference>(), any<Map<String, Any>>())
    }

    @Test fun `partially paid cancellation releases only unpaid allocation and retains agreement and receipts`() = runTest(testDispatcher) {
        `when`(recoveryStore.getRecords("uid1", "CANCEL_MILESTONE")).thenReturn(emptyList())
        val f = milestoneFixture()
        val timestamp = com.google.firebase.Timestamp(1, 0)
        `when`(f.milestoneSnapshot.exists()).thenReturn(true)
        `when`(f.milestoneSnapshot.getTimestamp("updatedAt")).thenReturn(timestamp)
        `when`(f.milestoneSnapshot.getLong("agreedAmountMinor")).thenReturn(40000L)
        `when`(f.milestoneSnapshot.getLong("receivedAmountMinor")).thenReturn(15000L)
        `when`(f.parentSnapshot.getLong("allocatedAmountMinor")).thenReturn(40000L)
        assertTrue(repository.cancelMilestone("uid1", "m1", "p1", timestamp, "stable-op").isSuccess)
        verify(f.tx).update(org.mockito.kotlin.eq(f.parent), argThat<Map<String, Any>> {
            get("allocatedAmountMinor") == 15000L && !containsKey("milestoneReceivedAmountMinor")
        })
        verify(f.tx).update(org.mockito.kotlin.eq(f.milestone), argThat<Map<String, Any>> {
            get("isCancelled") == true && !containsKey("agreedAmountMinor") && !containsKey("receivedAmountMinor")
        })
    }

    @Test fun `already cancelled milestone reconciles without releasing allocation again`() = runTest(testDispatcher) {
        `when`(recoveryStore.getRecords("uid1", "CANCEL_MILESTONE")).thenReturn(emptyList())
        val f = milestoneFixture()
        `when`(f.milestoneSnapshot.exists()).thenReturn(true)
        `when`(f.milestoneSnapshot.getBoolean("isCancelled")).thenReturn(true)
        assertTrue(repository.cancelMilestone("uid1", "m1", "p1", com.google.firebase.Timestamp(1, 0), "retry-op").isSuccess)
        verify(f.tx, never()).update(any<DocumentReference>(), any<Map<String, Any>>())
        verify(f.tx, never()).get(f.parent)
    }

    @Test fun `cancelling a suspended creation preserves recovery and propagates cancellation`() = runTest(testDispatcher) {
        val source = com.google.android.gms.tasks.TaskCompletionSource<Unit>()
        val f = milestoneFixture()
        `when`(firestore.runTransaction(any<Transaction.Function<Unit>>())).thenReturn(source.task)
        val result = kotlinx.coroutines.withTimeoutOrNull(100L) {
            repository.createExpectedPayment("uid1", ExpectedPayment("p1", "project", "Title", "freelance", "LKR", "2026-10-30", 10000L))
        }
        assertNull(result)
        verify(recoveryStore).saveOperation(argThat { recordId == "p1" && uid == "uid1" && resolvedType == "CREATE_PROJECT" })
        verify(recoveryStore, never()).removeRecord(any(), any(), any())
        verify(f.tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
    }
}
