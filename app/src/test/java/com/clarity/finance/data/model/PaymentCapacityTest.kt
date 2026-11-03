package com.clarity.finance.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class PaymentCapacityTest {
    private fun project() = ExpectedPayment(
        id = "p", type = "project", title = "Test", sourceId = "freelance",
        currency = "LKR", dueDate = "2026-10-30", agreedAmountMinor = 100000L,
        allocatedAmountMinor = 40000L, directReceivedAmountMinor = 20000L
    )
    @Test fun `direct receipts reduce capacity for new milestones and direct payments`() {
        assertEquals(40000L, project().unallocatedAmountMinor)
        assertEquals(80000L, project().outstandingAmountMinor)
    }
    @Test fun `cancelled milestone retains paid allocation`() {
        val p = project().copy(allocatedAmountMinor = 15000L, directReceivedAmountMinor = 0L,
            milestoneReceivedAmountMinor = 15000L)
        assertEquals(85000L, p.unallocatedAmountMinor)
        assertEquals(85000L, p.outstandingAmountMinor)
    }
    @Test fun `pending cached and malformed documents have distinct states`() {
        assertTrue(project().copy(hasPendingWrites = true).confirmationMessage!!.contains("Awaiting"))
        val cached = project().copy(isFromCache = true, updatedAt = Timestamp(1, 0))
        assertTrue(cached.isFromCache); assertNull(cached.confirmationMessage); assertTrue(cached.canModify)
        assertTrue(project().copy(isFromCache = true).confirmationMessage!!.contains("missing"))
        assertTrue(project().confirmationMessage!!.contains("missing"))
        assertFalse(project().canModify)
        assertTrue(project().copy(updatedAt = Timestamp(1, 0)).canModify)
        assertFalse(project().copy(updatedAt = Timestamp(1, 0), isCancelled = true).canModify)
    }
}
