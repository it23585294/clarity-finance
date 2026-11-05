package com.clarity.finance.ui.pilot

import com.clarity.finance.data.model.CatalogueSnapshot
import com.clarity.finance.data.repository.FinancialAttempt
import com.clarity.finance.data.repository.FinancialOperation

/** Wording only. Eligibility remains FinancialAttempt.rejected and the existing callbacks. */
fun financialChangeTitle(operation: FinancialOperation): String {
    val action = when (operation.kind) { "CREATE" -> "Add"; "UPDATE" -> "Edit"; "DELETE" -> "Delete"; "CANCEL" -> "Cancel"; "ARCHIVE" -> "Archive"; else -> operation.kind }
    val record = when (operation.group) { "EXPENSE" -> "expense"; "INCOME" -> "income"; "PROJECT" -> "agreement"; "MILESTONE" -> "milestone"; "RECEIPT" -> "payment"; "CATEGORY" -> "category"; "SOURCE" -> "source"; else -> operation.group.lowercase() }
    return "$action $record"
}
fun financialChangeStatus(attempt: FinancialAttempt): String = when (attempt.status) {
    "REJECTED" -> "Change not applied · Rejected"
    "UNCERTAIN" -> "Confirmation unknown · Original proposal retained"
    "PENDING" -> "Saved on this device · Waiting for confirmation"
    else -> "Saved change · ${attempt.status}"
}
fun financialChangeDetails(operation: FinancialOperation, catalogue: CatalogueSnapshot): List<Pair<String, String>> = operation.run {
    expense?.let { listOf("Amount" to "LKR ${PresentationFormat.minor(it.amountMinor)}", "Payment date" to PresentationFormat.date(it.expenseDate), "Category" to catalogue.referenceLabel("category", it.categoryId)) }
        ?: income?.let { listOf("Original amount" to "${it.currency} ${PresentationFormat.amount(it.originalAmountStr)}", "LKR amount" to "LKR ${PresentationFormat.minor(it.lkrAmountMinor)}", "Received" to PresentationFormat.date(it.receiptDate), "Source" to catalogue.referenceLabel("source", it.sourceId)) }
        ?: project?.let { listOf("Agreement" to it.title, "Agreed" to "${it.currency} ${PresentationFormat.minor(it.agreedAmountMinor)}") }
        ?: milestone?.let { listOf("Milestone" to it.title, "Agreed (project currency)" to PresentationFormat.minor(it.agreedAmountMinor)) }
        ?: this.catalogue?.let { listOf("Name" to it.name, "Change" to if (it.archived) "Archive" else it.defaultValue.replaceFirstChar { c -> c.uppercase() }) }
        ?: listOf("Record reference" to targetId)
}
