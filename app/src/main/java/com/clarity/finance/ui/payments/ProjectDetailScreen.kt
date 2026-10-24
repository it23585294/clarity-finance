package com.clarity.finance.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.PaymentDetailUiState
import com.clarity.finance.viewmodel.PaymentDetailViewModel

@Composable
fun ProjectDetailScreen(
    viewModel: PaymentDetailViewModel,
    paymentId: String,
    onNavigateToConfirmation: (String, String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cancelError by viewModel.cancelError.collectAsStateWithLifecycle()

    LaunchedEffect(paymentId) {
        viewModel.load(paymentId)
    }

    if (cancelError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Cancellation Error") },
            text = { Text(cancelError!!) },
            confirmButton = { Button(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Project Details", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNavigateBack) { Text("Back") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is PaymentDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is PaymentDetailUiState.Error -> {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            is PaymentDetailUiState.Success -> {
                val p = state.payment
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = p.title, style = MaterialTheme.typography.titleLarge)
                        Text(text = "Due: ${p.dueDate}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Agreement: ${p.currency} ${String.format(java.util.Locale.US, "%.2f", p.agreedAmountMinor / 100.0)}")
                        Text(text = "Received: ${p.currency} ${String.format(java.util.Locale.US, "%.2f", p.totalReceivedAmountMinor / 100.0)}")
                        Text(text = "Outstanding: ${p.currency} ${String.format(java.util.Locale.US, "%.2f", p.outstandingAmountMinor / 100.0)}")
                        Text(text = "Unallocated: ${p.currency} ${String.format(java.util.Locale.US, "%.2f", p.unallocatedAmountMinor / 100.0)}")
                        
                        if (p.isCancelled) {
                            Text(text = "CANCELLED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (p.unallocatedAmountMinor > 0) {
                                    Button(onClick = { onNavigateToConfirmation(p.id, null) }) { Text("Record Direct") }
                                }
                                OutlinedButton(onClick = { viewModel.cancelPayment(p) }) { Text("Cancel Project", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }

                if (state.milestones.isNotEmpty()) {
                    Text("Milestones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.milestones, key = { it.id }) { ms ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = ms.title, style = MaterialTheme.typography.titleMedium)
                                    Text(text = "Due: ${ms.dueDate}", style = MaterialTheme.typography.bodySmall)
                                    Text(text = "Agreed: ${p.currency} ${String.format(java.util.Locale.US, "%.2f", ms.agreedAmountMinor / 100.0)}")
                                    Text(text = "Outstanding: ${p.currency} ${String.format(java.util.Locale.US, "%.2f", ms.outstandingAmountMinor / 100.0)}")
                                    
                                    if (ms.isCancelled) {
                                        Text(text = "CANCELLED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (ms.outstandingAmountMinor > 0) {
                                                Button(onClick = { onNavigateToConfirmation(p.id, ms.id) }) { Text("Record") }
                                            }
                                            if (ms.receivedAmountMinor == 0L) { // Only allow cancel if no payments made
                                                OutlinedButton(onClick = { viewModel.cancelMilestone(p.id, ms) }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
