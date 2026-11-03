package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp

/** Only one typed payload is authoritative. UI flags/timestamps never become financial write fields. */
data class FinancialOperation(val operationId: String, val group: String, val kind: String, val targetId: String,
    val parentId: String? = null, val expectedUpdatedAt: Timestamp? = null,
    val expense: Expense? = null, val income: Income? = null, val project: ExpectedPayment? = null,
    val milestone: Milestone? = null, val crypto: CryptoResult? = null, val catalogue: CatalogueEntry? = null,
    val legacyPayload: String? = null) {
    val type get() = "FINANCIAL_${group}_$kind"
    val recordKey get() = "$group/${parentId ?: "-"}/$targetId"
    fun validate() {
        require(operationId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && targetId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid operation identity." }
        require(group in setOf("EXPENSE", "INCOME", "PROJECT", "MILESTONE", "RECEIPT", "CRYPTO", "CATEGORY", "SOURCE"))
        require(kind in setOf("CREATE", "UPDATE", "DELETE", "CANCEL", "ARCHIVE"))
        require((group in setOf("MILESTONE", "RECEIPT")) == (parentId != null))
        parentId?.let { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
        require(if (kind == "CREATE") expectedUpdatedAt == null else expectedUpdatedAt != null) { "A confirmed baseline is required for a change." }
        require(kind != "DELETE" || group in setOf("EXPENSE", "INCOME", "CRYPTO"))
        require(kind != "CANCEL" || group in setOf("PROJECT", "MILESTONE"))
        require(kind != "ARCHIVE" || group in setOf("CATEGORY", "SOURCE"))
        val bodies = listOfNotNull(expense, income, project, milestone, crypto, catalogue)
        require(bodies.size == if (kind in setOf("DELETE", "CANCEL")) 0 else 1) { "An operation has exactly one immutable payload." }
        expense?.let { require(group == "EXPENSE" && it.id == targetId); SavingsMath.amount(java.math.BigDecimal.valueOf(it.amountMinor, 2).toPlainString()); require(it.currency == "LKR" && CatalogueDefaults.validId("category", it.categoryId)); require(it.note.orEmpty().length <= 500) }
        income?.let {
            require(group in setOf("INCOME", "RECEIPT") && it.id == targetId)
            FinancialValidation.income(it)
            require(if (group == "INCOME") it.linkedPaymentId == null && it.linkedMilestoneId == null
                else it.linkedPaymentId == parentId && it.originalAmountMinor == java.math.BigDecimal(it.originalAmountStr).movePointRight(2).longValueExact())
        }
        project?.let { require(group == "PROJECT" && it.id == targetId && it.agreedAmountMinor in 1..100000000000L && it.title.isNotBlank() && it.title.length <= 100 && it.note.length <= 500)
            require(it.type in setOf("standalone", "project") && it.currency in setOf("LKR", "USD") && CatalogueDefaults.validId("source", it.sourceId)) }
        milestone?.let { require(group == "MILESTONE" && it.id == targetId && it.paymentId == parentId && it.agreedAmountMinor in 1..100000000000L && it.title.isNotBlank() && it.title.length <= 100) }
        crypto?.let { require(group == "CRYPTO" && it.id == targetId); CryptoReporting.validate(it.direction, it.amountMinor, it.resultDate, it.note, java.time.LocalDate.MAX) }
        catalogue?.let { require(it.id == targetId && group == if (it.kind == "category") "CATEGORY" else "SOURCE"); it.validate() }
        listOfNotNull(expense?.expenseDate, income?.receiptDate, crypto?.resultDate, project?.dueDate, milestone?.dueDate).forEach { SavingsMath.date(it) }
    }
    fun payload(): Map<String, Any?> = when (group) {
        "EXPENSE" -> expense?.let { mapOf("amountMinor" to it.amountMinor, "currency" to it.currency, "categoryId" to it.categoryId,
            "paymentChannelId" to it.paymentChannelId, "spendingType" to it.spendingType, "expenseDate" to it.expenseDate, "note" to it.note.orEmpty()) }
        "INCOME", "RECEIPT" -> income?.let { linkedMapOf<String, Any?>("sourceId" to it.sourceId, "receiptDate" to it.receiptDate, "currency" to it.currency,
            "originalAmountStr" to it.originalAmountStr, "lkrAmountMinor" to it.lkrAmountMinor, "conversionBasis" to it.conversionBasis,
            "conversionRateStr" to it.conversionRateStr, "note" to it.note).apply {
                if (group == "RECEIPT") { put("originalAmountMinor", it.originalAmountMinor); put("linkedPaymentId", parentId); put("linkedMilestoneId", it.linkedMilestoneId) }
            } }
        "PROJECT" -> project?.let { mapOf("type" to it.type, "title" to it.title, "sourceId" to it.sourceId, "currency" to it.currency,
            "dueDate" to it.dueDate, "agreedAmountMinor" to it.agreedAmountMinor, "note" to it.note) }
        "MILESTONE" -> milestone?.let { mapOf("title" to it.title, "dueDate" to it.dueDate, "agreedAmountMinor" to it.agreedAmountMinor) }
        "CRYPTO" -> crypto?.let { mapOf("direction" to it.direction, "amountMinor" to it.amountMinor, "resultDate" to it.resultDate, "note" to it.note) }
        "CATEGORY", "SOURCE" -> catalogue?.values()
        else -> null
    } ?: emptyMap()
}

/** Only an acknowledged server fence/immutable contradiction establishes logical rejection. */
class DefinitiveMutationRejection(message: String) : Exception(message)

internal fun uncertainSyncMessage(error: Exception): String {
    val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name
    return "${code?.let { "$it: " }.orEmpty()}${error.message ?: "Server response unavailable"}. " +
        "Outcome remains uncertain; an earlier transaction may still complete. Check sign-in, network or access. Original proposal retained."
}

interface FinancialRemote {
    suspend fun apply(uid: String, operation: FinancialOperation)
    /** Reconcile if committed; otherwise atomically fence this immutable operation before allowing replacement. */
    suspend fun fence(uid: String, operation: FinancialOperation)
}

data class FinancialAttempt(val operation: FinancialOperation, val status: String, val error: String? = null) {
    val rejected get() = status == "REJECTED"
    val details: String get() = operation.run {
        expense?.let { "LKR ${SavingsMath.money(it.amountMinor)} | ${it.expenseDate} | ${it.categoryId}" }
            ?: income?.let { "${it.currency} ${it.originalAmountStr} | LKR ${SavingsMath.money(it.lkrAmountMinor)} | ${it.receiptDate}" }
            ?: project?.let { "${it.title} | ${it.currency} ${SavingsMath.money(it.agreedAmountMinor)}" }
            ?: milestone?.let { "${it.title} | ${SavingsMath.money(it.agreedAmountMinor)} in the project currency" }
            ?: catalogue?.let { "${it.name} | ${if (it.archived) "archive" else it.defaultValue}" }
            ?: "${group.lowercase()} record: $targetId"
    }
}
data class FinancialRecovery(val attempts: List<FinancialAttempt> = emptyList(), val error: String? = null)

interface FinancialRecoveryAccess {
    fun observe(uid: String): kotlinx.coroutines.flow.Flow<FinancialRecovery>
    suspend fun submit(uid: String, operation: FinancialOperation)
    suspend fun resolve(uid: String, operation: FinancialOperation)
    suspend fun dismissRejected(uid: String, operation: FinancialOperation)
}

internal object FinancialValidation {
    fun income(row: Income) {
        require(row.currency in setOf("LKR", "USD") && row.lkrAmountMinor > 0 && row.note.length <= 500 && CatalogueDefaults.validId("source", row.sourceId)) { "Invalid income values." }
        require(row.originalAmountStr.length <= 20 && row.originalAmountStr.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?"))) { "Use a positive plain decimal original amount." }
        val original = java.math.BigDecimal(row.originalAmountStr)
        require(original.signum() > 0)
        if (row.currency == "LKR") require(row.conversionBasis == "native_lkr" && row.conversionRateStr == "1" && original.movePointRight(2).longValueExact() == row.lkrAmountMinor) { "Native LKR amount mismatch." }
        else if (row.conversionBasis == "actual_credit") require(row.conversionRateStr == null) { "Actual credit has no saved conversion rate." }
        else {
            val rate = row.conversionRateStr.orEmpty()
            require(row.conversionBasis == "confirmed_rate" && rate.length <= 20 && rate.matches(Regex("[0-9]+(?:\\.[0-9]{1,8})?"))) { "Confirm a positive plain decimal rate." }
            val value = java.math.BigDecimal(rate); require(value.signum() > 0)
            require(original.multiply(value).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() == row.lkrAmountMinor) { "Confirmed conversion mismatch." }
        }
    }
}
