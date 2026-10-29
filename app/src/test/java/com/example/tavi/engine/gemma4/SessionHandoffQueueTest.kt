package com.example.tavi.engine.gemma4

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SessionHandoffQueueTest {

    @Test
    fun testSerialMutualExclusion() = runTest {
        val queue = SessionHandoffQueue()
        val executionOrder = mutableListOf<String>()
        val counter = AtomicInteger(0)

        val task1 = async {
            queue.withExclusiveSession("TASK_1") {
                val current = counter.incrementAndGet()
                delay(40)
                executionOrder.add("TASK_1:$current")
            }
        }

        val task2 = async {
            delay(5) // Ensure task1 enters first
            queue.withExclusiveSession("TASK_2") {
                val current = counter.incrementAndGet()
                delay(20)
                executionOrder.add("TASK_2:$current")
            }
        }

        task1.await()
        task2.await()

        assertEquals("Expected exactly two executions", 2, executionOrder.size)
        assertEquals("TASK_1:1", executionOrder[0])
        assertEquals("TASK_2:2", executionOrder[1])
        assertNull("Owner should be null after queue completes", queue.currentOwner)
        assertEquals(0, queue.queuedRequestsCount)
    }

    @Test
    fun testSafeCloseWithNormalResource() = runTest {
        val queue = SessionHandoffQueue(closeTimeoutMs = 1000L)
        val closed = AtomicBoolean(false)
        val resource = AutoCloseable {
            closed.set(true)
        }

        val success = queue.safeClose(resource, "testResource")
        assertTrue(success)
        assertTrue(closed.get())
    }

    @Test
    fun testSafeCloseWithNullResource() = runTest {
        val queue = SessionHandoffQueue()
        val success = queue.safeClose(null)
        assertTrue(success)
    }
}
