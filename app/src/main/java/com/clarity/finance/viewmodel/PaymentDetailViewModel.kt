package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.model.Milestone
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

sealed interface PaymentDetailUiState {
    object Loading : PaymentDetailUiState
    object SignedOut : PaymentDetailUiState
    data class Success(
        val payment: ExpectedPayment,
        val milestones: List<Milestone>,
        val ownerUid: String = ""
    ) : PaymentDetailUiState
    data class Error(val message: String) : PaymentDetailUiState
}

data class AddMilestoneState(
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val retryMilestone: Milestone? = null
)

class PaymentDetailViewModel(
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _paymentId = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0L)
    fun retryLoad() { refresh.value++ }
    
    private val _cancelState = MutableStateFlow<String?>(null)
    val cancelError = _cancelState.asStateFlow()

    private val _milestoneState = MutableStateFlow(AddMilestoneState())
    val milestoneState = _milestoneState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PaymentDetailUiState> = _paymentId
        .filterNotNull()
        .flatMapLatest { pId ->
            authRepository.authState.combine(refresh) { user, _ -> user }.flatMapLatest { user ->
                if (user != null) {
                    paymentRepository.getExpectedPayments(user.uid).combine(paymentRepository.getMilestones(user.uid, pId)) { payments, milestones ->
                        val payment = payments.find { it.id == pId }
                        if (payment != null) {
                            PaymentDetailUiState.Success(payment, milestones, user.uid)
                        } else {
                            PaymentDetailUiState.Error("Payment not found")
                        }
                    }.onStart { emit(PaymentDetailUiState.Loading) }
                    .catch { emit(PaymentDetailUiState.Error(it.message ?: "Unable to load project")) }
                } else {
                    flowOf(PaymentDetailUiState.SignedOut)
                }
            }
        }
        .catch { emit(PaymentDetailUiState.Error(it.message ?: "Failed to load details")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PaymentDetailUiState.Loading
        )

    private var pendingMilestone: Milestone? = null
    private var pendingMilestoneOpId: String? = null
    private var pendingMilestoneOwner: String? = null
    private var generation = 0L
    private val _isCancelling = MutableStateFlow(false)
    val isCancelling = _isCancelling.asStateFlow()
    init {
        viewModelScope.launch {
            authRepository.authState.distinctUntilChangedBy { it?.uid }.collect {
                generation++
                _isCancelling.value = false
                pendingMilestone = null
                pendingMilestoneOpId = null
                pendingMilestoneOwner = null
                _milestoneState.value = AddMilestoneState()
                _cancelState.value = null
            }
        }
    }

    fun load(paymentId: String) {
        if (_paymentId.value != paymentId) {
            generation++
            _isCancelling.value = false
            pendingMilestone = null
            pendingMilestoneOpId = null
            pendingMilestoneOwner = null
            _milestoneState.value = AddMilestoneState()
            _cancelState.value = null
        }
        _paymentId.value = paymentId
    }

    fun createMilestone(payment: ExpectedPayment, title: String, amountStr: String, dueDate: String) {
        if (_milestoneState.value.isSubmitting) return
        val displayed = uiState.value as? PaymentDetailUiState.Success ?: return
        if (displayed.payment.id != payment.id) return
        payment.confirmationMessage?.let {
            _milestoneState.value = AddMilestoneState(error = it)
            return
        }
        if (payment.isCancelled || payment.type != "project") {
            _milestoneState.value = AddMilestoneState(error = "Select an active project")
            return
        }
        if (pendingMilestone != null && pendingMilestoneOwner == displayed.ownerUid) {
            submitMilestone(displayed.ownerUid, pendingMilestone!!, pendingMilestoneOpId!!)
            return
        }

        val token = generation
        _milestoneState.value = AddMilestoneState(isSubmitting = true)
        viewModelScope.launch {
            try {
                val restored = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    check(authRepository.authState.first()?.uid == displayed.ownerUid) { "Account changed" }
                    paymentRepository.getUnresolvedMilestoneCreation(displayed.ownerUid, payment.id) to true
                }
                if (generation != token) return@launch
                if (restored == null) {
                    _milestoneState.value = AddMilestoneState(error = "Unable to check saved attempts. Check your connection and retry.")
                } else if (restored.first != null) {
                    val original = restored.first!!
                    check(original.paymentId == payment.id) { "Saved milestone target mismatch" }
                    val operation = checkNotNull(original.lastMilestoneOpId) { "Saved milestone operation ID is missing" }
                    pendingMilestone = original
                    pendingMilestoneOwner = displayed.ownerUid
                    pendingMilestoneOpId = operation
                    _milestoneState.value = AddMilestoneState(retryMilestone = original,
                        error = "An earlier creation is unresolved. Retry the saved milestone before adding another.")
                } else {
                    createNewMilestone(payment, title, amountStr, dueDate, displayed.ownerUid)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == token) _milestoneState.value = AddMilestoneState(error = e.message ?: "Unable to check saved attempts")
            }
        }
    }

    private fun createNewMilestone(payment: ExpectedPayment, title: String, amountStr: String, dueDate: String, owner: String) {
        if (title.trim().isEmpty()) {
            _milestoneState.value = AddMilestoneState(error = "Title cannot be blank")
            return
        }

        if (!isValidDate(dueDate)) {
            _milestoneState.value = AddMilestoneState(error = "Invalid due date format")
            return
        }

        val origBdResult = parseAmount(amountStr)
        if (origBdResult.isFailure) {
            _milestoneState.value = AddMilestoneState(error = origBdResult.exceptionOrNull()?.message)
            return
        }
        val origBd = origBdResult.getOrThrow()
        val agreedMinor = try {
            origBd.multiply(BigDecimal(100)).longValueExact()
        } catch (e: ArithmeticException) {
            _milestoneState.value = AddMilestoneState(error = "Amount is too large")
            return
        }

        if (agreedMinor > 100000000000L || agreedMinor > payment.unallocatedAmountMinor) {
            val maxStr = BigDecimal.valueOf(payment.unallocatedAmountMinor, 2).toPlainString()
            _milestoneState.value = AddMilestoneState(error = "Amount exceeds unallocated balance ($maxStr ${payment.currency})")
            return
        }

        val milestoneId = java.util.UUID.randomUUID().toString()
        val opId = java.util.UUID.randomUUID().toString()

        val milestone = Milestone(
            id = milestoneId,
            paymentId = payment.id,
            title = title.trim(),
            dueDate = dueDate.trim(),
            agreedAmountMinor = agreedMinor,
            receivedAmountMinor = 0L,
            isCancelled = false
        )

        pendingMilestone = milestone
        pendingMilestoneOpId = opId
        pendingMilestoneOwner = owner
        submitMilestone(owner, milestone, opId)
    }

    private fun submitMilestone(owner: String, milestone: Milestone, opId: String) {
        val token = generation
        _milestoneState.value = AddMilestoneState(isSubmitting = true, retryMilestone = milestone)
        viewModelScope.launch {
            try {
                val user = authRepository.authState.first()
                if (generation != token) return@launch
                check(user?.uid == owner) { "Account changed" }
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    val restored = paymentRepository.getUnresolvedMilestoneCreation(owner, milestone.paymentId)
                    val target = restored ?: milestone
                    val operation = if (restored != null) checkNotNull(restored.lastMilestoneOpId) { "Saved milestone operation ID is missing" } else opId
                    if (generation != token) return@withTimeoutOrNull Result.failure<Unit>(IllegalStateException("Account changed"))
                    pendingMilestone = target
                    pendingMilestoneOpId = operation
                    if (restored != null && restored.id != milestone.id) {
                        Result.failure(IllegalStateException("An earlier creation is unresolved: ${restored.title}, ${BigDecimal.valueOf(restored.agreedAmountMinor, 2).toPlainString()}. Tap Add again to reconcile that original milestone first."))
                    } else paymentRepository.createMilestone(owner, target, operation)
                }
                if (generation != token) return@launch
                if (result?.isSuccess == true) {
                    pendingMilestone = null
                    pendingMilestoneOpId = null
                    pendingMilestoneOwner = null
                    _milestoneState.value = AddMilestoneState(isSuccess = true)
                } else {
                    _milestoneState.value = AddMilestoneState(retryMilestone = pendingMilestone, error =
                        (result?.exceptionOrNull()?.message ?: "Confirmation unknown. Check connection and retry.") +
                        " Retry uses the original milestone details.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == token) _milestoneState.value = AddMilestoneState(retryMilestone = pendingMilestone, error = e.message ?: "Unable to create milestone")
            }
        }
    }

    fun resetMilestoneState() {
        if (_milestoneState.value.isSubmitting) return
        _milestoneState.value = AddMilestoneState(retryMilestone = pendingMilestone)
    }

    fun cancelPayment(payment: ExpectedPayment) {
        if (_isCancelling.value) return
        val token = generation
        _isCancelling.value = true
        viewModelScope.launch {
            var targetOwnerUid: String? = null
            var targetPaymentId: String? = null
            try {
                val user = authRepository.authState.first()
                if (token != generation) return@launch
                val currentSuccess = uiState.value as? PaymentDetailUiState.Success
                if (user == null || currentSuccess == null || currentSuccess.ownerUid != user.uid || currentSuccess.payment.id != payment.id) {
                    _cancelState.value = "Account changed"
                    return@launch
                }
                targetOwnerUid = currentSuccess.ownerUid
                targetPaymentId = currentSuccess.payment.id

                if (payment.hasPendingWrites || payment.isFromCache) {
                    _cancelState.value = payment.confirmationMessage
                    return@launch
                }
                val updatedAt = payment.updatedAt
                if (updatedAt == null) {
                    _cancelState.value = payment.confirmationMessage
                    return@launch
                }
                val result = kotlinx.coroutines.withTimeout(15000L) { paymentRepository.cancelExpectedPayment(user.uid, payment.id, updatedAt) }
                if (token != generation) return@launch
                val endSuccess = uiState.value as? PaymentDetailUiState.Success
                if (endSuccess == null || endSuccess.ownerUid != targetOwnerUid || endSuccess.payment.id != targetPaymentId) {
                    return@launch
                }
                if (result.isFailure) {
                    _cancelState.value = result.exceptionOrNull()?.message ?: "Failed to cancel project"
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                val current = uiState.value as? PaymentDetailUiState.Success
                if (token == generation && current?.ownerUid == targetOwnerUid && current?.payment?.id == targetPaymentId)
                    _cancelState.value = "Cancellation outcome unknown. Reconnect and refresh before trying again."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val endSuccess = uiState.value as? PaymentDetailUiState.Success
                if (token == generation && targetOwnerUid != null && endSuccess != null && endSuccess.ownerUid == targetOwnerUid && endSuccess.payment.id == targetPaymentId) {
                    _cancelState.value = e.message ?: "Error"
                }
            } finally {
                if (token == generation) _isCancelling.value = false
            }
        }
    }

    fun cancelMilestone(paymentId: String, milestone: Milestone) {
        if (_isCancelling.value) return
        val token = generation
        _isCancelling.value = true
        viewModelScope.launch {
            var targetOwnerUid: String? = null
            var targetPaymentId: String? = null
            try {
                val user = authRepository.authState.first()
                if (token != generation) return@launch
                val currentSuccess = uiState.value as? PaymentDetailUiState.Success
                if (user == null || currentSuccess == null || currentSuccess.ownerUid != user.uid || currentSuccess.payment.id != paymentId) {
                    _cancelState.value = "Account changed"
                    return@launch
                }
                targetOwnerUid = currentSuccess.ownerUid
                targetPaymentId = currentSuccess.payment.id

                if (milestone.paymentId != paymentId) {
                    _cancelState.value = "Milestone belongs to another project"
                    return@launch
                }
                if (currentSuccess.payment.confirmationMessage != null || milestone.hasPendingWrites || milestone.isFromCache) {
                    _cancelState.value = currentSuccess.payment.confirmationMessage ?: milestone.confirmationMessage
                    return@launch
                }
                val updatedAt = milestone.updatedAt
                if (updatedAt == null) {
                    _cancelState.value = milestone.confirmationMessage
                    return@launch
                }
                val opId = java.util.UUID.randomUUID().toString()
                val result = kotlinx.coroutines.withTimeout(15000L) { paymentRepository.cancelMilestone(user.uid, milestone.id, paymentId, updatedAt, opId) }
                if (token != generation) return@launch
                val endSuccess = uiState.value as? PaymentDetailUiState.Success
                if (endSuccess == null || endSuccess.ownerUid != targetOwnerUid || endSuccess.payment.id != targetPaymentId) {
                    return@launch
                }
                if (result.isFailure) {
                    _cancelState.value = result.exceptionOrNull()?.message ?: "Failed to cancel milestone"
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                val current = uiState.value as? PaymentDetailUiState.Success
                if (token == generation && current?.ownerUid == targetOwnerUid && current?.payment?.id == targetPaymentId)
                    _cancelState.value = "Cancellation outcome unknown. Reconnect and refresh before trying again."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val endSuccess = uiState.value as? PaymentDetailUiState.Success
                if (token == generation && targetOwnerUid != null && endSuccess != null && endSuccess.ownerUid == targetOwnerUid && endSuccess.payment.id == targetPaymentId) {
                    _cancelState.value = e.message ?: "Error"
                }
            } finally {
                if (token == generation) _isCancelling.value = false
            }
        }
    }
    
    fun clearError() {
        _cancelState.value = null
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

    private fun getDefaultDate(): String {
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
