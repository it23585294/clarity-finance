package com.clarity.finance

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.clarity.finance.di.injectedViewModel
import com.clarity.finance.di.viewModelModule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Android owners/extras, isolated Koin context, no production repository or Firebase resolution. */
class DiViewModelOwnershipTest {
    @get:Rule val compose = createComposeRule()
    class Probe(val handle: SavedStateHandle) : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun recompositionReusesOwnerNavigationSeparatesOwnersAndPoppingClearsWithTypedArguments() {
        val created = mutableListOf<Probe>()
        val graph = koinApplication { modules(viewModelModule, module { viewModel { Probe(get()).also { created += it } } }) }
        try {
            compose.setContent {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "start") {
                    composable("start") { Button(onClick = { nav.navigate("probe/project") }) { Text("Open direct") } }
                    composable("probe/{id}?milestoneId={milestoneId}", arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("milestoneId") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )) {
                        val vm: Probe = injectedViewModel(graph.koin)
                        var recompositions by remember { mutableIntStateOf(0) }
                        Column {
                            Text("id=${vm.handle.get<String>("id")} milestone=${vm.handle.get<String>("milestoneId") ?: "none"}")
                            Button(onClick = { recompositions++ }) { Text("Refresh $recompositions") }
                            Button(onClick = { nav.navigate("probe/project?milestoneId=milestone") }) { Text("Open milestone") }
                            Button(onClick = { nav.popBackStack() }) { Text("Back") }
                        }
                    }
                }
            }
            compose.onNodeWithText("Open direct").performClick()
            compose.onNodeWithText("id=project milestone=none").assertIsDisplayed()
            compose.onNodeWithText("Refresh 0").performClick()
            compose.onNodeWithText("Refresh 1").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, created.size); assertNull(created[0].handle.get<String>("milestoneId")) }
            compose.onNodeWithText("Open milestone").performClick()
            compose.onNodeWithText("id=project milestone=milestone").assertIsDisplayed()
            compose.runOnIdle { assertEquals(2, created.size); assertNotSame(created[0], created[1]); assertFalse(created[0].cleared) }
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("id=project milestone=none").assertIsDisplayed()
            compose.waitUntil { created[1].cleared }
            compose.runOnIdle { assertEquals(2, created.size); assertFalse(created[0].cleared) }
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Open direct").assertIsDisplayed()
            compose.waitUntil { created[0].cleared }
        } finally { graph.close() }
    }
}
