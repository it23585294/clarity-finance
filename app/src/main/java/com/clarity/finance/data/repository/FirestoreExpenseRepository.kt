package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Expense
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Success means durable local acceptance. Confirmation and recovery are owned by the process coordinator. */
class FirestoreExpenseRepository(private val firestore: FirebaseFirestore, private val queue: FinancialQueue,
    private val scope: CoroutineScope) : ExpenseRepository {
    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("expenses")
    override fun getExpenses(userId: String): Flow<List<Expense>> = combine(
        collection(userId).confirmedRows { it.expenseRow() }, queue.observe(userId)
    ) { confirmed, recovery ->
        check(recovery.error == null) { recovery.error.orEmpty() }
        // Changes/deletions retain the existing confirmed contribution; proposals live in Recovery.
        val ids = confirmed.map { it.id }.toSet()
        val creates = recovery.attempts.filter { it.operation.group == "EXPENSE" && it.operation.kind == "CREATE" && it.operation.targetId !in ids }
            .mapNotNull { attempt -> attempt.operation.expense?.copy(isPending = true, syncError = attempt.error,
                isConflict = attempt.rejected) }
        val annotated = confirmed.map { row -> row.copy(queuedChange = recovery.attempts.firstOrNull {
            it.operation.group == "EXPENSE" && it.operation.targetId == row.id
        }?.let { "${it.operation.kind}: ${it.status}. ${it.error.orEmpty()} Confirmed value retained." }) }
        (annotated + creates).sortedWith(compareByDescending<Expense> { it.expenseDate }.thenBy { it.id })
    }
    override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(expense.id, "EXPENSE", "CREATE", expense.id, expense = expense))
    }
    override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(UUID.randomUUID().toString(), "EXPENSE", "UPDATE", expense.id,
            expectedUpdatedAt = originalUpdatedAt, expense = expense))
    }
    override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(UUID.randomUUID().toString(), "EXPENSE", "DELETE", expenseId, expectedUpdatedAt = originalUpdatedAt))
    }
    override suspend fun getExpense(userId: String, expenseId: String): Result<Expense> = localResult {
        val document = collection(userId).document(expenseId).get().await()
        check(document.exists()) { "Expense not found. Uncached history needs a connection." }
        document.expenseRow()
    }
    override fun retryFailedExpense(userId: String, expense: Expense) {
        scope.launch { queue.retryRecord(userId, "EXPENSE", expense.id) }
    }
}
