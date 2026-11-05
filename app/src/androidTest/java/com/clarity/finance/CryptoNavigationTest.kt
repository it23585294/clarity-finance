package com.clarity.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.CryptoReporting
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.theme.ClarityTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Production home/navigation/forms with isolated data. Never connects to or writes live crypto. */
class CryptoNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cryptoHomeGainLossEditAndDeleteAtNarrowWidth() {
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User("crypto-ui-test", "test@example.com"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() { authState.value = null }
        }
        val crypto = FakeCrypto()
        val graph = isolatedAppGraph(auth, unused(ExpenseRepository::class.java), unused(IncomeRepository::class.java),
            unused(PaymentRepository::class.java), crypto)
        compose.setContent { ClarityTheme { Box(Modifier.width(320.dp)) { IsolatedAppNavigation(graph) } } }
        compose.pilotClick("Crypto Results")
        compose.onNodeWithText("No realised results recorded for this month.").performScrollTo().assertIsDisplayed()
        add("60000", loss = false)
        add("25000", loss = true)
        compose.onNodeWithText("Month net: LKR +35,000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Year-to-date net").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LKR +35,000.00").assertIsDisplayed()
        compose.onNodeWithText("Gain LKR +60,000.00").performScrollTo().performClick()
        compose.onNodeWithText("Edit result").performClick()
        compose.onNodeWithText("Amount (LKR)").performTextReplacement("50000")
        compose.onNodeWithText("Save result").assertIsDisplayed().performClick()
        compose.onNodeWithText("Month net: LKR +25,000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Loss LKR -25,000.00").performScrollTo().performClick()
        compose.onNodeWithText("Delete result").performClick()
        compose.onNodeWithText("Confirm deletion").performClick()
        compose.onNodeWithText("Month net: LKR +50,000.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Crypto Results").assertExists()
    }

    private fun add(amount: String, loss: Boolean) {
        compose.onNodeWithText("Add result").performScrollTo().performClick()
        if (loss) compose.onNodeWithText("Loss", substring = false).performClick()
        compose.onNodeWithText("Amount (LKR)").performTextInput(amount)
        compose.onNodeWithText("Result date").assertTextContains(CryptoReporting.today().toString())
        compose.onNodeWithText("Save result").assertIsDisplayed().performClick()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unused(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Crypto must not access ordinary repositories: ${method.name}")
    } as T

    private class FakeCrypto : CryptoRepository {
        val rows = MutableStateFlow<List<CryptoResult>>(emptyList())
        override fun observe(uid: String, year: Int, cutoff: String) = rows.map {
            CryptoSnapshot(results = it.filter { row -> row.resultDate >= "$year-01-01" && row.resultDate <= cutoff }, fromCache = false, loaded = true)
        }
        override suspend fun submit(uid: String, operation: CryptoOperation) {
            val result = operation.result.copy(createdAt = operation.result.createdAt ?: Timestamp(1, 0),
                updatedAt = Timestamp(2, 0), lastOperationId = operation.operationId)
            rows.value = rows.value.filterNot { it.id == result.id } + if (operation.kind == "DELETE") emptyList() else listOf(result)
        }
        override suspend fun retry(uid: String, operation: CryptoOperation) = error("Unused")
        override suspend fun dismissRejected(uid: String, operation: CryptoOperation) = error("Unused")
    }
}
