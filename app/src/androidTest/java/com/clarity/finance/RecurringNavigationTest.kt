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
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.YearMonth

/** Production screens, navigation and ViewModels with isolated data only; no Firebase or production recovery. */
class RecurringNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("fixture", "fixture@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private class Data : RecurringRepository, ExpenseRepository {
        val templates = MutableStateFlow(ReportingBatch<RecurringTemplate>(emptyList(), false))
        val confirmations = MutableStateFlow<List<ConfirmedOccurrence>>(emptyList())
        val expenses = MutableStateFlow<List<Expense>>(emptyList())
        val recovery = MutableStateFlow(RecurringRecovery())
        val operations = linkedMapOf<String, RecurringOperation>()
        var autoConfirm = true
        private var stamp = 1L
        override fun templates(uid: String, limit: Int) = if (uid == "fixture") templates else MutableStateFlow(ReportingBatch<RecurringTemplate>(emptyList(), false))
        override fun occurrences(uid: String, templateId: String, start: String, endExclusive: String) = confirmations.map { all ->
            ReportingBatch(if (uid == "fixture") all.filter { it.confirmation.templateId == templateId && it.confirmation.month >= start && it.confirmation.month < endExclusive } else emptyList(), false)
        }
        override fun recovery(uid: String): Flow<RecurringRecovery> = if (uid == "fixture") recovery else MutableStateFlow(RecurringRecovery())
        override suspend fun synchronize(uid: String) { awaitCancellation() }
        override suspend fun submit(uid: String, operation: RecurringOperation) {
            check(uid == "fixture")
            operations[operation.operationId]?.let { check(it == operation); return }
            operations[operation.operationId] = operation
            recovery.value = recovery.value.copy(attempts = recovery.value.attempts + RecurringAttempt(operation, "PENDING"))
            if (autoConfirm) applySaved(operation)
        }
        fun applySaved(op: RecurringOperation) {
            if (op.kind == "CONFIRM") {
                val c = op.confirmation!!
                confirmations.value.firstOrNull { it.confirmation.id == c.id }?.let { check(it.confirmation == c); return }
                confirmations.value += ConfirmedOccurrence(c, op.operationId, Timestamp(stamp++, 0))
                expenses.value += Expense(c.expenseId, c.actual.amountMinor, categoryId = c.actual.categoryId, paymentChannelId = c.actual.paymentChannelId,
                    spendingType = c.actual.spendingType, expenseDate = c.actual.expenseDate, note = c.actual.note, updatedAt = Timestamp(stamp++, 0), recurringOccurrenceId = c.id)
            } else {
                val t = RecurringTemplate(op.templateId, op.config!!, op.version, op.kind == "CANCEL", op.operationId)
                templates.value = templates.value.copy(records = templates.value.records.orEmpty().filterNot { it.id == t.id } + t)
            }
            recovery.value = recovery.value.copy(attempts = recovery.value.attempts.filterNot { it.operation.operationId == op.operationId },
                acknowledgedIds = recovery.value.acknowledgedIds + op.operationId)
        }
        override suspend fun dismiss(uid: String, operation: RecurringOperation) {
            check(uid == "fixture" && recovery.value.attempts.single { it.operation == operation }.rejected)
            recovery.value = recovery.value.copy(attempts = recovery.value.attempts.filterNot { it.operation == operation })
        }
        override fun getExpenses(userId: String) = if (userId == "fixture") expenses else MutableStateFlow(emptyList())
        override suspend fun getExpense(userId: String, expenseId: String): Result<Expense> =
            expenses.value.find { userId == "fixture" && it.id == expenseId }?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Expense not found"))
        override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp): Result<Unit> {
            val old = expenses.value.single { userId == "fixture" && it.id == expense.id && it.updatedAt == originalUpdatedAt }
            expenses.value = expenses.value.filterNot { it.id == expense.id } + expense.copy(recurringOccurrenceId = old.recurringOccurrenceId, updatedAt = Timestamp(stamp++, 0))
            return Result.success(Unit)
        }
        override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp): Result<Unit> {
            check(expenses.value.any { userId == "fixture" && it.id == expenseId && it.updatedAt == originalUpdatedAt })
            expenses.value = expenses.value.filterNot { it.id == expenseId }; return Result.success(Unit)
        }
        override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> = error("No ordinary expense creation in this fixture")
        override fun retryFailedExpense(userId: String, expense: Expense) = error("No recovery retry in this fixture")
        fun expenses(uid: String, month: ReportingMonth) = expenses.map { rows -> ReportingBatch(if (uid == "fixture") rows.filter { month.contains(it.expenseDate) } else emptyList(), false) }
        fun income(uid: String, month: ReportingMonth) = MutableStateFlow(ReportingBatch<Income>(emptyList(), false))
        fun crypto(uid: String, month: ReportingMonth) = MutableStateFlow(ReportingBatch<CryptoResult>(emptyList(), false))
        fun projects(uid: String) = MutableStateFlow(ReportingBatch<ExpectedPayment>(emptyList(), false))
        // Reporting and recurring recovery have different result types, so ReportingRepository is adapted below.
    }
    private fun show(data: Data) {
        val reporting = object : ReportingRepository {
            override fun expenses(uid: String, month: ReportingMonth) = data.expenses(uid, month)
            override fun income(uid: String, month: ReportingMonth) = data.income(uid, month)
            override fun crypto(uid: String, month: ReportingMonth) = data.crypto(uid, month)
            override fun projects(uid: String) = data.projects(uid)
            override fun recovery(uid: String) = MutableStateFlow(ReportingRecovery())
        }
        val graph = isolatedAppGraph(auth, data, unused(IncomeRepository::class.java), unused(PaymentRepository::class.java), unused(CryptoRepository::class.java),
            reporting, recurring = data)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                ClarityTheme { Box(Modifier.width(320.dp)) { IsolatedAppNavigation(graph) } }
            }
        }
    }
    private fun locate(matcher: SemanticsMatcher) {
        val belongsToList = matcher and hasAnyAncestor(hasTestTag("recurring-list"))
        if (compose.onAllNodesWithTag("recurring-list").fetchSemanticsNodes().isNotEmpty() &&
            (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() || compose.onAllNodes(belongsToList).fetchSemanticsNodes().isNotEmpty()))
            compose.onNodeWithTag("recurring-list").performScrollToNode(matcher)
    }
    private fun visible(node: SemanticsNodeInteraction) { try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() } }
    private fun click(label: String) {
        if (label in listOf("Dashboard", "Recurring Expenses")) { compose.pilotClick(label); return }
        val base = (hasText(label) or hasContentDescription(label)) and hasClickAction() and hasSetTextAction().not()
        val dialog = base and hasAnyAncestor(isDialog())
        val matcher = if (compose.onAllNodes(dialog).fetchSemanticsNodes().isNotEmpty()) dialog else base
        locate(matcher); val node = compose.onNode(matcher); visible(node); node.performClick()
    }
    private fun text(label: String) {
        val base = hasText(label); val dialog = base and hasAnyAncestor(isDialog())
        val matcher = if (compose.onAllNodes(dialog).fetchSemanticsNodes().isNotEmpty()) dialog else base
        locate(matcher); val node = compose.onNode(matcher); visible(node); node.assertIsDisplayed()
    }
    private fun create(name: String, amount: String, first: String = Recurrence.today().toString()) {
        click("Add template")
        compose.onNodeWithText("Template name").performTextReplacement(name)
        compose.onNodeWithText("Suggested (LKR)").performTextReplacement(amount)
        compose.onNodeWithText("First due date").performTextReplacement(first)
        if (name == "Utilities") { click("Category"); click("Utilities") }
        click("Save template proposal")
    }
    private fun pay(month: String = YearMonth.from(Recurrence.today()).toString(), amount: String? = null) {
        click("Review ${com.clarity.finance.ui.pilot.PresentationFormat.month(month)} payment")
        if (amount != null) compose.onNodeWithText("Actual (LKR)").performTextReplacement(amount)
        click("Confirm payment proposal")
    }
    private fun back() { if (compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isEmpty()) compose.pilotClick("Plans") else compose.onNodeWithContentDescription("Back").performClick() }
    private fun deleteClick() {
        val nodes = compose.onAllNodes(hasText("Delete") and hasClickAction())
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
    }
    @Test fun AT09RentIgnoredUtilitiesAndEditedActualUpdateDashboardExactlyOnce() {
        val data = Data(); show(data); click("Recurring Expenses"); create("Rent", "34000"); click("Inspect Rent"); pay()
        text("Confirmed originally: LKR 34,000.00 on ${com.clarity.finance.ui.pilot.PresentationFormat.date(Recurrence.today().toString())}")
        compose.onNodeWithText("Review ${com.clarity.finance.ui.pilot.PresentationFormat.month(YearMonth.from(Recurrence.today()).toString())} payment").assertDoesNotExist()
        back(); create("Utilities", "5000"); back(); click("Dashboard")
        compose.onNodeWithText("LKR 34,000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LKR -34,000.00").performScrollTo().assertIsDisplayed()
        back(); click("Recurring Expenses"); click("Inspect Utilities"); pay(amount = "6250.50")
        back(); back(); click("Dashboard")
        compose.onNodeWithText("LKR 40,250.50").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LKR -40,250.50").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, data.expenses.value.size); assertEquals(2, data.confirmations.value.size)
            assertEquals(2, data.operations.values.count { it.kind == "CONFIRM" })
            assertEquals(setOf(3400000L, 625050L), data.expenses.value.map { it.amountMinor }.toSet())
        }
    }
    @Test fun actualCorrectionBackdateAndDeletionKeepConfirmationConsumedAndHistoryInspectable() {
        val data = Data(); show(data); click("Recurring Expenses"); create("Rent", "34000"); click("Inspect Rent"); pay()
        click("Inspect actual expense"); click("Edit expense")
        compose.onNodeWithText("Amount (LKR)").performTextReplacement("33000")
        val previous = YearMonth.from(Recurrence.today()).minusMonths(1).atEndOfMonth().toString()
        compose.onNodeWithText("Date (yyyy-MM-dd)").performTextReplacement(previous)
        click("Save Changes")
        text("Confirmed originally: LKR 34,000.00 on ${com.clarity.finance.ui.pilot.PresentationFormat.date(Recurrence.today().toString())}")
        compose.runOnIdle { assertEquals(3300000L, data.expenses.value.single().amountMinor); assertEquals(previous, data.expenses.value.single().expenseDate) }
        click("Inspect actual expense"); deleteClick(); click("Cancel")
        compose.runOnIdle { assertEquals(1, data.expenses.value.size) }
        deleteClick(); deleteClick()
        text("Confirmed originally: LKR 34,000.00 on ${com.clarity.finance.ui.pilot.PresentationFormat.date(Recurrence.today().toString())}")
        compose.onNodeWithText("Review ${com.clarity.finance.ui.pilot.PresentationFormat.month(YearMonth.from(Recurrence.today()).toString())} payment").assertDoesNotExist()
        click("Inspect actual expense"); text("Expense not found or already deleted."); click("Back")
        compose.runOnIdle { assertTrue(data.expenses.value.isEmpty()); assertEquals(1, data.confirmations.value.size) }
    }
    @Test fun templateEditingAndCancellationKeepStopDueCandidatesAndRetainActualHistory() {
        val data = Data(); val first = Recurrence.today().minusMonths(2).withDayOfMonth(1)
        show(data); click("Recurring Expenses"); create("Rent", "34000", first.toString()); click("Inspect Rent"); pay(YearMonth.from(first).toString())
        click("Edit template"); compose.onNodeWithText("First due date").assertIsNotEnabled()
        compose.onNodeWithText("Template name").performTextReplacement("Edited rent")
        compose.onNodeWithText("Suggested (LKR)").performTextReplacement("35000")
        compose.onNodeWithText("Monthly day").performTextReplacement("31"); click("Save template proposal")
        click("Cancel template"); click("Keep template")
        compose.runOnIdle { assertFalse(data.templates.value.records!!.single().isCancelled) }
        click("Cancel template")
        compose.runOnIdle { data.templates.value = data.templates.value.copy(records = data.templates.value.records!!.map { it.copy(version = it.version + 1) }) }
        click("Confirm cancellation"); text("Template changed; reopen cancellation."); click("Keep template")
        click("Cancel template"); click("Confirm cancellation")
        text("Cancelled. All unconfirmed occurrences, including already-due items, are stopped. Past expenses and confirmations remain.")
        click("Inspect actual expense"); text("Expense details")
        compose.runOnIdle { assertTrue(data.templates.value.records!!.single().isCancelled); assertEquals(1, data.expenses.value.size); assertEquals(1, data.confirmations.value.size) }
    }
    @Test fun pendingUncertainRejectedAndAccountSwitchStatesNeverBecomeConfirmedSpending() {
        val data = Data(); show(data); click("Recurring Expenses"); create("Rent", "34000"); click("Inspect Rent")
        data.autoConfirm = false; pay()
        text("Pending server confirmation. Confirmed spending is retained.")
        compose.runOnIdle {
            data.templates.value = data.templates.value.copy(fromCache = true)
            data.recovery.value = data.recovery.value.copy(attempts = data.recovery.value.attempts.map { it.copy(status = "UNCERTAIN", error = "Network unavailable") })
        }
        text("Cached templates; may be incomplete or out of date.")
        text("Outcome uncertain: Network unavailable. An earlier transaction may still complete. This proposal cannot be dismissed.")
        compose.onNodeWithText("Dismiss rejected proposal").assertDoesNotExist()
        compose.runOnIdle { data.recovery.value = data.recovery.value.copy(attempts = data.recovery.value.attempts.map { it.copy(status = "REJECTED", error = "Verified version conflict") }) }
        click("Dismiss rejected proposal"); pay(); click("Edit template")
        compose.onNodeWithText("Template name").performTextReplacement("Private draft")
        compose.runOnIdle { auth.authState.value = User("second-fixture", "second@example.com") }
        click("Recurring Expenses")
        text("No recurring templates recorded. Add your own obligation; no scenario savings or spending is prefilled.")
        compose.onNodeWithText("Private draft").assertDoesNotExist(); compose.onNodeWithText("Dismiss rejected proposal").assertDoesNotExist()
        compose.runOnIdle { auth.authState.value = User("fixture", "fixture@example.com") }
        click("Recurring Expenses"); text("Pending server confirmation. Confirmed spending is retained.")
        compose.runOnIdle { assertTrue(data.expenses.value.isEmpty()); assertEquals(1, data.recovery.value.attempts.size); assertEquals(0, data.confirmations.value.size) }
    }
    @Suppress("UNCHECKED_CAST") private fun <T> unused(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Unexpected unrelated repository call: ${method.name}")
    } as T
}
