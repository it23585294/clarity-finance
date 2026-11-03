package com.clarity.finance.data.repository

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull

/** Synchronous transaction body plus a real Task lifetime; all suspending waits remain outside Mockito answers. */
internal class FinancialRemoteFixture(val uid: String = "alice") {
    val db = mock(FirebaseFirestore::class.java)
    val tx = mock(Transaction::class.java)
    val documents = mutableMapOf<String, Map<String, Any?>>()
    val writes = mutableListOf<Triple<String, String, Map<String, Any?>?>>()
    private val refs = mutableMapOf<String, DocumentReference>()
    private val collections = mutableMapOf<String, CollectionReference>()
    var active: String? = uid
    var taskGate: TaskCompletionSource<Any>? = null
    val remote = FirestoreFinancialRemote(db) { active }
    private var seconds = 100L
    init {
        `when`(db.collection(any<String>())).thenAnswer { collection(it.getArgument(0)) }
        `when`(tx.get(any<DocumentReference>())).thenAnswer { snapshot(it.getArgument<DocumentReference>(0).path) }
        doAnswer { args ->
            @Suppress("UNCHECKED_CAST") val function = args.getArgument<Transaction.Function<Any>>(0)
            val start = writes.size
            val result = function.apply(tx)
            val stamp = Timestamp(++seconds, 0)
            for ((kind, path, data) in writes.drop(start)) {
                val resolved = data?.mapValues { (_, v) -> if (v is FieldValue) stamp else v }.orEmpty()
                when (kind) { "set" -> documents[path] = resolved; "update" -> documents[path] = documents[path].orEmpty() + resolved; "delete" -> documents.remove(path) }
            }
            taskGate?.task ?: Tasks.forResult(result)
        }.`when`(db).runTransaction(any<Transaction.Function<Any>>())
        doAnswer { args ->
            writes += Triple("set", args.getArgument<DocumentReference>(0).path, args.getArgument<Map<String, Any?>>(1)); tx
        }.`when`(tx).set(any<DocumentReference>(), any<Map<String, Any?>>())
        doAnswer { args ->
            writes += Triple("update", args.getArgument<DocumentReference>(0).path, args.getArgument<Map<String, Any?>>(1)); tx
        }.`when`(tx).update(any<DocumentReference>(), any<Map<String, Any?>>())
        doAnswer { args -> writes += Triple("delete", args.getArgument<DocumentReference>(0).path, null); tx }
            .`when`(tx).delete(any<DocumentReference>())
    }
    fun path(collection: String, id: String) = "users/$uid/$collection/$id"
    fun ref(path: String): DocumentReference = refs.getOrPut(path) {
        mock(DocumentReference::class.java).also { ref ->
            `when`(ref.path).thenReturn(path); `when`(ref.id).thenReturn(path.substringAfterLast('/'))
            `when`(ref.collection(any<String>())).thenAnswer { collection("$path/${it.getArgument<String>(0)}") }
            `when`(ref.get()).thenAnswer { Tasks.forResult(snapshot(path)) }
        }
    }
    private fun collection(path: String): CollectionReference = collections.getOrPut(path) {
        mock(CollectionReference::class.java).also { c -> `when`(c.document(any<String>())).thenAnswer { ref("$path/${it.getArgument<String>(0)}") } }
    }
    fun snapshot(path: String): DocumentSnapshot {
        val data = documents[path]
        return mock(DocumentSnapshot::class.java).also { snapshot ->
            `when`(snapshot.id).thenReturn(path.substringAfterLast('/')); `when`(snapshot.exists()).thenReturn(data != null)
            `when`(snapshot.metadata).thenReturn(mock(SnapshotMetadata::class.java))
            `when`(snapshot.get(any<String>())).thenAnswer { data?.get(it.getArgument<String>(0)) }
            `when`(snapshot.contains(any<String>())).thenAnswer { data?.containsKey(it.getArgument(0)) ?: false }
            `when`(snapshot.getString(any<String>())).thenAnswer { data?.get(it.getArgument<String>(0)) as? String }
            `when`(snapshot.getLong(any<String>())).thenAnswer { data?.get(it.getArgument<String>(0)) as? Long }
            `when`(snapshot.getBoolean(any<String>())).thenAnswer { data?.get(it.getArgument<String>(0)) as? Boolean }
            `when`(snapshot.getTimestamp(any<String>())).thenAnswer { data?.get(it.getArgument<String>(0)) as? Timestamp }
        }
    }
}
