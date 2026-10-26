package com.clarity.finance.data.repository

import org.junit.Assert.*
import org.junit.Test

class ReportingProjectionTest {
    @Test fun `pending creation edit date move and deletion retain the entire last clean contribution`() {
        val view = ReportingProjection<String>()
        assertEquals(listOf("original"), view.accept(listOf("original"), false, false).records)
        listOf(listOf("original", "new"), listOf("edited"), emptyList()).forEach { proposal ->
            val pending = view.accept(proposal, true, true)
            assertEquals(listOf("original"), pending.records)
            assertTrue(pending.awaitingWrites)
        }
        // Rejection restores the original; confirmation alone changes the contribution.
        assertEquals(listOf("original"), view.accept(listOf("original"), false, false).records)
        assertEquals(listOf("edited"), view.accept(listOf("edited"), false, false).records)
        assertEquals(emptyList<String>(), view.accept(emptyList(), false, false).records)
    }

    @Test fun `uncached or initial pending history is unavailable rather than zero`() {
        val view = ReportingProjection<String>()
        assertNull(view.accept(listOf("proposal"), true, true).records)
        val cachedEmpty = view.accept(emptyList(), true, false)
        assertNull(cachedEmpty.records)
        assertNotNull(cachedEmpty.error)
        val serverEmpty = view.accept(emptyList(), false, false)
        assertEquals(emptyList<String>(), serverEmpty.records)
        assertNull(serverEmpty.error)
        assertEquals(emptyList<String>(), view.accept(emptyList(), true, false).records)
    }

    @Test fun `cached nonempty data is labelled and a new projection never inherits another owner baseline`() {
        val a = ReportingProjection<String>().accept(listOf("a"), true, false)
        assertTrue(a.fromCache)
        assertEquals(listOf("a"), a.records)
        assertNull(ReportingProjection<String>().accept(emptyList(), true, true).records)
    }
}
