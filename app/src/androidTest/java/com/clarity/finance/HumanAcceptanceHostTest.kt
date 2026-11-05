package com.clarity.finance

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.acceptance.AcceptanceSession
import com.clarity.finance.acceptance.HumanAcceptanceActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Smoke check only; these machine actions/timings are NEVER human usability results. */
class HumanAcceptanceHostTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun interactiveHostUsesProductionExpenseFlowWithoutLiveBindings() {
        AcceptanceSession.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, HumanAcceptanceActivity::class.java).putExtra("isolatedAcceptance", true)
        try {
            ActivityScenario.launch<HumanAcceptanceActivity>(intent).use {
                compose.onNodeWithText("ISOLATED ACCEPTANCE", substring = true).assertExists()
                compose.onNodeWithText("LKR 90,300.00").assertExists()
                compose.pilotClick("Add Expense")
                compose.onNodeWithText("Amount (LKR)").performScrollTo().performTextReplacement("850")
                compose.onNodeWithText("Save").performScrollTo().performClick()
                compose.waitUntil(5000) { AcceptanceSession.fixture.createdIds.size == 1 }
                compose.pilotClick("History")
                compose.onNodeWithText("LKR 850.00", substring = true).performScrollTo().assertIsDisplayed()
                compose.runOnIdle {
                    val f = AcceptanceSession.fixture
                    assertEquals(1, f.createdIds.size)
                    assertEquals(85000L, f.rows.value.single { row -> row.id == f.createdIds.single() }.amountMinor)
                    assertTrue(f.rows.value.single { row -> row.id == f.pending.id }.isPending)
                    assertNull(f.graph.koin.getOrNull<com.google.firebase.firestore.FirebaseFirestore>())
                }
            }
        } finally { AcceptanceSession.reset() }
    }
}
