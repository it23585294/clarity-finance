package com.clarity.finance.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.PaymentConfirmationViewModel
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Record Payment", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(text = "This action requires an active network connection. The payment will be atomically synced with the project balance.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }

        OutlinedTextField(
            value = formState.amountStr,
            onValueChange = { viewModel.updateForm(amountStr = it) },
            label = { Text("Amount (${formState.currency})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        if (formState.currency == "USD") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = formState.conversionBasis == "confirmed_rate", onClick = { viewModel.updateForm(conversionBasis = "confirmed_rate") })
                    Text("Exchange Rate")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = formState.conversionBasis == "actual_credit", onClick = { viewModel.updateForm(conversionBasis = "actual_credit") })
                    Text("Actual LKR")
                }
            }

            if (formState.conversionBasis == "confirmed_rate") {
                OutlinedTextField(
                    value = formState.conversionRateStr,
                    onValueChange = { viewModel.updateForm(conversionRateStr = it) },
                    label = { Text("Confirmed LKR per USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (formState.conversionBasis == "actual_credit") {
                OutlinedTextField(
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
            value = formState.receiptDate,
            onValueChange = { viewModel.updateForm(receiptDate = it) },
            label = { Text("Receipt Date (yyyy-MM-dd)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = formState.note,
            onValueChange = { viewModel.updateForm(note = it) },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (formState.error != null) {
            Text(text = formState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.confirmPayment() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !formState.isSubmitting
        ) {
            if (formState.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Confirm Payment")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
