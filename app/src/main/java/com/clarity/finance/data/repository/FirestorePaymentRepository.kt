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

class FirestorePaymentRepository(
    private val firestore: FirebaseFirestore,
    private val recoveryStore: SubmissionRecoveryStore
) : PaymentRepository {

    override fun getExpectedPayments(userId: String): Flow<List<ExpectedPayment>> = callbackFlow {
        val listener = firestore.collection("users").document(userId).collection("expectedPayments")
            .addSnapshotListener { snapshot, error ->
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
                                updatedAt = doc.getTimestamp("updatedAt")
                            )
                        } catch (e: Exception) { null }
                    }
                    trySend(payments.sortedBy { it.dueDate })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun getMilestones(userId: String, paymentId: String): Flow<List<Milestone>> = callbackFlow {
        val listener = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId).collection("milestones")
            .addSnapshotListener { snapshot, error ->
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
                                updatedAt = doc.getTimestamp("updatedAt")
                            )
                        } catch (e: Exception) { null }
                    }
                    trySend(milestones.sortedBy { it.dueDate })
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getExpectedPayment(userId: String, paymentId: String): Result<ExpectedPayment> {
        return try {
            val doc = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId).get().await()
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
                    updatedAt = doc.getTimestamp("updatedAt")
                )
                Result.success(p)
            } else {
                Result.failure(Exception("Payment not found"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment): Result<Unit> {
        return try {
            val ref = firestore.collection("users").document(userId).collection("expectedPayments").document(payment.id)
            val map = payment.toFirestoreMap().toMutableMap()
            map["createdAt"] = FieldValue.serverTimestamp()
            ref.set(map).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val ref = firestore.collection("users").document(userId).collection("expectedPayments").document(payment.id)
            firestore.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (snap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)
                tx.update(ref, payment.toFirestoreMap())
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val ref = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
            firestore.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (snap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)
                tx.update(ref, mapOf("isCancelled" to true, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String): Result<Unit> {
        return try {
            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(milestone.paymentId)
            val msRef = parentRef.collection("milestones").document(milestone.id)
            firestore.runTransaction { tx ->
                val parentSnap = tx.get(parentRef)
                if (!parentSnap.exists()) throw FirebaseFirestoreException("Parent not found", FirebaseFirestoreException.Code.NOT_FOUND)
                
                val currentAllocated = parentSnap.getLong("allocatedAmountMinor") ?: 0L
                val newAllocated = Math.addExact(currentAllocated, milestone.agreedAmountMinor)
                
                tx.update(parentRef, mapOf(
                    "allocatedAmountMinor" to newAllocated,
                    "lastMilestoneId" to milestone.id,
                    "lastMilestoneOpId" to opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                
                val msMap = milestone.toFirestoreMap().toMutableMap()
                msMap["createdAt"] = FieldValue.serverTimestamp()
                msMap["lastMilestoneOpId"] = opId
                tx.set(msRef, msMap)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> {
        return try {
            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(milestone.paymentId)
            val msRef = parentRef.collection("milestones").document(milestone.id)
            firestore.runTransaction { tx ->
                val msSnap = tx.get(msRef)
                if (!msSnap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (msSnap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)
                
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
                
                val msMap = milestone.toFirestoreMap().toMutableMap()
                msMap["lastMilestoneOpId"] = opId
                tx.update(msRef, msMap)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: com.google.firebase.Timestamp, opId: String): Result<Unit> {
        return try {
            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
            val msRef = parentRef.collection("milestones").document(milestoneId)
            firestore.runTransaction { tx ->
                val msSnap = tx.get(msRef)
                if (!msSnap.exists()) throw FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
                if (msSnap.getTimestamp("updatedAt") != originalUpdatedAt) throw FirebaseFirestoreException("Aborted", FirebaseFirestoreException.Code.ABORTED)
                
                val agreed = msSnap.getLong("agreedAmountMinor") ?: 0L
                val received = msSnap.getLong("receivedAmountMinor") ?: 0L
                val diffToRelease = Math.subtractExact(agreed, received)
                
                val parentSnap = tx.get(parentRef)
                val currentAllocated = parentSnap.getLong("allocatedAmountMinor") ?: 0L
                val newAllocated = Math.subtractExact(currentAllocated, diffToRelease)
                
                tx.update(parentRef, mapOf(
                    "allocatedAmountMinor" to newAllocated,
                    "lastMilestoneId" to milestoneId,
                    "lastMilestoneOpId" to opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                
                tx.update(msRef, mapOf(
                    "isCancelled" to true,
                    "agreedAmountMinor" to received, // lock effective allocation
                    "lastMilestoneOpId" to opId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income): Result<Unit> {
        return executePaymentTransaction(userId, paymentId, milestoneId, income, isRetry = false)
    }

    override fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income) {
        CoroutineScope(Dispatchers.IO).launch {
            executePaymentTransaction(userId, paymentId, milestoneId, income, isRetry = true)
        }
    }

    private suspend fun executePaymentTransaction(userId: String, paymentId: String, milestoneId: String?, income: Income, isRetry: Boolean): Result<Unit> {
        val payloadType = if (milestoneId != null) "PAYMENT_MILESTONE" else "PAYMENT_DIRECT"
        val payload = PaymentConfirmationPayload(paymentId, milestoneId, income)
        
        try {
            if (!isRetry) {
                recoveryStore.saveRecord(SubmissionRecord.create(userId, income.id, payload, "PENDING", payloadType))
            }

            val parentRef = firestore.collection("users").document(userId).collection("expectedPayments").document(paymentId)
            val incomeRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(income.id)

            firestore.runTransaction { tx ->
                val incomeSnap = tx.get(incomeRef)
                if (incomeSnap.exists()) {
                    // Check if payload exactly matches
                    val rAmt = incomeSnap.getLong("originalAmountMinor")
                    val rPayId = incomeSnap.getString("linkedPaymentId")
                    if (rAmt == income.originalAmountMinor && rPayId == paymentId) {
                        return@runTransaction // Already succeeded
                    }
                    throw FirebaseFirestoreException("Conflict", FirebaseFirestoreException.Code.ALREADY_EXISTS)
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
            }.await()
            
            recoveryStore.removeRecord(userId, income.id, payloadType)
            return Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message ?: "Failed"
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                recoveryStore.saveRecord(SubmissionRecord.create(userId, income.id, payload, "CONFLICT", payloadType, msg))
            } else {
                recoveryStore.saveRecord(SubmissionRecord.create(userId, income.id, payload, "FAILED", payloadType, msg))
            }
            return Result.failure(e)
        }
    }

    private fun ExpectedPayment.toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "type" to type,
            "title" to title,
            "sourceId" to sourceId,
            "currency" to currency,
            "dueDate" to dueDate,
            "agreedAmountMinor" to agreedAmountMinor,
            "isCancelled" to isCancelled,
            "note" to note,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return map
    }

    private fun Milestone.toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to title,
            "dueDate" to dueDate,
            "agreedAmountMinor" to agreedAmountMinor,
            "isCancelled" to isCancelled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return map
    }
}
