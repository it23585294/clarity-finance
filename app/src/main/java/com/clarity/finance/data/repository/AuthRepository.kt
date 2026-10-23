package com.clarity.finance.data.repository

import com.clarity.finance.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<User?>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String): Result<User>
    suspend fun logout()
}
