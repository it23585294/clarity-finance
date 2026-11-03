package com.clarity.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.*
import com.clarity.finance.viewmodel.CatalogueViewModel

val LocalCatalogue = staticCompositionLocalOf { CatalogueSnapshot() }

@Composable
fun CatalogueStatus() {
    val catalogue = LocalCatalogue.current
    if (catalogue.entries == null) Text("Catalogue unavailable. Connect to load confirmed choices and labels.")
    else if (catalogue.fromCache) Text("Cached catalogue; choices and labels may be stale. New proposals await server checks.")
    catalogue.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
fun CatalogueScreen(viewModel: CatalogueViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Sources, categories & recovery", style = MaterialTheme.typography.headlineSmall)
        Text("Renaming and archiving preserve history and totals. Archived entries cannot be chosen for new transactions.")
        if (state.catalogue.fromCache) Text("Cached catalogue. Changes await server checks.")
        state.catalogue.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.recovery.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        for (kind in listOf("category", "source")) {
            Button(onClick = { viewModel.add(kind) }, enabled = !state.busy) { Text("Add $kind") }
            state.catalogue.entries.orEmpty().filter { it.kind == kind }.forEach { entry ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${entry.name}${if (entry.archived) " (archived)" else ""}")
                    Text("Default: ${entry.defaultValue}")
                    if (!entry.archived) {
                        TextButton(onClick = { viewModel.edit(entry) }, enabled = !state.busy) { Text("Rename / edit ${entry.name}") }
                        TextButton(onClick = { viewModel.askArchive(entry) }, enabled = !state.busy) { Text("Archive ${entry.name}") }
                    }
                } }
            }
        }
        Text("Saved transaction, project and catalogue changes", style = MaterialTheme.typography.titleLarge)
        Text("Crypto, savings and recurring proposals are shown in their respective screens.")
        Text("Local proposals do not change confirmed totals. A timeout or access error leaves the outcome unknown. Reconciliation runs automatically while the app is running.")
        if (state.recovery.attempts.isEmpty() && state.recovery.error == null) Text("No saved transaction, project or catalogue changes.")
        state.recovery.attempts.forEach { attempt ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${attempt.operation.group} · ${attempt.operation.kind} · ${attempt.status}")
                Text(attempt.details)
                attempt.error?.let { Text(it) }
                if (attempt.rejected) TextButton(onClick = { viewModel.dismiss(attempt) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                else TextButton(onClick = { viewModel.resolve(attempt) }, enabled = !state.busy) { Text("Reconcile or withdraw safely (online)") }
            } }
        }
    }
    state.form?.let { form -> AlertDialog(onDismissRequest = viewModel::close,
        title = { Text("${form.entry.kind.replaceFirstChar { it.uppercase() }} details") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(form.name, { viewModel.change(name = it) }, label = { Text("Name") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            Text("Default ${if (form.entry.kind == "category") "spending type" else "currency"}; each transaction can be corrected.")
            val choices = if (form.entry.kind == "category") listOf("committed", "discretionary") else listOf("LKR", "USD")
            choices.forEach { value -> FilterChip(selected = form.defaultValue == value, onClick = { viewModel.change(defaultValue = value) }, label = { Text(value) }, enabled = !state.busy) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = viewModel::save, enabled = !state.busy) { Text("Save proposal") } },
        dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("Cancel") } }) }
    state.archive?.let { entry -> AlertDialog(onDismissRequest = viewModel::close, title = { Text("Archive ${entry.name}?") },
        text = { Text("History and totals remain. This choice will no longer be available for new transactions after confirmation.") },
        confirmButton = { TextButton(onClick = viewModel::archive, enabled = !state.busy) { Text("Archive") } },
        dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("Keep active") } }) }
}
