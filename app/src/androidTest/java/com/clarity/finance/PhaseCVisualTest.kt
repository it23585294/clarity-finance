package com.clarity.finance

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.ui.theme.ClarityTheme
import com.clarity.finance.data.repository.*
import com.clarity.finance.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseCVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun matchingInitialFormsHistoryScheduleAndPendingStates() {
        val args = InstrumentationRegistry.getArguments()
        val before = args.getString("phaseCBefore") == "true"
        val dark = args.getString("phaseCDark") == "true"
        val narrow = args.getString("phaseCNarrow") == "true"
        if (args.getString("phaseCDevice320") == "true") {
            val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            assertEquals(320, config.screenWidthDp); assertEquals(2f, config.fontScale, 0.01f)
        }
        val data = PhaseCFixtures(); val graph = data.graph()
        compose.setContent {
            val d = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        val prefix = if (before) "phase-c-before" else "phase-c-after"
        fun shot(name: String) { androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.pilotScreenshot("$prefix-$name") }
        fun click(label: String) {
            val base = (hasText(label) or hasContentDescription(label)) and hasClickAction() and hasSetTextAction().not()
            val modal = base and hasAnyAncestor(isDialog())
            val matcher = if (compose.onAllNodes(modal).fetchSemanticsNodes().isNotEmpty()) modal else base
            val list = when {
                compose.onAllNodesWithTag("goal-history").fetchSemanticsNodes().isNotEmpty() -> "goal-history"
                compose.onAllNodesWithTag("recurring-list").fetchSemanticsNodes().isNotEmpty() -> "recurring-list"
                else -> null
            }
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() && list != null) compose.onNodeWithTag(list).performScrollToNode(matcher)
            val node = compose.onNode(matcher)
            try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
            node.performClick()
        }
        compose.pilotClick("Savings Goal"); shot("goal-initial")
        click("Record allocation"); shot("goal-allocation-form"); click("Close form")
        click("Edit goal"); shot("goal-form"); click("Close form")
        compose.onNodeWithTag("goal-history").performScrollToNode(hasTestTag("goal-edit-contribution"))
        shot("goal-history")
        if (!before) {
            compose.onNodeWithTag("goal-history").performScrollToNode(hasTestTag("goal-delete-withdrawal"))
            compose.onNodeWithTag("goal-delete-withdrawal").performClick()
            shot("goal-delete-confirmation"); click("Keep entry")
        }
        click("Inspect contribution schedule"); shot("goal-schedule"); click("Close schedule")
        compose.pilotClick("Back")
        compose.pilotClick("Recurring Expenses"); shot("recurring-initial")
        click(if (before) "Create recurring template" else "Add template"); shot("recurring-template-form"); click("Close form")
        click("Inspect Rent"); shot("recurring-detail")
        if (!before) { click("Cancel template"); shot("recurring-cancel-confirmation"); click("Keep template") }
        click("Review ${if (before) data.today.toString().take(7) else com.clarity.finance.ui.pilot.PresentationFormat.month(data.today.toString().take(7))} payment"); shot("recurring-actual-form"); click("Close form")
        compose.onNodeWithTag("recurring-list").performScrollToNode(hasText(if (before)
            "Confirmed originally: LKR 34000.00 on ${data.first}" else "Confirmed originally: LKR 34,000.00 on ${com.clarity.finance.ui.pilot.PresentationFormat.date(data.first.toString())}"))
        shot("recurring-history")
        compose.pilotClick("Back"); compose.pilotClick("Back")
        compose.pilotClick("Crypto Results"); shot("crypto-initial")
        click(if (before) "Add Result" else "Add result"); shot("crypto-form"); click(if (before) "Close Form" else "Close form")
        click(if (before) "Gain LKR +60000.00" else "Gain LKR +60,000.00")
        shot("crypto-inspection")
        if (!before) { click("Delete result"); shot("crypto-delete-confirmation"); click("Keep result") }
        click("Close")
        compose.runOnIdle {
            val baseline = data.cryptoState.value.results
            val operation = CryptoOperation("pending-edit", "UPDATE", baseline.first().copy(amountMinor = 7000000), baseline.first().updatedAt)
            data.cryptoState.value = CryptoSnapshot(results = listOf(operation.result.copy(sync = CryptoSync.PENDING)) + baseline.drop(1),
                attempts = listOf(operation), confirmedResults = baseline, loaded = true, fromCache = true)
        }
        compose.onNodeWithText("1 pending change awaiting server confirmation.").performScrollTo().assertIsDisplayed()
        shot("crypto-pending")
        compose.pilotClick("Back"); compose.pilotClick("Savings Goal")
        compose.runOnIdle {
            val op = GoalOperation("pending-correction", "ENTRY_UPDATE", 2, entry = data.entries.first().copy(amountMinor = 900000))
            data.goalState.value = data.goalState.value.copy(fromCache = true, attempts = listOf(GoalAttempt(op, "UNCERTAIN", "Confirmation unavailable")))
        }
        compose.onNodeWithTag("goal-history").performScrollToIndex(0); shot("goal-uncertain")
        compose.pilotClick("Back"); compose.pilotClick("Recurring Expenses")
        compose.runOnIdle {
            val c = RecurringConfirmation("rent", data.today.toString().take(7), data.today.withDayOfMonth(1).toString(), 0, "Rent",
                ActualRecurringExpense(3400000, data.today.toString(), "housing", "committed", "cash"))
            data.recurringRecovery.value = RecurringRecovery(listOf(RecurringAttempt(RecurringOperation("rejected-confirmation", "CONFIRM", "rent", 0, confirmation = c), "REJECTED", "Verified version conflict")))
        }
        compose.onNodeWithTag("recurring-list").performScrollToIndex(0); shot("recurring-rejected")
        compose.runOnIdle { assertTrue(data.goalOperations.isEmpty()); assertTrue(data.recurringOperations.isEmpty()); assertTrue(data.cryptoOperations.isEmpty()) }
    }
}
