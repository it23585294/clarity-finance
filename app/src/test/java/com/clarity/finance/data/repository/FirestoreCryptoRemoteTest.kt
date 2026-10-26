package com.clarity.finance.data.repository

import com.clarity.finance.data.model.CryptoResult
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCryptoRemoteTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val users = mock(CollectionReference::class.java)
    private val user = mock(DocumentReference::class.java)
    private val crypto = mock(CollectionReference::class.java)
    private val ref = mock(DocumentReference::class.java)
    private val tx = mock(Transaction::class.java)
    private val snapshot = mock(DocumentSnapshot::class.java)
    private var owner = "a"
    private val remote = FirestoreCryptoRemote(firestore) { owner }
    private val result = CryptoResult("r", "gain", 100, "2025-01-01")

    @Suppress("UNCHECKED_CAST")
    private fun setup(existing: Boolean) {
        `when`(firestore.collection("users")).thenReturn(users)
        `when`(users.document("a")).thenReturn(user)
        `when`(user.collection("cryptoResults")).thenReturn(crypto)
        `when`(crypto.document("r")).thenReturn(ref)
        `when`(tx.get(ref)).thenReturn(snapshot)
        `when`(snapshot.exists()).thenReturn(existing)
        if (existing) {
            `when`(snapshot.id).thenReturn("r")
            `when`(snapshot.metadata).thenReturn(mock(SnapshotMetadata::class.java))
            `when`(snapshot.getString("direction")).thenReturn("gain")
            `when`(snapshot.getLong("amountMinor")).thenReturn(100)
            `when`(snapshot.getString("resultDate")).thenReturn("2025-01-01")
            `when`(snapshot.getTimestamp("createdAt")).thenReturn(Timestamp(1, 0))
            `when`(snapshot.getTimestamp("updatedAt")).thenReturn(Timestamp(2, 0))
        }
        doAnswer { invocation ->
            invocation.getArgument<Transaction.Function<Unit>>(0).apply(tx)
            val task = mock(Task::class.java) as Task<Unit>
            `when`(task.isComplete).thenReturn(true)
            `when`(task.isSuccessful).thenReturn(true)
            `when`(task.result).thenReturn(Unit)
            task
        }.`when`(firestore).runTransaction(any<Transaction.Function<Unit>>())
    }

    @Test fun `create writes dedicated schema and update omits creation timestamp`() = runTest {
        setup(false)
        remote.apply("a", CryptoOperation("create", "CREATE", result))
        val created = argumentCaptor<Map<String, Any>>()
        verify(tx).set(eq(ref), created.capture())
        assertEquals(setOf("direction", "amountMinor", "resultDate", "note", "lastOperationId", "createdAt", "updatedAt"), created.firstValue.keys)
        assertEquals(FieldValue.serverTimestamp(), created.firstValue["createdAt"])
        assertEquals(100L, created.firstValue["amountMinor"])
        setup(true)
        remote.apply("a", CryptoOperation("edit", "UPDATE", result.copy(direction = "loss"), Timestamp(2, 0)))
        val updated = argumentCaptor<Map<String, Any>>()
        verify(tx).update(eq(ref), updated.capture())
        assertFalse(updated.firstValue.containsKey("createdAt"))
        assertEquals("loss", updated.firstValue["direction"])
        verify(user, never()).collection("incomeReceipts")
        verify(user, never()).collection("expenses")
    }

    @Test fun `account change after transaction read prevents the write`() = runTest {
        setup(false)
        `when`(tx.get(ref)).thenAnswer { owner = "b"; snapshot }
        try { remote.apply("a", CryptoOperation("op", "CREATE", result)); fail() }
        catch (_: IllegalStateException) { }
        verify(tx, never()).set(eq(ref), any<Map<String, Any>>())
        verify(tx, never()).delete(ref)
    }

    @Test fun `bounded listener is removed when observation is cancelled`() = runTest {
        setup(false)
        val query = mock(Query::class.java)
        val registration = mock(ListenerRegistration::class.java)
        `when`(crypto.whereGreaterThanOrEqualTo("resultDate", "2025-01-01")).thenReturn(query)
        `when`(query.whereLessThanOrEqualTo("resultDate", "2025-12-31")).thenReturn(query)
        `when`(query.orderBy("resultDate")).thenReturn(query)
        `when`(query.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())).thenReturn(registration)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            remote.observe("a", 2025, "2025-12-31").collect { }
        }
        runCurrent()
        job.cancelAndJoin()
        verify(registration).remove()
    }
}
