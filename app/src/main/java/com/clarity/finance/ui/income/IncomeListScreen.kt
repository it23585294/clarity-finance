package com.clarity.finance.ui.income

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.Income
import com.clarity.finance.viewmodel.IncomeListUiState
import com.clarity.finance.viewmodel.IncomeListViewModel

@Composable
fun IncomeListScreen(
    viewModel: IncomeListViewModel,
    onNavigateToAddIncome: () -> Unit,
    onNavigateToEditIncome: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Received Income", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNavigateBack) { Text("Back") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToAddIncome, modifier = Modifier.fillMaxWidth()) {
            Text("Add Income")
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is IncomeListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is IncomeListUiState.Error -> {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            is IncomeListUiState.SignedOut -> {}
            is IncomeListUiState.Success -> {
                // Month Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.changeMonth(-1) }) { Icon(Icons.Default.ArrowBack, contentDescription = "Previous") }
                    Text(text = "${state.currentMonth.month.name} ${state.currentMonth.year}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.changeMonth(1) }) { Icon(Icons.Default.ArrowForward, contentDescription = "Next") }
                }

                // Totals
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val totalStr = String.format(java.util.Locale.US, "%.2f", state.confirmedTotalLkr / 100.0)
                        Text("Confirmed Total: LKR $totalStr", style = MaterialTheme.typography.titleLarge)
                        
                        if (state.pendingTotalLkr > 0) {
                            val pendStr = String.format(java.util.Locale.US, "%.2f", state.pendingTotalLkr / 100.0)
                            Text("+ Pending: LKR $pendStr", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }

                        if (state.totalsBySource.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("By Source:", style = MaterialTheme.typography.labelLarge)
                            state.totalsBySource.forEach { (source, amount) ->
                                val srcStr = String.format(java.util.Locale.US, "%.2f", amount / 100.0)
                                Text("• $source: LKR $srcStr", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.incomes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No income recorded for this month")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.incomes, key = { it.id }) { income ->
                            val isClickable = !income.isPending && income.syncError == null && !income.isConflict
                            IncomeItem(
                                income = income,
                                modifier = if (isClickable) Modifier.clickable { onNavigateToEditIncome(income.id) } else Modifier,
                                onRetry = { viewModel.retryIncome(income) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeItem(income: Income, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${income.receiptDate} - ${income.sourceId}", style = MaterialTheme.typography.bodyLarge)
            val lkrStr = String.format(java.util.Locale.US, "%.2f", income.lkrAmountMinor / 100.0)
            Text(text = "LKR $lkrStr", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Original: ${income.currency} ${income.originalAmountStr}", style = MaterialTheme.typography.bodyMedium)
            
            if (income.syncError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (income.isConflict) "Conflict" else "Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onRetry) { Text("Retry", style = MaterialTheme.typography.bodySmall) }
                }
            } else if (income.isPending) {
                Text(text = "Pending", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (income.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Note: ${income.note}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
