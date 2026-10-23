package com.clarity.finance.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AddExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    var amountStr by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("other") }
    var paymentChannelId by remember { mutableStateOf("cash") }
    var spendingType by remember { mutableStateOf("discretionary") }
    var expenseDate by remember { mutableStateOf(viewModel.getDefaultDate()) }
    var note by remember { mutableStateOf("") }

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
    val categories = categoryUiMap.keys.toList()

    val channelUiMap = mapOf(
        "cash" to "Cash",
        "primary_bank_card" to "Primary bank/card",
        "secondary_bank_card" to "Secondary bank/card",
        "other" to "Other"
    )
    val channels = channelUiMap.keys.toList()
    
    // Auto-navigate on success
    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetState()
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
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
                            categoryId = selectionOption
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
                            paymentChannelId = selectionOption
                            chanExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = spendingType == "committed", onClick = { spendingType = "committed" })
                Text("Committed")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = spendingType == "discretionary", onClick = { spendingType = "discretionary" })
                Text("Discretionary")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = expenseDate,
            onValueChange = { expenseDate = it },
            label = { Text("Date (yyyy-MM-dd)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (formState.error != null) {
            Text(
                text = formState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                viewModel.addExpense(
                    amountStr, categoryId, paymentChannelId, spendingType, expenseDate, note
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !formState.isSubmitting
        ) {
            if (formState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Save")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
