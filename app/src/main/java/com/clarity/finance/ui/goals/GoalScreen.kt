package com.clarity.finance.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.SavingsMath
import com.clarity.finance.ui.pilot.*
import com.clarity.finance.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun GoalCard(viewModel: GoalCardViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Savings Goal", style = MaterialTheme.typography.titleMedium)
            val goal = state.snapshot.goal
            when {
                goal != null -> { Text(goal.config.name); Text("Saved: LKR ${PresentationFormat.minor(goal.savedMinor)}", style = MaterialTheme.typography.titleLarge) }
                state.snapshot.known -> Text("No savings goal recorded.")
                else -> Text("Savings goal unavailable; no zero balance is assumed.")
            }
            Text("Explicitly set aside · Separate from monthly surplus", style = MaterialTheme.typography.bodySmall)
            GoalStatus(state)
            OutlinedButton(onClick = onOpen) { Text("Open Savings Goal") }
        }
    }
}

@Composable
private fun GoalStatus(state: GoalUiState) {
    if (state.snapshot.fromCache && (state.snapshot.known || state.snapshot.goal != null)) Text("Cached savings; may be incomplete or out of date.", style = MaterialTheme.typography.bodySmall)
    state.snapshot.error?.let { StatusPanel(it, isError = true) }
    state.snapshot.recoveryError?.let { StatusPanel(it, isError = true) }
    state.error?.let { StatusPanel(it, isError = true) }
    if (state.busy) Text("Saving proposal locally...", style = MaterialTheme.typography.bodySmall)
    if (state.localPending != null) StatusPanel("Saved proposal awaiting server confirmation. Confirmed savings are retained.")
    if (state.unknown != null) StatusPanel("Local save outcome unknown. Keep the original proposal; retry checks the same identity.")
    state.snapshot.attempts.forEach {
        val acknowledged = state.snapshot.acknowledgedOperationId == it.operation.operationId
        StatusPanel(when {
            acknowledged -> "Server confirmed proposal; completing local recovery cleanup."
            it.isRejected -> "Rejected proposal: ${it.error ?: "Conflict or validation failure"}. Confirmed savings are retained."
            it.isUncertain -> "Proposal outcome uncertain: ${it.error ?: "Reconciliation required"}. Retained until server reconciliation; it cannot be dismissed."
            else -> "Saved proposal awaiting server confirmation. Confirmed savings are retained."
        }, isError = !acknowledged && it.isRejected)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalScreen(viewModel: GoalViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSchedule by remember { mutableStateOf(false) }
    var information by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.signedIn, state.snapshot.goal?.config?.startDate) { showSchedule = false }
    LifecycleResumeEffect(viewModel) { viewModel.refreshDate(); onPauseOrDispose {} }
    Scaffold(topBar = {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageHeader("Savings Goal", onBack) }
            InformationAction("About recorded savings") { information = true }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("goal-history"), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Money you record as set aside. No automatic transfers from surplus.", style = MaterialTheme.typography.bodySmall)
                    GoalStatus(state)
                    if (!state.signedIn) Text("Sign in to view your goal.")
                    state.snapshot.attempts.forEach { attempt ->
                        val proposed = attempt.operation
                        proposed.goal?.let { Text("Proposed goal: ${it.name}; target LKR ${PresentationFormat.minor(it.targetMinor)}; opening LKR ${PresentationFormat.minor(it.openingMinor)}; deadline ${PresentationFormat.date(it.deadline)}.") }
                        proposed.entry?.let {
                            Text("Proposed ${if (it.deleted) "deletion of " else ""}${it.kind}: LKR ${PresentationFormat.minor(it.amountMinor)} on ${PresentationFormat.date(it.entryDate)}.")
                            if (it.note.isNotBlank()) Text("Proposed note: ${it.note}")
                        }
                        if (attempt.isRejected) {
                            if (attempt.canReviseDeadline) OutlinedButton(onClick = { viewModel.reviseDeadline(attempt) }, enabled = !state.busy) { Text("Revise deadline") }
                            Text("Dismiss to review and correct the latest details. Retry keeps the original proposal.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { viewModel.retry(attempt) }, enabled = !state.busy) { Text("Retry original proposal") }
                            OutlinedButton(onClick = { viewModel.dismiss(attempt) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                        }
                    }
                    if (state.unknown != null) OutlinedButton(onClick = viewModel::retryUnknown, enabled = !state.busy) { Text("Retry local save") }
                }
            }
            val goal = state.snapshot.goal
            if (goal == null) {
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.snapshot.known) {
                        Text("No savings goal recorded. Confirm your own target and opening allocation.")
                        Button(onClick = viewModel::openGoal, enabled = state.canChange) { Text("Create goal") }
                    } else if (state.error == null && state.snapshot.error == null) CircularProgressIndicator()
                } }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(goal.config.name, style = MaterialTheme.typography.titleLarge)
                                Text("Saved: LKR ${PresentationFormat.minor(goal.savedMinor)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    DetailValue("Target", "LKR ${PresentationFormat.minor(goal.config.targetMinor)}")
                                    state.progress?.let { DetailValue("Remaining", "LKR ${PresentationFormat.minor(it.remainingMinor)}") }
                                }
                                state.progress?.let { progress ->
                                    Text("Progress: ${progress.percent}%")
                                    LinearProgressIndicator(progress = { progress.barFraction }, modifier = Modifier.fillMaxWidth())
                                    if (progress.overfundedMinor > 0) Text("Overfunded: LKR ${PresentationFormat.minor(progress.overfundedMinor)}")
                                    when {
                                        progress.reached -> Text("Goal reached. Required contribution: LKR 0.00")
                                        progress.overdue -> Text("Overdue: no remaining scheduled opportunities. Revise the deadline.")
                                        else -> DetailValue("Required per opportunity", "LKR ${PresentationFormat.minor(progress.requiredMinor!!)}")
                                    }
                                    Text("Remaining opportunities (including today): ${progress.opportunities}", style = MaterialTheme.typography.bodySmall)
                                    DetailValue("Next scheduled date", progress.nextDate?.let(PresentationFormat::date) ?: "None")
                                }
                                DetailValue("Deadline", PresentationFormat.date(goal.config.deadline))
                                Text("Started ${PresentationFormat.date(goal.config.startDate)} · Asia/Colombo", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(onClick = { viewModel.openEntry() }, enabled = state.canChange, modifier = Modifier.fillMaxWidth()) { Text("Record allocation") }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = viewModel::openGoal, enabled = state.canChange) { Text("Edit goal") }
                            if (state.progress != null) OutlinedButton(onClick = { showSchedule = true }) { Text("Inspect contribution schedule") }
                            TextButton(onClick = { scope.launch { list.animateScrollToItem(minOf(2, (list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))) } }) { Text("Allocation history") }
                        }
                        Text("Allocation history", style = MaterialTheme.typography.titleLarge)
                        if (state.snapshot.entries == null) Text("Allocation history unavailable; not assumed empty.")
                        else if (state.snapshot.entries.orEmpty().none { !it.deleted }) Text("No allocation entries recorded. Opening allocation is shown separately.")
                        Text("Opening allocation: LKR ${PresentationFormat.minor(goal.config.openingMinor)}")
                    }
                }
                items(state.snapshot.entries.orEmpty().filter { !it.deleted }.sortedWith(
                    compareByDescending<com.clarity.finance.data.model.SavingsEntry> { it.entryDate }.thenBy { it.id }), key = { it.id }) { entry ->
                    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${if (entry.kind == "contribution") "Contribution" else "Withdrawal"}: LKR ${PresentationFormat.minor(entry.amountMinor)}", style = MaterialTheme.typography.titleMedium)
                            Text(PresentationFormat.date(entry.entryDate)); if (entry.note.isNotBlank()) Text(entry.note)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.openEntry(entry) }, modifier = Modifier.testTag("goal-edit-${entry.id}"), enabled = state.canChange) { Text("Inspect / correct entry") }
                                TextButton(onClick = { viewModel.requestDelete(entry) }, modifier = Modifier.testTag("goal-delete-${entry.id}"), enabled = state.canChange) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    state.form?.let { form ->
        val isGoal = state.formKind == "goal"
        ReviewFormDialog(if (isGoal) "Goal details" else "Allocation details", viewModel::closeForm, actions = {
            Button(onClick = viewModel::save, enabled = state.canSaveForm, modifier = Modifier.fillMaxWidth()) { Text("Save proposal") }
            TextButton(onClick = viewModel::closeForm, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Close form") }
        }) {
            fun change(next: GoalForm) = viewModel.update(next)
            @Composable fun field(label: String, value: String, decimal: Boolean = false, editable: Boolean = true, supporting: String? = null, set: (String) -> Unit) {
                OutlinedTextField(value, set, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy && state.unknown == null && editable, singleLine = true,
                    supportingText = supporting?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
            }
            if (isGoal) {
                Text("Start date: ${PresentationFormat.date(form.startDate ?: state.snapshot.goal?.config?.startDate ?: SavingsMath.today().toString())} · Asia/Colombo")
                if (form.revising != null) StatusPanel("Only the deadline changes. The original values stay locked until the replacement is saved safely.")
                Text("Opening allocation is money you have already set aside.", style = MaterialTheme.typography.bodySmall)
                field("Goal name", form.name, editable = form.revising == null) { change(form.copy(name = it)) }
                field("Target (LKR)", form.target, true, editable = form.revising == null) { change(form.copy(target = it)) }
                field("Opening allocation", form.opening, true, editable = form.revising == null, supporting = "Amount in LKR") { change(form.copy(opening = it)) }
                field("Deadline", form.deadline, supporting = "YYYY-MM-DD") { change(form.copy(deadline = it)) }
            } else {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceOption("Contribution", form.kind == "contribution", !state.busy && state.unknown == null) { change(form.copy(kind = "contribution")) }
                    ChoiceOption("Withdrawal", form.kind == "withdrawal", !state.busy && state.unknown == null) { change(form.copy(kind = "withdrawal")) }
                }
                field("Amount (LKR)", form.amount, true, supporting = "Enter a positive amount.") { change(form.copy(amount = it)) }
                field("Allocation date", form.date, supporting = "YYYY-MM-DD · Asia/Colombo") { change(form.copy(date = it)) }
                OutlinedTextField(form.note, { change(form.copy(note = it)) }, label = { Text("Note (optional)") },
                    supportingText = { Text("Up to 500 characters") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && state.unknown == null)
            }
            state.error?.let { StatusPanel(it, isError = true) }
        }
    }
    state.deleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = viewModel::cancelDelete, containerColor = MaterialTheme.colorScheme.surface, title = { Text("Delete allocation entry?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${PresentationFormat.date(entry.entryDate)}: LKR ${PresentationFormat.minor(entry.amountMinor)}. Confirmed savings change only after server acknowledgement.")
                state.error?.let { Text(it) }
            } }, confirmButton = { TextButton(onClick = viewModel::confirmDelete, enabled = state.canChange) { Text("Confirm deletion") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete, enabled = !state.busy) { Text("Keep entry") } })
    }
    if (showSchedule) state.progress?.schedule?.let { schedule ->
        Dialog(onDismissRequest = { showSchedule = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Contribution schedule", style = MaterialTheme.typography.titleLarge)
                    Text("Asia/Colombo · Monthly dates keep the original start-day anchor, plus the deadline when needed. Required amounts round up to the nearest cent.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f, fill = false)) { items(schedule.count) { Text(PresentationFormat.date(schedule.dateAt(it).toString()), Modifier.padding(8.dp)) } }
                    TextButton(onClick = { showSchedule = false }, modifier = Modifier.fillMaxWidth()) { Text("Close schedule") }
                }
            }
        }
    }
    if (information) InformationDialog("Recorded savings",
        "Contributions record money you say you set aside. Clarity does not transfer or verify funds. Monthly surplus never automatically increases savings. Opening allocation plus confirmed contributions minus confirmed withdrawals is your recorded saved amount.") { information = false }
}
