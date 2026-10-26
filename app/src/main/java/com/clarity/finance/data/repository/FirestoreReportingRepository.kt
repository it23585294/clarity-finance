package com.clarity.finance.data.repository

import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

class FirestoreReportingRepository(
    private val firestore: FirebaseFirestore,
    private val recoveryStore: SubmissionRecoveryStore
) : ReportingRepository {
    private fun owner(uid: String, name: String): CollectionReference {
        require(uid.isNotBlank())
        return firestore.collection("users").document(uid).collection(name)
    }
    private fun month(uid: String, name: String, field: String, period: ReportingMonth): Query =
        owner(uid, name).whereGreaterThanOrEqualTo(field, period.start)
            .whereLessThan(field, period.endExclusive).orderBy(field)

    private fun <T> listen(query: Query, decode: (DocumentSnapshot) -> T): Flow<ReportingBatch<T>> = callbackFlow {
        val projection = ReportingProjection<T>()
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) {
                try {
                    // Pending local documents may have unresolved timestamps or changed reporting dates.
                    val pending = snapshot.metadata.hasPendingWrites() || snapshot.documents.any { it.metadata.hasPendingWrites() }
                    val rows = if (pending) emptyList() else snapshot.documents.map(decode)
                    trySend(projection.accept(rows, snapshot.metadata.isFromCache, pending))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { close(IllegalStateException("Reporting data could not be read: ${e.message}", e)) }
            }
        }
        awaitClose { registration.remove() }
    }

    override fun expenses(uid: String, month: ReportingMonth) = listen(month(uid, "expenses", "expenseDate", month)) { doc ->
        doc.timestamps()
        Expense(doc.id, doc.requiredLong("amountMinor"), doc.requiredString("currency"),
            doc.requiredString("categoryId"), doc.requiredString("paymentChannelId"), doc.requiredString("spendingType"),
            doc.requiredString("expenseDate"), doc.requiredString("note"), doc.getTimestamp("updatedAt"))
    }

    override fun income(uid: String, month: ReportingMonth) = listen(month(uid, "incomeReceipts", "receiptDate", month)) { doc ->
        doc.timestamps()
        Income(doc.id, doc.requiredString("sourceId"), doc.requiredString("receiptDate"), doc.requiredString("currency"),
            doc.requiredString("originalAmountStr"), doc.requiredLong("lkrAmountMinor"), doc.requiredString("conversionBasis"),
            doc.getString("conversionRateStr"), doc.requiredString("note"), doc.getLong("originalAmountMinor"),
            doc.getString("linkedPaymentId"), doc.getString("linkedMilestoneId"), doc.getTimestamp("createdAt"), doc.getTimestamp("updatedAt"))
    }

    override fun crypto(uid: String, month: ReportingMonth) = listen(month(uid, "cryptoResults", "resultDate", month)) { doc ->
        doc.timestamps()
        CryptoResult(doc.id, doc.requiredString("direction"), doc.requiredLong("amountMinor"), doc.requiredString("resultDate"),
            doc.requiredString("note"), doc.getTimestamp("createdAt"), doc.getTimestamp("updatedAt"), doc.requiredString("lastOperationId"),
            if (doc.metadata.isFromCache) CryptoSync.CACHED else CryptoSync.CONFIRMED)
    }

    override fun projects(uid: String) = listen(owner(uid, "expectedPayments")) { doc ->
        doc.timestamps()
        ExpectedPayment(doc.id, doc.requiredString("type"), doc.requiredString("title"), doc.requiredString("sourceId"),
            doc.requiredString("currency"), doc.requiredString("dueDate"), doc.requiredLong("agreedAmountMinor"),
            doc.requiredLong("allocatedAmountMinor"), doc.requiredLong("directReceivedAmountMinor"), doc.requiredLong("milestoneReceivedAmountMinor"),
            doc.getBoolean("isCancelled") ?: error("Missing project cancellation state."), doc.requiredString("note"),
            createdAt = doc.getTimestamp("createdAt"), updatedAt = doc.getTimestamp("updatedAt"), isFromCache = doc.metadata.isFromCache)
    }

    override fun recovery(uid: String) = recoveryStore.getOperationRecordsFlow(uid, setOf(
        "EXPENSE", "INCOME", "CREATE_PROJECT", "CREATE_MILESTONE", "PAYMENT_DIRECT", "PAYMENT_MILESTONE", "CANCEL_MILESTONE",
        "CRYPTO_CREATE", "CRYPTO_UPDATE", "CRYPTO_DELETE"
    )).map { records ->
        ReportingRecovery(records.count { it.status == "PENDING" }, records.count { it.status in setOf("FAILED", "CONFLICT") })
    }

    private fun DocumentSnapshot.requiredString(field: String) = getString(field) ?: error("Missing $field.")
    private fun DocumentSnapshot.requiredLong(field: String): Long {
        // getLong can coerce a floating Firestore number. Reporting must not silently truncate corrupt money.
        return get(field) as? Long ?: error("Invalid integer $field.")
    }
    private fun DocumentSnapshot.timestamps() {
        check(get("createdAt") is Timestamp && get("updatedAt") is Timestamp) { "Missing resolved server timestamps." }
    }
}
