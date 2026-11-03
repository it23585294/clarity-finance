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

    var amountStr by remember(formState.accountKey) { mutableStateOf("") }
    var categoryId by remember(formState.accountKey) { mutableStateOf("other") }
    var paymentChannelId by remember(formState.accountKey) { mutableStateOf("cash") }
    var spendingType by remember(formState.accountKey) { mutableStateOf("discretionary") }
    var expenseDate by remember(formState.accountKey) { mutableStateOf(viewModel.getDefaultDate()) }
    var note by remember(formState.accountKey) { mutableStateOf("") }

    // Auto-navigate on success
    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetState()
            onNavigateBack()
        }
    }

    ExpenseFormContent(
        amountStr = amountStr,
        onAmountChange = { amountStr = it },
        categoryId = categoryId,
        onCategoryChange = { categoryId = it },
        paymentChannelId = paymentChannelId,
        onPaymentChannelChange = { paymentChannelId = it },
        spendingType = spendingType,
        onSpendingTypeChange = { spendingType = it },
        expenseDate = expenseDate,
        onDateChange = { expenseDate = it },
        note = note,
        onNoteChange = { note = it },
        error = formState.error,
        isSubmitting = formState.isSubmitting,
        onSave = {
            viewModel.addExpense(
                amountStr, categoryId, paymentChannelId, spendingType, expenseDate, note
            )
        },
        onCancel = onNavigateBack,
        saveButtonText = "Save"
    )
}
