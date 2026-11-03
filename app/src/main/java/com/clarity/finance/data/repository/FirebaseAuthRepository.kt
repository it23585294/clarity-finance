package com.clarity.finance.data.repository

import com.clarity.finance.data.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException

class FirebaseAuthRepository(
    private val auth: FirebaseAuth
) : AuthRepository {

    override val authState: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser?.let {
                User(uid = it.uid, email = it.email ?: "")
            }
            trySend(user)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw Exception("Login failed")
        User(uid = firebaseUser.uid, email = firebaseUser.email ?: "")
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun register(email: String, password: String): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: throw Exception("Registration failed")
        User(uid = firebaseUser.uid, email = firebaseUser.email ?: "")
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun logout() {
        auth.signOut()
    }
}
