package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class CatalogueForm(val entry: CatalogueEntry, val name: String = entry.name, val defaultValue: String = entry.defaultValue)
data class CatalogueUiState(val uid: String? = null, val catalogue: CatalogueSnapshot = CatalogueSnapshot(),
    val recovery: FinancialRecovery = FinancialRecovery(), val form: CatalogueForm? = null,
    val archive: CatalogueEntry? = null, val busy: Boolean = false, val error: String? = null)

/** Account-scoped data/forms; production synchronization is owned by the Application, never this screen. */
class CatalogueViewModel(private val auth: AuthRepository, private val repository: CatalogueRepository,
    private val queue: FinancialRecoveryAccess) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogueUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    init { viewModelScope.launch {
        auth.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
            val token = ++generation; job?.cancel(); mutable.value = CatalogueUiState(uid = user?.uid)
            val uid = user?.uid ?: return@collect
            job = launch {
                launch { repository.observe(uid).collect { if (token == generation) mutable.value = state.value.copy(catalogue = it) } }
                launch { queue.observe(uid).collect { if (token == generation) mutable.value = state.value.copy(recovery = it) } }
            }
        }
    } }
    fun add(kind: String) { if (!state.value.busy) mutable.value = state.value.copy(form = CatalogueForm(CatalogueEntry("custom_${UUID.randomUUID()}", kind, "")), error = null) }
    fun edit(entry: CatalogueEntry) { if (!state.value.busy) mutable.value = state.value.copy(form = CatalogueForm(entry), error = null) }
    fun change(name: String = state.value.form?.name.orEmpty(), defaultValue: String = state.value.form?.defaultValue.orEmpty()) {
        val form = state.value.form ?: return
        if (!state.value.busy) mutable.value = state.value.copy(form = form.copy(name = name, defaultValue = defaultValue), error = null)
    }
    fun close() { if (!state.value.busy) mutable.value = state.value.copy(form = null, archive = null, error = null) }
    fun askArchive(entry: CatalogueEntry) { if (!state.value.busy) mutable.value = state.value.copy(archive = entry, error = null) }
    fun save() {
        val form = state.value.form ?: return
        val entry = form.entry.copy(name = form.name.trim(), defaultValue = form.defaultValue)
        action { uid ->
            entry.validate()
            queue.submit(uid, operation(entry)); if (state.value.uid == uid) mutable.value = state.value.copy(form = null)
        }
    }
    fun archive() {
        val entry = state.value.archive ?: return
        action { uid -> queue.submit(uid, operation(entry.copy(archived = true))); if (state.value.uid == uid) mutable.value = state.value.copy(archive = null) }
    }
    private fun operation(entry: CatalogueEntry) = FinancialOperation(UUID.randomUUID().toString(),
        if (entry.kind == "category") "CATEGORY" else "SOURCE", if (entry.updatedAt == null) "CREATE" else "UPDATE",
        entry.id, expectedUpdatedAt = entry.updatedAt, catalogue = entry)
    fun resolve(attempt: FinancialAttempt) = action { queue.resolve(it, attempt.operation) }
    fun dismiss(attempt: FinancialAttempt) = action { queue.dismissRejected(it, attempt.operation) }
    private fun action(block: suspend (String) -> Unit) {
        val uid = state.value.uid ?: return
        if (state.value.busy) return
        val token = generation; mutable.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block(uid) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) mutable.value = state.value.copy(error = e.message ?: "Change unavailable; original proposal retained.") }
            finally { if (token == generation) mutable.value = state.value.copy(busy = false) }
        }
    }
}
