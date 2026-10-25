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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class MilestoneCancellation(
    val paymentId: String,
    val milestoneId: String,
    val updatedAt: com.google.firebase.Timestamp,
    val opId: String
)

class FirestorePaymentRepository(
    private val firestore: FirebaseFirestore,
    private val recoveryStore: SubmissionRecoveryStore
) : PaymentRepository {

    override fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>> = callbackFlow {
        val listener = firestore.collection("users").document(userId).collection("expectedPayments")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val payments = snapshot.documents.mapNotNull { doc ->
                        try {
                            ExpectedPayment(
                                id = doc.id,
                                type = doc.getString("type") ?: "standalone",
                                title = doc.getString("title") ?: "",
                                sourceId = doc.getString("sourceId") ?: "",
                                currency = doc.getString("currency") ?: "LKR",
                                dueDate = doc.getString("dueDate") ?: "",
                                agreedAmountMinor = doc.getLong("agreedAmountMinor") ?: 0L,
                                allocatedAmountMinor = doc.getLong("allocatedAmountMinor") ?: 0L,
                                directReceivedAmountMinor = doc.getLong("directReceivedAmountMinor") ?: 0L,
                                milestoneReceivedAmountMinor = doc.getLong("milestoneReceivedAmountMinor") ?: 0L,
                                isCancelled = doc.getBoolean("isCancelled") ?: false,
                                note = doc.getString("note") ?: "",
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
        awaitClose { listener.remove() }
    }

    override fun getMilestones(userId: String, paymentId: String): Flow<List<Milestone>> = callbackFlow {
        val listener = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId).collection("milestones")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val milestones = snapshot.documents.mapNotNull { doc ->
                        try {
                            Milestone(
                                id = doc.id,
                                paymentId = paymentId,
                                title = doc.getString("title") ?: "",
                                dueDate = doc.getString("dueDate") ?: "",
                                agreedAmountMinor = doc.getLong("agreedAmountMinor") ?: 0L,
                                receivedAmountMinor = doc.getLong("receivedAmountMinor") ?: 0L,
                                isCancelled = doc.getBoolean("isCancelled") ?: false,
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
        awaitClose { listener.remove() }
    }

    override suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment> {
        return try {
            val doc = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
                .get(com.google.firebase.firestore.Source.SERVER).await()
            if (doc.exists()) {
                val p = ExpectedPayment(
                    id = doc.id,
                    type = doc.getString("type") ?: "standalone",
                    title = doc.getString("title") ?: "",
                    sourceId = doc.getString("sourceId") ?: "",
                    currency = doc.getString("currency") ?: "LKR",
                    dueDate = doc.getString("dueDate") ?: "",
                    agreedAmountMinor = doc.getLong("agreedAmountMinor") ?: 0L,
                    allocatedAmountMinor = doc.getLong("allocatedAmountMinor") ?: 0L,
                    directReceivedAmountMinor = doc.getLong("directReceivedAmountMinor") ?: 0L,
                    milestoneReceivedAmountMinor = doc.getLong("milestoneReceivedAmountMinor") ?: 0L,
                    isCancelled = doc.getBoolean("isCancelled") ?: false,
                    note = doc.getString("note") ?: "",
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

    override suspend fun getUnresolvedProjectCreation(userId: String): ExpectedPayment? =
        recoveryStore.getRecords(userId, "CREATE_PROJECT").firstOrNull()?.let {
            Gson().fromJson(it.payloadJson, ExpectedPayment::class.java).also { payment ->
                check(payment.id == it.recordId) { "Invalid saved creation identity" }
            }
        }

    override suspend fun getUnresolvedMilestoneCreation(userId: String, paymentId: String): Milestone? =
        recoveryStore.getRecords(userId, "CREATE_MILESTONE").map {
            Gson().fromJson(it.payloadJson, Milestone::class.java).also { milestone ->
                check(milestone.id == it.recordId) { "Invalid saved milestone identity" }
            }
        }.firstOrNull { it.paymentId == paymentId }

    override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> {
        return try {
            recoveryStore.saveOperation(SubmissionRecord.create(userId, payment.id, payment, "PENDING", "CREATE_PROJECT"))
            val ref = firestore.collection("users").document(userId).collection("expectedPayments").document(payment.id)
            firestore.runTransaction<Unit> { tx ->
                val existing = tx.get(ref)
                if (existing.exists()) {
                    val matches = existing.getString("type") == payment.type &&
                        existing.getString("title") == payment.title &&
                        existing.getString("sourceId") == payment.sourceId &&
                        existing.getString("currency") == payment.currency &&
                        existing.getString("dueDate") == payment.dueDate &&
                        existing.getLong("agreedAmountMinor") == payment.agreedAmountMinor &&
                        existing.getString("note") == payment.note
                    check(matches) { "Creation conflict: this ID already has different data." }
                    // Reconciliation must never reset balances, timestamps or cancellation.
                } else {
                    tx.set(ref, payment.toCreateMap())
                }
                Unit
            }.await()
            recoveryStore.removeRecord(userId, payment.id, "CREATE_PROJECT")
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val ref = firestore.collection("users").document(userId).collection("expectedPayments").document(payment.id)
            firestore.runTransaction<Unit> { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (snap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)
                if (snap.getBoolean("isCancelled") == true) throw FirebaseFirestoreException("Cancelled", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                tx.update(ref, payment.toUpdateMap())
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val ref = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
            firestore.runTransaction<Unit> { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (snap.getBoolean("isCancelled") == true) return@runTransaction
                if (snap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)
                tx.update(ref, mapOf("isCancelled" to true, "updatedAt" to FieldValue.serverTimestamp()))
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit> {
        return try {
            recoveryStore.saveOperation(SubmissionRecord.create(userId, milestone.id,
                milestone.copy(lastMilestoneOpId = opId), "PENDING", "CREATE_MILESTONE"))
            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(milestone.paymentId)
            val msRef = parentRef.collection("milestones").document(milestone.id)
            firestore.runTransaction<Unit> { tx ->
                val msSnap = tx.get(msRef)
                if (msSnap.exists()) {
                    check(msSnap.getString("title") == milestone.title &&
                        msSnap.getString("dueDate") == milestone.dueDate &&
                        msSnap.getLong("agreedAmountMinor") == milestone.agreedAmountMinor) {
                        "Creation conflict: milestone ID already has different data."
                    }
                    return@runTransaction
                }

                val parentSnap = tx.get(parentRef)
                if (!parentSnap.exists()) throw FirebaseFirestoreException("Parent not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (parentSnap.getString("type") != "project") throw FirebaseFirestoreException("Not a project", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                if (parentSnap.getBoolean("isCancelled") == true) throw FirebaseFirestoreException("Parent cancelled", FirebaseFirestoreException.Code.FAILED_PRECONDITION)

                val currentAllocated = parentSnap.getLong("allocatedAmountMinor") ?: 0L
                val directReceived = parentSnap.getLong("directReceivedAmountMinor") ?: 0L
                val agreed = parentSnap.getLong("agreedAmountMinor") ?: 0L

                val newAllocated = Math.addExact(currentAllocated, milestone.agreedAmountMinor)
                if (newAllocated > Math.subtractExact(agreed, directReceived)) {
                    throw FirebaseFirestoreException("Allocation exceeded", FirebaseFirestoreException.Code.OUT_OF_RANGE)
                }

                tx.update(parentRef, mapOf(
                    "allocatedAmountMinor" to newAllocated,
                    "lastMilestoneId" to milestone.id,
                    "lastMilestoneOpId" to opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                val msMap = milestone.toCreateMap(opId)
                tx.set(msRef, msMap)
                Unit
            }.await()
            recoveryStore.removeRecord(userId, milestone.id, "CREATE_MILESTONE")
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> {
        return try {
            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(milestone.paymentId)
            val msRef = parentRef.collection("milestones").document(milestone.id)
            firestore.runTransaction<Unit> { tx ->
                val msSnap = tx.get(msRef)
                if (!msSnap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (msSnap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)

                if (msSnap.getBoolean("isCancelled") == true) throw FirebaseFirestoreException("Cancelled", FirebaseFirestoreException.Code.FAILED_PRECONDITION)

                val oldAgreed = msSnap.getLong("agreedAmountMinor") ?: 0L
                val diff = Math.subtractExact(milestone.agreedAmountMinor, oldAgreed)

                val parentSnap = tx.get(parentRef)
                val currentAllocated = parentSnap.getLong("allocatedAmountMinor") ?: 0L
                val newAllocated = Math.addExact(currentAllocated, diff)

                tx.update(parentRef, mapOf(
                    "allocatedAmountMinor" to newAllocated,
                    "lastMilestoneId" to milestone.id,
                    "lastMilestoneOpId" to opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                tx.update(msRef, milestone.toUpdateMap(opId))
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> {
        val recordId = "$paymentId/$milestoneId"
        return try {
            val saved = recoveryStore.getRecords(userId, "CANCEL_MILESTONE").firstOrNull { it.recordId == recordId }
            val operation = saved?.let { Gson().fromJson(it.payloadJson, MilestoneCancellation::class.java) }
                ?: MilestoneCancellation(paymentId, milestoneId, originalUpdatedAt, opId)
            check(operation.paymentId == paymentId && operation.milestoneId == milestoneId) { "Cancellation target mismatch" }
            recoveryStore.saveOperation(SubmissionRecord.create(userId, recordId, operation, "PENDING", "CANCEL_MILESTONE"))
            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
            val msRef = parentRef.collection("milestones").document(milestoneId)
            firestore.runTransaction<Unit> { tx ->
                val msSnap = tx.get(msRef)
                if (!msSnap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (msSnap.getBoolean("isCancelled") == true) return@runTransaction
                if (msSnap.getTimestamp("updatedAt") != operation.updatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)

                val agreed = msSnap.getLong("agreedAmountMinor") ?: 0L
                val received = msSnap.getLong("receivedAmountMinor") ?: 0L
                val diffToRelease = Math.subtractExact(agreed, received)

                val parentSnap = tx.get(parentRef)
                val currentAllocated = parentSnap.getLong("allocatedAmountMinor") ?: 0L
                val newAllocated = Math.subtractExact(currentAllocated, diffToRelease)

                tx.update(parentRef, mapOf(
                    "allocatedAmountMinor" to newAllocated,
                    "lastMilestoneId" to milestoneId,
                    "lastMilestoneOpId" to operation.opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                tx.update(msRef, mapOf(
                    "isCancelled" to true,
                    "lastMilestoneOpId" to operation.opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                Unit
            }.await()
            recoveryStore.removeRecord(userId, recordId, "CANCEL_MILESTONE")
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            // A completed concurrency rejection is definite; the next attempt can use freshly loaded state.
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ABORTED) {
                recoveryStore.removeRecord(userId, recordId, "CANCEL_MILESTONE")
            }
            Result.failure(e)
        }
    }

    override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> {
        return executePaymentTransaction(userId, paymentId, milestoneId, income)
    }

    override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> {
        return executePaymentTransaction(userId, paymentId, milestoneId, income)
    }

    override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? {
        val payloadType = if (milestoneId != null) "PAYMENT_MILESTONE" else "PAYMENT_DIRECT"
        val records = recoveryStore.getRecords(userId, payloadType)
        for (record in records) {
                val payload = Gson().fromJson(record.payloadJson, PaymentConfirmationPayload::class.java)
                check(payload.income.id == record.recordId) { "Invalid saved receipt identity" }
                if (payload.paymentId == paymentId && payload.milestoneId == milestoneId) {
                    return payload.income
                }
        }
        return null
    }

    private suspend fun executePaymentTransaction(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> {
        val payloadType = if (milestoneId != null) "PAYMENT_MILESTONE" else "PAYMENT_DIRECT"
        val payload = PaymentConfirmationPayload(paymentId, milestoneId, income)
        if (income.linkedPaymentId != paymentId || income.linkedMilestoneId != milestoneId) {
            return Result.failure(IllegalArgumentException("Receipt target mismatch"))
        }

        var operationSaved = false
        try {
            recoveryStore.saveOperation(SubmissionRecord.create(userId, income.id, payload, "PENDING", payloadType))
            operationSaved = true

            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
            val incomeRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(income.id)

            firestore.runTransaction<Unit> { tx ->
                val incomeSnap = tx.get(incomeRef)
                if (incomeSnap.exists()) {
                    // Check if payload exactly matches
                    val rAmt = incomeSnap.getLong("originalAmountMinor")
                    val rPayId = incomeSnap.getString("linkedPaymentId")
                    val rMilId = incomeSnap.getString("linkedMilestoneId")
                    val rSourceId = incomeSnap.getString("sourceId")
                    val rCurrency = incomeSnap.getString("currency")
                    val rReceiptDate = incomeSnap.getString("receiptDate")
                    val rOriginalAmountStr = incomeSnap.getString("originalAmountStr")
                    val rLkrAmountMinor = incomeSnap.getLong("lkrAmountMinor")
                    val rConversionBasis = incomeSnap.getString("conversionBasis")
                    val rConversionRateStr = incomeSnap.getString("conversionRateStr")
                    val rNote = incomeSnap.getString("note")

                    if (rAmt == income.originalAmountMinor &&
                        rPayId == paymentId &&
                        rMilId == milestoneId &&
                        rSourceId == income.sourceId &&
                        rCurrency == income.currency &&
                        rReceiptDate == income.receiptDate &&
                        rOriginalAmountStr == income.originalAmountStr &&
                        rLkrAmountMinor == income.lkrAmountMinor &&
                        rConversionBasis == income.conversionBasis &&
                        rConversionRateStr == income.conversionRateStr &&
                        rNote == income.note
                    ) {
                        return@runTransaction // Already succeeded
                    }
                    throw Exception("Conflict")
                }

                val parentSnap = tx.get(parentRef)
                if (!parentSnap.exists()) throw FirebaseFirestoreException("Parent missing", FirebaseFirestoreException.Code.NOT_FOUND)
                if (parentSnap.getBoolean("isCancelled") == true) throw FirebaseFirestoreException("Cancelled", FirebaseFirestoreException.Code.FAILED_PRECONDITION)

                val amount = income.originalAmountMinor ?: 0L

                if (milestoneId != null) {
                    val msRef = parentRef.collection("milestones").document(milestoneId)
                    val msSnap = tx.get(msRef)
                    if (!msSnap.exists() || msSnap.getBoolean("isCancelled") == true) throw FirebaseFirestoreException("Milestone missing/cancelled", FirebaseFirestoreException.Code.FAILED_PRECONDITION)

                    val curMsRec = msSnap.getLong("receivedAmountMinor") ?: 0L
                    val msAgreed = msSnap.getLong("agreedAmountMinor") ?: 0L
                    if (Math.addExact(curMsRec, amount) > msAgreed) throw FirebaseFirestoreException("Overpayment", FirebaseFirestoreException.Code.OUT_OF_RANGE)

                    tx.update(msRef, mapOf(
                        "receivedAmountMinor" to Math.addExact(curMsRec, amount),
                        "lastReceiptId" to income.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))

                    val curParentMsRec = parentSnap.getLong("milestoneReceivedAmountMinor") ?: 0L
                    tx.update(parentRef, mapOf(
                        "milestoneReceivedAmountMinor" to Math.addExact(curParentMsRec, amount),
                        "lastReceiptId" to income.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    val curDirRec = parentSnap.getLong("directReceivedAmountMinor") ?: 0L
                    val agreed = parentSnap.getLong("agreedAmountMinor") ?: 0L
                    val allocated = parentSnap.getLong("allocatedAmountMinor") ?: 0L

                    if (Math.addExact(curDirRec, amount) > Math.subtractExact(agreed, allocated)) {
                        throw FirebaseFirestoreException("Overpayment", FirebaseFirestoreException.Code.OUT_OF_RANGE)
                    }

                    tx.update(parentRef, mapOf(
                        "directReceivedAmountMinor" to Math.addExact(curDirRec, amount),
                        "lastReceiptId" to income.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }

                val incMap = mutableMapOf<String, Any>(
                    "sourceId" to income.sourceId,
                    "receiptDate" to income.receiptDate,
                    "currency" to income.currency,
                    "originalAmountStr" to income.originalAmountStr,
                    "originalAmountMinor" to amount,
                    "lkrAmountMinor" to income.lkrAmountMinor,
                    "conversionBasis" to income.conversionBasis,
                    "note" to income.note,
                    "linkedPaymentId" to paymentId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (income.conversionRateStr != null) incMap["conversionRateStr"] = income.conversionRateStr
                if (milestoneId != null) incMap["linkedMilestoneId"] = milestoneId
                tx.set(incomeRef, incMap)
                Unit
            }.await()

            recoveryStore.removeRecord(userId, income.id, payloadType)
            return Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Failed"
            if (!operationSaved) return Result.failure(e)
            if (msg == "Conflict" || msg.contains("Conflict")) {
                recoveryStore.saveRecord(SubmissionRecord.create(userId, income.id, payload, "CONFLICT", payloadType, msg))
            } else {
                recoveryStore.saveRecord(SubmissionRecord.create(userId, income.id, payload, "FAILED", payloadType, msg))
            }
            return Result.failure(e)
        }
    }

    private fun ExpectedPayment.toCreateMap(): Map<String, Any> {
        return mapOf(
            "type" to type,
            "title" to title,
            "sourceId" to sourceId,
            "currency" to currency,
            "dueDate" to dueDate,
            "agreedAmountMinor" to agreedAmountMinor,
            "allocatedAmountMinor" to 0L,
            "directReceivedAmountMinor" to 0L,
            "milestoneReceivedAmountMinor" to 0L,
            "isCancelled" to false,
            "note" to note,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun ExpectedPayment.toUpdateMap(): Map<String, Any> {
        return mapOf(
            "title" to title,
            "dueDate" to dueDate,
            "agreedAmountMinor" to agreedAmountMinor,
            "note" to note,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun Milestone.toCreateMap(opId: String): Map<String, Any> {
        return mapOf(
            "title" to title,
            "dueDate" to dueDate,
            "agreedAmountMinor" to agreedAmountMinor,
            "receivedAmountMinor" to 0L,
            "isCancelled" to false,
            "lastMilestoneOpId" to opId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun Milestone.toUpdateMap(opId: String): Map<String, Any> {
        return mapOf(
            "title" to title,
            "dueDate" to dueDate,
            "agreedAmountMinor" to agreedAmountMinor,
            "lastMilestoneOpId" to opId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
