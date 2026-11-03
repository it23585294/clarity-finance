package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Income
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Success means durable local acceptance. Confirmation and recovery are owned by the process coordinator. */
class FirestoreIncomeRepository(private val firestore: FirebaseFirestore, private val queue: FinancialQueue,
    private val scope: CoroutineScope) : IncomeRepository {
    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("incomeReceipts")
    override fun getIncomes(userId: String): Flow<List<Income>> = rows(userId, collection(userId), null)
    override fun getIncomes(userId: String, period: com.clarity.finance.data.model.ReportingMonth): Flow<List<Income>> =
        rows(userId, collection(userId).whereGreaterThanOrEqualTo("receiptDate", period.start)
            .whereLessThan("receiptDate", period.endExclusive).orderBy("receiptDate"), period)
    private fun rows(userId: String, query: com.google.firebase.firestore.Query, period: com.clarity.finance.data.model.ReportingMonth?): Flow<List<Income>> = combine(
        query.confirmedRows { it.incomeRow() }, queue.observe(userId)
    ) { confirmed, recovery ->
        check(recovery.error == null) { recovery.error.orEmpty() }
        // Changes/deletions retain the existing confirmed contribution; proposals live in Recovery.
        val ids = confirmed.map { it.id }.toSet()
        val creates = recovery.attempts.filter { it.operation.group == "INCOME" && it.operation.kind == "CREATE" && it.operation.targetId !in ids }
            .mapNotNull { attempt -> attempt.operation.income?.copy(isPending = true, syncError = attempt.error,
                isConflict = attempt.rejected) }
        val annotated = confirmed.map { row -> row.copy(queuedChange = recovery.attempts.firstOrNull {
            it.operation.group == "INCOME" && it.operation.targetId == row.id
        }?.let { "${it.operation.kind}: ${it.status}. ${it.error.orEmpty()} Confirmed value retained." }) }
        (annotated + creates).filter { period == null || period.contains(it.receiptDate) }
            .sortedWith(compareByDescending<Income> { it.receiptDate }.thenBy { it.id })
    }
    override suspend fun enqueueIncome(userId: String, income: Income): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(income.id, "INCOME", "CREATE", income.id, income = income))
    }
    override suspend fun updateIncome(userId: String, income: Income, originalUpdatedAt: Timestamp): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(UUID.randomUUID().toString(), "INCOME", "UPDATE", income.id,
            expectedUpdatedAt = originalUpdatedAt, income = income))
    }
    override suspend fun deleteIncome(userId: String, incomeId: String, originalUpdatedAt: Timestamp): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(UUID.randomUUID().toString(), "INCOME", "DELETE", incomeId, expectedUpdatedAt = originalUpdatedAt))
    }
    override suspend fun getIncome(userId: String, incomeId: String): Result<Income> = localResult {
        val document = collection(userId).document(incomeId).get().await()
        check(document.exists()) { "Income not found. Uncached history needs a connection." }
        document.incomeRow()
    }
    override fun retryFailedIncome(userId: String, income: Income) {
        scope.launch { queue.retryRecord(userId, "INCOME", income.id) }
    }
}
