package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Income
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.SubmissionRecord
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class FirestoreIncomeRepository(
    private val firestore: FirebaseFirestore,
    private val recoveryStore: SubmissionRecoveryStore
) : IncomeRepository {

    override fun getIncomes(userId: String): Flow<List<Income>> {
        val firestoreFlow = callbackFlow {
            val listener = firestore.collection("users")
                .document(userId)
                .collection("incomeReceipts")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val incomes = snapshot.documents.mapNotNull { doc ->
                            try {
                                Income(
                                    id = doc.id,
                                    sourceId = doc.getString("sourceId") ?: "",
                                    receiptDate = doc.getString("receiptDate") ?: "",
                                    currency = doc.getString("currency") ?: "LKR",
                                    originalAmountStr = doc.getString("originalAmountStr") ?: "0",
                                    lkrAmountMinor = doc.getLong("lkrAmountMinor") ?: 0L,
                                    conversionBasis = doc.getString("conversionBasis") ?: "native_lkr",
                                    conversionRateStr = doc.getString("conversionRateStr"),
                                    note = doc.getString("note") ?: "",
                                    createdAt = doc.getTimestamp("createdAt"),
                                    updatedAt = doc.getTimestamp("updatedAt"),
                                    isPending = doc.metadata.hasPendingWrites()
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(incomes)
                    }
                }
            awaitClose { listener.remove() }
        }

        return combine(firestoreFlow, recoveryStore.getRecordsFlow(userId, "INCOME")) { firestoreIncomes, pendingRecords ->
            val pendingIncomes = pendingRecords.mapNotNull { record ->
                try {
                    val inc = Gson().fromJson(record.payloadJson, Income::class.java)
                    inc.copy(
                        isPending = record.status == "PENDING",
                        syncError = if (record.status == "FAILED" || record.status == "CONFLICT") record.errorReason ?: "Failed" else null,
                        isConflict = record.status == "CONFLICT"
                    )
                } catch (e: Exception) { null }
            }
            
            val firestoreIds = firestoreIncomes.map { it.id }.toSet()
            val filteredPending = pendingIncomes.filter { it.id !in firestoreIds }
            
            (firestoreIncomes + filteredPending).sortedWith(
                compareByDescending<Income> { it.receiptDate }.thenByDescending { it.id }
            )
        }
    }

    override suspend fun enqueueIncome(userId: String, income: Income): Result<Unit> = suspendCancellableCoroutine { cont ->
        val docRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(income.id)
        val data = income.toFirestoreMap(isRetry = false)

        var resumed = false
        val listener = docRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                if (!resumed) {
                    resumed = true
                    cont.resume(Result.failure(error))
                }
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                if (!resumed) {
                    resumed = true
                    cont.resume(Result.success(Unit))
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                recoveryStore.saveRecord(SubmissionRecord(userId, income.id, Gson().toJson(income), "PENDING", null, "INCOME"))
                
                docRef.set(data).addOnFailureListener { exception ->
                    CoroutineScope(Dispatchers.IO).launch {
                        recoveryStore.saveRecord(SubmissionRecord(userId, income.id, Gson().toJson(income), "FAILED", exception.message, "INCOME"))
                    }
                }.addOnSuccessListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        recoveryStore.removeRecord(income.id, "INCOME")
                    }
                }
            } catch (e: Exception) {
                if (!resumed) {
                    resumed = true
                    cont.resume(Result.failure(e))
                }
            }
        }

        cont.invokeOnCancellation { listener.remove() }
    }

    override fun retryFailedIncome(userId: String, income: Income) {
        val docRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(income.id)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = docRef.get().await()
                if (doc.exists()) {
                    val remoteAmount = doc.getLong("lkrAmountMinor")
                    val remoteDate = doc.getString("receiptDate")
                    if (remoteAmount == income.lkrAmountMinor && remoteDate == income.receiptDate) {
                        recoveryStore.removeRecord(income.id, "INCOME")
                    } else {
                        recoveryStore.saveRecord(SubmissionRecord(userId, income.id, Gson().toJson(income), "CONFLICT", "Conflicting fields on server", "INCOME"))
                    }
                } else {
                    recoveryStore.saveRecord(SubmissionRecord(userId, income.id, Gson().toJson(income), "PENDING", null, "INCOME"))
                    docRef.set(income.toFirestoreMap(isRetry = false)).addOnFailureListener { exception ->
                        CoroutineScope(Dispatchers.IO).launch {
                            recoveryStore.saveRecord(SubmissionRecord(userId, income.id, Gson().toJson(income), "FAILED", exception.message, "INCOME"))
                        }
                    }.addOnSuccessListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            recoveryStore.removeRecord(income.id, "INCOME")
                        }
                    }
                }
            } catch (e: Exception) {
                recoveryStore.saveRecord(SubmissionRecord(userId, income.id, Gson().toJson(income), "FAILED", e.message, "INCOME"))
            }
        }
    }

    override suspend fun getIncome(userId: String, incomeId: String): Result<Income> {
        return try {
            val docRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(incomeId)
            val doc = docRef.get().await()
            if (doc.exists()) {
                val income = Income(
                    id = doc.id,
                    sourceId = doc.getString("sourceId") ?: "",
                    receiptDate = doc.getString("receiptDate") ?: "",
                    currency = doc.getString("currency") ?: "LKR",
                    originalAmountStr = doc.getString("originalAmountStr") ?: "0",
                    lkrAmountMinor = doc.getLong("lkrAmountMinor") ?: 0L,
                    conversionBasis = doc.getString("conversionBasis") ?: "native_lkr",
                    conversionRateStr = doc.getString("conversionRateStr"),
                    note = doc.getString("note") ?: "",
                    createdAt = doc.getTimestamp("createdAt"),
                    updatedAt = doc.getTimestamp("updatedAt"),
                    isPending = doc.metadata.hasPendingWrites()
                )
                Result.success(income)
            } else {
                Result.failure(Exception("Income not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateIncome(userId: String, income: Income, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(income.id)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw FirebaseFirestoreException("Income not found", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val currentUpdatedAt = snapshot.getTimestamp("updatedAt")
                if (currentUpdatedAt != originalUpdatedAt) {
                    throw FirebaseFirestoreException("Income was modified elsewhere", FirebaseFirestoreException.Code.ABORTED)
                }
                transaction.update(docRef, income.toFirestoreMap(isRetry = true))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteIncome(userId: String, incomeId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(userId).collection("incomeReceipts").document(incomeId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw FirebaseFirestoreException("Income not found", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val currentUpdatedAt = snapshot.getTimestamp("updatedAt")
                if (currentUpdatedAt != originalUpdatedAt) {
                    throw FirebaseFirestoreException("Income was modified elsewhere", FirebaseFirestoreException.Code.ABORTED)
                }
                transaction.delete(docRef)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Income.toFirestoreMap(isRetry: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "sourceId" to sourceId,
            "receiptDate" to receiptDate,
            "currency" to currency,
            "originalAmountStr" to originalAmountStr,
            "lkrAmountMinor" to lkrAmountMinor,
            "conversionBasis" to conversionBasis,
            "note" to note,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (conversionRateStr != null) {
            map["conversionRateStr"] = conversionRateStr
        }
        if (!isRetry) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }
}
