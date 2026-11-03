package com.clarity.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.clarity.finance.di.ViewModelFactory
import com.clarity.finance.ui.navigation.AppNavigation
import com.clarity.finance.ui.theme.ClarityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ClarityApplication).container
        val viewModelFactory = ViewModelFactory(
            appContainer.authRepository, 
            appContainer.expenseRepository,
            appContainer.incomeRepository,
            appContainer.paymentRepository,
            appContainer.cryptoRepository,
            appContainer.reportingRepository,
            appContainer.goalRepository,
            appContainer.recurringRepository
        )

        setContent {
            ClarityTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavigation(viewModelFactory = viewModelFactory)
                    }
                }
            }
        }
    }
}
