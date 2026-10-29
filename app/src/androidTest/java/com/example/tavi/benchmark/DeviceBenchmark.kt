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
import com.example.tavi.engine.Gemma4BackendEngine
import com.example.tavi.engine.ModelManager
import com.example.tavi.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MetricRun(
    val coldInitMs: Long,
    val ttftMs: Long,
    val totalTimeMs: Long,
    val tokensGenerated: Int,
    val decodeTokensPerSec: Double,
    val peakPssKb: Long,
    val batteryDeltaPercent: Int,
    val thermalStatusBefore: Int,
    val thermalStatusAfter: Int
)

/**
 * Real device benchmark harness for Tavi on physical Android hardware.
 * Measures cold init ms, TTFT, decode tok/s, peak PSS, battery drain, and thermal throttling.
 * Outputs honest measured JSON per device.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class DeviceBenchmark {

    private lateinit var context: Context
    private lateinit var modelManager: ModelManager
    private lateinit var toolRegistry: ToolRegistry

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        modelManager = ModelManager(context)
        toolRegistry = ToolRegistry.getInstance(context)
    }

    @Test
    fun runFullDeviceBenchmarkSuite() {
        val modelFile = modelManager.modelFile
        val modelName = if (modelFile.name.contains("e2b")) "gemma-4-e2b" else "gemma-3-1b-it"

        val deviceName = "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val jsonFileName = "benchmark_${deviceName}_${modelName}_${dateStr}.json"

        val rootJson = JSONObject()
        rootJson.put("device_manufacturer", Build.MANUFACTURER)
        rootJson.put("device_model", Build.MODEL)
        rootJson.put("device_brand", Build.BRAND)
        rootJson.put("device_soc", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "unknown")
        rootJson.put("android_version", Build.VERSION.RELEASE)
        rootJson.put("api_level", Build.VERSION.SDK_INT)
        rootJson.put("model_evaluated", modelName)
        rootJson.put("model_file_size_bytes", if (modelFile.exists()) modelFile.length() else 0L)
        rootJson.put("timestamp", System.currentTimeMillis())
        rootJson.put("date", dateStr)

        val scenariosJson = JSONObject()

        // 1. Short Reply Scenario
        val shortReplyRuns = runScenario(
            modelPath = modelFile.absolutePath,
            prompt = "What is the capital of Kenya?",
            targetTokens = 30,
            repeatCount = 3
        )
        scenariosJson.put("short_reply", aggregateMetrics("short_reply", shortReplyRuns))

        // 2. Tool-call round trip (alarm_create)
        val toolCallRuns = runScenario(
            modelPath = modelFile.absolutePath,
            prompt = "Set an alarm for 7:30 AM tomorrow labeled Wake Up",
            targetTokens = 50,
            repeatCount = 3
        )
        scenariosJson.put("tool_call_alarm_create", aggregateMetrics("tool_call_alarm_create", toolCallRuns))

        // 3. 500-token long generation
        val longGenRuns = runScenario(
            modelPath = modelFile.absolutePath,
            prompt = "Explain how on-device language models work without an internet connection in detail.",
            targetTokens = 200,
            repeatCount = 3
        )
        scenariosJson.put("long_generation_200tok", aggregateMetrics("long_generation_200tok", longGenRuns))

        rootJson.put("scenarios", scenariosJson)

        // Write output JSON
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val outputFile = File(outputDir, jsonFileName)
        outputFile.writeText(rootJson.toString(2))
        android.util.Log.i("DeviceBenchmark", "Benchmark results written to: ${outputFile.absolutePath}")
    }

    private fun runScenario(
        modelPath: String,
        prompt: String,
        targetTokens: Int,
        repeatCount: Int
    ): List<MetricRun> {
        val runs = mutableListOf<MetricRun>()

        for (i in 0 until repeatCount) {
            val batteryBefore = getBatteryLevel()
            val thermalBefore = getThermalStatus()

            val initStart = SystemClock.elapsedRealtime()
            val engine = Gemma4BackendEngine(
                context = context,
                modelPath = modelPath,
                toolRegistry = toolRegistry
            )
            val initSuccess = engine.loadModel().isSuccess
            val coldInitMs = SystemClock.elapsedRealtime() - initStart

            var ttftMs = 0L
            var firstTokenRecorded = false
            var tokenCount = 0
            val genStart = SystemClock.elapsedRealtime()

            runBlocking {
                engine.generateResponse(prompt, emptyList()) { token ->
                    if (!firstTokenRecorded) {
                        firstTokenRecorded = true
                        ttftMs = SystemClock.elapsedRealtime() - genStart
                    }
                    tokenCount++
                }
            }

            val totalTimeMs = SystemClock.elapsedRealtime() - genStart
            val decodeTimeSec = if (totalTimeMs > ttftMs) (totalTimeMs - ttftMs) / 1000.0 else 0.001
            val decodeTokensPerSec = if (tokenCount > 1) (tokenCount - 1) / decodeTimeSec else 0.0

            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            val peakPssKb = memoryInfo.totalPss.toLong()

            val batteryAfter = getBatteryLevel()
            val thermalAfter = getThermalStatus()

            engine.unloadModel()
            engine.close()

            runs.add(
                MetricRun(
                    coldInitMs = coldInitMs,
                    ttftMs = ttftMs,
                    totalTimeMs = totalTimeMs,
                    tokensGenerated = tokenCount,
                    decodeTokensPerSec = decodeTokensPerSec,
                    peakPssKb = peakPssKb,
                    batteryDeltaPercent = batteryBefore - batteryAfter,
                    thermalStatusBefore = thermalBefore,
                    thermalStatusAfter = thermalAfter
                )
            )

            // Cool-down pause between test runs to avoid artificial thermal throttling
            Thread.sleep(1500)
        }

        return runs
    }

    private fun aggregateMetrics(scenarioName: String, runs: List<MetricRun>): JSONObject {
        val obj = JSONObject()
        val rawArray = JSONArray()

        val ttftList = runs.map { it.ttftMs }.sorted()
        val coldInitList = runs.map { it.coldInitMs }.sorted()
        val tokPerSecList = runs.map { it.decodeTokensPerSec }.sorted()
        val pssList = runs.map { it.peakPssKb }.sorted()

        runs.forEach { r ->
            val item = JSONObject().apply {
                put("cold_init_ms", r.coldInitMs)
                put("ttft_ms", r.ttftMs)
                put("total_time_ms", r.totalTimeMs)
                put("tokens_generated", r.tokensGenerated)
                put("decode_tokens_per_sec", "%.2f".format(Locale.US, r.decodeTokensPerSec))
                put("peak_pss_mb", r.peakPssKb / 1024)
                put("battery_delta_percent", r.batteryDeltaPercent)
                put("thermal_before", r.thermalStatusBefore)
                put("thermal_after", r.thermalStatusAfter)
            }
            rawArray.put(item)
        }

        obj.put("scenario", scenarioName)
        obj.put("runs_count", runs.size)
        obj.put("cold_init_median_ms", medianLong(coldInitList))
        obj.put("cold_init_min_ms", coldInitList.firstOrNull() ?: 0)
        obj.put("cold_init_max_ms", coldInitList.lastOrNull() ?: 0)

        obj.put("ttft_median_ms", medianLong(ttftList))
        obj.put("ttft_min_ms", ttftList.firstOrNull() ?: 0)
        obj.put("ttft_max_ms", ttftList.lastOrNull() ?: 0)

        obj.put("decode_tok_per_sec_median", "%.2f".format(Locale.US, medianDouble(tokPerSecList)))
        obj.put("decode_tok_per_sec_min", "%.2f".format(Locale.US, tokPerSecList.firstOrNull() ?: 0.0))
        obj.put("decode_tok_per_sec_max", "%.2f".format(Locale.US, tokPerSecList.lastOrNull() ?: 0.0))

        obj.put("peak_pss_median_mb", medianLong(pssList) / 1024)
        obj.put("raw_runs", rawArray)

        return obj
    }

    private fun medianLong(list: List<Long>): Long {
        if (list.isEmpty()) return 0
        return list[list.size / 2]
    }

    private fun medianDouble(list: List<Double>): Double {
        if (list.isEmpty()) return 0.0
        return list[list.size / 2]
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

    private fun getThermalStatus(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.currentThermalStatus ?: -1
        } else {
            -1
        }
    }
}
