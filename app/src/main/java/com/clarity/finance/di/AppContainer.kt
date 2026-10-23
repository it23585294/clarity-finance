package com.clarity.finance.di

import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.clarity.finance.data.repository.FirebaseAuthRepository
import com.clarity.finance.data.repository.FirestoreExpenseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import com.clarity.finance.data.local.ExpenseRecoveryStore
import com.clarity.finance.data.local.dataStore

class AppContainer(private val context: Context) {
    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(FirebaseAuth.getInstance())
    }
    
    val expenseRepository: ExpenseRepository by lazy {
        FirestoreExpenseRepository(
            FirebaseFirestore.getInstance(),
            ExpenseRecoveryStore(context.dataStore)
        )
    }
}
