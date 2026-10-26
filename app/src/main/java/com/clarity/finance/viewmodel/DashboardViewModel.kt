package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth

data class DashboardUiState(
    val signedIn: Boolean = false,
    val month: String,
    val contextVersion: Long = 0,
    val canPrevious: Boolean = true,
    val canNext: Boolean = false,
    val selectedCategory: String? = null,
    val expenses: ReportingBatch<Expense> = ReportingBatch(),
    val income: ReportingBatch<Income> = ReportingBatch(),
    val crypto: ReportingBatch<CryptoResult> = ReportingBatch(),
    val projects: ReportingBatch<ExpectedPayment> = ReportingBatch(),
    val spending: SpendingSummary? = null,
    val incomeText: String? = null,
    val expensesText: String? = null,
    val surplusText: String? = null,
    val surplusError: String? = null,
    val cryptoText: String? = null,
    val pendingAmounts: Map<String, String>? = null,
    val expenseHistory: List<Expense> = emptyList(),
    val incomeHistory: List<Income> = emptyList(),
    val recovery: ReportingRecovery? = null,
    val recoveryError: String? = null
)

class DashboardViewModel(
    private val auth: AuthRepository,
    private val repository: ReportingRepository,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    private fun currentMonth() = YearMonth.from(CryptoReporting.today(clock))
    private var month = currentMonth()
    private var uid: String? = null
    private var generation = 0L
    private var listeners: Job? = null
    private val _state = MutableStateFlow(DashboardUiState(month = month.toString()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
                uid = user?.uid
                month = currentMonth()
                restart()
            }
        }
    }

    fun changeMonth(delta: Long) {
        val target = month.plusMonths(delta)
        if (uid == null || target.year < 1 || target > currentMonth()) return
        month = target
        restart()
    }
    fun selectCategory(id: String?) {
        if (id != null && id !in ExpenseCategories.labels) return
        _state.value = _state.value.copy(selectedCategory = id)
        render()
    }
    fun refreshDate() { render() }
    fun retry() { restart(clear = false) }

    private fun restart(clear: Boolean = true) {
        generation++
        listeners?.cancel()
        val owner = uid
        val token = generation
        if (clear) _state.value = DashboardUiState(signedIn = owner != null, month = month.toString(), contextVersion = token,
            canPrevious = month > YearMonth.of(1, 1), canNext = month < currentMonth())
        if (owner == null) return
        val period = ReportingMonth(month)
        listeners = viewModelScope.launch {
            fun valid() = uid == owner && generation == token
            fun <T> watch(source: () -> Flow<ReportingBatch<T>>, read: () -> ReportingBatch<T>, write: (ReportingBatch<T>) -> Unit) = launch {
                while (isActive && valid()) {
                    val watchdog = launch {
                        delay(10000L)
                        if (valid() && read().records == null) {
                            write(read().copy(error = "Data is unavailable. Connect or retry loading.")); render()
                        }
                    }
                    try {
                        source().collect { batch ->
                            if (valid()) {
                                // A restarted listener cannot erase a previously clean contribution with a local proposal.
                                write(if (batch.awaitingWrites && batch.records == null) batch.copy(records = read().records) else batch)
                                render()
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (valid()) { write(read().copy(error = e.message ?: "Listener unavailable.", fromCache = true)); render() }
                    } finally { watchdog.cancel() }
                    delay(5000L)
                }
            }
            watch({ repository.expenses(owner, period) }, { _state.value.expenses }) { _state.value = _state.value.copy(expenses = it) }
            watch({ repository.income(owner, period) }, { _state.value.income }) { _state.value = _state.value.copy(income = it) }
            watch({ repository.crypto(owner, period) }, { _state.value.crypto }) { _state.value = _state.value.copy(crypto = it) }
            watch({ repository.projects(owner) }, { _state.value.projects }) { _state.value = _state.value.copy(projects = it) }
            launch {
                while (isActive && valid()) {
                    try {
                        repository.recovery(owner).collect {
                            if (valid()) _state.value = _state.value.copy(recovery = it, recoveryError = null)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (valid()) _state.value = _state.value.copy(recoveryError = e.message ?: "Saved changes unavailable.")
                    }
                    delay(5000L)
                }
            }
        }
    }

    private fun render() {
        val old = _state.value
        val period = ReportingMonth(month)
        fun <T, R> calculate(batch: ReportingBatch<T>, calculation: (List<T>) -> R): Pair<ReportingBatch<T>, R?> {
            if (batch.records == null || batch.error != null) return batch to null
            return try { batch to calculation(batch.records) }
            catch (_: ArithmeticException) { batch.copy(error = "Total exceeds the supported integer range; inspect individual records.") to null }
            catch (e: Exception) { batch.copy(error = e.message ?: "Invalid reporting data.") to null }
        }
        val (expenses, spending) = calculate(old.expenses) { MonthlyReporting.expenses(it, period) }
        val (income, received) = calculate(old.income) { MonthlyReporting.income(it, period) }
        val (crypto, cryptoNet) = calculate(old.crypto) { MonthlyReporting.crypto(it, period) }
        val (projects, pending) = calculate(old.projects, MonthlyReporting::pending)
        var surplusError: String? = null
        val surplus = if (received == null || spending == null) null else try {
            MonthlyReporting.money(Math.subtractExact(received, spending.totalMinor))
        } catch (_: ArithmeticException) { surplusError = "Surplus exceeds the supported integer range."; null }
        _state.value = old.copy(expenses = expenses, income = income, crypto = crypto, projects = projects,
            canPrevious = month > YearMonth.of(1, 1), canNext = month < currentMonth(),
            spending = spending, incomeText = received?.let(MonthlyReporting::money),
            expensesText = spending?.totalMinor?.let(MonthlyReporting::money), surplusText = surplus, surplusError = surplusError,
            cryptoText = cryptoNet?.let(CryptoReporting::money), pendingAmounts = pending?.mapValues { MonthlyReporting.money(it.value) },
            expenseHistory = expenses.records.orEmpty().filter { old.selectedCategory == null || it.categoryId == old.selectedCategory }
                .sortedWith(compareByDescending<Expense> { it.expenseDate }.thenBy { it.id }),
            incomeHistory = income.records.orEmpty().sortedWith(compareByDescending<Income> { it.receiptDate }.thenBy { it.id }))
    }
}
