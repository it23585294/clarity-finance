package com.clarity.finance

import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*

/** Fictional repositories only; mutation recording is not evidence of Firestore atomicity. */
class PhaseBFixtures : IncomeRepository, PaymentRepository {
    val today = SavingsMath.today().toString()
    val session = MutableStateFlow<User?>(User("phase-b-owner", "Fictional review account"))
    val auth = object : AuthRepository {
        override val authState = session
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { session.value = null }
    }
    val stamp = Timestamp(1,0)
    val sources = MutableStateFlow(CatalogueSnapshot(CatalogueDefaults.merge(listOf(
        CatalogueEntry("custom_consulting", "source", "Consulting", true, "USD", stamp))), false))
    val incomes = MutableStateFlow(listOf(
        Income("salary", "salary", today, "LKR", "100000", 10000000, "native_lkr", "1", "Fictional salary", updatedAt = stamp),
        Income("foreign", "custom_consulting", today, "USD", "100", 3100000, "confirmed_rate", "310", "Fictional overseas work", updatedAt = stamp),
        Income("linked", "custom_consulting", today, "LKR", "150", 15000, "native_lkr", "1", "Fictional linked receipt", linkedPaymentId = "project", updatedAt = stamp)))
    val projects = MutableStateFlow(listOf(ExpectedPayment("project", "project", "Studio website", "custom_consulting", "LKR", today,
        100000, allocatedAmountMinor = 40000, directReceivedAmountMinor = 5000, milestoneReceivedAmountMinor = 10000, updatedAt = stamp)))
    val milestones = MutableStateFlow(listOf(Milestone("milestone", "project", "Design approval", today, 40000, receivedAmountMinor = 10000, updatedAt = stamp)))
    val proposals = MutableStateFlow(FinancialRecovery(listOf(
        FinancialAttempt(FinancialOperation("uncertain", "INCOME", "UPDATE", "salary", expectedUpdatedAt = stamp, income = incomes.value.first()), "UNCERTAIN", "Confirmation unavailable. The original proposal is retained."),
        FinancialAttempt(FinancialOperation("rejected", "SOURCE", "UPDATE", "custom_consulting", expectedUpdatedAt = stamp, catalogue = sources.value.entries!!.first { it.id == "custom_consulting" }), "REJECTED", "Server rejected this proposal. Confirmed history is unchanged."))))
    val savedIncome = mutableListOf<Income>()
    val receipts = mutableListOf<Pair<String?, Income>>()
    val resolutions = mutableListOf<String>()
    val dismissals = mutableListOf<String>()
    val writes = mutableListOf<FinancialOperation>()
    var unresolvedReceipt: Income? = null
    var admission: CompletableDeferred<Unit>? = null
    override fun getIncomes(userId: String) = incomes.map { if (userId == "phase-b-owner") it else emptyList() }
    override suspend fun getIncome(userId: String, incomeId: String) = Result.success(incomes.value.single { it.id == incomeId })
    override suspend fun enqueueIncome(userId: String, income: Income): Result<Unit> { savedIncome += income; admission?.await(); return Result.success(Unit) }
    override fun retryFailedIncome(userId: String, income: Income) = error("No unrelated retry")
    override suspend fun updateIncome(userId: String, income: Income, originalUpdatedAt: Timestamp): Result<Unit> { savedIncome += income; incomes.value = incomes.value.filterNot { it.id == income.id } + income.copy(updatedAt = stamp); return Result.success(Unit) }
    override suspend fun deleteIncome(userId: String, incomeId: String, originalUpdatedAt: Timestamp): Result<Unit> { incomes.value = incomes.value.filterNot { it.id == incomeId }; return Result.success(Unit) }
    override fun getExpectedPayments(userId: String) = projects.map { if (userId == "phase-b-owner") it else emptyList() }
    override fun getMilestones(userId: String, paymentId: String) = milestones.map { it.filter { row -> row.paymentId == paymentId } }
    override suspend fun getExpectedPayment(userId: String, paymentId: String) = Result.success(projects.value.single { it.id == paymentId })
    override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? = unresolvedReceipt
    override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> { projects.value += payment.copy(updatedAt = stamp); return Result.success(Unit) }
    override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: Timestamp): Result<Unit> { projects.value = projects.value.filterNot { it.id == payment.id } + payment; return Result.success(Unit) }
    override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: Timestamp): Result<Unit> { projects.value = projects.value.map { if (it.id == paymentId) it.copy(isCancelled = true) else it }; return Result.success(Unit) }
    override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit> { milestones.value += milestone.copy(updatedAt = stamp); return Result.success(Unit) }
    override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: Timestamp, opId: String): Result<Unit> { milestones.value = milestones.value.filterNot { it.id == milestone.id } + milestone; return Result.success(Unit) }
    override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: Timestamp, opId: String): Result<Unit> { milestones.value = milestones.value.map { if (it.id == milestoneId) it.copy(isCancelled = true) else it }; return Result.success(Unit) }
    override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> { receipts += milestoneId to income; admission?.await(); return Result.success(Unit) }
    override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> { receipts += milestoneId to income; admission?.await(); return Result.failure(IllegalStateException("Confirmation still unknown in isolated fixture")) }
    val catalogue = object : CatalogueRepository { override fun observe(uid: String) = sources.map { if (uid == "phase-b-owner") it else CatalogueSnapshot(CatalogueDefaults.entries(), false) } }
    val recovery = object : FinancialRecoveryAccess {
        override fun observe(uid: String) = proposals.map { if (uid == "phase-b-owner") it else FinancialRecovery() }
        override suspend fun submit(uid: String, operation: FinancialOperation) { writes += operation
            sources.value = sources.value.copy(entries = CatalogueDefaults.merge(sources.value.entries.orEmpty().filterNot { it.id == operation.targetId && it.kind == operation.catalogue!!.kind } + operation.catalogue!!.copy(updatedAt = stamp))) }
        override suspend fun resolve(uid: String, operation: FinancialOperation) { resolutions += operation.operationId }
        override suspend fun dismissRejected(uid: String, operation: FinancialOperation) { dismissals += operation.operationId }
    }
    fun graph() = isolatedAppGraph(auth = auth, income = this, payment = this, catalogue = catalogue, recovery = recovery)
}
