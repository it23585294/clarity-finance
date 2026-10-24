package com.clarity.finance.ui.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AddEditIncomeViewModel
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditIncomeScreen(
    viewModel: AddEditIncomeViewModel,
    incomeId: String?,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(incomeId) {
        if (incomeId != null) {
            viewModel.loadIncome(incomeId)
        } else {
            viewModel.resetState()
        }
    }

    LaunchedEffect(formState.isSuccess, formState.isDeleted) {
        if (formState.isSuccess || formState.isDeleted) {
            viewModel.resetState()
            onNavigateBack()
        }
    }

    if (formState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (formState.isMissing && !formState.isSubmitting && !formState.isDeleting) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Income not found or already deleted.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateBack) { Text("Go Back") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (formState.isEditMode) "Edit Income" else "Add Income", style = MaterialTheme.typography.headlineSmall)
            if (formState.isEditMode && !formState.isStale && !formState.isMissing) {
                TextButton(onClick = { showDeleteDialog = true }, enabled = !formState.isSubmitting && !formState.isDeleting) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (formState.isStale) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Warning: This income was modified elsewhere since you opened it. Saving now would overwrite those changes.", color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadIncome(incomeId!!) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Reload from Server")
                    }
                }
            }
        }

        val sources = listOf("salary" to "Salary", "freelance" to "Freelance", "adsense" to "Adsense")
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Source
            var srcExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = srcExpanded,
                onExpandedChange = { srcExpanded = !srcExpanded }
            ) {
                OutlinedTextField(
                    value = sources.find { it.first == formState.sourceId }?.second ?: "Salary",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Source") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExpanded) },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = srcExpanded, onDismissRequest = { srcExpanded = false }) {
                    sources.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.second) },
                            onClick = {
                                viewModel.updateForm(sourceId = option.first)
                                srcExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date
            OutlinedTextField(
                value = formState.receiptDate,
                onValueChange = { viewModel.updateForm(receiptDate = it) },
                label = { Text("Receipt Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Currency
            var currExpanded by remember { mutableStateOf(false) }
            val currencies = listOf("LKR", "USD")
            ExposedDropdownMenuBox(
                expanded = currExpanded,
                onExpandedChange = { currExpanded = !currExpanded }
            ) {
                OutlinedTextField(
                    value = formState.currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currExpanded) },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = currExpanded, onDismissRequest = { currExpanded = false }) {
                    currencies.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                viewModel.updateForm(currency = option)
                                currExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = formState.originalAmountStr,
                onValueChange = { viewModel.updateForm(originalAmountStr = it) },
                label = { Text("Original Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (formState.currency == "USD") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = formState.conversionBasis == "confirmed_rate", onClick = { viewModel.updateForm(conversionBasis = "confirmed_rate") })
                        Text("Exchange Rate")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = formState.conversionBasis == "actual_credit", onClick = { viewModel.updateForm(conversionBasis = "actual_credit") })
                        Text("Actual LKR")
                    }
                }

                if (formState.conversionBasis == "confirmed_rate") {
                    OutlinedTextField(
                        value = formState.conversionRateStr,
                        onValueChange = { viewModel.updateForm(conversionRateStr = it) },
                        label = { Text("Confirmed LKR per USD") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (formState.conversionBasis == "actual_credit") {
                    OutlinedTextField(
                        value = formState.actualLkrCreditStr,
                        onValueChange = { viewModel.updateForm(actualLkrCreditStr = it) },
                        label = { Text("Actual LKR Credited") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Show derived rate
                    val origResult = viewModel.parseAmount(formState.originalAmountStr)
                    val lkrResult = viewModel.parseAmount(formState.actualLkrCreditStr)
                    if (origResult.isSuccess && lkrResult.isSuccess && origResult.getOrThrow() > BigDecimal.ZERO) {
                        val derived = lkrResult.getOrThrow().divide(origResult.getOrThrow(), 4, RoundingMode.HALF_UP)
                        Text("Effective rate: ~$derived LKR/USD", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = formState.note,
                onValueChange = { viewModel.updateForm(note = it) },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Preview LKR
            Spacer(modifier = Modifier.height(16.dp))
            if (formState.currency == "USD" && formState.conversionBasis == "confirmed_rate") {
                val origResult = viewModel.parseAmount(formState.originalAmountStr)
                val rateResult = viewModel.parseAmount(formState.conversionRateStr) // Use basic parsing for preview
                if (origResult.isSuccess && rateResult.isSuccess) {
                    val lkr = origResult.getOrThrow().multiply(rateResult.getOrThrow()).setScale(2, RoundingMode.HALF_UP)
                    Text("Preview: $lkr LKR", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (formState.error != null) {
                Text(text = formState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.saveChanges() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !formState.isSubmitting && !formState.isDeleting
            ) {
                if (formState.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text(if (formState.isEditMode) "Save Changes" else "Add Income")
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this income? (${formState.originalAmountStr} ${formState.currency} on ${formState.receiptDate})") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteIncome()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
