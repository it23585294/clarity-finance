package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProtocolPublicationReviewTest {
    @Test fun `withdrawing legacy creation over an existing document installs an unchanged nondeleted record barrier`() = runTest {
        val f = FinancialRemoteFixture(); val row = Expense("e", 85000, categoryId = "transport", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2026-01-31")
        val op = FinancialOperation("legacy", "EXPENSE", "CREATE", "e", expense = row, legacyPayload = "original raw JSON")
        val before = op.payload() + mapOf("amountMinor" to 90000L, "createdAt" to Timestamp(1,0), "updatedAt" to Timestamp(2,0))
        f.documents[f.path("expenses", "e")] = before
        try { f.remote.fence("alice", op); fail("Fence acknowledgement must establish rejection") } catch (_: DefinitiveMutationRejection) { }
        assertEquals(before, f.documents[f.path("expenses", "e")])
        assertEquals(false, f.documents[f.path("syncStates/EXPENSE/records", "e")]?.get("deleted"))
        assertTrue(f.documents.containsKey(f.path("syncFences", "legacy")))
    }
    @Test fun `matching immutable receipt reconciles even after the business record was deleted`() = runTest {
        val f = FinancialRemoteFixture(); val row = Expense("e", 100, categoryId = "other", paymentChannelId = "cash", spendingType = "committed", expenseDate = "2026-01-31")
        val op = FinancialOperation("create", "EXPENSE", "CREATE", "e", expense = row)
        f.remote.apply("alice", op); val writes = f.writes.size
        f.documents.remove(f.path("expenses", "e")) // Emulates a later separately accepted deletion, not a retry write.
        f.remote.apply("alice", op); f.remote.fence("alice", op)
        assertEquals(writes, f.writes.size); assertFalse(f.documents.containsKey(f.path("expenses", "e")))
        assertFalse(f.documents.containsKey(f.path("syncFences", "create")))
    }
    private fun proof(op: FinancialOperation) = mapOf("group" to op.group, "kind" to op.kind,
        "targetId" to op.targetId, "parentId" to (op.parentId ?: ""), "expectedUpdatedAt" to op.expectedUpdatedAt,
        "payload" to op.payload(), "createdAt" to Timestamp(1, 0))
    private suspend fun mustRemainUncertain(action: suspend () -> Unit) {
        try { action(); fail("No success or rejection without matching proof and record protection") }
        catch (_: DefinitiveMutationRejection) { fail("Incomplete/conflicting proof does not establish rejection of a legacy attempt") }
        catch (_: IllegalStateException) { }
    }
    @Test fun `a mismatching receipt cannot definitively reject a different outstanding legacy mutation`() = runTest {
        val f = FinancialRemoteFixture()
        val op = FinancialOperation("legacy", "EXPENSE", "CREATE", "e", expense = Expense("e", 100,
            categoryId = "other", paymentChannelId = "cash", spendingType = "committed", expenseDate = "2026-01-31"), legacyPayload = "raw")
        f.documents[f.path("syncOperations", "legacy")] = proof(op.copy(targetId = "another"))
        mustRemainUncertain { f.remote.apply("alice", op) }; assertTrue(f.writes.isEmpty())
    }
    @Test fun `an existing fence requires matching immutable details and an adopted record barrier before dismissal`() = runTest {
        val f = FinancialRemoteFixture()
        val op = FinancialOperation("legacy", "EXPENSE", "CREATE", "e", expense = Expense("e", 100,
            categoryId = "other", paymentChannelId = "cash", spendingType = "committed", expenseDate = "2026-01-31"), legacyPayload = "raw")
        f.documents[f.path("syncFences", "legacy")] = proof(op)
        mustRemainUncertain { f.remote.fence("alice", op) }
        f.documents[f.path("syncStates/EXPENSE/records", "e")] = mapOf("operationId" to "legacy", "deleted" to true)
        f.documents[f.path("syncFences", "legacy")] = proof(op.copy(targetId = "another"))
        mustRemainUncertain { f.remote.fence("alice", op) }
        f.documents[f.path("syncFences", "legacy")] = proof(op)
        try { f.remote.fence("alice", op); fail() } catch (_: DefinitiveMutationRejection) { }
        assertTrue(f.writes.isEmpty())
    }

}
