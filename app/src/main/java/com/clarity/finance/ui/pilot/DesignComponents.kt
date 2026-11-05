package com.clarity.finance.ui.pilot

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun LoadingPanel(message: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PeriodSelector(month: String, canPrevious: Boolean, canNext: Boolean, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(PresentationFormat.month(month), Modifier.weight(1f).testTag("reporting-period"), style = MaterialTheme.typography.titleLarge)
        IconButton(previous, enabled = canPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        IconButton(next, enabled = canNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

@Composable
fun InformationAction(label: String, onClick: () -> Unit) {
    IconButton(onClick) { Icon(Icons.Outlined.Info, label, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** No clipping/ellipsis: values and labels wrap naturally at large font sizes. */
@Composable
fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StatusPanel(message: String, isError: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}


/** Responsive header shared by Phase B; destinations and owners stay outside presentation. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Archive is metadata on a stable reference, never a replacement active choice. */
fun com.clarity.finance.data.model.CatalogueSnapshot.referenceLabel(kind: String, id: String): String =
    label(kind, id) + if (entries?.any { it.kind == kind && it.id == id && it.archived } == true) " (archived)" else ""

@Composable
fun ChoiceOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).selectable(selected, enabled = enabled,
        role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionField(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean,
    onSelect: (String) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, enabled = enabled, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled))
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
            choices.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

/** Phase C forms: bounded scrolling content, with actions reachable above the IME.
 * The caller retains all eligibility, dismissal and immutable-retry decisions. */
@Composable
fun ReviewFormDialog(title: String, onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(decorFitsSystemWindows = false)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().safeDrawingPadding().imePadding().padding(16.dp)) {
                Column(Modifier.weight(1f, fill = false)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    content()
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
            }
        }
    }
}

@Composable
fun InformationDialog(title: String, explanation: String, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { Text(explanation, Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
        confirmButton = { TextButton(onClick = onClose) { Text("Close information") } })
}
