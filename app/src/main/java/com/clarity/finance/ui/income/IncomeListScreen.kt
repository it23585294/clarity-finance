package com.clarity.finance.ui.income

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.Income
import com.clarity.finance.ui.LocalCatalogue
import com.clarity.finance.ui.pilot.*
import com.clarity.finance.viewmodel.IncomeListUiState
import com.clarity.finance.viewmodel.IncomeListViewModel

@Composable
fun IncomeListScreen(viewModel: IncomeListViewModel, onNavigateToAddIncome: () -> Unit,
    onNavigateToEditIncome: (String) -> Unit, onNavigateBack: () -> Unit,
    onNavigateToProject: (String) -> Unit = {}, onRecovery: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var showSources by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader("Received Income", onNavigateBack)
        when (val state = uiState) {
            is IncomeListUiState.Loading -> LoadingPanel("Loading income")
            is IncomeListUiState.Error -> StatusPanel(state.message, isError = true)
            is IncomeListUiState.SignedOut -> Unit
            is IncomeListUiState.Success -> {
                // Summary stays INSIDE the list: large source summaries cannot consume its viewport.
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        PeriodSelector(state.currentMonth.toString(), true, true,
                            { viewModel.changeMonth(-1) }, { viewModel.changeMonth(1) })
                        Button(onNavigateToAddIncome, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add Income") }
                        com.clarity.finance.ui.CatalogueStatus()
                        Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Recorded received income", style = MaterialTheme.typography.titleMedium)
                                Text("LKR ${PresentationFormat.minor(state.confirmedTotalLkr)}", style = MaterialTheme.typography.headlineSmall)
                                if (state.pendingTotalLkr > 0) Text("Not yet confirmed: LKR ${PresentationFormat.minor(state.pendingTotalLkr)}", style = MaterialTheme.typography.bodyMedium)
                                if (state.incomes.any { it.isFromCache }) Text("Includes saved data; it may be out of date.", style = MaterialTheme.typography.bodySmall)
                                if (state.totalsBySource.isNotEmpty()) {
                                    TextButton(onClick = { showSources = !showSources }) { Text(if (showSources) "Hide source totals" else "By source") }
                                    if (showSources) state.totalsBySource.forEach { (source, amount) ->
                                        DetailValue(LocalCatalogue.current.referenceLabel("source", source), "LKR ${PresentationFormat.minor(amount)}")
                                    }
                                }
                            }
                        }
                        TextButton(onRecovery) { Text("Review saved changes") }
                    }
                    if (state.incomes.isEmpty()) item { Text("No income recorded for this month", Modifier.padding(16.dp)) }
                    items(state.incomes, key = { it.id }) { income ->
                        val isClickable = !income.isPending && income.syncError == null && !income.isConflict && income.queuedChange == null
                        Surface(shape = MaterialTheme.shapes.medium) {
                            IncomeItem(income, if (isClickable) Modifier.clickable(onClickLabel = if (income.linkedPaymentId != null) "Open linked project" else "Inspect income") {
                                income.linkedPaymentId?.let(onNavigateToProject) ?: onNavigateToEditIncome(income.id)
                            } else Modifier, onRetry = { viewModel.retryIncome(income) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeItem(income: Income, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("LKR ${PresentationFormat.minor(income.lkrAmountMinor)}", style = MaterialTheme.typography.titleLarge)
        Text(LocalCatalogue.current.referenceLabel("source", income.sourceId), style = MaterialTheme.typography.titleMedium)
        Text(PresentationFormat.date(income.receiptDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val original = income.originalAmountMinor?.let(PresentationFormat::minor) ?: PresentationFormat.amount(income.originalAmountStr)
        Text("Original: ${income.currency} $original", style = MaterialTheme.typography.bodyMedium)
        if (income.currency == "USD") Text(when (income.conversionBasis) {
            "confirmed_rate" -> "Confirmed rate: ${income.conversionRateStr} LKR/USD"
            "actual_credit" -> "Actual LKR credited"
            else -> "Conversion basis: ${income.conversionBasis}"
        }, style = MaterialTheme.typography.bodySmall)
        income.queuedChange?.let { StatusPanel(it) }
        if (income.isFromCache && !income.isPending) Text("Saved data · May be out of date", style = MaterialTheme.typography.bodySmall)
        if (income.syncError != null) {
            StatusPanel(if (income.isConflict) "Change rejected. ${income.syncError}" else "Confirmation unknown. ${income.syncError}", isError = income.isConflict)
            TextButton(onClick = onRetry) { Text("Retry") }
        } else if (income.isPending) Text("Waiting for confirmation · Not recorded income yet", style = MaterialTheme.typography.bodySmall)
        if (income.note.isNotBlank()) Text("Note: ${income.note}", style = MaterialTheme.typography.bodyMedium)
        if (income.linkedPaymentId != null) Text("Linked payment · View project", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
