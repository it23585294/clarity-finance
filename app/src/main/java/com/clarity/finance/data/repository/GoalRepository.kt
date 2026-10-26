package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import kotlinx.coroutines.flow.Flow

data class GoalOperation(val operationId: String, val kind: String, val expectedVersion: Long? = null,
    val goal: GoalConfig? = null, val entry: SavingsEntry? = null, val previousDeadline: String? = null, val goalId: String = "active") {
    val type: String get() = "GOAL_$kind"
    val recordId: String get() = entry?.id ?: goalId
    val nextVersion: Long get() = expectedVersion?.let { Math.addExact(it, 1L) } ?: 0L
    fun payload(): Map<String, Any> = goal?.values() ?: entry!!.values()
    fun validate(temporal: Boolean = true, today: java.time.LocalDate = SavingsMath.today()) {
        require(goalId == "active" && operationId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid goal operation identity." }
        require(kind in setOf("CREATE", "EDIT", "ENTRY_CREATE", "ENTRY_UPDATE", "ENTRY_DELETE"))
        require((kind == "CREATE" && expectedVersion == null) || (kind != "CREATE" && expectedVersion != null && expectedVersion >= 0))
        require(expectedVersion != Long.MAX_VALUE) { "Goal version has reached the supported integer limit; it cannot be incremented safely." }
        if (kind in setOf("CREATE", "EDIT")) {
            require(goal != null && entry == null)
            SavingsMath.validate(goal, if (temporal) today else java.time.LocalDate.MAX,
                if (!temporal) goal.deadline else if (kind == "EDIT") previousDeadline else null)
        } else {
            require(goal == null && entry != null && entry.deleted == (kind == "ENTRY_DELETE"))
            SavingsMath.validate(entry, if (temporal) today else java.time.LocalDate.MAX)
        }
    }
}
data class GoalAttempt(val operation: GoalOperation, val status: String, val error: String? = null) {
    // Legacy FAILED records did not establish logical rejection and must be reconciled again.
    val isRejected get() = status in setOf("REJECTED", "EXPIRED")
    val canReviseDeadline get() = status == "EXPIRED" && operation.goal != null
    val isUncertain get() = status in setOf("UNCERTAIN", "FAILED", "CONFLICT")
}
data class GoalSnapshot(val goal: SavingsGoal? = null, val entries: List<SavingsEntry>? = null,
    val known: Boolean = false, val fromCache: Boolean = true, val attempts: List<GoalAttempt> = emptyList(),
    val error: String? = null, val recoveryError: String? = null, val acknowledgedOperationId: String? = null)

interface GoalRepository {
    /** Read-only, including on the dashboard. Does not retry saved operations. */
    fun observe(uid: String): Flow<GoalSnapshot>
    suspend fun submit(uid: String, operation: GoalOperation)
    suspend fun retry(uid: String, operation: GoalOperation)
    suspend fun dismissRejected(uid: String, operation: GoalOperation)
    suspend fun reviseExpired(uid: String, original: GoalOperation, replacement: GoalOperation)
    suspend fun synchronize(uid: String)
}

interface GoalRemote {
    fun observe(uid: String): Flow<GoalSnapshot>
    suspend fun apply(uid: String, operation: GoalOperation): GoalRemoteOutcome
}

sealed interface GoalRemoteOutcome {
    /** The atomic write was acknowledged, or its exact immutable receipt was reconciled. */
    data object Confirmed : GoalRemoteOutcome
    /** Successful server verification of a permanent contradiction, or acknowledgement of an immutable expiry fence. */
    data class Rejected(val reason: String, val deadlineExpired: Boolean = false) : GoalRemoteOutcome
    data class Uncertain(val reason: String) : GoalRemoteOutcome
}
