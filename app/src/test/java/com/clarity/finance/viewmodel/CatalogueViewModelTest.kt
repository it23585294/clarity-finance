package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueViewModelTest {
    private class Auth : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "fixture@example.test"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private class Recovery : FinancialRecoveryAccess {
        val data = mutableMapOf<String, MutableStateFlow<FinancialRecovery>>()
        val saved = mutableListOf<Pair<String, FinancialOperation>>()
        var gate: CompletableDeferred<Unit>? = null
        override fun observe(uid: String) = data.getOrPut(uid) { MutableStateFlow(FinancialRecovery()) }
        override suspend fun submit(uid: String, operation: FinancialOperation) {
            gate?.await(); saved += uid to operation
            data.getOrPut(uid) { MutableStateFlow(FinancialRecovery()) }.value = FinancialRecovery(listOf(FinancialAttempt(operation, "PENDING")))
        }
        override suspend fun resolve(uid: String, operation: FinancialOperation) = error("Unused")
        override suspend fun dismissRejected(uid: String, operation: FinancialOperation) = error("Unused")
    }
    @Test fun `catalogue adds renames and archives stable references without changing recorded totals`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = Auth(); val recovery = Recovery()
        val entries = MutableStateFlow(CatalogueSnapshot(CatalogueDefaults.entries(), false))
        val vm = CatalogueViewModel(auth, object : CatalogueRepository { override fun observe(uid: String) = entries }, recovery)
        val owner = ViewModelStore().also { it.put("catalogue", vm) }
        try {
            runCurrent(); vm.add("category"); vm.change(name = "Travel extras", defaultValue = "committed"); vm.save(); runCurrent()
            val added = recovery.saved.single().second.catalogue!!
            assertTrue(added.id.startsWith("custom_")); assertEquals("Travel extras", added.name)
            val confirmed = added.copy(updatedAt = com.google.firebase.Timestamp(1, 0))
            entries.value = CatalogueSnapshot(CatalogueDefaults.merge(listOf(confirmed)), false); runCurrent()
            vm.edit(confirmed); vm.change(name = "Travel"); vm.save(); runCurrent()
            assertEquals(added.id, recovery.saved.last().second.targetId)
            assertEquals("UPDATE", recovery.saved.last().second.kind)
            vm.askArchive(confirmed); vm.archive(); runCurrent()
            val archived = recovery.saved.last().second.catalogue!!
            val snapshot = CatalogueSnapshot(CatalogueDefaults.merge(listOf(archived)), false)
            assertFalse(snapshot.accepts("category", added.id))
            assertTrue(snapshot.accepts("category", added.id, added.id))
            assertEquals("Travel extras", snapshot.labels("category")[added.id])
            val row = Expense("e", 85000, categoryId = added.id, paymentChannelId = "cash", spendingType = "committed", expenseDate = "2026-01-31")
            val total = MonthlyReporting.expenses(listOf(row), ReportingMonth(java.time.YearMonth.of(2026, 1)))
            assertEquals(85000L, total.totalMinor); assertEquals(added.id, total.categories.single().id)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    @Test fun `account switch clears catalogue forms feedback and proposals and ignores late old callback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = Auth(); val recovery = Recovery().apply { gate = CompletableDeferred() }
        val old = CompletableDeferred<CatalogueSnapshot>()
        val repo = object : CatalogueRepository {
            override fun observe(uid: String) = flow {
                if (uid == "a") { val delayed = withContext(NonCancellable) { old.await() }; emit(delayed) }
                else emit(CatalogueSnapshot(CatalogueDefaults.entries(), false))
            }
        }
        val vm = CatalogueViewModel(auth, repo, recovery); val owner = ViewModelStore().also { it.put("catalogue", vm) }
        try {
            runCurrent(); vm.add("source"); vm.change(name = "Private A"); vm.save(); runCurrent()
            auth.authState.value = User("b", "other@example.test"); runCurrent()
            assertEquals("b", vm.state.value.uid); assertNull(vm.state.value.form); assertFalse(vm.state.value.busy)
            assertTrue(vm.state.value.recovery.attempts.isEmpty())
            old.complete(CatalogueSnapshot(listOf(CatalogueEntry("custom_a", "source", "Private A")), false)); runCurrent()
            assertFalse(vm.state.value.catalogue.entries.orEmpty().any { it.name == "Private A" })
            recovery.gate!!.complete(Unit); runCurrent()
            assertNull(vm.state.value.form); assertNull(vm.state.value.error); assertTrue(vm.state.value.recovery.attempts.isEmpty())
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
