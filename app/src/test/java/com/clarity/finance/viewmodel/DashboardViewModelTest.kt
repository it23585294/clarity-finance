package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "a@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private val repo = FakeReporting()
    private val store = ViewModelStore()
    private lateinit var vm: DashboardViewModel
    private val expense = Expense("e", 150000, categoryId = "transport", paymentChannelId = "cash",
        spendingType = "committed", expenseDate = "2026-01-01")
    private val income = Income("i", "salary", "2026-01-01", "LKR", "1000", 100000, "native_lkr", "1", "")
    private fun expenseFlow(uid: String = "a", month: String = "2026-01") = repo.expenseFlows.getOrPut(uid to month) { MutableStateFlow(ReportingBatch(emptyList(), false)) }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = DashboardViewModel(auth, repo, Clock.fixed(Instant.parse("2025-12-31T18:30:00Z"), ZoneOffset.UTC))
        store.put("dashboard", vm)
    }
    @After fun cleanup() { repo.gate?.complete(Unit); store.clear(); Dispatchers.resetMain() }

    @Test fun `ordinary negative surplus is unchanged by crypto pending income or recovery proposals`() = runTest(dispatcher) {
        runCurrent()
        expenseFlow().value = ReportingBatch(listOf(expense), false)
        repo.incomes.value = listOf(income)
        repo.cryptos.value = listOf(CryptoResult("g", "gain", 6000000, "2026-01-01"), CryptoResult("l", "loss", 2500000, "2026-01-01"))
        repo.parents.value = listOf(ExpectedPayment("p", "project", "Test", "freelance", "USD", "2027-01-01", 8000000))
        repo.saved.value = ReportingRecovery(2, 1)
        runCurrent()
        assertEquals("1000.00", vm.state.value.incomeText)
        assertEquals("1500.00", vm.state.value.expensesText)
        assertEquals("-500.00", vm.state.value.surplusText)
        assertEquals("+35000.00", vm.state.value.cryptoText)
        assertEquals(mapOf("USD" to "80000.00"), vm.state.value.pendingAmounts)
        assertEquals(ReportingRecovery(2, 1), vm.state.value.recovery)
    }

    @Test fun `category drill down follows corrections and real time edit delete without reopening`() = runTest(dispatcher) {
        runCurrent()
        val other = expense.copy(id = "o", categoryId = "shopping", amountMinor = 50000, spendingType = "discretionary")
        expenseFlow().value = ReportingBatch(listOf(expense, other), false); runCurrent()
        vm.selectCategory("transport")
        assertEquals(listOf("e"), vm.state.value.expenseHistory.map { it.id })
        assertEquals("2000.00", vm.state.value.expensesText)
        expenseFlow().value = ReportingBatch(listOf(expense.copy(categoryId = "shopping"), other), false); runCurrent()
        assertTrue(vm.state.value.expenseHistory.isEmpty())
        assertEquals("2000.00", vm.state.value.expensesText)
        vm.selectCategory("shopping")
        assertEquals(2, vm.state.value.expenseHistory.size)
        expenseFlow().value = ReportingBatch(listOf(other.copy(amountMinor = 40000)), false); runCurrent()
        assertEquals("400.00", vm.state.value.expensesText)
        assertEquals(listOf("o"), vm.state.value.expenseHistory.map { it.id })
        expenseFlow().value = ReportingBatch(emptyList(), false); runCurrent()
        assertEquals("0.00", vm.state.value.expensesText)
        assertTrue(vm.state.value.expenseHistory.isEmpty())
    }

    @Test fun `month and year backdating replaces the correct bounded views`() = runTest(dispatcher) {
        runCurrent()
        expenseFlow().value = ReportingBatch(listOf(expense), false); runCurrent()
        expenseFlow(month = "2025-12").value = ReportingBatch(listOf(expense.copy(expenseDate = "2025-12-31")), false)
        expenseFlow().value = ReportingBatch(emptyList(), false); runCurrent()
        assertEquals("0.00", vm.state.value.expensesText)
        vm.changeMonth(-1); runCurrent()
        assertEquals("2025-12", vm.state.value.month)
        assertEquals("1500.00", vm.state.value.expensesText)
        assertEquals("2025-12-01", repo.queries.last().second.start)
        assertEquals("2026-01-01", repo.queries.last().second.endExclusive)
        vm.changeMonth(1); runCurrent()
        assertEquals("0.00", vm.state.value.expensesText)
        vm.changeMonth(1)
        assertEquals("2026-01", vm.state.value.month) // No future reporting month.
    }

    @Test fun `empty is distinct from unavailable and independent sections survive a listener failure and recover`() = runTest(dispatcher) {
        runCurrent()
        assertEquals("0.00", vm.state.value.surplusText)
        assertNull(vm.state.value.spending?.committedPercent)
        repo.incomeFailure = true
        vm.retry(); runCurrent()
        assertEquals("Income denied", vm.state.value.income.error)
        assertNull(vm.state.value.incomeText)
        assertNull(vm.state.value.surplusText)
        assertEquals("0.00", vm.state.value.expensesText)
        assertEquals("0.00", vm.state.value.cryptoText)
        repo.incomeFailure = false
        advanceTimeBy(5001); runCurrent()
        assertEquals("0.00", vm.state.value.surplusText)
        assertNull(vm.state.value.income.error)
        expenseFlow().value = ReportingBatch(error = "Not cached"); runCurrent()
        assertNull(vm.state.value.expensesText)
        assertNull(vm.state.value.surplusText)
        assertEquals("0.00", vm.state.value.incomeText)
    }

    @Test fun `cached pending and rejected changes retain original confirmed contribution`() = runTest(dispatcher) {
        runCurrent()
        expenseFlow().value = ReportingBatch(listOf(expense), true, awaitingWrites = true)
        repo.incomes.value = listOf(income)
        repo.saved.value = ReportingRecovery(1, 0); runCurrent()
        assertEquals("-500.00", vm.state.value.surplusText)
        assertTrue(vm.state.value.expenses.fromCache)
        assertTrue(vm.state.value.expenses.awaitingWrites)
        expenseFlow().value = ReportingBatch(listOf(expense), false)
        repo.saved.value = ReportingRecovery(0, 1); runCurrent()
        assertEquals("-500.00", vm.state.value.surplusText)
        assertEquals(1, vm.state.value.recovery?.rejected)
        expenseFlow().value = ReportingBatch(listOf(expense.copy(amountMinor = 50000)), false); runCurrent()
        assertEquals("500.00", vm.state.value.surplusText)
    }

    @Test fun `overflow leaves income pending and history usable without wrapped expense or surplus totals`() = runTest(dispatcher) {
        runCurrent()
        repo.incomes.value = listOf(income)
        expenseFlow().value = ReportingBatch(listOf(expense.copy(amountMinor = Long.MAX_VALUE), expense.copy(id = "extra", amountMinor = 1)), false)
        runCurrent()
        assertNull(vm.state.value.expensesText)
        assertNull(vm.state.value.spending)
        assertNull(vm.state.value.surplusText)
        assertTrue(vm.state.value.expenses.error!!.contains("integer range"))
        assertEquals(2, vm.state.value.expenseHistory.size)
        assertEquals("1000.00", vm.state.value.incomeText)
        expenseFlow().value = ReportingBatch(emptyList(), false); runCurrent()
        assertNull(vm.state.value.expenses.error)
        assertEquals("1000.00", vm.state.value.surplusText)
    }

    @Test fun `account switch clears category history recovery and rejects delayed old listener results`() = runTest(dispatcher) {
        repo.gate = CompletableDeferred()
        repo.late = ReportingBatch(listOf(expense), false)
        runCurrent()
        vm.selectCategory("transport")
        auth.authState.value = User("b", "b@example.com"); runCurrent()
        assertNull(vm.state.value.selectedCategory)
        assertTrue(vm.state.value.expenseHistory.isEmpty())
        assertEquals("0.00", vm.state.value.expensesText)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals("0.00", vm.state.value.expensesText)
        assertTrue(vm.state.value.expenseHistory.isEmpty())
        assertEquals(1, repo.oldListenerClosed)
        auth.authState.value = null; runCurrent()
        assertFalse(vm.state.value.signedIn)
        assertNull(vm.state.value.incomeText)
        assertNull(vm.state.value.recovery)
    }

    @Test fun `month switch rejects delayed old callbacks and cancels old listeners`() = runTest(dispatcher) {
        repo.gate = CompletableDeferred()
        repo.late = ReportingBatch(listOf(expense), false)
        runCurrent()
        vm.changeMonth(-1); runCurrent()
        assertEquals("2025-12", vm.state.value.month)
        assertEquals("0.00", vm.state.value.expensesText)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals("2025-12", vm.state.value.month)
        assertEquals("0.00", vm.state.value.expensesText)
        assertTrue(vm.state.value.expenseHistory.isEmpty())
        assertEquals(1, repo.oldListenerClosed)
    }

    private class FakeReporting : ReportingRepository {
        val expenseFlows = mutableMapOf<Pair<String, String>, MutableStateFlow<ReportingBatch<Expense>>>()
        val incomes = MutableStateFlow<List<Income>>(emptyList())
        val cryptos = MutableStateFlow<List<CryptoResult>>(emptyList())
        val parents = MutableStateFlow<List<ExpectedPayment>>(emptyList())
        val saved = MutableStateFlow(ReportingRecovery())
        val queries = mutableListOf<Pair<String, ReportingMonth>>()
        var incomeFailure = false
        var gate: CompletableDeferred<Unit>? = null
        var late: ReportingBatch<Expense>? = null
        var oldListenerClosed = 0
        override fun expenses(uid: String, month: ReportingMonth): Flow<ReportingBatch<Expense>> {
            queries += uid to month
            if (uid == "a" && month.month.toString() == "2026-01" && gate != null) return object : Flow<ReportingBatch<Expense>> {
                // Intentionally ignore cancellation to exercise stale-callback guards, without blocking a thread.
                override suspend fun collect(collector: FlowCollector<ReportingBatch<Expense>>) {
                    try { withContext(NonCancellable) { gate!!.await(); collector.emit(late!!) } }
                    finally { oldListenerClosed++ }
                }
            }
            return expenseFlows.getOrPut(uid to month.month.toString()) { MutableStateFlow(ReportingBatch(emptyList(), false)) }
        }
        override fun income(uid: String, month: ReportingMonth) = flow {
            if (incomeFailure) error("Income denied")
            incomes.collect { emit(ReportingBatch(if (uid == "a") it.filter { row -> month.contains(row.receiptDate) } else emptyList(), false)) }
        }
        override fun crypto(uid: String, month: ReportingMonth) = cryptos.map { ReportingBatch(if (uid == "a") it.filter { row -> month.contains(row.resultDate) } else emptyList(), false) }
        override fun projects(uid: String) = parents.map { ReportingBatch(if (uid == "a") it else emptyList(), false) }
        override fun recovery(uid: String) = saved.map { if (uid == "a") it else ReportingRecovery() }
    }
}
