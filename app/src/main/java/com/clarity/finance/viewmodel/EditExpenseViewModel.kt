package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import com.clarity.finance.data.model.SavingsMath

data class EditExpenseFormState(
    val isSubmitting: Boolean = false,
    val isDeleting: Boolean = false,
    val isSuccess: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    val isStale: Boolean = false,
    val isMissing: Boolean = false,
    
    // Loaded data
    val isLoading: Boolean = true,
    val expenseId: String = "",
    val amountStr: String = "",
    val categoryId: String = "other",
    val paymentChannelId: String = "cash",
    val spendingType: String = "discretionary",
    val expenseDate: String = "",
    val note: String = ""
)

class EditExpenseViewModel(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {

    private val _formState = MutableStateFlow(EditExpenseFormState())
    val formState = _formState.asStateFlow()

    private var originalUpdatedAt: Timestamp? = null
    private var loadedUserId: String? = null
    private var generation = 0L
    private var sessionUid: String? = null
    private var initialized = false
    init { viewModelScope.launch {
        authRepository.authState.collect { user ->
            if (initialized && sessionUid != user?.uid && (sessionUid != null || loadedUserId != null)) {
                generation++; originalUpdatedAt = null; loadedUserId = null
                _formState.value = EditExpenseFormState(isLoading = false, error = "Account changed. Operation aborted.")
            }
            initialized = true; sessionUid = user?.uid
        }
    } }


    fun loadExpense(expenseId: String) {
        ++generation
        val token = generation
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (token != generation) return@launch
            if (user == null) {
                _formState.value = _formState.value.copy(error = "User not authenticated", isLoading = false)
                return@launch
            }
            loadedUserId = user.uid

            val result = expenseRepository.getExpense(user.uid, expenseId)
            if (token != generation || sessionUid != user.uid) return@launch
            if (result.isSuccess) {
                val expense = result.getOrThrow()
                originalUpdatedAt = expense.updatedAt
                val amountStr = BigDecimal.valueOf(expense.amountMinor, 2).toPlainString()
                
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    expenseId = expense.id,
                    amountStr = amountStr,
                    categoryId = expense.categoryId,
                    paymentChannelId = expense.paymentChannelId,
                    spendingType = expense.spendingType,
                    expenseDate = expense.expenseDate,
                    note = expense.note ?: "",
                    isStale = false,
                    isMissing = false,
                    error = null
                )
            } else {
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    isMissing = true,
                    error = "Expense not found or could not be loaded."
                )
            }
        }
    }

    fun updateForm(
        amountStr: String = _formState.value.amountStr,
        categoryId: String = _formState.value.categoryId,
        paymentChannelId: String = _formState.value.paymentChannelId,
        spendingType: String = _formState.value.spendingType,
        expenseDate: String = _formState.value.expenseDate,
        note: String = _formState.value.note
    ) {
        _formState.value = _formState.value.copy(
            amountStr = amountStr,
            categoryId = categoryId,
            paymentChannelId = paymentChannelId,
            spendingType = spendingType,
            expenseDate = expenseDate,
            note = note,
            error = null // Clear errors on typing
        )
    }

    fun saveChanges() {
        if (_formState.value.isSubmitting || _formState.value.isDeleting) return

        val state = _formState.value
        if (state.note.length > 500) {
            _formState.value = state.copy(error = "Note must have at most 500 characters")
            return
        }
        val amountMinorResult = parseAmount(state.amountStr)
        if (amountMinorResult.isFailure) {
            _formState.value = state.copy(error = amountMinorResult.exceptionOrNull()?.message)
            return
        }

        if (!isValidDate(state.expenseDate)) {
            _formState.value = state.copy(error = "Use a real nonfuture payment date (yyyy-MM-dd)")
            return
        }

        if (originalUpdatedAt == null) {
            _formState.value = state.copy(error = "Original timestamp missing. Cannot safely update.")
            return
        }

        _formState.value = state.copy(isSubmitting = true, error = null)

        val token = generation
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (token != generation) return@launch
            if (user == null || user.uid != loadedUserId) {
                _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed. Operation aborted.")
                return@launch
            }

            val expense = Expense(
                id = state.expenseId,
                amountMinor = amountMinorResult.getOrThrow(),
                currency = "LKR",
                categoryId = state.categoryId,
                paymentChannelId = state.paymentChannelId,
                spendingType = state.spendingType,
                expenseDate = state.expenseDate,
                note = state.note.trim()
            )

            val result = expenseRepository.updateExpense(user.uid, expense, originalUpdatedAt!!)
            
            if (token != generation || sessionUid != user.uid) return@launch
            if (result.isSuccess) {
                _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
            } else {
                val exception = result.exceptionOrNull()
                val message = exception?.message ?: "Failed to update"
                
                if (message.contains("NOT_FOUND", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isSubmitting = false, isMissing = true, error = "Expense was deleted elsewhere.")
                } else if (message.contains("ABORTED", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isSubmitting = false, isStale = true, error = "Expense was modified elsewhere. Review changes and reload.")
                } else if (message.contains("offline") || message.contains("network", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isSubmitting = false, error = "Network required to edit expense.")
                } else {
                    _formState.value = _formState.value.copy(isSubmitting = false, error = message)
                }
            }
        }
    }

    fun deleteExpense() {
        if (_formState.value.isSubmitting || _formState.value.isDeleting) return
        
        if (originalUpdatedAt == null) {
            _formState.value = _formState.value.copy(error = "Original timestamp missing. Cannot safely delete.")
            return
        }

        _formState.value = _formState.value.copy(isDeleting = true, error = null)

        val token = generation
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (token != generation) return@launch
            if (user == null || user.uid != loadedUserId) {
                _formState.value = _formState.value.copy(isDeleting = false, error = "Account changed. Operation aborted.")
                return@launch
            }

            val result = expenseRepository.deleteExpense(user.uid, _formState.value.expenseId, originalUpdatedAt!!)
            
            if (token != generation || sessionUid != user.uid) return@launch
            if (result.isSuccess) {
                _formState.value = _formState.value.copy(isDeleting = false, isDeleted = true)
            } else {
                val exception = result.exceptionOrNull()
                val message = exception?.message ?: "Failed to delete"
                
                if (message.contains("NOT_FOUND", ignoreCase = true)) {
                    // It's already deleted
                    _formState.value = _formState.value.copy(isDeleting = false, isDeleted = true)
                } else if (message.contains("ABORTED", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isDeleting = false, isStale = true, error = "Expense was modified elsewhere. Cannot delete. Reload first.")
                } else if (message.contains("offline") || message.contains("network", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isDeleting = false, error = "Network required to delete expense.")
                } else {
                    _formState.value = _formState.value.copy(isDeleting = false, error = message)
                }
            }
        }
    }

    fun resetState() {
        _formState.value = EditExpenseFormState()
    }

    private fun parseAmount(amount: String): Result<Long> {
        return try {
            Result.success(SavingsMath.amount(amount))
        } catch (e: ArithmeticException) {
            Result.failure(Exception("Amount is too large"))
        } catch (e: Exception) {
            Result.failure(Exception("Invalid amount"))
        }
    }

    private fun isValidDate(dateStr: String): Boolean {
        return try {
            SavingsMath.date(dateStr) <= SavingsMath.today(clock)
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
