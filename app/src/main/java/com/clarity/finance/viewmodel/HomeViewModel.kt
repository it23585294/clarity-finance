package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    object Loading : HomeUiState
    object SignedOut : HomeUiState
    data class SignedIn(val user: User) : HomeUiState
}

class HomeViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = authRepository.authState
        .map { user ->
            if (user != null) HomeUiState.SignedIn(user)
            else HomeUiState.SignedOut
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
