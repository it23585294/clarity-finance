package com.clarity.finance

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Rule
import org.junit.Test

class PhaseBVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun matchingInitialScreensFormsAndArchivedReferences() {
        val args = InstrumentationRegistry.getArguments()
        val before = args.getString("phaseBBefore") == "true"
        val narrow = args.getString("phaseBNarrow") == "true"
        val dark = args.getString("phaseBDark") == "true"
        if (args.getString("phaseBDevice320") == "true") {
            val actual = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            org.junit.Assert.assertEquals(320, actual.screenWidthDp)
            org.junit.Assert.assertEquals(2f, actual.fontScale, 0.01f)
        }
        val data = PhaseBFixtures(); val graph = data.graph()
        compose.setContent { val d = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        val p = if (before) "phase-b-before" else "phase-b-after"
        compose.pilotClick("History"); compose.pilotClick("Received Income")
        compose.pilotScreenshot("$p-income-initial")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Note: Fictional overseas work"))
        compose.onNodeWithText("Note: Fictional overseas work").performClick()
        if (!before) compose.onNodeWithText("Income details").assertIsDisplayed()
        compose.pilotScreenshot("$p-income-archived")
        if (!before) {
            compose.pilotClick("Edit income"); compose.pilotClick("Source")
            compose.onNode(hasText("Consulting (archived)") and hasAnyAncestor(isPopup())).assertIsDisplayed()
            compose.pilotScreenshot("$p-income-archived-choice")
            compose.onNode(hasText("Consulting (archived)") and hasAnyAncestor(isPopup())).performClick()
        }
        compose.pilotClick(if (before) "Cancel" else "Back")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.pilotClick("Add Income")
        compose.onNodeWithText("Original Amount").performScrollTo().performTextInput("100")
        compose.pilotScreenshot("$p-income-form")
        compose.pilotClick("Cancel"); compose.pilotClick("Back")
        compose.pilotClick("Pending Payments")
        compose.pilotScreenshot("$p-agreements-initial")
        compose.pilotClick(if (before) "Add Payment" else "Add agreement")
        compose.pilotScreenshot("$p-agreement-form")
        compose.pilotClick("Back")
        compose.onNodeWithText("Studio website").performScrollTo().performClick()
        compose.pilotScreenshot("$p-project-initial")
        compose.pilotClick(if (before) "Add Milestone" else "Add milestone")
        compose.onNode(isDialog()).assertIsDisplayed()
        if (!before) compose.onNode(hasText("Consulting (archived)") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.pilotScreenshot("$p-milestone-form")
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.pilotClick(if (before) "Record Direct" else "Record payment")
        compose.pilotScreenshot("$p-direct-receipt")
        compose.pilotClick("Back"); compose.pilotClick("Back"); compose.pilotClick("Back")
        compose.pilotClick("Categories & Sources")
        compose.pilotScreenshot("$p-catalogue-initial")
        if (!before) compose.pilotClick("Saved changes")
        compose.onNodeWithText(if (before) "Reconcile or withdraw safely (online)" else "Check outcome or withdraw safely").performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("$p-uncertain-recovery")
    }

    @Test fun pendingCachedAndLockedStatesKeepConfirmedFiguresAndEligibility() {
        val data = PhaseBFixtures()
        data.incomes.value = data.incomes.value.map { it.copy(isFromCache = true,
            queuedChange = if (it.id == "salary") "Saved edit · Confirmation unknown; recorded amount retained" else null) } +
            data.incomes.value.first().copy(id = "pending", isPending = true, lkrAmountMinor = 75000, originalAmountStr = "750")
        data.projects.value = data.projects.value.map { it.copy(isFromCache = true, queuedChange = "Saved agreement edit · Confirmation unknown") }
        val graph = data.graph()
        compose.setContent { ClarityTheme { Box(Modifier.width(390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } } }
        compose.pilotClick("History"); compose.pilotClick("Received Income")
        compose.onNodeWithText("LKR 131,150.00").assertIsDisplayed()
        compose.onNodeWithText("Not yet confirmed: LKR 750.00").assertIsDisplayed()
        compose.onNodeWithText("Includes saved data; it may be out of date.").assertIsDisplayed()
        compose.pilotScreenshot("phase-b-after-cached-pending-income")
        compose.pilotClick("Back"); compose.pilotClick("Pending Payments")
        compose.onNodeWithText("Studio website").performScrollTo().performClick()
        compose.onNodeWithText("Record payment").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Add milestone").assertIsNotEnabled()
        compose.onNodeWithText("Cancel agreement").assertIsNotEnabled()
        compose.pilotScreenshot("phase-b-after-uncertain-agreement")
        compose.runOnIdle {
            data.projects.value = data.projects.value.map { it.copy(queuedChange = null, isFromCache = false) }
            data.unresolvedReceipt = data.incomes.value.last().copy(id = "unresolved-receipt", note = "Fictional saved payment")
        }
        compose.pilotClick("Record payment")
        compose.onNodeWithText("Amount (LKR)").assertIsNotEnabled()
        compose.onNodeWithText("Retry Payment").performScrollTo().assertIsEnabled()
        compose.pilotScreenshot("phase-b-after-locked-receipt")
    }
}
