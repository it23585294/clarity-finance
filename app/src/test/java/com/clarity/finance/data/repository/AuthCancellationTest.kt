package com.clarity.finance.data.repository

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCancellationTest {
    @Test fun `cancelled login await propagates rather than returning an authentication failure`() = runTest {
        val auth = mock(FirebaseAuth::class.java); val task = TaskCompletionSource<AuthResult>()
        `when`(auth.signInWithEmailAndPassword("fixture@example.test", "local-only-fixture")).thenReturn(task.task)
        val job = backgroundScope.launch {
            FirebaseAuthRepository(auth).login("fixture@example.test", "local-only-fixture")
            fail("Cancellation must propagate")
        }
        runCurrent(); job.cancelAndJoin(); assertTrue(job.isCancelled); assertFalse(task.task.isComplete)
    }
    @Test fun `cancelled registration await propagates and does not prove the underlying Task failed`() = runTest {
        val auth = mock(FirebaseAuth::class.java); val task = TaskCompletionSource<AuthResult>()
        `when`(auth.createUserWithEmailAndPassword("fixture@example.test", "local-only-fixture")).thenReturn(task.task)
        val job = backgroundScope.launch {
            FirebaseAuthRepository(auth).register("fixture@example.test", "local-only-fixture")
            fail("Cancellation must propagate")
        }
        runCurrent(); job.cancelAndJoin(); assertTrue(job.isCancelled); assertFalse(task.task.isComplete)
    }
}
