package com.tk.quicksearch.search.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestSearchJobRunnerTest {
    @Test
    fun `rapid submissions compute and publish only latest query`() = runTest {
        val runner = LatestSearchJobRunner<String>(this, StandardTestDispatcher(testScheduler), 150)
        val computed = mutableListOf<String>()
        val published = mutableListOf<String>()

        runner.submit(
            compute = { "first".also(computed::add) },
            publish = published::add,
        )
        advanceTimeBy(100)
        runner.submit(
            compute = { "second".also(computed::add) },
            publish = published::add,
        )
        advanceUntilIdle()

        assertEquals(listOf("second"), computed)
        assertEquals(listOf("second"), published)
    }

    @Test
    fun `late non-cancellable computation cannot publish after newer query`() = runTest {
        val runner = LatestSearchJobRunner<String>(this, StandardTestDispatcher(testScheduler), 150)
        val published = mutableListOf<String>()

        runner.submit(
            compute = {
                withContext(NonCancellable) { delay(400) }
                "old"
            },
            publish = published::add,
        )
        advanceTimeBy(150)
        runCurrent()
        runner.submit(
            compute = { "new" },
            publish = published::add,
        )
        advanceUntilIdle()

        assertEquals(listOf("new"), published)
    }

    @Test
    fun `cancel before debounce prevents computation and publication`() = runTest {
        val runner = LatestSearchJobRunner<String>(this, StandardTestDispatcher(testScheduler), 150)
        var computed = false
        val published = mutableListOf<String>()

        runner.submit(
            compute = {
                computed = true
                "result"
            },
            publish = published::add,
        )
        advanceTimeBy(100)
        runner.cancel()
        advanceUntilIdle()

        assertFalse(computed)
        assertTrue(published.isEmpty())
    }
}
