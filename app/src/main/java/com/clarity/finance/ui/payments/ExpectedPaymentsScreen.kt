package com.clarity.finance.ui.payments

import androidx.compose.foundation.clickable
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
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.viewmodel.PaymentListUiState
import com.clarity.finance.viewmodel.PaymentListViewModel

@Composable
fun ExpectedPaymentsScreen(
    viewModel: PaymentListViewModel,
    onNavigateToProjectDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pending Payments", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNavigateBack) { Text("Back") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is PaymentListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is PaymentListUiState.Error -> {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            is PaymentListUiState.SignedOut -> {}
            is PaymentListUiState.Success -> {
                if (state.payments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No pending payments")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.payments, key = { it.id }) { payment ->
                            ExpectedPaymentItem(
                                payment = payment,
                                modifier = Modifier.clickable { onNavigateToProjectDetail(payment.id) }
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
fun ExpectedPaymentItem(payment: ExpectedPayment, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = payment.title, style = MaterialTheme.typography.titleMedium)
            val agreedStr = String.format(java.util.Locale.US, "%.2f", payment.agreedAmountMinor / 100.0)
            Text(text = "${payment.currency} $agreedStr", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Due: ${payment.dueDate} • ${if (payment.type == "project") "Project" else "Standalone"}", style = MaterialTheme.typography.bodyMedium)
            
            val outstandingStr = String.format(java.util.Locale.US, "%.2f", payment.outstandingAmountMinor / 100.0)
            Text(text = "Pending: ${payment.currency} $outstandingStr", color = if (payment.isCancelled) Color.Red else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (payment.isCancelled) {
            Text(text = "Cancelled", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
