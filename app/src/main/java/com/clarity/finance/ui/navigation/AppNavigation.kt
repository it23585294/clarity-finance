package com.clarity.finance.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarity.finance.di.ViewModelFactory
import com.clarity.finance.ui.auth.LoginScreen
import com.clarity.finance.ui.auth.RegisterScreen
import com.clarity.finance.ui.dashboard.DashboardScreen
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.viewmodel.SessionState

@Composable
fun AppNavigation(viewModelFactory: ViewModelFactory) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()

    if (sessionState is SessionState.Initializing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Determine initial route based on auth state
    val startDestination = if (sessionState is SessionState.SignedIn) "dashboard" else "login"

    // Listen to session state changes to enforce redirection and clear backstack
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.SignedIn -> {
                if (navController.currentDestination?.route != "dashboard") {
                    navController.navigate("dashboard") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is SessionState.SignedOut -> {
                if (navController.currentDestination?.route != "login" && navController.currentDestination?.route != "register") {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }
        composable("register") {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
        composable("dashboard") {
            val user = (sessionState as? SessionState.SignedIn)?.user
            if (user != null) {
                DashboardScreen(
                    user = user,
                    onSignOut = { authViewModel.logout() }
                )
            }
        }
    }
}
