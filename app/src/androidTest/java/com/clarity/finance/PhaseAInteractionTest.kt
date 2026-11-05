package com.clarity.finance

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.CatalogueRepository
import com.clarity.finance.ui.pilot.PresentationFormat
import com.clarity.finance.ui.theme.ClarityTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PhaseAInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun show(data: PilotFixtures, narrow: Boolean = false, catalogue: CatalogueRepository? = null) {
        val graph = if (catalogue == null) data.graph() else isolatedAppGraph(auth = data.auth, expense = data, reporting = data, catalogue = catalogue)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
    }
    @Test fun calendarKeepsBackdatedManualDateAndFutureSaveIsStillRejected() {
        val data = PilotFixtures(); show(data)
        compose.pilotClick("Add Expense")
        compose.onNodeWithText("Amount (LKR)").performTextInput("100")
        val backdated = LocalDate.parse(data.today).minusMonths(1).withDayOfMonth(1).toString()
        compose.onNodeWithText("Date (yyyy-MM-dd)").performTextReplacement(backdated)
        compose.onNodeWithContentDescription("Choose payment date").performClick()
        compose.onNodeWithText("Use date").performClick()
        compose.onNodeWithText("Date (yyyy-MM-dd)").assertTextContains(backdated)
        compose.onNodeWithText("Date (yyyy-MM-dd)").performTextReplacement(LocalDate.parse(data.today).plusDays(1).toString())
        compose.pilotClick("Save")
        compose.onNodeWithText("future", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, data.submitted.size) }
    }
    @Test fun monthAndIncomeModeSurviveHubVisits() {
        val data = PilotFixtures(); show(data)
        compose.pilotClick("History"); compose.pilotClick("Previous month"); compose.pilotClick("Income")
        val previous = java.time.YearMonth.parse(data.today.take(7)).minusMonths(1).toString()
        compose.pilotClick("Plans"); compose.pilotClick("History")
        compose.onNodeWithTag("reporting-period").assertTextEquals(PresentationFormat.month(previous))
        compose.onNodeWithText("Income").assertIsSelected()
        compose.pilotClick("Next month")
        compose.onNodeWithText("Fictional salary").performScrollTo().assertIsDisplayed()
    }
    @Test fun longHistoricalNamesAndMaximumAmountRemainFullyInspectableAtDoubleText() {
        val data = PilotFixtures()
        val name = "Household rent and shared accommodation expenses with a deliberately long retained category name"
        data.rows.value = listOf(data.rows.value.first().copy(amountMinor = Long.MAX_VALUE))
        val catalogue = object : CatalogueRepository {
            override fun observe(uid: String) = flowOf(CatalogueSnapshot(CatalogueDefaults.entries().map {
                if (it.id == "housing") it.copy(name = name, archived = true) else it
            }, false))
        }
        show(data, true, catalogue)
        compose.pilotClick("History")
        compose.onNodeWithText("Rent").performScrollTo().performClick()
        compose.onNodeWithText("LKR 92,233,720,368,547,758.07").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("phase-a-large-name-amount-2x")
        compose.pilotClick("Edit expense")
        compose.onNodeWithText("Amount (LKR)").assertTextContains("92233720368547758.07")
        compose.onNodeWithContentDescription("Choose payment date").performScrollTo().performClick()
        compose.onNodeWithText("Keep date").performClick()
        compose.pilotClick("Cancel")
        compose.runOnIdle { assertEquals(Long.MAX_VALUE, data.rows.value.single().amountMinor); assertEquals(0, data.submitted.size) }
    }
}
