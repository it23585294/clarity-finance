package com.clarity.finance.ui.crypto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clarity.finance.data.model.CryptoReporting
import com.clarity.finance.data.model.CryptoResult
import com.clarity.finance.data.model.CryptoSync
import com.clarity.finance.ui.pilot.*
import com.clarity.finance.viewmodel.CryptoForm
import com.clarity.finance.viewmodel.CryptoViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CryptoScreen(viewModel: CryptoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var information by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) { viewModel.refreshReportingDate(); onPauseOrDispose {} }
    Scaffold(topBar = {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageHeader("Crypto Results", onBack) }
            InformationAction("About realised crypto results") { information = true }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.signedIn) Text("Sign in to view your realised crypto results.")
            else {
                Text("Realised gains and losses · Separate from ordinary cash and savings", style = MaterialTheme.typography.bodySmall)
                PeriodSelector(state.month, state.canPreviousMonth && !state.busy, state.canNextMonth && !state.busy,
                    { viewModel.changeMonth(-1) }, { viewModel.changeMonth(1) })
                Button(onClick = viewModel::add, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Add result") }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Month net: ${if (state.loading) "Loading" else total(state.monthNet)}", style = MaterialTheme.typography.headlineSmall)
                        DetailValue("Year-to-date net", if (state.loading) "Loading" else total(state.ytdNet))
                        Text("Reporting year ${state.month.take(4)}: ${PresentationFormat.date("${state.month.take(4)}-01-01")} through ${PresentationFormat.date(state.cutoff)}", style = MaterialTheme.typography.bodySmall)
                        Text("Asia/Colombo · Saved proposals do not change recorded totals.", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.changeYear(-1) }, enabled = state.canPreviousYear && !state.busy) { Text("Previous year") }
                            OutlinedButton(onClick = { viewModel.changeYear(1) }, enabled = state.canNextYear && !state.busy) { Text("Next year") }
                        }
                    }
                }
                if (state.cached) Text("Cached view: totals may be incomplete or out of date; not a fresh server confirmation.", style = MaterialTheme.typography.bodySmall)
                state.message?.let { StatusPanel(it) }
                if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading results and saved attempts...") }
                state.error?.let {
                    StatusPanel(it, isError = true)
                    OutlinedButton(onClick = viewModel::retryLoad, enabled = !state.busy) { Text("Retry loading") }
                }
                if (state.unsettled.isNotEmpty()) {
                    Text("Saved crypto changes · All years", style = MaterialTheme.typography.titleMedium)
                    state.unsettled.forEach { result ->
                        CryptoRow(result, onInspect = { viewModel.inspect(result) })
                        if (result.sync == CryptoSync.FAILED) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.retry(result) }, enabled = !state.busy) { Text("Retry saved change") }
                                OutlinedButton(onClick = { viewModel.dismissRejected(result) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                            }
                            Text("Dismiss removes only the rejected proposal. Then reopen the recorded result to correct it.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Saved locally. Synchronises while the app is running. A timeout does not cancel the write.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { viewModel.resolve(result) }, enabled = !state.busy) { Text("Check outcome or withdraw safely") }
                        }
                    }
                }
                Text("Month history", style = MaterialTheme.typography.titleLarge)
                Text("Recorded results · Cached records are labelled", style = MaterialTheme.typography.bodySmall)
                if (state.history.isEmpty() && !state.loading) Text(if (state.cached)
                    "No cached results available for this month. Connect to load server history."
                    else "No realised results recorded for this month.")
                // Independent of aggregate availability: valid rows remain inspectable on overflow.
                state.history.forEach { result -> CryptoRow(result, onInspect = { viewModel.inspect(result) }) }
            }
        }
    }
    state.form?.let { form -> CryptoFormDialog(form, viewModel) }
    state.selected?.let { result ->
        AlertDialog(onDismissRequest = viewModel::closeDetails, containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Realised result") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${result.direction.replaceFirstChar { it.uppercase() }}: LKR ${PresentationFormat.amount(CryptoReporting.money(result.signedMinor))}", style = MaterialTheme.typography.titleLarge)
                DetailValue("Result date", PresentationFormat.date(result.resultDate))
                DetailValue("Note", result.note.ifEmpty { "None" })
                StatusPanel(status(result), isError = result.sync == CryptoSync.FAILED)
                result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (result.sync in setOf(CryptoSync.CONFIRMED, CryptoSync.CACHED) && !state.selectedHasAttempt) {
                    OutlinedButton(onClick = { viewModel.edit(result) }, enabled = !state.busy) { Text("Edit result") }
                    TextButton(onClick = { viewModel.requestDelete(result) }, enabled = !state.busy) { Text("Delete result", color = MaterialTheme.colorScheme.error) }
                } else Text("Resolve the saved proposal first. Recorded contributions stay in totals until confirmation.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = viewModel::closeDetails) { Text("Close") } })
    }
    state.deleteTarget?.let {
        AlertDialog(onDismissRequest = viewModel::dismissDelete, containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete realised result?") },
            text = { Text("This removes only this crypto result and its contribution to crypto totals after confirmation.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, enabled = !state.busy) { Text(if (state.busy) "Saving deletion..." else "Confirm deletion") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete, enabled = !state.busy) { Text("Keep result") } })
    }
    if (information) InformationDialog("Realised crypto results",
        "Record realised profit or loss in LKR, not gross sale proceeds, capital deposits or withdrawals, or unrealised price changes. Crypto stays separate from ordinary income, expenses, cash surplus, pending payments and goal savings. YTD is the current year through today, or a selected past year through December 31.") { information = false }
}

/** VM supplies canonical signed decimal text or its explicit unavailable sentinel. */
private fun total(value: String): String = if (value == "Unavailable") value else "LKR ${PresentationFormat.amount(value)}"

private fun status(result: CryptoResult): String = when (result.sync) {
    CryptoSync.CONFIRMED -> "Confirmed by server"
    CryptoSync.CACHED -> "Cached; awaiting fresh server version"
    CryptoSync.PENDING -> if (result.deleting) "Deletion pending" else "Pending server confirmation"
    CryptoSync.FAILED -> if (result.deleting) "Deletion rejected" else "Change rejected"
}

@Composable
private fun CryptoRow(result: CryptoResult, onInspect: () -> Unit) {
    OutlinedCard(onClick = onInspect, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${result.direction.replaceFirstChar { it.uppercase() }} LKR ${PresentationFormat.amount(CryptoReporting.money(result.signedMinor))}", style = MaterialTheme.typography.titleMedium)
            Text(PresentationFormat.date(result.resultDate), style = MaterialTheme.typography.bodyMedium)
            if (result.note.isNotBlank()) Text(result.note)
            Text(status(result), style = MaterialTheme.typography.bodySmall)
            result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Inspect result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CryptoFormDialog(form: CryptoForm, viewModel: CryptoViewModel) {
    val editable = !form.busy && form.savedOperation == null
    ReviewFormDialog(if (form.original == null) "Add realised result" else "Edit realised result", viewModel::closeForm, actions = {
        Button(onClick = viewModel::save, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (form.busy) "Saving locally..." else if (form.savedOperation != null) "Retry same save" else "Save result")
        }
        TextButton(onClick = viewModel::closeForm, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) { Text("Close form") }
    }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("gain", "loss").forEach { direction ->
                ChoiceOption(direction.replaceFirstChar { it.uppercase() }, form.direction == direction, editable) { viewModel.updateForm(direction = direction) }
            }
        }
        OutlinedTextField(value = form.amount, onValueChange = { viewModel.updateForm(amount = it) },
            label = { Text("Amount (LKR)") }, supportingText = { Text("Positive amount; Gain or Loss determines the sign.") }, enabled = editable, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.date, onValueChange = { viewModel.updateForm(date = it) },
            label = { Text("Result date") }, supportingText = { Text("YYYY-MM-DD · Asia/Colombo. Backdating is allowed.") },
            enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.note, onValueChange = { viewModel.updateForm(note = it) },
            label = { Text("Note (optional)") }, supportingText = { Text("Up to 500 characters") }, enabled = editable, modifier = Modifier.fillMaxWidth())
        form.error?.let { StatusPanel(it, isError = true) }
        if (form.savedOperation != null) StatusPanel("Retry uses the same result ID and saved details. Await server confirmation before recording it again.")
    }
}
