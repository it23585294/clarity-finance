package com.clarity.finance.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.viewmodel.PaymentDetailUiState
import com.clarity.finance.viewmodel.PaymentDetailViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    viewModel: PaymentDetailViewModel,
    paymentId: String,
    onNavigateToConfirmation: (String, String?) -> Unit,
    onNavigateBack: () -> Unit,
    onRecovery: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cancelError by viewModel.cancelError.collectAsStateWithLifecycle()
    val milestoneState by viewModel.milestoneState.collectAsStateWithLifecycle()
    val isCancelling by viewModel.isCancelling.collectAsStateWithLifecycle()
    val editForm by viewModel.editForm.collectAsStateWithLifecycle()

    editForm?.let { form -> AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = viewModel::closeEdit,
        title = { Text(if (form.milestone == null) "Edit agreement" else "Edit milestone") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Source, currency and received amounts stay unchanged. Your change is checked against the latest agreement.", style = MaterialTheme.typography.bodySmall)
            val enabled = !form.busy && !form.locked
            OutlinedTextField(form.title, { viewModel.updateEdit(title = it) }, label = { Text("Title") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.amount, { viewModel.updateEdit(amount = it) }, label = { Text("Agreed amount (${form.payment.currency})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.due, { viewModel.updateEdit(due = it) }, label = { Text("Due date (YYYY-MM-DD)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            if (form.milestone == null) OutlinedTextField(form.note, { viewModel.updateEdit(note = it) }, label = { Text("Optional note") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = viewModel::saveEdit, enabled = !form.busy) { Text(if (form.locked) "Retry same save" else "Save proposal") } },
        dismissButton = { TextButton(onClick = viewModel::closeEdit, enabled = !form.busy) { Text("Close") } }) }

    var showAddMilestoneDialog by remember { mutableStateOf(false) }
    var showCancelProjectDialog by remember { mutableStateOf(false) }
    var milestoneToCancel by remember { mutableStateOf<Milestone?>(null) }

    val owner = (uiState as? PaymentDetailUiState.Success)?.ownerUid
    LaunchedEffect(owner, paymentId) {
        showAddMilestoneDialog = false
        showCancelProjectDialog = false
        milestoneToCancel = null
    }
    LaunchedEffect(paymentId) {
        viewModel.load(paymentId)
    }

    LaunchedEffect(milestoneState.isSuccess) {
        if (milestoneState.isSuccess) {
            showAddMilestoneDialog = false
            viewModel.resetMilestoneState()
        }
    }

    if (cancelError != null) {
        AlertDialog(containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Cancellation Error") },
            text = { Text(cancelError!!) },
            confirmButton = { Button(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    if (showCancelProjectDialog && uiState is PaymentDetailUiState.Success) {
        val p = (uiState as PaymentDetailUiState.Success).payment
        AlertDialog(containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showCancelProjectDialog = false },
            title = { Text("Cancel agreement?") },
            text = { Text("Are you sure you want to cancel '${p.title}'? Existing agreements and receipts will be retained. No further payments can be recorded.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelProjectDialog = false
                        viewModel.cancelPayment(p)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelProjectDialog = false }) {
                    Text("Keep agreement")
                }
            }
        )
    }

    if (milestoneToCancel != null && uiState is PaymentDetailUiState.Success) {
        val ms = milestoneToCancel!!
        val p = (uiState as PaymentDetailUiState.Success).payment
        val unpaidStr = PresentationFormat.minor(ms.outstandingAmountMinor)
        AlertDialog(containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { milestoneToCancel = null },
            title = { Text("Cancel Milestone") },
            text = { Text("Are you sure you want to cancel '${ms.title}'? Only the unpaid allocation of ${p.currency} $unpaidStr will be released. Agreed and received amounts remain in history.") },
            confirmButton = {
                Button(
                    onClick = {
                        val targetMs = ms
                        milestoneToCancel = null
                        viewModel.cancelMilestone(p.id, targetMs)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { milestoneToCancel = null }) {
                    Text("Keep Milestone")
                }
            }
        )
    }

    if (showAddMilestoneDialog && uiState is PaymentDetailUiState.Success) {
        val p = (uiState as PaymentDetailUiState.Success).payment
        AddMilestoneDialog(
            project = p,
            isSubmitting = milestoneState.isSubmitting,
            retryMilestone = milestoneState.retryMilestone,
            error = milestoneState.error,
            onDismiss = {
                showAddMilestoneDialog = false
                viewModel.resetMilestoneState()
            },
            onConfirm = { title, amountStr, dueDate ->
                viewModel.createMilestone(p, title, amountStr, dueDate)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader("Agreement details", onNavigateBack)
        if (isCancelling) {
            Text("Waiting for server cancellation confirmation…")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when (val state = uiState) {
            is PaymentDetailUiState.Loading -> {
                LoadingPanel("Loading agreement")
            }
            is PaymentDetailUiState.Error -> {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.retryLoad() }) { Text("Retry loading") }
            }
            is PaymentDetailUiState.SignedOut -> {}
            is PaymentDetailUiState.Success -> {
                val p = state.payment
                LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = p.title, style = MaterialTheme.typography.titleLarge)
                        Text(text = "Due: ${PresentationFormat.date(p.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("source", p.sourceId), style = MaterialTheme.typography.bodyMedium)
                        Text(if (p.isCancelled) "Unpaid agreement · History only" else "Outstanding", style = MaterialTheme.typography.bodyMedium)
                        Text("${p.currency} ${PresentationFormat.minor(p.outstandingAmountMinor)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(text = "Agreement: ${p.currency} ${PresentationFormat.minor(p.agreedAmountMinor)}")
                        Text(text = "Received: ${p.currency} ${PresentationFormat.minor(p.totalReceivedAmountMinor)}")
                        Text(text = "Unallocated: ${p.currency} ${PresentationFormat.minor(p.unallocatedAmountMinor)}")
                        Text(when {
                            p.isCancelled -> "Cancelled agreements are excluded from collectible pending balances."
                            p.type == "project" -> "Unallocated is available for direct payments or new milestones."
                            else -> "Unallocated is available for recording a payment."
                        }, style = MaterialTheme.typography.bodySmall)
                        
                        TextButton(onClick = onRecovery) { Text("Review saved changes") }
                        if (p.isFromCache) Text("Cached agreement. Proposed changes await server version and capacity checks.")
                        if (p.confirmationMessage != null) {
                            Text(
                                text = p.confirmationMessage ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text("Actions wait for the required confirmation details.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { viewModel.retryLoad() }) { Text("Refresh confirmation") }
                        }

                        if (p.isCancelled) {
                            Text(text = "CANCELLED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(enabled = p.canModify && !isCancelling, onClick = viewModel::editProject) { Text("Edit agreement") }
                                if (p.unallocatedAmountMinor > 0) {
                                    Button(enabled = p.canModify && !isCancelling, onClick = { onNavigateToConfirmation(p.id, null) }) { Text("Record payment") }
                                }
                                if (p.type == "project") {
                                    Button(enabled = p.canModify && !isCancelling, onClick = {
                                        viewModel.resetMilestoneState()
                                        showAddMilestoneDialog = true
                                    }) { Text("Add milestone") }
                                }
                                val canCancel = p.canModify && !isCancelling
                                OutlinedButton(
                                    onClick = { showCancelProjectDialog = true },
                                    enabled = canCancel
                                ) {
                                    Text("Cancel agreement", color = if (canCancel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                }
                            }
                        }
                    }
                }
                }

                if (state.milestones.isNotEmpty()) {
                    item {
                    Text("Milestones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                        items(state.milestones, key = { it.id }) { ms ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = ms.title, style = MaterialTheme.typography.titleMedium)
                                    Text(text = "Due: ${PresentationFormat.date(ms.dueDate)}", style = MaterialTheme.typography.bodySmall)
                                    Text(text = "Agreed: ${p.currency} ${PresentationFormat.minor(ms.agreedAmountMinor)}")
                                    Text(text = "Received: ${p.currency} ${PresentationFormat.minor(ms.receivedAmountMinor)}")
                                    Text(text = "Unpaid agreement: ${p.currency} ${PresentationFormat.minor(ms.outstandingAmountMinor)}")
                                    if (ms.isCancelled) Text("Agreement and received history retained.", style = MaterialTheme.typography.bodySmall)
                                    
                                    if (ms.isFromCache) Text("Cached milestone; changes await server checks.")
                                    OutlinedButton(enabled = p.canModify && ms.canModify && !isCancelling, onClick = { viewModel.editMilestone(ms) }) { Text("Edit milestone") }
                                    if (ms.confirmationMessage != null) {
                                        Text(
                                            text = ms.confirmationMessage ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    if (ms.isCancelled) {
                                        Text(text = "CANCELLED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    } else if (!p.isCancelled) {
                                        FlowRow(
                                            modifier = Modifier.padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (ms.outstandingAmountMinor > 0) {
                                                Button(enabled = p.canModify && ms.canModify && !isCancelling, onClick = { onNavigateToConfirmation(p.id, ms.id) }) { Text("Record milestone payment") }
                                            }
                                            if (ms.outstandingAmountMinor > 0) {
                                                val canCancelMs = p.canModify && ms.canModify && !isCancelling
                                                OutlinedButton(
                                                    onClick = { milestoneToCancel = ms },
                                                    enabled = canCancelMs
                                                ) {
                                                    Text("Cancel milestone", color = if (canCancelMs) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
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
}

@Composable
fun AddMilestoneDialog(
    project: ExpectedPayment,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    retryMilestone: Milestone? = null
) {
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toString()) }
    LaunchedEffect(retryMilestone) {
        retryMilestone?.let {
            title = it.title
            amountStr = java.math.BigDecimal.valueOf(it.agreedAmountMinor, 2).toPlainString()
            dueDate = it.dueDate
        }
    }

    AlertDialog(containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Add milestone") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (error != null) {
                    Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                DetailValue("Inherited currency", project.currency)
                DetailValue("Inherited source", com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("source", project.sourceId))
                val unallocStr = PresentationFormat.minor(project.unallocatedAmountMinor)
                Text("Unallocated Capacity: ${project.currency} $unallocStr", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    enabled = !isSubmitting && retryMilestone == null,
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Milestone Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    enabled = !isSubmitting && retryMilestone == null,
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Agreed Amount *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    enabled = !isSubmitting && retryMilestone == null,
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (yyyy-MM-dd) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, amountStr, dueDate) },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (retryMilestone != null) "Retry Add" else "Add")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}
