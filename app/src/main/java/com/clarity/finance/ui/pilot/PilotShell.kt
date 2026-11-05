package com.clarity.finance.ui.pilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** One shell/back-stack owner; changing tabs never creates another reporting ViewModel. */
@Composable
fun PilotShell(account: String, onNavigate: (String) -> Unit, onSignOut: () -> Unit,
    dashboard: @Composable (String, (String) -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var historyPage by rememberSaveable { mutableStateOf("history") }
    val tabs = listOf("Overview", "History", "Plans", "More")
    val savedScreens = rememberSaveableStateHolder()
    BackHandler(enabled = tab != "Overview") { tab = "Overview" }
    Scaffold(bottomBar = {
        Surface(shadowElevation = 3.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
                val enlarged = maxWidth < 360.dp && LocalDensity.current.fontScale >= 1.8f
                Column {
                    tabs.chunked(if (enlarged) 2 else 4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            row.forEach { name ->
                                TextButton(onClick = { tab = name }, colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (tab == name) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                    .semantics { selected = tab == name; role = Role.Tab }) {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(when (name) { "Overview" -> Icons.Outlined.Home; "History" -> Icons.Outlined.ReceiptLong; "Plans" -> Icons.Outlined.EventNote; else -> Icons.Outlined.MoreHoriz }, contentDescription = null, modifier = Modifier.size(22.dp))
                                        Text(name, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                "Overview", "History" -> savedScreens.SaveableStateProvider("reporting") {
                    dashboard(if (tab == "Overview") "dashboard" else historyPage) { next ->
                        if (next == "dashboard") tab = "Overview" else { historyPage = next; tab = "History" }
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tab, style = MaterialTheme.typography.headlineMedium)
                    if (tab == "Plans") {
                        Text("Agreements, savings and payments to review.", style = MaterialTheme.typography.bodyMedium)
                        Hub("Pending Payments", "Current agreements and milestones", "pendingPayments", onNavigate)
                        Hub("Savings Goal", "Your explicitly recorded allocations", "savingsGoal", onNavigate)
                        Hub("Recurring Expenses", "Review actual payments; templates alone are not spending", "recurringExpenses", onNavigate)
                    } else {
                        Text("Manage", style = MaterialTheme.typography.titleMedium)
                        Hub("Crypto Results", "Realised gains and losses, separate from cash", "cryptoResults", onNavigate)
                        Hub("Categories & Sources", "Add, rename and archive choices", "catalogue", onNavigate)
                        Hub("Saved Changes", "Ordinary transactions, agreements and categories. Other saved changes stay with their feature.", "catalogue", onNavigate)
                        HorizontalDivider()
                        Text("Account", style = MaterialTheme.typography.titleMedium)
                        Text(account, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hub(title: String, description: String, route: String, navigate: (String) -> Unit) {
    OutlinedCard(onClick = { navigate(route) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description)
        }
    }
}
