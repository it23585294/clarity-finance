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

    @Test fun `category filtering preserves creation order through corrections and backdating`() = runTest(dispatcher) {
        runCurrent()
        val old = expense.copy(id = "a-old", createdAt = com.google.firebase.Timestamp(1, 0))
        val newer = expense.copy(id = "z-new", createdAt = com.google.firebase.Timestamp(3, 0))
        val middle = expense.copy(id = "middle", categoryId = "food_dining", createdAt = com.google.firebase.Timestamp(2, 0))
        expenseFlow().value = ReportingBatch(listOf(old, newer, middle), false); runCurrent()
        assertEquals(listOf("z-new", "middle", "a-old"), vm.state.value.expenseHistory.map { it.id })
        vm.selectCategory("transport"); runCurrent()
        assertEquals(listOf("z-new", "a-old"), vm.state.value.expenseHistory.map { it.id })
        expenseFlow().value = ReportingBatch(listOf(old.copy(note = "Edited", updatedAt = com.google.firebase.Timestamp(999, 0)),
            newer, middle.copy(categoryId = "transport", updatedAt = com.google.firebase.Timestamp(999, 0))), false); runCurrent()
        assertEquals(listOf("z-new", "middle", "a-old"), vm.state.value.expenseHistory.map { it.id })
        assertEquals("4500.00", vm.state.value.expensesText)
        expenseFlow().value = ReportingBatch(listOf(newer, middle.copy(categoryId = "transport")), false)
        expenseFlow(month = "2025-12").value = ReportingBatch(listOf(old.copy(expenseDate = "2025-12-31")), false); runCurrent()
        assertEquals("3000.00", vm.state.value.expensesText)
        vm.changeMonth(-1); runCurrent()
        assertEquals(listOf("a-old"), vm.state.value.expenseHistory.map { it.id })
        assertEquals("1500.00", vm.state.value.expensesText)
    }

    @Test fun `income and crypto history sort immutable creation without changing totals or old receipt links`() = runTest(dispatcher) {
        runCurrent(); vm.changeMonth(-1); runCurrent()
        val old = income.copy(id = "a", receiptDate = "2025-12-31", createdAt = com.google.firebase.Timestamp(1, 0), linkedPaymentId = "project")
        val newest = old.copy(id = "z", createdAt = com.google.firebase.Timestamp(2, 0))
        val earlierDate = old.copy(id = "earlier-date", receiptDate = "2025-12-30", createdAt = com.google.firebase.Timestamp(999, 0))
        repo.incomes.value = listOf(old, earlierDate, newest)
        repo.cryptos.value = listOf(CryptoResult("a", "gain", 6000000, "2025-12-31", createdAt = com.google.firebase.Timestamp(1, 0)),
            CryptoResult("z", "loss", 2500000, "2025-12-31", createdAt = com.google.firebase.Timestamp(2, 0)))
        runCurrent()
        assertEquals(listOf("z", "a", "earlier-date"), vm.state.value.incomeHistory.map { it.id })
        assertEquals(listOf("z", "a"), vm.state.value.cryptoHistory.map { it.id })
        assertEquals("3000.00", vm.state.value.incomeText); assertEquals("+35000.00", vm.state.value.cryptoText)
        assertTrue(vm.state.value.incomeHistory.all { it.linkedPaymentId == "project" })
        repo.incomes.value = listOf(old.copy(updatedAt = com.google.firebase.Timestamp(9999, 0)), earlierDate, newest); runCurrent()
        assertEquals(listOf("z", "a", "earlier-date"), vm.state.value.incomeHistory.map { it.id })
        repo.incomes.value = listOf(old.copy(receiptDate = "2025-11-30"), earlierDate, newest); runCurrent()
        assertEquals(listOf("z", "earlier-date"), vm.state.value.incomeHistory.map { it.id })
        assertEquals("2000.00", vm.state.value.incomeText)
    }

    @Test fun `crypto overflow retains creation ordered inspectable rows without reordering aggregate inputs`() = runTest(dispatcher) {
        runCurrent()
        val old = CryptoResult("a", "gain", Long.MAX_VALUE, "2026-01-01", createdAt = com.google.firebase.Timestamp(1, 0))
        val newer = old.copy(id = "z", createdAt = com.google.firebase.Timestamp(2, 0))
        repo.cryptos.value = listOf(old, newer); runCurrent()
        assertNull(vm.state.value.cryptoText)
        assertTrue(vm.state.value.crypto.error!!.contains("supported integer range"))
        assertEquals(listOf("z", "a"), vm.state.value.cryptoHistory.map { it.id })
        vm.selectCategory("transport"); runCurrent()
        assertEquals(listOf("z", "a"), vm.state.value.cryptoHistory.map { it.id })
    }

    @Test fun `history orders same day creation descending while edits filters and reporting periods remain stable`() = runTest(dispatcher) {
        runCurrent()
        val older = expense.copy(id = "a", createdAt = com.google.firebase.Timestamp(1, 0))
        val newer = expense.copy(id = "z", createdAt = com.google.firebase.Timestamp(2, 0))
        expenseFlow().value = ReportingBatch(listOf(older, newer), false); runCurrent()
        assertEquals(listOf("z", "a"), vm.state.value.expenseHistory.map { it.id })
        assertEquals("3000.00", vm.state.value.expensesText)
        expenseFlow().value = ReportingBatch(listOf(older.copy(updatedAt = com.google.firebase.Timestamp(999, 0)), newer), false); runCurrent()
        vm.selectCategory("transport"); runCurrent()
        assertEquals(listOf("z", "a"), vm.state.value.expenseHistory.map { it.id })
        expenseFlow().value = ReportingBatch(listOf(older), false); runCurrent()
        assertEquals(listOf("a"), vm.state.value.expenseHistory.map { it.id })
        assertEquals("1500.00", vm.state.value.expensesText)
        vm.changeMonth(-1); runCurrent()
        assertTrue(vm.state.value.expenseHistory.isEmpty())
    }

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

    @Test fun `crypto overflow retains valid inspectable rows across renders with an unavailable aggregate`() = runTest(dispatcher) {
        runCurrent()
        val rows = listOf(CryptoResult("large", "gain", Long.MAX_VALUE, "2026-01-01"),
            CryptoResult("small", "gain", 1, "2026-01-01"))
        repo.cryptos.value = rows; runCurrent()
        assertNull(vm.state.value.cryptoText)
        assertTrue(vm.state.value.crypto.error!!.contains("integer range"))
        assertEquals(rows, vm.state.value.cryptoHistory)
        vm.selectCategory(null) // Switching to history renders again while the aggregate error remains.
        assertEquals(rows, vm.state.value.cryptoHistory)
        assertNull(vm.state.value.cryptoText)
        assertEquals("0.00", vm.state.value.surplusText)
        repo.cryptos.value = listOf(rows.last()); runCurrent()
        assertEquals("+0.01", vm.state.value.cryptoText)
        assertNull(vm.state.value.crypto.error)
    }

    @Test fun `malformed crypto after otherwise overflowing rows remains an explicit validation error`() = runTest(dispatcher) {
        runCurrent()
        val large = CryptoResult("large", "gain", Long.MAX_VALUE, "2026-01-01")
        repo.cryptos.value = listOf(large, large.copy(id = "small", amountMinor = 1), large.copy(id = "bad", direction = "invalid"))
        runCurrent()
        assertNull(vm.state.value.cryptoText)
        assertEquals("Invalid result direction", vm.state.value.crypto.error)
        assertTrue(vm.state.value.cryptoHistory.isEmpty())
        vm.selectCategory(null)
        assertEquals("Invalid result direction", vm.state.value.crypto.error)
        assertTrue(vm.state.value.cryptoHistory.isEmpty())
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
