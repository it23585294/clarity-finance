package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AddExpenseViewModel

@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    val owner = formState.accountKey ?: return
    val draft = formState.draft

    // Auto-navigate on success
    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetState()
            onNavigateBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
    Text("Add Expense", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
    ExpenseFormContent(
        amountStr = draft.amountStr,
        onAmountChange = { viewModel.updateDraft(owner, amountStr = it) },
        categoryId = draft.categoryId,
        onCategoryChange = { viewModel.updateDraft(owner, categoryId = it) },
        paymentChannelId = draft.paymentChannelId,
        onPaymentChannelChange = { viewModel.updateDraft(owner, paymentChannelId = it) },
        spendingType = draft.spendingType,
        onSpendingTypeChange = { viewModel.updateDraft(owner, spendingType = it) },
        expenseDate = draft.expenseDate,
        onDateChange = { viewModel.updateDraft(owner, expenseDate = it) },
        note = draft.note,
        onNoteChange = { viewModel.updateDraft(owner, note = it) },
        error = formState.error,
        isSubmitting = formState.isSubmitting,
        onSave = {
            viewModel.addExpense(
                draft.amountStr, draft.categoryId, draft.paymentChannelId, draft.spendingType, draft.expenseDate, draft.note
            )
        },
        onCancel = onNavigateBack,
        saveButtonText = "Save"
    )
    }
}
