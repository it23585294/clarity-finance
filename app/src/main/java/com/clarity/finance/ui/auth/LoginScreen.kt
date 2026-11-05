package com.clarity.finance.ui.auth

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clarity.finance.viewmodel.AuthViewModel

@Composable
fun LoginScreen(viewModel: AuthViewModel, onNavigateToRegister: () -> Unit) {
    val formState by viewModel.loginFormState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { password = ""; viewModel.clearErrors() }
    }
    AuthFormContent(false, email, { email = it }, password, { password = it },
        visible = passwordVisible, onVisibility = { passwordVisible = !passwordVisible }, state = formState,
        onSubmit = { viewModel.login(email, password) }, onAlternate = onNavigateToRegister)
}
