package com.clarity.finance.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurringScheduleTest {
    private val config = RecurringConfig("Rent", "housing", "committed", "cash", 3400000, "2024-01-31", 31)
    private fun bad(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun `original anchor handles leap February without cumulative drift`() {
        assertEquals(listOf("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30"), (1..4).map { Recurrence.due(config, YearMonth.of(2024, it)).toString() })
        assertEquals("2025-02-28", Recurrence.due(config, YearMonth.of(2025, 2)).toString())
        assertEquals("2025-03-31", Recurrence.due(config, YearMonth.of(2025, 3)).toString())
    }
    @Test fun `monthly identity survives due day and suggested payload edits while confirmed history keeps original date`() {
        val month = YearMonth.of(2024, 2)
        val original = RecurringConfirmation("rent", month.toString(), "2024-02-29", 0, "Rent",
            ActualRecurringExpense(3400000, "2024-02-28", "housing", "committed", "cash"))
        val edited = RecurringTemplate("rent", config.copy(anchorDay = 5, suggestedMinor = 3500000), 1)
        assertEquals(original.id, Recurrence.occurrenceId(edited.id, month.toString()))
        val slots = Recurrence.slots(edited, month, listOf(ConfirmedOccurrence(original, "confirmed")), LocalDate.parse("2024-04-30"))
        assertEquals("2024-02-29", slots.first().dueDate)
        assertEquals("2024-03-05", slots[1].dueDate)
        assertEquals(3400000L, slots.first().confirmed!!.confirmation.actual.amountMinor)
        assertEquals("2024-01-31", Recurrence.due(edited.config, YearMonth.of(2024, 1)).toString())
    }
    @Test fun `missed occurrences begin at oldest month and explicit windows generate at most twelve`() {
        val t = RecurringTemplate("rent", config, 0)
        val first = Recurrence.slots(t, YearMonth.of(2024, 1), emptyList(), LocalDate.parse("2026-03-31"))
        val next = Recurrence.slots(t, YearMonth.of(2025, 1), emptyList(), LocalDate.parse("2026-03-31"))
        assertEquals(12, first.size); assertEquals("2024-01", first.first().month); assertTrue(first.all { it.overdue })
        assertEquals("2025-01", next.first().month); assertTrue(first.map { it.id }.intersect(next.map { it.id }.toSet()).isEmpty())
        assertEquals(3, Recurrence.slots(t, YearMonth.of(2026, 1), emptyList(), LocalDate.parse("2026-03-31")).size)
    }
    @Test fun `cancellation stops all unconfirmed including overdue without removing confirmations`() {
        val original = RecurringConfirmation("rent", "2024-02", "2024-02-29", 0, "Rent", ActualRecurringExpense(3400000, "2024-02-28", "housing", "committed", "cash"))
        val cancelled = RecurringTemplate("rent", config, 1, true)
        val rows = Recurrence.slots(cancelled, YearMonth.of(2024, 1), listOf(ConfirmedOccurrence(original, "accepted")), LocalDate.parse("2026-03-31"))
        assertEquals(1, rows.size); assertNotNull(rows.single().confirmed)
    }
    @Test fun `Colombo midnight includes newly due today and never offers future actual dates`() {
        val before = Clock.fixed(Instant.parse("2026-03-30T18:29:59Z"), ZoneOffset.UTC)
        val after = Clock.fixed(Instant.parse("2026-03-30T18:30:00Z"), ZoneOffset.UTC)
        val t = RecurringTemplate("rent", config, 0)
        assertEquals(2, Recurrence.slots(t, YearMonth.of(2026, 1), emptyList(), Recurrence.today(before)).size)
        assertEquals(3, Recurrence.slots(t, YearMonth.of(2026, 1), emptyList(), Recurrence.today(after)).size)
        assertFalse(Recurrence.slots(t, YearMonth.of(2026, 1), emptyList(), Recurrence.today(after)).last().overdue)
        bad { Recurrence.validate(ActualRecurringExpense(1, "2026-04-01", "housing", "committed", "cash"), Recurrence.today(after)) }
    }
    @Test fun `exact amount schema and real calendar validation reject invalid proposals`() {
        listOf("", "0", "-1", "1e3", "1.001", "92233720368547758.08").forEach { bad { SavingsMath.amount(it) } }
        assertEquals(Long.MAX_VALUE, SavingsMath.amount("92233720368547758.07"))
        listOf(config.copy(name = " "), config.copy(suggestedMinor = 0), config.copy(anchorDay = 32), config.copy(cadence = "weekly"),
            config.copy(firstDueDate = "2025-02-29"), config.copy(categoryId = "fake")).forEach { bad { Recurrence.validate(it) } }
        bad { Recurrence.validate(ActualRecurringExpense(1, "1900-02-29", "housing", "committed", "cash")) }
        bad { Recurrence.validate(ActualRecurringExpense(1, "2024-02-29", "housing", "committed", "cash", "x".repeat(501))) }
    }
    @Test fun `AT09 only actual rent and edited utilities affect ordinary categories and surplus exactly once`() {
        val period = ReportingMonth(YearMonth.of(2026, 1))
        val templates = listOf(config, config.copy(name = "Utilities", categoryId = "utilities", suggestedMinor = 500000))
        assertEquals(2, templates.size)
        assertEquals(0L, MonthlyReporting.expenses(emptyList(), period).totalMinor)
        val rent = Expense("rec_rent_2026-01", 3400000, categoryId = "housing", paymentChannelId = "cash", spendingType = "committed", expenseDate = "2026-01-31", recurringOccurrenceId = "rent_2026-01")
        val utilities = rent.copy(id = "rec_utilities_2026-01", amountMinor = 625050, categoryId = "utilities", recurringOccurrenceId = "utilities_2026-01")
        assertEquals(3400000L, MonthlyReporting.expenses(listOf(rent), period).totalMinor)
        val spending = MonthlyReporting.expenses(listOf(rent, utilities), period)
        assertEquals(4025050L, spending.totalMinor); assertEquals(4025050L, spending.categories.sumOf { it.amountMinor })
        assertEquals(-4025050L, Math.subtractExact(MonthlyReporting.income(emptyList(), period), spending.totalMinor))
        val prior = period.copy(month = YearMonth.of(2025, 12))
        val corrected = utilities.copy(amountMinor = 600000, expenseDate = "2025-12-31")
        assertEquals(600000L, MonthlyReporting.expenses(listOf(corrected), prior).totalMinor)
        assertEquals(3400000L, MonthlyReporting.expenses(listOf(rent), period).totalMinor)
        assertEquals(0L, MonthlyReporting.crypto(emptyList(), period)); assertTrue(MonthlyReporting.pending(emptyList()).isEmpty())
    }
}
