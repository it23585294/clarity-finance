package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

data class RecurringForm(val kind: String, val templateId: String, val baseVersion: Long? = null,
    val name: String = "", val amount: String = "", val categoryId: String = "housing", val spendingType: String = "committed",
    val paymentChannelId: String = "cash", val firstDueDate: String = "", val anchorDay: String = "", val month: String? = null,
    val dueDate: String? = null, val actualDate: String = "", val note: String = "")
data class RecurringUiState(val signedIn: Boolean = false, val templates: ReportingBatch<RecurringTemplate> = ReportingBatch(),
    val templateLimit: Int = 25, val selectedId: String? = null, val windowStart: String? = null,
    val occurrences: ReportingBatch<ConfirmedOccurrence> = ReportingBatch(), val slots: List<RecurringSlot> = emptyList(),
    val recovery: RecurringRecovery? = null, val form: RecurringForm? = null, val cancel: RecurringTemplate? = null,
    val busy: Boolean = false, val error: String? = null, val unknown: RecurringOperation? = null,
    val local: Map<String, RecurringOperation> = emptyMap(), val today: String = "") {
    val selected get() = templates.records?.find { it.id == selectedId }
    val canPrepare get() = signedIn && !busy && unknown == null && recovery != null && recovery.error == null
    fun blocked(recordId: String) = local.values.any { it.recordId == recordId } || recovery?.attempts.orEmpty().any { it.operation.recordId == recordId }
}

class RecurringViewModel(private val auth: AuthRepository, private val repository: RecurringRepository,
    private val clock: Clock = Clock.systemUTC(), private val synchronize: Boolean = true) : ViewModel() {
    private val mutable = MutableStateFlow(RecurringUiState(today = Recurrence.today(clock).toString()))
    val state = mutable.asStateFlow()
    private var uid: String? = null
    private var generation = 0L
    private var listToken = 0L
    private var targetToken = 0L
    private var accountJob: Job? = null
    private var templatesJob: Job? = null
    private var occurrencesJob: Job? = null
    init {
        viewModelScope.launch {
            auth.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
                generation++; uid = user?.uid; accountJob?.cancel(); templatesJob?.cancel(); occurrencesJob?.cancel()
                mutable.value = RecurringUiState(signedIn = uid != null, today = Recurrence.today(clock).toString())
                val owner = uid ?: return@collect
                val token = generation
                accountJob = launch {
                    launch {
                        while (isActive) {
                            try { repository.recovery(owner).collect { recovery ->
                                if (valid(owner, token)) {
                                    mutable.value = state.value.copy(recovery = recovery)
                                    clearObserved()
                                }
                            } } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { if (valid(owner, token)) mutable.value = state.value.copy(recovery = RecurringRecovery(error = e.message ?: "Recovery unavailable.")) }
                            delay(5000)
                        }
                    }
                    if (synchronize) launch {
                        while (isActive) {
                            try { repository.synchronize(owner) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { if (valid(owner, token)) mutable.value = state.value.copy(error = "Recovery paused: ${e.message}") }
                            delay(5000)
                        }
                    }
                    launch { while (isActive) { delay(60000); if (valid(owner, token)) refreshDate() } }
                    launch {
                        delay(10000)
                        if (valid(owner, token) && state.value.templates.records == null && state.value.templates.error == null)
                            mutable.value = state.value.copy(templates = state.value.templates.copy(error = "Templates unavailable. Reconnect; no empty account is assumed."))
                    }
                }
                watchTemplates()
            }
        }
    }
    private fun valid(owner: String, token: Long) = owner == uid && token == generation
    private fun clearObserved() {
        val snapshot = state.value
        fun observed(op: RecurringOperation) = snapshot.recovery?.attempts.orEmpty().any { it.operation == op } ||
            op.operationId in snapshot.recovery?.acknowledgedIds.orEmpty() || snapshot.templates.records.orEmpty().any { it.lastOperationId == op.operationId } ||
            snapshot.occurrences.records.orEmpty().any { it.confirmation == op.confirmation }
        mutable.value = snapshot.copy(unknown = snapshot.unknown?.takeUnless(::observed), local = snapshot.local.filterValues { !observed(it) })
    }
    private fun watchTemplates() {
        val owner = uid ?: return; val token = generation; val queryToken = ++listToken
        templatesJob?.cancel()
        templatesJob = viewModelScope.launch {
            while (isActive) {
                try { repository.templates(owner, state.value.templateLimit).collect { batch ->
                    if (valid(owner, token) && queryToken == listToken) {
                        mutable.value = state.value.copy(templates = batch); clearObserved(); refreshDate()
                    }
                } } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (valid(owner, token) && queryToken == listToken) mutable.value = state.value.copy(
                    templates = state.value.templates.copy(fromCache = true, error = "Templates unavailable: ${e.message}")) }
                delay(5000)
            }
        }
    }
    fun loadMore() {
        if (state.value.templateLimit <= Int.MAX_VALUE - 25) {
            mutable.value = state.value.copy(templateLimit = state.value.templateLimit + 25); watchTemplates()
        }
    }
    fun select(template: RecurringTemplate) {
        if (state.value.templates.records.orEmpty().none { it.id == template.id }) return
        mutable.value = state.value.copy(selectedId = template.id, windowStart = template.config.firstDueDate.take(7), form = null, cancel = null, error = null)
        watchOccurrences()
    }
    fun closeTemplate() {
        targetToken++; occurrencesJob?.cancel()
        mutable.value = state.value.copy(selectedId = null, windowStart = null, occurrences = ReportingBatch(), slots = emptyList(), form = null, cancel = null, error = null)
    }
    fun page(direction: Int) {
        val template = state.value.selected ?: return
        val start = Recurrence.month(state.value.windowStart ?: return).plusMonths(if (direction < 0) -12 else 12)
        if (start < YearMonth.from(SavingsMath.date(template.config.firstDueDate)) || start > YearMonth.from(Recurrence.today(clock))) return
        mutable.value = state.value.copy(windowStart = start.toString(), form = null, error = null); watchOccurrences()
    }
    private fun watchOccurrences() {
        val owner = uid ?: return; val template = state.value.selectedId ?: return; val start = state.value.windowStart ?: return
        val token = generation; val target = ++targetToken
        occurrencesJob?.cancel(); mutable.value = state.value.copy(occurrences = ReportingBatch(), slots = emptyList())
        refreshDate()
        occurrencesJob = viewModelScope.launch {
            launch {
                delay(10000)
                if (valid(owner, token) && target == targetToken && state.value.occurrences.records == null && state.value.occurrences.error == null)
                    mutable.value = state.value.copy(occurrences = state.value.occurrences.copy(error = "Confirmation history unavailable. Server reconciliation is required."))
            }
            while (isActive) {
                try { repository.occurrences(owner, template, start, YearMonth.parse(start).plusMonths(12).toString()).collect { batch ->
                    if (valid(owner, token) && target == targetToken) {
                        mutable.value = state.value.copy(occurrences = batch); clearObserved(); refreshDate()
                    }
                } } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (valid(owner, token) && target == targetToken) mutable.value = state.value.copy(
                    occurrences = state.value.occurrences.copy(fromCache = true, error = "Confirmation history unavailable: ${e.message}")) }
                delay(5000)
            }
        }
    }
    fun refreshDate() {
        val today = Recurrence.today(clock)
        try { mutable.value = state.value.copy(today = today.toString(), slots = state.value.selected?.let {
            Recurrence.slots(it, YearMonth.parse(state.value.windowStart), state.value.occurrences.records.orEmpty(), today)
        }.orEmpty()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = state.value.copy(slots = emptyList(), error = "Schedule unavailable: ${e.message}") }
    }
    fun newTemplate() {
        if (!state.value.canPrepare) return
        mutable.value = state.value.copy(form = RecurringForm("CREATE", UUID.randomUUID().toString(), firstDueDate = state.value.today,
            anchorDay = SavingsMath.date(state.value.today).dayOfMonth.toString()), error = null)
    }
    fun editTemplate() {
        val t = state.value.selected ?: return
        if (!state.value.canPrepare || t.isCancelled || state.value.blocked(t.id)) return
        mutable.value = state.value.copy(form = RecurringForm("EDIT", t.id, t.version, t.config.name, SavingsMath.money(t.config.suggestedMinor),
            t.config.categoryId, t.config.spendingType, t.config.paymentChannelId, t.config.firstDueDate, t.config.anchorDay.toString()), error = null)
    }
    fun confirm(slot: RecurringSlot) {
        val t = state.value.selected ?: return
        if (!state.value.canPrepare || t.isCancelled || slot.confirmed != null || state.value.blocked(slot.id) || slot !in state.value.slots) return
        mutable.value = state.value.copy(form = RecurringForm("CONFIRM", t.id, t.version, t.config.name, SavingsMath.money(t.config.suggestedMinor),
            t.config.categoryId, t.config.spendingType, t.config.paymentChannelId, month = slot.month, dueDate = slot.dueDate, actualDate = state.value.today), error = null)
    }
    fun update(form: RecurringForm) { if (!state.value.busy && state.value.unknown == null) mutable.value = state.value.copy(form = form, error = null) }
    fun closeForm() { if (!state.value.busy) mutable.value = state.value.copy(form = null, error = null) }
    fun requestCancel() {
        val t = state.value.selected ?: return
        if (state.value.canPrepare && !t.isCancelled && !state.value.blocked(t.id)) mutable.value = state.value.copy(cancel = t, error = null)
    }
    fun keepTemplate() { if (!state.value.busy) mutable.value = state.value.copy(cancel = null) }
    fun cancelTemplate() {
        val t = state.value.cancel ?: return
        if (!state.value.canPrepare) return
        if (state.value.selected?.version != t.version) { mutable.value = state.value.copy(error = "Template changed; reopen cancellation."); return }
        persist(RecurringOperation(UUID.randomUUID().toString(), "CANCEL", t.id, t.version, t.config))
    }
    fun save() {
        if (!state.value.canPrepare) return
        val f = state.value.form ?: return
        try {
            val op = if (f.kind == "CONFIRM") {
                check(state.value.selected?.version == f.baseVersion && state.value.selected?.isCancelled == false) { "Template changed; close and review the latest occurrence." }
                val actual = ActualRecurringExpense(SavingsMath.amount(f.amount), f.actualDate, f.categoryId, f.spendingType, f.paymentChannelId, f.note.trim())
                Recurrence.validate(actual, Recurrence.today(clock))
                RecurringOperation(UUID.randomUUID().toString(), f.kind, f.templateId, f.baseVersion,
                    confirmation = RecurringConfirmation(f.templateId, f.month!!, f.dueDate!!, f.baseVersion!!, f.name, actual))
            } else {
                if (f.kind == "EDIT") check(state.value.selected?.version == f.baseVersion && state.value.selected?.isCancelled == false) { "Template changed; reopen its current details." }
                val config = RecurringConfig(f.name.trim(), f.categoryId, f.spendingType, f.paymentChannelId, SavingsMath.amount(f.amount), f.firstDueDate,
                    f.anchorDay.toIntOrNull() ?: error("Enter monthly day 1–31."))
                Recurrence.validate(config)
                if (f.kind == "EDIT") check(config.firstDueDate == state.value.selected?.config?.firstDueDate) { "First due date is fixed." }
                RecurringOperation(UUID.randomUUID().toString(), f.kind, f.templateId, f.baseVersion, config)
            }
            if (state.value.blocked(op.recordId)) error("Resolve this record's saved proposal first. The original details are retained.")
            persist(op)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = state.value.copy(error = e.message ?: "Check the entered values.") }
    }
    private fun persist(op: RecurringOperation) = action { owner, token, target ->
        mutable.value = state.value.copy(unknown = op)
        val persisted = withTimeoutOrNull(10000) { repository.submit(owner, op); true } ?: false
        if (!valid(owner, token)) return@action
        if (persisted) mutable.value = state.value.copy(unknown = null, local = state.value.local + (op.operationId to op),
            form = if (target == targetToken) null else state.value.form, cancel = if (target == targetToken) null else state.value.cancel)
        clearObserved()
    }
    fun retryLocalSave() { state.value.unknown?.let(::persist) }
    fun dismiss(attempt: RecurringAttempt) {
        if (attempt.rejected && attempt in state.value.recovery?.attempts.orEmpty()) action { owner, _, _ -> repository.dismiss(owner, attempt.operation) }
    }
    private fun action(block: suspend (String, Long, Long) -> Unit) {
        if (state.value.busy) return
        val owner = uid ?: return; val token = generation; val target = targetToken
        mutable.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block(owner, token, target) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid(owner, token) && target == targetToken) mutable.value = state.value.copy(error = e.message ?: "Operation unavailable.") }
            finally { if (valid(owner, token)) mutable.value = state.value.copy(busy = false) }
        }.also { job -> accountJob?.invokeOnCompletion { job.cancel() } }
    }
}
