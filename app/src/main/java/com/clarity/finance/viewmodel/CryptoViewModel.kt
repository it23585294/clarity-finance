package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.CryptoReporting
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.CryptoOperation
import com.clarity.finance.data.repository.CryptoRepository
import com.clarity.finance.data.repository.CryptoSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class CryptoForm(
    val id: String = UUID.randomUUID().toString(),
    val direction: String = "gain",
    val amount: String = "",
    val date: String,
    val note: String = "",
    val original: CryptoResult? = null,
    val busy: Boolean = false,
    val savedOperation: CryptoOperation? = null,
    val error: String? = null
)

data class CryptoUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = true,
    val month: String,
    val cutoff: String,
    val canNextMonth: Boolean = false,
    val canPreviousMonth: Boolean = true,
    val canNextYear: Boolean = false,
    val canPreviousYear: Boolean = true,
    val monthNet: String = "0.00",
    val ytdNet: String = "0.00",
    val history: List<CryptoResult> = emptyList(),
    val unsettled: List<CryptoResult> = emptyList(),
    val cached: Boolean = true,
    val error: String? = null,
    val unknownOperationCount: Int = 0,
    val confirmedPendingIds: Set<String> = emptySet(),
    val form: CryptoForm? = null,
    val selected: CryptoResult? = null,
    val selectedHasAttempt: Boolean = false,
    val deleteTarget: CryptoResult? = null,
    val busy: Boolean = false
) {
    /** Feedback follows all unresolved operations, rather than the last Save callback. */
    val message: String? get() {
        val pending = unsettled.count { it.sync == CryptoSync.PENDING && it.id !in confirmedPendingIds }
        val rejected = unsettled.count { it.sync == CryptoSync.FAILED }
        return listOfNotNull(
            if (pending > 0) "$pending pending change${if (pending == 1) "" else "s"} awaiting server confirmation." else null,
            if (rejected > 0) "$rejected rejected change${if (rejected == 1) "" else "s"}. Review saved proposals." else null,
            if (unknownOperationCount > 0) "$unknownOperationCount operation${if (unknownOperationCount == 1) "" else "s"} with an unknown save outcome. Retry the same saved details." else null
        ).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
}

class CryptoViewModel(
    private val auth: AuthRepository,
    private val repository: CryptoRepository,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    private fun today() = CryptoReporting.today(clock)
    private var month = YearMonth.from(today())
    private val _state = MutableStateFlow(initial())
    val state = _state.asStateFlow()
    private var uid: String? = null
    private var generation = 0L
    private var observation: Job? = null
    private var action: Job? = null
    private var snapshot = CryptoSnapshot()
    private var deleteOperation: CryptoOperation? = null
    private var reportingDate = today()
    private val unknownOperations = mutableMapOf<String, CryptoOperation>()
    private val confirmedOperations = mutableSetOf<String>()

    private fun initial() = CryptoUiState(month = month.toString(), cutoff = CryptoReporting.cutoff(month.year, today()).toString(),
        canNextMonth = month < YearMonth.from(today()), canPreviousMonth = month > YearMonth.of(1, 1),
        canNextYear = month.year < today().year, canPreviousYear = month.year > 1)

    init {
        viewModelScope.launch {
            auth.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
                generation++
                observation?.cancel()
                action?.cancel()
                deleteOperation = null
                unknownOperations.clear()
                confirmedOperations.clear()
                uid = user?.uid
                month = YearMonth.from(today())
                reportingDate = today()
                snapshot = CryptoSnapshot()
                _state.value = initial().copy(signedIn = user != null, loading = user != null)
                if (user != null) observe()
            }
        }
    }

    private fun observe() {
        observation?.cancel()
        val owner = uid ?: return
        val token = generation
        val year = month.year
        reportingDate = today()
        val cutoff = CryptoReporting.cutoff(year, reportingDate).toString()
        observation = viewModelScope.launch {
            val watchdog = launch {
                delay(10000L)
                if (valid(owner, token) && !snapshot.loaded) _state.value = _state.value.copy(loading = false,
                    error = "Server data is unavailable. Saved proposals are retained; retry loading when connected.")
            }
            try {
                repository.observe(owner, year, cutoff).collect {
                    if (valid(owner, token) && month.year == year) {
                        snapshot = it
                        confirmedOperations.retainAll(it.attempts.map { attempt -> attempt.operationId }.toSet())
                        it.attempts.filter(::hasConfirmedOutcome).forEach { attempt -> confirmedOperations.add(attempt.operationId) }
                        val resolved = unknownOperations.filterValues(::hasRecordedOutcome).keys.toSet()
                        resolved.forEach { id -> unknownOperations.remove(id) }
                        val form = _state.value.form
                        if (form?.savedOperation?.operationId in resolved)
                            _state.value = _state.value.copy(form = form?.copy(error = null))
                        render()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (valid(owner, token)) _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load results.")
            } finally { watchdog.cancel() }
        }
    }

    private fun valid(owner: String, token: Long) = uid == owner && generation == token

    private fun hasRecordedOutcome(operation: CryptoOperation): Boolean =
        snapshot.attempts.any { it.operationId == operation.operationId && it.result.id == operation.result.id && it.kind == operation.kind } ||
            hasConfirmedOutcome(operation)

    private fun hasConfirmedOutcome(operation: CryptoOperation): Boolean = operation.operationId in confirmedOperations ||
        (snapshot.confirmedResults ?: snapshot.results).any {
                it.sync == CryptoSync.CONFIRMED && it.id == operation.result.id && it.lastOperationId == operation.operationId &&
                    it.direction == operation.result.direction && it.amountMinor == operation.result.amountMinor &&
                    it.resultDate == operation.result.resultDate && it.note == operation.result.note
            }

    private fun render() {
        val current = _state.value
        val confirmed = snapshot.confirmedResults ?: snapshot.results
        val selected = current.selected?.let { old ->
            confirmed.firstOrNull { it.id == old.id } ?: snapshot.results.firstOrNull { it.id == old.id }
        }
        val totals = try {
            CryptoReporting.money(CryptoReporting.monthNet(confirmed, month)) to
                CryptoReporting.money(CryptoReporting.net(confirmed, LocalDate.of(month.year, 1, 1), CryptoReporting.cutoff(month.year, reportingDate)))
        } catch (_: ArithmeticException) { null }
        _state.value = current.copy(loading = !snapshot.loaded && snapshot.error == null && snapshot.results.isEmpty(),
            month = month.toString(), cutoff = CryptoReporting.cutoff(month.year, reportingDate).toString(),
            canNextMonth = month < YearMonth.from(today()), canPreviousMonth = month > YearMonth.of(1, 1),
            canNextYear = month.year < today().year, canPreviousYear = month.year > 1,
            monthNet = totals?.first ?: "Unavailable", ytdNet = totals?.second ?: "Unavailable", selected = selected,
            selectedHasAttempt = snapshot.attempts.any { it.result.id == selected?.id },
            history = confirmed.filter { it.resultDate.startsWith(month.toString()) },
            unsettled = snapshot.results.filter { it.sync in listOf(CryptoSync.PENDING, CryptoSync.FAILED) },
            unknownOperationCount = unknownOperations.size,
            confirmedPendingIds = snapshot.attempts.filter { it.operationId in confirmedOperations }.map { it.result.id }.toSet(),
            cached = snapshot.fromCache, error = if (totals == null)
                "Totals exceed the supported integer range. Inspect individual results." else snapshot.error)
    }

    fun changeMonth(delta: Long) {
        if (_state.value.form != null || _state.value.busy) return
        val next = month.plusMonths(delta)
        if (next.year !in 1..today().year || next > YearMonth.from(today())) return
        val yearChanged = next.year != month.year
        month = next
        _state.value = _state.value.copy(selected = null, deleteTarget = null)
        if (yearChanged) {
            generation++
            snapshot = CryptoSnapshot()
            _state.value = initial().copy(signedIn = uid != null, unknownOperationCount = unknownOperations.size,
                unsettled = _state.value.unsettled, confirmedPendingIds = _state.value.confirmedPendingIds)
            observe()
        } else render()
    }

    fun changeYear(delta: Int) {
        val year = month.year + delta
        if (year !in 1..today().year) return
        val target = YearMonth.of(year, month.monthValue).coerceAtMost(YearMonth.from(today()))
        changeMonth(java.time.temporal.ChronoUnit.MONTHS.between(month, target))
    }

    fun refreshReportingDate() {
        if (uid == null || reportingDate == today() || _state.value.busy || _state.value.form != null) return
        generation++
        month = month.coerceAtMost(YearMonth.from(today()))
        snapshot = CryptoSnapshot()
        _state.value = initial().copy(signedIn = true, unknownOperationCount = unknownOperations.size,
            unsettled = _state.value.unsettled, confirmedPendingIds = _state.value.confirmedPendingIds)
        observe()
    }
    fun retryLoad() { if (uid != null) { _state.value = _state.value.copy(error = null, loading = true); observe() } }
    fun inspect(result: CryptoResult) { _state.value = _state.value.copy(selected = result,
        selectedHasAttempt = snapshot.attempts.any { it.result.id == result.id }) }
    fun closeDetails() { _state.value = _state.value.copy(selected = null) }

    fun add() {
        refreshReportingDate()
        if (uid != null && !_state.value.busy) _state.value = _state.value.copy(form = CryptoForm(date = today().toString()), selected = null)
    }

    fun edit(result: CryptoResult) {
        if (uid == null || result.sync != CryptoSync.CONFIRMED || result.updatedAt == null || _state.value.busy ||
            snapshot.attempts.any { it.result.id == result.id }) return
        _state.value = _state.value.copy(form = CryptoForm(id = result.id, direction = result.direction,
            amount = BigDecimal.valueOf(result.amountMinor, 2).toPlainString(), date = result.resultDate,
            note = result.note, original = result), selected = null)
    }

    fun updateForm(direction: String? = null, amount: String? = null, date: String? = null, note: String? = null) {
        val form = _state.value.form ?: return
        if (form.busy || form.savedOperation != null) return
        _state.value = _state.value.copy(form = form.copy(direction = direction ?: form.direction, amount = amount ?: form.amount,
            date = date ?: form.date, note = note ?: form.note, error = null))
    }

    fun closeForm() { if (_state.value.form?.busy != true) _state.value = _state.value.copy(form = null) }

    fun save() {
        val form = _state.value.form ?: return
        if (form.busy || _state.value.busy) return
        val operation = try {
            form.savedOperation ?: run {
                val amount = CryptoReporting.amount(form.amount)
                CryptoReporting.validate(form.direction, amount, form.date, form.note, today())
                val result = CryptoResult(form.id, form.direction, amount, form.date, form.note,
                    createdAt = form.original?.createdAt, updatedAt = form.original?.updatedAt)
                CryptoOperation(UUID.randomUUID().toString(), if (form.original == null) "CREATE" else "UPDATE", result, form.original?.updatedAt)
            }
        } catch (e: IllegalArgumentException) {
            _state.value = _state.value.copy(form = form.copy(error = e.message)); return
        }
        _state.value = _state.value.copy(form = form.copy(busy = true, savedOperation = operation, error = null))
        perform(operation, formSubmission = true)
    }

    fun requestDelete(result: CryptoResult) {
        if (result.sync == CryptoSync.CONFIRMED && result.updatedAt != null && !_state.value.busy &&
            snapshot.attempts.none { it.result.id == result.id }) {
            deleteOperation = CryptoOperation(UUID.randomUUID().toString(), "DELETE", result, result.updatedAt)
            _state.value = _state.value.copy(deleteTarget = result)
        }
    }
    fun dismissDelete() { if (!_state.value.busy) { deleteOperation = null; _state.value = _state.value.copy(deleteTarget = null) } }
    fun confirmDelete() {
        val result = _state.value.deleteTarget ?: return
        if (_state.value.busy) return
        // Keep this exact operation even if saving it locally times out.
        val existing = snapshot.attempts.firstOrNull { it.result.id == result.id }
        perform(existing ?: deleteOperation ?: return)
    }

    fun retry(result: CryptoResult) {
        snapshot.attempts.firstOrNull { it.result.id == result.id }?.let { perform(it, retry = true) }
    }
    fun dismissRejected(result: CryptoResult) {
        snapshot.attempts.firstOrNull { it.result.id == result.id }?.let { perform(it, dismiss = true) }
    }

    private fun perform(operation: CryptoOperation, formSubmission: Boolean = false, retry: Boolean = false, dismiss: Boolean = false) {
        if (_state.value.busy) return
        val owner = uid ?: return
        val token = generation
        _state.value = _state.value.copy(busy = true, error = null)
        action = viewModelScope.launch {
            try {
                val accepted = withTimeoutOrNull(10000L) {
                    check(auth.authState.first()?.uid == owner && valid(owner, token)) { "Account changed. Reopen Crypto Results." }
                    when {
                        dismiss -> repository.dismissRejected(owner, operation)
                        retry -> repository.retry(owner, operation)
                        else -> repository.submit(owner, operation)
                    }
                    true
                } ?: false
                if (!valid(owner, token)) return@launch
                if (accepted) {
                    unknownOperations.remove(operation.operationId)
                    _state.value = _state.value.copy(busy = false, form = if (formSubmission) null else _state.value.form,
                        selected = null, deleteTarget = null, unknownOperationCount = unknownOperations.size)
                } else {
                    // An empty query is not evidence that a timed-out write failed or was cancelled.
                    val unknown = !hasRecordedOutcome(operation)
                    if (unknown) unknownOperations[operation.operationId] = operation
                    else unknownOperations.remove(operation.operationId)
                    _state.value = _state.value.copy(busy = false, unknownOperationCount = unknownOperations.size,
                        form = _state.value.form?.copy(busy = false, error = if (unknown) "Local save outcome is unknown. Retry these same details." else null),
                        error = if (unknown) "Save outcome unknown. Reopen recovery before making another change." else snapshot.error)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (valid(owner, token)) _state.value = _state.value.copy(busy = false,
                    form = _state.value.form?.copy(busy = false, error = e.message), error = e.message)
            }
        }
    }
}
