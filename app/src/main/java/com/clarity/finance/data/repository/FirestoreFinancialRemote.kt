package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** A cancelled/timed-out await never cancels or forgets an outstanding Firebase Task. */
internal class RetainedTasks<T> {
    private val starts = Mutex()
    private val tasks = ConcurrentHashMap<String, Task<T>>()
    suspend fun await(key: String, create: () -> Task<T>): T {
        val task = starts.withLock {
            tasks[key] ?: create().also { next ->
                tasks[key] = next
                next.addOnCompleteListener(Executor { it.run() }) { tasks.remove(key, next) }
            }
        }
        return task.await()
    }
}

/** Immutable receipts/fences are checked before mutable targets. Financial/counter writes remain one transaction. */
class FirestoreFinancialRemote(private val db: FirebaseFirestore, private val activeUid: () -> String?) : FinancialRemote {
    private data class Decision(val rejection: String? = null)
    private val tasks = RetainedTasks<Decision>()
    private class Reject(message: String) : Exception(message)
    private fun owner(uid: String) { check(activeUid() == uid) { "Account/access changed. The original user's proposal is retained." } }
    private fun root(uid: String) = db.collection("users").document(uid)
    private fun primary(uid: String, op: FinancialOperation): DocumentReference = when (op.group) {
        "EXPENSE" -> root(uid).collection("expenses").document(op.targetId)
        "INCOME", "RECEIPT" -> root(uid).collection("incomeReceipts").document(op.targetId)
        "PROJECT" -> root(uid).collection("expectedPayments").document(op.targetId)
        "MILESTONE" -> root(uid).collection("expectedPayments").document(op.parentId!!).collection("milestones").document(op.targetId)
        "CRYPTO" -> root(uid).collection("cryptoResults").document(op.targetId)
        "CATEGORY" -> root(uid).collection("expenseCategories").document(op.targetId)
        "SOURCE" -> root(uid).collection("incomeSources").document(op.targetId)
        else -> error("Unknown operation target.")
    }
    private fun state(uid: String, op: FinancialOperation): DocumentReference {
        val group = if (op.group == "RECEIPT") "INCOME" else op.group
        val base = root(uid).collection("syncStates").document(group)
        return if (group == "MILESTONE") base.collection("parents").document(op.parentId!!).collection("records").document(op.targetId)
            else base.collection("records").document(op.targetId)
    }
    private fun proof(op: FinancialOperation): Map<String, Any?> = mapOf("group" to op.group, "kind" to op.kind,
        "targetId" to op.targetId, "parentId" to (op.parentId ?: ""), "expectedUpdatedAt" to op.expectedUpdatedAt, "payload" to op.payload())
    private fun matches(snapshot: DocumentSnapshot, op: FinancialOperation) = proof(op).all { (key, value) -> snapshot.get(key) == value }

    override suspend fun apply(uid: String, operation: FinancialOperation) = execute(uid, operation, false)
    override suspend fun fence(uid: String, operation: FinancialOperation) = execute(uid, operation, true)

    private suspend fun execute(uid: String, op: FinancialOperation, withdraw: Boolean) {
        owner(uid); op.validate()
        val result = tasks.await("$uid/${op.operationId}/$withdraw") {
            db.runTransaction { tx ->
                owner(uid)
                val receiptRef = root(uid).collection("syncOperations").document(op.operationId)
                val fenceRef = root(uid).collection("syncFences").document(op.operationId)
                val receipt = tx.get(receiptRef)
                val fence = tx.get(fenceRef)
                if (receipt.exists()) {
                    check(matches(receipt, op)) { "Operation receipt identity conflict. Original proposal retained; rejection is not established for a legacy attempt." }
                    return@runTransaction Decision()
                }
                if (fence.exists()) {
                    check(matches(fence, op)) { "Fence identity conflict. Original proposal retained for inspection." }
                    check(tx.get(state(uid, op)).exists()) { "Fence has no record barrier. An older attempt may still commit; keep this proposal." }
                    return@runTransaction Decision("The server has permanently fenced this proposal. Review confirmed history before replacing it.")
                }
                val ref = primary(uid, op)
                val before = tx.get(ref)
                val stateRef = state(uid, op)
                val priorState = tx.get(stateRef)
                // Legacy matching creates/receipts are reconciled before cancellation/version/catalogue checks.
                if (op.legacyPayload != null && op.kind == "CREATE" && before.exists() && sameBusiness(before, op)) return@runTransaction Decision()
                if (op.legacyPayload != null && op.kind == "CANCEL" && before.getBoolean("isCancelled") == true) return@runTransaction Decision()
                if (withdraw) {
                    writeFence(tx, op, fenceRef, stateRef, before.exists(), priorState.exists())
                    return@runTransaction Decision("Unconfirmed proposal withdrawn with server acknowledgement.")
                }
                if (op.legacyPayload != null && op.kind == "CREATE" && !before.exists() && op.group in setOf("EXPENSE", "INCOME", "CRYPTO")) {
                    // Pre-protocol absence cannot distinguish 'never submitted' from 'committed, then deleted'.
                    error("Legacy outcome cannot be established from absence. Review history and resolve before recreating; the saved proposal is retained.")
                }
                val decision = try {
                    if (op.kind == "CREATE") {
                        reject(!before.exists() && !priorState.exists(), "This record identity already exists or was retired. Do not recreate it.")
                    } else {
                        reject(before.exists() && before.getTimestamp("updatedAt") == op.expectedUpdatedAt, "Record changed or was removed. Reopen the confirmed version.")
                    }
                    // All reads precede all writes. plan() returns only the write phase.
                    val write = plan(tx, uid, op, ref, before)
                    owner(uid)
                    write()
                    tx.set(receiptRef, proof(op) + ("createdAt" to FieldValue.serverTimestamp()))
                    tx.set(stateRef, mapOf("operationId" to op.operationId, "deleted" to (op.kind == "DELETE"), "updatedAt" to FieldValue.serverTimestamp()))
                    Decision()
                } catch (e: Reject) {
                    // The fence serializes against earlier attempts; a local/access exception never reaches this branch.
                    writeFence(tx, op, fenceRef, stateRef, before.exists(), priorState.exists())
                    Decision(e.message ?: "Proposal fenced after a verified conflict.")
                }
                decision
            }
        }
        result.rejection?.let { throw DefinitiveMutationRejection(it) }
    }

    private fun writeFence(tx: Transaction, op: FinancialOperation, fence: DocumentReference, state: DocumentReference,
        targetExists: Boolean, stateExists: Boolean) {
        tx.set(fence, proof(op) + ("createdAt" to FieldValue.serverTimestamp()))
        // Old SDK Tasks never read syncFences. First adoption also installs a rule-enforced record barrier,
        // including for existing documents, without changing any business field or replacing a prior retirement.
        if (!stateExists) tx.set(state, mapOf("operationId" to op.operationId, "deleted" to !targetExists,
            "updatedAt" to FieldValue.serverTimestamp()))
    }

    private fun reject(valid: Boolean, reason: String) { if (!valid) throw Reject(reason) }
    private fun sameBusiness(doc: DocumentSnapshot, op: FinancialOperation) = op.payload().all { (key, value) ->
        if (key == "note") (doc.get(key) ?: "") == value else doc.get(key) == value
    }
    private fun dated(date: String) = reject(SavingsMath.date(date) <= SavingsMath.today(), "Actual transaction dates cannot be future dates.")
    private fun catalogue(tx: Transaction, uid: String, kind: String, id: String, unchanged: String? = null) {
        reject(CatalogueDefaults.validId(kind, id), "Unknown catalogue identity.")
        val doc = tx.get(root(uid).collection(if (kind == "category") "expenseCategories" else "incomeSources").document(id))
        reject((doc.exists() || !id.startsWith("custom_")) && (id == unchanged || doc.getBoolean("archived") != true), "This catalogue choice is missing or archived; choose an active value.")
    }
    private fun stamp(values: Map<String, Any?>, create: Boolean): Map<String, Any?> = values +
        mapOf("updatedAt" to FieldValue.serverTimestamp()) + if (create) mapOf("createdAt" to FieldValue.serverTimestamp()) else emptyMap()

    private fun plan(tx: Transaction, uid: String, op: FinancialOperation, ref: DocumentReference, before: DocumentSnapshot): () -> Unit {
        val create = op.kind == "CREATE"
        if (op.group in setOf("CATEGORY", "SOURCE")) {
            val entry = op.catalogue!!
            reject(!before.exists() || before.getBoolean("archived") != true || entry.archived, "Archived entries cannot be restored by this operation.")
            val body = stamp(entry.values(), create)
            return { if (create) tx.set(ref, body) else tx.update(ref, body) }
        }
        if (op.group == "EXPENSE") {
            if (op.kind == "DELETE") return { tx.delete(ref) }
            val row = op.expense!!; dated(row.expenseDate)
            reject(row.amountMinor > 0 && row.currency == "LKR" && row.note.orEmpty().length <= 500, "Invalid expense values.")
            reject(row.paymentChannelId in setOf("cash", "primary_bank_card", "secondary_bank_card", "other") && row.spendingType in setOf("committed", "discretionary"), "Invalid expense classifications.")
            catalogue(tx, uid, "category", row.categoryId, if (create) null else before.getString("categoryId"))
            val body = stamp(op.payload(), create)
            return { if (create) tx.set(ref, body) else tx.update(ref, body) }
        }
        if (op.group == "INCOME") {
            reject(!before.exists() || before.getString("linkedPaymentId") == null, "Linked project receipts are immutable. Inspect the project instead.")
            if (op.kind == "DELETE") return { tx.delete(ref) }
            val row = op.income!!; dated(row.receiptDate); validateIncome(row)
            catalogue(tx, uid, "source", row.sourceId, if (create) null else before.getString("sourceId"))
            val body = stamp(op.payload(), create) // Explicit null clears an obsolete confirmed rate on actual-credit edits.
            return { if (create) tx.set(ref, body) else tx.update(ref, body) }
        }
        if (op.group == "CRYPTO") {
            if (op.kind == "DELETE") return { tx.delete(ref) }
            val row = op.crypto!!; dated(row.resultDate)
            val body = stamp(op.payload() + ("lastOperationId" to op.operationId), create)
            return { if (create) tx.set(ref, body) else tx.update(ref, body) }
        }
        if (op.group == "PROJECT") {
            reject(!before.exists() || before.getBoolean("isCancelled") != true, "Cancelled agreements cannot be changed.")
            if (op.kind == "CANCEL") return { tx.update(ref, mapOf("isCancelled" to true, "updatedAt" to FieldValue.serverTimestamp())) }
            val row = op.project!!
            reject(row.type in setOf("standalone", "project") && row.currency in setOf("LKR", "USD"), "Invalid agreement type/currency.")
            catalogue(tx, uid, "source", row.sourceId, if (create) null else before.getString("sourceId"))
            if (!create) {
                reject(row.type == before.getString("type") && row.sourceId == before.getString("sourceId") && row.currency == before.getString("currency"), "Agreement type/source/currency are immutable.")
                reject(row.agreedAmountMinor >= Math.addExact(before.getLong("allocatedAmountMinor")!!, before.getLong("directReceivedAmountMinor")!!), "Agreement is below allocated/received money.")
            }
            val body = stamp(op.payload() + if (create) mapOf("allocatedAmountMinor" to 0L, "directReceivedAmountMinor" to 0L,
                "milestoneReceivedAmountMinor" to 0L, "isCancelled" to false) else emptyMap(), create)
            return { if (create) tx.set(ref, body) else tx.update(ref, body) }
        }
        val parentRef = root(uid).collection("expectedPayments").document(op.parentId!!)
        val parent = tx.get(parentRef)
        reject(parent.exists() && (parent.getBoolean("isCancelled") != true || (op.group == "MILESTONE" && op.kind == "CANCEL")), "Parent agreement is unavailable or cancelled.")
        if (op.group == "MILESTONE") {
            reject(parent.getString("type") == "project", "Milestones require a project.")
            reject(!before.exists() || before.getBoolean("isCancelled") != true, "Cancelled milestone cannot be changed.")
            val received = if (create) 0L else before.getLong("receivedAmountMinor")!!
            val previous = if (create) 0L else before.getLong("agreedAmountMinor")!!
            val agreed = if (op.kind == "CANCEL") previous else op.milestone!!.agreedAmountMinor
            reject(agreed >= received, "Milestone agreement is below received money.")
            val delta = if (op.kind == "CANCEL") -Math.subtractExact(previous, received) else Math.subtractExact(agreed, previous)
            val allocated = Math.addExact(parent.getLong("allocatedAmountMinor")!!, delta)
            reject(allocated >= 0 && allocated <= Math.subtractExact(parent.getLong("agreedAmountMinor")!!, parent.getLong("directReceivedAmountMinor")!!), "Project allocation capacity exceeded.")
            val msBody = if (op.kind == "CANCEL") mapOf("isCancelled" to true, "lastMilestoneOpId" to op.operationId, "updatedAt" to FieldValue.serverTimestamp())
                else stamp(op.payload() + ("lastMilestoneOpId" to op.operationId) + if (create) mapOf("receivedAmountMinor" to 0L, "isCancelled" to false) else emptyMap(), create)
            return {
                tx.update(parentRef, mapOf("allocatedAmountMinor" to allocated, "lastMilestoneId" to op.targetId,
                    "lastMilestoneOpId" to op.operationId, "updatedAt" to FieldValue.serverTimestamp()))
                if (create) tx.set(ref, msBody) else tx.update(ref, msBody)
            }
        }
        reject(op.group == "RECEIPT", "Unsupported operation target.")
        val income = op.income!!; dated(income.receiptDate); validateIncome(income)
        val amount = income.originalAmountMinor ?: 0L
        reject(amount > 0 && income.linkedPaymentId == op.parentId && income.currency == parent.getString("currency") && income.sourceId == parent.getString("sourceId"), "Receipt does not match its agreement.")
        reject(java.math.BigDecimal(income.originalAmountStr).movePointRight(2).longValueExact() == amount, "Receipt original amount mismatch.")
        // The receipt inherits its existing agreement's immutable source; it is not a new source selection.
        catalogue(tx, uid, "source", income.sourceId, parent.getString("sourceId"))
        val msId = income.linkedMilestoneId
        val msRef = msId?.let { parentRef.collection("milestones").document(it) }
        val ms = msRef?.let { tx.get(it) }
        val next = if (ms != null) {
            reject(ms.exists() && ms.getBoolean("isCancelled") != true, "Milestone unavailable/cancelled.")
            val value = Math.addExact(ms.getLong("receivedAmountMinor")!!, amount)
            reject(value <= ms.getLong("agreedAmountMinor")!!, "Milestone overpayment."); value
        } else {
            val value = Math.addExact(parent.getLong("directReceivedAmountMinor")!!, amount)
            reject(value <= Math.subtractExact(parent.getLong("agreedAmountMinor")!!, parent.getLong("allocatedAmountMinor")!!), "Direct payment exceeds unallocated agreement."); value
        }
        val body = stamp(op.payload().filterValues { it != null }, true)
        return {
            if (msRef != null) {
                tx.update(msRef, mapOf("receivedAmountMinor" to next, "lastReceiptId" to op.targetId, "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(parentRef, mapOf("milestoneReceivedAmountMinor" to Math.addExact(parent.getLong("milestoneReceivedAmountMinor")!!, amount),
                    "lastReceiptId" to op.targetId, "updatedAt" to FieldValue.serverTimestamp()))
            } else tx.update(parentRef, mapOf("directReceivedAmountMinor" to next, "lastReceiptId" to op.targetId, "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(ref, body)
        }
    }

    private fun validateIncome(row: Income) {
        reject(row.currency in setOf("LKR", "USD") && row.lkrAmountMinor > 0 && row.note.length <= 500, "Invalid income values.")
        reject(row.originalAmountStr.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?")) && row.originalAmountStr.length <= 20 && java.math.BigDecimal(row.originalAmountStr).signum() > 0, "Use a positive plain decimal original amount.")
        if (row.currency == "LKR") reject(row.conversionBasis == "native_lkr" && row.conversionRateStr == "1" &&
            java.math.BigDecimal(row.originalAmountStr).movePointRight(2).longValueExact() == row.lkrAmountMinor, "LKR income must preserve its exact native amount.")
        else {
            reject(row.conversionBasis in setOf("confirmed_rate", "actual_credit"), "Confirm a foreign-income conversion basis.")
            if (row.conversionBasis == "confirmed_rate") {
                val rate = row.conversionRateStr
                reject(rate != null && rate.matches(Regex("[0-9]+(?:\\.[0-9]{1,8})?")) && rate.length <= 20 && java.math.BigDecimal(rate).signum() > 0, "Use a positive plain decimal rate.")
                reject(java.math.BigDecimal(row.originalAmountStr).multiply(java.math.BigDecimal(rate)).setScale(2, java.math.RoundingMode.HALF_UP).movePointRight(2).longValueExact() == row.lkrAmountMinor, "Confirmed conversion does not match LKR amount.")
            } else reject(row.conversionRateStr == null, "Actual credit must not retain an obsolete rate.")
        }
    }
}
