package com.clarity.finance.acceptance

import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.di.viewModelModule
import com.google.firebase.Timestamp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.UUID

/** Debug-only, process-memory fixtures. No production module, Firebase client, store or worker. */
class AcceptanceFixture {
    val sessionId: String = UUID.randomUUID().toString()
    val owner = "isolated-human-review"
    val today = SavingsMath.today()
    private var sequence = 100L
    private fun stamp() = Timestamp(++sequence, 0)
    val user = MutableStateFlow<User?>(User(owner, "Fictional review account"))
    private val initial = Timestamp(1, 0)
    val pending = Expense("example-pending", 85000, categoryId = "transport", paymentChannelId = "primary_bank_card",
        spendingType = "discretionary", expenseDate = today.toString(), note = "Pending example — not confirmed", isPending = true)
    val rows = MutableStateFlow(listOf(
        Expense("example-rent", 3400000, categoryId = "housing", paymentChannelId = "cash", spendingType = "committed",
            expenseDate = today.toString(), note = "Fictional rent", updatedAt = initial),
        Expense("example-groceries", 600000, categoryId = "food_dining", paymentChannelId = "cash", spendingType = "committed",
            expenseDate = today.toString(), note = "Fictional groceries", updatedAt = initial),
        Expense("example-leisure", 1000000, categoryId = "custom_past_leisure", paymentChannelId = "secondary_bank_card", spendingType = "discretionary",
            expenseDate = today.toString(), note = "Fictional archived-category history", updatedAt = initial), pending))
    val createdIds = mutableListOf<String>()
    private val archived = CatalogueEntry("custom_past_leisure", "category", "Past leisure", true, "discretionary", initial)
    val attempts = MutableStateFlow(FinancialRecovery(listOf(
        FinancialAttempt(FinancialOperation("example-pending-operation", "EXPENSE", "CREATE", pending.id, expense = pending), "PENDING"),
        FinancialAttempt(FinancialOperation("example-rejected-operation", "CATEGORY", "UPDATE", archived.id,
            expectedUpdatedAt = initial, catalogue = archived.copy(name = "Proposed rename")), "REJECTED", "Example rejection; recorded history is unchanged."))))
    private fun readOnly(): Result<Unit> = Result.failure(IllegalStateException("Read-only fixture. Use ordinary expense capture for the timed trials."))
    val auth = object : AuthRepository {
        override val authState = user
        override suspend fun login(email: String, password: String): Result<User> =
            if (email == "review@example.test" && password == "test-only-123") Result.success(User(owner, "Fictional review account")).onSuccess { user.value = it }
            else Result.failure(IllegalArgumentException("Fictional credentials only"))
        override suspend fun register(email: String, password: String): Result<User> = Result.failure(IllegalStateException("Registration is disabled in this isolated fixture."))
        override suspend fun logout() { user.value = null }
    }
    val expenses = object : ExpenseRepository {
        override fun getExpenses(userId: String) = rows.map { if (userId == owner) it else emptyList() }
        override suspend fun getExpense(userId: String, expenseId: String): Result<Expense> = rows.value.firstOrNull { userId == owner && it.id == expenseId }
            ?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Expense not found or already deleted."))
        override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> {
            check(userId == owner)
            rows.value.firstOrNull { it.id == expense.id }?.let { existing ->
                val saved = FinancialOperation("fixture-match", "EXPENSE", "CREATE", expense.id, expense = existing).payload()
                val proposed = FinancialOperation("fixture-match", "EXPENSE", "CREATE", expense.id, expense = expense).payload()
                return if (saved == proposed) Result.success(Unit) else Result.failure(IllegalStateException("Fixture identity conflict"))
            }
            createdIds += expense.id
            val simulatedConfirmation = Timestamp.now()
            rows.value += expense.copy(createdAt = simulatedConfirmation, updatedAt = simulatedConfirmation, isPending = false, isFromCache = false)
            return Result.success(Unit) // Simulated acknowledgement; never evidence of Firestore persistence.
        }
        override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp): Result<Unit> {
            val old = rows.value.find { userId == owner && it.id == expense.id } ?: return readOnly()
            if (old.updatedAt != originalUpdatedAt) return Result.failure(IllegalStateException("Fixture changed; reopen it."))
            rows.value = rows.value.map { if (it.id == old.id) expense.copy(updatedAt = stamp(), createdAt = old.createdAt, recurringOccurrenceId = old.recurringOccurrenceId) else it }
            return Result.success(Unit)
        }
        override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp): Result<Unit> {
            val old = rows.value.find { userId == owner && it.id == expenseId } ?: return readOnly()
            if (old.updatedAt != originalUpdatedAt) return Result.failure(IllegalStateException("Fixture changed; reopen it."))
            rows.value = rows.value.filterNot { it.id == expenseId }; return Result.success(Unit)
        }
        override fun retryFailedExpense(userId: String, expense: Expense) { /* Retain the example proposal. */ }
    }
    private val receipts = listOf(
        Income("example-salary", "salary", today.toString(), "LKR", "128000", 12800000, "native_lkr", "1", "Fictional salary", updatedAt = initial),
        Income("example-adsense", "adsense", today.toString(), "USD", "41", 1230000, "confirmed_rate", "300", "Fictional fixed conversion", updatedAt = initial),
        Income("example-prior-direct", "freelance", today.minusMonths(1).withDayOfMonth(1).toString(), "LKR", "20000", 2000000,
            "native_lkr", "1", "Fictional earlier project receipt", originalAmountMinor = 2000000, linkedPaymentId = "example-project", updatedAt = initial),
        Income("example-prior-milestone", "freelance", today.minusMonths(1).withDayOfMonth(1).toString(), "LKR", "20000", 2000000,
            "native_lkr", "1", "Fictional earlier stage receipt", originalAmountMinor = 2000000, linkedPaymentId = "example-project",
            linkedMilestoneId = "example-milestone", updatedAt = initial))
    val income = object : IncomeRepository {
        override fun getIncomes(userId: String) = flowOf(if (userId == owner) receipts else emptyList())
        override suspend fun getIncome(userId: String, incomeId: String) = receipts.find { userId == owner && it.id == incomeId }
            ?.let { Result.success(it) } ?: Result.failure(IllegalStateException("No fixture receipt"))
        override suspend fun enqueueIncome(userId: String, income: Income) = readOnly()
        override suspend fun updateIncome(userId: String, income: Income, originalUpdatedAt: Timestamp) = readOnly()
        override suspend fun deleteIncome(userId: String, incomeId: String, originalUpdatedAt: Timestamp) = readOnly()
        override fun retryFailedIncome(userId: String, income: Income) {}
    }
    private val project = ExpectedPayment("example-project", "project", "Fictional website agreement", "freelance", "LKR", today.plusMonths(1).toString(),
        8000000, allocatedAmountMinor = 4000000, directReceivedAmountMinor = 2000000, milestoneReceivedAmountMinor = 2000000, updatedAt = initial)
    private val milestone = Milestone("example-milestone", project.id, "Fictional first stage", today.plusDays(7).toString(), 4000000, 2000000, updatedAt = initial)
    val payments = object : PaymentRepository {
        override fun getExpectedPayments(userId: String) = flowOf(if (userId == owner) listOf(project) else emptyList())
        override fun getMilestones(userId: String, paymentId: String) = flowOf(if (userId == owner && paymentId == project.id) listOf(milestone) else emptyList())
        override suspend fun getExpectedPayment(userId: String, paymentId: String) = if (userId == owner && paymentId == project.id) Result.success(project) else Result.failure(IllegalStateException("No fixture agreement"))
        override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment) = readOnly()
        override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: Timestamp) = readOnly()
        override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: Timestamp) = readOnly()
        override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String) = readOnly()
        override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: Timestamp, opId: String) = readOnly()
        override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: Timestamp, opId: String) = readOnly()
        override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income) = readOnly()
        override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income) = readOnly()
        override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? = null
    }
    private val cryptoRows = listOf(CryptoResult("example-gain", "gain", 6000000, today.toString(), "Fictional realised gain", updatedAt = initial),
        CryptoResult("example-loss", "loss", 2500000, today.toString(), "Fictional realised loss", updatedAt = initial))
    val crypto = object : CryptoRepository {
        override fun observe(uid: String, year: Int, cutoff: String) = flowOf(CryptoSnapshot(
            results = if (uid == owner) cryptoRows.filter { it.resultDate.take(4) == year.toString() && it.resultDate <= cutoff } else emptyList(), fromCache = false, loaded = true))
        override suspend fun submit(uid: String, operation: CryptoOperation) { error("Read-only fixture") }
        override suspend fun retry(uid: String, operation: CryptoOperation) { error("Read-only fixture") }
        override suspend fun dismissRejected(uid: String, operation: CryptoOperation) { error("Read-only fixture") }
    }
    private val goal = SavingsGoal(GoalConfig("Fictional learning fund", 49000000, 1120000, today.toString(), today.plusYears(1).toString()), 1120000, 0, "example-goal", updatedAt = initial)
    val goals = object : GoalRepository {
        override fun observe(uid: String) = flowOf(if (uid == owner) GoalSnapshot(goal, emptyList(), true, false) else GoalSnapshot(known = true, entries = emptyList(), fromCache = false))
        override suspend fun submit(uid: String, operation: GoalOperation) { error("Read-only fixture") }
        override suspend fun retry(uid: String, operation: GoalOperation) { error("Read-only fixture") }
        override suspend fun dismissRejected(uid: String, operation: GoalOperation) { error("Read-only fixture") }
        override suspend fun reviseExpired(uid: String, original: GoalOperation, replacement: GoalOperation) { error("Read-only fixture") }
        override suspend fun synchronize(uid: String) { awaitCancellation() }
    }
    private val rent = RecurringTemplate("example-rent-template", RecurringConfig("Fictional rent template", "housing", "committed", "cash", 3400000,
        today.withDayOfMonth(1).toString(), 1), 0, updatedAt = initial)
    val recurring = object : RecurringRepository {
        override fun templates(uid: String, limit: Int) = flowOf(ReportingBatch(if (uid == owner) listOf(rent) else emptyList(), false))
        override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = flowOf(ReportingBatch(emptyList<ConfirmedOccurrence>(), false))
        override fun recovery(uid: String) = flowOf(RecurringRecovery())
        override suspend fun submit(uid: String, operation: RecurringOperation) { error("Read-only fixture") }
        override suspend fun dismiss(uid: String, operation: RecurringOperation) { error("Read-only fixture") }
        override suspend fun synchronize(uid: String) { awaitCancellation() }
    }
    val recovery = object : FinancialRecoveryAccess {
        override fun observe(uid: String) = attempts.map { if (uid == owner) it else FinancialRecovery() }
        override suspend fun submit(uid: String, operation: FinancialOperation) { error("Read-only fixture") }
        override suspend fun resolve(uid: String, operation: FinancialOperation) { /* No simulated server proof or false success. */ }
        override suspend fun dismissRejected(uid: String, operation: FinancialOperation) {
            check(uid == owner && attempts.value.attempts.any { it.operation == operation && it.rejected })
            attempts.value = attempts.value.copy(attempts = attempts.value.attempts.filterNot { it.operation == operation })
        }
    }
    val catalogue = object : CatalogueRepository {
        override fun observe(uid: String) = flowOf(CatalogueSnapshot(CatalogueDefaults.entries() + if (uid == owner) listOf(archived) else emptyList(), false))
    }
    val reporting = object : ReportingRepository {
        override fun expenses(uid: String, month: ReportingMonth) = rows.map { ReportingBatch(if (uid == owner) it.filter { r -> !r.isPending && month.contains(r.expenseDate) } else emptyList(), false) }
        override fun income(uid: String, month: ReportingMonth) = flowOf(ReportingBatch(if (uid == owner) receipts.filter { month.contains(it.receiptDate) } else emptyList(), false))
        override fun projects(uid: String) = flowOf(ReportingBatch(if (uid == owner) listOf(project) else emptyList(), false))
        override fun crypto(uid: String, month: ReportingMonth) = flowOf(ReportingBatch(if (uid == owner) cryptoRows.filter { month.contains(it.resultDate) } else emptyList(), false))
        override fun recovery(uid: String) = attempts.map { ReportingRecovery(if (uid == owner) it.attempts.count { r -> !r.rejected } else 0, if (uid == owner) it.attempts.count { r -> r.rejected } else 0) }
    }
    val graph by lazy { koinApplication { modules(viewModelModule, module {
        single<AuthRepository> { auth }; single<ExpenseRepository> { expenses }; single<IncomeRepository> { income }
        single<PaymentRepository> { payments }; single<CryptoRepository> { crypto }; single<GoalRepository> { goals }
        single<RecurringRepository> { recurring }; single<ReportingRepository> { reporting }
        single<CatalogueRepository> { catalogue }; single<FinancialRecoveryAccess> { recovery }
    }) } }
}

object AcceptanceSession {
    var fixture = AcceptanceFixture()
        private set
    fun reset() { fixture.graph.close(); fixture = AcceptanceFixture() }
}
