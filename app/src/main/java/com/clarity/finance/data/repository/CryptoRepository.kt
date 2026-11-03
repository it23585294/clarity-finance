package com.clarity.finance.data.repository

import com.clarity.finance.data.model.CryptoResult
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow

data class CryptoOperation(
    val operationId: String,
    val kind: String,
    val result: CryptoResult,
    val expectedUpdatedAt: Timestamp? = null,
    val protocolVersion: Int = 2
) {
    val type: String get() = "CRYPTO_$kind"
}

data class CryptoSnapshot(
    val results: List<CryptoResult> = emptyList(),
    val attempts: List<CryptoOperation> = emptyList(),
    val fromCache: Boolean = true,
    val error: String? = null,
    val loaded: Boolean = false,
    /** Authoritative baseline stays separate from unconfirmed edit/delete proposals. */
    val confirmedResults: List<CryptoResult>? = null
)

interface CryptoRepository {
    suspend fun synchronize(uid: String) {}
    fun observe(uid: String, year: Int, cutoff: String): Flow<CryptoSnapshot>
    /** Returns after durable local acceptance, not after server confirmation. */
    suspend fun submit(uid: String, operation: CryptoOperation)
    suspend fun retry(uid: String, operation: CryptoOperation)
    suspend fun resolve(uid: String, operation: CryptoOperation) { error("Online resolution unavailable.") }
    suspend fun dismissRejected(uid: String, operation: CryptoOperation)
}
