package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecord
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.data.model.PaymentConfirmationPayload
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestorePaymentRepository(
    private val firestore: FirebaseFirestore,
    private val queue: FinancialQueue
) : PaymentRepository {

    override fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>> = combine(payments(userId), queue.observe(userId)) { rows, recovery ->
        check(recovery.error == null) { recovery.error.orEmpty() }
        val confirmed = rows.map { row -> row.copy(queuedChange = recovery.attempts.firstOrNull {
            (it.operation.group == "PROJECT" && it.operation.targetId == row.id) || it.operation.parentId == row.id
        }?.let { "${it.operation.group} ${it.operation.kind}: ${it.status}. ${it.error.orEmpty()} Confirmed amounts retained." }) }
        val ids = rows.map { it.id }.toSet()
        val creates = recovery.attempts.filter { it.operation.group == "PROJECT" && it.operation.kind == "CREATE" && it.operation.targetId !in ids }
            .mapNotNull { it.operation.project?.copy(hasPendingWrites = true, queuedChange = "Creation ${it.status}. ${it.error.orEmpty()}") }
        confirmed + creates
    }
    private fun payments(userId: String): Flow<List<ExpectedPayment>> = callbackFlow {
        var known = false
        val watchdog = launch { kotlinx.coroutines.delay(10000); if (!known) close(IllegalStateException("Confirmed history is unavailable. Connect or retry; zero is not assumed.")) }
        val listener = firestore.collection("users").document(userId).collection("expectedPayments")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.metadata.hasPendingWrites() || (snapshot.metadata.isFromCache && snapshot.isEmpty && !known)) return@addSnapshotListener
                    known = true; watchdog.cancel()
                    val payments = snapshot.documents.mapNotNull { doc ->
                        try {
                            ExpectedPayment(
                                id = doc.id,
                                type = doc.text("type"),
                                title = doc.text("title"),
                                sourceId = doc.text("sourceId"),
                                currency = doc.text("currency"),
                                dueDate = doc.text("dueDate"),
                                agreedAmountMinor = doc.integer("agreedAmountMinor"),
                                allocatedAmountMinor = doc.integer("allocatedAmountMinor"),
                                directReceivedAmountMinor = doc.integer("directReceivedAmountMinor"),
                                milestoneReceivedAmountMinor = doc.integer("milestoneReceivedAmountMinor"),
                                isCancelled = doc.getBoolean("isCancelled") ?: error("Missing cancellation state."),
                                note = doc.text("note"),
                                lastReceiptId = doc.getString("lastReceiptId"),
                                lastMilestoneId = doc.getString("lastMilestoneId"),
                                lastMilestoneOpId = doc.getString("lastMilestoneOpId"),
                                createdAt = doc.getTimestamp("createdAt"),
                                updatedAt = doc.getTimestamp("updatedAt"),
                                hasPendingWrites = doc.metadata.hasPendingWrites(),
                                isFromCache = doc.metadata.isFromCache
                            )
                        } catch (e: Exception) { close(IllegalStateException("Invalid payment document ${doc.id}", e)); return@addSnapshotListener }
                    }
                    trySend(payments.sortedBy { it.dueDate })
                }
            }
        awaitClose { watchdog.cancel(); listener.remove() }
    }

    override fun getMilestones(userId: String, paymentId: String): Flow<List<Milestone>> = combine(milestones(userId, paymentId), queue.observe(userId)) { rows, recovery ->
        check(recovery.error == null) { recovery.error.orEmpty() }
        val attempts = recovery.attempts.filter { it.operation.group == "MILESTONE" && it.operation.parentId == paymentId }
        val ids = rows.map { it.id }.toSet()
        rows.map { row -> row.copy(queuedChange = attempts.firstOrNull { it.operation.targetId == row.id }?.let { "${it.operation.kind}: ${it.status}. ${it.error.orEmpty()} Confirmed amounts retained." }) } +
            attempts.filter { it.operation.kind == "CREATE" && it.operation.targetId !in ids }.mapNotNull {
                it.operation.milestone?.copy(hasPendingWrites = true, queuedChange = "Creation ${it.status}. ${it.error.orEmpty()}") }
    }
    private fun milestones(userId: String, paymentId: String): Flow<List<Milestone>> = callbackFlow {
        var known = false
        val watchdog = launch { kotlinx.coroutines.delay(10000); if (!known) close(IllegalStateException("Confirmed history is unavailable. Connect or retry; zero is not assumed.")) }
        val listener = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId).collection("milestones")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.metadata.hasPendingWrites() || (snapshot.metadata.isFromCache && snapshot.isEmpty && !known)) return@addSnapshotListener
                    known = true; watchdog.cancel()
                    val milestones = snapshot.documents.mapNotNull { doc ->
                        try {
                            Milestone(
                                id = doc.id,
                                paymentId = paymentId,
                                title = doc.text("title"),
                                dueDate = doc.text("dueDate"),
                                agreedAmountMinor = doc.integer("agreedAmountMinor"),
                                receivedAmountMinor = doc.integer("receivedAmountMinor"),
                                isCancelled = doc.getBoolean("isCancelled") ?: error("Missing cancellation state."),
                                lastReceiptId = doc.getString("lastReceiptId"),
                                lastMilestoneOpId = doc.getString("lastMilestoneOpId"),
                                createdAt = doc.getTimestamp("createdAt"),
                                updatedAt = doc.getTimestamp("updatedAt"),
                                hasPendingWrites = doc.metadata.hasPendingWrites(),
                                isFromCache = doc.metadata.isFromCache
                            )
                        } catch (e: Exception) { close(IllegalStateException("Invalid payment document ${doc.id}", e)); return@addSnapshotListener }
                    }
                    trySend(milestones.sortedBy { it.dueDate })
                }
            }
        awaitClose { watchdog.cancel(); listener.remove() }
    }

    override suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment> {
        return try {
            val doc = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
                .get().await()
            if (doc.exists()) {
                val p = ExpectedPayment(
                    id = doc.id,
                    type = doc.text("type"),
                    title = doc.text("title"),
                    sourceId = doc.text("sourceId"),
                    currency = doc.text("currency"),
                    dueDate = doc.text("dueDate"),
                    agreedAmountMinor = doc.integer("agreedAmountMinor"),
                    allocatedAmountMinor = doc.integer("allocatedAmountMinor"),
                    directReceivedAmountMinor = doc.integer("directReceivedAmountMinor"),
                    milestoneReceivedAmountMinor = doc.integer("milestoneReceivedAmountMinor"),
                    isCancelled = doc.getBoolean("isCancelled") ?: error("Missing cancellation state."),
                    note = doc.text("note"),
                    lastReceiptId = doc.getString("lastReceiptId"),
                    lastMilestoneId = doc.getString("lastMilestoneId"),
                    lastMilestoneOpId = doc.getString("lastMilestoneOpId"),
                    createdAt = doc.getTimestamp("createdAt"),
                    updatedAt = doc.getTimestamp("updatedAt"),
                    hasPendingWrites = doc.metadata.hasPendingWrites(),
                                isFromCache = doc.metadata.isFromCache
                )
                Result.success(p)
            } else {
                Result.failure(Exception("Payment not found"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getUnresolvedProjectCreation(userId: String): ExpectedPayment? = queue.pending(userId)
        .firstOrNull { it.operation.group == "PROJECT" && it.operation.kind == "CREATE" }?.operation?.project
    override suspend fun getUnresolvedMilestoneCreation(userId: String, paymentId: String): Milestone? = queue.pending(userId)
        .firstOrNull { it.operation.group == "MILESTONE" && it.operation.kind == "CREATE" && it.operation.parentId == paymentId }?.operation?.milestone
    override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(payment.id, "PROJECT", "CREATE", payment.id, project = payment))
    }
    override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(java.util.UUID.randomUUID().toString(), "PROJECT", "UPDATE", payment.id, expectedUpdatedAt = originalUpdatedAt, project = payment))
    }
    override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(java.util.UUID.randomUUID().toString(), "PROJECT", "CANCEL", paymentId, expectedUpdatedAt = originalUpdatedAt))
    }
    override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(opId, "MILESTONE", "CREATE", milestone.id, milestone.paymentId, milestone = milestone.copy(lastMilestoneOpId = opId)))
    }
    override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(opId, "MILESTONE", "UPDATE", milestone.id, milestone.paymentId, originalUpdatedAt, milestone = milestone))
    }
    override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> = localResult {
        queue.submit(userId, FinancialOperation(opId, "MILESTONE", "CANCEL", milestoneId, paymentId, originalUpdatedAt))
    }
    override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> = localResult {
        check(income.linkedPaymentId == paymentId && income.linkedMilestoneId == milestoneId) { "Receipt identity does not match its agreement." }
        queue.submit(userId, FinancialOperation(income.id, "RECEIPT", "CREATE", income.id, paymentId, income = income))
    }
    override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> = localResult {
        val pending = queue.pending(userId).firstOrNull { it.operation.group == "RECEIPT" && it.operation.targetId == income.id && it.operation.parentId == paymentId }
        check(pending != null && pending.operation.income == income) { "Retry must use the saved receipt details." }
        queue.retry(userId, pending.operation)
    }
    override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? = queue.pending(userId)
        .firstOrNull { it.operation.group == "RECEIPT" && it.operation.parentId == paymentId && it.operation.income?.linkedMilestoneId == milestoneId }?.operation?.income
}
