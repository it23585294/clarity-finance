package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.util.UUID

data class GoalForm(val name: String = "", val target: String = "", val opening: String = "0.00", val deadline: String = "",
    val entryId: String? = null, val kind: String = "contribution", val amount: String = "", val date: String = "", val note: String = "",
    val baseVersion: Long? = null)
data class GoalUiState(val signedIn: Boolean = false, val snapshot: GoalSnapshot = GoalSnapshot(), val progress: GoalProgress? = null,
    val form: GoalForm? = null, val formKind: String? = null, val deleteEntry: SavingsEntry? = null, val deleteVersion: Long? = null,
    val busy: Boolean = false, val error: String? = null, val unknown: GoalOperation? = null, val localPending: GoalOperation? = null) {
    val blocked get() = busy || unknown != null || localPending != null || snapshot.attempts.isNotEmpty() || snapshot.recoveryError != null
    val canChange get() = signedIn && snapshot.known && snapshot.error == null && snapshot.entries != null && !blocked
}

/** Dashboard observes only. Synchronization is deliberately started only by the allocation screen. */
open class GoalViewModel(private val auth: AuthRepository, private val repository: GoalRepository,
    private val clock: Clock = Clock.systemUTC(), private val synchronize: Boolean = true) : ViewModel() {
    private var uid: String? = null
    private var generation = 0L
    private var listener: Job? = null
    private val mutable = MutableStateFlow(GoalUiState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            auth.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
                uid = user?.uid; generation++; listener?.cancel()
                mutable.value = GoalUiState(signedIn = uid != null)
                val owner = uid ?: return@collect
                val token = generation
                listener = launch {
                    launch {
                        while (isActive) {
                            try { repository.observe(owner).collect { snapshot ->
                                if (valid(owner, token)) {
                                    mutable.value = mutable.value.copy(snapshot = snapshot,
                                        unknown = mutable.value.unknown?.takeUnless { observed(snapshot, it) },
                                        localPending = mutable.value.localPending?.takeUnless { observed(snapshot, it) })
                                    refreshDate()
                                }
                            } } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { if (valid(owner, token)) mutable.value = mutable.value.copy(
                                snapshot = mutable.value.snapshot.copy(fromCache = true, error = e.message ?: "Goal unavailable.")) }
                            delay(5000)
                        }
                    }
                    if (synchronize) launch {
                        while (isActive) {
                            try { repository.synchronize(owner) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { if (valid(owner, token)) mutable.value = mutable.value.copy(error = "Recovery paused: ${e.message}") }
                            delay(5000)
                        }
                    }
                    launch { while (isActive) {
                        val midnight = SavingsMath.today(clock).plusDays(1).atStartOfDay(CryptoReporting.zone).toInstant()
                        delay(java.time.Duration.between(clock.instant(), midnight).toMillis().coerceIn(1, 60000))
                        if (valid(owner, token)) refreshDate()
                    } }
                    launch { delay(10000); if (valid(owner, token) && !mutable.value.snapshot.known)
                        mutable.value = mutable.value.copy(snapshot = mutable.value.snapshot.copy(error = "Goal is unavailable. Reconnect to load; no zero balance is assumed.")) }
                }
            }
        }
    }
    private fun valid(owner: String, token: Long) = owner == uid && token == generation
    private fun observed(snapshot: GoalSnapshot, operation: GoalOperation) = snapshot.attempts.any { it.operation == operation } ||
        snapshot.acknowledgedOperationId == operation.operationId || snapshot.goal?.lastOperationId == operation.operationId ||
        snapshot.entries.orEmpty().any { it.lastOperationId == operation.operationId }
    fun refreshDate() {
        try { mutable.value = mutable.value.copy(progress = mutable.value.snapshot.goal?.let { SavingsMath.progress(it, SavingsMath.today(clock)) }) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = mutable.value.copy(progress = null, error = e.message ?: "Goal calculation unavailable.") }
    }
    fun openGoal() {
        if (!state.value.canChange) return
        val config = state.value.snapshot.goal?.config
        mutable.value = state.value.copy(formKind = "goal", form = config?.let {
            GoalForm(it.name, SavingsMath.money(it.targetMinor), SavingsMath.money(it.openingMinor), it.deadline, baseVersion = state.value.snapshot.goal?.version)
        } ?: GoalForm(), error = null)
    }
    fun openEntry(entry: SavingsEntry? = null) {
        if (!state.value.canChange || state.value.snapshot.goal == null || entry?.deleted == true) return
        if (entry != null && state.value.snapshot.entries.orEmpty().none { it == entry }) return
        mutable.value = state.value.copy(formKind = "entry", form = GoalForm(entryId = entry?.id,
            kind = entry?.kind ?: "contribution", amount = entry?.let { SavingsMath.money(it.amountMinor) } ?: "",
            date = entry?.entryDate ?: SavingsMath.today(clock).toString(), note = entry?.note ?: "", baseVersion = state.value.snapshot.goal?.version), error = null)
    }
    fun update(form: GoalForm) { if (!state.value.busy && state.value.unknown == null) mutable.value = state.value.copy(form = form, error = null) }
    fun closeForm() { if (!state.value.busy) mutable.value = state.value.copy(form = null, formKind = null, error = null) }
    fun requestDelete(entry: SavingsEntry) {
        if (state.value.canChange && state.value.snapshot.entries.orEmpty().contains(entry) && !entry.deleted)
            mutable.value = state.value.copy(deleteEntry = entry, deleteVersion = state.value.snapshot.goal?.version, error = null)
    }
    fun cancelDelete() { if (!state.value.busy) mutable.value = state.value.copy(deleteEntry = null) }
    fun save() {
        if (!state.value.canChange) return
        val form = state.value.form ?: return
        try {
            val goal = state.value.snapshot.goal
            check(goal?.version == form.baseVersion) { "Goal changed while this form was open. Close it and reopen the latest version." }
            val op = if (state.value.formKind == "goal") {
                val config = GoalConfig(form.name.trim(), SavingsMath.amount(form.target), SavingsMath.amount(form.opening, true),
                    goal?.config?.startDate ?: SavingsMath.today(clock).toString(), form.deadline)
                SavingsMath.validate(config, SavingsMath.today(clock), goal?.config?.deadline)
                if (goal != null) SavingsMath.openingBalance(goal, config.openingMinor)
                GoalOperation(UUID.randomUUID().toString(), if (goal == null) "CREATE" else "EDIT", goal?.version,
                    goal = config, previousDeadline = goal?.config?.deadline)
            } else {
                requireNotNull(goal)
                val entry = SavingsEntry(form.entryId ?: UUID.randomUUID().toString(), form.kind, SavingsMath.amount(form.amount), form.date, form.note)
                SavingsMath.validate(entry, SavingsMath.today(clock))
                val before = state.value.snapshot.entries.orEmpty().find { it.id == entry.id }
                SavingsMath.balance(goal.savedMinor, before, entry)
                GoalOperation(UUID.randomUUID().toString(), if (before == null) "ENTRY_CREATE" else "ENTRY_UPDATE", goal.version, entry = entry)
            }
            submit(op)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = state.value.copy(error = e.message ?: "Check the entered values.") }
    }
    fun confirmDelete() {
        if (!state.value.canChange) return
        val entry = state.value.deleteEntry ?: return
        val goal = state.value.snapshot.goal ?: return
        try {
            check(goal.version == state.value.deleteVersion) { "Goal changed. Close this confirmation and inspect the latest entry." }
            SavingsMath.balance(goal.savedMinor, entry, entry.copy(deleted = true))
            submit(GoalOperation(UUID.randomUUID().toString(), "ENTRY_DELETE", goal.version, entry = entry.copy(deleted = true)))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = state.value.copy(error = e.message) }
    }
    private fun submit(operation: GoalOperation) = action {
        val token = generation
        // Retain identity even if the local persistence await times out or is cancelled.
        mutable.value = state.value.copy(unknown = operation)
        val persisted = withTimeoutOrNull(10000) { repository.submit(it, operation); true } ?: false
        if (!valid(it, token)) return@action
        val seen = observed(state.value.snapshot, operation)
        mutable.value = state.value.copy(unknown = if (persisted || seen) null else operation,
            localPending = if (persisted && !seen) operation else null,
            form = if (persisted) null else state.value.form, formKind = if (persisted) null else state.value.formKind,
            deleteEntry = if (persisted) null else state.value.deleteEntry)
    }
    fun retryUnknown() { state.value.unknown?.let { submit(it) } }
    fun retry(attempt: GoalAttempt) { if (state.value.snapshot.attempts.contains(attempt)) action { repository.retry(it, attempt.operation) } }
    fun dismiss(attempt: GoalAttempt) { if (attempt.isRejected && state.value.snapshot.attempts.contains(attempt)) action { repository.dismissRejected(it, attempt.operation) } }
    private fun action(block: suspend (String) -> Unit) {
        if (state.value.busy) return
        val owner = uid ?: return
        val token = generation
        mutable.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block(owner) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid(owner, token)) mutable.value = state.value.copy(error = e.message ?: "Change unavailable.") }
            finally { if (valid(owner, token)) mutable.value = state.value.copy(busy = false) }
        }.also { job -> listener?.invokeOnCompletion { job.cancel() } }
    }
}

class GoalCardViewModel(auth: AuthRepository, repository: GoalRepository) : GoalViewModel(auth, repository, synchronize = false)
