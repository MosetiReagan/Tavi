package com.example.tavi.engine

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLlmEngineFallbackTest {

    private val dummyContext: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun testFallbackChainE2bToDeterministicWhenWeightsMissing() = runTest {
        val engine = LiteRtLlmEngine(
            context = dummyContext,
            modelPath = null,
            initialRuntime = InferenceRuntime.GEMMA_4_E2B
        )

        assertFalse("Engine should not have genuine inference without weights", engine.isGenuineInferenceActive)

        // Generate greeting / inquiry - should fall back cleanly
        var receivedToken = ""
        val response = engine.generateResponse("Who are you?", emptyList()) { token ->
            receivedToken += token
        }

        assertTrue("Response should contain Tavi assistant identity", response.contains("Tavi", ignoreCase = true))
        assertTrue(receivedToken.isNotEmpty())
    }

    @Test
    fun testFallbackChainDeterministicMathRouting() = runTest {
        val engine = LiteRtLlmEngine(
            context = dummyContext,
            modelPath = null,
            initialRuntime = InferenceRuntime.GEMMA_4_E2B
        )

        // Propose tool calls for math calculation without weights
        val calls = engine.proposeToolCalls("calculate 50 * 4", emptyList())
        assertEquals(1, calls.size)
        assertEquals("calculator", calls.first().toolName)
    }

    @Test
    fun testSwitchRuntimeUpdatesActiveRuntime() {
        val engine = LiteRtLlmEngine(
            context = dummyContext,
            modelPath = null,
            initialRuntime = InferenceRuntime.GEMMA_4_E2B
        )

        assertEquals(InferenceRuntime.GEMMA_4_E2B, engine.activeRuntime)

        engine.switchRuntime(InferenceRuntime.GEMMA_3_1B)
        assertEquals(InferenceRuntime.GEMMA_3_1B, engine.activeRuntime)

        engine.switchRuntime(InferenceRuntime.DETERMINISTIC_ONLY)
        assertEquals(InferenceRuntime.DETERMINISTIC_ONLY, engine.activeRuntime)
        assertFalse(engine.isGenuineInferenceActive)
    }

    @Test
    fun testDiagnosticsReportsHonestFallbackStatus() {
        val engine = LiteRtLlmEngine(
            context = dummyContext,
            modelPath = null,
            initialRuntime = InferenceRuntime.GEMMA_4_E2B
        )

        val diag = engine.getEngineDiagnostics()
        assertEquals(true, diag["fallbackActive"])
        assertEquals(false, diag["isGenuineInferenceActive"])
        assertEquals(false, diag["hasWeights"])
        assertEquals("GEMMA_4_E2B", diag["activeRuntime"])
    }
}
