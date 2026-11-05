package com.clarity.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production navigation/ViewModels, standalone fake graph; no server or recovery worker bindings. */
class CrossHistoryOrderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun incomeLinkedRouteAndCryptoOverflowHistoryUseCreationOrder() {
        val agreements = PhaseBFixtures()
        val earlier = agreements.incomes.value.first().copy(id = "a-old", note = "Earlier income", createdAt = Timestamp(1, 0))
        val latest = earlier.copy(id = "z-new", note = "Latest linked income", createdAt = Timestamp(2, 0), linkedPaymentId = "project")
        agreements.incomes.value = listOf(earlier, latest)
        val results = MutableStateFlow(listOf(
            CryptoResult("a-old", "gain", Long.MAX_VALUE, agreements.today, "Earlier crypto", createdAt = Timestamp(1, 0), updatedAt = Timestamp(1, 0)),
            CryptoResult("z-new", "gain", Long.MAX_VALUE, agreements.today, "Latest crypto", createdAt = Timestamp(2, 0), updatedAt = Timestamp(2, 0))))
        val report = object : ReportingRepository {
            override fun expenses(uid: String, month: ReportingMonth) = flowOf(ReportingBatch(emptyList<Expense>(), false))
            override fun income(uid: String, month: ReportingMonth) = agreements.incomes.map { ReportingBatch(it.filter { r -> month.contains(r.receiptDate) }, false) }
            override fun crypto(uid: String, month: ReportingMonth) = results.map { ReportingBatch(it.filter { r -> month.contains(r.resultDate) }, false) }
            override fun projects(uid: String) = flowOf(ReportingBatch(emptyList<ExpectedPayment>(), false))
            override fun recovery(uid: String) = flowOf(ReportingRecovery())
        }
        val crypto = object : CryptoRepository {
            override fun observe(uid: String, year: Int, cutoff: String) = results.map { CryptoSnapshot(it, fromCache = false, loaded = true) }
            override suspend fun submit(uid: String, operation: CryptoOperation) = error("No fixture writes")
            override suspend fun retry(uid: String, operation: CryptoOperation) = error("No fixture retry")
            override suspend fun dismissRejected(uid: String, operation: CryptoOperation) = error("No fixture dismissal")
        }
        val graph = isolatedAppGraph(auth = agreements.auth, income = agreements, payment = agreements, reporting = report, crypto = crypto)
        compose.setContent { ClarityTheme { IsolatedAppNavigation(graph) } }
        fun above(first: String, second: String) = assertTrue(compose.onNodeWithText(first).getUnclippedBoundsInRoot().top.value <
            compose.onNodeWithText(second).getUnclippedBoundsInRoot().top.value)
        compose.pilotClick("History"); compose.pilotClick("Income")
        above("Latest linked income", "Earlier income")
        compose.pilotClick("Latest linked income")
        compose.onNodeWithText("Studio website").assertExists() // Protected agreement route, not ordinary income editor.
        compose.onNodeWithText("Original Amount").assertDoesNotExist()
        compose.pilotClick("Back"); compose.pilotClick("Crypto history")
        above("Latest crypto", "Earlier crypto")
        compose.onNodeWithText("Total exceeds the supported integer range", substring = true).assertExists()
        compose.pilotClick("Latest crypto")
        compose.onNodeWithText("Realised crypto result").assertExists(); compose.pilotClick("Close")
        compose.pilotClick("More"); compose.pilotClick("Crypto Results")
        above("Latest crypto", "Earlier crypto")
        compose.pilotClick("Latest crypto")
        compose.onNodeWithText("Realised result").assertExists()
        compose.runOnIdle { assertTrue(agreements.savedIncome.isEmpty()); assertTrue(agreements.receipts.isEmpty()) }
        graph.close()
    }

    @Test fun categoryFilterKeepsRelativeOrderAndEditMetadataDoesNotMoveAnExpense() {
        val data = PilotFixtures()
        val original = data.rows.value.first()
        val older = original.copy(id = "a-old", note = "Earlier filtered expense", createdAt = Timestamp(1, 0))
        val newest = original.copy(id = "z-new", note = "Latest filtered expense", createdAt = Timestamp(3, 0))
        val middle = original.copy(id = "middle", categoryId = "transport", note = "Other category", createdAt = Timestamp(2, 0))
        data.rows.value = listOf(older, middle, newest)
        val graph = data.graph()
        compose.setContent { ClarityTheme { IsolatedAppNavigation(graph) } }
        compose.pilotClick("History")
        compose.pilotClick("Housing")
        fun ordered() = assertTrue(compose.onNodeWithText("Latest filtered expense").getUnclippedBoundsInRoot().top.value <
            compose.onNodeWithText("Earlier filtered expense").getUnclippedBoundsInRoot().top.value)
        ordered(); compose.onNodeWithText("Other category").assertDoesNotExist()
        compose.runOnIdle { data.rows.value = listOf(older.copy(updatedAt = Timestamp(999, 0)), middle, newest) }
        ordered()
        compose.pilotClick("Latest filtered expense"); compose.pilotClick("Back")
        compose.onNodeWithText("Expense category: Housing").assertExists(); ordered()
        compose.runOnIdle { assertTrue(data.submitted.isEmpty()) }
        graph.close()
    }
}
