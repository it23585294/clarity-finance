package com.clarity.finance

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseBKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun enlargedIncomeAndReceiptActionsRemainAboveRealKeyboard() {
        val data = PhaseBFixtures(); val graph = data.graph()
        lateinit var host: View
        PilotTestActivity.content = {
            host = LocalView.current
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) {
                ClarityTheme { Box(Modifier.width(320.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        fun checkAction(label: String, screenshot: String) {
            compose.waitUntil(5000) { ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { it > 0 } == true }
            val action = compose.onNode(hasText(label) and hasClickAction())
            action.performScrollTo().assertIsDisplayed(); compose.waitForIdle()
            val frame = Rect(); val location = IntArray(2)
            compose.runOnIdle { host.getWindowVisibleDisplayFrame(frame); host.getLocationOnScreen(location) }
            assertTrue("$label must fit above real IME", action.fetchSemanticsNode().boundsInRoot.bottom + location[1] <= frame.bottom + 1)
            compose.pilotScreenshot(screenshot)
        }
        try {
            ActivityScenario.launch(PilotTestActivity::class.java).use {
                compose.pilotClick("History"); compose.pilotClick("Received Income"); compose.pilotClick("Add Income")
                compose.onNodeWithText("Original Amount").performScrollTo().performClick().performTextInput("10")
                checkAction("Add Income", "phase-b-after-income-keyboard-2x")
                compose.onNode(hasText("Cancel") and hasClickAction()).performScrollTo().performClick()
                compose.pilotClick("Back"); compose.pilotClick("Pending Payments")
                compose.onNodeWithText("Studio website").performScrollTo().performClick(); compose.pilotClick("Record payment")
                compose.onNodeWithText("Amount (LKR)").performScrollTo().performClick().performTextInput("10")
                checkAction("Confirm Payment", "phase-b-after-receipt-keyboard-2x")
                compose.onNodeWithContentDescription("Back").assertIsDisplayed()
                compose.runOnIdle { assertTrue(data.savedIncome.isEmpty()); assertTrue(data.receipts.isEmpty()) }
            }
        } finally { PilotTestActivity.content = null; graph.close() }
    }
}
