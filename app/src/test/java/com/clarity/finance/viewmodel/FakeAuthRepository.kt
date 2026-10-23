package com.clarity.finance.viewmodel

import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<User?>(null)
    override val authState: Flow<User?> = _authState

    var shouldFail = false
    var registeredUsers = mutableMapOf<String, String>() // email -> password

    override suspend fun login(email: String, password: String): Result<User> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        
        val storedPassword = registeredUsers[email]
        return if (storedPassword == password) {
            val user = User("fake-uid-$email", email)
            _authState.value = user
            Result.success(user)
        } else {
            Result.failure(Exception("Invalid credentials"))
        }
    }

    override suspend fun register(email: String, password: String): Result<User> {
        if (shouldFail) return Result.failure(Exception("Network error"))
        
        if (registeredUsers.containsKey(email)) {
            return Result.failure(Exception("email address is already in use by another account"))
        }

        registeredUsers[email] = password
        val user = User("fake-uid-$email", email)
        _authState.value = user
        return Result.success(user)
    }

    override suspend fun logout() {
        _authState.value = null
    }
}
