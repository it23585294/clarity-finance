package com.clarity.finance.di

import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.ExpenseRepository
import com.clarity.finance.data.repository.FirebaseAuthRepository
import com.clarity.finance.data.repository.FirestoreExpenseRepository
import com.clarity.finance.data.repository.IncomeRepository
import com.clarity.finance.data.repository.FirestoreIncomeRepository
import com.clarity.finance.data.repository.PaymentRepository
import com.clarity.finance.data.repository.FirestorePaymentRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.dataStore
import com.clarity.finance.data.repository.CryptoRepository
import com.clarity.finance.data.repository.FirestoreCryptoRepository
import com.clarity.finance.data.repository.FirestoreCryptoRemote
import com.clarity.finance.data.repository.ReportingRepository
import com.clarity.finance.data.repository.FirestoreReportingRepository

class AppContainer(private val context: Context) {
    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(FirebaseAuth.getInstance())
    }
    
    private val recoveryStore by lazy { SubmissionRecoveryStore(context.dataStore) }
    
    val expenseRepository: ExpenseRepository by lazy {
        FirestoreExpenseRepository(FirebaseFirestore.getInstance(), recoveryStore)
    }

    val incomeRepository: IncomeRepository by lazy {
        FirestoreIncomeRepository(FirebaseFirestore.getInstance(), recoveryStore)
    }

    val paymentRepository: PaymentRepository by lazy {
        FirestorePaymentRepository(FirebaseFirestore.getInstance(), recoveryStore)
    }

    val cryptoRepository: CryptoRepository by lazy {
        FirestoreCryptoRepository(FirestoreCryptoRemote(FirebaseFirestore.getInstance()) {
            FirebaseAuth.getInstance().currentUser?.uid
        }, recoveryStore)
    }

    val reportingRepository: ReportingRepository by lazy {
        FirestoreReportingRepository(FirebaseFirestore.getInstance(), recoveryStore)
    }
}
