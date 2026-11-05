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

/** Real production navigation/forms. Explicit in-memory owners only; no Firebase or durable production store. */
class CatalogueNavigationTest {
    @get:Rule val compose = createComposeRule()
    private class Data : CatalogueRepository {
        private val overrides = mutableMapOf<String, MutableList<CatalogueEntry>>()
        private val version = MutableStateFlow(0L)
        val writes = mutableListOf<Pair<String, FinancialOperation>>()
        override fun observe(uid: String): Flow<CatalogueSnapshot> = version.map { CatalogueSnapshot(CatalogueDefaults.merge(overrides[uid].orEmpty()), false) }
        suspend fun submit(uid: String, operation: FinancialOperation) {
            val entry = operation.catalogue ?: error("Unexpected fixture write")
            entry.validate(); writes += uid to operation
            val rows = overrides.getOrPut(uid) { mutableListOf() }; rows.removeAll { it.id == entry.id && it.kind == entry.kind }
            rows += entry.copy(updatedAt = Timestamp(++version.value, 0))
            version.value++
        }
    }
    @Test fun additionsRenameArchiveAndAccountIsolationUseStableIds() {
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User("catalogue-a", "fixture@example.test"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() { authState.value = null }
        }
        val data = Data()
        val recovery = object : FinancialRecoveryAccess {
            override fun observe(uid: String) = flowOf(FinancialRecovery())
            override suspend fun submit(uid: String, operation: FinancialOperation) = data.submit(uid, operation)
            override suspend fun resolve(uid: String, operation: FinancialOperation) = error("Unused")
            override suspend fun dismissRejected(uid: String, operation: FinancialOperation) = error("Unused")
        }
        val graph = isolatedAppGraph(auth, catalogue = data, recovery = recovery)
        compose.setContent { ClarityTheme { IsolatedAppNavigation(graph) } }
        compose.pilotClick("Sources, categories & recovery")
        compose.onNodeWithText("Add category").performScrollTo().performClick()
        compose.onNodeWithText("Name").performTextInput("Travel extras")
        compose.onNodeWithText("Save proposal").performClick()
        compose.waitUntil(5000) { data.writes.size == 1 }
        val stableId = data.writes.single().second.targetId
        assertTrue(stableId.startsWith("custom_"))
        compose.onNodeWithText("Rename / edit Travel extras").performScrollTo().performClick()
        compose.onNodeWithText("Name").performTextReplacement("Travel")
        compose.onNodeWithText("Save proposal").performClick()
        compose.waitUntil(5000) { data.writes.size == 2 }
        assertEquals(stableId, data.writes.last().second.targetId)
        compose.onNodeWithText("Archive Travel").performScrollTo().performClick()
        compose.onNodeWithText("Keep active").performClick()
        assertEquals(2, data.writes.size)
        compose.onNodeWithText("Archive Travel").performClick()
        compose.onNodeWithText("Archive", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { data.writes.size == 3 }
        compose.onNodeWithText("Travel (archived)").performScrollTo().assertIsDisplayed()
        assertEquals(stableId, data.writes.last().second.targetId)
        compose.runOnIdle { auth.authState.value = User("catalogue-b", "other@example.test") }
        compose.waitForIdle()
        compose.onNodeWithText("Travel (archived)").assertDoesNotExist()
        assertTrue(data.writes.all { it.first == "catalogue-a" })
    }
    @Test fun unknownOrArchivedCatalogueDoesNotInventAnActiveExpenseSelection() {
        val catalogue = androidx.compose.runtime.mutableStateOf(CatalogueSnapshot())
        val category = androidx.compose.runtime.mutableStateOf("other")
        var saves = 0
        compose.setContent { ClarityTheme {
            androidx.compose.runtime.CompositionLocalProvider(com.clarity.finance.ui.LocalCatalogue provides catalogue.value) {
                com.clarity.finance.ui.expenses.ExpenseFormContent(
                    amountStr = "1.00", onAmountChange = {}, categoryId = category.value,
                    onCategoryChange = { category.value = it }, paymentChannelId = "cash", onPaymentChannelChange = {},
                    spendingType = "discretionary", onSpendingTypeChange = {}, expenseDate = "2026-01-31",
                    onDateChange = {}, note = "", onNoteChange = {}, error = null, isSubmitting = false,
                    onSave = { saves++ }, onCancel = {})
            }
        } }
        compose.onNodeWithText("Category label unavailable (other)").assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { catalogue.value = CatalogueSnapshot(CatalogueDefaults.merge(listOf(
            CatalogueEntry("other", "category", "Archived miscellaneous", true, "discretionary", Timestamp(1, 0)))), true) }
        compose.onNodeWithText("Archived miscellaneous").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saves); category.value = "housing" }
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

}
