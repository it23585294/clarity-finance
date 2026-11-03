package com.clarity.finance.data.repository

import com.clarity.finance.data.model.User
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSyncCoordinatorTest {
    private class Auth : AuthRepository {
        override val authState = MutableStateFlow<User?>(null)
        override suspend fun login(email: String, password: String) = error("Online auth not used")
        override suspend fun register(email: String, password: String) = error("Online auth not used")
        override suspend fun logout() { authState.value = null }
    }
    @Test fun `start is singleton screen independent and account switch cancels all old workers`() = runTest {
        val auth = Auth(); val active = mutableSetOf<String>(); val starts = mutableListOf<String>()
        val worker = SyncWorker("financial") { uid ->
            starts += uid; active += uid
            try { awaitCancellation() } finally { active -= uid }
        }
        val sync = SessionSyncCoordinator(auth, listOf(worker), backgroundScope)
        sync.start(); sync.start(); runCurrent()
        assertTrue(starts.isEmpty())
        auth.authState.value = User("a", "a@example.invalid"); runCurrent()
        assertEquals(listOf("a"), starts)
        auth.authState.value = User("b", "b@example.invalid"); runCurrent()
        assertEquals(setOf("b"), active); assertEquals("b", sync.state.value.owner)
        auth.authState.value = null; runCurrent()
        assertTrue(active.isEmpty()); assertNull(sync.state.value.owner); assertTrue(sync.state.value.errors.isEmpty())
    }
    @Test fun `a delayed old worker error cannot replace the new account state`() = runTest {
        val auth = Auth(); val oldResult = CompletableDeferred<Unit>()
        val worker = SyncWorker("financial") { uid ->
            if (uid == "a") withContext(NonCancellable) { oldResult.await(); error("old-user message") }
            else awaitCancellation()
        }
        val sync = SessionSyncCoordinator(auth, listOf(worker), backgroundScope); sync.start(); runCurrent()
        auth.authState.value = User("a", "a@example.invalid"); runCurrent()
        auth.authState.value = User("b", "b@example.invalid"); runCurrent()
        assertEquals("b", sync.state.value.owner)
        oldResult.complete(Unit); runCurrent()
        assertEquals("b", sync.state.value.owner); assertTrue(sync.state.value.errors.isEmpty())
    }
}
