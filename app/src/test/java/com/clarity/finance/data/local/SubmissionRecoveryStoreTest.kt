package com.clarity.finance.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionRecoveryStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `operation payload is immutable and identity includes uid and operation type`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { tempFolder.newFile("operations.preferences_pb") })
        val store = SubmissionRecoveryStore(dataStore)
        val original = SubmissionRecord("u1", "same", "original", "PENDING", type = "CREATE_PROJECT")
        store.saveOperation(original)
        store.saveOperation(original)
        store.saveOperation(original.copy(uid = "u2", payloadJson = "other user"))
        store.saveOperation(original.copy(type = "CREATE_MILESTONE", payloadJson = "other type"))
        try {
            store.saveOperation(original.copy(payloadJson = "changed"))
            fail("Must reject a changed retry payload")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf(original), store.getRecords("u1", "CREATE_PROJECT"))
        assertEquals("other user", store.getRecords("u2", "CREATE_PROJECT").single().payloadJson)
        assertEquals("other type", store.getRecords("u1", "CREATE_MILESTONE").single().payloadJson)
    }

    @Test
    fun `saveRecord and removeRecord preserve records with same recordId under different uid`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(testDispatcher),
            produceFile = { tempFolder.newFile("test_recovery_${System.currentTimeMillis()}.preferences_pb") }
        )

        val store = SubmissionRecoveryStore(dataStore)

        val recordUid1 = SubmissionRecord("uid1", "same_id", "{}", "PENDING", type = "PAYMENT_DIRECT")
        val recordUid2 = SubmissionRecord("uid2", "same_id", "{}", "PENDING", type = "PAYMENT_DIRECT")

        store.saveRecord(recordUid1)
        store.saveRecord(recordUid2)

        val recordsUid1 = store.getRecords("uid1", "PAYMENT_DIRECT")
        val recordsUid2 = store.getRecords("uid2", "PAYMENT_DIRECT")

        assertEquals(1, recordsUid1.size)
        assertEquals("uid1", recordsUid1.first().uid)
        assertEquals(1, recordsUid2.size)
        assertEquals("uid2", recordsUid2.first().uid)

        // Removing uid1's record must preserve uid2's record
        store.removeRecord("uid1", "same_id", "PAYMENT_DIRECT")

        val afterRemoveUid1 = store.getRecords("uid1", "PAYMENT_DIRECT")
        val afterRemoveUid2 = store.getRecords("uid2", "PAYMENT_DIRECT")

        assertTrue(afterRemoveUid1.isEmpty())
        assertEquals(1, afterRemoveUid2.size)
        assertEquals("uid2", afterRemoveUid2.first().uid)
    }
}
