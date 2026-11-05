package com.clarity.finance

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.User
import com.clarity.finance.ui.theme.ClarityTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Production navigation/screens, standalone Koin and fictional repositories only. */
class PilotUiTest {
    @get:Rule val compose = createComposeRule()
    private fun show(data: PilotFixtures, width: Int = 390, scale: Float = 1f) {
        val graph = data.graph()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                ClarityTheme { Box(Modifier.width(width.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
    }

    @Test fun overviewHistoryInspectionContextAndRepeatedSaveRetainConfirmedTotals() {
        val data = PilotFixtures().apply { admission = CompletableDeferred() }
        show(data)
        compose.onNodeWithText("LKR 75,000.00").performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("after-overview")
        compose.pilotClick("History")
        compose.pilotClick("Housing")
        compose.onNodeWithText("Rent").performScrollTo().performClick()
        compose.onNodeWithText("Expense details").assertIsDisplayed()
        compose.pilotScreenshot("after-inspection")
        compose.pilotClick("Back")
        compose.onNodeWithText("Rent").assertIsDisplayed() // retained scroll position, without a new scroll action
        compose.onNodeWithText("Expense category: Housing").performScrollTo().assertIsDisplayed()
        compose.pilotClick("Plans"); compose.pilotClick("More"); compose.pilotClick("History")
        compose.onNodeWithText("Expense category: Housing").performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("after-history")
        compose.pilotClick("Add Expense")
        compose.onNodeWithText("Amount (LKR)").performTextInput("750")
        compose.pilotScreenshot("after-add-expense")
        compose.onNode(hasText("Save") and hasClickAction()).performScrollTo().performTouchInput { doubleClick() }
        compose.onNode(hasText("Save") and hasClickAction()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, data.submitted.size); data.admission!!.complete(Unit) }
        compose.pilotClick("Overview")
        compose.onNodeWithText("LKR 45,000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reporting saved changes: 1 unresolved", substring = true).performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("after-pending")
        compose.runOnIdle { data.session.value = User("pilot-b", "Other fictional account") }
        compose.onNodeWithText("LKR 75,000.00").assertDoesNotExist()
        compose.onNodeWithText("Reporting saved changes: 1 unresolved", substring = true).assertDoesNotExist()
    }

    @Test fun narrowDoubleTextKeepsNavigationLabelsKeyboardAndControlsReachable() {
        val data = PilotFixtures()
        show(data, 320, 2f)
        listOf("Overview", "History", "Plans", "More").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.pilotClick("Add Expense")
        compose.onNodeWithText("Amount (LKR)").performClick().performTextInput("0")
        compose.pilotClick("Committed")
        compose.onNode(hasText("Committed") and hasClickAction()).assertIsSelected()
        compose.pilotClick("Save")
        compose.onNodeWithText("Amount must be greater than zero.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, data.submitted.size) }
        compose.onNodeWithText("Amount (LKR)").performScrollTo().performTextReplacement("875.25")
        compose.pilotScreenshot("after-narrow-2x-form")
        compose.onNode(hasText("Cancel") and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Test fun unavailableAndCachedSectionsStayHonestWithoutFabricatedSurplus() {
        val data = PilotFixtures().apply { cached = true; unavailable = true }
        show(data)
        compose.onNodeWithText("Income and expense data must both be available to calculate surplus.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LKR 120,000.00").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Showing saved data; may be incomplete or out of date.")[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Expenses unavailable in this isolated example").performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("after-unavailable-cached")
    }

    @Test fun cachedSurplusIdentifiesItsBasisNextToTheAmount() {
        show(PilotFixtures().apply { cached = true })
        compose.onNodeWithText("LKR 75,000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Surplus uses saved data; it may be out of date.").assertIsDisplayed()
    }

    @Test fun narrowDoubleTextInspectionAndKeepDeletionRemainReachable() {
        val data = PilotFixtures()
        show(data, 320, 2f)
        compose.onNodeWithText("LKR 75,000.00").performScrollTo().assertIsDisplayed()
        compose.pilotClick("History"); compose.pilotClick("Housing")
        compose.onNodeWithText("Rent").performScrollTo().performClick()
        compose.onNodeWithText("LKR 34,000.00").performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("after-narrow-2x-inspection")
        compose.pilotClick("Delete")
        compose.onNodeWithText("Confirm Deletion").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(3, data.rows.value.size) }
        compose.pilotClick("Back")
        compose.onNodeWithText("Expense category: Housing").performScrollTo().assertIsDisplayed()
    }
}
