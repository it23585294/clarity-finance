package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.Income
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.IncomeRepository
import com.google.firebase.Timestamp
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

data class AddEditIncomeFormState(
    val isSubmitting: Boolean = false,
    val isDeleting: Boolean = false,
    val isSuccess: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    val isStale: Boolean = false,
    val isMissing: Boolean = false,
    
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val incomeId: String = "",
    val sourceId: String = "salary",
    val receiptDate: String = "",
    val currency: String = "LKR",
    val originalAmountStr: String = "",
    val conversionBasis: String = "native_lkr",
    val conversionRateStr: String = "",
    val actualLkrCreditStr: String = "", // Used only in UI when actual_credit is selected
    val note: String = ""
) {
    val effectiveRateText: String? get() = try {
        val original = plain(originalAmountStr, 2); val credit = plain(actualLkrCreditStr, 2)
        credit.divide(original, 4, RoundingMode.HALF_UP).toPlainString()
    } catch (_: Exception) { null }
    val previewLkrText: String? get() = try {
        val minor = plain(originalAmountStr, 2).multiply(plain(conversionRateStr, 8)).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(minor > 0); BigDecimal.valueOf(minor, 2).toPlainString()
    } catch (_: Exception) { null }
    private fun plain(text: String, scale: Int): BigDecimal {
        require(text.trim().matches(Regex("[0-9]+(?:\\.[0-9]{1,$scale})?")))
        return BigDecimal(text.trim()).also { require(it.signum() > 0) }
    }
}

class AddEditIncomeViewModel(
    private val authRepository: AuthRepository,
    private val incomeRepository: IncomeRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(AddEditIncomeFormState(receiptDate = getDefaultDate()))
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
                _formState.value = AddEditIncomeFormState(isLoading = false, error = "Account changed. Operation aborted.")
            }
            initialized = true; sessionUid = user?.uid
        }
    } }


    fun loadIncome(incomeId: String) {
        ++generation
        _formState.value = _formState.value.copy(isLoading = true, isEditMode = true, incomeId = incomeId)
        val token = generation
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (token != generation) return@launch
            if (user == null) {
                _formState.value = _formState.value.copy(error = "User not authenticated", isLoading = false)
                return@launch
            }
            loadedUserId = user.uid

            val result = incomeRepository.getIncome(user.uid, incomeId)
            if (token != generation || sessionUid != user.uid) return@launch
            if (result.isSuccess) {
                val income = result.getOrThrow()
                if (income.linkedPaymentId != null) {
                    _formState.value = _formState.value.copy(isLoading = false, isMissing = true, error = "Linked receipt: inspect its project. It cannot be edited or deleted here.")
                    return@launch
                }
                originalUpdatedAt = income.updatedAt
                
                var rateStr = ""
                var actualCreditStr = ""
                if (income.conversionBasis == "confirmed_rate") {
                    rateStr = income.conversionRateStr ?: ""
                } else if (income.conversionBasis == "actual_credit") {
                    actualCreditStr = BigDecimal.valueOf(income.lkrAmountMinor, 2).toPlainString()
                }

                _formState.value = _formState.value.copy(
                    isLoading = false,
                    sourceId = income.sourceId,
                    receiptDate = income.receiptDate,
                    currency = income.currency,
                    originalAmountStr = income.originalAmountStr,
                    conversionBasis = income.conversionBasis,
                    conversionRateStr = rateStr,
                    actualLkrCreditStr = actualCreditStr,
                    note = income.note,
                    isStale = false,
                    isMissing = false,
                    error = null
                )
            } else {
                _formState.value = _formState.value.copy(isLoading = false, isMissing = true, error = "Income not found.")
            }
        }
    }

    fun updateForm(
        sourceId: String = _formState.value.sourceId,
        receiptDate: String = _formState.value.receiptDate,
        currency: String = _formState.value.currency,
        originalAmountStr: String = _formState.value.originalAmountStr,
        conversionBasis: String = _formState.value.conversionBasis,
        conversionRateStr: String = _formState.value.conversionRateStr,
        actualLkrCreditStr: String = _formState.value.actualLkrCreditStr,
        note: String = _formState.value.note
    ) {
        val finalBasis = if (currency == "LKR") "native_lkr" else if (conversionBasis == "native_lkr") "confirmed_rate" else conversionBasis
        
        _formState.value = _formState.value.copy(
            sourceId = sourceId,
            receiptDate = receiptDate,
            currency = currency,
            originalAmountStr = originalAmountStr,
            conversionBasis = finalBasis,
            conversionRateStr = conversionRateStr,
            actualLkrCreditStr = actualLkrCreditStr,
            note = note,
            error = null
        )
    }

    fun saveChanges() {
        if (_formState.value.isSubmitting || _formState.value.isDeleting) return
        val state = _formState.value.let { if (!it.isEditMode && it.incomeId.isEmpty()) it.copy(incomeId = UUID.randomUUID().toString()) else it }

        if (!isValidDate(state.receiptDate)) {
            _formState.value = state.copy(error = "Invalid date format or future date.")
            return
        }

        // Validate Money and conversion
        val origBdResult = parseAmount(state.originalAmountStr)
        if (origBdResult.isFailure) {
            _formState.value = state.copy(error = origBdResult.exceptionOrNull()?.message)
            return
        }
        val origBd = origBdResult.getOrThrow()

        var lkrAmountMinor = 0L
        var finalRateStr: String? = null

        try {
            if (state.currency == "LKR") {
                lkrAmountMinor = origBd.multiply(BigDecimal(100)).longValueExact()
                finalRateStr = "1"
            } else { // USD
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
        } catch (e: Exception) {
            _formState.value = state.copy(error = "Calculation error: ${e.message}")
            return
        }

        if (state.isEditMode && originalUpdatedAt == null) {
            _formState.value = state.copy(error = "Original timestamp missing. Cannot update.")
            return
        }

        _formState.value = state.copy(isSubmitting = true, error = null)

        val token = generation
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (token != generation) return@launch
            if (user == null || (state.isEditMode && user.uid != loadedUserId)) {
                _formState.value = _formState.value.copy(isSubmitting = false, error = "Account changed or not authenticated.")
                return@launch
            }

            val income = Income(
                id = state.incomeId,
                sourceId = state.sourceId,
                receiptDate = state.receiptDate,
                currency = state.currency,
                originalAmountStr = state.originalAmountStr.trim(),
                lkrAmountMinor = lkrAmountMinor,
                conversionBasis = state.conversionBasis,
                conversionRateStr = finalRateStr,
                note = state.note.trim()
            )

            val result = if (state.isEditMode) {
                incomeRepository.updateIncome(user.uid, income, originalUpdatedAt!!)
            } else {
                incomeRepository.enqueueIncome(user.uid, income)
            }

            if (token != generation || sessionUid != user.uid) return@launch
            if (result.isSuccess) {
                _formState.value = _formState.value.copy(isSubmitting = false, isSuccess = true)
            } else {
                val exception = result.exceptionOrNull()
                val message = exception?.message ?: "Failed to save"
                
                if (message.contains("NOT_FOUND", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isSubmitting = false, isMissing = true, error = "Income was deleted elsewhere.")
                } else if (message.contains("ABORTED", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isSubmitting = false, isStale = true, error = "Income was modified elsewhere. Review and reload.")
                } else if (state.isEditMode && (message.contains("offline") || message.contains("network", ignoreCase = true))) {
                    _formState.value = _formState.value.copy(isSubmitting = false, error = "Network required to edit income.")
                } else {
                    _formState.value = _formState.value.copy(isSubmitting = false, error = message)
                }
            }
        }
    }

    fun deleteIncome() {
        if (_formState.value.isSubmitting || _formState.value.isDeleting || !_formState.value.isEditMode) return
        if (originalUpdatedAt == null) {
            _formState.value = _formState.value.copy(error = "Original timestamp missing. Cannot delete.")
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

            val result = incomeRepository.deleteIncome(user.uid, _formState.value.incomeId, originalUpdatedAt!!)
            if (token != generation || sessionUid != user.uid) return@launch
            if (result.isSuccess) {
                _formState.value = _formState.value.copy(isDeleting = false, isDeleted = true)
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to delete"
                if (message.contains("NOT_FOUND", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isDeleting = false, isDeleted = true)
                } else if (message.contains("ABORTED", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isDeleting = false, isStale = true, error = "Income modified elsewhere. Cannot delete. Reload first.")
                } else if (message.contains("offline") || message.contains("network", ignoreCase = true)) {
                    _formState.value = _formState.value.copy(isDeleting = false, error = "Network required to delete income.")
                } else {
                    _formState.value = _formState.value.copy(isDeleting = false, error = message)
                }
            }
        }
    }

    fun resetState() {
        _formState.value = AddEditIncomeFormState(receiptDate = getDefaultDate())
    }

    fun parseAmount(amount: String): Result<BigDecimal> {
        return try {
            require(amount.trim().matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?"))) { "Use a positive plain decimal amount." }
            val bd = BigDecimal(amount.trim())
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Amount must be positive"))
            if (bd.scale() > 2) return Result.failure(Exception("Maximum 2 decimal places allowed"))
            Result.success(bd)
        } catch (e: IllegalArgumentException) {
            Result.failure(Exception("Invalid amount format"))
        }
    }

    private fun parseRate(rate: String): Result<BigDecimal> {
        return try {
            require(rate.trim().matches(Regex("[0-9]+(?:\\.[0-9]{1,8})?"))) { "Use a positive plain decimal rate." }
            val bd = BigDecimal(rate.trim())
            if (bd <= BigDecimal.ZERO) return Result.failure(Exception("Rate must be positive"))
            if (bd.scale() > 8) return Result.failure(Exception("Maximum 8 decimal places allowed"))
            Result.success(bd)
        } catch (e: IllegalArgumentException) {
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
