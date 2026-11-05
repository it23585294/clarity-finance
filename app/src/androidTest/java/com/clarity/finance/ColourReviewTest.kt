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
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/** Read-only visual comparison. Explicit fake graph; every catalogue/recovery write fails. */
class ColourReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun matchingOverviewIncomeFormAndSelectedCatalogueStates() {
        val args = InstrumentationRegistry.getArguments()
        val variant = args.getString("colourVariant") ?: "current"
        require(variant in listOf("current", "proposed"))
        val dark = args.getString("colourDark") == "true"
        val data = PilotFixtures()
        val income = PhaseBFixtures()
        val stamp = Timestamp(1, 0)
        val category = CatalogueEntry("food_dining", "category", "Food & Dining", false, "committed", stamp)
        val recovery = object : FinancialRecoveryAccess {
            override fun observe(uid: String) = flowOf(FinancialRecovery(listOf(
                FinancialAttempt(FinancialOperation("colour-unknown", "CATEGORY", "UPDATE", category.id,
                    expectedUpdatedAt = stamp, catalogue = category), "UNCERTAIN", "Original proposal retained."),
                FinancialAttempt(FinancialOperation("colour-rejected", "CATEGORY", "UPDATE", "other",
                    expectedUpdatedAt = stamp, catalogue = category.copy(id = "other", name = "Other")), "REJECTED", "Recorded history is unchanged."))))
            override suspend fun submit(uid: String, operation: FinancialOperation) = error("No writes in colour review")
            override suspend fun resolve(uid: String, operation: FinancialOperation) = error("No recovery actions in colour review")
            override suspend fun dismissRejected(uid: String, operation: FinancialOperation) = error("No dismissals in colour review")
        }
        val graph = isolatedAppGraph(auth = data.auth, expense = data, reporting = data,
            income = income, recovery = recovery)
        compose.setContent {
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density.density, 1f)) {
                ClarityTheme { Box(Modifier.width(390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
        val prefix = "colour-$variant-${if (dark) "dark" else "light"}"
        listOf("LKR 75,000.00", "LKR 120,000.00", "LKR 45,000.00").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.pilotScreenshot("$prefix-overview")
        compose.pilotClick("History"); compose.pilotClick("Received Income"); compose.pilotClick("Add Income")
        // Keep this unsaved fictional form identical if capture crosses midnight.
        compose.onNodeWithText("Receipt Date (yyyy-MM-dd)").performScrollTo().performTextReplacement("2026-11-04")
        compose.onNodeWithText("Original Amount").performScrollTo().performTextInput("1250.50")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNode(hasText("Add Income") and hasClickAction()).performScrollTo().assertIsEnabled().assertIsDisplayed()
        compose.pilotScreenshot("$prefix-income-form")
        compose.pilotClick("Cancel"); compose.pilotClick("Back")
        compose.pilotClick("Categories & Sources")
        compose.onNodeWithText("Categories").assertIsSelected()
        compose.pilotScreenshot("$prefix-categories")
        compose.pilotClick("Saved changes")
        compose.onNodeWithText("Saved changes").assertIsSelected()
        compose.onNodeWithText("Confirmation unknown", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Change not applied", substring = true).performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("$prefix-saved-changes")
        compose.runOnIdle {
            org.junit.Assert.assertTrue(income.savedIncome.isEmpty())
            org.junit.Assert.assertTrue(income.receipts.isEmpty())
            org.junit.Assert.assertTrue(data.submitted.isEmpty())
        }
    }
}
