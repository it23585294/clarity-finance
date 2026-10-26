package com.clarity.finance.ui.crypto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clarity.finance.data.model.CryptoReporting
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.clarity.finance.viewmodel.CryptoForm
import com.clarity.finance.viewmodel.CryptoViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CryptoScreen(viewModel: CryptoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReportingDate()
        onPauseOrDispose { }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Crypto Results") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.signedIn) Text("Sign in to view your realised crypto results.")
            else {
                Text("Record realised profit or loss in LKR, not gross sale proceeds, capital deposits or withdrawals, or unrealised price changes.")
                Text("Crypto stays separate from income, expenses, cash surplus, pending payments and goal savings.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::add, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Add Result") }
                Text("Reporting timezone: Asia/Colombo", style = MaterialTheme.typography.bodySmall)
                Text("Selected month: ${state.month}", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.changeMonth(-1) }, enabled = state.canPreviousMonth && !state.busy) { Text("Previous month") }
                    OutlinedButton(onClick = { viewModel.changeMonth(1) }, enabled = state.canNextMonth && !state.busy) { Text("Next month") }
                    OutlinedButton(onClick = { viewModel.changeYear(-1) }, enabled = state.canPreviousYear && !state.busy) { Text("Previous year") }
                    OutlinedButton(onClick = { viewModel.changeYear(1) }, enabled = state.canNextYear && !state.busy) { Text("Next year") }
                }
                Text("Month net: LKR ${state.monthNet}", style = MaterialTheme.typography.titleLarge)
                Text("Year-to-date net: LKR ${state.ytdNet}", style = MaterialTheme.typography.titleLarge)
                Text("Reporting year ${state.month.take(4)}: January 1 through ${state.cutoff}")
                Text("Based on confirmed/cached records. Pending and failed proposals do not change these totals.", style = MaterialTheme.typography.bodySmall)
                if (state.cached) Text("Cached view: totals may be incomplete or out of date; not a fresh server confirmation.")
                state.message?.let { Text(it) }
                if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading results and saved attempts...") }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::retryLoad, enabled = !state.busy) { Text("Retry loading") }
                }
                if (state.unsettled.isNotEmpty()) {
                    Text("Pending / failed changes (all years)", style = MaterialTheme.typography.titleMedium)
                    state.unsettled.forEach { result ->
                        CryptoRow(result, onInspect = { viewModel.inspect(result) })
                        if (result.sync == CryptoSync.FAILED) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.retry(result) }, enabled = !state.busy) { Text("Retry saved change") }
                                OutlinedButton(onClick = { viewModel.dismissRejected(result) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                            }
                            Text("Dismiss only removes the rejected local proposal. Then reopen the server record to correct it, or add the corrected result.", style = MaterialTheme.typography.bodySmall)
                        } else Text("Saved locally. Synchronises while this screen is open. A timeout does not cancel the write.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Month history (confirmed/cached records)", style = MaterialTheme.typography.titleMedium)
                if (state.history.isEmpty() && !state.loading) Text(if (state.cached)
                    "No cached results available for this month. Connect to load server history."
                    else "No realised results recorded for this month.")
                state.history.forEach { result -> CryptoRow(result, onInspect = { viewModel.inspect(result) }) }
            }
        }
    }
    state.form?.let { form -> CryptoFormDialog(form, viewModel) }
    state.selected?.let { result ->
        AlertDialog(onDismissRequest = viewModel::closeDetails, title = { Text("Realised result") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${result.direction.replaceFirstChar { it.uppercase() }}: LKR ${CryptoReporting.money(result.signedMinor)}")
                Text("Result date: ${result.resultDate}")
                Text("Note: ${result.note.ifEmpty { "None" }}")
                Text("Status: ${status(result)}")
                result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (result.sync == CryptoSync.CONFIRMED && !state.selectedHasAttempt) {
                    OutlinedButton(onClick = { viewModel.edit(result) }, enabled = !state.busy) { Text("Edit Result") }
                    OutlinedButton(onClick = { viewModel.requestDelete(result) }, enabled = !state.busy) { Text("Delete Result") }
                } else Text("Resolve any saved proposal first. Editing and deletion require a confirmed server version.")
            }
        }, confirmButton = { TextButton(onClick = viewModel::closeDetails) { Text("Close") } })
    }
    state.deleteTarget?.let {
        AlertDialog(onDismissRequest = viewModel::dismissDelete, title = { Text("Delete realised result?") },
            text = { Text("This removes only this crypto result and its contribution to crypto totals after confirmation.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, enabled = !state.busy) { Text(if (state.busy) "Saving deletion..." else "Confirm Delete") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete, enabled = !state.busy) { Text("Keep Result") } })
    }
}

private fun status(result: CryptoResult): String = when (result.sync) {
    CryptoSync.CONFIRMED -> "Confirmed by server"
    CryptoSync.CACHED -> "Cached; awaiting fresh server version"
    CryptoSync.PENDING -> if (result.deleting) "Deletion pending" else "Pending server confirmation"
    CryptoSync.FAILED -> if (result.deleting) "Deletion rejected" else "Change rejected"
}

@Composable
private fun CryptoRow(result: CryptoResult, onInspect: () -> Unit) {
    OutlinedCard(onClick = onInspect, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${result.direction.replaceFirstChar { it.uppercase() }} LKR ${CryptoReporting.money(result.signedMinor)}", style = MaterialTheme.typography.titleMedium)
            Text(result.resultDate)
            Text(status(result))
            result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Inspect result", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CryptoFormDialog(form: CryptoForm, viewModel: CryptoViewModel) {
    val editable = !form.busy && form.savedOperation == null
    Dialog(onDismissRequest = viewModel::closeForm) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (form.original == null) "Add realised result" else "Edit realised result", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("gain", "loss").forEach { direction ->
                        FilterChip(selected = form.direction == direction, enabled = editable,
                            onClick = { viewModel.updateForm(direction = direction) }, label = { Text(direction.replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedTextField(value = form.amount, onValueChange = { viewModel.updateForm(amount = it) },
                    label = { Text("Positive LKR amount") }, enabled = editable, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = form.date, onValueChange = { viewModel.updateForm(date = it) },
                    label = { Text("Actual result date (YYYY-MM-DD)") }, supportingText = { Text("Today in Asia/Colombo by default. Backdating is allowed.") },
                    enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = form.note, onValueChange = { viewModel.updateForm(note = it) },
                    label = { Text("Note (optional, max 500 characters)") }, enabled = editable, modifier = Modifier.fillMaxWidth())
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (form.savedOperation != null) Text("Retry uses the same result ID and saved details. Await server confirmation before recording it again.")
                Button(onClick = viewModel::save, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (form.busy) "Saving locally..." else if (form.savedOperation != null) "Retry Same Save" else "Save Result")
                }
                TextButton(onClick = viewModel::closeForm, enabled = !form.busy) { Text("Close Form") }
            }
        }
    }
}
