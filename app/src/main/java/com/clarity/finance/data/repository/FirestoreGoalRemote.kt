package com.clarity.finance.data.repository

import com.clarity.finance.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

private data class GoalDocument(val goal: SavingsGoal?, val cached: Boolean, val pending: Boolean)
private data class GoalEntries(val entries: List<SavingsEntry>, val cached: Boolean, val pending: Boolean)

class FirestoreGoalRemote(private val firestore: FirebaseFirestore, private val activeUid: () -> String?) : GoalRemote {
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

    override suspend fun apply(uid: String, operation: GoalOperation) {
        operation.validate(temporal = false)
        check(activeUid() == uid) { "Account changed. Saved goal operation belongs to the original account." }
        val goalRef = parent(uid)
        val receiptRef = goalRef.collection("operations").document(operation.operationId)
        firestore.runTransaction { tx ->
            check(activeUid() == uid) { "Account changed. Reopen Savings Goal." }
            val receipt = tx.get(receiptRef)
            if (receipt.exists()) {
                check(receipt.getString("kind") == operation.kind && receipt.getString("entryId") == (operation.entry?.id ?: "") &&
                    receipt.getLong("version") == operation.nextVersion && receipt.get("payload") == operation.payload() &&
                    receipt.getTimestamp("createdAt") != null) { "Operation identity conflict. Keep the saved payload for inspection." }
                check(activeUid() == uid) { "Account changed." }
                return@runTransaction
            }
            val goalDoc = tx.get(goalRef)
            val before = if (goalDoc.exists()) goalDoc.goal() else null
            checkGoalVersion(operation, before)
            val entryRef = operation.entry?.let { goalRef.collection("entries").document(it.id) }
            val entryDoc = entryRef?.let { tx.get(it) }
            val oldEntry = if (entryDoc?.exists() == true) entryDoc.entry() else null
            val saved = goalMutationBalance(operation, before, oldEntry)
            // Reconcile first: an acknowledged create remains acknowledged even after its deadline passes.
            operation.validate()
            if (operation.goal != null && before != null) {
                check(operation.goal.startDate == before.config.startDate) { "Goal start date cannot change." }
                SavingsMath.validate(operation.goal, SavingsMath.today(), before.config.deadline)
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
        }.await()
    }
    private fun DocumentSnapshot.string(field: String) = get(field) as? String ?: error("Missing or invalid $field.")
    private fun DocumentSnapshot.long(field: String) = get(field) as? Long ?: error("Missing or invalid integer $field.")
    private fun DocumentSnapshot.timestamp(field: String) = get(field) as? Timestamp ?: error("Missing resolved $field.")
    private fun DocumentSnapshot.goal(): SavingsGoal {
        val config = GoalConfig(string("name"), long("targetMinor"), long("openingMinor"), string("startDate"), string("deadline"))
        SavingsMath.validate(config, SavingsMath.today(), config.deadline)
        return SavingsGoal(config, long("savedMinor"), long("version"), string("lastOperationId"), string("lastEntryId"),
            timestamp("createdAt"), timestamp("updatedAt")).also { check(it.savedMinor >= 0 && it.version >= 0) { "Invalid saved goal balance/version." } }
    }
    private fun DocumentSnapshot.entry(): SavingsEntry = SavingsEntry(id, string("kind"), long("amountMinor"), string("entryDate"),
        string("note"), get("deleted") as? Boolean ?: error("Invalid deletion state."), long("version"), string("lastOperationId"),
        timestamp("createdAt"), timestamp("updatedAt")).also { SavingsMath.validate(it, SavingsMath.today()); check(it.version > 0) }
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
