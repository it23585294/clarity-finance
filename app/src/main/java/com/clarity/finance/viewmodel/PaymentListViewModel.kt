package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.ExpectedPayment
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.PaymentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface PaymentListUiState {
    object Loading : PaymentListUiState
    object SignedOut : PaymentListUiState
    data class Success(val payments: List<ExpectedPayment>) : PaymentListUiState
    data class Error(val message: String) : PaymentListUiState
}

class PaymentListViewModel(
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {
    private val refresh = MutableStateFlow(0L)
    fun retryLoad() { refresh.value++ }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PaymentListUiState> = authRepository.authState
        .combine(refresh) { user, _ -> user }
        .flatMapLatest { user ->
            if (user != null) {
                paymentRepository.getExpectedPayments(user.uid)
                    .map<List<ExpectedPayment>, PaymentListUiState> { PaymentListUiState.Success(it) }
                    .onStart { emit(PaymentListUiState.Loading) }
                    .catch { emit(PaymentListUiState.Error(it.message ?: "Unable to load payments")) }
            } else {
                flowOf(PaymentListUiState.SignedOut)
            }
        }
        .catch { emit(PaymentListUiState.Error(it.message ?: "Failed to load payments")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PaymentListUiState.Loading
        )
}
