package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

data class PaymentConfirmationFormState(
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val isRetry: Boolean = false,
    val error: String? = null,

    val isLoading: Boolean = false,
    val paymentId: String = "",
    val milestoneId: String? = null,
    val sourceId: String = "",
    val currency: String = "LKR",

    val amountStr: String = "",
    val conversionBasis: String = "native_lkr",
    val conversionRateStr: String = "",
    val actualLkrCreditStr: String = "",

    val receiptDate: String = "",
    val note: String = ""
)

class PaymentConfirmationViewModel(
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(PaymentConfirmationFormState(receiptDate = getDefaultDate()))
    val formState = _formState.asStateFlow()

    private var maxAmountMinor: Long = 0L

    private var receiptId: String = ""
    private var currentUserId: String = ""
    private var unresolvedIncome: Income? = null
    private var operationToken: Long = 0L

    init {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                val newUid = user?.uid ?: ""
                if (currentUserId.isNotEmpty() && currentUserId != newUid) {
                    operationToken++
                    unresolvedIncome = null
                    currentUserId = newUid
                    if (user == null) {
                        _formState.value = PaymentConfirmationFormState(error = "User not authenticated")
                    } else {
                        _formState.value = PaymentConfirmationFormState(error = "Account changed.")
                    }
                }
            }
        }
    }

    fun load(paymentId: String, milestoneId: String?) {
        val token = ++operationToken
        unresolvedIncome = null
        maxAmountMinor = 0L
        _formState.value = PaymentConfirmationFormState(isLoading = true, paymentId = paymentId, milestoneId = milestoneId, receiptDate = getDefaultDate())
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(15000L) {
            val user = authRepository.authState.first()
            if (operationToken != token) return@withTimeout
            if (user == null) {
                if (operationToken == token) {
                    _formState.value = _formState.value.copy(error = "User not authenticated", isLoading = false)
                }
                return@withTimeout
            }
            if (operationToken != token) return@withTimeout

            currentUserId = user.uid
            val unresolved = paymentRepository.getUnresolvedPayment(user.uid, paymentId, milestoneId)
            if (operationToken != token) return@withTimeout

            if (unresolved != null) {
                unresolvedIncome = unresolved
                receiptId = unresolved.id
                _formState.value = _formState.value.copy(
                    isRetry = true, isLoading = false,
                    amountStr = unresolved.originalAmountStr,
                    sourceId = unresolved.sourceId,
                    currency = unresolved.currency,
                    conversionBasis = unresolved.conversionBasis,
                    conversionRateStr = unresolved.conversionRateStr ?: "",
                    receiptDate = unresolved.receiptDate,
                    actualLkrCreditStr = BigDecimal.valueOf(unresolved.lkrAmountMinor, 2).toPlainString(),
                    note = unresolved.note,
                    error = "Unresolved payment found. Please retry."
                )
                return@withTimeout
            } else {
                unresolvedIncome = null
                receiptId = UUID.randomUUID().toString()
            }

            val result = paymentRepository.getExpectedPayment(user.uid, paymentId)
            if (operationToken != token) return@withTimeout

            if (result.isSuccess) {
                val p = result.getOrThrow()
                check(!p.isCancelled && !p.hasPendingWrites && !p.isFromCache) {
                    p.confirmationMessage ?: "Project is cancelled"
                }
                var title = p.title

                if (milestoneId != null) {
                    val msFlow = paymentRepository.getMilestones(user.uid, paymentId).first()
                    if (operationToken != token) return@withTimeout
                    val ms = msFlow.find { it.id == milestoneId }
                    if (ms != null) {
                        check(!ms.isCancelled && !ms.hasPendingWrites && !ms.isFromCache) {
                            ms.confirmationMessage ?: "Milestone is cancelled"
                        }
                        maxAmountMinor = ms.outstandingAmountMinor
                        title = ms.title
                    } else {
                        if (operationToken == token) {
                            _formState.value = _formState.value.copy(error = "Milestone not found", isLoading = false)
                        }
                        return@withTimeout
                    }
                } else {
                    maxAmountMinor = Math.subtractExact(p.agreedAmountMinor, Math.addExact(p.allocatedAmountMinor, p.directReceivedAmountMinor))
                }

                if (operationToken == token) {
                    _formState.value = _formState.value.copy(
                        isLoading = false,
                        sourceId = unresolved?.sourceId ?: p.sourceId,
                        currency = unresolved?.currency ?: p.currency,
                        amountStr = unresolved?.originalAmountStr ?: _formState.value.amountStr,
                        conversionBasis = unresolved?.conversionBasis ?: (if (p.currency == "LKR") "native_lkr" else "confirmed_rate"),
                        conversionRateStr = unresolved?.conversionRateStr ?: _formState.value.conversionRateStr,
                        receiptDate = unresolved?.receiptDate ?: _formState.value.receiptDate,
                        note = unresolved?.note ?: "Payment for: $title"
                    )
                }
            } else {
                if (operationToken == token) {
                    _formState.value = _formState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Payment not found.")
                }
            }

                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (operationToken == token) _formState.value = _formState.value.copy(
                    isLoading = false, isSubmitting = false, isRetry = unresolvedIncome != null,
                    error = "Server confirmation unavailable. Check your connection, then retry.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationToken == token) _formState.value = _formState.value.copy(
                    isLoading = false, isSubmitting = false, isRetry = unresolvedIncome != null,
                    error = e.message ?: "Unable to contact server")
            }
        }
    }

    fun updateForm(
        amountStr: String = _formState.value.amountStr,
        conversionBasis: String = _formState.value.conversionBasis,
        conversionRateStr: String = _formState.value.conversionRateStr,
        actualLkrCreditStr: String = _formState.value.actualLkrCreditStr,
        receiptDate: String = _formState.value.receiptDate,
        note: String = _formState.value.note
    ) {
        if (_formState.value.isLoading || _formState.value.isSubmitting || _formState.value.isRetry) return
        val currency = _formState.value.currency
        val finalBasis = if (currency == "LKR") "native_lkr" else if (conversionBasis == "native_lkr") "confirmed_rate" else conversionBasis

        _formState.value = _formState.value.copy(
            amountStr = amountStr,
            conversionBasis = finalBasis,
            conversionRateStr = conversionRateStr,
            actualLkrCreditStr = actualLkrCreditStr,
            receiptDate = receiptDate,
            note = note,
            error = null
        )
    }

    fun confirmPayment() {
        val state = _formState.value
        if (state.isSubmitting || state.isLoading || state.isSuccess) return
        if (state.sourceId.isEmpty()) return

        val token = operationToken
        val targetUserId = currentUserId
        val targetPaymentId = state.paymentId
        val targetMilestoneId = state.milestoneId

        if (state.isRetry && unresolvedIncome != null) {
            val targetIncome = unresolvedIncome!!
            _formState.value = state.copy(isSubmitting = true, error = null)
            viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(15000L) {
                if (operationToken != token) return@withTimeout
                val user = authRepository.authState.first()
                if (operationToken != token) return@withTimeout
                if (user == null || user.uid != targetUserId) {
                    if (operationToken == token) {
                        _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed.")
                    }
                    return@withTimeout
                }
                val result = paymentRepository.retryFailedPayment(user.uid, targetPaymentId, targetMilestoneId, targetIncome)
                if (operationToken != token || currentUserId != targetUserId) return@withTimeout
                if (result.isSuccess) {
                    _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Failed to save"
                    _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = message)
                }

                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (operationToken == token) _formState.value = _formState.value.copy(
                    isLoading = false, isSubmitting = false, isRetry = unresolvedIncome != null,
                    error = "Server confirmation unavailable. Check your connection, then retry.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationToken == token) _formState.value = _formState.value.copy(
                    isLoading = false, isSubmitting = false, isRetry = unresolvedIncome != null,
                    error = e.message ?: "Unable to contact server")
            }
        }
            return
        }

        if (!isValidDate(state.receiptDate)) {
            _formState.value = state.copy(error = "Invalid date format or future date.")
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
        val originalMinor = try {
            origBd.multiply(BigDecimal(100)).longValueExact()
        } catch (e: ArithmeticException) {
            _formState.value = state.copy(error = "Amount is too large")
            return
        }

        if (originalMinor > maxAmountMinor) {
            _formState.value = state.copy(error = "Cannot exceed outstanding balance: ${BigDecimal.valueOf(maxAmountMinor, 2).toPlainString()} ${state.currency}")
            return
        }

        var lkrAmountMinor = 0L
        var finalRateStr: String? = null

        try {
            if (state.currency == "LKR") {
                lkrAmountMinor = originalMinor
                finalRateStr = "1"
            } else {
                if (state.conversionBasis == "confirmed_rate") {
                    val rateResult = parseRate(state.conversionRateStr)
                    if (rateResult.isFailure) {
                        _formState.value = state.copy(error = rateResult.exceptionOrNull()?.message)
                        return
                    }
                    val rateBd = rateResult.getOrThrow()
                    lkrAmountMinor = origBd.multiply(rateBd).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    finalRateStr = state.conversionRateStr.trim()
                } else if (state.conversionBasis == "actual_credit") {
                    val actualLkrResult = parseAmount(state.actualLkrCreditStr)
                    if (actualLkrResult.isFailure) {
                        _formState.value = state.copy(error = "Actual LKR Credit: " + actualLkrResult.exceptionOrNull()?.message)
                        return
                    }
                    lkrAmountMinor = actualLkrResult.getOrThrow().multiply(BigDecimal(100)).longValueExact()
                    finalRateStr = null
                }
            }
            if (lkrAmountMinor <= 0L) {
                _formState.value = state.copy(error = "Calculated LKR amount must be greater than 0.")
                return
            }
        } catch (e: ArithmeticException) {
            _formState.value = state.copy(error = "Calculated amount is too large (overflow).")
            return
        }

        val targetIncome = Income(
            id = receiptId,
            sourceId = state.sourceId,
            receiptDate = state.receiptDate,
            currency = state.currency,
            originalAmountStr = state.amountStr.trim(),
            originalAmountMinor = originalMinor,
            lkrAmountMinor = lkrAmountMinor,
            conversionBasis = state.conversionBasis,
            conversionRateStr = finalRateStr,
            note = state.note.trim(),
            linkedPaymentId = state.paymentId,
            linkedMilestoneId = state.milestoneId
        )

        unresolvedIncome = targetIncome
        _formState.value = state.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(15000L) {
            if (operationToken != token) return@withTimeout
            val user = authRepository.authState.first()
            if (operationToken != token) return@withTimeout
            if (user == null || user.uid != targetUserId) {
                if (operationToken == token) {
                    _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed.")
                }
                return@withTimeout
            }

            val result = paymentRepository.confirmPayment(user.uid, targetPaymentId, targetMilestoneId, targetIncome)
            if (operationToken != token || currentUserId != targetUserId) return@withTimeout

            if (result.isSuccess) {
                _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
            } else {
                unresolvedIncome = targetIncome
                val message = result.exceptionOrNull()?.message ?: "Failed to save"
                _formState.value = _formState.value.copy(isSubmitting = false, isRetry = true, error = message)
            }

                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (operationToken == token) _formState.value = _formState.value.copy(
                    isLoading = false, isSubmitting = false, isRetry = unresolvedIncome != null,
                    error = "Server confirmation unavailable. Check your connection, then retry.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operationToken == token) _formState.value = _formState.value.copy(
                    isLoading = false, isSubmitting = false, isRetry = unresolvedIncome != null,
                    error = e.message ?: "Unable to contact server")
            }
        }
    }

    private fun parseAmount(amount: String): Result<BigDecimal> {
        return try {
            val bd = BigDecimal(amount.trim())
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Amount must be positive"))
            if (bd.scale() > 2) return Result.failure(Exception("Maximum 2 decimal places allowed"))
            // Check scientific notation specifically if we want, or just rely on bd parsing
            if (amount.trim().lowercase().contains('e')) return Result.failure(Exception("Scientific notation not allowed"))
            Result.success(bd)
        } catch (e: NumberFormatException) {
            Result.failure(Exception("Invalid amount format"))
        }
    }

    private fun parseRate(rate: String): Result<BigDecimal> {
        return try {
            val bd = BigDecimal(rate.trim())
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Rate must be positive"))
            if (bd.scale() > 8) return Result.failure(Exception("Maximum 8 decimal places allowed"))
            if (rate.trim().lowercase().contains('e')) return Result.failure(Exception("Scientific notation not allowed"))
            Result.success(bd)
        } catch (e: NumberFormatException) {
            Result.failure(Exception("Invalid rate format"))
        }
    }

    fun getDefaultDate(): String {
        return LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
    }

    private fun isValidDate(dateStr: String): Boolean {
        return try {
            val parsed = LocalDate.parse(dateStr)
            val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
            !parsed.isAfter(today)
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
