package com.clarity.finance.data.repository

import com.clarity.finance.data.model.CryptoResult
import com.google.firebase.firestore.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCryptoRemoteTest {
    private val fixture = FinancialRemoteFixture("a")
    private val remote = FirestoreCryptoRemote(fixture.db, { fixture.active }, fixture.remote)
    private val result = CryptoResult("r", "gain", 100, "2025-01-01")
    @Test fun `create writes dedicated schema and update omits creation timestamp`() = runTest {
        remote.apply("a", CryptoOperation("create", "CREATE", result))
        val path = fixture.path("cryptoResults", "r")
        val created = fixture.documents[path]!!
        assertEquals(setOf("direction", "amountMinor", "resultDate", "note", "lastOperationId", "createdAt", "updatedAt"), created.keys)
        assertEquals(100L, created["amountMinor"])
        remote.apply("a", CryptoOperation("edit", "UPDATE", result.copy(direction = "loss"), created["updatedAt"] as com.google.firebase.Timestamp))
        val edit = fixture.writes.last { it.first == "update" && it.second == path }.third!!
        assertFalse(edit.containsKey("createdAt")); assertEquals("loss", edit["direction"])
        assertEquals(created["createdAt"], fixture.documents[path]!!["createdAt"])
        assertTrue(fixture.writes.none { it.second.contains("incomeReceipts") || it.second.contains("expenses/") })
    }
    @Test fun `account change after transaction read prevents the write`() = runTest {
        val path = fixture.path("cryptoResults", "r")
        `when`(fixture.tx.get(fixture.ref(path))).thenAnswer { fixture.active = "b"; fixture.snapshot(path) }
        try { remote.apply("a", CryptoOperation("op", "CREATE", result)); fail() } catch (_: IllegalStateException) { }
        assertTrue(fixture.writes.isEmpty())
    }
    @Test fun `bounded listener is removed when observation is cancelled`() = runTest {
        val crypto = fixture.db.collection("users").document("a").collection("cryptoResults")
        val query = mock(Query::class.java); val registration = mock(ListenerRegistration::class.java)
        `when`(crypto.whereGreaterThanOrEqualTo("resultDate", "2025-01-01")).thenReturn(query)
        `when`(query.whereLessThanOrEqualTo("resultDate", "2025-12-31")).thenReturn(query)
        `when`(query.orderBy("resultDate")).thenReturn(query)
        `when`(query.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(registration)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { remote.observe("a", 2025, "2025-12-31").collect { } }
        runCurrent(); job.cancelAndJoin(); verify(registration).remove()
    }
}
