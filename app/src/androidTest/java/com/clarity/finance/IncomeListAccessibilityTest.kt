package com.clarity.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.LocalCatalogue
import com.clarity.finance.ui.income.IncomeListScreen
import com.clarity.finance.ui.theme.ClarityTheme
import com.clarity.finance.viewmodel.IncomeListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId

/** Production list/VM, isolated data: a growing summary must not consume the transaction viewport. */
class IncomeListAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun enlargedNarrowLayoutCanScrollPastLongSourceSummaryAndInspectActualIncome() {
        val today = LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
        val entries = (0..7).map { CatalogueEntry("custom_fixture_$it", "source",
            "Long archived source $it with a name retained for existing historical transactions", true) }
        val records = entries.mapIndexed { i, source -> Income("row_$i", source.id, today, "LKR", "100", 10000,
            "native_lkr", "1", "Scrolling fixture $i") }
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User("isolated-income", "fixture@example.test"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() = error("Unused")
        }
        val repo = Proxy.newProxyInstance(IncomeRepository::class.java.classLoader, arrayOf(IncomeRepository::class.java)) { _, method, _ ->
            check(method.name == "getIncomes") { "Unexpected isolated repository call" }
            flowOf(records)
        } as IncomeRepository
        val vm = IncomeListViewModel(auth, repo)
        val owner = ViewModelStore().also { it.put("income", vm) }
        var inspected: String? = null
        try {
            compose.setContent { ClarityTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f),
                    LocalCatalogue provides CatalogueSnapshot(CatalogueDefaults.merge(entries), false)) {
                    Box(Modifier.width(320.dp)) {
                        IncomeListScreen(vm, {}, { inspected = it }, {})
                    }
                }
            } }
            compose.waitUntil(5000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("By source").performScrollTo().performClick()
            val list = compose.onNode(hasScrollToIndexAction())
            // Fail promptly on the original zero-height list, rather than hanging a scroll-to action.
            assertTrue("Income list must retain a usable viewport below a long summary",
                list.fetchSemanticsNode().boundsInRoot.height >= with(compose.density) { 48.dp.toPx() })
            list.performScrollToNode(hasText("Note: Scrolling fixture 7"))
            compose.onNodeWithText("Note: Scrolling fixture 7").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("row_7", inspected) }
        } finally { compose.runOnIdle { owner.clear() } }
    }
}
