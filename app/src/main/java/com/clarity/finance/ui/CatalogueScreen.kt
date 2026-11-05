package com.clarity.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.*
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogueScreen(viewModel: CatalogueViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("category") }
    val catalogueScroll = rememberScrollState()
    val recoveryScroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader("Categories & Sources", onBack)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("category" to "Categories", "source" to "Sources", "recovery" to "Saved changes").forEach { (id, label) ->
                FilterChip(section == id, onClick = { section = id }, label = { Text(label) })
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(if (section == "recovery") recoveryScroll else catalogueScroll)
            .imePadding().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { StatusPanel(it, isError = true) }
            if (section != "recovery") {
                Text("Renaming keeps history. Archived choices are unavailable for new records.", style = MaterialTheme.typography.bodySmall)
                if (state.catalogue.fromCache) Text("Saved catalogue · Choices may be out of date", style = MaterialTheme.typography.bodySmall)
                state.catalogue.error?.let { StatusPanel(it, isError = true) }
                if (state.catalogue.entries == null) Text("Catalogue unavailable. Connect to load confirmed choices and labels.")
                Button(onClick = { viewModel.add(section) }, enabled = !state.busy) { Text("Add $section") }
                state.catalogue.entries.orEmpty().filter { it.kind == section }.forEach { entry ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${entry.name}${if (entry.archived) " (archived)" else ""}", style = MaterialTheme.typography.titleMedium)
                        Text("Default: ${entry.defaultValue.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall)
                        if (!entry.archived) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.edit(entry) }, enabled = !state.busy) { Text("Rename / edit ${entry.name}") }
                            TextButton(onClick = { viewModel.askArchive(entry) }, enabled = !state.busy) { Text("Archive ${entry.name}", color = MaterialTheme.colorScheme.error) }
                        }
                    } }
                }
            } else {
                Text("Saved transaction, project and catalogue changes", style = MaterialTheme.typography.titleMedium)
                Text("Crypto, savings and recurring proposals remain in their own screens.", style = MaterialTheme.typography.bodySmall)
                Text("Recorded totals stay unchanged until confirmation. An unknown outcome must be checked before replacing a proposal.", style = MaterialTheme.typography.bodySmall)
                state.recovery.error?.let { StatusPanel(it, isError = true) }
                if (state.recovery.attempts.isEmpty() && state.recovery.error == null) Text("No saved transaction, project or catalogue changes.")
                state.recovery.attempts.forEach { attempt ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(financialChangeTitle(attempt.operation), style = MaterialTheme.typography.titleMedium)
                        StatusPanel(financialChangeStatus(attempt), isError = attempt.rejected)
                        financialChangeDetails(attempt.operation, state.catalogue).forEach { (label, value) -> DetailValue(label, value) }
                        attempt.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (attempt.rejected) TextButton(onClick = { viewModel.dismiss(attempt) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                        else {
                            Text("Online: check whether it was accepted, or stop the original operation safely before replacement.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { viewModel.resolve(attempt) }, enabled = !state.busy) { Text("Check outcome or withdraw safely") }
                        }
                    } }
                }
            }
        }
    }
    state.form?.let { form -> AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = viewModel::close,
        title = { Text("${form.entry.kind.replaceFirstChar { it.uppercase() }} details") },
        text = { Column(Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(form.name, { viewModel.change(name = it) }, label = { Text("Name") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            Text("Default ${if (form.entry.kind == "category") "spending type" else "currency"}; each transaction can be corrected.")
            val choices = if (form.entry.kind == "category") listOf("committed", "discretionary") else listOf("LKR", "USD")
            choices.forEach { value -> FilterChip(selected = form.defaultValue == value, onClick = { viewModel.change(defaultValue = value) }, label = { Text(value.replaceFirstChar { it.uppercase() }) }, enabled = !state.busy) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = viewModel::save, enabled = !state.busy) { Text("Save proposal") } },
        dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("Cancel") } }) }
    state.archive?.let { entry -> AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = viewModel::close, title = { Text("Archive ${entry.name}?") },
        text = { Text("History and totals remain. This choice will no longer be available for new transactions after confirmation.") },
        confirmButton = { TextButton(onClick = viewModel::archive, enabled = !state.busy) { Text("Archive") } },
        dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("Keep active") } }) }
}
