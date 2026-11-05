package com.clarity.finance

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.clarity.finance.ui.LocalCatalogue
import com.clarity.finance.ui.expenses.AddExpenseScreen
import com.clarity.finance.ui.expenses.ExpenseFormContent
import com.clarity.finance.ui.theme.ClarityTheme
import com.clarity.finance.viewmodel.AddExpenseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** No production dependencies. Saved-instance restoration is distinct from process death. */
class PilotRiskRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("pilot-a", "fictional"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private val unusedExpense = Proxy.newProxyInstance(ExpenseRepository::class.java.classLoader,
        arrayOf(ExpenseRepository::class.java)) { _, _, _ -> error("No writes in draft tests") } as ExpenseRepository

    @Test fun draftSurvivesSavedInstanceStateRestorationButClearsForAnotherOwner() {
        val vm = AddExpenseViewModel(auth, unusedExpense)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ClarityTheme {
            CompositionLocalProvider(LocalCatalogue provides CatalogueSnapshot(CatalogueDefaults.entries(), false)) {
                AddExpenseScreen(vm) {}
            }
        } }
        compose.onNodeWithText("Amount (LKR)").performTextInput("875.25")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Amount (LKR)").assertTextContains("875.25")
        compose.runOnIdle { auth.authState.value = User("pilot-b", "fictional") }
        compose.onNodeWithText("Amount (LKR)").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun tappingSpendingTypeLabelSelectsItsOption() {
        var selected by mutableStateOf("discretionary")
        compose.setContent { ClarityTheme {
            CompositionLocalProvider(LocalCatalogue provides CatalogueSnapshot(CatalogueDefaults.entries(), false)) {
                ExpenseFormContent("100", {}, "other", {}, "cash", {}, selected, { selected = it },
                    SavingsMath.today().toString(), {}, "", {}, null, false, {}, {})
            }
        } }
        compose.onNodeWithText("Committed").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("committed", selected) }
    }

    @Test fun submittedDetailsDoNotReappearAsANewUnsentDraftInAFreshOwner() {
        val admitted = CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : ExpenseRepository {
            override fun getExpenses(userId: String) = error("Unused")
            override suspend fun getExpense(userId: String, expenseId: String) = error("Unused")
            override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp) = error("Unused")
            override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp) = error("Unused")
            override fun retryFailedExpense(userId: String, expense: Expense) = error("Unused")
            override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> {
                calls++; admitted.await(); return Result.success(Unit)
            }
        }
        val original = AddExpenseViewModel(auth, repository)
        var freshOwner = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val model = remember { if (freshOwner) AddExpenseViewModel(auth, repository) else original }
            ClarityTheme { CompositionLocalProvider(LocalCatalogue provides CatalogueSnapshot(CatalogueDefaults.entries(), false)) {
                AddExpenseScreen(model) {}
            } }
        }
        compose.onNodeWithText("Amount (LKR)").performTextInput("875.25")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls); freshOwner = true }
        try {
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Amount (LKR)").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            compose.runOnIdle { assertEquals(1, calls) }
        } finally { admitted.complete(Unit) }
    }
}
