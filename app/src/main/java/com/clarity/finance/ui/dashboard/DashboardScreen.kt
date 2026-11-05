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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import com.clarity.finance.ui.pilot.*
import androidx.compose.ui.platform.testTag
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
    goalCard: @Composable () -> Unit = {},
    selectedPage: String? = null,
    onSelectPage: ((String) -> Unit)? = null,
    onAllExpenses: () -> Unit = {},
    onIncomeList: () -> Unit = {},
    onRecovery: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var localPage by rememberSaveable { mutableStateOf("dashboard") }
    val page = selectedPage ?: localPage
    fun select(next: String) { if (onSelectPage != null) onSelectPage(next) else localPage = next }
    val overviewScroll = rememberScrollState()
    val historyScroll = rememberScrollState()
    val insightsScroll = rememberScrollState()
    var explanation by remember { mutableStateOf<Pair<String, String>?>(null) }
    var historyKind by rememberSaveable { mutableStateOf("expenses") }
    var categoryMenu by remember { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    var cryptoDetailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.contextVersion) { cryptoDetailId = null; explanation = null; categoryMenu = false }
    LifecycleResumeEffect(viewModel) { viewModel.refreshDate(); onPauseOrDispose { } }
    Scaffold(topBar = {
        Surface {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedPage == null || page != "dashboard") IconButton(onClick = {
                    if (page == "insights") select("history") else if (page != "dashboard") select("dashboard") else onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(when (page) { "history" -> "History"; "insights" -> "Spending insights"; else -> "Overview" },
                    Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (page == "history") {
                    IconButton(onClick = onAddExpense) { Icon(Icons.Default.Add, "Add Expense") }
                    Box {
                        IconButton(onClick = { historyMenu = true }) { Icon(Icons.Default.MoreVert, "History options") }
                        DropdownMenu(historyMenu, { historyMenu = false }) {
                            DropdownMenuItem(text = { Text("Spending insights") }, onClick = { historyMenu = false; select("insights") })
                            DropdownMenuItem(text = { Text("All recorded expenses") }, onClick = { historyMenu = false; onAllExpenses() })
                            DropdownMenuItem(text = { Text("Expense dates and times") }, onClick = {
                                historyMenu = false; explanation = "Expense dates and times" to PresentationFormat.expenseTimesExplanation
                            })
                        }
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("reporting-scroll-$page").verticalScroll(when (page) { "history" -> historyScroll; "insights" -> insightsScroll; else -> overviewScroll }).padding(16.dp)
            .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.signedIn) Text("Sign in to view recorded finances.")
            else {
                com.clarity.finance.ui.CatalogueStatus()
                PeriodSelector(state.month, state.canPrevious, state.canNext,
                    { viewModel.changeMonth(-1) }, { viewModel.changeMonth(1) })
                if (page == "dashboard") {
                    Text("Based on recorded transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (page == "insights") TextButton(onClick = { select("history") }) { Text("Monthly history") }
                if (page == "dashboard") Button(onClick = onAddExpense,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text("Add Expense")
                }
                if (page == "dashboard") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Recorded cash surplus", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                InformationAction("Recorded totals information") { explanation = "Recorded totals" to recordedExplanation }
                            }
                            Text(state.surplusText?.let { "LKR ${PresentationFormat.amount(it)}" } ?: "Unavailable",
                                Modifier.testTag("cash-surplus"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Income − expenses · Not a bank balance or automatic savings", style = MaterialTheme.typography.bodySmall)
                            if (state.surplusText != null && (state.income.fromCache || state.expenses.fromCache)) {
                                Text("Surplus uses saved data; it may be out of date.", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (state.surplusText == null) Text(state.surplusError ?: "Income and expense data must both be available to calculate surplus.")
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f ||
                                    maxOf(state.incomeText.orEmpty().length, state.expensesText.orEmpty().length) > 16
                                @Composable fun income() { CashMeasure("Ordinary received income", state.incomeText, state.income) }
                                @Composable fun expenses() { CashMeasure("Recorded expenses", state.expensesText, state.expenses) }
                                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { income(); expenses() }
                                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) { income() }; Column(Modifier.weight(1f)) { expenses() }
                                }
                            }
                        }
                    }
                    TextButton(onClick = onIncomeList) { Text("Received Income") }
                    Text("Beyond this month’s cash", style = MaterialTheme.typography.titleMedium)
                    ReportCard("Signed realised crypto net", state.cryptoText, state.crypto)
                    OutlinedButton(onClick = onCrypto) { Text("Manage Crypto Results") }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Outstanding pending income", style = MaterialTheme.typography.titleMedium)
                            Text("Current collectible amounts · Not month-end balances", style = MaterialTheme.typography.bodySmall)
                            BatchStatus(state.projects)
                            when {
                                state.pendingAmounts == null -> Text("Pending balances unavailable")
                                state.pendingAmounts!!.isEmpty() -> Text("No collectible pending income recorded.")
                                else -> state.pendingAmounts!!.forEach { (currency, amount) -> Text("$currency ${PresentationFormat.amount(amount)}", style = MaterialTheme.typography.titleLarge) }
                            }
                            InformationAction("Pending income information") { explanation = "Pending income" to "Cancelled projects are excluded; parent outstanding balances count once. Pending income does not increase received income or surplus. Original currencies remain separate; no estimated exchange rate is applied." }
                            OutlinedButton(onClick = onPayments) { Text("View Pending Payments") }
                        }
                    }
                    goalCard()
                }
                if (page == "insights") {
                    BatchStatus(state.expenses)
                    val spending = state.spending
                    if (spending == null) Text("Spending insights unavailable")
                    else if (spending.totalMinor == 0L) Text("No expenses recorded for this month. Add an expense to see spending insights.")
                    else {
                        Text("Total expenses: LKR ${PresentationFormat.minor(spending.totalMinor)}", style = MaterialTheme.typography.titleLarge)
                        Text("Committed: LKR ${PresentationFormat.minor(spending.committedMinor)} (${spending.committedPercent}%)")
                        Text("Discretionary: LKR ${PresentationFormat.minor(spending.discretionaryMinor)} (${spending.discretionaryPercent}%)")
                        Text("Shares rounded to one decimal; discretionary is the complement of committed.", style = MaterialTheme.typography.bodySmall)
                        Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                        spending.categories.forEach { category ->
                            OutlinedCard(onClick = { viewModel.selectCategory(category.id); historyKind = "expenses"; select("history") }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val label = com.clarity.finance.ui.LocalCatalogue.current.label("category", category.id)
                                    Text("${label}: LKR ${PresentationFormat.minor(category.amountMinor)}")
                                    LinearProgressIndicator(progress = { category.barFraction }, modifier = Modifier.fillMaxWidth())
                                    Text("Inspect $label expenses")
                                }
                            }
                        }
                    }
                }
                if (page == "history") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("expenses" to "Expenses", "income" to "Income", "crypto" to "Crypto history").forEach { (kind, label) ->
                            FilterChip(selected = historyKind == kind, onClick = { historyKind = kind }, label = { Text(label) })
                        }
                    }
                    if (historyKind == "expenses") {
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.testTag("history-category-filter")) { Text("Expense category: ${state.selectedCategory?.let { com.clarity.finance.ui.LocalCatalogue.current.label("category", it) } ?: "All categories"}") }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text("All categories") }, onClick = { viewModel.selectCategory(null); categoryMenu = false })
                            com.clarity.finance.ui.LocalCatalogue.current.labels("category").forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.selectCategory(id); categoryMenu = false })
                            }
                        }
                    }
                    Text(state.expensesText?.let { "Monthly total (all categories): LKR ${PresentationFormat.amount(it)}" } ?: "Expense total unavailable", style = MaterialTheme.typography.bodySmall)
                    BatchStatus(state.expenses)
                    if (state.expenses.records != null && state.expenseHistory.isEmpty()) Text("No matching expenses recorded for this month.")
                    state.expenseHistory.forEach { row ->
                        HistoryCard("${com.clarity.finance.ui.LocalCatalogue.current.label("category", row.categoryId)} • ${PresentationFormat.date(row.expenseDate)}",
                            "LKR ${PresentationFormat.minor(row.amountMinor)} • ${row.spendingType.replaceFirstChar { it.uppercase() }}", row.note.orEmpty(),
                            "Inspect / edit expense", { onExpense(row.id) }, time = PresentationFormat.serverSavedAt(row.createdAt, row.isPending))
                    }
                    }
                    if (historyKind == "income") {
                        Text("Ordinary received income", style = MaterialTheme.typography.titleLarge)
                        BatchStatus(state.income)
                        if (state.income.records != null && state.incomeHistory.isEmpty()) Text("No received income recorded for this month.")
                        state.incomeHistory.forEach { row ->
                            HistoryCard("${com.clarity.finance.ui.LocalCatalogue.current.label("source", row.sourceId)} • ${PresentationFormat.date(row.receiptDate)}",
                                "LKR ${PresentationFormat.minor(row.lkrAmountMinor)}", row.note,
                                if (row.linkedPaymentId != null) "Linked receipt: inspect project" else "Inspect / edit income",
                                { row.linkedPaymentId?.let(onProject) ?: onIncome(row.id) })
                        }
                    }
                    if (historyKind == "crypto") {
                        Text("Realised crypto results (separate from cash transactions)", style = MaterialTheme.typography.titleLarge)
                        BatchStatus(state.crypto)
                        if (state.crypto.records != null && state.crypto.records!!.isEmpty()) Text("No realised crypto results recorded for this month.")
                        state.cryptoHistory.forEach { row ->
                            HistoryCard("${row.direction} • ${PresentationFormat.date(row.resultDate)}", "LKR ${PresentationFormat.amount(CryptoReporting.money(row.signedMinor))}", row.note,
                                "Inspect crypto result", { cryptoDetailId = row.id })
                        }
                    }
                }
                if (page == "history") TextButton(onClick = onIncomeList) { Text("Received Income") }
                if (page != "dashboard") InformationAction("Recorded totals information") { explanation = "Recorded totals" to recordedExplanation }
                state.recovery?.let {
                    if (it.pending > 0 || it.rejected > 0) {
                        StatusPanel("Reporting saved changes: ${it.pending} unresolved; ${it.rejected} rejected/conflicting. Recorded totals stay unchanged until confirmation.")
                        TextButton(onClick = onRecovery) { Text("Review saved changes") }
                    }
                } ?: Text("Checking saved changes…")
                state.recoveryError?.let { Text("Saved changes unavailable: $it", color = MaterialTheme.colorScheme.error) }
                if (state.income.error != null || state.expenses.error != null || state.crypto.error != null || state.projects.error != null || state.recoveryError != null) {
                    OutlinedButton(onClick = viewModel::retry) { Text("Retry reporting data") }
                }
            }
        }
    }
    explanation?.let { (title, message) ->
        AlertDialog(onDismissRequest = { explanation = null }, title = { Text(title) },
            text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { explanation = null }) { Text("Close") } })
    }
    state.cryptoHistory.firstOrNull { it.id == cryptoDetailId }?.let { result ->
        AlertDialog(onDismissRequest = { cryptoDetailId = null }, title = { Text("Realised crypto result") }, text = {
            Text("${PresentationFormat.date(result.resultDate)}\n${result.direction}: LKR ${PresentationFormat.amount(CryptoReporting.money(result.signedMinor))}\n${result.note}\nManage changes in Crypto Results.")
        }, confirmButton = { TextButton(onClick = { cryptoDetailId = null }) { Text("Close") } })
    }
}

@Composable
private fun <T> ReportCard(title: String, amount: String?, batch: ReportingBatch<T>) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(amount?.let { "LKR ${PresentationFormat.amount(it)}" } ?: "Unavailable", style = MaterialTheme.typography.headlineSmall)
            BatchStatus(batch)
        }
    }
}

@Composable
private fun <T> BatchStatus(batch: ReportingBatch<T>) {
    batch.error?.let { StatusPanel(it, isError = true) }
    if (batch.awaitingWrites) Text("Waiting for confirmation. Recorded amounts stay in totals.", style = MaterialTheme.typography.bodySmall)
    if (batch.records != null && batch.fromCache) Text("Showing saved data; may be incomplete or out of date.", style = MaterialTheme.typography.bodySmall)
    if (batch.records == null && batch.error == null && !batch.awaitingWrites) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Loading recorded transactions…")
    }
}

@Composable
private fun HistoryCard(title: String, amount: String, note: String, action: String, onClick: () -> Unit, time: String? = null) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(amount, style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            time?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyMedium)
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private const val recordedExplanation = "Reporting uses Asia/Colombo transaction dates, not upload timestamps. Pending edits or deletions retain the original confirmed contribution. Crypto, goal allocations and pending income are excluded from cash surplus. Cash surplus is not a bank balance or automatic savings."

@Composable
private fun <T> CashMeasure(label: String, amount: String?, batch: ReportingBatch<T>) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(amount?.let { "LKR ${PresentationFormat.amount(it)}" } ?: "Unavailable", style = MaterialTheme.typography.titleMedium)
    BatchStatus(batch)
}
