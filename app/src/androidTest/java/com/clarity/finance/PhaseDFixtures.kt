package com.clarity.finance

import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*

/** All credentials/data are fictional. No production repository or sync worker is installed. */
class PhaseDFixtures {
    val finance = PhaseCFixtures()
    val agreements = PhaseBFixtures()
    val session = finance.session.apply { value = null }
    var loginCalls = 0
    var registerCalls = 0
    var logoutCalls = 0
    var admission: CompletableDeferred<Unit>? = null
    var authResult: Result<User> = Result.success(User(finance.owner, "Fictional review account"))
    val unavailable = MutableStateFlow(false)
    val auth = object : AuthRepository {
        override val authState = session
        override suspend fun login(email: String, password: String): Result<User> {
            loginCalls++; admission?.await()
            return authResult.onSuccess { session.value = it }
        }
        override suspend fun register(email: String, password: String): Result<User> {
            registerCalls++; admission?.await()
            return authResult.onSuccess { session.value = it }
        }
        override suspend fun logout() { logoutCalls++; session.value = null }
    }
    val expenses = object : ExpenseRepository by finance.expenses {
        override fun getExpenses(userId: String) = combine(finance.expenseRows, unavailable) { rows, failed ->
            if (failed) error("Expenses unavailable in this isolated example.")
            if (userId == finance.owner) rows else emptyList()
        }
    }
    fun graph() = isolatedAppGraph(auth, expense = expenses, income = agreements, payment = agreements,
        reporting = finance.reporting, goal = finance.goals, recurring = finance.recurring, crypto = finance.crypto)
    fun signIn() { session.value = User(finance.owner, "Fictional review account") }
}
