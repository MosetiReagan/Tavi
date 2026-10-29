package com.example.tavi.engine

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.provider.OpenableColumns
import com.example.tavi.model.ModelOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

data class DeviceRamAssessment(
    val isSupported: Boolean,
    val isLowRamDevice: Boolean,
    val totalRamMb: Long,
    val availRamMb: Long,
    val recommendedModelId: String,
    val warningMessage: String? = null
)

data class InstalledModelInfo(
    val isInstalled: Boolean,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val filePath: String,
    val fileName: String,
    val modelId: String
)

/**
 * Manages the local model lifecycle for Gemma 4 E2B and Gemma 3 1B on-device assistant.
 * Handles storage, installation, resumable HTTP range downloads, SAF import, SHA-256 integrity verification,
 * and low-memory callbacks.
 */
class ModelManager(private val context: Context) : ComponentCallbacks2 {

    companion object {
        const val MODEL_GEMMA_4_E2B = "gemma-4-e2b"
        const val MODEL_GEMMA_3_1B = "gemma-3-1b-it"

        const val MODEL_FILE_NAME_E2B = "$MODEL_GEMMA_4_E2B.litertlm"
        const val MODEL_FILE_NAME_3_1B_LITERTLM = "$MODEL_GEMMA_3_1B.litertlm"
        const val MODEL_FILE_NAME_3_1B_TASK = "$MODEL_GEMMA_3_1B.task"

        // Default download URLs
        const val DEFAULT_DOWNLOAD_URL_E2B = "https://huggingface.co/google/gemma-4-e2b-it-litert/resolve/main/gemma-4-e2b-it.litertlm"
        const val DEFAULT_DOWNLOAD_URL_3_1B = "https://huggingface.co/lotapa/gemma3-1b-it-int4.litertlm/resolve/main/gemma3-1b-it-int4.litertlm"
        const val DEFAULT_MODEL_DOWNLOAD_URL = DEFAULT_DOWNLOAD_URL_3_1B

        const val SIZE_E2B_MB = 2583L
        const val SIZE_3_1B_MB = 1100L
        const val RAM_REQUIRED_E2B_MB = 6144
        const val RAM_REQUIRED_3_1B_MB = 2048

        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024
        private const val PREFS_NAME = "tavi_model_prefs"
        private const val KEY_RUNTIME = "active_inference_runtime"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"

        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val ramAssessment = checkDeviceRamSupport()

    private val _activeModelId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_MODEL_ID, null) ?: ramAssessment.recommendedModelId
    )
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    val modelFile: File
        get() {
            val activeId = _activeModelId.value
            val primaryFile = if (activeId == MODEL_GEMMA_4_E2B) {
                File(modelsDir, MODEL_FILE_NAME_E2B)
            } else {
                val litertlm31 = File(modelsDir, MODEL_FILE_NAME_3_1B_LITERTLM)
                if (litertlm31.exists() && litertlm31.length() > 0) litertlm31 else File(modelsDir, MODEL_FILE_NAME_3_1B_TASK)
            }

            if (primaryFile.exists() && primaryFile.length() > 0) return primaryFile

            // Fallback: check if the other model exists
            val altFile = if (activeId == MODEL_GEMMA_4_E2B) {
                val litertlm31 = File(modelsDir, MODEL_FILE_NAME_3_1B_LITERTLM)
                if (litertlm31.exists() && litertlm31.length() > 0) litertlm31 else File(modelsDir, MODEL_FILE_NAME_3_1B_TASK)
            } else {
                File(modelsDir, MODEL_FILE_NAME_E2B)
            }
            if (altFile.exists() && altFile.length() > 0) return altFile

            return primaryFile
        }

    private val _activeRuntime = MutableStateFlow(
        InferenceRuntime.fromString(prefs.getString(KEY_RUNTIME, InferenceRuntime.GEMMA_4_E2B.name))
    )
    val activeRuntime: StateFlow<InferenceRuntime> = _activeRuntime.asStateFlow()

    fun setRuntime(runtime: InferenceRuntime) {
        prefs.edit().putString(KEY_RUNTIME, runtime.name).apply()
        _activeRuntime.value = runtime
    }

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isLoaded = MutableStateFlow(modelFile.exists() && modelFile.length() > 0)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        context.registerComponentCallbacks(this)
        setupNetworkMonitoring()
    }

    private fun setupNetworkMonitoring() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            _isOffline.value = false
            return
        }

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isOffline.value = caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOffline.value = false
            }

            override fun onLost(network: Network) {
                _isOffline.value = true
            }
        })
    }

    fun getInstalledModelInfo(modelId: String = _activeModelId.value): InstalledModelInfo {
        val targetFile = if (modelId == MODEL_GEMMA_4_E2B) {
            File(modelsDir, MODEL_FILE_NAME_E2B)
        } else {
            val litertlm31 = File(modelsDir, MODEL_FILE_NAME_3_1B_LITERTLM)
            if (litertlm31.exists() && litertlm31.length() > 0) litertlm31 else File(modelsDir, MODEL_FILE_NAME_3_1B_TASK)
        }

        val exists = targetFile.exists() && targetFile.length() > 0
        val sizeBytes = if (exists) targetFile.length() else 0L
        val formatted = if (exists) {
            val mb = sizeBytes / (1024.0 * 1024.0)
            if (mb >= 1000) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
        } else {
            "Not installed"
        }
        return InstalledModelInfo(
            isInstalled = exists,
            fileSizeBytes = sizeBytes,
            formattedSize = formatted,
            filePath = targetFile.absolutePath,
            fileName = targetFile.name,
            modelId = modelId
        )
    }

    /**
     * Assesses whether device has sufficient RAM to run local LLM inference without OOM risks.
     * Recommendation: totalMem >= 6GB -> Gemma 4 E2B else Gemma 3 1B.
     */
    fun checkDeviceRamSupport(): DeviceRamAssessment {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        val recommended = if (isLowRam || (totalRamMb in 1 until 6000)) {
            MODEL_GEMMA_3_1B
        } else {
            MODEL_GEMMA_4_E2B
        }

        val isInsufficient = isLowRam || (totalRamMb in 1 until 1900)
        return DeviceRamAssessment(
            isSupported = !isInsufficient,
            isLowRamDevice = isLowRam,
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            recommendedModelId = recommended,
            warningMessage = if (isInsufficient) {
                "Device has low RAM (${if (totalRamMb > 0) "${totalRamMb} MB" else "low-RAM mode"} detected, requires at least 2048 MB). Running on-device LLM may cause out-of-memory terminations. Offline fast-path rules will be used."
            } else null
        )
    }

    fun getAvailableModels(): List<ModelOption> {
        val ramAssessment = checkDeviceRamSupport()
        val e2bInfo = getInstalledModelInfo(MODEL_GEMMA_4_E2B)
        val g3Info = getInstalledModelInfo(MODEL_GEMMA_3_1B)

        val e2bRecommended = ramAssessment.recommendedModelId == MODEL_GEMMA_4_E2B

        return listOf(
            ModelOption(
                id = MODEL_GEMMA_4_E2B,
                name = if (e2bRecommended) "Gemma 4 E2B (Recommended)" else "Gemma 4 E2B (High RAM)",
                sizeDescription = if (e2bInfo.isInstalled) "Installed • ${e2bInfo.formattedSize}" else "2.58 GB • ~6 GB RAM",
                ramRequiredMb = RAM_REQUIRED_E2B_MB,
                isInstalled = e2bInfo.isInstalled,
                isLoaded = e2bInfo.isInstalled && _isLoaded.value && _activeModelId.value == MODEL_GEMMA_4_E2B,
                localPath = if (e2bInfo.isInstalled) e2bInfo.filePath else null,
                isPrimary = e2bRecommended,
                description = "Google Gemma 4 E2B instruction-tuned model. Supports native OpenAPI tool calling and thinking reasoning channels."
            ),
            ModelOption(
                id = MODEL_GEMMA_3_1B,
                name = if (!e2bRecommended) "Gemma 3 1B (Recommended for device)" else "Gemma 3 1B (Low memory)",
                sizeDescription = if (g3Info.isInstalled) "Installed • ${g3Info.formattedSize}" else "1.1 GB • ~2 GB RAM",
                ramRequiredMb = RAM_REQUIRED_3_1B_MB,
                isInstalled = g3Info.isInstalled,
                isLoaded = g3Info.isInstalled && _isLoaded.value && _activeModelId.value == MODEL_GEMMA_3_1B,
                localPath = if (g3Info.isInstalled) g3Info.filePath else null,
                isPrimary = !e2bRecommended,
                description = "Compact Gemma 3 1B model tuned for budget devices. Fast cold init and low memory footprint."
            )
        )
    }

    fun selectModel(modelId: String) {
        _activeModelId.value = modelId
        prefs.edit().putString(KEY_ACTIVE_MODEL_ID, modelId).apply()
        _isLoaded.value = modelFile.exists() && modelFile.length() > 0
    }

    fun deleteModel(modelId: String = _activeModelId.value): Boolean {
        val target = if (modelId == MODEL_GEMMA_4_E2B) {
            File(modelsDir, MODEL_FILE_NAME_E2B)
        } else {
            val f = File(modelsDir, MODEL_FILE_NAME_3_1B_LITERTLM)
            if (f.exists()) f else File(modelsDir, MODEL_FILE_NAME_3_1B_TASK)
        }

        val deleted = if (target.exists()) target.delete() else false
        if (deleted) {
            _isLoaded.value = modelFile.exists() && modelFile.length() > 0
        }
        return deleted
    }

    suspend fun importModelFromUri(
        context: Context,
        sourceUri: Uri,
        expectedSha256: String? = null,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var totalBytes = -1L
        var displayName = ""
        try {
            contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    totalBytes = cursor.getLong(sizeIndex)
                }
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: ""
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ModelManager", "Failed resolving content URI metadata", e)
        }

        val targetDestination = when {
            displayName.contains("gemma-4", ignoreCase = true) || displayName.contains("e2b", ignoreCase = true) -> {
                File(modelsDir, MODEL_FILE_NAME_E2B)
            }
            displayName.endsWith(".task", ignoreCase = true) -> {
                File(modelsDir, MODEL_FILE_NAME_3_1B_TASK)
            }
            else -> {
                File(modelsDir, MODEL_FILE_NAME_3_1B_LITERTLM)
            }
        }
        val tempTarget = File(modelsDir, "${targetDestination.name}.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesCopied = 0L

            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempTarget).use { output ->
                    val buffer = ByteArray(65536)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesCopied += read
                        onProgress(bytesCopied, totalBytes)
                        read = input.read(buffer)
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open input stream from URI: $sourceUri"))

            if (bytesCopied < MIN_MODEL_BYTES) {
                tempTarget.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        "Selected model file is only ${bytesCopied / (1024 * 1024)} MB. Incomplete model bundle."
                    )
                )
            }

            // Verify integrity
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!expectedSha256.isNullOrBlank()) {
                if (!actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                    tempTarget.delete()
                    return@withContext Result.failure(SecurityException("SHA-256 verification mismatch.\nExpected: $expectedSha256\nActual: $actualHash"))
                }
            }

            if (targetDestination.exists()) targetDestination.delete()
            if (!tempTarget.renameTo(targetDestination)) {
                tempTarget.copyTo(targetDestination, overwrite = true)
                tempTarget.delete()
            }

            _isLoaded.value = true
            Result.success(targetDestination)
        } catch (e: Exception) {
            tempTarget.delete()
            Result.failure(e)
        }
    }

    /**
     * Downloads model weights from an HTTP/HTTPS endpoint with resumable HTTP Range requests,
     * free-space validation, SHA-256 integrity check, and progress reporting.
     */
    suspend fun downloadModel(
        url: String = if (_activeModelId.value == MODEL_GEMMA_4_E2B) DEFAULT_DOWNLOAD_URL_E2B else DEFAULT_DOWNLOAD_URL_3_1B,
        expectedSha256: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val isE2b = url.contains("e2b", ignoreCase = true) || url.contains("gemma-4", ignoreCase = true)
        val requiredMb = if (isE2b) 2800L else 1250L

        // 1. Free space check
        try {
            val freeBytes = modelsDir.usableSpace
            val requiredBytes = requiredMb * 1024 * 1024
            if (freeBytes in 1 until requiredBytes) {
                val freeMb = freeBytes / (1024 * 1024)
                return@withContext Result.failure(
                    java.io.IOException("Insufficient storage space: Tavi requires at least $requiredMb MB free space, but only ${freeMb} MB is available.")
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ModelManager", "Could not check free storage space", e)
        }

        val targetDestination = if (isE2b) {
            File(modelsDir, MODEL_FILE_NAME_E2B)
        } else if (url.contains(".task", ignoreCase = true)) {
            File(modelsDir, MODEL_FILE_NAME_3_1B_TASK)
        } else {
            File(modelsDir, MODEL_FILE_NAME_3_1B_LITERTLM)
        }

        val tempTarget = File(modelsDir, "${targetDestination.name}.download.tmp")
        var connection: java.net.HttpURLConnection? = null
        val modelKey = if (isE2b) MODEL_GEMMA_4_E2B else MODEL_GEMMA_3_1B

        try {
            var currentUrl = url
            var redirects = 0
            val maxRedirects = 8
            val existingBytes = if (tempTarget.exists()) tempTarget.length() else 0L

            while (redirects < maxRedirects) {
                val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "Tavi-Android-Assistant/1.0")

                // Resumable download range header
                if (existingBytes > 0) {
                    conn.setRequestProperty("Range", "bytes=$existingBytes-")
                }

                val status = conn.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return@withContext Result.failure(java.io.IOException("Redirect with no Location header from $currentUrl"))
                    }
                    currentUrl = if (location.startsWith("http")) location else java.net.URL(java.net.URL(currentUrl), location).toString()
                    redirects++
                } else if (status == 206 || status in 200..299) {
                    connection = conn
                    break
                } else if (status == 416) { // Range not satisfiable, restart from zero
                    conn.disconnect()
                    tempTarget.delete()
                    return@withContext downloadModel(url, expectedSha256, onProgress)
                } else {
                    conn.disconnect()
                    return@withContext Result.failure(java.io.IOException("HTTP download error: $status ${conn.responseMessage}"))
                }
            }

            val finalConn = connection ?: return@withContext Result.failure(java.io.IOException("Too many redirects: $redirects"))
            val isPartial = finalConn.responseCode == 206
            val serverContentLength = finalConn.contentLengthLong
            val totalBytes = if (isPartial) existingBytes + serverContentLength else serverContentLength

            var bytesCopied = if (isPartial) existingBytes else 0L

            finalConn.inputStream.use { input ->
                FileOutputStream(tempTarget, isPartial).use { output ->
                    val buffer = ByteArray(65536)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val progressRatio = if (totalBytes > 0) (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        _downloadProgress.value = mapOf(modelKey to progressRatio)
                        onProgress(bytesCopied, totalBytes)
                        read = input.read(buffer)
                    }
                }
            }

            if (bytesCopied < MIN_MODEL_BYTES) {
                tempTarget.delete()
                _downloadProgress.value = emptyMap()
                return@withContext Result.failure(
                    IllegalStateException("Downloaded file is only ${bytesCopied / (1024 * 1024)} MB. Incomplete download.")
                )
            }

            // Verify integrity
            if (!expectedSha256.isNullOrBlank()) {
                val actualHash = computeSha256(tempTarget)
                if (!actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                    tempTarget.delete()
                    _downloadProgress.value = emptyMap()
                    return@withContext Result.failure(
                        SecurityException("SHA-256 mismatch. Expected: $expectedSha256, Actual: $actualHash")
                    )
                }
            }

            if (targetDestination.exists()) targetDestination.delete()
            if (!tempTarget.renameTo(targetDestination)) {
                tempTarget.copyTo(targetDestination, overwrite = true)
                tempTarget.delete()
            }

            _downloadProgress.value = emptyMap()
            _isLoaded.value = true
            Result.success(targetDestination)
        } catch (e: Exception) {
            _downloadProgress.value = emptyMap()
            Result.failure(e)
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                android.util.Log.w("ModelManager", "Error closing download connection: ${e.message}")
            }
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            android.util.Log.w("ModelManager", "OS trim memory warning (level=$level). Model state preserved.")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        android.util.Log.e("ModelManager", "OS reported low memory critical state.")
    }
}
