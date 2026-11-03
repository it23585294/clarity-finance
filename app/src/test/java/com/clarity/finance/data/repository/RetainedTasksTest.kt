package com.clarity.finance.data.repository

import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedTasksTest {
    @Test fun `cancelled await retains the actual Firebase Task and prevents overlapping attempts`() = runTest {
        val tasks = RetainedTasks<Int>(); val underlying = TaskCompletionSource<Int>(); var starts = 0
        val first = backgroundScope.async { withTimeoutOrNull(10000) { tasks.await("uid/type/id") { starts++; underlying.task } } }
        runCurrent(); advanceTimeBy(10001); runCurrent()
        assertNull(first.await()); assertFalse(underlying.task.isComplete)
        val retry = backgroundScope.async { tasks.await("uid/type/id") { starts++; underlying.task } }
        runCurrent(); assertEquals(1, starts)
        underlying.setResult(7); runCurrent()
        assertEquals(7, retry.await()); assertEquals(1, starts)
    }
}
