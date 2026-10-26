package com.clarity.finance.data.model

import com.google.firebase.Timestamp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class GoalConfig(val name: String, val targetMinor: Long, val openingMinor: Long, val startDate: String, val deadline: String) {
    fun values(): Map<String, Any> = mapOf("name" to name, "targetMinor" to targetMinor, "openingMinor" to openingMinor,
        "startDate" to startDate, "deadline" to deadline)
}
data class SavingsGoal(val config: GoalConfig, val savedMinor: Long, val version: Long, val lastOperationId: String,
    val lastEntryId: String = "", val createdAt: Timestamp? = null, val updatedAt: Timestamp? = null)
data class SavingsEntry(val id: String, val kind: String, val amountMinor: Long, val entryDate: String, val note: String = "",
    val deleted: Boolean = false, val version: Long = 0, val lastOperationId: String = "",
    val createdAt: Timestamp? = null, val updatedAt: Timestamp? = null) {
    fun values(): Map<String, Any> = mapOf("kind" to kind, "amountMinor" to amountMinor, "entryDate" to entryDate, "note" to note, "deleted" to deleted)
    val effect: Long get() = if (deleted) 0L else when (kind) {
        "contribution" -> amountMinor
        "withdrawal" -> Math.negateExact(amountMinor)
        else -> error("Invalid allocation kind.")
    }
}

/** Finite, inspectable schedule without storing an expanding array or February drift. */
class GoalSchedule(start: LocalDate, private val deadline: LocalDate) {
    private val anchor = start
    private val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(deadline)).toInt().let {
        if (it > 0 && start.plusMonths(it.toLong()) > deadline) it - 1 else it.coerceAtLeast(0)
    }
    val count: Int = months + if (months > 0 && start.plusMonths(months.toLong()) == deadline) 0 else 1
    init { require(deadline > start) { "Deadline must be after the goal start." } }
    fun dateAt(index: Int): LocalDate {
        require(index in 0 until count)
        return if (index < months) anchor.plusMonths(index.toLong() + 1) else deadline
    }
    fun firstOnOrAfter(today: LocalDate): Int {
        var low = 0; var high = count
        while (low < high) {
            val mid = (low + high) / 2
            if (dateAt(mid) < today) low = mid + 1 else high = mid
        }
        return low
    }
}
data class GoalProgress(val savedMinor: Long, val remainingMinor: Long, val overfundedMinor: Long,
    val percent: String, val barFraction: Float, val opportunities: Int, val nextDate: String?,
    val requiredMinor: Long?, val reached: Boolean, val overdue: Boolean, val schedule: GoalSchedule)

object SavingsMath {
    fun today(clock: Clock = Clock.systemUTC()) = CryptoReporting.today(clock)
    fun money(minor: Long) = BigDecimal.valueOf(minor, 2).toPlainString()
    fun amount(text: String, zeroAllowed: Boolean = false): Long {
        if (zeroAllowed && text.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")) && BigDecimal(text).signum() == 0) return 0
        return CryptoReporting.amount(text)
    }
    fun date(text: String): LocalDate {
        require(text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Use YYYY-MM-DD." }
        return try { LocalDate.parse(text).also { require(it.year in 1..9999) { "Enter a real calendar date." } } }
        catch (_: java.time.DateTimeException) { throw IllegalArgumentException("Enter a real calendar date.") }
    }
    fun validate(config: GoalConfig, today: LocalDate, previousDeadline: String? = null) {
        require(config.name.isNotBlank() && config.name.length <= 100) { "Goal name must be 1–100 characters." }
        require(config.targetMinor > 0 && config.openingMinor >= 0) { "Target must be positive and opening allocation nonnegative." }
        val start = date(config.startDate); val deadline = date(config.deadline)
        require(start <= today && deadline > start) { "Check the start date and deadline." }
        require(deadline > today || config.deadline == previousDeadline) { "Choose a future deadline." }
    }
    fun validate(entry: SavingsEntry, today: LocalDate) {
        require(entry.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid entry ID." }
        require(entry.kind in setOf("contribution", "withdrawal")) { "Select contribution or withdrawal." }
        require(entry.amountMinor > 0) { "Amount must be positive." }
        require(date(entry.entryDate) <= today) { "Allocation date cannot be in the future." }
        require(entry.note.length <= 500) { "Note must be 500 characters or fewer." }
    }
    fun balance(saved: Long, before: SavingsEntry?, after: SavingsEntry): Long = checked(
        BigInteger.valueOf(saved).subtract(BigInteger.valueOf(before?.effect ?: 0)).add(BigInteger.valueOf(after.effect)))
    fun openingBalance(goal: SavingsGoal, opening: Long): Long = checked(BigInteger.valueOf(goal.savedMinor)
        .subtract(BigInteger.valueOf(goal.config.openingMinor)).add(BigInteger.valueOf(opening)))
    fun ledgerBalance(opening: Long, entries: List<SavingsEntry>): Long = checked(entries.fold(BigInteger.valueOf(opening)) { sum, entry ->
        sum.add(BigInteger.valueOf(entry.effect))
    })
    private fun checked(value: BigInteger): Long {
        require(value.signum() >= 0) { "This change would make recorded savings negative." }
        require(value <= BigInteger.valueOf(Long.MAX_VALUE)) { "Savings exceed the supported integer range." }
        return value.toLong() // Bounds checked above; BigInteger.longValueExact requires Android API 31.
    }
    fun progress(goal: SavingsGoal, today: LocalDate): GoalProgress {
        require(goal.savedMinor >= 0 && goal.config.targetMinor > 0)
        val remaining = if (goal.savedMinor >= goal.config.targetMinor) 0L else goal.config.targetMinor - goal.savedMinor
        val over = if (goal.savedMinor > goal.config.targetMinor) goal.savedMinor - goal.config.targetMinor else 0L
        val percentage = BigDecimal.valueOf(goal.savedMinor).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(goal.config.targetMinor), 2, RoundingMode.HALF_UP)
        val schedule = GoalSchedule(date(goal.config.startDate), date(goal.config.deadline))
        val first = schedule.firstOnOrAfter(today); val n = schedule.count - first
        val required = if (remaining == 0L) 0L else if (n == 0) null else BigDecimal.valueOf(remaining)
            .divide(BigDecimal.valueOf(n.toLong()), 0, RoundingMode.CEILING).longValueExact()
        return GoalProgress(goal.savedMinor, remaining, over, percentage.toPlainString(),
            percentage.divide(BigDecimal(100)).coerceIn(BigDecimal.ZERO, BigDecimal.ONE).toFloat(), n,
            if (n == 0) null else schedule.dateAt(first).toString(), required, remaining == 0L, n == 0 && remaining > 0, schedule)
    }
}
