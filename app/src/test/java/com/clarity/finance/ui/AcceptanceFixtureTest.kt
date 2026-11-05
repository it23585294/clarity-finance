package com.clarity.finance.ui

import com.clarity.finance.acceptance.AcceptanceFixture
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AcceptanceFixtureTest {
    @Test fun fixtureGraphCannotResolveLiveFirebaseOrFinancialWorkers() {
        val f = AcceptanceFixture(); val graph = f.graph
        assertSame(f.expenses, graph.koin.get<ExpenseRepository>())
        assertSame(f.auth, graph.koin.get<AuthRepository>())
        assertNull(graph.koin.getOrNull<FirebaseFirestore>())
        assertNull(graph.koin.getOrNull<FinancialQueue>())
        assertNull(graph.koin.getOrNull<SessionSyncCoordinator>())
        graph.close()
    }
    @Test fun pendingExampleIsExcludedAndBaselineUsesProductionReporting() = runTest {
        val f = AcceptanceFixture(); val month = ReportingMonth(java.time.YearMonth.from(f.today))
        val rows = f.reporting.expenses(f.owner, month).first().records!!
        assertEquals(3, rows.size); assertEquals(5000000L, rows.sumOf { it.amountMinor })
        assertFalse(rows.any { it.id == f.pending.id })
        assertEquals(14030000L, f.reporting.income(f.owner, month).first().records!!.sumOf { it.lkrAmountMinor })
    }
    @Test fun stableCaptureIdentityDoesNotDuplicateOrAcceptDifferentPayload() = runTest {
        val f = AcceptanceFixture(); val e = f.pending.copy(id = "human-trial", isPending = false, note = "")
        assertTrue(f.expenses.enqueueExpense(f.owner, e).isSuccess)
        assertTrue(f.expenses.enqueueExpense(f.owner, e).isSuccess)
        assertTrue(f.expenses.enqueueExpense(f.owner, e.copy(amountMinor = 90000)).isFailure)
        assertEquals(listOf(e.id), f.createdIds)
        assertEquals(1, f.rows.value.count { it.id == e.id })
    }
    @Test fun otherOwnerCannotReadSyntheticRecordsOrCustomCatalogue() = runTest {
        val f = AcceptanceFixture()
        assertTrue(f.expenses.getExpenses("other").first().isEmpty())
        assertTrue(f.income.getIncomes("other").first().isEmpty())
        assertTrue(f.payments.getExpectedPayments("other").first().isEmpty())
        assertNull(f.goals.observe("other").first().goal)
        assertTrue(f.recovery.observe("other").first().attempts.isEmpty())
        assertFalse(f.catalogue.observe("other").first().entries!!.any { it.id == "custom_past_leisure" })
    }
}
