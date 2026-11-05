package com.clarity.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.data.model.*
import com.clarity.finance.ui.pilot.PresentationFormat
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Isolated repositories only. No production client, queue, login or mutation. */
class HistoryMetadataTest {
    @get:Rule val compose = createComposeRule()
    private fun stamp(day: String, minute: Int) = Timestamp(Instant.parse("${day}T04:${minute.toString().padStart(2, '0')}:00Z").epochSecond, 0)
    private fun checkConfiguration() {
        if (InstrumentationRegistry.getArguments().getString("historyNarrow") == "true") {
            val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            assertEquals(320, config.screenWidthDp); assertEquals(2f, config.fontScale, 0.01f)
        }
    }
    @Test fun sameDayOrderTimeInformationAndBackContextUseImmutableCreation() {
        checkConfiguration()
        val data = PilotFixtures()
        val old = data.rows.value.first().copy(id = "a-old", note = "Earlier entry", createdAt = stamp(data.today, 30))
        val newer = old.copy(id = "z-new", note = "Latest entry", createdAt = stamp(data.today, 32))
        data.rows.value = listOf(old, newer)
        val graph = data.graph()
        compose.setContent { ClarityTheme { IsolatedAppNavigation(graph) } }
        compose.pilotClick("History")
        fun correctOrder() {
            // Both rows may initially be offscreen at system 2x. Clipped bounds become zero;
            // compare layout positions, then separately assert timestamp visibility after scrolling.
            val newerTop = compose.onNodeWithText("Latest entry").getUnclippedBoundsInRoot().top.value
            val olderTop = compose.onNodeWithText("Earlier entry").getUnclippedBoundsInRoot().top.value
            assertTrue("Newest creation must precede the lower-ID older expense", newerTop < olderTop)
        }
        correctOrder()
        compose.onNodeWithText(PresentationFormat.serverSavedAt(newer.createdAt)).performScrollTo().assertIsDisplayed()
        compose.pilotScreenshot("history-metadata-known-time")
        compose.pilotClick("Latest entry")
        compose.onNodeWithText("Recorded expense").assertExists()
        compose.pilotClick("Back")
        compose.onNodeWithTag("reporting-period").assertTextEquals(PresentationFormat.month(data.today.take(7)))
        compose.runOnIdle { data.rows.value = listOf(old.copy(updatedAt = Timestamp(9999999999L, 0)), newer) }
        correctOrder()
        compose.pilotClick("History options"); compose.pilotClick("Expense dates and times")
        compose.onNodeWithText("An offline entry shows when it later reaches the server", substring = true).assertExists()
        compose.pilotScreenshot("history-metadata-time-information")
        compose.pilotClick("Close")
        compose.runOnIdle { assertTrue(data.submitted.isEmpty()) }
        graph.close()
    }

    @Test fun allRecordedExpensesLabelsUnknownAndPendingTimesWithoutGuessing() {
        checkConfiguration()
        val data = PilotFixtures()
        val known = data.rows.value.first().copy(note = "Known time", createdAt = stamp(data.today, 31))
        val unknown = data.rows.value[1].copy(note = "Unknown time", createdAt = null)
        val pending = data.rows.value[2].copy(note = "Pending time", isPending = true, createdAt = stamp(data.today, 59))
        data.rows.value = listOf(known, unknown, pending)
        val graph = data.graph()
        compose.setContent { ClarityTheme { IsolatedAppNavigation(graph) } }
        compose.pilotClick("History"); compose.pilotClick("All recorded expenses")
        fun visible(label: String) {
            compose.onNodeWithTag("all-expenses-list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        visible(PresentationFormat.serverSavedAt(known.createdAt))
        visible("Server-saved time unavailable")
        visible("Server-saved time pending")
        compose.onNodeWithText(PresentationFormat.serverSavedAt(pending.createdAt)).assertDoesNotExist()
        compose.pilotScreenshot("history-metadata-pending-and-unknown")
        compose.onNodeWithTag("all-expenses-list").performScrollToIndex(0)
        compose.pilotClick("Expense dates and times")
        compose.onNodeWithText(PresentationFormat.expenseTimesExplanation).assertExists()
        compose.pilotClick("Close information")
        compose.runOnIdle { assertTrue(data.submitted.isEmpty()) }
        graph.close()
    }
}
