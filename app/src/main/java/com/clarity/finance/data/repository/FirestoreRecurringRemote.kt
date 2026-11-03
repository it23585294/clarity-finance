package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.YearMonth

class FirestoreRecurringRemote(private val firestore: FirebaseFirestore, private val clock: Clock = Clock.systemUTC(),
    private val activeUid: () -> String?) : RecurringRemote {
    private data class Running(val operation: RecurringOperation, val task: Task<RecurringOutcome>)
    private val running = mutableMapOf<Pair<String, String>, Running>()
    private fun owner(uid: String) = firestore.collection("users").document(uid)
    override fun templates(uid: String, limit: Int): Flow<ReportingBatch<RecurringTemplate>> {
        require(limit > 0)
        return listen(owner(uid).collection("recurringTemplates").orderBy(FieldPath.documentId()).limit(limit.toLong() + 1)) { it.template() }
    }
    override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String): Flow<ReportingBatch<ConfirmedOccurrence>> {
        Recurrence.id(templateId)
        val end = YearMonth.parse(endExclusive)
        require(end == Recurrence.month(start).plusMonths(12))
        return listen(owner(uid).collection("recurringOccurrences").whereEqualTo("templateId", templateId)
            .whereGreaterThanOrEqualTo("month", start).whereLessThan("month", if (end.year > 9999) "\uf8ff" else endExclusive).orderBy("month")) { it.occurrence() }
    }
    private fun <T> listen(query: Query, decode: (DocumentSnapshot) -> T): Flow<ReportingBatch<T>> = callbackFlow {
        val projection = ReportingProjection<T>()
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { docs, error ->
            if (error != null) close(error)
            else if (docs != null) try {
                val pending = docs.metadata.hasPendingWrites() || docs.documents.any { it.metadata.hasPendingWrites() }
                trySend(projection.accept(if (pending) emptyList() else docs.documents.map(decode), docs.metadata.isFromCache, pending))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { close(e) }
        }
        awaitClose { registration.remove() }
    }
    override suspend fun apply(uid: String, operation: RecurringOperation): RecurringOutcome {
        operation.validate()
        check(activeUid() == uid) { "Account changed. Proposal belongs to the original account." }
        val key = uid to operation.operationId
        val task = synchronized(running) {
            running[key]?.also { check(it.operation == operation) { "An outstanding proposal cannot change." } }?.task
                ?: transaction(uid, operation).also { running[key] = Running(operation, it) }
        }
        task.addOnCompleteListener(java.util.concurrent.Executor { it.run() }) {
            synchronized(running) { if (running[key]?.task === task) running.remove(key) }
        }
        try { return task.await() }
        finally { if (task.isComplete) synchronized(running) { if (running[key]?.task === task) running.remove(key) } }
    }
    private fun transaction(uid: String, op: RecurringOperation): Task<RecurringOutcome> {
        val root = owner(uid)
        val templateRef = root.collection("recurringTemplates").document(op.templateId)
        val operationRef = root.collection("recurringOperations").document(op.operationId)
        return firestore.runTransaction<RecurringOutcome> { tx ->
            check(activeUid() == uid) { "Account changed. Reopen recurring expenses." }
            val confirmation = op.confirmation
            val occurrenceRef = confirmation?.let { root.collection("recurringOccurrences").document(it.id) }
            if (occurrenceRef != null) {
                val existing = tx.get(occurrenceRef)
                if (existing.exists()) {
                    val accepted = existing.occurrence()
                    // Do not consult mutable template/expense state. A deleted expense remains consumed.
                    return@runTransaction if (accepted.confirmation == confirmation) RecurringOutcome.Confirmed
                    else RecurringOutcome.Rejected("This month was confirmed with different details. Inspect its existing actual expense/history; no second expense was created.")
                }
            }
            val receipt = tx.get(operationRef)
            if (receipt.exists()) {
                check(confirmation == null && receipt.text("kind") == op.kind && receipt.text("recordId") == op.recordId &&
                    receipt.text("templateId") == op.templateId && receipt.integer("version") == op.version && receipt.get("payload") == op.payload()) {
                    "Operation receipt identity conflict. Keep the immutable proposal for inspection."
                }
                receipt.timestamp("createdAt")
                return@runTransaction RecurringOutcome.Confirmed
            }
            val document = tx.get(templateRef)
            val before = if (document.exists()) document.template() else null
            // Returning these results commits a read-only transaction: the SDK must verify receipt absence
            // and monotonic state before rejection is exposed. Errors alone never establish rejection.
            if (op.kind == "CREATE" && before != null) return@runTransaction RecurringOutcome.Rejected("This template identity already exists. Open the confirmed template.")
            if (before?.isCancelled == true) return@runTransaction RecurringOutcome.Rejected("The template is cancelled. Unconfirmed occurrences are stopped; existing spending is unchanged.")
            if (op.kind != "CREATE" && before != null && before.version > op.expectedVersion!!)
                return@runTransaction RecurringOutcome.Rejected("The template changed. Dismiss this rejected proposal, inspect the latest version and review the actual details again.")
            check(if (op.kind == "CREATE") before == null else before != null && before.version == op.expectedVersion) { "Template version is unavailable; reconciliation required." }
            if (op.config != null && before != null && op.config.firstDueDate != before.config.firstDueDate)
                return@runTransaction RecurringOutcome.Rejected("The first due date cannot change. Existing monthly identities are retained.")
            if (op.kind == "CANCEL" && op.config != before!!.config)
                return@runTransaction RecurringOutcome.Rejected("Cancellation details conflict with the confirmed template.")
            if (confirmation != null) {
                val template = before!!
                if (confirmation.templateName != template.config.name || confirmation.dueDate != Recurrence.due(template.config, YearMonth.parse(confirmation.month)).toString())
                    return@runTransaction RecurringOutcome.Rejected("The scheduled occurrence differs from this template version. Reopen the current occurrence.")
                check(confirmation.dueDate <= Recurrence.today(clock).toString()) { "This occurrence is not due yet." }
                Recurrence.validate(confirmation.actual, Recurrence.today(clock))
                val expenseRef = root.collection("expenses").document(confirmation.expenseId)
                check(!tx.get(expenseRef).exists()) { "Expense identity exists without a confirmation. Keep this proposal for inspection." }
                check(activeUid() == uid) { "Account changed." }
                tx.set(expenseRef, confirmation.actual.values() + mapOf("recurringOccurrenceId" to confirmation.id,
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                tx.set(occurrenceRef!!, confirmation.values() + mapOf("operationId" to op.operationId, "createdAt" to FieldValue.serverTimestamp()))
            } else {
                check(activeUid() == uid) { "Account changed." }
                val data = op.payload() + mapOf("version" to op.version, "lastOperationId" to op.operationId, "updatedAt" to FieldValue.serverTimestamp())
                if (before == null) tx.set(templateRef, data + ("createdAt" to FieldValue.serverTimestamp())) else tx.update(templateRef, data)
            }
            tx.set(operationRef, mapOf("kind" to op.kind, "templateId" to op.templateId, "recordId" to op.recordId,
                "version" to op.version, "payload" to op.payload(), "createdAt" to FieldValue.serverTimestamp()))
            RecurringOutcome.Confirmed
        }
    }
    private fun DocumentSnapshot.text(field: String) = get(field) as? String ?: error("Missing/invalid $field.")
    private fun DocumentSnapshot.integer(field: String) = get(field) as? Long ?: error("Missing/invalid integer $field.")
    private fun DocumentSnapshot.timestamp(field: String) = get(field) as? Timestamp ?: error("Missing resolved $field.")
    private fun DocumentSnapshot.template(): RecurringTemplate {
        val day = integer("anchorDay"); check(day in 1..31)
        val config = RecurringConfig(text("name"), text("categoryId"), text("spendingType"), text("paymentChannelId"),
            integer("suggestedMinor"), text("firstDueDate"), day.toInt(), text("cadence"))
        Recurrence.validate(config); Recurrence.id(id)
        return RecurringTemplate(id, config, integer("version"), get("isCancelled") as? Boolean ?: error("Invalid cancellation state."),
            text("lastOperationId"), timestamp("createdAt"), timestamp("updatedAt")).also { check(it.version >= 0) }
    }
    private fun DocumentSnapshot.occurrence(): ConfirmedOccurrence {
        val actual = get("actual") as? Map<*, *> ?: error("Missing actual expense payload.")
        fun string(key: String) = actual[key] as? String ?: error("Invalid actual $key.")
        val expense = ActualRecurringExpense(actual["amountMinor"] as? Long ?: error("Invalid actual amount."), string("expenseDate"),
            string("categoryId"), string("spendingType"), string("paymentChannelId"), string("note"), string("currency"))
        Recurrence.validate(expense)
        val value = RecurringConfirmation(text("templateId"), text("month"), text("dueDate"), integer("templateVersion"), text("templateName"), expense)
        check(value.id == id && value.expenseId == text("expenseId") && value.templateVersion >= 0)
        Recurrence.id(value.templateId); Recurrence.month(value.month); SavingsMath.date(value.dueDate)
        check(value.dueDate.startsWith(value.month + "-") && value.templateName.isNotBlank() && value.templateName.length <= 100)
        val operation = text("operationId"); Recurrence.id(operation)
        return ConfirmedOccurrence(value, operation, timestamp("createdAt"))
    }
}
