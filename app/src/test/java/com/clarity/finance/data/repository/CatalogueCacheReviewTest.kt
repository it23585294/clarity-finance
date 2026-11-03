package com.clarity.finance.data.repository

import androidx.datastore.preferences.core.*
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueCacheReviewTest {
    @get:org.junit.Rule val files = org.junit.rules.TemporaryFolder()
    private class ListenerFixture(uid: String) {
        val remote = FinancialRemoteFixture(uid)
        val callbacks = mutableMapOf<String, EventListener<QuerySnapshot>>()
        init {
            for (collection in listOf("expenseCategories", "incomeSources")) {
                val ref = remote.db.collection("users").document(uid).collection(collection)
                doAnswer { callbacks[collection] = it.getArgument(1); mock(ListenerRegistration::class.java) }
                    .`when`(ref).addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
            }
        }
        fun emit(collection: String, cached: Boolean) {
            val metadata = mock(SnapshotMetadata::class.java); `when`(metadata.isFromCache).thenReturn(cached)
            val empty = mock(QuerySnapshot::class.java); `when`(empty.metadata).thenReturn(metadata)
            `when`(empty.documents).thenReturn(emptyList()); callbacks.getValue(collection).onEvent(empty, null)
        }
    }
    @Test fun `cold empty cache must not invent active builtins before server overrides are known`() = runTest {
        val store = SubmissionRecoveryStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { files.newFile("cold.preferences_pb") }))
        val fixture = ListenerFixture("a"); var latest = CatalogueSnapshot()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FirestoreCatalogueRepository(fixture.remote.db, store).observe("a").collect { latest = it }
        }
        withTimeout(10000) { while (fixture.callbacks.size != 2) yield() }
        fixture.callbacks.keys.forEach { fixture.emit(it, true) }; runCurrent()
        assertNull(latest.entries); assertFalse(latest.accepts("category", "other"))
        assertTrue(latest.choices("source").isEmpty())
        assertEquals("Category label unavailable (other)", latest.label("category", "other"))
        // A server answer for only one collection is not a complete catalogue.
        fixture.emit("expenseCategories", false); runCurrent(); assertNull(latest.entries)
        fixture.emit("incomeSources", false); runCurrent()
        withTimeout(10000) { while (latest.entries == null) yield() }
        assertTrue(latest.accepts("source", "salary")); assertFalse(latest.fromCache)
        job.cancelAndJoin()
    }
    @Test fun `reopened catalogue retains confirmed archived names through partial cache without touching corrupt recovery or another UID`() = runTest {
        val disk = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { files.newFile("warm.preferences_pb") })
        val raw = "{preserved corrupt recovery bytes}"
        disk.edit { it[stringPreferencesKey("submission_records")] = raw }
        val store = SubmissionRecoveryStore(disk)
        val rows = CatalogueDefaults.merge(listOf(CatalogueEntry("housing", "category", "Former rent", true,
            "committed", Timestamp(10, 0)), CatalogueEntry("custom_client", "source", "Former client", true, "USD", Timestamp(10, 0))))
        store.saveConfirmedCatalogue("a", rows)
        val reopened = SubmissionRecoveryStore(disk)
        assertNull(reopened.confirmedCatalogue("b"))
        val fixture = ListenerFixture("a"); var latest = CatalogueSnapshot()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FirestoreCatalogueRepository(fixture.remote.db, reopened).observe("a").collect { latest = it }
        }
        withTimeout(10000) { while (fixture.callbacks.size != 2) yield() }
        fixture.callbacks.keys.forEach { fixture.emit(it, true) }; runCurrent()
        assertEquals("Former rent", latest.label("category", "housing")); assertTrue(latest.fromCache)
        assertFalse(latest.accepts("category", "housing")); assertTrue(latest.accepts("category", "housing", "housing"))
        assertFalse(latest.accepts("source", "custom_client"))
        fixture.emit("incomeSources", false); runCurrent()
        assertEquals("Former client", latest.label("source", "custom_client"))
        assertEquals(raw, disk.data.first()[stringPreferencesKey("submission_records")])
        job.cancelAndJoin()
    }
}
