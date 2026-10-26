package com.clarity.finance.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Shared capture/reporting labels; stored IDs and historical classifications do not change. */
object ExpenseCategories {
    val labels = mapOf(
        "food_dining" to "Food & Dining", "transport" to "Transport", "housing" to "Housing",
        "utilities" to "Utilities", "shopping" to "Shopping", "health" to "Health",
        "entertainment" to "Entertainment", "education" to "Education", "other" to "Other"
    )
}

data class ReportingMonth(val month: YearMonth) {
    val start: String = month.atDay(1).toString()
    val endExclusive: String = month.plusMonths(1).atDay(1).toString()
    fun contains(date: String): Boolean = date >= start && date < endExclusive
}

data class CategorySpending(val id: String, val label: String, val amountMinor: Long, val barFraction: Float)
data class SpendingSummary(
    val totalMinor: Long, val committedMinor: Long, val discretionaryMinor: Long,
    val committedPercent: String?, val discretionaryPercent: String?,
    val categories: List<CategorySpending>
)

/** All totals are integer minor units. Float is used only for a visual bar fraction. */
object MonthlyReporting {
    fun money(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
    fun date(date: String) {
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && LocalDate.parse(date).year >= 1) {
            "Invalid transaction date."
        }
    }
    fun expenses(rows: List<Expense>, period: ReportingMonth): SpendingSummary {
        val amounts = mutableMapOf<String, Long>()
        var committed = 0L
        var discretionary = 0L
        rows.forEach {
            require(!it.isPending && it.syncError == null && !it.isConflict) { "Unconfirmed expense in reporting data." }
            date(it.expenseDate)
            require(period.contains(it.expenseDate) && it.currency == "LKR" && it.amountMinor > 0)
            require(it.categoryId in ExpenseCategories.labels) { "Unknown expense category." }
            when (it.spendingType) {
                "committed" -> committed = Math.addExact(committed, it.amountMinor)
                "discretionary" -> discretionary = Math.addExact(discretionary, it.amountMinor)
                else -> error("Unknown spending type.")
            }
            amounts[it.categoryId] = Math.addExact(amounts[it.categoryId] ?: 0L, it.amountMinor)
        }
        val total = Math.addExact(committed, discretionary)
        // Round committed once to one decimal; use its complement so the displayed pair sums to 100.0%.
        val committedShare = if (total == 0L) null else BigDecimal.valueOf(committed)
            .multiply(BigDecimal(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
        val categories = amounts.map { (id, amount) -> CategorySpending(id, ExpenseCategories.labels.getValue(id), amount,
            BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP).toFloat()) }
            .sortedWith(compareByDescending<CategorySpending> { it.amountMinor }.thenBy { it.label })
        return SpendingSummary(total, committed, discretionary, committedShare?.toPlainString(),
            committedShare?.let { BigDecimal("100.0").subtract(it).toPlainString() }, categories)
    }

    fun income(rows: List<Income>, period: ReportingMonth): Long = rows.fold(0L) { sum, row ->
        require(!row.isPending && row.syncError == null && !row.isConflict) { "Unconfirmed income in reporting data." }
        date(row.receiptDate)
        require(period.contains(row.receiptDate) && row.lkrAmountMinor > 0)
        require(row.sourceId in setOf("salary", "freelance", "adsense") && row.currency in setOf("LKR", "USD")) { "Invalid ordinary income source or currency." }
        Math.addExact(sum, row.lkrAmountMinor)
    }

    /** Validate every row before summing, so overflow cannot mask a later malformed record. */
    fun cryptoHistory(rows: List<CryptoResult>, period: ReportingMonth): List<CryptoResult> = rows.onEach { row ->
        require(row.sync in setOf(CryptoSync.CONFIRMED, CryptoSync.CACHED)) { "Unconfirmed crypto proposal in reporting data." }
        date(row.resultDate)
        require(period.contains(row.resultDate) && row.amountMinor > 0)
        require(row.direction in setOf("gain", "loss")) { "Invalid result direction" }
    }

    fun crypto(rows: List<CryptoResult>, period: ReportingMonth): Long = cryptoHistory(rows, period).fold(0L) { sum, row ->
        Math.addExact(sum, row.signedMinor)
    }

    /** Parent counters already include milestone receipts. Never fetch or sum milestone allocations here. */
    fun pending(rows: List<ExpectedPayment>): Map<String, Long> {
        val amounts = sortedMapOf<String, Long>()
        rows.forEach {
            require(!it.hasPendingWrites) { "Unconfirmed project in reporting data." }
            require(it.currency in setOf("LKR", "USD")) { "Unsupported project currency." }
            require(it.agreedAmountMinor > 0 && it.directReceivedAmountMinor >= 0 && it.milestoneReceivedAmountMinor >= 0)
            val outstanding = it.outstandingAmountMinor
            require(outstanding >= 0) { "Project receipts exceed its agreement." }
            if (!it.isCancelled && outstanding > 0)
                amounts[it.currency] = Math.addExact(amounts[it.currency] ?: 0L, outstanding)
        }
        return amounts
    }
}
