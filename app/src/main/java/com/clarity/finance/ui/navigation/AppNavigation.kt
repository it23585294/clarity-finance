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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarity.finance.di.injectedViewModel
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import com.clarity.finance.ui.auth.LoginScreen
import com.clarity.finance.ui.auth.RegisterScreen
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.viewmodel.ExpenseListViewModel
import com.clarity.finance.viewmodel.SessionState

@Composable
fun AppNavigation(graph: Koin = GlobalContext.get()) {
    val catalogueViewModel: com.clarity.finance.viewmodel.CatalogueViewModel = injectedViewModel(graph)
    val catalogueState by catalogueViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = injectedViewModel(graph)
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

    androidx.compose.runtime.CompositionLocalProvider(com.clarity.finance.ui.LocalCatalogue provides catalogueState.catalogue) {
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
                val homeViewModel: com.clarity.finance.viewmodel.HomeViewModel = injectedViewModel(graph)
                com.clarity.finance.ui.HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToExpenses = { navController.navigate("dashboard") },
                    onNavigateToIncome = { navController.navigate("income") },
                    onNavigateToPendingPayments = { navController.navigate("pendingPayments") },
                    onNavigateToCrypto = { navController.navigate("cryptoResults") },
                    onNavigateToDashboard = { navController.navigate("financialDashboard") },
                    onNavigateToGoal = { navController.navigate("savingsGoal") },
                    onNavigateToRecurring = { navController.navigate("recurringExpenses") },
                    onNavigateToCatalogue = { navController.navigate("catalogue") }
                )
            }
        }
        composable("catalogue") { com.clarity.finance.ui.CatalogueScreen(catalogueViewModel) { navController.popBackStack() } }
        composable("financialDashboard") {
            val dashboardViewModel: com.clarity.finance.viewmodel.DashboardViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.dashboard.DashboardScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate("addExpense") },
                onExpense = { navController.navigate("editExpense/${android.net.Uri.encode(it)}") },
                onIncome = { navController.navigate("addEditIncome/${android.net.Uri.encode(it)}") },
                onProject = { navController.navigate("projectDetail/${android.net.Uri.encode(it)}") },
                onCrypto = { navController.navigate("cryptoResults") },
                onPayments = { navController.navigate("pendingPayments") },
                goalCard = {
                    val goalCardViewModel: com.clarity.finance.viewmodel.GoalCardViewModel = injectedViewModel(graph)
                    com.clarity.finance.ui.goals.GoalCard(goalCardViewModel) { navController.navigate("savingsGoal") }
                }
            )
        }
        composable("recurringExpenses") {
            val recurringViewModel: com.clarity.finance.viewmodel.RecurringViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.recurring.RecurringScreen(recurringViewModel,
                onBack = { navController.popBackStack() },
                onExpense = { navController.navigate("editExpense/${android.net.Uri.encode(it)}") })
        }
        composable("savingsGoal") {
            val goalViewModel: com.clarity.finance.viewmodel.GoalViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.goals.GoalScreen(goalViewModel) { navController.popBackStack() }
        }
        composable("cryptoResults") {
            val cryptoViewModel: com.clarity.finance.viewmodel.CryptoViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.crypto.CryptoScreen(cryptoViewModel, onBack = { navController.popBackStack() })
        }
        composable("pendingPayments") {
            val paymentListViewModel: com.clarity.finance.viewmodel.PaymentListViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.payments.ExpectedPaymentsScreen(
                viewModel = paymentListViewModel,
                onRecovery = { navController.navigate("catalogue") },
                onNavigateToProjectDetail = { id -> navController.navigate("projectDetail/$id") },
                onNavigateToAddPayment = { navController.navigate("addPayment") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("addPayment") {
            val addPaymentViewModel: com.clarity.finance.viewmodel.AddPaymentViewModel = injectedViewModel(graph)
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
            val paymentDetailViewModel: com.clarity.finance.viewmodel.PaymentDetailViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.payments.ProjectDetailScreen(
                viewModel = paymentDetailViewModel,
                onRecovery = { navController.navigate("catalogue") },
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
            val confirmationViewModel: com.clarity.finance.viewmodel.PaymentConfirmationViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.payments.PaymentConfirmationScreen(
                viewModel = confirmationViewModel,
                paymentId = paymentId,
                milestoneId = milestoneId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("income") {
            val incomeListViewModel: com.clarity.finance.viewmodel.IncomeListViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.income.IncomeListScreen(
                viewModel = incomeListViewModel,
                onNavigateToAddIncome = { navController.navigate("addEditIncome/new") },
                onRecovery = { navController.navigate("catalogue") },
                onNavigateToEditIncome = { id -> navController.navigate("addEditIncome/${android.net.Uri.encode(id)}") },
                onNavigateToProject = { id -> navController.navigate("projectDetail/${android.net.Uri.encode(id)}") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "addEditIncome/{incomeId}",
            arguments = listOf(androidx.navigation.navArgument("incomeId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val incomeId = backStackEntry.arguments?.getString("incomeId")
            val effectiveId = if (incomeId == "new") null else incomeId
            val addEditIncomeViewModel: com.clarity.finance.viewmodel.AddEditIncomeViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.income.AddEditIncomeScreen(
                viewModel = addEditIncomeViewModel,
                incomeId = effectiveId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("dashboard") {
            val user = (sessionState as? SessionState.SignedIn)?.user
            if (user != null) {
                val expenseListViewModel: ExpenseListViewModel = injectedViewModel(graph)
                com.clarity.finance.ui.expenses.ExpensesScreen(
                    viewModel = expenseListViewModel,
                    onRecovery = { navController.navigate("catalogue") },
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
            val addExpenseViewModel: com.clarity.finance.viewmodel.AddExpenseViewModel = injectedViewModel(graph)
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
            val editExpenseViewModel: com.clarity.finance.viewmodel.EditExpenseViewModel = injectedViewModel(graph)
            com.clarity.finance.ui.expenses.EditExpenseScreen(
                viewModel = editExpenseViewModel,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
}
