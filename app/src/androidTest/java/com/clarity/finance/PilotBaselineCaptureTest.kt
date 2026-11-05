package com.clarity.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in historical capture. Fake-only; retained as evidence tooling, not a live audit. */
class PilotBaselineCaptureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun acceptedBeforeScreensWithFictionalRecords() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("capturePilotBaseline") == "true")
        val data = PilotFixtures()
        compose.setContent { ClarityTheme { IsolatedAppNavigation(data.graph()) } }
        compose.onNodeWithText("Dashboard").performScrollTo().performClick()
        compose.onNodeWithText("LKR 75000.00").performScrollTo()
        compose.pilotScreenshot("before-overview")
        compose.onNodeWithText("Monthly history").performScrollTo().performClick()
        compose.onNodeWithText("Rent").performScrollTo()
        compose.pilotScreenshot("before-history")
        compose.onNodeWithText("Rent").performClick()
        compose.onNodeWithText("Amount (LKR)").assertIsDisplayed()
        compose.pilotScreenshot("before-inspection")
    }
}
