package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import com.clarity.finance.ui.pilot.InformationAction
import com.clarity.finance.ui.pilot.StatusPanel
import com.clarity.finance.data.model.CryptoReporting
import java.time.LocalDate
import java.time.ZoneOffset

val categoryUiMap = com.clarity.finance.data.model.ExpenseCategories.labels

val channelUiMap = mapOf(
    "cash" to "Cash",
    "primary_bank_card" to "Primary bank/card",
    "secondary_bank_card" to "Secondary bank/card",
    "other" to "Other"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormContent(
    amountStr: String,
    onAmountChange: (String) -> Unit,
    categoryId: String,
    onCategoryChange: (String) -> Unit,
    paymentChannelId: String,
    onPaymentChannelChange: (String) -> Unit,
    spendingType: String,
    onSpendingTypeChange: (String) -> Unit,
    expenseDate: String,
    onDateChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    error: String?,
    isSubmitting: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    saveButtonText: String = "Save"
) {
    val catalogue = com.clarity.finance.ui.LocalCatalogue.current
    val categoryUiMap = catalogue.labels("category")
    val categories = catalogue.choices("category", if (saveButtonText != "Save") categoryId else null).map { it.id }
    val channels = channelUiMap.keys.toList()
    var information by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    val today = CryptoReporting.today() // Reporting date, independent of device time zone.
    if (showCalendar) {
        val calendar = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(expenseDate) }.getOrDefault(today)
                .coerceAtMost(today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1..today.year,
            initialDisplayMode = if (LocalDensity.current.fontScale >= 1.5f) DisplayMode.Input else DisplayMode.Picker,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        )
        DatePickerDialog(onDismissRequest = { showCalendar = false },
            confirmButton = { TextButton(onClick = {
                calendar.selectedDateMillis?.let { millis ->
                    onDateChange(java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                showCalendar = false
            }, enabled = !isSubmitting && calendar.selectedDateMillis != null) { Text("Use date") } },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Keep date") } }) {
            DatePicker(calendar, modifier = Modifier.verticalScroll(rememberScrollState()))
        }
    }
    information?.let { message ->
        AlertDialog(onDismissRequest = { information = null }, title = { Text("About this expense") },
            text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { information = null }) { Text("Close") } })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (catalogue.entries == null) Text("Loading categories. Connect to load uncached choices.")
        if (catalogue.fromCache) Text("Cached category choices; proposals await server checks.")
        catalogue.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = amountStr,
            onValueChange = onAmountChange,
            label = { Text("Amount (LKR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = expenseDate,
            onValueChange = onDateChange,
            label = { Text("Date (yyyy-MM-dd)") },
            singleLine = true, enabled = !isSubmitting,
            trailingIcon = { IconButton(onClick = { showCalendar = true }, enabled = !isSubmitting) {
                Icon(Icons.Outlined.CalendarMonth, "Choose payment date")
            } },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Payment date · Asia/Colombo · Backdating allowed", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (categoryId !in categories) Text("Choose an active category before saving.", color = MaterialTheme.colorScheme.error)
        var catExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = catExpanded,
            onExpandedChange = { if (!isSubmitting) catExpanded = !catExpanded }
        ) {
            OutlinedTextField(
                value = catalogue.label("category", categoryId),
                onValueChange = {},
                readOnly = true, enabled = !isSubmitting,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = catExpanded,
                onDismissRequest = { catExpanded = false }
            ) {
                categories.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(categoryUiMap[selectionOption]!!) },
                        onClick = {
                            onCategoryChange(selectionOption)
                            catalogue.defaultValue("category", selectionOption)?.let(onSpendingTypeChange)
                            catExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var chanExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = chanExpanded,
            onExpandedChange = { if (!isSubmitting) chanExpanded = !chanExpanded }
        ) {
            OutlinedTextField(
                value = channelUiMap[paymentChannelId] ?: "Cash",
                onValueChange = {},
                readOnly = true, enabled = !isSubmitting,
                label = { Text("Payment Channel") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chanExpanded) },
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = chanExpanded,
                onDismissRequest = { chanExpanded = false }
            ) {
                channels.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(channelUiMap[selectionOption]!!) },
                        onClick = {
                            onPaymentChannelChange(selectionOption)
                            chanExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Spending type", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            InformationAction("Spending type information") { information = "Committed covers obligations such as rent and bills. Discretionary covers flexible choices. Choose the classification that fits this payment." }
        }
        FlowRow(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.heightIn(min = 48.dp).selectable(spendingType == "committed", enabled = !isSubmitting, role = Role.RadioButton, onClick = { onSpendingTypeChange("committed") }).padding(end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = spendingType == "committed", onClick = null)
                Text("Committed")
            }
            Row(Modifier.heightIn(min = 48.dp).selectable(spendingType == "discretionary", enabled = !isSubmitting, role = Role.RadioButton, onClick = { onSpendingTypeChange("discretionary") }).padding(end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = spendingType == "discretionary", onClick = null)
                Text("Discretionary")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("Note (Optional)") },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Saved locally first; totals wait for confirmation.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            InformationAction("Saving information") { information = "Your proposal is saved on this device before submission. Pending edits and deletions keep the original recorded amount until confirmation. A timeout does not establish whether the server accepted a change." }
        }
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !isSubmitting && categoryId in categories
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Saving on this device…")
            } else {
                Text(saveButtonText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
