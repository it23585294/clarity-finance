package com.clarity.finance.data.repository

import com.clarity.finance.data.model.Expense
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.SubmissionRecord
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

class FirestoreExpenseRepository(
    private val firestore: FirebaseFirestore,
    private val recoveryStore: SubmissionRecoveryStore
) : ExpenseRepository {

    override fun getExpenses(userId: String): Flow<List<Expense>> {
        val firestoreFlow = callbackFlow {
            val listener = firestore.collection("users")
                .document(userId)
                .collection("expenses")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val expenses = snapshot.documents.mapNotNull { doc ->
                            try {
                                Expense(
                                    id = doc.id,
                                    amountMinor = doc.getLong("amountMinor") ?: 0L,
                                    currency = doc.getString("currency") ?: "LKR",
                                    categoryId = doc.getString("categoryId") ?: "",
                                    paymentChannelId = doc.getString("paymentChannelId") ?: "",
                                    spendingType = doc.getString("spendingType") ?: "",
                                    expenseDate = doc.getString("expenseDate") ?: "",
                                    note = doc.getString("note"),
                                    updatedAt = doc.getTimestamp("updatedAt"),
                                    isPending = doc.metadata.hasPendingWrites()
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(expenses)
                    }
                }
            awaitClose { listener.remove() }
        }

        return combine(firestoreFlow, recoveryStore.getRecordsFlow(userId, "EXPENSE")) { firestoreExpenses, pendingRecords ->
            val pendingExpenses = pendingRecords.mapNotNull { it.toExpense() }
            
            // Only show pending/failed if they are not already in firestoreExpenses
            val firestoreIds = firestoreExpenses.map { it.id }.toSet()
            val filteredPending = pendingExpenses.filter { it.id !in firestoreIds }
            
            (firestoreExpenses + filteredPending).sortedWith(
                compareByDescending<Expense> { it.expenseDate }.thenByDescending { it.id }
            )
        }
    }

    override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> = suspendCancellableCoroutine { cont ->
        val docRef = firestore.collection("users").document(userId).collection("expenses").document(expense.id)
        val data = expense.toFirestoreMap(isRetry = false)

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

        // Persist to local datastore BEFORE pushing to Firestore
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // Ensure we don't accidentally resurrect a deleted expense if this was lingering
                val doc = docRef.get().await()
                if (doc.exists() || doc.metadata.isFromCache) {
                   // Or simply proceed, as rules / reconciliation handles staleness
                }
                
                recoveryStore.saveRecord(SubmissionRecord.fromExpense(userId, expense, "PENDING"))
                
                docRef.set(data).addOnFailureListener { exception ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        recoveryStore.saveRecord(SubmissionRecord.fromExpense(userId, expense, "FAILED", exception.message))
                    }
                }.addOnSuccessListener {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        recoveryStore.removeRecord(userId, expense.id, "EXPENSE")
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

    override fun retryFailedExpense(userId: String, expense: Expense) {
        val docRef = firestore.collection("users").document(userId).collection("expenses").document(expense.id)
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val doc = docRef.get().await()
                if (doc.exists()) {
                    // Reconcile: If it already matches, it's successful. Otherwise conflict.
                    val remoteAmount = doc.getLong("amountMinor")
                    val remoteDate = doc.getString("expenseDate")
                    if (remoteAmount == expense.amountMinor && remoteDate == expense.expenseDate) {
                        recoveryStore.removeRecord(userId, expense.id, "EXPENSE") // Confirmed successful
                    } else {
                        recoveryStore.saveRecord(SubmissionRecord.fromExpense(userId, expense, "CONFLICT", "Conflicting fields on server"))
                    }
                } else {
                    // Confirmed absent. Safe to retry `set`
                    recoveryStore.saveRecord(SubmissionRecord.fromExpense(userId, expense, "PENDING"))
                    docRef.set(expense.toFirestoreMap(isRetry = false)).addOnFailureListener { exception ->
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            recoveryStore.saveRecord(SubmissionRecord.fromExpense(userId, expense, "FAILED", exception.message))
                        }
                    }.addOnSuccessListener {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            recoveryStore.removeRecord(userId, expense.id, "EXPENSE")
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline, permission denied, etc -> keep failed
                recoveryStore.saveRecord(SubmissionRecord.fromExpense(userId, expense, "FAILED", e.message))
            }
        }
    }

    override suspend fun getExpense(userId: String, expenseId: String): Result<Expense> {
        return try {
            val docRef = firestore.collection("users").document(userId).collection("expenses").document(expenseId)
            val doc = docRef.get().await()
            if (doc.exists()) {
                val expense = Expense(
                    id = doc.id,
                    amountMinor = doc.getLong("amountMinor") ?: 0L,
                    currency = doc.getString("currency") ?: "LKR",
                    categoryId = doc.getString("categoryId") ?: "",
                    paymentChannelId = doc.getString("paymentChannelId") ?: "",
                    spendingType = doc.getString("spendingType") ?: "",
                    expenseDate = doc.getString("expenseDate") ?: "",
                    note = doc.getString("note"),
                    updatedAt = doc.getTimestamp("updatedAt"),
                    isPending = doc.metadata.hasPendingWrites()
                )
                Result.success(expense)
            } else {
                Result.failure(Exception("Expense not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(userId).collection("expenses").document(expense.id)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw FirebaseFirestoreException("Expense not found", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val currentUpdatedAt = snapshot.getTimestamp("updatedAt")
                if (currentUpdatedAt != originalUpdatedAt) {
                    throw FirebaseFirestoreException("Expense was modified elsewhere", FirebaseFirestoreException.Code.ABORTED)
                }
                transaction.update(docRef, expense.toFirestoreMap(isRetry = true))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(userId).collection("expenses").document(expenseId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw FirebaseFirestoreException("Expense not found", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val currentUpdatedAt = snapshot.getTimestamp("updatedAt")
                if (currentUpdatedAt != originalUpdatedAt) {
                    throw FirebaseFirestoreException("Expense was modified elsewhere", FirebaseFirestoreException.Code.ABORTED)
                }
                transaction.delete(docRef)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Expense.toFirestoreMap(isRetry: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "amountMinor" to amountMinor,
            "currency" to currency,
            "categoryId" to categoryId,
            "paymentChannelId" to paymentChannelId,
            "spendingType" to spendingType,
            "expenseDate" to expenseDate,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!note.isNullOrBlank()) {
            map["note"] = note.trim()
        }
        if (!isRetry) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }
}
