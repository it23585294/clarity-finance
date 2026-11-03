package com.clarity.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercises the production navigation graph and buttons on Android; data is isolated from Firebase. */
class PaymentNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun directAndMilestoneButtonsOpenConfirmationOnNarrowScreen() {
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User("navigation-test", "test@example.com"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() { authState.value = null }
        }
        val payments = NavigationPayments()
        val graph = isolatedAppGraph(auth, unused(ExpenseRepository::class.java), unused(IncomeRepository::class.java), payments,
            unused(CryptoRepository::class.java))
        compose.setContent { ClarityTheme { Box(Modifier.width(320.dp)) { IsolatedAppNavigation(graph) } } }
        compose.onNodeWithText("Pending Payments").performClick()
        compose.onNodeWithText("Navigation Project").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Record Direct"))
        compose.onNodeWithText("Record Direct").performClick()
        compose.onNodeWithText("Record Payment").assertIsDisplayed()
        compose.onNodeWithText("Payment for: Navigation Project").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Record", substring = false))
        compose.onNodeWithText("Record", substring = false).performClick()
        compose.onNodeWithText("Record Payment").assertIsDisplayed()
        compose.onNodeWithText("Payment for: Navigation Milestone").assertIsDisplayed()
    }

    @Test fun agreementAndMilestoneEditorsPreserveCountersAndReachSaveOnNarrowScreen() {
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User("navigation-test", "fixture@example.test"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() { authState.value = null }
        }
        val payments = NavigationPayments()
        val graph = isolatedAppGraph(auth, payment = payments)
        compose.setContent { ClarityTheme { Box(Modifier.width(320.dp)) { IsolatedAppNavigation(graph) } } }
        compose.onNodeWithText("Pending Payments").performClick()
        compose.onNodeWithText("Navigation Project").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Edit agreement"))
        compose.onNodeWithText("Edit agreement").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Corrected project")
        compose.onNodeWithText("Agreed amount (LKR)").performTextReplacement("1200")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save proposal").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(120000L, payments.savedProject!!.agreedAmountMinor)
        org.junit.Assert.assertEquals(40000L, payments.savedProject!!.allocatedAmountMinor)
        org.junit.Assert.assertEquals(0L, payments.savedProject!!.totalReceivedAmountMinor)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Edit milestone"))
        compose.onNodeWithText("Edit milestone").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Corrected milestone")
        compose.onNodeWithText("Agreed amount (LKR)").performTextReplacement("450")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save proposal").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(45000L, payments.savedMilestone!!.agreedAmountMinor)
        org.junit.Assert.assertEquals(0L, payments.savedMilestone!!.receivedAmountMinor)
        org.junit.Assert.assertEquals("p1", payments.savedMilestone!!.paymentId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unused(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Unexpected repository call: ${method.name}")
    } as T

    private class NavigationPayments : PaymentRepository {
        var savedProject: ExpectedPayment? = null
        var savedMilestone: Milestone? = null
        val project = ExpectedPayment("p1", "project", "Navigation Project", "freelance", "LKR", "2026-10-30",
            100000L, allocatedAmountMinor = 40000L, updatedAt = Timestamp(1, 0))
        val milestone = Milestone("m1", "p1", "Navigation Milestone", "2026-10-30", 40000L, updatedAt = Timestamp(1, 0))
        override fun getExpectedPayments(userId: String) = flowOf(listOf(project))
        override fun getMilestones(userId: String, paymentId: String) = flowOf(listOf(milestone))
        override suspend fun getExpectedPayment(userId: String, paymentId: String) = Result.success(project)
        override suspend fun getUnresolvedPayment(userId: String, paymentId: String, milestoneId: String?): Income? = null
        override suspend fun createExpectedPayment(userId: String, payment: ExpectedPayment) = error("Unused")
        override suspend fun updateExpectedPayment(userId: String, payment: ExpectedPayment, originalUpdatedAt: Timestamp): Result<Unit> {
            savedProject = payment; return Result.success(Unit)
        }
        override suspend fun cancelExpectedPayment(userId: String, paymentId: String, originalUpdatedAt: Timestamp) = error("Unused")
        override suspend fun createMilestone(userId: String, milestone: Milestone, opId: String) = error("Unused")
        override suspend fun updateMilestone(userId: String, milestone: Milestone, originalUpdatedAt: Timestamp, opId: String): Result<Unit> {
            savedMilestone = milestone; return Result.success(Unit)
        }
        override suspend fun cancelMilestone(userId: String, milestoneId: String, paymentId: String, originalUpdatedAt: Timestamp, opId: String) = error("Unused")
        override suspend fun confirmPayment(userId: String, paymentId: String, milestoneId: String?, income: Income) = error("Unused")
        override suspend fun retryFailedPayment(userId: String, paymentId: String, milestoneId: String?, income: Income) = error("Unused")
    }
}
