package com.clarity.finance.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.pilot.StatusPanel
import com.clarity.finance.viewmodel.FormState

/** Presentation only. Passwords remain in the caller's non-saveable composition state. */
@Composable
internal fun AuthFormContent(register: Boolean, email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit, confirm: String = "", onConfirm: (String) -> Unit = {},
    visible: Boolean, onVisibility: () -> Unit, state: FormState, onSubmit: () -> Unit, onAlternate: () -> Unit) {
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("auth-form")
            .verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clarity Finance", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (register) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge)
                Text(if (register) "Keep your recorded finances in one place." else "Sign in to your account.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(email, onEmail, singleLine = true, label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, onPassword, singleLine = true, label = { Text("Password") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }, onDone = { onSubmit() }),
                trailingIcon = { TextButton(onClick = onVisibility,
                    modifier = Modifier.semantics { contentDescription = if (visible) "Hide password" else "Show password" }) {
                    Text(if (visible) "Hide" else "Show")
                } },
                supportingText = if (register) ({ Text("At least 6 characters") }) else null,
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus))
            if (register) OutlinedTextField(confirm, onConfirm, singleLine = true, label = { Text("Confirm Password") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }), modifier = Modifier.fillMaxWidth().focusRequester(confirmFocus))
            state.error?.let { error -> Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { StatusPanel(error, isError = true) } }
            Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()
                .semantics { if (state.isSubmitting) stateDescription = if (register) "Creating account" else "Signing in" }) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (register) "Create Account" else "Sign In")
            }
            TextButton(onClick = onAlternate, modifier = Modifier.fillMaxWidth()) {
                Text(if (register) "Back to sign in" else "Create an account")
            }
        }
    }
}
