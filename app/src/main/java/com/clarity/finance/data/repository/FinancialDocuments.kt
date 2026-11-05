package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

internal fun DocumentSnapshot.text(key: String) = get(key) as? String ?: error("Invalid $key.")
internal fun DocumentSnapshot.integer(key: String) = get(key) as? Long ?: error("Invalid integer $key.")
internal fun DocumentSnapshot.expenseRow(): Expense {
    val note = if (contains("note")) text("note") else ""
    check(note.length <= 500)
    val origin = if (contains("recurringOccurrenceId")) text("recurringOccurrenceId") else null
    check(origin == null || id == "rec_$origin")
    return Expense(id, integer("amountMinor"), text("currency"), text("categoryId"), text("paymentChannelId"),
        text("spendingType"), text("expenseDate"), note, getTimestamp("updatedAt"), metadata.hasPendingWrites(),
        recurringOccurrenceId = origin, isFromCache = metadata.isFromCache, createdAt = getTimestamp("createdAt")).also {
        check(it.amountMinor > 0 && CatalogueDefaults.validId("category", it.categoryId))
        SavingsMath.date(it.expenseDate)
    }
}
internal fun DocumentSnapshot.incomeRow() = Income(id, text("sourceId"), text("receiptDate"), text("currency"),
    text("originalAmountStr"), integer("lkrAmountMinor"), text("conversionBasis"), getString("conversionRateStr"),
    text("note"), getLong("originalAmountMinor"), getString("linkedPaymentId"), getString("linkedMilestoneId"),
    getTimestamp("createdAt"), getTimestamp("updatedAt"), metadata.hasPendingWrites(), isFromCache = metadata.isFromCache).also {
    check(it.lkrAmountMinor > 0 && CatalogueDefaults.validId("source", it.sourceId)); SavingsMath.date(it.receiptDate)
}

/** An empty uncached query is unknown. Unacknowledged SDK proposals never replace the clean baseline. */
internal fun <T> Query.confirmedRows(decode: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
    var baseline: List<T>? = null
    val watchdog = launch {
        delay(10000)
        if (baseline == null) close(IllegalStateException("Confirmed history is unavailable. Connect to load uncached records; zero is not assumed."))
    }
    val listener = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) close(error)
        else if (snapshot != null) try {
            if (snapshot.metadata.hasPendingWrites()) {
                baseline?.let { trySend(it) }
                    // Keep listening for acknowledgement; the watchdog bounds an unavailable baseline.
            } else if (snapshot.metadata.isFromCache && snapshot.isEmpty && baseline == null) {
                // An initial empty cache is not an empty server account. Keep the listener alive for its server event.
            } else {
                val rows = snapshot.documents.map(decode)
                baseline = rows; watchdog.cancel(); trySend(rows)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { close(IllegalStateException("History could not be read: ${e.message}", e)) }
    }
    awaitClose { watchdog.cancel(); listener.remove() }
}

internal suspend fun <T> localResult(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (e: CancellationException) { throw e }
catch (e: Exception) { Result.failure(e) }
