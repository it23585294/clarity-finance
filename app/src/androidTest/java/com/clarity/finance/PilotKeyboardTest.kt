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
import com.clarity.finance.ui.navigation.AppNavigation
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PilotKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun enlargedExpenseControlsRemainAboveRealKeyboard() {
        val data = PilotFixtures(); val graph = data.graph()
        lateinit var host: View
        PilotTestActivity.content = {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                ClarityTheme { Box(Modifier.width(320.dp).fillMaxHeight()) { AppNavigation(graph.koin) } }
            }
        }
        try {
            ActivityScenario.launch(PilotTestActivity::class.java).use {
                compose.pilotClick("Add Expense")
                compose.onNodeWithText("Amount (LKR)").performClick().performTextInput("850")
                compose.waitUntil(5000) { ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { it > 0 } == true }
                val save = compose.onNode(hasText("Save") and hasClickAction())
                save.performScrollTo().assertIsDisplayed()
                compose.waitForIdle()
                val frame = Rect(); val location = IntArray(2)
                compose.runOnIdle { host.getWindowVisibleDisplayFrame(frame); host.getLocationOnScreen(location) }
                val bounds = save.fetchSemanticsNode().boundsInRoot
                assertTrue("Save must fit above the real IME", bounds.bottom + location[1] <= frame.bottom + 1)
                compose.pilotScreenshot("after-keyboard-2x")
                compose.onNode(hasText("Cancel") and hasClickAction()).performScrollTo().assertIsDisplayed()
            }
        } finally { PilotTestActivity.content = null; graph.close() }
    }
}
