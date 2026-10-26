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
import java.time.*

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
    private val rejections = mock(CollectionReference::class.java)
    private val rejectionRef = mock(DocumentReference::class.java)
    private val rejectionDoc = mock(DocumentSnapshot::class.java)
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
        `when`(parent.collection("rejections")).thenReturn(rejections); `when`(rejections.document(any())).thenReturn(rejectionRef)
        `when`(tx.get(rejectionRef)).thenReturn(rejectionDoc)
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
    private fun clock(date: String) = Clock.fixed(LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
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
        assertEquals(GoalRemoteOutcome.Confirmed, remote.apply("a", op))
        verify(tx, never()).get(parent); verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        verify(tx, never()).get(rejectionRef)
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
    @Test fun `expired create and deadline changing edit write only the immutable expiry fence`() = runTest {
        setup()
        val dated = FirestoreGoalRemote(firestore, clock("2026-03-01")) { owner }
        val proposal = config.copy(deadline = "2026-02-28")
        proposal.let { SavingsMath.validate(it, LocalDate.parse("2026-01-31")) }
        val create = GoalOperation("expired-create", "CREATE", goal = proposal)
        assertEquals(true, (dated.apply("a", create) as GoalRemoteOutcome.Rejected).deadlineExpired)
        val marker = argumentCaptor<Map<String, Any>>()
        verify(tx).set(eq(rejectionRef), marker.capture())
        assertEquals(mapOf("kind" to "CREATE", "version" to 0L, "payload" to proposal.values(), "createdAt" to FieldValue.serverTimestamp()), marker.firstValue)
        clearInvocations(tx); existing()
        val edit = GoalOperation("expired-edit", "EDIT", 0, goal = proposal, previousDeadline = config.deadline)
        assertEquals(true, (dated.apply("a", edit) as GoalRemoteOutcome.Rejected).deadlineExpired)
        verify(tx).set(eq(rejectionRef), any<Map<String, Any>>())
        verify(tx, never()).update(eq(parent), any<Map<String, Any>>()); verify(tx, never()).set(eq(parent), any<Map<String, Any>>())
        verify(tx, never()).set(eq(receiptRef), any<Map<String, Any>>())
    }
    @Test fun `matching expiry marker reconciles without a parent or financial write and rejects changed payload`() = runTest {
        setup()
        val op = GoalOperation("expired", "CREATE", goal = config.copy(deadline = "2025-12-31"))
        `when`(rejectionDoc.exists()).thenReturn(true)
        `when`(rejectionDoc.getString("kind")).thenReturn("CREATE"); `when`(rejectionDoc.getLong("version")).thenReturn(0L)
        `when`(rejectionDoc.get("payload")).thenReturn(op.payload()); `when`(rejectionDoc.getTimestamp("createdAt")).thenReturn(Timestamp(1, 0))
        assertTrue((remote.apply("a", op) as GoalRemoteOutcome.Rejected).deadlineExpired)
        verify(tx, never()).get(parent); verify(tx, never()).set(any<DocumentReference>(), any<Map<String, Any>>())
        try { remote.apply("a", op.copy(goal = op.goal!!.copy(openingMinor = 1))); fail() } catch (_: IllegalStateException) {}
    }
    @Test fun `unchanged overdue edit remains legal and does not create an expiry fence`() = runTest {
        setup(); existing()
        val dated = FirestoreGoalRemote(firestore, clock("2028-01-01")) { owner }
        assertEquals(GoalRemoteOutcome.Confirmed, dated.apply("a", GoalOperation("overdue", "EDIT", 0, goal = config.copy(name = "Still editable"), previousDeadline = config.deadline)))
        verify(tx, never()).set(eq(rejectionRef), any<Map<String, Any>>())
        verify(tx).update(eq(parent), any<Map<String, Any>>())
    }
    @Test fun `expired outstanding Task can be fenced without awaiting it or submitting another financial mutation`() = runTest {
        setup()
        var day = "2026-01-31"
        val mutableClock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = clock(day).withZone(zone)
            override fun instant() = clock(day).instant()
        }
        val dated = FirestoreGoalRemote(firestore, mutableClock) { owner }
        val source = TaskCompletionSource<GoalRemoteOutcome>()
        val expiryTask = TaskCompletionSource<GoalRemoteOutcome>()
        var expiryOutcome: GoalRemoteOutcome? = null
        val op = GoalOperation("outstanding-expiry", "CREATE", goal = config.copy(deadline = "2026-02-28"))
        var invocation = 0
        doAnswer { args ->
            invocation++
            if (invocation == 1) source.task else {
                expiryOutcome = args.getArgument<Transaction.Function<GoalRemoteOutcome>>(0).apply(tx)
                expiryTask.task
            }
        }.`when`(firestore).runTransaction(any<Transaction.Function<GoalRemoteOutcome>>())
        val wait = backgroundScope.launch { withTimeoutOrNull(10000) { dated.apply("a", op) } }
        runCurrent(); advanceTimeBy(10001); runCurrent(); wait.join()
        day = "2026-03-01"
        val fenceWait = backgroundScope.launch { withTimeoutOrNull(10000) { dated.apply("a", op) } }
        runCurrent(); advanceTimeBy(10001); runCurrent(); fenceWait.join()
        assertFalse(source.task.isComplete); assertEquals(2, invocation)
        source.setException(java.io.IOException("Old write fenced by rules"))
        var result: GoalRemoteOutcome? = null
        val reconcile = backgroundScope.launch { result = dated.apply("a", op) }; runCurrent()
        assertEquals(2, invocation) // Reuse the outstanding fence even after the original Task completes.
        expiryTask.setResult(expiryOutcome!!); runCurrent(); reconcile.join()
        assertTrue((result as GoalRemoteOutcome.Rejected).deadlineExpired)
        verify(tx).set(eq(rejectionRef), any<Map<String, Any>>())
        verify(tx, never()).set(eq(parent), any<Map<String, Any>>()); verify(tx, never()).update(eq(parent), any<Map<String, Any>>())
    }
    @Test fun `server refusal of a client proposed expiry is not returned as a rejection`() = runTest {
        setup()
        doAnswer { args ->
            args.getArgument<Transaction.Function<GoalRemoteOutcome>>(0).apply(tx)
            TaskCompletionSource<GoalRemoteOutcome>().also { it.setException(java.io.IOException("Server date does not establish expiry")) }.task
        }.`when`(firestore).runTransaction(any<Transaction.Function<GoalRemoteOutcome>>())
        val dated = FirestoreGoalRemote(firestore, clock("2028-01-01")) { owner }
        try { dated.apply("a", GoalOperation("clock-ahead", "CREATE", goal = config)); fail() } catch (_: java.io.IOException) {}
        verify(tx).set(eq(rejectionRef), any<Map<String, Any>>())
        verify(tx, never()).set(eq(parent), any<Map<String, Any>>())
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
