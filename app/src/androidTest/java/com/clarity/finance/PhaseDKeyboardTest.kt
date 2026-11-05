package com.clarity.finance

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseDKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun keyboardNextDoneAndBothAuthActionsRemainReachableAtSystemDoubleFont() {
        val args = InstrumentationRegistry.getArguments()
        val dark = args.getString("phaseDDark") == "true"
        val actual = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertEquals(320, actual.screenWidthDp); assertEquals(2f, actual.fontScale, .01f)
        val data = PhaseDFixtures(); val graph = data.graph()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val flags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { this.flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        PilotTestActivity.content = {
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config) {
                ClarityTheme { Box(Modifier.width(320.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        fun find(node: AccessibilityNodeInfo?, label: String): Rect? {
            if (node == null) return null
            if (node.text?.toString() == label && node.isVisibleToUser) return Rect().also(node::getBoundsInScreen)
            for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
            return null
        }
        fun check(label: String, scene: String) {
            compose.waitUntil(5000) { automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo().assertIsDisplayed()
            fun action() = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.firstNotNullOfOrNull { find(it.root, label) }
            compose.waitUntil(5000) { action() != null }
            val ime = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.minOf { Rect().also(it::getBoundsInScreen).top }
            assertTrue("$label must be fully above the real IME", action()!!.bottom <= ime + 1)
            if (args.getString("pilotScreenshots") == "true") {
                // Root capture omits the native IME; capture the inspected fake-only device window.
                automation.waitForIdle(100, 2000)
                android.os.SystemClock.sleep(400)
                val folder = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "pilot-screenshots").apply { mkdirs() }
                val suffix = args.getString("pilotScreenshotSuffix").orEmpty()
                java.io.File(folder, "$scene$suffix.png").outputStream().use { stream ->
                    checkNotNull(automation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }
        fun type(label: String, value: String) = compose.onNodeWithText(label).performScrollTo().performClick().performTextReplacement(value)
        try {
            ActivityScenario.launch(PilotTestActivity::class.java).use {
                type("Email", "review@example.test"); compose.onNodeWithText("Email").performImeAction()
                compose.onNodeWithText("Password").assertIsFocused(); type("Password", "")
                check("Sign In", "phase-d-after-login-keyboard")
                Espresso.closeSoftKeyboard(); compose.pilotClick("Create an account")
                type("Email", "review@example.test"); compose.onNodeWithText("Email").performImeAction()
                compose.onNodeWithText("Password").assertIsFocused(); type("Password", "")
                compose.onNodeWithText("Password").performImeAction(); compose.onNodeWithText("Confirm Password").assertIsFocused()
                type("Confirm Password", ""); check("Create Account", "phase-d-after-register-keyboard")
                compose.runOnIdle { assertEquals(0, data.loginCalls); assertEquals(0, data.registerCalls) }
            }
        } finally {
            PilotTestActivity.content = null; graph.close()
            automation.serviceInfo = automation.serviceInfo.apply { this.flags = flags }
        }
    }
}
