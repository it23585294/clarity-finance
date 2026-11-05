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
import com.clarity.finance.ui.pilot.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.viewmodel.PaymentListUiState
import com.clarity.finance.viewmodel.PaymentListViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpectedPaymentsScreen(
    viewModel: PaymentListViewModel,
    onNavigateToProjectDetail: (String) -> Unit,
    onNavigateToAddPayment: () -> Unit,
    onNavigateBack: () -> Unit,
    onRecovery: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader("Pending agreements", onNavigateBack)
        Button(onClick = onNavigateToAddPayment, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add agreement") }
        Text("Expected income · Only recorded payments become received income", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRecovery) { Text("Review saved changes") }
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is PaymentListUiState.Loading -> {
                LoadingPanel("Loading agreements")
            }
            is PaymentListUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryLoad() }) { Text("Retry loading") }
                    }
                }
            }
            is PaymentListUiState.SignedOut -> {}
            is PaymentListUiState.Success -> {
                if (state.payments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No pending agreements")
                            Text("Use Add agreement to record expected income.", style = MaterialTheme.typography.bodyMedium)
                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpectedPaymentItem(payment: ExpectedPayment, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = payment.title, style = MaterialTheme.typography.titleMedium)
            val agreedStr = PresentationFormat.minor(payment.agreedAmountMinor)
            Text(text = "${payment.currency} $agreedStr", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Due: ${PresentationFormat.date(payment.dueDate)} · ${if (payment.type == "project") "Project" else "Standalone"}", style = MaterialTheme.typography.bodyMedium)
            
            val outstandingStr = PresentationFormat.minor(payment.outstandingAmountMinor)
            Text(text = "${if (payment.isCancelled) "Unpaid history" else "Outstanding"}: ${payment.currency} $outstandingStr", color = if (payment.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Text(com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("source", payment.sourceId), style = MaterialTheme.typography.bodySmall)
        payment.confirmationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (payment.isCancelled) {
            Text(text = "Cancelled", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
