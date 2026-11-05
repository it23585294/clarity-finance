package com.clarity.finance.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AddPaymentViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPaymentScreen(
    viewModel: AddPaymentViewModel,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val catalogue = com.clarity.finance.ui.LocalCatalogue.current
    val editable = !formState.isLoading && !formState.isSubmitting && !formState.isRetry

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            PageHeader("Add agreement", onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Record expected income. This does not record a received payment.", style = MaterialTheme.typography.bodySmall)
            if (formState.error != null) {
                Text(
                    text = formState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (formState.isLoading) {
                Text("Checking saved attempts…")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text("Agreement type", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    enabled = editable,
                    selected = formState.type == "standalone",
                    onClick = { viewModel.updateForm(type = "standalone") },
                    label = { Text("Standalone") }
                )
                FilterChip(
                    enabled = editable,
                    selected = formState.type == "project",
                    onClick = { viewModel.updateForm(type = "project") },
                    label = { Text("Project") }
                )
            }

            OutlinedTextField(
                enabled = editable,
                value = formState.title,
                onValueChange = { viewModel.updateForm(title = it) },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            com.clarity.finance.ui.CatalogueStatus()
            SelectionField("Source", catalogue.referenceLabel("source", formState.sourceId),
                catalogue.choices("source").map { it.id to it.name }, editable) { id ->
                viewModel.updateForm(sourceId = id, currency = catalogue.defaultValue("source", id) ?: formState.currency)
            }
            SelectionField("Currency", formState.currency, listOf("LKR" to "LKR", "USD" to "USD"), editable) { viewModel.updateForm(currency = it) }

            OutlinedTextField(
                enabled = editable,
                value = formState.amountStr,
                onValueChange = { viewModel.updateForm(amountStr = it) },
                label = { Text("Agreed Amount *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                enabled = editable,
                value = formState.dueDate,
                onValueChange = { viewModel.updateForm(dueDate = it) },
                label = { Text("Due Date (yyyy-MM-dd) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                enabled = editable,
                value = formState.note,
                onValueChange = { viewModel.updateForm(note = it) },
                label = { Text("Note (optional, max 500 chars)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    enabled = !formState.isSubmitting && !formState.isLoading
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.savePayment() },
                    enabled = !formState.isSubmitting && !formState.isLoading && (formState.isRetry || catalogue.accepts("source", formState.sourceId))
                ) {
                    if (formState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (formState.isRetry) "Retry Save" else "Save")
                    }
                }
            }
        }
    }
}
