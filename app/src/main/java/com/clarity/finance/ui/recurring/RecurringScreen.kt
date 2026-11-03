package com.clarity.finance.ui.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.*
import com.clarity.finance.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(viewModel: RecurringViewModel, onBack: () -> Unit, onExpense: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.refreshDate(); onPauseOrDispose {} }
    Scaffold(topBar = { TopAppBar(title = { Text("Recurring Expenses") }, navigationIcon = {
        IconButton(onClick = { if (state.selectedId != null) viewModel.closeTemplate() else onBack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("recurring-list"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Templates are suggested obligations, not recorded spending. Nothing is paid or added to expenses until you review and confirm an actual payment.")
                    Text("Dates: Asia/Colombo • Today: ${state.today}")
                    if (!state.signedIn) Text("Sign in to view recurring expenses.")
                    if (state.templates.fromCache && state.templates.records != null) Text("Cached templates; may be incomplete or out of date.")
                    state.templates.error?.let { Text(it) }
                    state.recovery?.error?.let { Text(it) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.busy) Text("Saving proposal locally…")
                    if (state.unknown != null) {
                        Text("Local save outcome unknown. Keep the original proposal; retry uses the same identity.")
                        OutlinedButton(onClick = viewModel::retryLocalSave, enabled = !state.busy) { Text("Retry local save") }
                    }
                    state.local.values.forEach { Text("Saved ${it.kind.lowercase()} proposal awaiting server confirmation. Confirmed spending is unchanged.") }
                }
            }
            items(state.recovery?.attempts.orEmpty(), key = { it.operation.type + it.operation.recordId }) { attempt ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val op = attempt.operation
                        Text("Saved ${op.kind.lowercase()}: ${op.config?.name ?: op.confirmation?.templateName}")
                        op.confirmation?.let { Text("Scheduled ${it.dueDate}; actual LKR ${SavingsMath.money(it.actual.amountMinor)} on ${it.actual.expenseDate}. Not counted until confirmed.") }
                        Text(when {
                            op.operationId in state.recovery?.acknowledgedIds.orEmpty() -> "Server confirmed; completing local recovery cleanup."
                            attempt.rejected -> "Rejected proposal: ${attempt.error}. Confirmed spending is retained."
                            attempt.status == "PENDING" -> "Pending server confirmation. Confirmed spending is retained."
                            else -> "Outcome uncertain: ${attempt.error}. An earlier transaction may still complete. This proposal cannot be dismissed."
                        })
                        if (attempt.rejected) {
                            Text("Dismiss this rejected proposal, then review the latest template/occurrence to correct it. Existing confirmations remain inspectable.")
                            OutlinedButton(onClick = { viewModel.dismiss(attempt) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                        }
                    }
                }
            }
            if (state.selectedId == null) {
                item { Button(onClick = viewModel::newTemplate, enabled = state.canPrepare) { Text("Create recurring template") } }
                if (state.templates.records == null) item { Text("Templates unavailable or loading; no empty account is assumed.") }
                else {
                    if (state.templates.records.orEmpty().isEmpty()) item { Text("No recurring templates recorded. Add your own obligation; no scenario savings or spending is prefilled.") }
                    items(state.templates.records.orEmpty().take(state.templateLimit), key = { it.id }) { template ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(template.config.name, style = MaterialTheme.typography.titleMedium)
                                Text("Suggested LKR ${SavingsMath.money(template.config.suggestedMinor)} • ${ExpenseCategories.labels[template.config.categoryId]}")
                                Text(if (template.isCancelled) "Cancelled • confirmation history retained" else "Monthly • first due ${template.config.firstDueDate} • day ${template.config.anchorDay}, clamped to month end")
                                OutlinedButton(onClick = { viewModel.select(template) }) { Text("Inspect ${template.config.name}") }
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(template.config.name, style = MaterialTheme.typography.headlineSmall)
                            Text("Suggested LKR ${SavingsMath.money(template.config.suggestedMinor)}; this is not confirmed spending.")
                            Text("${ExpenseCategories.labels[template.config.categoryId]} • ${template.config.spendingType} • ${Recurrence.channels[template.config.paymentChannelId]}")
                            Text("Monthly day ${template.config.anchorDay}, clamped to month end. First due date: ${template.config.firstDueDate} (fixed).")
                            Text("Edits apply to all unconfirmed months, including missed months. Confirmed dates/expenses keep their original identities. Pending confirmations retain their original details and may be rejected if a template change commits first.")
                            if (template.isCancelled) Text("Cancelled. All unconfirmed occurrences, including already-due items, are stopped. Past expenses and confirmations remain.")
                            else {
                                OutlinedButton(onClick = viewModel::editTemplate, enabled = state.canPrepare && !state.blocked(template.id)) { Text("Edit template") }
                                OutlinedButton(onClick = viewModel::requestCancel, enabled = state.canPrepare && !state.blocked(template.id)) { Text("Cancel template") }
                            }
                            Text("Schedule/history window: ${state.windowStart} • up to 12 months. Other months are available with the page buttons; older missed items are not silently discarded.")
                            OutlinedButton(onClick = { viewModel.page(-1) }) { Text("Previous 12 months") }
                            OutlinedButton(onClick = { viewModel.page(1) }) { Text("Next 12 months") }
                            if (state.occurrences.fromCache && state.occurrences.records != null) Text("Cached confirmations; may be incomplete. Server reconciliation checks every proposal.")
                            state.occurrences.error?.let { Text(it) }
                            if (state.occurrences.records == null) Text("Confirmation history unavailable or loading. Schedule candidates below are not proof that an occurrence is unpaid; confirmation reconciles server history first.")
                            Text("Confirmations show the original actual payload. Open the actual expense for current corrected details or its deleted state. Deletion never makes a month confirmable again.")
                            if (state.slots.isEmpty()) Text(if (template.isCancelled) "No confirmed occurrences available in this window." else "No due occurrences in this window as of ${state.today}. Ignoring an item creates no expense.")
                        }
                    }
                    items(state.slots, key = { it.id }) { slot ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Scheduled: ${slot.dueDate}")
                                val confirmed = slot.confirmed
                                if (confirmed != null) {
                                    Text("Confirmed originally: LKR ${SavingsMath.money(confirmed.confirmation.actual.amountMinor)} on ${confirmed.confirmation.actual.expenseDate}")
                                    OutlinedButton(onClick = { onExpense(confirmed.confirmation.expenseId) }) { Text("Inspect actual expense") }
                                } else {
                                    Text(if (state.occurrences.records == null) "Scheduled candidate • confirmation state unavailable" else if (slot.overdue) "Overdue • not automatically recorded" else "Due • not automatically recorded")
                                    Button(onClick = { viewModel.confirm(slot) }, enabled = state.canPrepare && !state.blocked(slot.id)) { Text("Review ${slot.month} payment") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.form?.let { form ->
        Dialog(onDismissRequest = viewModel::closeForm) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val actual = form.kind == "CONFIRM"
                    Text(if (actual) "Confirm actual payment" else "Recurring template details", style = MaterialTheme.typography.titleLarge)
                    if (actual) Text("${form.name} • Scheduled due ${form.dueDate}. Review actual spending; suggested amounts are editable. This app does not make payments.")
                    else Text("Monthly only. Defaults apply to unconfirmed occurrences; no actual expense is created here.")
                    val enabled = !state.busy && state.unknown == null
                    @Composable fun field(label: String, value: String, decimal: Boolean = false, editable: Boolean = true, change: (String) -> Unit) {
                        OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            enabled = enabled && editable, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
                    }
                    if (!actual) field("Template name", form.name) { viewModel.update(form.copy(name = it)) }
                    field(if (actual) "Actual amount (LKR)" else "Suggested amount (LKR)", form.amount, true) { viewModel.update(form.copy(amount = it)) }
                    RecurringChoice("Category", form.categoryId, ExpenseCategories.labels, enabled) { viewModel.update(form.copy(categoryId = it)) }
                    RecurringChoice("Spending type", form.spendingType, mapOf("committed" to "Committed", "discretionary" to "Discretionary"), enabled) { viewModel.update(form.copy(spendingType = it)) }
                    RecurringChoice("Payment channel", form.paymentChannelId, Recurrence.channels, enabled) { viewModel.update(form.copy(paymentChannelId = it)) }
                    if (actual) {
                        field("Actual payment date (YYYY-MM-DD)", form.actualDate) { viewModel.update(form.copy(actualDate = it)) }
                        OutlinedTextField(form.note, { viewModel.update(form.copy(note = it)) }, label = { Text("Optional note (max 500)") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
                    } else {
                        field("First due date (YYYY-MM-DD)", form.firstDueDate, editable = form.kind == "CREATE") { viewModel.update(form.copy(firstDueDate = it)) }
                        field("Monthly day (1–31)", form.anchorDay) { viewModel.update(form.copy(anchorDay = it)) }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.unknown != null) Text("Local save outcome unknown. Close this form, then use Retry local save. The original proposal identity and details are retained.")
                    Button(onClick = viewModel::save, enabled = state.canPrepare) { Text(if (actual) "Confirm payment proposal" else "Save template proposal") }
                    OutlinedButton(onClick = viewModel::closeForm, enabled = !state.busy) { Text("Close form") }
                }
            }
        }
    }
    state.cancel?.let { template ->
        AlertDialog(onDismissRequest = viewModel::keepTemplate, title = { Text("Cancel recurring template?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${template.config.name}: stop all unconfirmed occurrences, including overdue ones. Recorded expenses and confirmation history remain. Pending confirmations keep their original payload; whichever operation commits first determines the result.")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.unknown != null) Text("Local save outcome unknown. Keep this proposal; close the dialog and retry the same local save.")
            } },
            confirmButton = { TextButton(onClick = viewModel::cancelTemplate, enabled = state.canPrepare) { Text("Confirm cancellation") } },
            dismissButton = { TextButton(onClick = viewModel::keepTemplate, enabled = !state.busy) { Text("Keep template") } })
    }
}

@Composable
private fun RecurringChoice(label: String, selected: String, options: Map<String, String>, enabled: Boolean, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label: ${options[selected] ?: "Choose"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; change(id) }) }
        }
    }
}
