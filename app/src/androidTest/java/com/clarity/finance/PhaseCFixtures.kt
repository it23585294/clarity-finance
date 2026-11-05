package com.clarity.finance

import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*

/** Fictional state only. Production ViewModels/screens; these fakes do not prove server atomicity. */
class PhaseCFixtures {
    val owner = "phase-c-owner"
    val today = SavingsMath.today()
    val session = MutableStateFlow<User?>(User(owner, "Fictional review account"))
    val auth = object : AuthRepository {
        override val authState = session
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { session.value = null }
    }
    private var version = 10L
    private fun stamp() = Timestamp(++version, 0)
    val entries = listOf(
        SavingsEntry("contribution", "contribution", 1000000, today.minusDays(1).toString(), "Fictional contribution"),
        SavingsEntry("withdrawal", "withdrawal", 120000, today.toString(), "Fictional withdrawal"))
    val config = GoalConfig("Learning fund", 49000000, 1120000, today.toString(), today.plusYears(1).toString())
    val goalState = MutableStateFlow(GoalSnapshot(SavingsGoal(config, 2000000, 2, "seed"), entries, true, false))
    val goalOperations = linkedMapOf<String, GoalOperation>()
    var autoGoal = true
    var admission: CompletableDeferred<Unit>? = null
    val goals = object : GoalRepository {
        override fun observe(uid: String) = goalState.map { if (uid == owner) it else GoalSnapshot(known = true, fromCache = false, entries = emptyList()) }
        override suspend fun synchronize(uid: String) { awaitCancellation() }
        override suspend fun submit(uid: String, operation: GoalOperation) {
            check(uid == owner)
            goalOperations[operation.operationId]?.let { check(it == operation); return }
            goalOperations[operation.operationId] = operation
            admission?.await()
            goalState.value = goalState.value.copy(attempts = listOf(GoalAttempt(operation, "PENDING")))
            if (autoGoal) confirmGoal(operation)
        }
        override suspend fun retry(uid: String, operation: GoalOperation) { check(goalState.value.attempts.any { it.operation == operation }) }
        override suspend fun dismissRejected(uid: String, operation: GoalOperation) {
            check(goalState.value.attempts.single { it.operation == operation }.isRejected)
            goalState.value = goalState.value.copy(attempts = emptyList())
        }
        override suspend fun reviseExpired(uid: String, original: GoalOperation, replacement: GoalOperation) {
            check(goalState.value.attempts.single().let { it.operation == original && it.canReviseDeadline })
            submit(uid, replacement)
        }
    }
    fun confirmGoal(op: GoalOperation) {
        val old = goalState.value; val before = old.goal; val cfg = op.goal ?: before!!.config
        val saved = when (op.kind) {
            "CREATE" -> cfg.openingMinor
            "EDIT" -> SavingsMath.openingBalance(before!!, cfg.openingMinor)
            else -> SavingsMath.balance(before!!.savedMinor, old.entries.orEmpty().find { it.id == op.entry!!.id }, op.entry!!)
        }
        goalState.value = GoalSnapshot(SavingsGoal(cfg, saved, op.nextVersion, op.operationId, op.entry?.id ?: ""),
            if (op.entry == null) old.entries.orEmpty() else old.entries.orEmpty().filterNot { it.id == op.entry.id } + op.entry, true, false)
    }

    val first = today.minusMonths(1).withDayOfMonth(1)
    val rent = RecurringTemplate("rent", RecurringConfig("Rent", "housing", "committed", "cash", 3400000, first.toString(), 1), 0)
    val utilities = RecurringTemplate("utilities", rent.config.copy(name = "Utilities", categoryId = "utilities", suggestedMinor = 500000,
        firstDueDate = today.withDayOfMonth(1).toString()), 0)
    val original = RecurringConfirmation(rent.id, first.toString().take(7), first.toString(), 0, "Rent",
        ActualRecurringExpense(3400000, first.toString(), "housing", "committed", "cash", "Fictional original rent"))
    val templates = MutableStateFlow(ReportingBatch(listOf(rent, utilities), false))
    val confirmations = MutableStateFlow(listOf(ConfirmedOccurrence(original, "seed-confirmation", Timestamp(1, 0))))
    val expenseRows = MutableStateFlow(listOf(Expense(original.expenseId, 3400000, categoryId = "housing", spendingType = "committed",
        paymentChannelId = "cash", expenseDate = first.toString(), note = "Fictional original rent", updatedAt = Timestamp(1, 0), recurringOccurrenceId = original.id)))
    val recurringRecovery = MutableStateFlow(RecurringRecovery())
    val recurringOperations = linkedMapOf<String, RecurringOperation>()
    var autoRecurring = true
    val recurring = object : RecurringRepository {
        override fun templates(uid: String, limit: Int) = templates.map { if (uid == owner) it else ReportingBatch(emptyList(), false) }
        override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = confirmations.map { rows ->
            ReportingBatch(if (uid == owner) rows.filter { it.confirmation.templateId == templateId && it.confirmation.month >= start && it.confirmation.month < endExclusive } else emptyList(), false)
        }
        override fun recovery(uid: String) = recurringRecovery.map { if (uid == owner) it else RecurringRecovery() }
        override suspend fun synchronize(uid: String) { awaitCancellation() }
        override suspend fun submit(uid: String, operation: RecurringOperation) {
            check(uid == owner)
            recurringOperations[operation.operationId]?.let { check(it == operation); return }
            recurringOperations[operation.operationId] = operation
            admission?.await()
            recurringRecovery.value = recurringRecovery.value.copy(attempts = recurringRecovery.value.attempts + RecurringAttempt(operation, "PENDING"))
            if (autoRecurring) confirmRecurring(operation)
        }
        override suspend fun dismiss(uid: String, operation: RecurringOperation) {
            check(recurringRecovery.value.attempts.single { it.operation == operation }.rejected)
            recurringRecovery.value = recurringRecovery.value.copy(attempts = recurringRecovery.value.attempts.filterNot { it.operation == operation })
        }
    }
    fun confirmRecurring(op: RecurringOperation) {
        if (op.kind == "CONFIRM") {
            val c = op.confirmation!!
            confirmations.value.firstOrNull { it.confirmation.id == c.id }?.let { check(it.confirmation == c); return }
            confirmations.value += ConfirmedOccurrence(c, op.operationId, stamp())
            expenseRows.value += Expense(c.expenseId, c.actual.amountMinor, categoryId = c.actual.categoryId,
                paymentChannelId = c.actual.paymentChannelId, spendingType = c.actual.spendingType, expenseDate = c.actual.expenseDate,
                note = c.actual.note, updatedAt = stamp(), recurringOccurrenceId = c.id)
        } else {
            val t = RecurringTemplate(op.templateId, op.config!!, op.version, op.kind == "CANCEL", op.operationId)
            templates.value = templates.value.copy(records = templates.value.records.orEmpty().filterNot { it.id == t.id } + t)
        }
        recurringRecovery.value = recurringRecovery.value.copy(attempts = recurringRecovery.value.attempts.filterNot { it.operation == op }, acknowledgedIds = recurringRecovery.value.acknowledgedIds + op.operationId)
    }
    val expenses = object : ExpenseRepository {
        override fun getExpenses(userId: String) = expenseRows.map { if (userId == owner) it else emptyList() }
        override suspend fun getExpense(userId: String, expenseId: String) = expenseRows.value.find { userId == owner && it.id == expenseId }
            ?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Expense not found or already deleted."))
        override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> = error("No ordinary create")
        override fun retryFailedExpense(userId: String, expense: Expense) = error("No recovery retry")
        override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp): Result<Unit> {
            val old = expenseRows.value.single { it.id == expense.id && it.updatedAt == originalUpdatedAt }
            expenseRows.value = expenseRows.value.filterNot { it.id == old.id } + expense.copy(updatedAt = stamp(), recurringOccurrenceId = old.recurringOccurrenceId)
            return Result.success(Unit)
        }
        override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp): Result<Unit> {
            check(expenseRows.value.any { it.id == expenseId && it.updatedAt == originalUpdatedAt })
            expenseRows.value = expenseRows.value.filterNot { it.id == expenseId }; return Result.success(Unit)
        }
    }
    val cryptoState = MutableStateFlow(CryptoSnapshot(listOf(
        CryptoResult("gain", "gain", 6000000, today.toString(), "Fictional realised gain", Timestamp(1, 0), Timestamp(1, 0)),
        CryptoResult("loss", "loss", 2500000, today.toString(), "Fictional realised loss", Timestamp(1, 0), Timestamp(1, 0))), fromCache = false, loaded = true))
    val cryptoOperations = linkedMapOf<String, CryptoOperation>()
    var autoCrypto = true
    val crypto = object : CryptoRepository {
        override fun observe(uid: String, year: Int, cutoff: String) = cryptoState.map { snap ->
            if (uid != owner) CryptoSnapshot(loaded = true, fromCache = false) else snap.copy(
                results = snap.results.filter { it.resultDate >= "$year-01-01" && it.resultDate <= cutoff },
                confirmedResults = snap.confirmedResults?.filter { it.resultDate >= "$year-01-01" && it.resultDate <= cutoff })
        }
        override suspend fun submit(uid: String, operation: CryptoOperation) {
            check(uid == owner)
            cryptoOperations[operation.operationId]?.let { check(it == operation); return }
            cryptoOperations[operation.operationId] = operation; admission?.await()
            if (autoCrypto) {
                val next = operation.result.copy(createdAt = operation.result.createdAt ?: stamp(), updatedAt = stamp(), lastOperationId = operation.operationId)
                cryptoState.value = CryptoSnapshot(cryptoState.value.results.filterNot { it.id == next.id } +
                    if (operation.kind == "DELETE") emptyList() else listOf(next), loaded = true, fromCache = false)
            } else cryptoState.value = cryptoState.value.copy(confirmedResults = cryptoState.value.confirmedResults ?: cryptoState.value.results,
                results = cryptoState.value.results.filterNot { it.id == operation.result.id } + operation.result.copy(sync = CryptoSync.PENDING, deleting = operation.kind == "DELETE"),
                attempts = cryptoState.value.attempts + operation)
        }
        override suspend fun retry(uid: String, operation: CryptoOperation) = error("No automatic retry in visual fixture")
        override suspend fun resolve(uid: String, operation: CryptoOperation) = error("No server resolution in visual fixture")
        override suspend fun dismissRejected(uid: String, operation: CryptoOperation) {
            check(cryptoState.value.results.any { it.id == operation.result.id && it.sync == CryptoSync.FAILED })
            cryptoState.value = cryptoState.value.copy(results = cryptoState.value.confirmedResults.orEmpty(), attempts = cryptoState.value.attempts.filterNot { it == operation })
        }
    }
    val reporting = object : ReportingRepository {
        override fun expenses(uid: String, month: ReportingMonth) = expenseRows.map { ReportingBatch(if (uid == owner) it.filter { r -> month.contains(r.expenseDate) } else emptyList(), false) }
        override fun income(uid: String, month: ReportingMonth) = flowOf(ReportingBatch(emptyList<Income>(), false))
        override fun projects(uid: String) = flowOf(ReportingBatch(emptyList<ExpectedPayment>(), false))
        override fun crypto(uid: String, month: ReportingMonth) = cryptoState.map { ReportingBatch(if (uid == owner)
            (it.confirmedResults ?: it.results).filter { r -> r.sync in setOf(CryptoSync.CONFIRMED, CryptoSync.CACHED) && month.contains(r.resultDate) } else emptyList(), false) }
        override fun recovery(uid: String) = flowOf(ReportingRecovery())
    }
    fun graph() = isolatedAppGraph(auth, expense = expenses, goal = goals, recurring = recurring, crypto = crypto, reporting = reporting)
}
