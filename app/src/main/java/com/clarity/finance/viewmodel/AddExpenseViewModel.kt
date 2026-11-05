package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID

data class ExpenseDraft(val amountStr: String = "", val categoryId: String = "other", val paymentChannelId: String = "cash",
    val spendingType: String = "discretionary", val expenseDate: String = "", val note: String = "")

data class AddExpenseFormState(val isSubmitting: Boolean = false, val isSuccess: Boolean = false,
    val error: String? = null, val accountKey: String? = null, val draft: ExpenseDraft = ExpenseDraft())

class AddExpenseViewModel(private val authRepository: AuthRepository, private val expenseRepository: ExpenseRepository,
    private val clock: Clock = Clock.systemUTC()) : ViewModel() {
    private val mutable = MutableStateFlow(AddExpenseFormState())
    val formState = mutable.asStateFlow()
    private var uid: String? = null
    private var generation = 0L
    private var proposal: Expense? = null
    init { viewModelScope.launch {
        authRepository.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
            if (uid != user?.uid) {
                generation++; uid = user?.uid; proposal = null
                mutable.value = freshState()
            }
        }
    } }
    private fun freshState() = AddExpenseFormState(accountKey = uid, draft = ExpenseDraft(expenseDate = getDefaultDate()))
    fun resetState() { proposal = null; mutable.value = freshState() }
    /** Retained UI draft only. Never serialized into recovery or restored into a fresh submission owner. */
    fun updateDraft(owner: String, amountStr: String? = null, categoryId: String? = null, paymentChannelId: String? = null,
        spendingType: String? = null, expenseDate: String? = null, note: String? = null) {
        mutable.update { current ->
            if (current.accountKey != owner || current.isSubmitting || current.isSuccess) current else {
                val old = current.draft
                current.copy(draft = old.copy(amountStr = amountStr ?: old.amountStr, categoryId = categoryId ?: old.categoryId,
                    paymentChannelId = paymentChannelId ?: old.paymentChannelId, spendingType = spendingType ?: old.spendingType,
                    expenseDate = expenseDate ?: old.expenseDate, note = note ?: old.note))
            }
        }
    }
    fun addExpense(amountStr: String, categoryId: String, paymentChannelId: String, spendingType: String, expenseDate: String, note: String) {
        if (formState.value.isSubmitting || formState.value.isSuccess) return
        val owner = uid ?: run { mutable.value = formState.value.copy(error = "User not authenticated"); return }
        val desired = try {
            val minor = SavingsMath.amount(amountStr.trim())
            require(SavingsMath.date(expenseDate) <= SavingsMath.today(clock)) { "Use a real nonfuture expense date (yyyy-MM-dd)." }
            require(note.trim().length <= 500) { "Note must have at most 500 characters." }
            require(CatalogueDefaults.validId("category", categoryId) && paymentChannelId in Recurrence.channels && spendingType in setOf("committed", "discretionary")) { "Choose valid classifications." }
            Expense(proposal?.id ?: UUID.randomUUID().toString(), minor, categoryId = categoryId,
                paymentChannelId = paymentChannelId, spendingType = spendingType, expenseDate = expenseDate, note = note.trim())
                .also { check(proposal == null || proposal == it) { "Retry the original saved details, or inspect Recovery before changing them." } }
        } catch (e: Exception) { mutable.value = formState.value.copy(error = e.message); return }
        proposal = desired // Stable even if durable acceptance itself is interrupted/returns an error.
        val token = generation; mutable.value = formState.value.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val result = expenseRepository.enqueueExpense(owner, desired)
                if (generation == token && uid == owner) mutable.value = formState.value.copy(isSubmitting = false,
                    isSuccess = result.isSuccess, error = result.exceptionOrNull()?.message)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == token) mutable.value = formState.value.copy(isSubmitting = false, error = e.message) }
        }
    }
    fun parseAmount(amount: String): Result<Long> = try { Result.success(SavingsMath.amount(amount.trim())) }
        catch (e: Exception) { Result.failure(e) }
    fun getDefaultDate() = SavingsMath.today(clock).toString()
}
