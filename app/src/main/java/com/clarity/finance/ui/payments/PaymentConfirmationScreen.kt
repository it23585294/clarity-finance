package com.clarity.finance.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.PaymentConfirmationViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaymentConfirmationScreen(
    viewModel: PaymentConfirmationViewModel,
    paymentId: String,
    milestoneId: String?,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(paymentId, milestoneId) {
        viewModel.load(paymentId, milestoneId)
    }

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            onNavigateBack()
        }
    }

    if (formState.isLoading) {
        LoadingPanel("Loading payment details")
        return
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Record payment", onNavigateBack)
        Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(if (milestoneId == null) "Direct receipt" else "Milestone receipt", style = MaterialTheme.typography.titleMedium)
        DetailValue("Inherited source", com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("source", formState.sourceId))
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(text = "Record money already received. A saved proposal changes confirmed income and agreement balances only after confirmation.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }

        OutlinedTextField(
            enabled = !formState.isSubmitting && !formState.isRetry,
            value = formState.amountStr,
            onValueChange = { viewModel.updateForm(amountStr = it) },
            label = { Text("Amount (${formState.currency})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        if (formState.currency == "USD") {
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceOption("Exchange Rate", formState.conversionBasis == "confirmed_rate", !formState.isSubmitting && !formState.isRetry) { viewModel.updateForm(conversionBasis = "confirmed_rate") }
                ChoiceOption("Actual LKR", formState.conversionBasis == "actual_credit", !formState.isSubmitting && !formState.isRetry) { viewModel.updateForm(conversionBasis = "actual_credit") }
            }

            if (formState.conversionBasis == "confirmed_rate") {
                OutlinedTextField(
            enabled = !formState.isSubmitting && !formState.isRetry,
                    value = formState.conversionRateStr,
                    onValueChange = { viewModel.updateForm(conversionRateStr = it) },
                    label = { Text("Confirmed LKR per USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (formState.conversionBasis == "actual_credit") {
                OutlinedTextField(
            enabled = !formState.isSubmitting && !formState.isRetry,
                    value = formState.actualLkrCreditStr,
                    onValueChange = { viewModel.updateForm(actualLkrCreditStr = it) },
                    label = { Text("Actual LKR Credited") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            enabled = !formState.isSubmitting && !formState.isRetry,
            value = formState.receiptDate,
            onValueChange = { viewModel.updateForm(receiptDate = it) },
            label = { Text("Receipt Date (yyyy-MM-dd)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            enabled = !formState.isSubmitting && !formState.isRetry,
            value = formState.note,
            onValueChange = { viewModel.updateForm(note = it) },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (formState.isRetry) StatusPanel("A saved payment is unresolved. Retry checks the same original details; they cannot be edited here.")
        if (formState.error != null) {
            Text(text = formState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (formState.error != null && !formState.isRetry && formState.sourceId.isEmpty()) {
            TextButton(onClick = { viewModel.load(paymentId, milestoneId) }) { Text("Retry loading") }
        }
        Button(
            onClick = { viewModel.confirmPayment() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !formState.isSubmitting && formState.sourceId.isNotEmpty()
        ) {
            if (formState.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text(if (formState.isRetry) "Retry Payment" else "Confirm Payment")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
