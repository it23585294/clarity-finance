package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.EditExpenseViewModel

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EditExpenseScreen(
    viewModel: EditExpenseViewModel,
    expenseId: String,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editing by rememberSaveable(expenseId) { mutableStateOf(false) }

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
        com.clarity.finance.ui.pilot.LoadingPanel("Loading expense")
        return
    }

    if (formState.isMissing && !formState.isSubmitting && !formState.isDeleting) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Expense not found or already deleted.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
                Text("Back")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (editing) "Edit Expense" else "Expense details", style = MaterialTheme.typography.headlineSmall)
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
                    Text("This expense changed elsewhere. Review the latest record before saving. Your draft is retained.", color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadExpense(expenseId) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Reload from Server")
                    }
                }
            }
        }

        if (!editing) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("LKR ${PresentationFormat.amount(formState.amountStr)}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Recorded expense", style = MaterialTheme.typography.titleMedium)
                if (formState.fromCache) Text("Showing saved data; may be incomplete or out of date.")
                formState.savedChange?.let {
                    val label = when (it) { "UPDATE" -> "Proposed edit"; "DELETE" -> "Proposed deletion"; else -> "Saved proposal" }
                    StatusPanel("$label. The original recorded contribution is retained.")
                }
                formState.changeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                DetailValue("Payment date", PresentationFormat.date(formState.expenseDate))
                DetailValue("Category", com.clarity.finance.ui.LocalCatalogue.current.label("category", formState.categoryId))
                DetailValue("Spending type", formState.spendingType.replaceFirstChar { it.uppercase() })
                DetailValue("Payment channel", channelUiMap[formState.paymentChannelId] ?: formState.paymentChannelId)
                if (formState.note.isNotBlank()) Text(formState.note)
                formState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { editing = true }, enabled = !formState.isSubmitting && !formState.isDeleting) { Text("Edit expense") }
                OutlinedButton(onClick = onNavigateBack) { Text("Back") }
            }
        } else ExpenseFormContent(
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
            text = { Text("Are you sure you want to delete this expense? (LKR ${PresentationFormat.amount(formState.amountStr)} on ${PresentationFormat.date(formState.expenseDate)})") },
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
