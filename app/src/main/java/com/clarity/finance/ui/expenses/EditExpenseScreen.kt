package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.EditExpenseViewModel

@Composable
fun EditExpenseScreen(
    viewModel: EditExpenseViewModel,
    expenseId: String,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        viewModel.loadExpense(expenseId)
    }

    LaunchedEffect(formState.isSuccess, formState.isDeleted) {
        if (formState.isSuccess || formState.isDeleted) {
            viewModel.resetState()
            onNavigateBack()
        }
    }

    if (formState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (formState.isMissing && !formState.isSubmitting && !formState.isDeleting) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Expense not found or already deleted.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
                Text("Go Back")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Edit Expense", style = MaterialTheme.typography.headlineSmall)
            if (!formState.isStale && !formState.isMissing) {
                TextButton(onClick = { showDeleteDialog = true }, enabled = !formState.isSubmitting && !formState.isDeleting) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (formState.isStale) {
            // Conflict handling: Keep the user's unsaved form below, but show warning + reload action above
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Warning: This expense was modified elsewhere since you opened it. Saving now would overwrite those changes.", color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadExpense(expenseId) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Reload from Server")
                    }
                }
            }
        }

        ExpenseFormContent(
            amountStr = formState.amountStr,
            onAmountChange = { viewModel.updateForm(amountStr = it) },
            categoryId = formState.categoryId,
            onCategoryChange = { viewModel.updateForm(categoryId = it) },
            paymentChannelId = formState.paymentChannelId,
            onPaymentChannelChange = { viewModel.updateForm(paymentChannelId = it) },
            spendingType = formState.spendingType,
            onSpendingTypeChange = { viewModel.updateForm(spendingType = it) },
            expenseDate = formState.expenseDate,
            onDateChange = { viewModel.updateForm(expenseDate = it) },
            note = formState.note,
            onNoteChange = { viewModel.updateForm(note = it) },
            error = formState.error,
            isSubmitting = formState.isSubmitting || formState.isDeleting,
            onSave = { viewModel.saveChanges() },
            onCancel = onNavigateBack,
            saveButtonText = "Save Changes"
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this expense? (${formState.amountStr} LKR on ${formState.expenseDate})") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteExpense()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
