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
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.viewmodel.ExpenseListViewModel
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

    val startDestination = if (sessionState is SessionState.SignedIn) "home" else "login"

    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.SignedIn -> {
                if (navController.currentDestination?.route != "home" && navController.currentDestination?.route != "dashboard" && navController.currentDestination?.route != "income") {
                    navController.navigate("home") {
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
        composable("home") {
            val user = (sessionState as? SessionState.SignedIn)?.user
            if (user != null) {
                val homeViewModel: com.clarity.finance.viewmodel.HomeViewModel = viewModel(factory = viewModelFactory)
                com.clarity.finance.ui.HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToExpenses = { navController.navigate("dashboard") },
                    onNavigateToIncome = { navController.navigate("income") },
                    onNavigateToPendingPayments = { navController.navigate("pendingPayments") }
                )
            }
        }
        composable("pendingPayments") {
            val paymentListViewModel: com.clarity.finance.viewmodel.PaymentListViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.payments.ExpectedPaymentsScreen(
                viewModel = paymentListViewModel,
                onNavigateToProjectDetail = { id -> navController.navigate("projectDetail/$id") },
                onNavigateToAddPayment = { navController.navigate("addPayment") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("addPayment") {
            val addPaymentViewModel: com.clarity.finance.viewmodel.AddPaymentViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.payments.AddPaymentScreen(
                viewModel = addPaymentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "projectDetail/{paymentId}",
            arguments = listOf(androidx.navigation.navArgument("paymentId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val paymentId = backStackEntry.arguments?.getString("paymentId") ?: return@composable
            val paymentDetailViewModel: com.clarity.finance.viewmodel.PaymentDetailViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.payments.ProjectDetailScreen(
                viewModel = paymentDetailViewModel,
                paymentId = paymentId,
                onNavigateToConfirmation = { pId, msId ->
                    if (msId != null) {
                        navController.navigate("confirmPayment/${android.net.Uri.encode(pId)}?milestoneId=${android.net.Uri.encode(msId)}")
                    } else {
                        navController.navigate("confirmPayment/${android.net.Uri.encode(pId)}")
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "confirmPayment/{paymentId}?milestoneId={milestoneId}",
            arguments = listOf(
                androidx.navigation.navArgument("paymentId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("milestoneId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val paymentId = backStackEntry.arguments?.getString("paymentId") ?: return@composable
            val milestoneId = backStackEntry.arguments?.getString("milestoneId")
            val confirmationViewModel: com.clarity.finance.viewmodel.PaymentConfirmationViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.payments.PaymentConfirmationScreen(
                viewModel = confirmationViewModel,
                paymentId = paymentId,
                milestoneId = milestoneId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("income") {
            val incomeListViewModel: com.clarity.finance.viewmodel.IncomeListViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.income.IncomeListScreen(
                viewModel = incomeListViewModel,
                onNavigateToAddIncome = { navController.navigate("addEditIncome/new") },
                onNavigateToEditIncome = { id -> navController.navigate("addEditIncome/$id") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "addEditIncome/{incomeId}",
            arguments = listOf(androidx.navigation.navArgument("incomeId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val incomeId = backStackEntry.arguments?.getString("incomeId")
            val effectiveId = if (incomeId == "new") null else incomeId
            val addEditIncomeViewModel: com.clarity.finance.viewmodel.AddEditIncomeViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.income.AddEditIncomeScreen(
                viewModel = addEditIncomeViewModel,
                incomeId = effectiveId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("dashboard") {
            val user = (sessionState as? SessionState.SignedIn)?.user
            if (user != null) {
                val expenseListViewModel: ExpenseListViewModel = viewModel(factory = viewModelFactory)
                com.clarity.finance.ui.expenses.ExpensesScreen(
                    viewModel = expenseListViewModel,
                    onNavigateToAddExpense = {
                        navController.navigate("addExpense")
                    },
                    onNavigateToEditExpense = { expenseId ->
                        navController.navigate("editExpense/$expenseId")
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable("addExpense") {
            val addExpenseViewModel: com.clarity.finance.viewmodel.AddExpenseViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.expenses.AddExpenseScreen(
                viewModel = addExpenseViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "editExpense/{expenseId}",
            arguments = listOf(androidx.navigation.navArgument("expenseId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId") ?: return@composable
            val editExpenseViewModel: com.clarity.finance.viewmodel.EditExpenseViewModel = viewModel(factory = viewModelFactory)
            com.clarity.finance.ui.expenses.EditExpenseScreen(
                viewModel = editExpenseViewModel,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
