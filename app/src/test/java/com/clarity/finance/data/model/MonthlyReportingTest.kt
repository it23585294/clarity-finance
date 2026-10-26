package com.clarity.finance.data.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class MonthlyReportingTest {
    private val period = ReportingMonth(YearMonth.of(2026, 1))
    private fun expense(id: String, amount: Long, category: String = "housing", type: String = "committed") =
        Expense(id, amount, categoryId = category, paymentChannelId = "cash", spendingType = type, expenseDate = "2026-01-15")
    private fun income(amount: Long) = Income("i", "salary", "2026-01-15", "LKR", "1000", amount, "native_lkr", "1", "")
    private fun project(id: String, currency: String = "LKR", cancelled: Boolean = false) =
        ExpectedPayment(id, "project", "Test", "freelance", currency, "2027-01-01", 8000000,
            allocatedAmountMinor = 5000000, directReceivedAmountMinor = 2000000, milestoneReceivedAmountMinor = 2000000, isCancelled = cancelled)

    @Test fun `AT14 received 1000 minus expenses 1500 gives negative 500 independently of crypto and pending`() {
        val received = MonthlyReporting.income(listOf(income(100000)), period)
        val spending = MonthlyReporting.expenses(listOf(expense("e", 150000)), period)
        assertEquals("-500.00", MonthlyReporting.money(Math.subtractExact(received, spending.totalMinor)))
        assertEquals(3500000L, MonthlyReporting.crypto(listOf(CryptoResult("g", "gain", 6000000, "2026-01-01"),
            CryptoResult("l", "loss", 2500000, "2026-01-31")), period))
        assertEquals(mapOf("LKR" to 4000000L), MonthlyReporting.pending(listOf(project("p"))))
        assertEquals(100000L, received)
        assertEquals(150000L, spending.totalMinor)
    }

    @Test fun `AT06 sorted category sums equal expenses with exact 80 20 split`() {
        val report = MonthlyReporting.expenses(listOf(expense("rent", 4000000), expense("meal", 1000000, "food_dining", "discretionary")), period)
        assertEquals(5000000L, report.totalMinor)
        assertEquals(report.totalMinor, report.categories.sumOf { it.amountMinor })
        assertEquals(listOf("Housing", "Food & Dining"), report.categories.map { it.label })
        assertEquals("80.0", report.committedPercent)
        assertEquals("20.0", report.discretionaryPercent)
        assertEquals(0.8f, report.categories.first().barFraction)
    }

    @Test fun `percentage complement is consistent for repeating fractions and empty spending has no percentages`() {
        val report = MonthlyReporting.expenses(listOf(expense("c", 1), expense("d", 2, type = "discretionary")), period)
        assertEquals("33.3", report.committedPercent)
        assertEquals("66.7", report.discretionaryPercent)
        assertEquals(BigDecimal("100.0"), BigDecimal(report.committedPercent).add(BigDecimal(report.discretionaryPercent)))
        val empty = MonthlyReporting.expenses(emptyList(), period)
        assertEquals(0L, empty.totalMinor)
        assertNull(empty.committedPercent)
        assertNull(empty.discretionaryPercent)
        assertTrue(empty.categories.isEmpty())
    }

    @Test fun `pending totals ignore cancelled agreements and allocation without mixing currencies`() {
        assertEquals(mapOf("LKR" to 4000000L, "USD" to 4000000L), MonthlyReporting.pending(listOf(
            project("lkr"), project("usd", "USD"), project("cancelled", cancelled = true))))
        // Allocation is planned capacity, not another collectible balance.
        assertEquals(MonthlyReporting.pending(listOf(project("p"))),
            MonthlyReporting.pending(listOf(project("p").copy(allocatedAmountMinor = 0))))
    }

    @Test fun `Colombo month and year windows include transaction dates and exclude the next month`() {
        val before = CryptoReporting.today(Clock.fixed(Instant.parse("2025-12-31T18:29:59Z"), ZoneOffset.UTC))
        val after = CryptoReporting.today(Clock.fixed(Instant.parse("2025-12-31T18:30:00Z"), ZoneOffset.UTC))
        assertEquals("2025-12-31", before.toString())
        assertEquals("2026-01-01", after.toString())
        val december = ReportingMonth(YearMonth.from(before))
        assertTrue(december.contains("2025-12-31"))
        assertFalse(december.contains("2026-01-01"))
        assertEquals("2026-01-01", december.endExclusive)
        val leap = ReportingMonth(YearMonth.of(2024, 2))
        assertTrue(leap.contains("2024-02-29"))
        assertFalse(leap.contains("2024-03-01"))
    }

    @Test fun `pending rejected and malformed data cannot enter confirmed totals`() {
        assertThrows(IllegalArgumentException::class.java) { MonthlyReporting.income(listOf(income(1).copy(isPending = true)), period) }
        assertThrows(IllegalArgumentException::class.java) { MonthlyReporting.expenses(listOf(expense("e", 1).copy(syncError = "Denied")), period) }
        assertThrows(IllegalArgumentException::class.java) { MonthlyReporting.crypto(listOf(CryptoResult("g", "gain", 1, "2026-01-01", sync = CryptoSync.FAILED)), period) }
        assertThrows(IllegalStateException::class.java) { MonthlyReporting.expenses(listOf(expense("e", 1, type = "unknown")), period) }
        assertThrows(IllegalArgumentException::class.java) { MonthlyReporting.expenses(listOf(expense("e", 1).copy(expenseDate = "2025-12-31")), period) }
    }

    @Test fun `checked totals never wrap and display remains exact at integer limits`() {
        assertEquals("92233720368547758.07", MonthlyReporting.money(Long.MAX_VALUE))
        assertThrows(ArithmeticException::class.java) { MonthlyReporting.expenses(listOf(expense("a", Long.MAX_VALUE), expense("b", 1)), period) }
        assertThrows(ArithmeticException::class.java) { MonthlyReporting.income(listOf(income(Long.MAX_VALUE), income(1)), period) }
        assertThrows(ArithmeticException::class.java) { MonthlyReporting.crypto(listOf(CryptoResult("a", "loss", Long.MAX_VALUE, "2026-01-01"),
            CryptoResult("b", "loss", 2, "2026-01-01")), period) }
        assertThrows(ArithmeticException::class.java) { MonthlyReporting.pending(listOf(project("p").copy(agreedAmountMinor = Long.MAX_VALUE,
            directReceivedAmountMinor = 0, milestoneReceivedAmountMinor = 0), project("q"))) }
    }
}
