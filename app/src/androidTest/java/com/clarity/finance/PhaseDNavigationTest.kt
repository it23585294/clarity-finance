package com.clarity.finance

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.theme.ClarityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseDNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativeWindowResourcesMatchTheCanonicalThemeInBothConfigurations() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (dark in listOf(false, true)) {
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            val resources = context.createConfigurationContext(config).resources
            val colors = com.clarity.finance.ui.theme.clarityColorScheme(dark)
            assertEquals(colors.background.toArgb(), resources.getColor(R.color.clarity_window_background, null))
            assertEquals(colors.primary.toArgb(), resources.getColor(R.color.clarity_window_accent, null))
        }
    }
    @Test fun everyFeatureRemainsReachableWithoutSubmittingFinancialChanges() {
        val data = PhaseDFixtures().apply { signIn() }; val graph = data.graph()
        compose.setContent { ClarityTheme { Box(Modifier.width(390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } } }
        fun click(label: String) = compose.pilotClick(label)
        fun close() = click("Back")
        click("Add Expense"); compose.onNodeWithText("Amount (LKR)").assertExists(); click("Cancel")
        click("History"); click("Received Income"); click("Add Income"); compose.onNodeWithText("Original Amount").assertExists(); click("Cancel"); close()
        click("Pending Payments"); click("Add agreement"); compose.onNodeWithText("Add agreement").assertExists(); close(); close()
        click("Savings Goal"); click("Record allocation"); compose.onNodeWithText("Contribution").assertExists(); click("Close form"); close()
        click("Recurring Expenses"); click("Add template"); compose.onNodeWithText("Recurring template details").assertExists(); click("Close form"); close()
        click("Crypto Results"); click("Add result"); compose.onNodeWithText("Add realised result").assertExists(); click("Close form"); close()
        click("Categories & Sources"); compose.onNodeWithText("Categories & Sources").assertExists(); close()
        click("Saved Changes"); click("Saved changes")
        compose.onNodeWithText("Saved transaction, project and catalogue changes").assertExists(); close()
        click("History"); click("All recorded expenses"); compose.onNodeWithText("LKR 34,000.00").assertExists(); close()
        click("Spending insights"); compose.onNodeWithText("Spending insights").assertExists()
        compose.runOnIdle {
            assertEquals(2, data.finance.goalState.value.entries!!.size)
            assertEquals(1, data.finance.expenseRows.value.size)
            assertTrue(data.finance.goalOperations.isEmpty()); assertTrue(data.finance.recurringOperations.isEmpty()); assertTrue(data.finance.cryptoOperations.isEmpty())
            assertTrue(data.agreements.savedIncome.isEmpty()); assertTrue(data.agreements.receipts.isEmpty()); assertTrue(data.agreements.writes.isEmpty())
        }
    }
}
