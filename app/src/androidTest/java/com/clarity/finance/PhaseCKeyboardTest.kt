package com.clarity.finance

import android.content.res.Configuration
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Bounds in native screen coordinates, including a Dialog's separate window and the real IME. */
class PhaseCKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun allocationActualPaymentAndCryptoActionsRemainAboveRealKeyboard() {
        val data = PhaseCFixtures(); val graph = data.graph()
        val dark = InstrumentationRegistry.getArguments().getString("phaseCDark") == "true"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val service = automation.serviceInfo
        val originalFlags = service.flags
        service.flags = service.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = service
        PilotTestActivity.content = {
            val d = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(d.density, 2f)) {
                ClarityTheme { Box(Modifier.width(320.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        fun click(label: String) {
            val matcher = (hasText(label) or hasContentDescription(label)) and hasClickAction()
            val list = listOf("goal-history", "recurring-list").firstOrNull { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() && list != null) compose.onNodeWithTag(list).performScrollToNode(matcher)
            val node = compose.onNode(matcher)
            try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
            node.performClick()
        }
        fun bounds(node: AccessibilityNodeInfo?, text: String): Rect? {
            if (node == null) return null
            if (node.text?.toString() == text && node.isVisibleToUser) return Rect().also { node.getBoundsInScreen(it) }
            for (i in 0 until node.childCount) bounds(node.getChild(i), text)?.let { return it }
            return null
        }
        fun check(label: String, screenshot: String) {
            compose.waitUntil(5000) { automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
            val ime = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .map { Rect().also(it::getBoundsInScreen) }.minOf { it.top }
            fun actionBounds() = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .firstNotNullOfOrNull { bounds(it.root, label) }
            // The platform accessibility tree can lag behind Compose semantics/IME animation.
            compose.waitUntil(5000) { actionBounds() != null }
            val button = actionBounds()
            assertNotNull("$label must be exposed in the dialog accessibility tree", button)
            assertTrue("$label must fit above the real IME", button!!.bottom <= ime + 1)
            assertTrue("$label must have nonempty native bounds", button.width() > 0 && button.height() > 0)
            compose.pilotScreenshot(screenshot)
        }
        fun type(label: String) { compose.onNodeWithText(label).performScrollTo().performClick().performTextReplacement("25") }
        try {
            ActivityScenario.launch(PilotTestActivity::class.java).use {
                compose.pilotClick("Savings Goal"); click("Record allocation"); type("Amount (LKR)")
                check("Save proposal", "phase-c-after-allocation-keyboard-2x"); click("Close form")
                compose.pilotClick("Back"); compose.pilotClick("Recurring Expenses"); click("Inspect Rent")
                click("Review ${com.clarity.finance.ui.pilot.PresentationFormat.month(data.today.toString().take(7))} payment")
                type("Actual (LKR)"); check("Confirm payment proposal", "phase-c-after-recurring-keyboard-2x"); click("Close form")
                compose.pilotClick("Back"); compose.pilotClick("Back"); compose.pilotClick("Crypto Results"); click("Add result")
                type("Amount (LKR)"); check("Save result", "phase-c-after-crypto-keyboard-2x")
                compose.runOnIdle { assertTrue(data.goalOperations.isEmpty()); assertTrue(data.recurringOperations.isEmpty()); assertTrue(data.cryptoOperations.isEmpty()) }
            }
        } finally {
            PilotTestActivity.content = null; graph.close()
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }
}
