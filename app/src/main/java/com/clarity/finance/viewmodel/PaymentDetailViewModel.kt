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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PaymentDetailUiState {
    object Loading : PaymentDetailUiState
    data class Success(
        val payment: ExpectedPayment,
        val milestones: List<Milestone>
    ) : PaymentDetailUiState
    data class Error(val message: String) : PaymentDetailUiState
}

class PaymentDetailViewModel(
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _paymentId = MutableStateFlow<String?>(null)
    
    private val _cancelState = MutableStateFlow<String?>(null)
    val cancelError = _cancelState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PaymentDetailUiState> = _paymentId
        .filterNotNull()
        .flatMapLatest { pId ->
            authRepository.authState.flatMapLatest { user ->
                if (user != null) {
                    paymentRepository.getExpectedPayments(user.uid).combine(paymentRepository.getMilestones(user.uid, pId)) { payments, milestones ->
                        val payment = payments.find { it.id == pId }
                        if (payment != null) {
                            PaymentDetailUiState.Success(payment, milestones)
                        } else {
                            PaymentDetailUiState.Error("Payment not found")
                        }
                    }
                } else {
                    flowOf(PaymentDetailUiState.Error("Signed out"))
                }
            }
        }
        .catch { emit(PaymentDetailUiState.Error(it.message ?: "Failed to load details")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PaymentDetailUiState.Loading
        )

    fun load(paymentId: String) {
        _paymentId.value = paymentId
    }

    fun cancelPayment(payment: ExpectedPayment) {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                if (user != null) {
                    val result = paymentRepository.cancelExpectedPayment(user.uid, payment.id, payment.updatedAt!!)
                    if (result.isFailure) {
                        _cancelState.value = result.exceptionOrNull()?.message ?: "Failed to cancel project"
                    }
                }
            }
        }
    }

    fun cancelMilestone(paymentId: String, milestone: Milestone) {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                if (user != null) {
                    val opId = java.util.UUID.randomUUID().toString()
                    val result = paymentRepository.cancelMilestone(user.uid, milestone.id, paymentId, milestone.updatedAt!!, opId)
                    if (result.isFailure) {
                        _cancelState.value = result.exceptionOrNull()?.message ?: "Failed to cancel milestone"
                    }
                }
            }
        }
    }
    
    fun clearError() {
        _cancelState.value = null
    }
}
