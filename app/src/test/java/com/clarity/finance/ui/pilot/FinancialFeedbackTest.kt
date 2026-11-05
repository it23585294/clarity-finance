package com.clarity.finance.ui.pilot

import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class FinancialFeedbackTest {
    @Test fun archivedAndUnknownReferencesRemainDistinctFromActiveChoices() {
        val c = CatalogueSnapshot(CatalogueDefaults.merge(listOf(CatalogueEntry("salary", "source", "Former salary", true))), false)
        assertEquals("Former salary (archived)", c.referenceLabel("source", "salary"))
        assertFalse(c.accepts("source", "salary"))
        assertTrue(c.accepts("source", "salary", "salary"))
        assertEquals("Source label unavailable (salary)", CatalogueSnapshot().referenceLabel("source", "salary"))
    }
    @Test fun accessErrorIsNotPresentedAsRejectionAndFormattingDoesNotMutateProposal() {
        val row = Income("row", "salary", "2026-01-31", "USD", "1000.50", 31015500, "confirmed_rate", "310", "Fictional")
        val op = FinancialOperation("same-operation", "INCOME", "UPDATE", "row", expectedUpdatedAt = Timestamp(1,0), income = row)
        val attempt = FinancialAttempt(op, "UNCERTAIN", "PERMISSION_DENIED after an earlier attempt")
        assertEquals("Edit income", financialChangeTitle(op))
        assertTrue(financialChangeStatus(attempt).startsWith("Confirmation unknown"))
        assertFalse(attempt.rejected)
        val details = financialChangeDetails(op, CatalogueSnapshot(CatalogueDefaults.entries(), false))
        assertTrue(details.contains("Original amount" to "USD 1,000.50"))
        assertTrue(details.contains("LKR amount" to "LKR 310,155.00"))
        assertTrue(details.contains("Received" to "31 Jan 2026"))
        assertEquals("1000.50", op.income!!.originalAmountStr)
        assertEquals("310", op.income!!.conversionRateStr)
        assertEquals("same-operation", op.operationId)
    }
    @Test fun onlyAuthoritativeRejectedStateUsesRejectedWording() {
        val op = FinancialOperation("fixture", "INCOME", "DELETE", "row", expectedUpdatedAt = Timestamp(1,0))
        assertTrue(financialChangeStatus(FinancialAttempt(op, "REJECTED")).contains("Rejected"))
        assertTrue(FinancialAttempt(op, "REJECTED").rejected)
        assertFalse(FinancialAttempt(op, "PENDING").rejected)
        assertTrue(financialChangeStatus(FinancialAttempt(op, "PENDING")).startsWith("Saved on this device"))
        assertEquals(listOf("Record reference" to "row"), financialChangeDetails(op, CatalogueSnapshot()))
    }
}
