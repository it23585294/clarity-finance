package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

data class AddExpenseFormState(
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

class AddExpenseViewModel(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(AddExpenseFormState())
    val formState = _formState.asStateFlow()

    fun resetState() {
        _formState.value = AddExpenseFormState()
    }

    fun addExpense(
        amountStr: String,
        categoryId: String,
        paymentChannelId: String,
        spendingType: String,
        expenseDate: String,
        note: String
    ) {
        if (_formState.value.isSubmitting) return

        val amountMinorResult = parseAmount(amountStr)
        if (amountMinorResult.isFailure) {
            _formState.value = _formState.value.copy(error = amountMinorResult.exceptionOrNull()?.message)
            return
        }

        if (!isValidDate(expenseDate)) {
            _formState.value = _formState.value.copy(error = "Invalid date format. Use real calendar date (yyyy-MM-dd)")
            return
        }

        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (user == null) {
                _formState.value = _formState.value.copy(error = "User not authenticated")
                return@launch
            }

            _formState.value = AddExpenseFormState(isSubmitting = true)

            val expense = Expense(
                id = UUID.randomUUID().toString(),
                amountMinor = amountMinorResult.getOrThrow(),
                currency = "LKR",
                categoryId = categoryId,
                paymentChannelId = paymentChannelId,
                spendingType = spendingType,
                expenseDate = expenseDate,
                note = note.trim()
            )

            val result = expenseRepository.enqueueExpense(user.uid, expense)
            if (result.isSuccess) {
                // It was queued successfully
                _formState.value = AddExpenseFormState(isSuccess = true)
            } else {
                _formState.value = AddExpenseFormState(
                    error = result.exceptionOrNull()?.message ?: "Failed to save expense"
                )
            }
        }
    }

    fun parseAmount(amount: String): Result<Long> {
        return try {
            val bd = BigDecimal(amount.trim())
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Amount must be positive"))
            if (bd.scale() > 2) return Result.failure(Exception("Maximum 2 decimal places allowed"))
            
            val cents = bd.multiply(BigDecimal(100)).longValueExact()
            Result.success(cents)
        } catch (e: ArithmeticException) {
            Result.failure(Exception("Amount is too large"))
        } catch (e: Exception) {
            Result.failure(Exception("Invalid amount"))
        }
    }

    fun getDefaultDate(): String {
        return LocalDate.now().toString()
    }

    private fun isValidDate(dateStr: String): Boolean {
        return try {
            LocalDate.parse(dateStr)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
