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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Entirely isolated repositories, real screens/navigation/ViewModels; never touches Firebase records or recovery. */
class GoalNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(User("goal-fixture", "test@example.com"))
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    private class Data : GoalRepository {
        val snapshots = MutableStateFlow(GoalSnapshot(known = true, entries = emptyList(), fromCache = false))
        var confirmImmediately = true
        val operations = linkedMapOf<String, GoalOperation>()
        override fun observe(uid: String) = if (uid == "goal-fixture") snapshots else MutableStateFlow(GoalSnapshot(known = true, entries = emptyList(), fromCache = false))
        override suspend fun synchronize(uid: String) { awaitCancellation() }
        override suspend fun submit(uid: String, operation: GoalOperation) {
            operations[operation.operationId]?.let { check(it == operation); return }
            operations[operation.operationId] = operation
            snapshots.value = snapshots.value.copy(attempts = listOf(GoalAttempt(operation, "PENDING")))
            if (confirmImmediately) confirm(operation)
        }
        fun confirm(op: GoalOperation) {
            val old = snapshots.value
            val before = old.goal
            val config = op.goal ?: before!!.config
            val saved = when (op.kind) {
                "CREATE" -> config.openingMinor
                "EDIT" -> SavingsMath.openingBalance(before!!, config.openingMinor)
                else -> SavingsMath.balance(before!!.savedMinor, old.entries.orEmpty().find { it.id == op.entry!!.id }, op.entry!!)
            }
            val entries = if (op.entry == null) old.entries.orEmpty() else old.entries.orEmpty().filterNot { it.id == op.entry.id } + op.entry
            snapshots.value = GoalSnapshot(SavingsGoal(config, saved, op.nextVersion, op.operationId, op.entry?.id ?: ""), entries, true, false)
        }
        override suspend fun retry(uid: String, operation: GoalOperation) { error("No retry in isolated UI check") }
        override suspend fun dismissRejected(uid: String, operation: GoalOperation) { snapshots.value = snapshots.value.copy(attempts = emptyList()) }
        override suspend fun reviseExpired(uid: String, original: GoalOperation, replacement: GoalOperation) {
            check(snapshots.value.attempts.single().operation == original && snapshots.value.attempts.single().canReviseDeadline)
            submit(uid, replacement)
        }
    }
    private val reporting = object : ReportingRepository {
        override fun expenses(uid: String, month: ReportingMonth) = MutableStateFlow(ReportingBatch<Expense>(emptyList(), false))
        override fun income(uid: String, month: ReportingMonth) = MutableStateFlow(ReportingBatch<Income>(emptyList(), false))
        override fun crypto(uid: String, month: ReportingMonth) = MutableStateFlow(ReportingBatch<CryptoResult>(emptyList(), false))
        override fun projects(uid: String) = MutableStateFlow(ReportingBatch<ExpectedPayment>(emptyList(), false))
        override fun recovery(uid: String) = MutableStateFlow(ReportingRecovery())
    }
    private fun show(data: Data) {
        val graph = isolatedAppGraph(auth, unused(ExpenseRepository::class.java), unused(IncomeRepository::class.java),
            unused(PaymentRepository::class.java), unused(CryptoRepository::class.java), reporting, data)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                ClarityTheme { Box(Modifier.width(320.dp)) { IsolatedAppNavigation(graph) } }
            }
        }
    }
    private fun locate(matcher: SemanticsMatcher) {
        val belongsToList = matcher and hasAnyAncestor(hasTestTag("goal-history"))
        if (compose.onAllNodesWithTag("goal-history").fetchSemanticsNodes().isNotEmpty() &&
            (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() || compose.onAllNodes(belongsToList).fetchSemanticsNodes().isNotEmpty()))
            compose.onNodeWithTag("goal-history").performScrollToNode(matcher)
    }
    private fun visible(node: SemanticsNodeInteraction) {
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
    }
    private fun click(label: String) { if (label in listOf("Dashboard", "Savings Goal")) { compose.pilotClick(label); return }; val matcher = hasText(label) and hasClickAction(); locate(matcher); val node = compose.onNode(matcher); visible(node); node.performClick() }
    private fun text(label: String) {
        val base = hasText(label)
        val modal = base and hasAnyAncestor(isDialog())
        val matcher = if (compose.onAllNodes(modal).fetchSemanticsNodes().isNotEmpty()) modal else base
        locate(matcher); val node = compose.onNode(matcher); visible(node); node.assertIsDisplayed()
    }
    private fun create() {
        click("Create goal")
        compose.onNodeWithText("Goal name").performTextReplacement("Isolated savings fixture")
        compose.onNodeWithText("Target (LKR)").performTextReplacement("490000")
        compose.onNodeWithText("Opening allocation").performTextReplacement("11200")
        compose.onNode(hasText("Deadline") and hasSetTextAction()).performTextReplacement(SavingsMath.today().plusYears(1).toString())
        click("Save proposal")
    }
    private fun add(amount: String, withdrawal: Boolean = false) {
        click("Record allocation")
        if (withdrawal) click("Withdrawal")
        compose.onNodeWithText("Amount (LKR)").performTextReplacement(amount)
        click("Save proposal")
    }
    private fun entryAction(data: Data, kind: String, action: String) {
        val id = data.snapshots.value.entries!!.single { it.kind == kind && !it.deleted }.id
        val tag = "goal-$action-$id"
        compose.onNodeWithTag("goal-history").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    @Test fun createContributeWithdrawScheduleInspectCorrectDeleteAndDashboardUpdate() {
        val data = Data(); show(data); click("Savings Goal"); create()
        text("Saved: LKR 11,200.00"); text("Required per opportunity"); text("LKR 39,900.00")
        text("Progress: 2.29%")
        click("Inspect contribution schedule"); text(com.clarity.finance.ui.pilot.PresentationFormat.date(SavingsMath.today().plusMonths(1).toString())); click("Close schedule")
        add("10000"); text("Saved: LKR 21,200.00")
        add("1200", true); text("Saved: LKR 20,000.00")
        entryAction(data, "withdrawal", "edit")
        compose.onNodeWithText("Amount (LKR)").performTextReplacement("1300")
        click("Save proposal"); text("Saved: LKR 19,900.00")
        entryAction(data, "withdrawal", "delete")
        compose.onNodeWithText("Delete allocation entry?").assertIsDisplayed(); click("Keep entry")
        text("Saved: LKR 19,900.00")
        entryAction(data, "withdrawal", "delete"); click("Confirm deletion")
        text("Saved: LKR 21,200.00")
        compose.onNodeWithContentDescription("Back").performClick(); click("Dashboard")
        text("Saved: LKR 21,200.00"); compose.onAllNodesWithText("LKR 0.00")[0].performScrollTo().assertIsDisplayed()
        click("Open Savings Goal"); text("Saved: LKR 21,200.00")
        compose.runOnIdle { assertEquals(5, data.operations.size); assertEquals(2, data.snapshots.value.entries!!.size) }
    }
    @Test fun pendingRejectedChangesRetainConfirmedContributionAndAccountChangeClearsIt() {
        val data = Data(); show(data); click("Savings Goal"); create(); add("10000")
        data.confirmImmediately = false
        entryAction(data, "contribution", "delete"); click("Confirm deletion")
        text("Saved: LKR 21,200.00"); text("Saved proposal awaiting server confirmation. Confirmed savings are retained.")
        compose.runOnIdle {
            val attempt = data.snapshots.value.attempts.single()
            data.snapshots.value = data.snapshots.value.copy(attempts = listOf(attempt.copy(status = "FAILED", error = "Earlier outcome unknown")))
        }
        text("Proposal outcome uncertain: Earlier outcome unknown. Retained until server reconciliation; it cannot be dismissed.")
        compose.onNodeWithText("Dismiss rejected proposal").assertDoesNotExist()
        text("Saved: LKR 21,200.00")
        compose.runOnIdle {
            val attempt = data.snapshots.value.attempts.single()
            data.snapshots.value = data.snapshots.value.copy(attempts = listOf(attempt.copy(status = "REJECTED", error = "Isolated conflict")))
        }
        text("Saved: LKR 21,200.00"); text("Rejected proposal: Isolated conflict. Confirmed savings are retained.")
        click("Dismiss rejected proposal")
        compose.runOnIdle { auth.authState.value = User("other-fixture", "other@example.com") }
        click("Savings Goal"); text("No savings goal recorded. Confirm your own target and opening allocation.")
        compose.onNodeWithText("Isolated savings fixture").assertDoesNotExist()
        compose.onNodeWithText("Rejected proposal: Isolated conflict. Confirmed savings are retained.").assertDoesNotExist()
    }
    @Test fun invalidFormAndOverdueRevisionRemainAccessibleAt320dpWithEnlargedFont() {
        val data = Data(); show(data); click("Savings Goal"); click("Create goal"); click("Save proposal")
        text("Goal details"); assertTrue(data.operations.isEmpty())
        click("Close form")
        compose.runOnIdle {
            val today = SavingsMath.today()
            val config = GoalConfig("Overdue fixture", 10000, 100, today.minusYears(2).toString(), today.minusDays(1).toString())
            data.snapshots.value = GoalSnapshot(SavingsGoal(config, 100, 0, "seed"), emptyList(), true, true)
        }
        text("Cached savings; may be incomplete or out of date.")
        text("Overdue: no remaining scheduled opportunities. Revise the deadline.")
        click("Edit goal")
        compose.onNode(hasText("Deadline") and hasSetTextAction()).performTextReplacement(SavingsMath.today().plusMonths(1).toString())
        click("Save proposal"); text("Next scheduled date"); text(com.clarity.finance.ui.pilot.PresentationFormat.date(SavingsMath.today().toString()))
    }
    @Test fun serverExpiredCreateCanReviseDeadlineWithoutDiscardingItsSavedValues() {
        val data = Data()
        val today = SavingsMath.today()
        val original = GoalOperation("expired-ui", "CREATE", goal = GoalConfig("Expired fixture", 49000000, 1120000,
            today.minusMonths(2).toString(), today.minusDays(1).toString()))
        data.snapshots.value = GoalSnapshot(known = true, entries = emptyList(), fromCache = false,
            attempts = listOf(GoalAttempt(original, "EXPIRED", "Server expiry marker confirmed")))
        show(data); click("Savings Goal"); click("Revise deadline")
        compose.onNodeWithText("Goal name").assertIsNotEnabled()
        compose.onNodeWithText("Target (LKR)").assertIsNotEnabled()
        compose.onNodeWithText("Opening allocation").assertIsNotEnabled()
        click("Close form")
        compose.runOnIdle { assertEquals(original, data.snapshots.value.attempts.single().operation); assertTrue(data.operations.isEmpty()) }
        click("Revise deadline")
        compose.onNode(hasText("Deadline") and hasSetTextAction()).performTextReplacement(today.plusYears(1).toString())
        click("Save proposal"); text("Saved: LKR 11,200.00")
        compose.runOnIdle {
            val saved = data.snapshots.value.goal!!
            assertEquals(original.goal!!.startDate, saved.config.startDate)
            assertEquals(1, data.operations.size); assertNotEquals(original.operationId, saved.lastOperationId)
            assertTrue(data.snapshots.value.attempts.isEmpty())
        }
    }
    @Suppress("UNCHECKED_CAST") private fun <T> unused(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Unexpected ordinary repository access: ${method.name}")
    } as T
}
