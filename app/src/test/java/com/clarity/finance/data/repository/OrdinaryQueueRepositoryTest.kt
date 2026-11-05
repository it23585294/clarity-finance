package com.clarity.finance.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class OrdinaryQueueRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private fun TestScope.store() = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
        produceFile = { temp.newFile("ordinary.preferences_pb") }))
    private val stamp = Timestamp(1, 0)
    private val income = Income("i", "salary", "2026-01-31", "LKR", "1000", 100000, "native_lkr", "1", "", updatedAt = stamp)
    private val unusedRemote = object : FinancialRemote {
        override suspend fun apply(uid: String, operation: FinancialOperation) = error("A screen must not submit remote writes")
        override suspend fun fence(uid: String, operation: FinancialOperation) = error("Unused")
    }

    @Test fun `ordinary confirmed history orders immutable creation within actual date without financial writes`() = runTest {
        val fixture = FinancialRemoteFixture("a")
        val store = store()
        val repo = FirestoreExpenseRepository(fixture.db, FinancialQueue(unusedRemote, store), backgroundScope)
        val old = Expense("a", 10000, categoryId = "transport", paymentChannelId = "cash",
            spendingType = "committed", expenseDate = "2026-01-31")
        val newer = old.copy(id = "z")
        listOf(old to Timestamp(1, 0), newer to Timestamp(2, 0)).forEach { (e, created) ->
            fixture.documents[fixture.path("expenses", e.id)] = FinancialOperation(e.id, "EXPENSE", "CREATE", e.id, expense = e).payload() +
                mapOf("createdAt" to created, "updatedAt" to Timestamp(999, 0))
        }
        val collection = fixture.db.collection("users").document("a").collection("expenses")
        var listener: EventListener<QuerySnapshot>? = null
        val registration = mock(ListenerRegistration::class.java)
        doAnswer { listener = it.getArgument(1); registration }.`when`(collection)
            .addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
        var latest: List<Expense>? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.getExpenses("a").collect { latest = it } }
        val snapshot = mock(QuerySnapshot::class.java)
        `when`(snapshot.metadata).thenReturn(mock(SnapshotMetadata::class.java))
        val documents = listOf(fixture.snapshot(fixture.path("expenses", "a")), fixture.snapshot(fixture.path("expenses", "z")))
        `when`(snapshot.documents).thenReturn(documents)
        listener!!.onEvent(snapshot, null)
        withTimeout(10000) { while (latest == null) yield() }
        assertEquals(listOf("z", "a"), latest!!.map { it.id })
        assertEquals(Timestamp(2, 0), latest!!.first().createdAt)
        assertEquals(20000L, latest!!.sumOf { it.amountMinor })
        assertTrue(store.getAllRecords("a").isEmpty())
        job.cancelAndJoin(); verify(registration).remove()
    }
    @Test fun `retry bookkeeping failures are feedback rather than uncaught process-scope exceptions`() = runTest {
        val disk = mock(SubmissionRecoveryStore::class.java)
        `when`(disk.getAllRecordsFlow("a")).thenReturn(flowOf(emptyList()))
        `when`(disk.getAllRecords("a")).thenAnswer { throw java.io.IOException("Local store unavailable") }
        val queue = FinancialQueue(unusedRemote, disk); val db = mock(FirebaseFirestore::class.java)
        var feedback = FinancialRecovery()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { queue.observe("a").collect { feedback = it } }
        FirestoreExpenseRepository(db, queue, backgroundScope).retryFailedExpense("a", Expense("e", 100, categoryId = "other",
            paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2026-01-31"))
        FirestoreIncomeRepository(db, queue, backgroundScope).retryFailedIncome("a", income)
        runCurrent()
        assertTrue(feedback.error!!.contains("Retry unavailable")); assertTrue(feedback.error!!.contains("retained"))
        observer.cancelAndJoin(); verifyNoInteractions(db)
    }
    @Test fun `expense and income create edit delete are persisted before any network call and survive repository restart`() = runTest {
        val store = store(); val queue = FinancialQueue(unusedRemote, store); val db = mock(FirebaseFirestore::class.java)
        val expenses = FirestoreExpenseRepository(db, queue, backgroundScope); val incomes = FirestoreIncomeRepository(db, queue, backgroundScope)
        val e = Expense("e", 85000, categoryId = "other", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2026-01-31")
        assertTrue(expenses.enqueueExpense("a", e).isSuccess); assertTrue(expenses.enqueueExpense("a", e).isSuccess)
        assertTrue(expenses.updateExpense("a", e.copy(id = "edit"), stamp).isSuccess)
        assertTrue(expenses.deleteExpense("a", "delete", stamp).isSuccess)
        assertTrue(incomes.enqueueIncome("a", income).isSuccess)
        assertTrue(incomes.updateIncome("a", income.copy(id = "income_edit"), stamp).isSuccess)
        assertTrue(incomes.deleteIncome("a", "income_delete", stamp).isSuccess)
        val reopened = FinancialQueue(unusedRemote, store)
        assertEquals(6, reopened.pending("a").size); assertTrue(reopened.pending("b").isEmpty())
        assertEquals(listOf("CREATE","UPDATE","DELETE","CREATE","UPDATE","DELETE"), reopened.pending("a").map { it.operation.kind })
        assertEquals(100000L, reopened.pending("a").first { it.operation.group == "INCOME" }.operation.income!!.lkrAmountMinor)
        verifyNoInteractions(db)
    }
    @Test fun `pending and rejected income changes keep the original confirmed contribution and expose current recovery feedback`() = runTest {
        val store = store(); val fixture = FinancialRemoteFixture("a")
        val rejecting = object : FinancialRemote {
            override suspend fun apply(uid: String, operation: FinancialOperation) { throw DefinitiveMutationRejection("Authoritative fence") }
            override suspend fun fence(uid: String, operation: FinancialOperation) { throw DefinitiveMutationRejection("Authoritative fence") }
        }
        val queue = FinancialQueue(rejecting, store); val repo = FirestoreIncomeRepository(fixture.db, queue, backgroundScope)
        val path = fixture.path("incomeReceipts", "i")
        fixture.documents[path] = FinancialOperation("seed", "INCOME", "CREATE", "i", income = income).payload() + mapOf("createdAt" to stamp, "updatedAt" to stamp)
        val collection = fixture.db.collection("users").document("a").collection("incomeReceipts")
        var listener: EventListener<QuerySnapshot>? = null
        val registration = mock(ListenerRegistration::class.java)
        doAnswer { listener = it.getArgument(1); registration }.`when`(collection).addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
        var latest: List<Income>? = null
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.getIncomes("a").collect { latest = it } }
        val snapshot = mock(QuerySnapshot::class.java)
        val confirmedDocument = fixture.snapshot(path)
        `when`(snapshot.metadata).thenReturn(mock(SnapshotMetadata::class.java)); `when`(snapshot.documents).thenReturn(listOf(confirmedDocument))
        listener!!.onEvent(snapshot, null)
        assertTrue(repo.updateIncome("a", income.copy(originalAmountStr = "2000", lkrAmountMinor = 200000), stamp).isSuccess)
        withTimeout(10000) { while (latest?.singleOrNull()?.queuedChange == null) yield() }
        assertEquals(100000L, latest!!.single().lkrAmountMinor); assertFalse(latest!!.single().isPending)
        assertTrue(latest!!.single().queuedChange!!.contains("PENDING"))
        val worker = backgroundScope.launch { queue.synchronize("a") }
        withTimeout(10000) { while (latest?.singleOrNull()?.queuedChange?.contains("REJECTED") != true) yield() }
        assertEquals(100000L, latest!!.single().lkrAmountMinor); assertNull(latest!!.single().syncError)
        queue.dismissRejected("a", queue.pending("a").single().operation)
        withTimeout(10000) { while (latest?.singleOrNull()?.queuedChange != null) yield() }
        assertTrue(repo.deleteIncome("a", "i", stamp).isSuccess)
        withTimeout(10000) { while (latest?.singleOrNull()?.queuedChange == null) yield() }
        assertEquals(100000L, latest!!.single().lkrAmountMinor)
        worker.cancelAndJoin(); observer.cancelAndJoin(); verify(registration).remove()
    }
    @Test fun `an initial empty cache keeps listening and never becomes a confirmed empty account`() = runTest {
        val query = mock(Query::class.java); val registration = mock(ListenerRegistration::class.java)
        var callback: EventListener<QuerySnapshot>? = null
        doAnswer { callback = it.getArgument(1); registration }.`when`(query).addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
        var latest: List<String>? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { query.confirmedRows { it.id }.collect { latest = it } }
        val metadata = mock(SnapshotMetadata::class.java); `when`(metadata.isFromCache).thenReturn(true)
        val empty = mock(QuerySnapshot::class.java); `when`(empty.metadata).thenReturn(metadata); `when`(empty.isEmpty).thenReturn(true)
        `when`(empty.documents).thenReturn(emptyList())
        callback!!.onEvent(empty, null); runCurrent(); assertNull(latest); assertTrue(job.isActive)
        `when`(metadata.isFromCache).thenReturn(false)
        callback!!.onEvent(empty, null); runCurrent(); assertEquals(emptyList<String>(), latest)
        job.cancelAndJoin(); verify(registration).remove()
    }
    @Test fun `decoder preserves linked receipt protection and rejects a present invalid optional expense note`() {
        val fixture = FinancialRemoteFixture()
        val linked = income.copy(linkedPaymentId = "p", linkedMilestoneId = "m", originalAmountMinor = 100000)
        val path = fixture.path("incomeReceipts", "i")
        fixture.documents[path] = FinancialOperation("r", "RECEIPT", "CREATE", "i", "p", income = linked).payload() + mapOf("createdAt" to stamp, "updatedAt" to stamp)
        assertEquals("p", fixture.snapshot(path).incomeRow().linkedPaymentId); assertEquals("m", fixture.snapshot(path).incomeRow().linkedMilestoneId)
        val ep = fixture.path("expenses", "e")
        fixture.documents[ep] = mapOf("amountMinor" to 85000L, "currency" to "LKR", "categoryId" to "transport", "paymentChannelId" to "cash", "spendingType" to "discretionary", "expenseDate" to "2026-01-31")
        assertEquals("", fixture.snapshot(ep).expenseRow().note)
        fixture.documents[ep] = fixture.documents[ep]!! + ("note" to null)
        try { fixture.snapshot(ep).expenseRow(); fail("A present null note violates the existing schema") } catch (_: IllegalStateException) { }
    }
}
