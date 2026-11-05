package com.clarity.finance

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseCInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun show(data: PhaseCFixtures) {
        val graph = data.graph()
        val narrow = InstrumentationRegistry.getArguments().getString("phaseCNarrow") == "true"
        compose.setContent { val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
    }
    private fun click(label: String) {
        val base = (hasText(label) or hasContentDescription(label)) and hasClickAction() and hasSetTextAction().not()
        val modal = base and hasAnyAncestor(isDialog())
        val matcher = if (compose.onAllNodes(modal).fetchSemanticsNodes().isNotEmpty()) modal else base
        val list = listOf("goal-history", "recurring-list").firstOrNull { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        val insideList = list != null && compose.onAllNodes(matcher and hasAnyAncestor(hasTestTag(list))).fetchSemanticsNodes().isNotEmpty()
        val handledList = list != null && (insideList || compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty())
        if (handledList) {
            checkNotNull(list)
            compose.onNodeWithTag(list).performScrollToNode(matcher)
        }
        val node = compose.onNode(matcher)
        // A partially visible large-font button can pass isDisplayed while its click centre is clipped.
        if (!handledList && compose.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        compose.waitForIdle()
        node.assertIsDisplayed()
        node.performClick()
    }
    private fun field(label: String, value: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun text(label: String) {
        val matcher = hasText(label)
        val list = listOf("goal-history", "recurring-list").firstOrNull { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() && list != null) compose.onNodeWithTag(list).performScrollToNode(matcher)
        compose.onNode(matcher).performScrollTo().assertIsDisplayed()
    }

    @Test fun allocationChoiceRepeatedSaveAndAllRecoveryStatesRetainConfirmedSavings() {
        val data = PhaseCFixtures().apply { autoGoal = false; admission = CompletableDeferred() }; show(data)
        compose.pilotClick("Savings Goal"); text("Saved: LKR 20,000.00"); text("LKR 470,000.00")
        click("Record allocation"); click("Withdrawal")
        compose.onNodeWithText("Withdrawal").assertIsSelected()
        field("Amount (LKR)", "1000")
        compose.onNodeWithText("Save proposal").performTouchInput { doubleClick() }
        compose.runOnIdle {
            val op = data.goalOperations.values.single(); assertEquals("withdrawal", op.entry!!.kind); assertEquals(100000L, op.entry.amountMinor)
            data.admission!!.complete(Unit)
        }
        text("Saved: LKR 20,000.00")
        compose.onNodeWithText("Dismiss rejected proposal").assertDoesNotExist()
        compose.runOnIdle { data.goalState.value = data.goalState.value.copy(attempts = data.goalState.value.attempts.map { it.copy(status = "UNCERTAIN", error = "Access unavailable") }) }
        text("Saved: LKR 20,000.00"); compose.onNodeWithText("Dismiss rejected proposal").assertDoesNotExist()
        compose.runOnIdle { data.goalState.value = data.goalState.value.copy(attempts = data.goalState.value.attempts.map { it.copy(status = "REJECTED", error = "Verified rejection") }) }
        click("Dismiss rejected proposal"); text("Saved: LKR 20,000.00")
        compose.runOnIdle { assertEquals(1, data.goalOperations.size); assertEquals(2000000L, data.goalState.value.goal!!.savedMinor); assertEquals(2, data.goalState.value.entries!!.size) }
    }

    @Test fun invalidWithdrawalAndOverfundedReachedStateRemainTruthful() {
        val data = PhaseCFixtures(); show(data); compose.pilotClick("Savings Goal")
        click("Record allocation"); click("Withdrawal"); field("Amount (LKR)", "20000.01"); click("Save proposal")
        compose.onNode(hasText("This change would make recorded savings negative.") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(data.goalOperations.isEmpty()) }
        click("Close form")
        compose.runOnIdle { data.goalState.value = GoalSnapshot(SavingsGoal(data.config.copy(openingMinor = 50000000), 50000000, 0, "overfunded"), emptyList(), true, false) }
        text("Saved: LKR 500,000.00"); text("Overfunded: LKR 10,000.00"); text("Goal reached. Required contribution: LKR 0.00")
    }

    @Test fun cryptoAggregateOverflowRetainsValidInspectableRows() {
        val data = PhaseCFixtures()
        data.cryptoState.value = CryptoSnapshot(listOf(data.cryptoState.value.results.first().copy(amountMinor = Long.MAX_VALUE),
            CryptoResult("extra", "gain", 1, data.today.toString(), updatedAt = Timestamp(1, 0))), loaded = true, fromCache = false)
        show(data); compose.pilotClick("Crypto Results")
        text("Month net: Unavailable"); text("Unavailable")
        click("Gain LKR +92,233,720,368,547,758.07")
        compose.onNodeWithText("Gain: LKR +92,233,720,368,547,758.07").assertIsDisplayed()
        compose.onNodeWithText("Edit result").assertIsEnabled()
        compose.runOnIdle { assertTrue(data.cryptoOperations.isEmpty()) }
    }

    @Test fun pendingRejectedCryptoEditRetainsOriginalNetAndAccountSwitchClearsDraftAndFeedback() {
        val data = PhaseCFixtures().apply { autoCrypto = false }; show(data); compose.pilotClick("Crypto Results")
        click("Gain LKR +60,000.00"); click("Edit result"); field("Amount (LKR)", "65000"); click("Save result")
        text("Month net: LKR +35,000.00"); text("1 pending change awaiting server confirmation.")
        click("Gain LKR +60,000.00"); compose.onNodeWithText("Edit result").assertDoesNotExist(); click("Close")
        compose.runOnIdle { data.cryptoState.value = data.cryptoState.value.copy(results = data.cryptoState.value.results.map {
            if (it.sync == CryptoSync.PENDING) it.copy(sync = CryptoSync.FAILED, error = "Definitive fixture rejection") else it }) }
        text("Month net: LKR +35,000.00"); text("1 rejected change. Review saved proposals.")
        click("Dismiss rejected proposal"); text("Month net: LKR +35,000.00")
        click("Add result"); field("Note (optional)", "Old owner private draft")
        compose.runOnIdle { data.session.value = User("other-phase-c", "Other fictional account") }
        compose.onNodeWithText("Old owner private draft").assertDoesNotExist()
        compose.pilotClick("Crypto Results"); text("No realised results recorded for this month.")
        compose.onNodeWithText("1 rejected change. Review saved proposals.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, data.cryptoOperations.size); assertEquals(2, data.cryptoState.value.confirmedResults!!.size) }
    }

    @Test fun recurringActualProposalIsUniqueAndDeletedExpenseNeverReopensConfirmation() {
        val data = PhaseCFixtures().apply { autoRecurring = false; admission = CompletableDeferred() }; show(data)
        compose.pilotClick("Recurring Expenses"); click("Inspect Rent")
        val month = data.today.toString().take(7)
        click("Review ${com.clarity.finance.ui.pilot.PresentationFormat.month(month)} payment")
        field("Actual (LKR)", "33000")
        compose.onNodeWithText("Confirm payment proposal").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, data.recurringOperations.size); data.admission!!.complete(Unit) }
        compose.runOnIdle { data.confirmRecurring(data.recurringOperations.values.single()) }
        val row = hasAnyAncestor(hasTestTag("recurring-slot-$month"))
        compose.onNodeWithTag("recurring-list").performScrollToNode(hasTestTag("recurring-slot-$month"))
        compose.onNode(hasText("Inspect actual expense") and row).performClick()
        click("Delete"); compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(2, data.expenseRows.value.size) }
        click("Delete"); compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()
        compose.onNodeWithTag("recurring-list").performScrollToNode(hasTestTag("recurring-slot-$month"))
        compose.onNodeWithText("Review ${com.clarity.finance.ui.pilot.PresentationFormat.month(month)} payment").assertDoesNotExist()
        compose.onNode(hasText("Inspect actual expense") and row).performClick()
        compose.onNodeWithText("Expense not found", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, data.recurringOperations.size); assertEquals(2, data.confirmations.value.size)
            assertEquals(1, data.expenseRows.value.size); assertEquals(3300000L, data.recurringOperations.values.single().confirmation!!.actual.amountMinor)
        }
    }

    @Test fun emptyCachedGoalAndUncachedUnavailableStateNeverImplyConfirmedZero() {
        val data = PhaseCFixtures()
        data.goalState.value = GoalSnapshot(known = true, entries = emptyList(), fromCache = true)
        show(data); compose.pilotClick("Savings Goal")
        text("Cached savings; may be incomplete or out of date.")
        text("No savings goal recorded. Confirm your own target and opening allocation.")
        compose.runOnIdle { data.goalState.value = GoalSnapshot(error = "Goal unavailable in isolated fixture") }
        compose.onNodeWithText("Goal unavailable in isolated fixture").assertIsDisplayed()
        compose.onNodeWithText("No savings goal recorded. Confirm your own target and opening allocation.").assertDoesNotExist()
        compose.onNodeWithText("Saved: LKR 0.00").assertDoesNotExist()
    }

    @Test fun accountChangeClearsGoalFormAndRecordedHistory() {
        val data = PhaseCFixtures(); show(data); compose.pilotClick("Savings Goal"); click("Edit goal")
        field("Goal name", "Old owner goal draft")
        compose.runOnIdle { data.session.value = User("other-phase-c", "Other fictional account") }
        compose.onNodeWithText("Old owner goal draft").assertDoesNotExist()
        compose.pilotClick("Savings Goal")
        compose.onNodeWithText("Learning fund").assertDoesNotExist()
        compose.onNodeWithText("Fictional contribution").assertDoesNotExist()
        text("No savings goal recorded. Confirm your own target and opening allocation.")
    }

    @Test fun longNamesLargeAmountsAndMonthContextRemainInspectable() {
        val data = PhaseCFixtures()
        val name = "A longer savings goal name for reviewing complete amounts and readable labels"
        data.goalState.value = GoalSnapshot(SavingsGoal(data.config.copy(name = name, targetMinor = Long.MAX_VALUE,
            openingMinor = Long.MAX_VALUE - 100), Long.MAX_VALUE - 100, 0, "large"), emptyList(), true, false)
        val templateName = "A longer household agreement name with a large suggested amount"
        data.templates.value = ReportingBatch(listOf(data.rent.copy(config = data.rent.config.copy(name = templateName, suggestedMinor = Long.MAX_VALUE))), false)
        show(data); compose.pilotClick("Savings Goal")
        text(name); text("Saved: LKR 92,233,720,368,547,757.07"); text("LKR 1.00")
        compose.pilotScreenshot("phase-c-after-large-goal")
        compose.pilotClick("Back"); compose.pilotClick("Recurring Expenses")
        click("Inspect $templateName"); text(templateName); text("LKR 92,233,720,368,547,758.07")
        compose.pilotScreenshot("phase-c-after-large-recurring")
        val window = "From ${com.clarity.finance.ui.pilot.PresentationFormat.month(data.first.toString().take(7))} · 12-month window"
        text(window); click("Inspect actual expense"); click("Back"); text(window)
        compose.pilotClick("Back"); compose.pilotClick("Back"); compose.pilotClick("Crypto Results")
        click("Previous month"); val previous = com.clarity.finance.ui.pilot.PresentationFormat.month(data.today.minusMonths(1).toString().take(7))
        text(previous); click("Add result"); click("Close form"); text(previous)
    }

    @Test fun cryptoBackdatingAcrossYearUpdatesPeriodsAndPreservesOtherFinances() {
        val data = PhaseCFixtures(); show(data); compose.pilotClick("Crypto Results")
        click("Gain LKR +60,000.00"); click("Edit result")
        val date = data.today.minusYears(1).toString()
        field("Result date", date); click("Save result")
        text("Month net: LKR -25,000.00")
        click("Previous year"); text("Month net: LKR +60,000.00")
        text("Reporting year ${data.today.year - 1}: 1 Jan ${data.today.year - 1} through 31 Dec ${data.today.year - 1}")
        click("Gain LKR +60,000.00")
        compose.onNode(hasText(com.clarity.finance.ui.pilot.PresentationFormat.date(date)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, data.cryptoOperations.size); assertEquals(6000000L, data.cryptoOperations.values.single().result.amountMinor)
            assertEquals(date, data.cryptoOperations.values.single().result.resultDate)
            assertEquals(2000000L, data.goalState.value.goal!!.savedMinor)
            assertEquals(3400000L, data.expenseRows.value.single().amountMinor)
            assertEquals(1, data.confirmations.value.size); assertTrue(data.recurringOperations.isEmpty()); assertTrue(data.goalOperations.isEmpty())
        }
    }
}
