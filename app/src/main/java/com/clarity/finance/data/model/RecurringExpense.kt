package com.clarity.finance.data.model

import com.google.firebase.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

data class RecurringConfig(val name: String, val categoryId: String, val spendingType: String,
    val paymentChannelId: String, val suggestedMinor: Long, val firstDueDate: String, val anchorDay: Int,
    val cadence: String = "monthly") {
    fun values(): Map<String, Any> = mapOf("name" to name, "categoryId" to categoryId, "spendingType" to spendingType,
        "paymentChannelId" to paymentChannelId, "suggestedMinor" to suggestedMinor, "firstDueDate" to firstDueDate,
        "anchorDay" to anchorDay.toLong(), "cadence" to cadence)
}
data class RecurringTemplate(val id: String, val config: RecurringConfig, val version: Long, val isCancelled: Boolean = false,
    val lastOperationId: String = "", val createdAt: Timestamp? = null, val updatedAt: Timestamp? = null)
data class ActualRecurringExpense(val amountMinor: Long, val expenseDate: String, val categoryId: String,
    val spendingType: String, val paymentChannelId: String, val note: String = "", val currency: String = "LKR") {
    fun values(): Map<String, Any> = mapOf("amountMinor" to amountMinor, "expenseDate" to expenseDate, "categoryId" to categoryId,
        "spendingType" to spendingType, "paymentChannelId" to paymentChannelId, "note" to note, "currency" to currency)
}
data class RecurringConfirmation(val templateId: String, val month: String, val dueDate: String, val templateVersion: Long,
    val templateName: String, val actual: ActualRecurringExpense) {
    val id get() = Recurrence.occurrenceId(templateId, month)
    val expenseId get() = "rec_$id"
    fun values(): Map<String, Any> = mapOf("templateId" to templateId, "month" to month, "dueDate" to dueDate,
        "templateVersion" to templateVersion, "templateName" to templateName, "expenseId" to expenseId, "actual" to actual.values())
}
data class ConfirmedOccurrence(val confirmation: RecurringConfirmation, val operationId: String, val createdAt: Timestamp? = null)
data class RecurringSlot(val id: String, val month: String, val dueDate: String, val overdue: Boolean,
    val confirmed: ConfirmedOccurrence? = null)

object Recurrence {
    val channels = mapOf("cash" to "Cash", "primary_bank_card" to "Primary bank/card", "secondary_bank_card" to "Secondary bank/card", "other" to "Other")
    fun today(clock: Clock = Clock.systemUTC()): LocalDate = SavingsMath.today(clock)
    fun id(value: String) { require(value.matches(Regex("[A-Za-z0-9-]{1,128}"))) { "Invalid recurring identity." } }
    fun month(value: String): YearMonth {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}"))) { "Use a real reporting month." }
        return YearMonth.parse(value).also { require(it.year in 1..9999) }
    }
    fun occurrenceId(templateId: String, month: String): String { id(templateId); month(month); return "${templateId}_$month" }
    fun validate(config: RecurringConfig) {
        require(config.name.isNotBlank() && config.name.length <= 100) { "Enter a name of at most 100 characters." }
        require(config.suggestedMinor > 0) { "Suggested amount must be positive." }
        require(config.categoryId in ExpenseCategories.labels && config.paymentChannelId in channels && config.spendingType in setOf("committed", "discretionary")) { "Choose valid expense classifications." }
        SavingsMath.date(config.firstDueDate)
        require(config.cadence == "monthly" && config.anchorDay in 1..31) { "Monthly day must be between 1 and 31." }
    }
    fun validate(actual: ActualRecurringExpense, today: LocalDate = LocalDate.MAX) {
        require(actual.amountMinor > 0 && actual.currency == "LKR") { "Enter a positive LKR amount." }
        require(actual.categoryId in ExpenseCategories.labels && actual.paymentChannelId in channels && actual.spendingType in setOf("committed", "discretionary")) { "Choose valid expense classifications." }
        require(SavingsMath.date(actual.expenseDate) <= today) { "Actual payment date cannot be in the future." }
        require(actual.note.length <= 500) { "Note must have at most 500 characters." }
    }
    fun due(config: RecurringConfig, month: YearMonth): LocalDate {
        val first = SavingsMath.date(config.firstDueDate)
        require(month >= YearMonth.from(first))
        return if (month == YearMonth.from(first)) first else month.atDay(minOf(config.anchorDay, month.lengthOfMonth()))
    }
    /** Only twelve months are generated. The ViewModel exposes explicit paging back to the first month. */
    fun slots(template: RecurringTemplate, start: YearMonth, confirmations: List<ConfirmedOccurrence>, today: LocalDate): List<RecurringSlot> {
        require(confirmations.all { it.confirmation.templateId == template.id && Recurrence.month(it.confirmation.month) >= start &&
            Recurrence.month(it.confirmation.month) < start.plusMonths(12) }) { "Confirmation history does not match this template/window." }
        val byMonth = confirmations.associateBy { it.confirmation.month }
        require(byMonth.size == confirmations.size) { "Duplicate occurrence history." }
        return (0L..11L).map { start.plusMonths(it) }.filter { it <= YearMonth.from(today) && it >= YearMonth.from(SavingsMath.date(template.config.firstDueDate)) }
            .mapNotNull { month ->
                val confirmed = byMonth[month.toString()]
                if (confirmed != null) RecurringSlot(confirmed.confirmation.id, month.toString(), confirmed.confirmation.dueDate, false, confirmed)
                else if (template.isCancelled) null
                else due(template.config, month).takeIf { it <= today }?.let { RecurringSlot(occurrenceId(template.id, month.toString()), month.toString(), it.toString(), it < today) }
            }
    }
}
