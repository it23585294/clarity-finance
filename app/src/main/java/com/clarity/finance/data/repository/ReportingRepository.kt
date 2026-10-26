package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import kotlinx.coroutines.flow.Flow

/** Null records mean unavailable, never an implicitly empty month. No proposal is overlaid on these rows. */
data class ReportingBatch<T>(
    val records: List<T>? = null,
    val fromCache: Boolean = true,
    val awaitingWrites: Boolean = false,
    val error: String? = null
)

data class ReportingRecovery(val pending: Int = 0, val rejected: Int = 0)

/** Read-only reporting over existing schemas; mutations stay with the existing feature repositories. */
interface ReportingRepository {
    fun expenses(uid: String, month: ReportingMonth): Flow<ReportingBatch<Expense>>
    fun income(uid: String, month: ReportingMonth): Flow<ReportingBatch<Income>>
    fun crypto(uid: String, month: ReportingMonth): Flow<ReportingBatch<CryptoResult>>
    fun projects(uid: String): Flow<ReportingBatch<ExpectedPayment>>
    fun recovery(uid: String): Flow<ReportingRecovery>
}

/** A local edit/delete may remove or move a document from the query. Hold the entire last clean view. */
internal class ReportingProjection<T> {
    private var baseline: List<T>? = null
    fun accept(rows: List<T>, cached: Boolean, pending: Boolean): ReportingBatch<T> {
        if (pending) return ReportingBatch(baseline, true, awaitingWrites = true)
        if (cached && rows.isEmpty() && baseline == null)
            return ReportingBatch(error = "This history is not available in cache. Connect to load it.")
        baseline = rows
        return ReportingBatch(rows, cached)
    }
}
