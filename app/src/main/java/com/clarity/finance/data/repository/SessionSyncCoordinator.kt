package com.clarity.finance.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SyncWorker(val name: String, val run: suspend (String) -> Unit)
data class SessionSyncState(val owner: String? = null, val errors: Map<String, String> = emptyMap())

/** One process singleton. No Activity, screen, or route owns synchronization. No background-execution promise. */
class SessionSyncCoordinator(private val auth: AuthRepository, private val workers: List<SyncWorker>,
    private val scope: CoroutineScope) {
    private var observer: Job? = null
    private var account: Job? = null
    private var generation = 0L
    private val mutable = MutableStateFlow(SessionSyncState())
    val state = mutable.asStateFlow()

    @Synchronized fun start() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            auth.authState.distinctUntilChangedBy { it?.uid }.collect { user ->
                val token = ++generation
                account?.cancel()
                val uid = user?.uid
                mutable.value = SessionSyncState(owner = uid)
                if (uid == null) return@collect
                account = scope.launch {
                    supervisorScope {
                        workers.forEach { worker -> launch {
                            while (isActive) {
                                try {
                                    worker.run(uid)
                                    if (generation == token) mutable.update { it.copy(errors = it.errors - worker.name) }
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) {
                                    if (generation == token && mutable.value.owner == uid) mutable.update {
                                        it.copy(errors = it.errors + (worker.name to "Synchronization unavailable: ${e.message}. Saved proposals are retained."))
                                    }
                                }
                                delay(5000)
                            }
                        } }
                    }
                }
            }
        }
    }
}
