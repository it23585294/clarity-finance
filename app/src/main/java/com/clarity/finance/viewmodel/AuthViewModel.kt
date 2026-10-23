package com.clarity.finance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    object Initializing : SessionState
    object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

data class FormState(
    val isSubmitting: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.authState
        .map { user ->
            if (user != null) SessionState.SignedIn(user)
            else SessionState.SignedOut
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SessionState.Initializing
        )

    private val _loginFormState = MutableStateFlow(FormState())
    val loginFormState = _loginFormState.asStateFlow()

    private val _registerFormState = MutableStateFlow(FormState())
    val registerFormState = _registerFormState.asStateFlow()

    fun clearErrors() {
        _loginFormState.value = _loginFormState.value.copy(error = null)
        _registerFormState.value = _registerFormState.value.copy(error = null)
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // A pure kotlin fallback for unit tests if android.util is mocked loosely, 
    // but typically we can handle tests by injecting a validator or mocking. 
    // Here we'll stick to a simple Regex so it's fully testable without Robolectric.
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
    private fun isEmailValid(email: String): Boolean = emailPattern.matches(email)

    fun login(emailInput: String, passwordInput: String) {
        if (_loginFormState.value.isSubmitting) return

        val email = emailInput.trim()
        val password = passwordInput // Do not trim password

        if (email.isBlank() || password.isEmpty()) {
            _loginFormState.value = _loginFormState.value.copy(error = "Please enter both email and password.")
            return
        }

        if (!isEmailValid(email)) {
            _loginFormState.value = _loginFormState.value.copy(error = "Invalid email format.")
            return
        }

        _loginFormState.value = FormState(isSubmitting = true)

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isFailure) {
                // Return a generic error per requirements
                _loginFormState.value = FormState(
                    isSubmitting = false,
                    error = "Email or password is incorrect."
                )
            } else {
                _loginFormState.value = FormState()
            }
        }
    }

    fun register(emailInput: String, passwordInput: String, confirmPasswordInput: String) {
        if (_registerFormState.value.isSubmitting) return

        val email = emailInput.trim()
        val password = passwordInput
        val confirmPassword = confirmPasswordInput

        if (email.isBlank() || password.isEmpty() || confirmPassword.isEmpty()) {
            _registerFormState.value = _registerFormState.value.copy(error = "Please fill in all fields.")
            return
        }

        if (!isEmailValid(email)) {
            _registerFormState.value = _registerFormState.value.copy(error = "Invalid email format.")
            return
        }

        if (password.length < 6) {
            _registerFormState.value = _registerFormState.value.copy(error = "Password must be at least 6 characters.")
            return
        }

        if (password != confirmPassword) {
            _registerFormState.value = _registerFormState.value.copy(error = "Passwords do not match.")
            return
        }

        _registerFormState.value = FormState(isSubmitting = true)

        viewModelScope.launch {
            val result = authRepository.register(email, password)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                val message = exception?.message ?: "Registration failed."
                // Simplify or map Firebase errors into readable ones
                val readableMessage = when {
                    message.contains("email address is already in use", ignoreCase = true) -> "An account with this email already exists."
                    message.contains("network", ignoreCase = true) -> "Network error. Please check your connection."
                    message.contains("too many requests", ignoreCase = true) -> "Too many attempts. Please try again later."
                    else -> "Registration failed. Please try again."
                }
                _registerFormState.value = FormState(
                    isSubmitting = false,
                    error = readableMessage
                )
            } else {
                _registerFormState.value = FormState()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
