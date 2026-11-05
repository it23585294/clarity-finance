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
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Rule
import org.junit.Test

/** Matching initial-scroll evidence. Standalone fictional graph; never a live audit. */
class PhaseAVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun initialOverviewAndHistoryThenFormAndInspection() {
        val args = InstrumentationRegistry.getArguments()
        val before = args.getString("phaseABefore") == "true"
        val narrow = args.getString("phaseANarrow") == "true"
        val data = PilotFixtures()
        val graph = data.graph()
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        val prefix = if (before) "phase-a-before" else "phase-a-after"
        compose.onNodeWithText("Based on recorded transactions").assertIsDisplayed()
        if (!before && !narrow) {
            listOf("LKR 75,000.00", "LKR 120,000.00", "LKR 45,000.00").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        }
        compose.pilotScreenshot("$prefix-overview-initial") // no scroll to an amount
        compose.pilotClick("History")
        if (!before) {
            compose.onNodeWithTag("reporting-period").assertIsDisplayed()
            org.junit.Assert.assertEquals(0f, compose.onNodeWithTag("reporting-scroll-history")
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value())
            if (!narrow) compose.onNodeWithText("Groceries").assertIsDisplayed()
        }
        compose.pilotScreenshot("$prefix-history-initial") // no scroll to a transaction
        compose.onNodeWithText("Rent").performScrollTo().performClick()
        compose.pilotScreenshot("$prefix-inspection")
        compose.pilotClick("Back")
        compose.pilotClick("Add Expense")
        compose.onNodeWithText("Amount (LKR)").performTextInput("750")
        compose.pilotScreenshot("$prefix-form")
    }
}
