package com.clarity.finance.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.Expense
import com.clarity.finance.ui.pilot.*
import com.clarity.finance.viewmodel.ExpenseListViewModel
import com.clarity.finance.viewmodel.ExpensesUiState

@Composable
fun ExpensesScreen(viewModel: ExpenseListViewModel, onNavigateToAddExpense: () -> Unit,
    onNavigateToEditExpense: (String) -> Unit, onNavigateBack: () -> Unit, onRecovery: () -> Unit = {}) {
    val uiState by viewModel.expensesState.collectAsStateWithLifecycle()
    var showTimes by remember { mutableStateOf(false) }
    Scaffold(topBar = { PageHeader("All recorded expenses", onNavigateBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("all-expenses-list"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All dates · Monthly totals and filters are in History.", style = MaterialTheme.typography.bodyMedium)
                    InformationAction("Expense dates and times") { showTimes = true }
                    com.clarity.finance.ui.CatalogueStatus()
                    Button(onClick = onNavigateToAddExpense, modifier = Modifier.fillMaxWidth()) { Text("Add Expense") }
                    TextButton(onClick = onRecovery) { Text("Review saved changes") }
                }
            }
            when (val state = uiState) {
                is ExpensesUiState.Loading -> item { LoadingPanel("Loading expenses") }
                is ExpensesUiState.Error -> item { StatusPanel(state.message, isError = true) }
                is ExpensesUiState.SignedOut -> Unit // Navigation owns sign-out.
                is ExpensesUiState.Success -> {
                    if (state.expenses.isEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No expenses to show.")
                            Text("Saved data may be incomplete. Check History for period totals and data status.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(state.expenses, key = { it.id }) { expense ->
                        val isClickable = !expense.isPending && expense.syncError == null && !expense.isConflict && expense.queuedChange == null
                        ExpenseItem(expense, if (isClickable) Modifier.clickable { onNavigateToEditExpense(expense.id) } else Modifier,
                            onRetry = { viewModel.retryExpense(expense) })
                    }
                }
            }
        }
    }
    if (showTimes) InformationDialog("Expense dates and times", PresentationFormat.expenseTimesExplanation) { showTimes = false }
}

@Composable
fun ExpenseItem(expense: Expense, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    val channel = mapOf("cash" to "Cash", "primary_bank_card" to "Primary bank/card",
        "secondary_bank_card" to "Secondary bank/card", "other" to "Other")
    OutlinedCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${expense.currency} ${PresentationFormat.minor(expense.amountMinor)}", style = MaterialTheme.typography.titleLarge)
            Text(com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("category", expense.categoryId), style = MaterialTheme.typography.titleMedium)
            Text(PresentationFormat.date(expense.expenseDate), style = MaterialTheme.typography.bodyMedium)
            Text(PresentationFormat.serverSavedAt(expense.createdAt, expense.isPending), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${channel[expense.paymentChannelId] ?: "Payment method unavailable"} · ${expense.spendingType.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            expense.queuedChange?.let { StatusPanel(it) }
            if (expense.isFromCache && !expense.isPending) Text("Cached · May be out of date", style = MaterialTheme.typography.bodySmall)
            if (expense.syncError != null) {
                StatusPanel(if (expense.isConflict) "Change rejected. Review the saved proposal." else "Outcome unresolved. Check the original change before trying again.", isError = expense.isConflict)
                TextButton(onClick = onRetry) { Text("Retry") }
            } else if (expense.isPending) Text("Pending · Not confirmed", style = MaterialTheme.typography.bodySmall)
            else if (expense.isConflict) StatusPanel("Changed elsewhere. Review saved changes before editing.", isError = true)
            if (!expense.note.isNullOrBlank()) Text(expense.note, style = MaterialTheme.typography.bodyMedium)
            if (!expense.isPending && expense.syncError == null && !expense.isConflict && expense.queuedChange == null)
                Text("Inspect expense", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
