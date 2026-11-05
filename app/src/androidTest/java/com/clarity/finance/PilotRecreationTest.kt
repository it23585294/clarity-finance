package com.clarity.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.clarity.finance.ui.navigation.AppNavigation
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred

class PilotRecreationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun actualActivityRecreationRetainsExpenseDraftRouteAndReportingOwner() {
        val data = PilotFixtures()
        val graph = data.graph()
        PilotTestActivity.content = { ClarityTheme { AppNavigation(graph.koin) } }
        try {
            ActivityScenario.launch(PilotTestActivity::class.java).use { scenario ->
                compose.pilotClick("Add Expense")
                compose.onNodeWithText("Amount (LKR)").performTextInput("9876.54")
                compose.runOnIdle { assertEquals(1, data.reportingStarts) }
                scenario.recreate()
                compose.onNodeWithText("Amount (LKR)").assertTextContains("9876.54")
                compose.onNode(hasText("Plans") and hasClickAction()).assertDoesNotExist()
                compose.runOnIdle { assertEquals(1, data.reportingStarts); assertEquals(0, data.submitted.size) }
                compose.runOnIdle { data.admission = CompletableDeferred() }
                compose.pilotClick("Save")
                compose.runOnIdle { assertEquals(1, data.submitted.size) }
                scenario.recreate()
                compose.onNodeWithText("Amount (LKR)").assertTextContains("9876.54")
                compose.runOnIdle {
                    assertEquals(1, data.submitted.size)
                    assertEquals(1, data.reportingStarts)
                    data.admission!!.complete(Unit)
                }
            }
        } finally { PilotTestActivity.content = null; graph.close() }
    }
}
