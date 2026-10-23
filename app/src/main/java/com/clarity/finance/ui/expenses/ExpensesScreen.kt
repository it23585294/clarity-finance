package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.Expense
import com.clarity.finance.viewmodel.ExpenseListViewModel
import com.clarity.finance.viewmodel.ExpensesUiState

@Composable
fun ExpensesScreen(
    viewModel: ExpenseListViewModel,
    onNavigateToAddExpense: () -> Unit,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.expensesState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Clarity Expenses", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onSignOut) {
                Text("Sign Out")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onNavigateToAddExpense,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Expense")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is ExpensesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ExpensesUiState.Error -> {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            is ExpensesUiState.SignedOut -> {
                // Handled by navigation
            }
            is ExpensesUiState.Success -> {
                if (state.expenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No expenses recorded")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.expenses, key = { it.id }) { expense ->
                            ExpenseItem(
                                expense = expense, 
                                onRetry = { viewModel.retryExpense(expense) }
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
fun ExpenseItem(expense: Expense, onRetry: () -> Unit) {
    val categoryUiMap = mapOf(
        "food_dining" to "Food & Dining",
        "transport" to "Transport",
        "housing" to "Housing",
        "utilities" to "Utilities",
        "shopping" to "Shopping",
        "health" to "Health",
        "entertainment" to "Entertainment",
        "education" to "Education",
        "other" to "Other"
    )

    val channelUiMap = mapOf(
        "cash" to "Cash",
        "primary_bank_card" to "Primary bank/card",
        "secondary_bank_card" to "Secondary bank/card",
        "other" to "Other"
    )
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${expense.expenseDate} - ${categoryUiMap[expense.categoryId] ?: expense.categoryId}", style = MaterialTheme.typography.bodyLarge)
            val amountStr = String.format(java.util.Locale.US, "%.2f", expense.amountMinor / 100.0)
            Text(text = "${expense.currency} $amountStr", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${channelUiMap[expense.paymentChannelId] ?: expense.paymentChannelId} • ${expense.spendingType}", style = MaterialTheme.typography.bodyMedium)
            
            if (expense.syncError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (expense.isConflict) "Conflict" else "Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onRetry) {
                        Text("Retry", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (expense.isPending) {
                Text(text = "Pending", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!expense.note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Note: ${expense.note}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
