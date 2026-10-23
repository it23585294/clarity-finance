package com.clarity.finance.di

import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.data.repository.FirebaseAuthRepository
import com.google.firebase.auth.FirebaseAuth

class AppContainer {
    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(FirebaseAuth.getInstance())
    }
}
