package com.clarity.finance.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CryptoReportingTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val month = YearMonth.of(2026, 10)
    private fun result(id: String, direction: String, amount: Long, date: String = "2026-10-25") = CryptoResult(id, direction, amount, date)

    @Test fun `AT03 signed gains and losses yield exact plus 35000`() {
        val records = listOf(result("g", "gain", 6000000), result("l", "loss", 2500000))
        assertEquals(3500000L, CryptoReporting.monthNet(records, month))
        assertEquals("+35000.00", CryptoReporting.money(CryptoReporting.net(records, LocalDate.of(2026, 1, 1), today)))
    }

    @Test fun `net loss zero and extreme displays use exact decimal arithmetic`() {
        assertEquals("-35000.00", CryptoReporting.money(CryptoReporting.monthNet(listOf(result("l", "loss", 3500000)), month)))
        assertEquals("0.00", CryptoReporting.money(CryptoReporting.monthNet(emptyList(), month)))
        assertEquals("+92233720368547758.07", CryptoReporting.money(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, CryptoReporting.amount("92233720368547758.07"))
    }

    @Test fun `blank zero signs exponent excess decimals and overflow are rejected`() {
        listOf("", " ", "0", "0.00", "-1", "+1", "1e3", "1E3", "1.001", ".01", "1.", "NaN", "92233720368547758.08").forEach {
            assertThrows("Accepted $it", IllegalArgumentException::class.java) { CryptoReporting.amount(it) }
        }
        assertEquals(1L, CryptoReporting.amount("0.01"))
    }

    @Test fun `real calendar backdates and notes are validated`() {
        CryptoReporting.validate("loss", 1, "2024-02-29", "", today)
        CryptoReporting.validate("gain", 1, today.toString(), "x".repeat(500), today)
        listOf("2026-02-29", "1900-02-29", "2026-04-31", "2026-13-01", "0000-01-01", "2026-1-01", "2026-10-27").forEach {
            assertThrows(IllegalArgumentException::class.java) { CryptoReporting.validate("gain", 1, it, "", today) }
        }
        assertThrows(IllegalArgumentException::class.java) { CryptoReporting.validate("gain", 1, "2026-01-01", "x".repeat(501), today) }
        assertThrows(IllegalArgumentException::class.java) { CryptoReporting.validate("other", 1, "2026-01-01", "", today) }
    }

    @Test fun `Colombo crosses month and year at 1830 UTC independently of device timezone`() {
        fun day(instant: String) = CryptoReporting.today(Clock.fixed(Instant.parse(instant), ZoneOffset.ofHours(-8)))
        assertEquals(LocalDate.of(2025, 12, 31), day("2025-12-31T18:29:59Z"))
        assertEquals(LocalDate.of(2026, 1, 1), day("2025-12-31T18:30:00Z"))
        assertEquals(LocalDate.of(2026, 2, 1), day("2026-01-31T18:30:00Z"))
        assertEquals(today, CryptoReporting.cutoff(2026, today))
        assertEquals(LocalDate.of(2025, 12, 31), CryptoReporting.cutoff(2025, today))
        assertThrows(IllegalArgumentException::class.java) { CryptoReporting.cutoff(2027, today) }
    }

    @Test fun `backdated edit and deletion move or remove exactly one contribution`() {
        val a = result("a", "gain", 6000000)
        val b = result("b", "loss", 2500000)
        val edited = a.copy(resultDate = "2025-12-31")
        assertEquals(-2500000L, CryptoReporting.monthNet(listOf(edited, b), month))
        assertEquals(6000000L, CryptoReporting.monthNet(listOf(edited, b), YearMonth.of(2025, 12)))
        assertEquals(-2500000L, CryptoReporting.net(listOf(edited, b), LocalDate.of(2026, 1, 1), today))
        val deletedTwice = listOf(edited, b).filterNot { it.id == "b" }.filterNot { it.id == "b" }
        assertEquals(0L, CryptoReporting.monthNet(deletedTwice, month))
    }

    @Test fun `pending and failed proposals never count as confirmed results`() {
        val a = result("a", "gain", 100)
        val records = listOf(a, a.copy(id = "b", sync = CryptoSync.PENDING), a.copy(id = "c", sync = CryptoSync.FAILED))
        assertEquals(100L, CryptoReporting.monthNet(records, month))
        assertEquals(100L, CryptoReporting.monthNet(listOf(a.copy(sync = CryptoSync.CACHED)), month))
        assertThrows(ArithmeticException::class.java) {
            CryptoReporting.monthNet(listOf(a.copy(amountMinor = Long.MAX_VALUE), a), month)
        }
    }
}
