package com.clarity.finance.data.model

import com.clarity.finance.data.repository.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SavingsMathTest {
    private val config = GoalConfig("Laptop", 49000000, 1120000, "2026-01-31", "2027-01-31")
    private fun goal(saved: Long = config.openingMinor) = SavingsGoal(config, saved, 0, "create")
    private fun dates(start: String, end: String) = GoalSchedule(LocalDate.parse(start), LocalDate.parse(end)).let { s -> (0 until s.count).map(s::dateAt).map { it.toString() } }
    private fun invalid(block: () -> Unit) { try { block(); fail("Expected invalid input") } catch (_: IllegalArgumentException) {} }
    @Test fun `AT07 exact twelve opportunities and AT08 explicit allocations`() {
        val progress = SavingsMath.progress(goal(), LocalDate.parse("2026-01-31"))
        assertEquals(12, progress.opportunities); assertEquals(47880000L, progress.remainingMinor)
        assertEquals(3990000L, progress.requiredMinor); assertEquals("2.29", progress.percent)
        val contribution = SavingsEntry("c", "contribution", 1000000, "2026-01-31")
        val saved = SavingsMath.balance(goal().savedMinor, null, contribution)
        assertEquals(2120000L, saved)
        assertEquals(2000000L, SavingsMath.balance(saved, null, SavingsEntry("w", "withdrawal", 120000, "2026-01-31")))
        // Reporting has no allocation input. The same transactions retain exactly the same surplus.
        val period = ReportingMonth(YearMonth.of(2026, 1))
        val income = Income("i", "salary", "2026-01-31", "LKR", "10000", 1000000, "native_lkr", "1", "")
        assertEquals(1000000L, MonthlyReporting.income(listOf(income), period))
        assertEquals(0L, MonthlyReporting.expenses(emptyList(), period).totalMinor)
    }
    @Test fun `month end anchor does not drift including leap year and final deadline`() {
        assertEquals(listOf("2024-02-29", "2024-03-31", "2024-04-30", "2024-05-05"), dates("2024-01-31", "2024-05-05"))
        assertEquals(listOf("2025-02-28", "2025-03-31"), dates("2025-01-31", "2025-03-31"))
        assertEquals(listOf("2025-02-03"), dates("2025-01-31", "2025-02-03"))
    }
    @Test fun `today inclusion Colombo rollover ceil cent reached overdue and overfunded`() {
        val g = goal().copy(config = config.copy(targetMinor = 101, openingMinor = 0, startDate = "2026-01-31", deadline = "2026-03-31"), savedMinor = 0)
        val today = SavingsMath.today(Clock.fixed(Instant.parse("2026-02-27T18:30:00Z"), ZoneOffset.UTC))
        assertEquals(LocalDate.parse("2026-02-28"), today)
        val p = SavingsMath.progress(g, today); assertEquals(2, p.opportunities); assertEquals(51L, p.requiredMinor)
        assertEquals("2026-02-28", p.nextDate)
        assertTrue(SavingsMath.progress(g, LocalDate.parse("2026-04-01")).overdue)
        assertNull(SavingsMath.progress(g, LocalDate.parse("2026-04-01")).requiredMinor)
        val reached = SavingsMath.progress(g.copy(savedMinor = 120), LocalDate.parse("2026-04-01"))
        assertTrue(reached.reached); assertEquals(19L, reached.overfundedMinor); assertEquals("118.81", reached.percent)
        assertEquals(1f, reached.barFraction); assertEquals(0L, reached.requiredMinor)
    }
    @Test fun `exact input and real calendar validation rejects invalid values`() {
        listOf("", "0", "-1", "1e2", "1.001", "92233720368547758.08", "NaN").forEach { text -> invalid { SavingsMath.amount(text) } }
        assertEquals(Long.MAX_VALUE, SavingsMath.amount("92233720368547758.07"))
        assertEquals(0L, SavingsMath.amount("0.00", true))
        listOf("2025-02-29", "1900-02-29", "2026-04-31", "0000-01-01").forEach { invalid { SavingsMath.date(it) } }
        invalid { SavingsMath.validate(SavingsEntry("e", "contribution", 1, "2026-03-01"), LocalDate.parse("2026-02-28")) }
        invalid { SavingsMath.validate(config, LocalDate.parse("2027-02-01")) }
        SavingsMath.validate(config, LocalDate.parse("2027-02-01"), config.deadline)
    }
    @Test fun `negative corrections deletions opening changes and overflow are rejected`() {
        val c = SavingsEntry("c", "contribution", 100, "2026-01-01")
        invalid { SavingsMath.balance(20, null, c.copy(kind = "withdrawal")) }
        invalid { SavingsMath.balance(20, c, c.copy(deleted = true)) }
        invalid { SavingsMath.balance(20, c, c.copy(amountMinor = 79)) }
        invalid { SavingsMath.openingBalance(goal(20), 0) }
        invalid { SavingsMath.balance(Long.MAX_VALUE, null, c) }
        assertEquals(Long.MAX_VALUE, SavingsMath.balance(0, c.copy(kind = "withdrawal", amountMinor = Long.MAX_VALUE), c.copy(deleted = true)))
    }
    @Test fun `version conflicts tombstone identities and deletion payload are protected`() {
        val c = SavingsEntry("c", "contribution", 100, "2026-01-01")
        val op = GoalOperation("o", "ENTRY_DELETE", 0, entry = c.copy(deleted = true))
        try { checkGoalVersion(op.copy(expectedVersion = 1), goal()); fail() } catch (_: IllegalStateException) {}
        try { goalMutationBalance(op, goal(), c.copy(amountMinor = 101)); fail() } catch (_: IllegalStateException) {}
        try { goalMutationBalance(op.copy(kind = "ENTRY_CREATE", entry = c), goal(), c.copy(deleted = true)); fail() } catch (_: IllegalStateException) {}
        assertEquals(goal().savedMinor - 100, goalMutationBalance(op, goal(), c))
    }
}
