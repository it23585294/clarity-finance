package com.clarity.finance.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clarity.finance.data.local.SubmissionRecord
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCryptoRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val operation = CryptoOperation("op-1", "CREATE", CryptoResult("r1", "gain", 6000000, "2025-01-01"))

    private class Remote : CryptoRemote {
        val snapshots = MutableStateFlow(CryptoRemoteSnapshot(emptyList(), true))
        var gate: CompletableDeferred<Unit>? = null
        var commitBeforeGate = false
        var failure: Exception? = IOException("Offline")
        val calls = mutableListOf<Pair<String, CryptoOperation>>()
        val server = mutableMapOf<Pair<String, String>, CryptoResult>()
        var writes = 0
        override fun observe(uid: String, year: Int, cutoff: String) = snapshots.map { it.copy(results = it.results.filter { row ->
            row.resultDate >= "$year-01-01" && row.resultDate <= cutoff
        }) }
        private fun commit(uid: String, operation: CryptoOperation) {
            val key = uid to operation.result.id
            if (operation.requiresWrite(server[key])) {
                writes++
                if (operation.kind == "DELETE") server.remove(key)
                else server[key] = operation.result.copy(createdAt = Timestamp(1, 0), updatedAt = Timestamp(2, 0), lastOperationId = operation.operationId)
            }
            snapshots.value = CryptoRemoteSnapshot(server.filterKeys { it.first == uid }.values.toList(), false)
        }
        override suspend fun apply(uid: String, operation: CryptoOperation) {
            calls += uid to operation
            if (commitBeforeGate) commit(uid, operation)
            gate?.await()
            failure?.let { throw it }
            if (!commitBeforeGate) commit(uid, operation)
        }
    }

    @Test fun `stable local retry survives repository restart and only recovers the owner crypto operations`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { temp.newFile("restart.preferences_pb") }))
        val remote = Remote()
        var repository = FirestoreCryptoRepository(remote, store)
        store.saveRecord(SubmissionRecord("a", "legacy", "{}", "PENDING"))
        repository.submit("a", operation)
        repository.submit("a", operation)
        repository.submit("b", operation.copy(operationId = "b-op"))
        var latest = CryptoSnapshot()
        var observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe("a", 2026, "2026-10-26").collect { latest = it }
        }
        runCurrent()
        assertEquals(CryptoSync.PENDING, latest.results.single().sync)
        assertEquals(listOf("a"), remote.calls.map { it.first })
        assertEquals(1, store.getRecords("a", "CRYPTO_CREATE").size)
        try { repository.submit("a", operation.copy(kind = "UPDATE")); fail() }
        catch (_: IllegalStateException) { }
        observer.cancelAndJoin()
        // A new repository uses the same persisted store; backdated attempts sync even outside the selected year.
        repository = FirestoreCryptoRepository(remote, store)
        remote.failure = null
        observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe("a", 2026, "2026-10-26").collect { latest = it }
        }
        runCurrent()
        assertTrue(store.getRecords("a", "CRYPTO_CREATE").isEmpty())
        assertEquals(1, store.getRecords("b", "CRYPTO_CREATE").size)
        assertEquals(1, store.getRecords("a", "EXPENSE").size)
        assertTrue(remote.calls.all { it.first == "a" && it.second == operation })
        assertEquals(1, remote.writes)
        assertEquals(1, remote.server.size)
        observer.cancelAndJoin()
    }

    @Test fun `timeout leaves immutable pending payload and later retry reconciles without another identity`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { temp.newFile("timeout.preferences_pb") }))
        val remote = Remote().apply { gate = CompletableDeferred(); failure = null; commitBeforeGate = true }
        val repository = FirestoreCryptoRepository(remote, store)
        repository.submit("a", operation)
        var latest = CryptoSnapshot()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe("a", 2025, "2025-12-31").collect { latest = it }
        }
        runCurrent()
        advanceTimeBy(10001); runCurrent()
        assertEquals("PENDING", store.getRecords("a", "CRYPTO_CREATE").single().status)
        assertEquals(CryptoSync.PENDING, latest.results.single().sync)
        try { repository.submit("a", operation.copy(result = operation.result.copy(amountMinor = 1))); fail() }
        catch (_: IllegalStateException) { }
        try { repository.dismissRejected("a", operation); fail() } catch (_: IllegalStateException) { }
        remote.gate!!.complete(Unit)
        advanceTimeBy(5000); runCurrent()
        assertTrue(store.getRecords("a", "CRYPTO_CREATE").isEmpty())
        assertTrue(remote.calls.all { it.second == operation })
        assertEquals(1, remote.writes)
        assertEquals(1, remote.server.size)
        job.cancelAndJoin()
    }

    @Test fun `definitive rejection remains visible retryable and dismissible without changing other records`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { temp.newFile("failed.preferences_pb") }))
        val remote = Remote().apply { failure = IllegalStateException("Changed elsewhere") }
        val repository = FirestoreCryptoRepository(remote, store)
        repository.submit("a", operation)
        repository.submit("b", operation)
        var latest = CryptoSnapshot()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe("a", 2025, "2025-12-31").collect { latest = it }
        }
        runCurrent()
        assertEquals(CryptoSync.FAILED, latest.results.single().sync)
        assertEquals("Changed elsewhere", latest.results.single().error)
        repository.retry("a", operation)
        assertEquals("PENDING", store.getRecords("a", "CRYPTO_CREATE").single().status)
        advanceTimeBy(5000); runCurrent()
        repository.dismissRejected("a", operation)
        runCurrent()
        assertTrue(latest.results.isEmpty())
        assertEquals(1, store.getRecords("b", "CRYPTO_CREATE").size)
        job.cancelAndJoin()
    }
}
