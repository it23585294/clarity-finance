package com.clarity.finance.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.data.model.SavingsMath
import com.clarity.finance.viewmodel.*

@Composable
fun GoalCard(viewModel: GoalCardViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Savings Goal", style = MaterialTheme.typography.titleMedium)
            Text("Current explicitly allocated savings; separate from monthly surplus.")
            val goal = state.snapshot.goal
            when {
                goal != null -> { Text(goal.config.name); Text("Saved: LKR ${SavingsMath.money(goal.savedMinor)}") }
                state.snapshot.known -> Text("No savings goal recorded.")
                else -> Text("Savings goal unavailable; no zero balance is assumed.")
            }
            GoalStatus(state)
            OutlinedButton(onClick = onOpen) { Text("Open Savings Goal") }
        }
    }
}

@Composable
private fun GoalStatus(state: GoalUiState) {
    if (state.snapshot.fromCache && state.snapshot.goal != null) Text("Cached savings; may be incomplete or out of date.")
    state.snapshot.error?.let { Text(it) }
    state.snapshot.recoveryError?.let { Text(it) }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.busy) Text("Saving proposal locally...")
    if (state.localPending != null) Text("Saved proposal awaiting server confirmation. Confirmed savings are retained.")
    if (state.unknown != null) Text("Local save outcome unknown. Keep the original proposal; retry checks the same identity.")
    state.snapshot.attempts.forEach {
        Text(when {
            state.snapshot.acknowledgedOperationId == it.operation.operationId -> "Server confirmed proposal; completing local recovery cleanup."
            it.isRejected -> "Rejected proposal: ${it.error ?: "Conflict or validation failure"}. Confirmed savings are retained."
            it.isUncertain -> "Proposal outcome uncertain: ${it.error ?: "Reconciliation required"}. Retained until server reconciliation; it cannot be dismissed."
            else -> "Saved proposal awaiting server confirmation. Confirmed savings are retained."
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(viewModel: GoalViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSchedule by remember { mutableStateOf(false) }
    LaunchedEffect(state.signedIn, state.snapshot.goal?.config?.startDate) { showSchedule = false }
    LifecycleResumeEffect(viewModel) { viewModel.refreshDate(); onPauseOrDispose {} }
    Scaffold(topBar = { TopAppBar(title = { Text("Savings Goal") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("goal-history"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contributions record money you say you set aside. Clarity does not transfer or verify funds. Monthly surplus never automatically increases savings.")
                GoalStatus(state)
                if (!state.signedIn) Text("Sign in to view your goal.")
                state.snapshot.attempts.forEach { attempt ->
                    val proposed = attempt.operation
                    proposed.goal?.let { Text("Proposed goal: ${it.name}; target LKR ${SavingsMath.money(it.targetMinor)}; opening LKR ${SavingsMath.money(it.openingMinor)}; deadline ${it.deadline}.") }
                    proposed.entry?.let {
                        Text("Proposed ${if (it.deleted) "deletion of " else ""}${it.kind}: LKR ${SavingsMath.money(it.amountMinor)} on ${it.entryDate}.")
                        if (it.note.isNotBlank()) Text("Proposed note: ${it.note}")
                    }
                    if (attempt.isRejected) {
                        Text("Dismiss a rejected proposal to reopen the latest details and correct them. Retrying keeps the original payload and version.")
                        OutlinedButton(onClick = { viewModel.retry(attempt) }, enabled = !state.busy) { Text("Retry original proposal") }
                        OutlinedButton(onClick = { viewModel.dismiss(attempt) }, enabled = !state.busy) { Text("Dismiss rejected proposal") }
                    }
                }
                if (state.unknown != null) OutlinedButton(onClick = viewModel::retryUnknown, enabled = !state.busy) { Text("Retry local save") }
              }
            }
            val goal = state.snapshot.goal
            if (goal == null) {
                item {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.snapshot.known) {
                        Text("No savings goal recorded. Confirm your own target and opening allocation.")
                        Button(onClick = viewModel::openGoal, enabled = state.canChange) { Text("Create goal") }
                    } else if (state.error == null && state.snapshot.error == null) CircularProgressIndicator()
                  }
                }
            } else {
                item {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(goal.config.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Start: ${goal.config.startDate} • Deadline: ${goal.config.deadline} • Asia/Colombo")
                    Text("Saved: LKR ${SavingsMath.money(goal.savedMinor)}")
                    Text("Target: LKR ${SavingsMath.money(goal.config.targetMinor)}")
                    state.progress?.let { progress ->
                        Text("Remaining: LKR ${SavingsMath.money(progress.remainingMinor)}")
                        Text("Progress: ${progress.percent}%")
                        LinearProgressIndicator(progress = { progress.barFraction }, modifier = Modifier.fillMaxWidth())
                        if (progress.overfundedMinor > 0) Text("Overfunded: LKR ${SavingsMath.money(progress.overfundedMinor)}")
                        when {
                            progress.reached -> Text("Goal reached. Required contribution: LKR 0.00")
                            progress.overdue -> Text("Overdue: no remaining scheduled opportunities. Revise the deadline.")
                            else -> Text("Required per opportunity: LKR ${SavingsMath.money(progress.requiredMinor!!)} (rounded up to the nearest cent)")
                        }
                        Text("Remaining opportunities (including today): ${progress.opportunities}")
                        Text("Next scheduled date: ${progress.nextDate ?: "None"}")
                        OutlinedButton(onClick = { showSchedule = true }) { Text("Inspect contribution schedule") }
                    }
                    OutlinedButton(onClick = viewModel::openGoal, enabled = state.canChange) { Text("Edit goal") }
                    Button(onClick = { viewModel.openEntry() }, enabled = state.canChange) { Text("Record allocation") }
                    Text("Allocation history", style = MaterialTheme.typography.titleLarge)
                    if (state.snapshot.entries == null) Text("Allocation history unavailable; not assumed empty.")
                    else if (state.snapshot.entries.orEmpty().none { !it.deleted }) Text("No allocation entries recorded. Opening allocation is shown separately.")
                    Text("Opening allocation: LKR ${SavingsMath.money(goal.config.openingMinor)}")
                  }
                }
                items(state.snapshot.entries.orEmpty().filter { !it.deleted }.sortedWith(
                    compareByDescending<com.clarity.finance.data.model.SavingsEntry> { it.entryDate }.thenBy { it.id }), key = { it.id }) { entry ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${if (entry.kind == "contribution") "Contribution" else "Withdrawal"}: LKR ${SavingsMath.money(entry.amountMinor)}")
                            Text(entry.entryDate); if (entry.note.isNotBlank()) Text(entry.note)
                            OutlinedButton(onClick = { viewModel.openEntry(entry) }, modifier = Modifier.testTag("goal-edit-${entry.id}"), enabled = state.canChange) { Text("Inspect / correct entry") }
                            OutlinedButton(onClick = { viewModel.requestDelete(entry) }, modifier = Modifier.testTag("goal-delete-${entry.id}"), enabled = state.canChange) { Text("Delete entry") }
                        }
                    }
                }
            }
        }
    }
    state.form?.let { form ->
        Dialog(onDismissRequest = viewModel::closeForm) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isGoal = state.formKind == "goal"
                    Text(if (isGoal) "Goal details" else "Allocation details", style = MaterialTheme.typography.titleLarge)
                    fun change(next: GoalForm) = viewModel.update(next)
                    @Composable fun field(label: String, value: String, decimal: Boolean = false, set: (String) -> Unit) {
                        OutlinedTextField(value, set, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy && state.unknown == null, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
                    }
                    if (isGoal) {
                        Text("Start date: ${state.snapshot.goal?.config?.startDate ?: SavingsMath.today()} (Asia/Colombo)")
                        Text("Confirm only money you have already allocated as the opening allocation.")
                        field("Goal name", form.name) { change(form.copy(name = it)) }
                        field("Target (LKR)", form.target, true) { change(form.copy(target = it)) }
                        field("Opening allocation (LKR)", form.opening, true) { change(form.copy(opening = it)) }
                        field("Deadline (YYYY-MM-DD)", form.deadline) { change(form.copy(deadline = it)) }
                    } else {
                        OutlinedButton(onClick = { change(form.copy(kind = if (form.kind == "contribution") "withdrawal" else "contribution")) },
                            enabled = !state.busy && state.unknown == null) { Text("Type: ${form.kind} (change)") }
                        field("Positive amount (LKR)", form.amount, true) { change(form.copy(amount = it)) }
                        field("Allocation date (YYYY-MM-DD)", form.date) { change(form.copy(date = it)) }
                        OutlinedTextField(form.note, { change(form.copy(note = it)) }, label = { Text("Optional note (max 500)") },
                            modifier = Modifier.fillMaxWidth(), enabled = !state.busy && state.unknown == null)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = viewModel::save, enabled = state.canChange) { Text("Save proposal") }
                    OutlinedButton(onClick = viewModel::closeForm, enabled = !state.busy) { Text("Close form") }
                }
            }
        }
    }
    state.deleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = viewModel::cancelDelete, title = { Text("Delete allocation entry?") },
            text = { Column { Text("${entry.entryDate}: LKR ${SavingsMath.money(entry.amountMinor)}. Confirmed savings change only after server acknowledgement.")
                state.error?.let { Text(it) } } },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, enabled = state.canChange) { Text("Confirm deletion") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete, enabled = !state.busy) { Text("Keep entry") } })
    }
    if (showSchedule) state.progress?.schedule?.let { schedule ->
        Dialog(onDismissRequest = { showSchedule = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(16.dp)) {
                    Text("Contribution schedule • Asia/Colombo")
                    Text("Monthly dates use the original start-day anchor, plus the final deadline when needed.")
                    LazyColumn(Modifier.weight(1f, fill = false)) { items(schedule.count) { Text(schedule.dateAt(it).toString(), Modifier.padding(8.dp)) } }
                    TextButton(onClick = { showSchedule = false }) { Text("Close schedule") }
                }
            }
        }
    }
}
