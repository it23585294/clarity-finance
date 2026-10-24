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

    fun load(paymentId: String, milestoneId: String?) {
        _formState.value = _formState.value.copy(isLoading = true, paymentId = paymentId, milestoneId = milestoneId)
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (user == null) {
                _formState.value = _formState.value.copy(error = "User not authenticated", isLoading = false)
                return@launch
            }

            val result = paymentRepository.getExpectedPayment(user.uid, paymentId)
            if (result.isSuccess) {
                val p = result.getOrThrow()
                var title = p.title
                
                if (milestoneId != null) {
                    val msFlow = paymentRepository.getMilestones(user.uid, paymentId).first()
                    val ms = msFlow.find { it.id == milestoneId }
                    if (ms != null) {
                        maxAmountMinor = ms.outstandingAmountMinor
                        title = ms.title
                    } else {
                        _formState.value = _formState.value.copy(error = "Milestone not found", isLoading = false)
                        return@launch
                    }
                } else {
                    maxAmountMinor = Math.subtractExact(p.agreedAmountMinor, Math.addExact(p.allocatedAmountMinor, p.directReceivedAmountMinor))
                }

                _formState.value = _formState.value.copy(
                    isLoading = false,
                    sourceId = p.sourceId,
                    currency = p.currency,
                    conversionBasis = if (p.currency == "LKR") "native_lkr" else "confirmed_rate",
                    note = "Payment for: $title"
                )
            } else {
                _formState.value = _formState.value.copy(isLoading = false, error = "Payment not found.")
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
        if (_formState.value.isSubmitting) return
        val state = _formState.value

        if (!isValidDate(state.receiptDate)) {
            _formState.value = state.copy(error = "Invalid date format or future date.")
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
            _formState.value = state.copy(error = "Cannot exceed outstanding balance: ${maxAmountMinor / 100.0} ${state.currency}")
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

        _formState.value = state.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (user == null) {
                _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed.")
                return@launch
            }

            val income = Income(
                id = UUID.randomUUID().toString(),
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

            val result = paymentRepository.confirmPayment(user.uid, state.paymentId, state.milestoneId, income)

            if (result.isSuccess) {
                _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to save"
                _formState.value = _formState.value.copy(isSubmitting = false, error = message)
            }
        }
    }

    private fun parseAmount(amount: String): Result<BigDecimal> {
        return try {
            val bd = BigDecimal(amount.trim())
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Amount must be positive"))
            if (bd.scale() > 2) return Result.failure(Exception("Maximum 2 decimal places allowed"))
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
