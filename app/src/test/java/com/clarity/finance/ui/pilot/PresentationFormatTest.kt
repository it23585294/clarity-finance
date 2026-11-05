package com.clarity.finance.ui.pilot

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationFormatTest {
    @Test fun serverSavedTimeUsesColomboAcrossMidnightAndDoesNotBorrowDeviceLocaleOrZone() {
        val locale = java.util.Locale.getDefault()
        val zone = java.util.TimeZone.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            val timestamp = com.google.firebase.Timestamp(java.time.Instant.parse("2026-11-04T18:30:00Z").epochSecond, 0)
            assertEquals("Server saved: 5 Nov 2026, 00:00 (Colombo)", PresentationFormat.serverSavedAt(timestamp))
            assertEquals("Server-saved time unavailable", PresentationFormat.serverSavedAt(null))
            assertEquals("Server-saved time pending", PresentationFormat.serverSavedAt(timestamp, pending = true))
        } finally { java.util.Locale.setDefault(locale); java.util.TimeZone.setDefault(zone) }
    }
    @Test fun groupingPreservesExactMinorUnitsAndSignedResults() {
        assertEquals("120,000.00", PresentationFormat.minor(12000000))
        assertEquals("+35,000.00", PresentationFormat.amount("+35000.00"))
        assertEquals("-0.01", PresentationFormat.minor(-1))
        assertEquals("92,233,720,368,547,758.07", PresentationFormat.minor(Long.MAX_VALUE))
        assertEquals("-92,233,720,368,547,758.08", PresentationFormat.minor(Long.MIN_VALUE))
        assertEquals("0.00", PresentationFormat.minor(0))
    }
    @Test fun readableDatesAndPeriodsDoNotDependOnDeviceLocale() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("November 2026", PresentationFormat.month("2026-11"))
            assertEquals("29 Feb 2024", PresentationFormat.date("2024-02-29"))
            assertEquals("unavailable", PresentationFormat.date("unavailable"))
            assertEquals("1,234.56", PresentationFormat.amount("1234.56"))
        } finally { java.util.Locale.setDefault(original) }
    }
}
