package com.clarity.finance.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.ReportingBatch
import com.clarity.finance.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onExpense: (String) -> Unit,
    onIncome: (String) -> Unit,
    onProject: (String) -> Unit,
    onCrypto: () -> Unit,
    onPayments: () -> Unit,
    goalCard: @Composable () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("dashboard") }
    var cryptoDetailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.contextVersion) { cryptoDetailId = null }
    LifecycleResumeEffect(viewModel) { viewModel.refreshDate(); onPauseOrDispose { } }
    Scaffold(topBar = {
        TopAppBar(title = { Text(when (page) {
            "history" -> "Monthly history"
            "insights" -> "Spending insights"
            else -> "Financial dashboard"
        }) }, navigationIcon = {
            IconButton(onClick = { if (page == "dashboard") onBack() else page = "dashboard" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        })
    }, floatingActionButton = {
        if (state.signedIn) ExtendedFloatingActionButton(onClick = onAddExpense) { Text("Add Expense") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
            .padding(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.signedIn) Text("Sign in to view recorded finances.")
            else {
                com.clarity.finance.ui.CatalogueStatus()
                Text("Reporting month: ${state.month}", style = MaterialTheme.typography.titleMedium)
                Text("Asia/Colombo transaction dates • Based on recorded transactions.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.changeMonth(-1) }, enabled = state.canPrevious) { Text("Previous month") }
                    OutlinedButton(onClick = { viewModel.changeMonth(1) }, enabled = state.canNext) { Text("Next month") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { page = "dashboard" }) { Text("Overview") }
                    OutlinedButton(onClick = { viewModel.selectCategory(null); page = "history" }) { Text("Monthly history") }
                    OutlinedButton(onClick = { page = "insights" }) { Text("Spending insights") }
                }
                if (page == "dashboard") {
                    goalCard()
                    ReportCard("Ordinary received income", state.incomeText, state.income)
                    ReportCard("Recorded expenses", state.expensesText, state.expenses)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Recorded cash surplus", style = MaterialTheme.typography.titleMedium)
                            Text(state.surplusText?.let { "LKR $it" } ?: "Unavailable", style = MaterialTheme.typography.headlineSmall)
                            Text("Ordinary received income minus recorded expenses. Based on recorded transactions.")
                            if (state.surplusText == null) Text(state.surplusError ?: "Income and expense data must both be available to calculate surplus.")
                            else if (state.income.fromCache || state.expenses.fromCache) Text("Cached view; may be incomplete or out of date.")
                        }
                    }
                    ReportCard("Signed realised crypto net", state.cryptoText, state.crypto)
                    Text("Crypto is separate and excluded from cash surplus.")
                    OutlinedButton(onClick = onCrypto) { Text("Manage Crypto Results") }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Outstanding pending income", style = MaterialTheme.typography.titleMedium)
                            Text("Current collectible project balances, not historical month-end balances. Excludes cancelled projects; parent balances count once. Excluded from received income and surplus.")
                            BatchStatus(state.projects)
                            when {
                                state.pendingAmounts == null -> Text("Pending balances unavailable")
                                state.pendingAmounts!!.isEmpty() -> Text("No collectible pending income recorded.")
                                else -> state.pendingAmounts!!.forEach { (currency, amount) -> Text("$currency $amount") }
                            }
                            Text("Original currencies shown separately; no estimated exchange rate is applied.")
                            OutlinedButton(onClick = onPayments) { Text("View Pending Payments") }
                        }
                    }
                }
                if (page == "insights") {
                    BatchStatus(state.expenses)
                    val spending = state.spending
                    if (spending == null) Text("Spending insights unavailable")
                    else if (spending.totalMinor == 0L) Text("No expenses recorded for this month. Add an expense to see spending insights.")
                    else {
                        Text("Total expenses: LKR ${MonthlyReporting.money(spending.totalMinor)}", style = MaterialTheme.typography.titleLarge)
                        Text("Committed: LKR ${MonthlyReporting.money(spending.committedMinor)} (${spending.committedPercent}%)")
                        Text("Discretionary: LKR ${MonthlyReporting.money(spending.discretionaryMinor)} (${spending.discretionaryPercent}%)")
                        Text("Shares rounded to one decimal; discretionary is the complement of committed.", style = MaterialTheme.typography.bodySmall)
                        Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                        spending.categories.forEach { category ->
                            OutlinedCard(onClick = { viewModel.selectCategory(category.id); page = "history" }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val label = com.clarity.finance.ui.LocalCatalogue.current.label("category", category.id)
                                    Text("${label}: LKR ${MonthlyReporting.money(category.amountMinor)}")
                                    LinearProgressIndicator(progress = { category.barFraction }, modifier = Modifier.fillMaxWidth())
                                    Text("Inspect $label expenses")
                                }
                            }
                        }
                    }
                }
                if (page == "history") {
                    Text("Expense category: ${state.selectedCategory?.let { com.clarity.finance.ui.LocalCatalogue.current.labels("category")[it] ?: it } ?: "All categories"}", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.selectedCategory == null, onClick = { viewModel.selectCategory(null) }, label = { Text("All categories") })
                        com.clarity.finance.ui.LocalCatalogue.current.labels("category").forEach { (id, label) ->
                            FilterChip(selected = state.selectedCategory == id, onClick = { viewModel.selectCategory(id) }, label = { Text(label) })
                        }
                    }
                    Text("Expenses", style = MaterialTheme.typography.titleLarge)
                    BatchStatus(state.expenses)
                    if (state.expenses.records != null && state.expenseHistory.isEmpty()) Text("No matching expenses recorded for this month.")
                    state.expenseHistory.forEach { row ->
                        HistoryCard("${com.clarity.finance.ui.LocalCatalogue.current.label("category", row.categoryId)} • ${row.expenseDate}",
                            "LKR ${MonthlyReporting.money(row.amountMinor)} • ${row.spendingType}", row.note.orEmpty(),
                            "Inspect / edit expense", { onExpense(row.id) })
                    }
                    if (state.selectedCategory == null) {
                        Text("Ordinary received income", style = MaterialTheme.typography.titleLarge)
                        BatchStatus(state.income)
                        if (state.income.records != null && state.incomeHistory.isEmpty()) Text("No received income recorded for this month.")
                        state.incomeHistory.forEach { row ->
                            HistoryCard("${com.clarity.finance.ui.LocalCatalogue.current.label("source", row.sourceId)} • ${row.receiptDate}",
                                "LKR ${MonthlyReporting.money(row.lkrAmountMinor)}", row.note,
                                if (row.linkedPaymentId != null) "Linked receipt: inspect project" else "Inspect / edit income",
                                { row.linkedPaymentId?.let(onProject) ?: onIncome(row.id) })
                        }
                        Text("Realised crypto results (separate from cash transactions)", style = MaterialTheme.typography.titleLarge)
                        BatchStatus(state.crypto)
                        if (state.crypto.records != null && state.crypto.records!!.isEmpty()) Text("No realised crypto results recorded for this month.")
                        state.cryptoHistory.forEach { row ->
                            HistoryCard("${row.direction} • ${row.resultDate}", "LKR ${CryptoReporting.money(row.signedMinor)}", row.note,
                                "Inspect crypto result", { cryptoDetailId = row.id })
                        }
                    }
                }
                HorizontalDivider()
                Text("Only confirmed/cached contributions are included. Pending or rejected proposals do not replace confirmed contributions.", style = MaterialTheme.typography.bodySmall)
                state.recovery?.let {
                    Text("Saved changes across all periods: ${it.pending} pending, ${it.rejected} rejected/conflicting. Review them in Expenses, Income, Pending Payments or Crypto Results.")
                } ?: Text("Checking saved changes…")
                state.recoveryError?.let { Text("Saved changes unavailable: $it", color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = viewModel::retry) { Text("Retry reporting data") }
            }
        }
    }
    state.cryptoHistory.firstOrNull { it.id == cryptoDetailId }?.let { result ->
        AlertDialog(onDismissRequest = { cryptoDetailId = null }, title = { Text("Realised crypto result") }, text = {
            Text("${result.resultDate}\n${result.direction}: LKR ${CryptoReporting.money(result.signedMinor)}\n${result.note}\nManage changes in Crypto Results.")
        }, confirmButton = { TextButton(onClick = { cryptoDetailId = null }) { Text("Close") } })
    }
}

@Composable
private fun <T> ReportCard(title: String, amount: String?, batch: ReportingBatch<T>) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(amount?.let { "LKR $it" } ?: "Unavailable", style = MaterialTheme.typography.headlineSmall)
            BatchStatus(batch)
        }
    }
}

@Composable
private fun <T> BatchStatus(batch: ReportingBatch<T>) {
    batch.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (batch.awaitingWrites) Text("Changes awaiting server confirmation. Retaining the last confirmed/cached contribution; if unavailable, totals remain unavailable.")
    if (batch.records != null && batch.fromCache) Text("Cached view; may be incomplete or out of date.")
    if (batch.records == null && batch.error == null && !batch.awaitingWrites) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Loading recorded transactions…")
    }
}

@Composable
private fun HistoryCard(title: String, amount: String, note: String, action: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(amount)
            if (note.isNotBlank()) Text(note)
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}
