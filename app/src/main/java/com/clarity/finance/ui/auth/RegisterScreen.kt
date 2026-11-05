package com.clarity.finance.ui.auth

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onNavigateToLogin: () -> Unit) {
    val formState by viewModel.registerFormState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { password = ""; confirmPassword = ""; viewModel.clearErrors() }
    }
    AuthFormContent(true, email, { email = it }, password, { password = it }, confirmPassword, { confirmPassword = it },
        passwordVisible, { passwordVisible = !passwordVisible }, formState,
        onSubmit = { viewModel.register(email, password, confirmPassword) }, onAlternate = onNavigateToLogin)
}
