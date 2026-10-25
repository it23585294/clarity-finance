package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

data class AddPaymentFormState(
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val isRetry: Boolean = false,
    val error: String? = null,

    val type: String = "standalone",
    val title: String = "",
    val sourceId: String = "freelance",
    val currency: String = "LKR",
    val amountStr: String = "",
    val dueDate: String = "",
    val note: String = ""
)

class AddPaymentViewModel(
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(AddPaymentFormState(dueDate = getDefaultDate()))
    val formState = _formState.asStateFlow()

    private var currentUserId: String = ""
    private var paymentId: String = UUID.randomUUID().toString()
    private var savedPayment: ExpectedPayment? = null
    private var operationToken: Long = 0L
    private var recoveryReady = false

    init {
        viewModelScope.launch {
            authRepository.authState.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                val newUid = user?.uid ?: ""
                if (currentUserId != newUid) {
                    val hadAccount = currentUserId.isNotEmpty()
                    operationToken++
                    savedPayment = null
                    recoveryReady = false
                    paymentId = UUID.randomUUID().toString()
                    currentUserId = newUid
                    if (user == null) {
                        _formState.value = AddPaymentFormState(dueDate = getDefaultDate(), error = "User not authenticated")
                    } else {
                        _formState.value = AddPaymentFormState(dueDate = getDefaultDate(), error = if (hadAccount) "Account changed." else null)
                    }
                }
                if (user != null) {
                    currentUserId = user.uid
                    val token = operationToken
                    _formState.value = _formState.value.copy(isLoading = true)
                    try {
                        val recovered = withTimeoutOrNull(10000L) {
                            paymentRepository.getUnresolvedProjectCreation(user.uid) to true
                        } ?: throw IllegalStateException("Unable to load saved attempts. Check your connection and reopen this form.")
                        if (token == operationToken && currentUserId == user.uid) {
                            recoveryReady = true
                            val payment = recovered.first
                            if (payment != null) {
                                savedPayment = payment
                                paymentId = payment.id
                                _formState.value = AddPaymentFormState(isRetry = true,
                                    type = payment.type, title = payment.title, sourceId = payment.sourceId,
                                    currency = payment.currency, amountStr = BigDecimal.valueOf(payment.agreedAmountMinor, 2).toPlainString(),
                                    dueDate = payment.dueDate, note = payment.note,
                                    error = "Unconfirmed creation restored. Retry uses the saved details.")
                            } else _formState.value = _formState.value.copy(isLoading = false)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (token == operationToken) _formState.value = _formState.value.copy(isLoading = false, error = e.message)
                    }
                }
            }
        }
    }

    fun updateForm(
        type: String = _formState.value.type,
        title: String = _formState.value.title,
        sourceId: String = _formState.value.sourceId,
        currency: String = _formState.value.currency,
        amountStr: String = _formState.value.amountStr,
        dueDate: String = _formState.value.dueDate,
        note: String = _formState.value.note
    ) {
        if (_formState.value.isLoading || _formState.value.isSubmitting || _formState.value.isRetry) return
        _formState.value = _formState.value.copy(
            type = type,
            title = title,
            sourceId = sourceId,
            currency = currency,
            amountStr = amountStr,
            dueDate = dueDate,
            note = note,
            error = null
        )
    }

    fun savePayment() {
        val state = _formState.value
        if (state.isLoading || state.isSubmitting || state.isSuccess) return
        if (!recoveryReady) {
            _formState.value = state.copy(error = "Saved attempts could not be loaded. Reopen this form before saving.")
            return
        }

        val token = operationToken

        if (state.isRetry && savedPayment != null) {
            val targetPayment = savedPayment!!
            _formState.value = state.copy(isSubmitting = true, error = null)
            viewModelScope.launch {
                try {
                    val user = authRepository.authState.first()
                    if (operationToken != token) return@launch
                    if (user == null || user.uid != currentUserId) {
                        _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed.")
                        return@launch
                    }
                    val result = withTimeoutOrNull(10000L) {
                        paymentRepository.createExpectedPayment(user.uid, targetPayment)
                    }
                    if (operationToken != token) return@launch

                    if (result == null) {
                        _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = "Server confirmation is unknown. Check your connection and retry this same save.")
                    } else if (result.isSuccess) {
                        _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
                    } else {
                        val message = result.exceptionOrNull()?.message ?: "Failed to save"
                        _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (operationToken == token) {
                        _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = e.message ?: "Failed to save")
                    }
                }
            }
            return
        }

        if (state.title.trim().isEmpty()) {
            _formState.value = state.copy(error = "Title cannot be blank")
            return
        }

        if (state.sourceId.trim().isEmpty()) {
            _formState.value = state.copy(error = "Source cannot be blank")
            return
        }

        if (!isValidDate(state.dueDate)) {
            _formState.value = state.copy(error = "Invalid due date format")
            return
        }

        if (state.note.length > 500) {
            _formState.value = state.copy(error = "Note cannot exceed 500 characters")
            return
        }

        val origBdResult = parseAmount(state.amountStr)
        if (origBdResult.isFailure) {
            _formState.value = state.copy(error = origBdResult.exceptionOrNull()?.message)
            return
        }
        val origBd = origBdResult.getOrThrow()
        val agreedMinor = try {
            origBd.multiply(BigDecimal(100)).longValueExact()
        } catch (e: ArithmeticException) {
            _formState.value = state.copy(error = "Amount is too large")
            return
        }

        if (agreedMinor > 100000000000L) {
            _formState.value = state.copy(error = "Amount exceeds maximum limit")
            return
        }

        val targetPayment = ExpectedPayment(
            id = paymentId,
            type = state.type,
            title = state.title.trim(),
            sourceId = state.sourceId.trim(),
            currency = state.currency,
            dueDate = state.dueDate.trim(),
            agreedAmountMinor = agreedMinor,
            allocatedAmountMinor = 0L,
            directReceivedAmountMinor = 0L,
            milestoneReceivedAmountMinor = 0L,
            isCancelled = false,
            note = state.note.trim()
        )

        _formState.value = state.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            try {
                val user = authRepository.authState.first()
                if (operationToken != token) return@launch
                if (user == null || user.uid != currentUserId) {
                    _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed.")
                    return@launch
                }

                val result = withTimeoutOrNull(10000L) {
                    paymentRepository.createExpectedPayment(user.uid, targetPayment)
                }
                if (operationToken != token) return@launch

                if (result == null) {
                    savedPayment = targetPayment
                    _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = "Server confirmation is unknown. Check your connection and retry this same save.")
                } else if (result.isSuccess) {
                    _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
                } else {
                    savedPayment = targetPayment
                    val message = result.exceptionOrNull()?.message ?: "Failed to save"
                    _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationToken == token) {
                    savedPayment = targetPayment
                    _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = e.message ?: "Failed to save")
                }
            }
        }
    }

    private fun parseAmount(amount: String): Result<BigDecimal> {
        return try {
            val trimmed = amount.trim()
            if (trimmed.isEmpty()) return Result.failure(Exception("Amount is required"))
            val bd = BigDecimal(trimmed)
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Amount must be positive"))
            if (bd.scale() > 2) return Result.failure(Exception("Maximum 2 decimal places allowed"))
            if (trimmed.lowercase().contains('e')) return Result.failure(Exception("Scientific notation not allowed"))
            Result.success(bd)
        } catch (e: NumberFormatException) {
            Result.failure(Exception("Invalid amount format"))
        }
    }

    fun getDefaultDate(): String {
        return LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
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
