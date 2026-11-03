package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import kotlinx.coroutines.flow.Flow

data class RecurringOperation(val operationId: String, val kind: String, val templateId: String,
    val expectedVersion: Long? = null, val config: RecurringConfig? = null, val confirmation: RecurringConfirmation? = null) {
    val type get() = "RECURRING_$kind"
    val recordId get() = confirmation?.id ?: templateId
    val version get() = if (kind == "CONFIRM") expectedVersion!! else expectedVersion?.let { Math.addExact(it, 1) } ?: 0
    fun payload(): Map<String, Any> = confirmation?.values() ?: (config!!.values() + ("isCancelled" to (kind == "CANCEL")))
    fun validate() {
        Recurrence.id(operationId); Recurrence.id(templateId)
        require(kind in setOf("CREATE", "EDIT", "CANCEL", "CONFIRM"))
        require(if (kind == "CREATE") expectedVersion == null else expectedVersion != null && expectedVersion >= 0 && (kind == "CONFIRM" || expectedVersion < Long.MAX_VALUE))
        if (kind == "CONFIRM") {
            require(config == null && confirmation != null && confirmation.templateId == templateId && confirmation.templateVersion == expectedVersion)
            Recurrence.month(confirmation.month); SavingsMath.date(confirmation.dueDate); Recurrence.validate(confirmation.actual)
            require(confirmation.dueDate.startsWith(confirmation.month + "-") && confirmation.templateName.isNotBlank() && confirmation.templateName.length <= 100)
        } else { require(config != null && confirmation == null); Recurrence.validate(config) }
    }
}
data class RecurringAttempt(val operation: RecurringOperation, val status: String, val error: String? = null) {
    val rejected get() = status == "REJECTED"
}
data class RecurringRecovery(val attempts: List<RecurringAttempt> = emptyList(), val error: String? = null,
    val acknowledgedIds: Set<String> = emptySet())
sealed interface RecurringOutcome {
    data object Confirmed : RecurringOutcome
    data class Rejected(val reason: String) : RecurringOutcome
    data class Uncertain(val reason: String) : RecurringOutcome
}
interface RecurringReader {
    /** Explicit page size + one sentinel. The UI labels additional templates instead of silently hiding them. */
    fun templates(uid: String, limit: Int): Flow<ReportingBatch<RecurringTemplate>>
    fun occurrences(uid: String, templateId: String, start: String, endExclusive: String): Flow<ReportingBatch<ConfirmedOccurrence>>
}
interface RecurringRemote : RecurringReader {
    suspend fun apply(uid: String, operation: RecurringOperation): RecurringOutcome
}
interface RecurringRepository : RecurringReader {
    fun recovery(uid: String): Flow<RecurringRecovery>
    suspend fun submit(uid: String, operation: RecurringOperation)
    suspend fun dismiss(uid: String, operation: RecurringOperation)
    suspend fun synchronize(uid: String)
}
