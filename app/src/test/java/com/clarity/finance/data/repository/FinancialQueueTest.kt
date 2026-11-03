package com.clarity.finance.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.Expense
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialQueueTest {
    @get:Rule val files = TemporaryFolder()
    private fun op(id: String) = FinancialOperation("op_$id", "EXPENSE", "CREATE", id,
        expense = Expense(id, 85000, categoryId = "transport", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2026-01-31"))
    private fun TestScope.store() = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
        produceFile = { files.newFile("queue.preferences_pb") }))

    private class Remote(private val scope: CoroutineScope) : FinancialRemote {
        val permit = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val receipts = mutableMapOf<Pair<String, String>, FinancialOperation>()
        val fences = mutableSetOf<Pair<String, String>>()
        val running = mutableMapOf<Pair<String, String>, Deferred<Result<Unit>>>()
        var denied = false
        var mutations = 0
        override suspend fun apply(uid: String, operation: FinancialOperation) {
            if (denied) error("Account/access error before reconciliation")
            val key = uid to operation.operationId
            receipts[key]?.let { check(it == operation); return }
            if (key in fences) throw DefinitiveMutationRejection("Server fenced")
            started.complete(Unit)
            running.getOrPut(key) { scope.async {
                permit.await() // A genuinely suspending remote lifetime, independent of the caller's await.
                if (key in fences) Result.failure(DefinitiveMutationRejection("Fence won"))
                else { if (receipts.putIfAbsent(key, operation) == null) mutations++; Result.success(Unit) }
            } }.await().getOrThrow()
        }
        override suspend fun fence(uid: String, operation: FinancialOperation) {
            if (denied) error("Cannot establish a server fence offline")
            val key = uid to operation.operationId
            receipts[key]?.let { check(it == operation); return }
            fences += key
            throw DefinitiveMutationRejection("Authoritative fence")
        }
    }

    @Test fun `three offline records persist before any remote submission and recover with stable identities`() = runTest {
        val store = store(); val remote = Remote(backgroundScope); val queue = FinancialQueue(remote, store)
        val operations = (1..3).map { op("expense_$it") }
        operations.forEach { queue.submit("a", it); queue.submit("a", it) }
        assertEquals(operations, queue.pending("a").map { it.operation }); assertEquals(0, remote.mutations)
        val reopened = FinancialQueue(remote, store)
        remote.permit.complete(Unit)
        backgroundScope.launch { reopened.synchronize("a") }
        runCurrent()
        // DataStore uses real suspending IO; observe its completion instead of blocking a mock answer.
        withTimeout(10000) { while (reopened.pending("a").isNotEmpty()) { yield() } }
        assertEquals(3, remote.mutations)
        assertEquals(operations.toSet(), remote.receipts.values.toSet())
        assertTrue(reopened.pending("b").isEmpty())
    }

    @Test fun `timeout then access error cannot dismiss an outstanding earlier commit`() = runTest {
        val store = store(); val remote = Remote(backgroundScope); val queue = FinancialQueue(remote, store); val original = op("one")
        queue.submit("a", original)
        backgroundScope.launch { queue.synchronize("a") }
        remote.started.await(); advanceTimeBy(10001); runCurrent()
        assertEquals("UNCERTAIN", queue.pending("a").single().status)
        remote.denied = true
        advanceTimeBy(5001); runCurrent()
        assertEquals(original, queue.pending("a").single().operation)
        assertTrue(queue.pending("a").single().error!!.contains("Account/access error"))
        assertTrue(queue.pending("a").single().error!!.contains("uncertain"))
        try { queue.dismissRejected("a", original); fail("Uncertain operation is not dismissible") } catch (_: IllegalStateException) { }
        remote.permit.complete(Unit); runCurrent()
        assertEquals(1, remote.mutations)
        remote.denied = false
        advanceTimeBy(5001); runCurrent()
        withTimeout(10000) { while (queue.pending("a").isNotEmpty()) yield() }
        assertEquals(1, remote.mutations)
    }

    @Test fun `an acknowledged fence permits dismissal and wins against an outstanding mutation`() = runTest {
        val store = store(); val remote = Remote(backgroundScope); val queue = FinancialQueue(remote, store); val original = op("one")
        queue.submit("a", original); backgroundScope.launch { queue.synchronize("a") }; remote.started.await()
        queue.resolve("a", original)
        assertTrue(queue.pending("a").single().rejected)
        queue.dismissRejected("a", original)
        remote.permit.complete(Unit); runCurrent()
        assertEquals(0, remote.mutations)
        assertTrue(queue.pending("a").isEmpty())
    }

    @Test fun `account scopes immutable payloads and conflicting edits cannot be overwritten`() = runTest {
        val store = store(); val remote = Remote(backgroundScope); val queue = FinancialQueue(remote, store); val original = op("one")
        queue.submit("a", original); queue.submit("b", original)
        try { queue.submit("a", original.copy(expense = original.expense!!.copy(amountMinor = 99000))); fail("Immutable retry required") } catch (_: IllegalStateException) { }
        assertEquals(85000L, queue.pending("a").single().operation.expense!!.amountMinor)
        assertEquals(original, queue.pending("b").single().operation)
    }
    @Test fun `interrupted acceptance of the same edit reuses its original operation identity and gates dependent changes`() = runTest {
        val store = store(); val queue = FinancialQueue(Remote(backgroundScope), store)
        val initial = op("edit").copy(kind = "UPDATE", expectedUpdatedAt = com.google.firebase.Timestamp(1, 0))
        queue.submit("a", initial)
        queue.submit("a", initial.copy(operationId = "newly_generated_unused_nonce"))
        assertEquals(initial, queue.pending("a").single().operation)
        try { queue.submit("a", initial.copy(kind = "DELETE", expense = null)); fail("Do not delete over an unresolved edit") } catch (_: IllegalStateException) { }
        val p = com.clarity.finance.data.model.ExpectedPayment("p", "project", "P", "salary", "LKR", "2026-12-31", 100000)
        queue.submit("a", FinancialOperation("parent", "PROJECT", "CREATE", "p", project = p))
        try {
            queue.submit("a", FinancialOperation("child", "MILESTONE", "CREATE", "m", "p",
                milestone = com.clarity.finance.data.model.Milestone("m", "p", "M", "2026-12-31", 10000)))
            fail("A child must wait for its parent and exact confirmed version")
        } catch (_: IllegalStateException) { }
    }
    @Test fun `a late uncertain await cannot downgrade a concurrently acknowledged withdrawal fence`() = runTest {
        val store = store(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val remote = object : FinancialRemote {
            override suspend fun apply(uid: String, operation: FinancialOperation) {
                started.complete(Unit); release.await(); error("Access response lost after server fence")
            }
            override suspend fun fence(uid: String, operation: FinancialOperation) { throw DefinitiveMutationRejection("Acknowledged fence") }
        }
        val queue = FinancialQueue(remote, store); val original = op("fenced")
        queue.submit("a", original); val worker = backgroundScope.launch { queue.synchronize("a") }; started.await()
        queue.resolve("a", original); assertTrue(queue.pending("a").single().rejected)
        release.complete(Unit); runCurrent()
        // Wait for the suspended sweep to finish its bookkeeping before inspecting/dismissing.
        advanceTimeBy(1); runCurrent()
        assertTrue(queue.pending("a").single().rejected)
        queue.dismissRejected("a", original); assertTrue(queue.pending("a").isEmpty()); worker.cancelAndJoin()
    }
    @Test fun `late owner A retry error cannot erase owner B recovery feedback`() = runTest {
        val disk = org.mockito.Mockito.mock(SubmissionRecoveryStore::class.java)
        for (uid in listOf("a", "b")) {
            org.mockito.Mockito.`when`(disk.getAllRecordsFlow(uid)).thenReturn(flowOf(emptyList()))
            org.mockito.Mockito.`when`(disk.getAllRecords(uid)).thenAnswer { throw java.io.IOException("Store unavailable for $uid") }
        }
        val queue = FinancialQueue(Remote(backgroundScope), disk)
        var b = FinancialRecovery()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { queue.observe("b").collect { b = it } }
        queue.retryRecord("b", "EXPENSE", "e"); runCurrent()
        assertTrue(b.error!!.contains("for b"))
        queue.retryRecord("a", "EXPENSE", "e"); runCurrent()
        assertTrue(b.error!!.contains("for b")); assertFalse(b.error!!.contains("for a"))
        observer.cancelAndJoin()
    }

    @Test fun `local cleanup failure after acknowledgement cannot submit another mutation even after queue restart`() = runTest {
        val disk = org.mockito.Mockito.spy(store())
        val remote = Remote(backgroundScope); remote.permit.complete(Unit)
        val queue = FinancialQueue(remote, disk); val original = op("cleanup")
        queue.submit("a", original)
        val row = disk.getAllRecords("a").single()
        org.mockito.Mockito.doThrow(java.io.UncheckedIOException(java.io.IOException("Disk cleanup unavailable")))
            .doCallRealMethod().`when`(disk).removeRecord("a", row.recordId, row.resolvedType)
        var feedback = FinancialRecovery()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.observe("a").collect { feedback = it }
        }
        val worker = backgroundScope.launch { queue.synchronize("a") }
        withTimeout(10000) { while (feedback.error == null) yield() }
        assertEquals(1, remote.mutations); assertEquals(original, queue.pending("a").single().operation)
        assertTrue(feedback.error!!.contains("Local recovery unavailable"))
        worker.cancelAndJoin(); observer.cancelAndJoin()
        val reopened = FinancialQueue(remote, disk)
        val replacement = backgroundScope.launch { reopened.synchronize("a") }
        withTimeout(10000) { while (reopened.pending("a").isNotEmpty()) yield() }
        assertEquals(1, remote.mutations); assertEquals(1, remote.receipts.size)
        replacement.cancelAndJoin()
    }

}
