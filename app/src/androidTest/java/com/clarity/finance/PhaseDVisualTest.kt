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
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseDVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun authenticationHubsAllExpensesAndErrors() {
        val args = InstrumentationRegistry.getArguments()
        val before = args.getString("phaseDBefore") == "true"
        val dark = args.getString("phaseDDark") == "true"
        val narrow = args.getString("phaseDNarrow") == "true"
        if (narrow) {
            val actual = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            assertEquals(320, actual.screenWidthDp); assertEquals(2f, actual.fontScale, 0.01f)
        }
        val data = PhaseDFixtures(); val graph = data.graph()
        compose.setContent {
            val d = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        val prefix = if (before) "phase-d-before" else "phase-d-after"
        fun shot(name: String) {
            if (narrow && name !in setOf("login", "register-error", "all-expenses", "list-error")) return
            Espresso.closeSoftKeyboard(); compose.pilotScreenshot("$prefix-$name")
        }
        compose.onNodeWithText("Email").assertExists(); shot("login")
        compose.pilotClick("Sign In"); compose.onNodeWithText("Please enter both email and password.").performScrollTo().assertIsDisplayed(); shot("login-error")
        compose.pilotClick(if (before) "Don't have an account? Register" else "Create an account")
        compose.onNode(hasText("Create Account") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Please fill in all fields.").performScrollTo().assertIsDisplayed(); shot("register-error")
        compose.runOnIdle { assertEquals(0, data.loginCalls); assertEquals(0, data.registerCalls); data.signIn() }
        compose.pilotClick("Plans"); shot("plans")
        compose.pilotClick("More"); shot("more")
        compose.pilotClick("History"); compose.pilotClick("All recorded expenses")
        compose.onNodeWithText(if (before) "Clarity Expenses" else "All recorded expenses").assertExists(); shot("all-expenses")
        compose.onNodeWithText(if (before) "LKR 34000.00" else "LKR 34,000.00").performScrollTo().performClick()
        compose.onNodeWithText("Recorded expense").assertExists(); shot("inspection")
        compose.pilotClick(if (before) "Back to history" else "Back")
        compose.runOnIdle { data.unavailable.value = true }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Expenses unavailable in this isolated example.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("list-error")
        compose.runOnIdle { assertTrue(data.finance.goalOperations.isEmpty()); assertTrue(data.finance.recurringOperations.isEmpty()); assertTrue(data.finance.cryptoOperations.isEmpty()) }
    }
}
