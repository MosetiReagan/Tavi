package com.example.tavi.benchmark

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.tavi.engine.LiteRtLlmEngine
import com.example.tavi.engine.ModelManager
import com.example.tavi.context.ContextBudgetManager
import com.example.tavi.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Real inference only. Chunks and token estimates are never presented as native tokenizer counts. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class DeviceBenchmark {
    @Test fun runFullDeviceBenchmarkSuite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val models = ModelManager.getInstance(context)
        val weights = models.modelFile
        assumeTrue("Install a model on this device before benchmarking", weights.exists() && weights.length() > 100_000_000)
        val registry = ToolRegistry.getInstance(context)
        val scenarios = listOf(
            "short_reply" to "What is the capital of Kenya?",
            "structured_reply" to "Return a JSON object describing an alarm at 07:30, with fields hour, minutes and label. Only describe it.",
            "long_reply" to "Explain how on-device language models work without an internet connection in detail.")
        val report = JSONObject().put("device_model", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
            .put("api_level", Build.VERSION.SDK_INT).put("weights", weights.name).put("weights_bytes", weights.length())
            .put("selected_runtime", models.activeRuntime.value.name).put("timestamp", System.currentTimeMillis())
            .put("measurement_notes", "Fresh engine instances in one process. Output chunks are callback chunks. Tokens are conservative text estimates. Peak process PSS sampled every 50 ms. No phone actions are executed.")
        val results = JSONObject()
        var failedRuns = 0
        for ((name, prompt) in scenarios) {
            val runs = JSONArray()
            repeat(3) {
                val peak = AtomicLong(0)
                val sampler = launch(Dispatchers.Default) {
                    while (isActive) {
                        val memory = Debug.MemoryInfo(); Debug.getMemoryInfo(memory)
                        peak.accumulateAndGet(memory.totalPss.toLong(), ::maxOf)
                        delay(50)
                    }
                }
                val engine = LiteRtLlmEngine(context, weights.absolutePath, models.activeRuntime.value, registry)
                val batteryBefore = battery(context)
                val thermalBefore = thermal(context)
                val start = SystemClock.elapsedRealtime()
                var firstChunkMs: Long? = null
                var chunks = 0
                var output = ""
                var error: String? = null
                var initMs = 0L
                var duration = 0L
                try {
                    val loaded = withContext(Dispatchers.IO) { engine.loadModel() }
                    initMs = SystemClock.elapsedRealtime() - start
                    if (loaded.isFailure || !engine.isGenuineInferenceActive) {
                        error = loaded.exceptionOrNull()?.message ?: "Neural inference was not active; no generation measurement collected."
                    } else {
                        val generationStart = SystemClock.elapsedRealtime()
                        output = withTimeout(120_000) { engine.generateResponse(prompt, emptyList()) {
                            if (firstChunkMs == null) firstChunkMs = SystemClock.elapsedRealtime() - generationStart
                            chunks++
                        } }
                        duration = SystemClock.elapsedRealtime() - generationStart
                        if (output.isBlank() || chunks == 0 || !engine.isGenuineInferenceActive) {
                            error = "The runtime did not produce a streamed neural response."
                        }
                    }
                } catch (e: TimeoutCancellationException) { error = "Generation exceeded 120 seconds." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
                finally { sampler.cancelAndJoin(); engine.close() }
                if (error != null) failedRuns++
                val batteryAfter = battery(context)
                runs.put(JSONObject().put("effective_runtime", engine.activeRuntime.name).put("engine_init_ms", initMs)
                    .put("first_chunk_ms", firstChunkMs ?: JSONObject.NULL).put("generation_ms", duration)
                    .put("output_characters", output.length).put("output_chunks", chunks)
                    .put("estimated_output_tokens", ContextBudgetManager.estimateTokens(output))
                    .put("characters_per_second", if (duration > 0) output.length * 1000.0 / duration else JSONObject.NULL)
                    .put("sampled_peak_process_pss_kb", peak.get())
                    .put("battery_delta_percent", if (batteryBefore >= 0 && batteryAfter >= 0) batteryBefore - batteryAfter else JSONObject.NULL)
                    .put("thermal_before", thermalBefore).put("thermal_after", thermal(context)).put("error", error ?: JSONObject.NULL))
                delay(1500)
            }
            results.put(name, runs)
        }
        report.put("scenarios", results)
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(directory, "benchmark_${Build.MODEL.replace(' ', '_')}_${System.currentTimeMillis()}.json")
        file.writeText(report.toString(2))
        android.util.Log.i("DeviceBenchmark", "Measured report: ${file.absolutePath}")
        assertEquals("Some neural benchmark runs failed; inspect ${file.name}", 0, failedRuns)
    }
    private fun battery(context: Context): Int {
        val state = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }
    private fun thermal(context: Context) = if (Build.VERSION.SDK_INT >= 29)
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else -1
}
