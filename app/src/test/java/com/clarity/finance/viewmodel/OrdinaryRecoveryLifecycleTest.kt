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
class OrdinaryRecoveryLifecycleTest {
    private class Auth : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("a", "fixture@example.test"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private open class Expenses : ExpenseRepository {
        override fun getExpenses(userId: String) = flowOf(emptyList<Expense>())
        override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> = error("Unused")
        override fun retryFailedExpense(userId: String, expense: Expense) = error("Unused")
        override suspend fun getExpense(userId: String, expenseId: String): Result<Expense> = error("Unused")
        override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = error("Unused")
        override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: com.google.firebase.Timestamp): Result<Unit> = error("Unused")
    }
    @Test fun `duplicate save taps share one immutable expense and a delayed old account result never restores feedback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = Auth(); val gate = CompletableDeferred<Unit>(); val saves = mutableListOf<Pair<String,Expense>>()
        val repo = object : Expenses() {
            override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> {
                saves += userId to expense; withContext(NonCancellable) { gate.await() }; return Result.success(Unit)
            }
        }
        val vm = AddExpenseViewModel(auth, repo); val owner = ViewModelStore().also { it.put("add", vm) }
        try {
            runCurrent(); repeat(2) { vm.addExpense("850", "transport", "cash", "discretionary", "2026-01-31", "") }; runCurrent()
            assertEquals(1, saves.size); assertEquals("a", saves.single().first)
            auth.authState.value = User("b", "other@example.test"); runCurrent(); gate.complete(Unit); runCurrent()
            assertFalse(vm.formState.value.isSuccess); assertFalse(vm.formState.value.isSubmitting)
            assertEquals("b", vm.formState.value.accountKey); assertNull(vm.formState.value.error)
        } finally { owner.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun `a terminated ordinary listener retries while open and account switching remains usable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = Auth(); var calls = 0; val secondAccount = CompletableDeferred<Unit>()
        val repo = object : Expenses() {
            override fun getExpenses(userId: String) = flow {
                calls++; if (calls == 1) error("Network unavailable")
                if (userId == "b") secondAccount.await()
                emit(listOf(Expense(userId, 100, categoryId = "other", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = "2026-01-31")))
                awaitCancellation()
            }
        }
        val vm = ExpenseListViewModel(auth, repo); val owner = ViewModelStore().also { it.put("list", vm) }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.expensesState.collect { } }
        try {
            runCurrent(); assertTrue(vm.expensesState.value is ExpensesUiState.Error)
            advanceTimeBy(5001); runCurrent(); assertEquals("a", (vm.expensesState.value as ExpensesUiState.Success).expenses.single().id)
            auth.authState.value = User("b", "other@example.test"); runCurrent()
            assertTrue(vm.expensesState.value is ExpensesUiState.Loading)
            secondAccount.complete(Unit); runCurrent()
            assertEquals("b", (vm.expensesState.value as ExpensesUiState.Success).expenses.single().id)
        } finally { observer.cancelAndJoin(); owner.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
