package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.time.Clock

private data class GoalDocument(val goal: SavingsGoal?, val cached: Boolean, val pending: Boolean)
private data class GoalEntries(val entries: List<SavingsEntry>, val cached: Boolean, val pending: Boolean)

class FirestoreGoalRemote(private val firestore: FirebaseFirestore, private val clock: Clock = Clock.systemUTC(), private val activeUid: () -> String?) : GoalRemote {
    private data class Running(val operation: GoalOperation, val task: Task<GoalRemoteOutcome>)
    private val running = mutableMapOf<Pair<String, String>, Running>()
    private val expiryChecks = mutableMapOf<Pair<String, String>, Running>()
    private fun parent(uid: String) = firestore.collection("users").document(uid).collection("savingsGoals").document("active")
    private fun goalFlow(uid: String): Flow<GoalDocument> = callbackFlow {
        val listener = parent(uid).addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (error != null) close(error)
            else if (doc != null) try {
                val pending = doc.metadata.hasPendingWrites()
                trySend(GoalDocument(if (doc.exists() && !pending) doc.goal() else null, doc.metadata.isFromCache, pending))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { close(e) }
        }
        awaitClose { listener.remove() }
    }
    private fun entriesFlow(uid: String): Flow<GoalEntries> = callbackFlow {
        // Fetch every entry, including malformed documents lacking a date, rather than letting orderBy exclude them.
        val listener = parent(uid).collection("entries").addSnapshotListener(MetadataChanges.INCLUDE) { docs, error ->
            if (error != null) close(error)
            else if (docs != null) try {
                val pending = docs.metadata.hasPendingWrites() || docs.documents.any { it.metadata.hasPendingWrites() }
                trySend(GoalEntries(if (pending) emptyList() else docs.documents.map { it.entry() }, docs.metadata.isFromCache, pending))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { close(e) }
        }
        awaitClose { listener.remove() }
    }
    override fun observe(uid: String): Flow<GoalSnapshot> = flow {
        var last = GoalSnapshot()
        combine(goalFlow(uid), entriesFlow(uid)) { doc, rows -> doc to rows }.collect { (doc, rows) ->
            val next = when {
                doc.pending || rows.pending -> last.copy(fromCache = true, error = "Goal changes await server confirmation. Retaining confirmed savings.")
                doc.goal == null -> if (doc.cached) last.copy(fromCache = true, error = "Goal is not available in cache. Connect to check for an existing goal.")
                    else if (rows.entries.isNotEmpty()) last.copy(error = "Goal and ledger are inconsistent; connect or retry loading.")
                    else GoalSnapshot(known = true, entries = emptyList(), fromCache = false)
                rows.cached && rows.entries.isEmpty() && last.entries == null -> GoalSnapshot(goal = doc.goal, known = true,
                    fromCache = true, error = "Allocation history is not available in cache. Saved balance is from the cached goal.")
                else -> {
                    val goal = doc.goal
                    val consistent = SavingsMath.ledgerBalance(goal.config.openingMinor, rows.entries) == goal.savedMinor &&
                        rows.entries.all { it.version <= goal.version } && (goal.lastEntryId.isEmpty() || rows.entries.any {
                            it.id == goal.lastEntryId && it.version == goal.version && it.lastOperationId == goal.lastOperationId })
                    if (!consistent) last.copy(fromCache = true, error = "Waiting for matching goal and ledger snapshots; previous confirmed values retained.")
                    else GoalSnapshot(goal, rows.entries, true, doc.cached || rows.cached)
                }
            }
            if (next.error == null) last = next
            emit(next)
        }
    }

    override suspend fun apply(uid: String, operation: GoalOperation): GoalRemoteOutcome {
        operation.validate(temporal = false)
        check(activeUid() == uid) { "Account changed. Saved goal operation belongs to the original account." }
        val key = uid to operation.operationId
        val (attempts, task) = synchronized(running) {
            val old = running[key]?.also { check(it.operation == operation) { "Running operation payload cannot change." } }
            // A financial retry shares the old Task. A finance-free expiry check may fence it safely.
            val fenceOnly = old != null && !old.task.isComplete && operation.goal?.let { SavingsMath.date(it.deadline) <= SavingsMath.today(clock) } == true
            val tasks = if (fenceOnly || expiryChecks.containsKey(key)) expiryChecks else running
            val current = tasks[key]?.also { check(it.operation == operation) { "Running operation payload cannot change." } }?.task
                ?: transaction(uid, operation, tasks === expiryChecks).also { tasks[key] = Running(operation, it) }
            tasks to current
        }
        task.addOnCompleteListener(java.util.concurrent.Executor { it.run() }) {
            synchronized(running) { if (attempts[key]?.task === task) attempts.remove(key) }
        }
        try { return task.await() }
        finally {
            // Await cancellation does not cancel Firebase. Keep an outstanding Task for the next waiter.
            if (task.isComplete) synchronized(running) { if (attempts[key]?.task === task) attempts.remove(key) }
        }
    }

    private fun transaction(uid: String, operation: GoalOperation, fenceOnly: Boolean = false): Task<GoalRemoteOutcome> {
        val goalRef = parent(uid)
        val receiptRef = goalRef.collection("operations").document(operation.operationId)
        return firestore.runTransaction<GoalRemoteOutcome> { tx ->
            check(activeUid() == uid) { "Account changed. Reopen Savings Goal." }
            val receipt = tx.get(receiptRef)
            if (receipt.exists()) {
                check(receipt.getString("kind") == operation.kind && receipt.getString("entryId") == (operation.entry?.id ?: "") &&
                    receipt.getLong("version") == operation.nextVersion && receipt.get("payload") == operation.payload() &&
                    receipt.getTimestamp("createdAt") != null) { "Operation identity conflict. Keep the saved payload for inspection." }
                check(activeUid() == uid) { "Account changed." }
                return@runTransaction GoalRemoteOutcome.Confirmed
            }
            val expiryRef = operation.goal?.let { goalRef.collection("rejections").document(operation.operationId) }
            val expiry = expiryRef?.let { tx.get(it) }
            if (expiry?.exists() == true) {
                check(expiry.getString("kind") == operation.kind && expiry.getLong("version") == operation.nextVersion &&
                    expiry.get("payload") == operation.payload() && expiry.getTimestamp("createdAt") != null) { "Expiry marker identity conflict. Keep the original proposal." }
                return@runTransaction expiredDeadline()
            }
            val goalDoc = tx.get(goalRef)
            val before = if (goalDoc.exists()) goalDoc.goal() else null
            // These contradictions are permanent under the existing no-delete/monotonic-version rules.
            // Returning (rather than throwing) verifies the read set at commit. If an older attempt wins
            // first, verification retries this callback, which then reconciles its matching receipt.
            if (operation.kind == "CREATE" && before != null)
                return@runTransaction GoalRemoteOutcome.Rejected("A goal already exists. Reopen its latest details.")
            if (operation.kind != "CREATE" && before != null && before.version > operation.expectedVersion!!)
                return@runTransaction GoalRemoteOutcome.Rejected("Goal changed elsewhere. Reopen the latest version before making this change.")
            checkGoalVersion(operation, before)
            val entryRef = operation.entry?.let { goalRef.collection("entries").document(it.id) }
            val entryDoc = entryRef?.let { tx.get(it) }
            val oldEntry = if (entryDoc?.exists() == true) entryDoc.entry() else null
            // Only this pure balance/identity calculation is classified here. With the expected
            // parent version fixed and these ledger reads verified, its violations cannot be
            // repaired by an older outstanding attempt. Decode/access/SDK failures are not proofs.
            val saved = try { goalMutationBalance(operation, before, oldEntry) }
            catch (e: IllegalArgumentException) { return@runTransaction GoalRemoteOutcome.Rejected(e.message ?: "Invalid resulting allocation balance.") }
            catch (e: IllegalStateException) { return@runTransaction GoalRemoteOutcome.Rejected(e.message ?: "Entry conflicts with the confirmed ledger.") }
            if (operation.goal != null && before != null && operation.goal.startDate != before.config.startDate)
                return@runTransaction GoalRemoteOutcome.Rejected("Goal start date cannot change.")
            if (operation.goal != null && SavingsMath.date(operation.goal.deadline) <= SavingsMath.today(clock) &&
                (operation.kind == "CREATE" || operation.goal.deadline != before?.config?.deadline)) {
                check(activeUid() == uid) { "Account changed. Reopen Savings Goal." }
                // The clock only proposes this fence. Rules must establish expiry using request.time,
                // exclude an accepted receipt, and permanently block this operation before we return EXPIRED.
                tx.set(expiryRef!!, mapOf("kind" to operation.kind, "version" to operation.nextVersion,
                    "payload" to operation.payload(), "createdAt" to FieldValue.serverTimestamp()))
                return@runTransaction expiredDeadline()
            }
            if (fenceOnly) return@runTransaction GoalRemoteOutcome.Uncertain("An earlier transaction is outstanding; no expired deadline was established.")
            // Reconcile first: an acknowledged create remains acknowledged even after its deadline passes.
            operation.validate(today = SavingsMath.today(clock))
            if (operation.goal != null && before != null) {
                check(operation.goal.startDate == before.config.startDate) { "Goal start date cannot change." }
                SavingsMath.validate(operation.goal, SavingsMath.today(clock), before.config.deadline)
            }
            check(activeUid() == uid) { "Account changed. Reopen Savings Goal." }
            val configuration = operation.goal ?: before!!.config
            val parentData = configuration.values().toMutableMap().apply {
                put("savedMinor", saved); put("version", operation.nextVersion)
                put("lastOperationId", operation.operationId); put("lastEntryId", operation.entry?.id ?: "")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            if (before == null) { parentData["createdAt"] = FieldValue.serverTimestamp(); tx.set(goalRef, parentData) }
            else tx.update(goalRef, parentData)
            if (operation.entry != null) {
                val entryData = operation.entry.values().toMutableMap().apply {
                    put("version", operation.nextVersion); put("lastOperationId", operation.operationId)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                if (oldEntry == null) { entryData["createdAt"] = FieldValue.serverTimestamp(); tx.set(entryRef!!, entryData) }
                else tx.update(entryRef!!, entryData)
            }
            tx.set(receiptRef, mapOf("kind" to operation.kind, "entryId" to (operation.entry?.id ?: ""), "version" to operation.nextVersion,
                "payload" to operation.payload(), "createdAt" to FieldValue.serverTimestamp()))
            GoalRemoteOutcome.Confirmed
        }
    }
    private fun expiredDeadline() = GoalRemoteOutcome.Rejected("The proposed deadline expired. Revise the deadline; the original operation is blocked by its server expiry marker.", deadlineExpired = true)
    private fun DocumentSnapshot.string(field: String) = get(field) as? String ?: error("Missing or invalid $field.")
    private fun DocumentSnapshot.long(field: String) = get(field) as? Long ?: error("Missing or invalid integer $field.")
    private fun DocumentSnapshot.timestamp(field: String) = get(field) as? Timestamp ?: error("Missing resolved $field.")
    private fun DocumentSnapshot.goal(): SavingsGoal {
        val config = GoalConfig(string("name"), long("targetMinor"), long("openingMinor"), string("startDate"), string("deadline"))
        SavingsMath.validate(config, SavingsMath.today(clock), config.deadline)
        return SavingsGoal(config, long("savedMinor"), long("version"), string("lastOperationId"), string("lastEntryId"),
            timestamp("createdAt"), timestamp("updatedAt")).also { check(it.savedMinor >= 0 && it.version >= 0) { "Invalid saved goal balance/version." } }
    }
    private fun DocumentSnapshot.entry(): SavingsEntry = SavingsEntry(id, string("kind"), long("amountMinor"), string("entryDate"),
        string("note"), get("deleted") as? Boolean ?: error("Invalid deletion state."), long("version"), string("lastOperationId"),
        timestamp("createdAt"), timestamp("updatedAt")).also { SavingsMath.validate(it, SavingsMath.today(clock)); check(it.version > 0) }
}

internal fun checkGoalVersion(operation: GoalOperation, before: SavingsGoal?) {
    if (operation.kind == "CREATE") check(before == null) { "A goal already exists. Reopen it rather than creating another." }
    else check(before != null && before.version == operation.expectedVersion) { "Goal changed elsewhere. Reopen the latest version before making this change." }
}
internal fun goalMutationBalance(operation: GoalOperation, before: SavingsGoal?, oldEntry: SavingsEntry?): Long = when (operation.kind) {
    "CREATE" -> operation.goal!!.openingMinor
    "EDIT" -> SavingsMath.openingBalance(before!!, operation.goal!!.openingMinor)
    else -> {
        if (operation.kind == "ENTRY_CREATE") check(oldEntry == null) { "Entry identity already exists; deleted IDs cannot be reused." }
        else check(oldEntry != null && !oldEntry.deleted) { "Entry was deleted or changed elsewhere." }
        if (operation.kind == "ENTRY_DELETE") check(oldEntry!!.values() == operation.entry!!.copy(deleted = false).values()) { "Deletion payload changed." }
        SavingsMath.balance(before!!.savedMinor, oldEntry, operation.entry!!)
    }
}
