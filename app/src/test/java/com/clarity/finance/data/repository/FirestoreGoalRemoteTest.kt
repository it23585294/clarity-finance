package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
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
class FirestoreGoalRemoteTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val users = mock(CollectionReference::class.java)
    private val user = mock(DocumentReference::class.java)
    private val goals = mock(CollectionReference::class.java)
    private val parent = mock(DocumentReference::class.java)
    private val entries = mock(CollectionReference::class.java)
    private val entryRef = mock(DocumentReference::class.java)
    private val receipts = mock(CollectionReference::class.java)
    private val receiptRef = mock(DocumentReference::class.java)
    private val tx = mock(Transaction::class.java)
    private val goalDoc = mock(DocumentSnapshot::class.java)
    private val entryDoc = mock(DocumentSnapshot::class.java)
    private val receiptDoc = mock(DocumentSnapshot::class.java)
    private var owner = "a"
    private val remote = FirestoreGoalRemote(firestore) { owner }
    private val config = GoalConfig("Fixture", 49000000, 1120000, "2025-01-01", "2027-01-01")
    @Suppress("UNCHECKED_CAST") private fun setup() {
        `when`(firestore.collection("users")).thenReturn(users); `when`(users.document("a")).thenReturn(user)
        `when`(user.collection("savingsGoals")).thenReturn(goals); `when`(goals.document("active")).thenReturn(parent)
        `when`(parent.collection("operations")).thenReturn(receipts); `when`(receipts.document(any())).thenReturn(receiptRef)
        `when`(parent.collection("entries")).thenReturn(entries); `when`(entries.document("entry")).thenReturn(entryRef)
        `when`(tx.get(parent)).thenReturn(goalDoc); `when`(tx.get(receiptRef)).thenReturn(receiptDoc); `when`(tx.get(entryRef)).thenReturn(entryDoc)
        doAnswer { invocation ->
            val result = invocation.getArgument<Transaction.Function<GoalRemoteOutcome>>(0).apply(tx)
            val task = mock(Task::class.java) as Task<GoalRemoteOutcome>
            `when`(task.isComplete).thenReturn(true); `when`(task.isSuccessful).thenReturn(true); `when`(task.result).thenReturn(result); task
        }.`when`(firestore).runTransaction(any<Transaction.Function<GoalRemoteOutcome>>())
    }
    private fun existing() {
        `when`(goalDoc.exists()).thenReturn(true)
        val fields = config.values() + mapOf("savedMinor" to 1120000L, "version" to 0L, "lastOperationId" to "create", "lastEntryId" to "",
            "createdAt" to Timestamp(1, 0), "updatedAt" to Timestamp(2, 0))
        `when`(goalDoc.get(any<String>())).thenAnswer { fields[it.getArgument<String>(0)] }
    }
    @Test fun `production create and entry mutation write exact parent ledger and immutable receipt payloads`() = runTest {
        setup(); remote.apply("a", GoalOperation("create", "CREATE", goal = config))
        val parentData = argumentCaptor<Map<String, Any>>()
        verify(tx).set(eq(parent), parentData.capture()); assertEquals(1120000L, parentData.firstValue["savedMinor"])
        assertEquals(FieldValue.serverTimestamp(), parentData.firstValue["createdAt"])
        existing()
        val entry = SavingsEntry("entry", "contribution", 1000000, "2025-01-01")
        remote.apply("a", GoalOperation("add", "ENTRY_CREATE", 0, entry = entry))
        val update = argumentCaptor<Map<String, Any>>()
        verify(tx).update(eq(parent), update.capture())
        assertEquals(2120000L, update.firstValue["savedMinor"]); assertEquals(1L, update.firstValue["version"])
        assertFalse(update.firstValue.containsKey("createdAt"))
        val entryData = argumentCaptor<Map<String, Any>>()
        verify(tx).set(eq(entryRef), entryData.capture()); assertEquals(entry.values(), entryData.firstValue.filterKeys { it in entry.values() })
        verify(user, never()).collection("expenses"); verify(user, never()).collection("incomeReceipts"); verify(user, never()).collection("cryptoResults")
    }
    @Test fun `matching immutable receipt reconciles before temporal or stale version checks without additional writes`() = runTest {
        setup()
        val op = GoalOperation("accepted", "CREATE", goal = config.copy(deadline = "2025-12-31"))
        `when`(receiptDoc.exists()).thenReturn(true)
        `when`(receiptDoc.getString("kind")).thenReturn("CREATE"); `when`(receiptDoc.getString("entryId")).thenReturn("")
        `when`(receiptDoc.getLong("version")).thenReturn(0L); `when`(receiptDoc.get("payload")).thenReturn(op.payload())
        `when`(receiptDoc.getTimestamp("createdAt")).thenReturn(Timestamp(1, 0))
        remote.apply("a", op)
        verify(tx, never()).get(parent); verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        `when`(receiptDoc.get("payload")).thenReturn(op.payload() + ("openingMinor" to 1L))
        try { remote.apply("a", op); fail() } catch (_: IllegalStateException) {}
    }
    @Test fun `account changes and stale versions abort before all writes`() = runTest {
        setup(); existing()
        try { remote.apply("a", GoalOperation("edit", "EDIT", 99, goal = config)); fail() } catch (_: IllegalStateException) {}
        `when`(tx.get(parent)).thenAnswer { owner = "b"; goalDoc }
        try { remote.apply("a", GoalOperation("edit", "EDIT", 0, goal = config)); fail() } catch (_: IllegalStateException) {}
        verify(tx, never()).update(eq(parent), any<Map<String, Any>>())
    }
    @Test fun `verified immutable contradiction returns rejection only as successful read transaction outcome`() = runTest {
        setup(); existing()
        val result = remote.apply("a", GoalOperation("different-create", "CREATE", goal = config))
        assertTrue(result is GoalRemoteOutcome.Rejected)
        `when`(goalDoc.get("version")).thenReturn(1L)
        val stale = remote.apply("a", GoalOperation("old-edit", "EDIT", 0, goal = config))
        assertTrue(stale is GoalRemoteOutcome.Rejected)
        `when`(goalDoc.get("version")).thenReturn(0L)
        val impossibleWithdrawal = remote.apply("a", GoalOperation("negative", "ENTRY_CREATE", 0,
            entry = SavingsEntry("entry", "withdrawal", 1120001, "2025-01-01")))
        assertTrue(impossibleWithdrawal is GoalRemoteOutcome.Rejected)
        verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        verify(tx, never()).update(any<DocumentReference>(), any<Map<String, Any>>())
    }
    @Test fun `cancelled Task await and account error retain original outstanding transaction without overlapping submission`() = runTest {
        setup()
        val source = TaskCompletionSource<GoalRemoteOutcome>()
        doReturn(source.task).`when`(firestore).runTransaction(any<Transaction.Function<GoalRemoteOutcome>>())
        val op = GoalOperation("pending-task", "CREATE", goal = config)
        var timedOut = false
        val first = backgroundScope.launch { timedOut = withTimeoutOrNull(10000) { remote.apply("a", op) } == null }
        runCurrent(); advanceTimeBy(10001); runCurrent(); first.join()
        assertTrue(timedOut); assertFalse(source.task.isComplete)
        try { remote.apply("a", op.copy(goal = config.copy(openingMinor = 1))); fail() } catch (_: IllegalStateException) {}
        owner = "b"
        try { remote.apply("a", op); fail() } catch (_: IllegalStateException) {}
        owner = "a"
        var result: GoalRemoteOutcome? = null
        val later = backgroundScope.launch { result = remote.apply("a", op) }; runCurrent()
        verify(firestore, times(1)).runTransaction(any<Transaction.Function<GoalRemoteOutcome>>())
        source.setResult(GoalRemoteOutcome.Confirmed); runCurrent(); later.join()
        assertEquals(GoalRemoteOutcome.Confirmed, result)
        verify(firestore, times(1)).runTransaction(any<Transaction.Function<GoalRemoteOutcome>>())
    }
    @Test fun `parent and untruncated ledger listeners include metadata and are removed on cancellation`() = runTest {
        setup()
        val ordered = entries; val a = mock(ListenerRegistration::class.java); val b = mock(ListenerRegistration::class.java)
        `when`(parent.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<DocumentSnapshot>>())).thenReturn(a)
        `when`(ordered.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(b)
        val job = backgroundScope.launch { remote.observe("a").collect {} }; runCurrent(); job.cancelAndJoin()
        verify(a).remove(); verify(b).remove(); verify(ordered, never()).limit(any<Long>())
    }
    @Test fun `local pending parent or ledger snapshots retain confirmed contribution and uncached absence stays unknown`() = runTest {
        setup(); existing()
        val ordered = entries
        val goalMetadata = mock(SnapshotMetadata::class.java)
        val rows = mock(QuerySnapshot::class.java); val rowMetadata = mock(SnapshotMetadata::class.java)
        `when`(goalDoc.metadata).thenReturn(goalMetadata); `when`(rows.metadata).thenReturn(rowMetadata)
        `when`(rows.documents).thenReturn(emptyList())
        `when`(parent.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<DocumentSnapshot>>())).thenReturn(mock(ListenerRegistration::class.java))
        `when`(ordered.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(mock(ListenerRegistration::class.java))
        var latest = GoalSnapshot()
        var failure: Exception? = null
        val watch = backgroundScope.launch {
            try { remote.observe("a").collect { latest = it } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e }
        }; runCurrent()
        val goalListener = argumentCaptor<EventListener<DocumentSnapshot>>(); val rowListener = argumentCaptor<EventListener<QuerySnapshot>>()
        verify(parent).addSnapshotListener(eq(MetadataChanges.INCLUDE), goalListener.capture())
        verify(ordered).addSnapshotListener(eq(MetadataChanges.INCLUDE), rowListener.capture())
        `when`(goalDoc.exists()).thenReturn(false); `when`(goalMetadata.isFromCache).thenReturn(true)
        goalListener.firstValue.onEvent(goalDoc, null); rowListener.firstValue.onEvent(rows, null); runCurrent()
        assertFalse(latest.known); assertNull(latest.goal); assertNotNull(latest.error)
        `when`(goalDoc.exists()).thenReturn(true); `when`(goalMetadata.isFromCache).thenReturn(false)
        goalListener.firstValue.onEvent(goalDoc, null); runCurrent()
        assertEquals(1120000L, latest.goal!!.savedMinor); assertNull(latest.error)
        `when`(goalMetadata.hasPendingWrites()).thenReturn(true)
        goalListener.firstValue.onEvent(goalDoc, null); runCurrent()
        assertEquals(1120000L, latest.goal!!.savedMinor); assertNotNull(latest.error)
        `when`(rowMetadata.hasPendingWrites()).thenReturn(true); rowListener.firstValue.onEvent(rows, null); runCurrent()
        assertEquals(1120000L, latest.goal!!.savedMinor)
        `when`(goalMetadata.hasPendingWrites()).thenReturn(false); `when`(rowMetadata.hasPendingWrites()).thenReturn(false)
        goalListener.firstValue.onEvent(goalDoc, null); rowListener.firstValue.onEvent(rows, null); runCurrent()
        assertEquals(1120000L, latest.goal!!.savedMinor); assertNull(latest.error)
        `when`(entryDoc.id).thenReturn("malformed")
        `when`(entryDoc.metadata).thenReturn(mock(SnapshotMetadata::class.java))
        `when`(entryDoc.get("kind")).thenReturn("contribution"); `when`(entryDoc.get("amountMinor")).thenReturn(1L)
        `when`(rows.documents).thenReturn(listOf(entryDoc))
        rowListener.firstValue.onEvent(rows, null); runCurrent()
        assertTrue(failure?.message.orEmpty().contains("entryDate"))
        assertEquals(1120000L, latest.goal!!.savedMinor)
        watch.cancelAndJoin()
    }
}
