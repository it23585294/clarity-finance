package com.clarity.finance.ui.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.*
import com.clarity.finance.ui.pilot.*
import com.clarity.finance.viewmodel.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringScreen(viewModel: RecurringViewModel, onBack: () -> Unit, onExpense: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var information by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) { viewModel.refreshDate(); onPauseOrDispose {} }
    Scaffold(topBar = {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageHeader("Recurring Expenses", { if (state.selectedId != null) viewModel.closeTemplate() else onBack() }) }
            InformationAction("About recurring templates and history") { information = true }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("recurring-list"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Templates suggest payments. Only confirmed actual payments count as expenses.", style = MaterialTheme.typography.bodySmall)
                    Text("Today: ${PresentationFormat.date(state.today)} · Asia/Colombo", style = MaterialTheme.typography.bodySmall)
                    if (!state.signedIn) Text("Sign in to view recurring expenses.")
                    if (state.templates.fromCache && state.templates.records != null) Text("Cached templates; may be incomplete or out of date.", style = MaterialTheme.typography.bodySmall)
                    state.templates.error?.let { StatusPanel(it, isError = true) }
                    state.recovery?.error?.let { StatusPanel(it, isError = true) }
                    state.error?.let { StatusPanel(it, isError = true) }
                    if (state.busy) Text("Saving proposal locally...", style = MaterialTheme.typography.bodySmall)
                    if (state.unknown != null) {
                        StatusPanel("Local save outcome unknown. Keep the original proposal; retry uses the same identity.")
                        OutlinedButton(onClick = viewModel::retryLocalSave, enabled = !state.busy) { Text("Retry local save") }
                    }
                    state.local.values.forEach { Text("Saved ${it.kind.lowercase()} proposal awaiting server confirmation. Confirmed spending is unchanged.") }
                }
            }
            items(state.recovery?.attempts.orEmpty(), key = { it.operation.type + it.operation.recordId }) { attempt ->
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val op = attempt.operation
                        val acknowledged = op.operationId in state.recovery?.acknowledgedIds.orEmpty()
                        Text("${if (op.kind == "CONFIRM") "Saved payment" else "Saved template change"}: ${op.config?.name ?: op.confirmation?.templateName}", style = MaterialTheme.typography.titleMedium)
                        op.confirmation?.let { Text("Scheduled ${PresentationFormat.date(it.dueDate)}; actual LKR ${PresentationFormat.minor(it.actual.amountMinor)} on ${PresentationFormat.date(it.actual.expenseDate)}. Not counted until confirmed.") }
                        StatusPanel(when {
                            acknowledged -> "Server confirmed; completing local recovery cleanup."
                            attempt.rejected -> "Rejected proposal: ${attempt.error}. Confirmed spending is retained."
                            attempt.status == "PENDING" -> "Pending server confirmation. Confirmed spending is retained."
                            else -> "Outcome uncertain: ${attempt.error}. An earlier transaction may still complete. This proposal cannot be dismissed."
                        }, isError = !acknowledged && attempt.rejected)
                        if (attempt.rejected) {
                            Text("Dismiss to review the latest details and correct this rejected proposal. Existing confirmations remain.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { viewModel.dismiss(attempt) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                        }
                    }
                }
            }
            if (state.selectedId == null) {
                item { Button(onClick = viewModel::newTemplate, enabled = state.canPrepare, modifier = Modifier.fillMaxWidth()) { Text("Add template") } }
                if (state.templates.records == null) item { Text("Templates unavailable or loading; no empty account is assumed.") }
                else {
                    if (state.templates.records.orEmpty().isEmpty()) item { Text("No recurring templates recorded. Add your own obligation; no scenario savings or spending is prefilled.") }
                    items(state.templates.records.orEmpty().take(state.templateLimit), key = { it.id }) { template ->
                        OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(template.config.name, style = MaterialTheme.typography.titleLarge)
                                DetailValue("Suggested amount", "LKR ${PresentationFormat.minor(template.config.suggestedMinor)}")
                                Text(com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("category", template.config.categoryId))
                                Text(if (template.isCancelled) "Cancelled · Confirmation history retained" else
                                    "Monthly · First due ${PresentationFormat.date(template.config.firstDueDate)} · Day ${template.config.anchorDay}, clamped to month end", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { viewModel.select(template) }, modifier = Modifier.semantics { contentDescription = "Inspect ${template.config.name}" }) { Text("View template") }
                            }
                        }
                    }
                    if (state.templates.records.orEmpty().size > state.templateLimit) item {
                        Text("More templates exist beyond this page.")
                        OutlinedButton(onClick = viewModel::loadMore) { Text("Load more templates") }
                    }
                }
            } else {
                val template = state.selected
                if (template == null) item { Text("Selected template unavailable. Return to templates or reconnect; no data is assumed absent.") }
                else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(template.config.name, style = MaterialTheme.typography.titleLarge)
                                    DetailValue("Suggested amount · Not recorded spending", "LKR ${PresentationFormat.minor(template.config.suggestedMinor)}")
                                    Text(com.clarity.finance.ui.LocalCatalogue.current.referenceLabel("category", template.config.categoryId))
                                    Text("${template.config.spendingType.replaceFirstChar { it.uppercase() }} · ${Recurrence.channels[template.config.paymentChannelId] ?: "Payment method unavailable"}")
                                    Text("Monthly day ${template.config.anchorDay}, clamped to month end. First due ${PresentationFormat.date(template.config.firstDueDate)} is fixed.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (template.isCancelled) Text("Cancelled. All unconfirmed occurrences, including already-due items, are stopped. Past expenses and confirmations remain.")
                            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = viewModel::editTemplate, enabled = state.canPrepare && !state.blocked(template.id)) { Text("Edit template") }
                                TextButton(onClick = viewModel::requestCancel, enabled = state.canPrepare && !state.blocked(template.id)) { Text("Cancel template", color = MaterialTheme.colorScheme.error) }
                            }
                            Text("Payments and confirmation history", style = MaterialTheme.typography.titleLarge)
                            Text("From ${state.windowStart?.let(PresentationFormat::month) ?: "Unavailable"} · 12-month window", style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.page(-1) }) { Text("Previous 12 months") }
                                OutlinedButton(onClick = { viewModel.page(1) }) { Text("Next 12 months") }
                            }
                            if (state.occurrences.fromCache && state.occurrences.records != null) Text("Cached confirmations; may be incomplete. Server reconciliation checks every proposal.", style = MaterialTheme.typography.bodySmall)
                            state.occurrences.error?.let { StatusPanel(it, isError = true) }
                            if (state.occurrences.records == null) StatusPanel("Confirmation history unavailable or loading. Candidates are not proof of an unpaid occurrence; confirmation checks server history first.")
                            Text("A confirmation stays used after its expense is corrected or deleted. Open the actual expense for current details.", style = MaterialTheme.typography.bodySmall)
                            if (state.slots.isEmpty()) Text(if (template.isCancelled) "No confirmed occurrences available in this window." else "No due occurrences in this window as of ${PresentationFormat.date(state.today)}. Ignoring an item creates no expense.")
                        }
                    }
                    items(state.slots, key = { it.id }) { slot ->
                        OutlinedCard(Modifier.fillMaxWidth().testTag("recurring-slot-${slot.month}"), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Scheduled: ${PresentationFormat.date(slot.dueDate)}", style = MaterialTheme.typography.titleMedium)
                                val confirmed = slot.confirmed
                                if (confirmed != null) {
                                    Text("Confirmed originally: LKR ${PresentationFormat.minor(confirmed.confirmation.actual.amountMinor)} on ${PresentationFormat.date(confirmed.confirmation.actual.expenseDate)}")
                                    OutlinedButton(onClick = { onExpense(confirmed.confirmation.expenseId) }) { Text("Inspect actual expense") }
                                } else {
                                    Text(if (state.occurrences.records == null) "Scheduled candidate · Confirmation state unavailable" else if (slot.overdue) "Overdue · Not automatically recorded" else "Due · Not automatically recorded")
                                    Button(onClick = { viewModel.confirm(slot) }, enabled = state.canPrepare && !state.blocked(slot.id)) { Text("Review ${PresentationFormat.month(slot.month)} payment") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.form?.let { form ->
        val actual = form.kind == "CONFIRM"
        val enabled = !state.busy && state.unknown == null
        val catalogue = com.clarity.finance.ui.LocalCatalogue.current
        ReviewFormDialog(if (actual) "Review actual payment" else "Recurring template details", viewModel::closeForm, actions = {
            Button(onClick = viewModel::save, enabled = state.canPrepare && catalogue.accepts("category", form.categoryId, if (form.kind == "EDIT") state.selected?.config?.categoryId else null),
                modifier = Modifier.fillMaxWidth()) { Text(if (actual) "Confirm payment proposal" else "Save template proposal") }
            TextButton(onClick = viewModel::closeForm, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Close form") }
        }) {
            if (actual) {
                Text(form.name, style = MaterialTheme.typography.titleMedium)
                Text("Scheduled due ${form.dueDate?.let(PresentationFormat::date) ?: "Unavailable"}")
                Text("Review what you actually paid. This records an expense; Clarity does not make a payment.", style = MaterialTheme.typography.bodySmall)
            } else Text("Monthly suggestion only. Saving this template creates no actual expense.", style = MaterialTheme.typography.bodySmall)
            @Composable fun field(label: String, value: String, decimal: Boolean = false, editable: Boolean = true, hint: String? = null, change: (String) -> Unit) {
                OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = hint?.let { { Text(it) } }, enabled = enabled && editable,
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
            }
            if (!actual) field("Template name", form.name) { viewModel.update(form.copy(name = it)) }
            field(if (actual) "Actual (LKR)" else "Suggested (LKR)", form.amount, true) { viewModel.update(form.copy(amount = it)) }
            com.clarity.finance.ui.CatalogueStatus()
            SelectionField("Category", catalogue.referenceLabel("category", form.categoryId),
                catalogue.choices("category", if (form.kind == "EDIT") form.categoryId else null).map { it.id to catalogue.referenceLabel("category", it.id) }, enabled) {
                viewModel.update(form.copy(categoryId = it, spendingType = catalogue.defaultValue("category", it) ?: form.spendingType))
            }
            SelectionField("Spending type", form.spendingType.replaceFirstChar { it.uppercase() }, listOf("committed" to "Committed", "discretionary" to "Discretionary"), enabled) { viewModel.update(form.copy(spendingType = it)) }
            SelectionField("Payment method", Recurrence.channels[form.paymentChannelId] ?: "Unavailable (${form.paymentChannelId})", Recurrence.channels.toList(), enabled) { viewModel.update(form.copy(paymentChannelId = it)) }
            if (actual) {
                field("Payment date", form.actualDate, hint = "YYYY-MM-DD · Asia/Colombo") { viewModel.update(form.copy(actualDate = it)) }
                OutlinedTextField(form.note, { viewModel.update(form.copy(note = it)) }, label = { Text("Note (optional)") }, supportingText = { Text("Up to 500 characters") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
            } else {
                field("First due date", form.firstDueDate, editable = form.kind == "CREATE", hint = "YYYY-MM-DD · Fixed after creation") { viewModel.update(form.copy(firstDueDate = it)) }
                field("Monthly day", form.anchorDay, hint = "1–31 · Clamped to month end") { viewModel.update(form.copy(anchorDay = it)) }
            }
            state.error?.let { StatusPanel(it, isError = true) }
            if (state.unknown != null) StatusPanel("Local save outcome unknown. Close this form, then use Retry local save. The original proposal identity and details are retained.")
        }
    }
    state.cancel?.let { template ->
        AlertDialog(onDismissRequest = viewModel::keepTemplate, containerColor = MaterialTheme.colorScheme.surface, title = { Text("Cancel recurring template?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${template.config.name}: stop all unconfirmed occurrences, including overdue ones. Recorded expenses and confirmation history remain. Pending confirmations keep their original payload; whichever operation commits first determines the result.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.unknown != null) Text("Local save outcome unknown. Keep this proposal; close the dialog and retry the same local save.")
            } }, confirmButton = { TextButton(onClick = viewModel::cancelTemplate, enabled = state.canPrepare) { Text("Confirm cancellation") } },
            dismissButton = { TextButton(onClick = viewModel::keepTemplate, enabled = !state.busy) { Text("Keep template") } })
    }
    if (information) InformationDialog("Templates and actual payments",
        "Templates never create spending automatically. Review each due or overdue occurrence and confirm what you actually paid. Monthly dates keep the original anchor and clamp to month end. Edits apply to unconfirmed months, including missed ones. Confirmed history keeps its original identity and payload. Cancelling stops all unconfirmed occurrences, including already-due items; recorded expenses remain. Page in 12-month windows to inspect older months. Pending confirmations retain their saved details and may be rejected if an edit or cancellation commits first.") { information = false }
}
