package com.clarity.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.di.ViewModelFactory
import com.clarity.finance.ui.navigation.AppNavigation
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.YearMonth

/** Real navigation, reporting ViewModel and existing edit/delete form, with isolated repositories only. */
class DashboardNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val today = CryptoReporting.today()
    private val timestamp = Timestamp(1, 0)
    private val rent = Expense("rent", 4000000, categoryId = "housing", paymentChannelId = "cash", spendingType = "committed",
        expenseDate = today.toString(), note = "Reporting fixture rent", updatedAt = timestamp)
    private val meal = rent.copy(id = "meal", amountMinor = 1000000, categoryId = "food_dining", spendingType = "discretionary", note = "Reporting fixture meal")
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("reporting-a", "test@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private class Data : ReportingRepository {
        val expenses = MutableStateFlow<List<Expense>>(emptyList())
        val income = MutableStateFlow<List<Income>>(emptyList())
        val crypto = MutableStateFlow<List<CryptoResult>>(emptyList())
        val projects = MutableStateFlow<List<ExpectedPayment>>(emptyList())
        var expenseError: String? = null
        override fun expenses(uid: String, month: ReportingMonth) = expenses.map {
            ReportingBatch(if (uid == "reporting-a") it.filter { row -> month.contains(row.expenseDate) } else emptyList(), false, error = expenseError)
        }
        override fun income(uid: String, month: ReportingMonth) = income.map {
            ReportingBatch(if (uid == "reporting-a") it.filter { row -> month.contains(row.receiptDate) } else emptyList(), false)
        }
        override fun crypto(uid: String, month: ReportingMonth) = crypto.map {
            ReportingBatch(if (uid == "reporting-a") it.filter { row -> month.contains(row.resultDate) } else emptyList(), false)
        }
        override fun projects(uid: String) = projects.map { ReportingBatch(if (uid == "reporting-a") it else emptyList(), false) }
        override fun recovery(uid: String) = MutableStateFlow(ReportingRecovery())
    }
    private fun show(data: Data, enlarged: Boolean = false) {
        val expenseRepository = object : ExpenseRepository {
            override fun getExpenses(userId: String) = data.expenses
            override suspend fun getExpense(userId: String, expenseId: String) = Result.success(data.expenses.value.single { it.id == expenseId })
            override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp): Result<Unit> {
                data.expenses.value = data.expenses.value.filterNot { it.id == expense.id } + expense.copy(updatedAt = timestamp)
                return Result.success(Unit)
            }
            override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp): Result<Unit> {
                data.expenses.value = data.expenses.value.filterNot { it.id == expenseId }
                return Result.success(Unit)
            }
            override suspend fun enqueueExpense(userId: String, expense: Expense) = error("No creation in this navigation test")
            override fun retryFailedExpense(userId: String, expense: Expense) = error("Unused")
        }
        val factory = ViewModelFactory(auth, expenseRepository, unused(IncomeRepository::class.java), unused(PaymentRepository::class.java),
            unused(CryptoRepository::class.java), data)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (enlarged) 1.3f else density.fontScale)) {
                ClarityTheme { Box(Modifier.width(320.dp)) { AppNavigation(factory) } }
            }
        }
        compose.onNodeWithText("Dashboard").performScrollTo().performClick()
    }
    private fun click(label: String) = compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()

    @Test fun dashboardInsightsCategoryBackdateDeleteAndQuickAddUseProductionNavigation() {
        val data = Data()
        data.expenses.value = listOf(rent, meal)
        data.income.value = listOf(Income("salary", "salary", today.toString(), "LKR", "1000", 100000, "native_lkr", "1", "Reporting fixture income"))
        show(data)
        compose.onNodeWithText("LKR -49000.00").performScrollTo().assertIsDisplayed()
        click("Spending insights")
        compose.onNodeWithText("Committed: LKR 40000.00 (80.0%)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Discretionary: LKR 10000.00 (20.0%)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Housing: LKR 40000.00").performScrollTo().performClick()
        compose.onNodeWithText("Reporting fixture rent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reporting fixture meal").assertDoesNotExist()
        compose.onNodeWithText("Reporting fixture income").assertDoesNotExist()
        click("Inspect / edit expense")
        compose.onNodeWithText("Edit Expense").assertIsDisplayed()
        val previous = YearMonth.from(today).minusMonths(1).atEndOfMonth().toString()
        compose.onNodeWithText("Date (yyyy-MM-dd)").performTextReplacement(previous)
        click("Save Changes")
        compose.onNodeWithText("No matching expenses recorded for this month.").performScrollTo().assertIsDisplayed()
        click("Previous month")
        compose.onNodeWithText("Reporting fixture rent").performScrollTo().assertIsDisplayed()
        click("Inspect / edit expense")
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.onNodeWithText("Confirm Deletion").assertIsDisplayed()
        compose.onNode(hasText("Delete") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("No matching expenses recorded for this month.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("meal"), data.expenses.value.map { it.id }) }
        compose.onNode(hasText("Add Expense") and hasClickAction()).performClick()
        compose.onNodeWithText("Amount (LKR)").assertIsDisplayed()
        click("Cancel")
        compose.onNodeWithText("Reporting month: ${YearMonth.from(today).minusMonths(1)}").performScrollTo().assertIsDisplayed()
    }

    @Test fun enlargedLayoutKeepsIndependentSectionsAndLinkedReceiptProtectionThenClearsOldAccount() {
        val data = Data()
        data.expenseError = "Expense listener unavailable"
        data.income.value = listOf(Income("linked", "freelance", today.toString(), "LKR", "1000", 100000, "native_lkr", "1", "Linked fixture",
            linkedPaymentId = "parent"))
        data.crypto.value = listOf(CryptoResult("gain", "gain", 50000, today.toString(), "Crypto fixture"))
        data.projects.value = listOf(ExpectedPayment("p", "project", "Fixture", "freelance", "USD", today.toString(), 20000))
        show(data, enlarged = true)
        compose.onNodeWithText("LKR 1000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Expense listener unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Income and expense data must both be available to calculate surplus.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("USD 200.00").performScrollTo().assertIsDisplayed()
        click("Monthly history")
        compose.onNodeWithText("Linked receipt: inspect project").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inspect / edit income").assertDoesNotExist()
        click("Inspect crypto result")
        compose.onNodeWithText("Realised crypto result").assertIsDisplayed()
        compose.runOnIdle { auth.authState.value = User("reporting-b", "other@example.com") }
        compose.onNodeWithText("Realised crypto result").assertDoesNotExist()
        compose.onNodeWithText("Dashboard").performScrollTo().performClick()
        click("Monthly history")
        compose.onNodeWithText("Linked fixture").assertDoesNotExist()
        compose.onNodeWithText("Crypto fixture").assertDoesNotExist()
        compose.onNodeWithText("No received income recorded for this month.").performScrollTo().assertIsDisplayed()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unused(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Unexpected repository access: ${method.name}")
    } as T
}
