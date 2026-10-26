package com.clarity.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.crypto.CryptoScreen
import com.clarity.finance.ui.theme.ClarityTheme
import com.clarity.finance.viewmodel.CryptoViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/** Production screen and ViewModel, with isolated state; no live Firebase reads or writes. */
class CryptoFeedbackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bannerTracksPendingRejectedConfirmationAndAccountChanges() {
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User("banner-a", "test@example.com"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() { authState.value = null }
        }
        val repository = object : CryptoRepository {
            val a = MutableStateFlow(CryptoSnapshot(loaded = true, fromCache = false))
            val b = MutableStateFlow(CryptoSnapshot(loaded = true, fromCache = false))
            override fun observe(uid: String, year: Int, cutoff: String) = if (uid == "banner-a") a else b
            override suspend fun submit(uid: String, operation: CryptoOperation) = error("No writes in this UI test")
            override suspend fun retry(uid: String, operation: CryptoOperation) = error("Unused")
            override suspend fun dismissRejected(uid: String, operation: CryptoOperation) = error("Unused")
        }
        val vm = CryptoViewModel(auth, repository)
        val store = ViewModelStore().apply { put("crypto", vm) }
        val first = CryptoResult("first", "gain", 100, CryptoReporting.today().toString(),
            createdAt = Timestamp(1, 0), updatedAt = Timestamp(2, 0))
        val second = first.copy(id = "second", amountMinor = 200)
        val op1 = CryptoOperation("op1", "CREATE", first)
        val op2 = CryptoOperation("op2", "CREATE", second)
        try {
            compose.setContent { ClarityTheme { Box(Modifier.width(320.dp)) { CryptoScreen(vm, {}) } } }
            compose.runOnIdle {
                repository.a.value = CryptoSnapshot(results = listOf(first.copy(sync = CryptoSync.PENDING), second.copy(sync = CryptoSync.PENDING)),
                    attempts = listOf(op1, op2), loaded = true, fromCache = false)
            }
            compose.onNodeWithText("2 pending changes awaiting server confirmation.").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                repository.a.value = CryptoSnapshot(results = listOf(first.copy(sync = CryptoSync.PENDING), second.copy(sync = CryptoSync.PENDING)),
                    attempts = listOf(op1, op2), confirmedResults = listOf(first.copy(lastOperationId = op1.operationId)),
                    loaded = true, fromCache = false)
            }
            compose.onNodeWithText("1 pending change awaiting server confirmation.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("2 pending changes awaiting server confirmation.").assertDoesNotExist()
            compose.runOnIdle {
                repository.a.value = CryptoSnapshot(results = listOf(first, second.copy(sync = CryptoSync.FAILED, error = "Rejected")),
                    attempts = listOf(op2), loaded = true, fromCache = false)
            }
            compose.onNodeWithText("1 rejected change. Review saved proposals.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1 pending change awaiting server confirmation.").assertDoesNotExist()
            compose.runOnIdle { repository.a.value = CryptoSnapshot(results = listOf(first, second), loaded = true, fromCache = false) }
            compose.onNodeWithText("1 rejected change. Review saved proposals.").assertDoesNotExist()
            compose.runOnIdle {
                repository.a.value = CryptoSnapshot(results = listOf(first.copy(sync = CryptoSync.PENDING)),
                    attempts = listOf(op1), loaded = true, fromCache = false)
            }
            compose.onNodeWithText("1 pending change awaiting server confirmation.").performScrollTo().assertIsDisplayed()
            compose.runOnIdle { auth.authState.value = User("banner-b", "other@example.com") }
            compose.onNodeWithText("1 pending change awaiting server confirmation.").assertDoesNotExist()
            compose.onNodeWithText("No realised results recorded for this month.").performScrollTo().assertIsDisplayed()
        } finally { compose.runOnIdle { store.clear() } }
    }
}
