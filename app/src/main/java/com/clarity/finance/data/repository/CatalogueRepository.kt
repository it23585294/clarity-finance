package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive

interface CatalogueRepository { fun observe(uid: String): Flow<CatalogueSnapshot> }

class FirestoreCatalogueRepository(private val db: FirebaseFirestore,
    private val store: com.clarity.finance.data.local.SubmissionRecoveryStore) : CatalogueRepository {
    private fun entries(uid: String, kind: String): Flow<Pair<List<CatalogueEntry>, Boolean>> = callbackFlow {
        val registration = db.collection("users").document(uid)
            .collection(if (kind == "category") "expenseCategories" else "incomeSources")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) try {
                    check(!snapshot.metadata.hasPendingWrites()) { "Catalogue awaiting server confirmation." }
                    val rows = snapshot.documents.map { doc ->
                        check(doc.get("createdAt") is com.google.firebase.Timestamp && doc.get("updatedAt") is com.google.firebase.Timestamp) { "Missing confirmed catalogue timestamps." }
                        CatalogueEntry(doc.id, kind, doc.text("name"), doc.get("archived") as? Boolean ?: error("Invalid archive flag."),
                            doc.text("defaultValue"), doc.getTimestamp("updatedAt")).also { it.validate() }
                    }
                    trySend(rows to snapshot.metadata.isFromCache)
                } catch (e: Exception) { close(e) }
            }
        awaitClose { registration.remove() }
    }
    override fun observe(uid: String): Flow<CatalogueSnapshot> = flow {
        require(uid.isNotBlank())
        var last = CatalogueSnapshot()
        try { last = last.copy(entries = store.confirmedCatalogue(uid)); emit(last) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { emit(last.copy(error = "Confirmed catalogue cache unavailable: ${e.message}")) }
        while (currentCoroutineContext().isActive) {
            try {
                combine(entries(uid, "category"), entries(uid, "source")) { categories, sources ->
                    if (categories.second || sources.second) null
                    else CatalogueSnapshot(CatalogueDefaults.merge(categories.first + sources.first), false)
                }.collect { complete ->
                    if (complete == null) emit(last.copy(fromCache = true, error = if (last.entries == null)
                        "Catalogue is not available in confirmed cache. Connect to load choices and labels." else null))
                    else {
                        var confirmed = complete
                        try { store.saveConfirmedCatalogue(uid, complete.entries!!) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { confirmed = complete.copy(error = "Server catalogue loaded; local cache could not be saved: ${e.message}") }
                        last = confirmed; emit(confirmed)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { emit(last.copy(error = "Catalogue unavailable: ${e.message}")) }
            delay(5000)
        }
    }
}
