package com.clarity.finance.ui.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AddEditIncomeViewModel
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditIncomeScreen(
    viewModel: AddEditIncomeViewModel,
    incomeId: String?,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editing by rememberSaveable(incomeId) { mutableStateOf(false) }

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
        com.clarity.finance.ui.pilot.LoadingPanel("Loading income")
        return
    }

    if (formState.isMissing && !formState.isSubmitting && !formState.isDeleting) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Income not found or already deleted.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateBack) { Text("Back") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(if (formState.isEditMode) { if (editing) "Edit Income" else "Income details" } else "Add Income", onNavigateBack)
        if (formState.isEditMode && !formState.isStale && !formState.isMissing) {
            TextButton(onClick = { showDeleteDialog = true }, enabled = !formState.isSubmitting && !formState.isDeleting) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        if (formState.isStale) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("This income changed elsewhere. Review the latest record before saving. Your draft is retained.", color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadIncome(incomeId!!) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Reload from Server")
                    }
                }
            }
        }

        val catalogue = com.clarity.finance.ui.LocalCatalogue.current
        val sources = catalogue.choices("source", if (formState.isEditMode) formState.sourceId else null).map { it.id to it.name }
        
        if (formState.isEditMode && !editing) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${formState.currency} ${PresentationFormat.amount(formState.originalAmountStr)}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                DetailValue("Source", catalogue.referenceLabel("source", formState.sourceId))
                DetailValue("Received", PresentationFormat.date(formState.receiptDate))
                if (formState.currency == "USD") {
                    if (formState.conversionBasis == "confirmed_rate") {
                        DetailValue("Confirmed rate", "${formState.conversionRateStr} LKR/USD")
                        formState.previewLkrText?.let { DetailValue("Recorded LKR equivalent", "LKR ${PresentationFormat.amount(it)}") }
                    } else DetailValue("Actual LKR credited", "LKR ${PresentationFormat.amount(formState.actualLkrCreditStr)}")
                }
                if (formState.note.isNotBlank()) Text(formState.note)
                formState.error?.let { StatusPanel(it, isError = true) }
                Button(onClick = { editing = true }, enabled = !formState.isSubmitting && !formState.isDeleting) { Text("Edit income") }
                TextButton(onClick = onNavigateBack) { Text("Back to income") }
            }
        } else
        Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            com.clarity.finance.ui.CatalogueStatus()
            Text("Record money already received. Pending agreements are separate.", style = MaterialTheme.typography.bodySmall)
            // Source
            var srcExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = srcExpanded,
                onExpandedChange = { srcExpanded = !srcExpanded }
            ) {
                OutlinedTextField(
                    value = catalogue.referenceLabel("source", formState.sourceId),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Source") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExpanded) },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = srcExpanded, onDismissRequest = { srcExpanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
                    sources.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(catalogue.referenceLabel("source", option.first)) },
                            onClick = {
                                viewModel.updateForm(sourceId = option.first, currency = catalogue.defaultValue("source", option.first) ?: formState.currency)
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
                ExposedDropdownMenu(expanded = currExpanded, onDismissRequest = { currExpanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
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
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceOption("Exchange Rate", formState.conversionBasis == "confirmed_rate") { viewModel.updateForm(conversionBasis = "confirmed_rate") }
                    ChoiceOption("Actual LKR", formState.conversionBasis == "actual_credit") { viewModel.updateForm(conversionBasis = "actual_credit") }
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
                    
                    formState.effectiveRateText?.let { Text("Effective rate: ~$it LKR/USD", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp)) }

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
                formState.previewLkrText?.let { Text("LKR equivalent: ${PresentationFormat.amount(it)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }

            }

            if (formState.error != null) {
                Text(text = formState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.saveChanges() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !formState.isSubmitting && !formState.isDeleting && sources.any { it.first == formState.sourceId }
            ) {
                if (formState.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text(if (formState.isEditMode) "Save Changes" else "Add Income")
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this income? (${formState.currency} ${PresentationFormat.amount(formState.originalAmountStr)} on ${PresentationFormat.date(formState.receiptDate)})") },
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
