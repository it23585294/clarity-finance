package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val categoryUiMap = mapOf(
    "food_dining" to "Food & Dining",
    "transport" to "Transport",
    "housing" to "Housing",
    "utilities" to "Utilities",
    "shopping" to "Shopping",
    "health" to "Health",
    "entertainment" to "Entertainment",
    "education" to "Education",
    "other" to "Other"
)

val channelUiMap = mapOf(
    "cash" to "Cash",
    "primary_bank_card" to "Primary bank/card",
    "secondary_bank_card" to "Secondary bank/card",
    "other" to "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
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
    val categories = categoryUiMap.keys.toList()
    val channels = channelUiMap.keys.toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = amountStr,
            onValueChange = onAmountChange,
            label = { Text("Amount (LKR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        var catExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = catExpanded,
            onExpandedChange = { catExpanded = !catExpanded }
        ) {
            OutlinedTextField(
                value = categoryUiMap[categoryId] ?: "Other",
                onValueChange = {},
                readOnly = true,
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
            onExpandedChange = { chanExpanded = !chanExpanded }
        ) {
            OutlinedTextField(
                value = channelUiMap[paymentChannelId] ?: "Cash",
                onValueChange = {},
                readOnly = true,
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = spendingType == "committed", onClick = { onSpendingTypeChange("committed") })
                Text("Committed")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = spendingType == "discretionary", onClick = { onSpendingTypeChange("discretionary") })
                Text("Discretionary")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = expenseDate,
            onValueChange = onDateChange,
            label = { Text("Date (yyyy-MM-dd)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("Note (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
