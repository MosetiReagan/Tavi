package com.example.tavi.engine

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
import java.security.MessageDigest

data class InstalledModelInfo(
    val isInstalled: Boolean,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val filePath: String,
    val fileName: String
)

/**
 * Manages the local model lifecycle for Gemma 3 1B on-device assistant.
 * Handles storage, installation, in-app file import, SHA-256 integrity verification, and OS low-memory callbacks.
 */
class ModelManager(private val context: Context) : ComponentCallbacks2 {

    companion object {
        const val MODEL_GEMMA_3_1B = "gemma-3-1b-it"
        const val MODEL_FILE_NAME = "$MODEL_GEMMA_3_1B.task"
        const val MODEL_FILE_NAME_LITERTLM = "$MODEL_GEMMA_3_1B.litertlm"
        const val DEFAULT_MODEL_DOWNLOAD_URL = "https://huggingface.co/google/gemma-3-1b-it-litert/resolve/main/gemma-3-1b-it.litertlm"
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024
        private const val PREFS_NAME = "tavi_model_prefs"
        private const val KEY_RUNTIME = "active_inference_runtime"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    val modelFile: File
        get() {
            val litertlmFile = File(modelsDir, MODEL_FILE_NAME_LITERTLM)
            if (litertlmFile.exists() && litertlmFile.length() > 0) return litertlmFile
            val taskFile = File(modelsDir, MODEL_FILE_NAME)
            if (taskFile.exists() && taskFile.length() > 0) return taskFile
            return litertlmFile
        }

    private val _activeRuntime = MutableStateFlow(
        InferenceRuntime.fromString(prefs.getString(KEY_RUNTIME, InferenceRuntime.LITERTLM.name))
    )
    val activeRuntime: StateFlow<InferenceRuntime> = _activeRuntime.asStateFlow()

    fun setRuntime(runtime: InferenceRuntime) {
        prefs.edit().putString(KEY_RUNTIME, runtime.name).apply()
        _activeRuntime.value = runtime
    }

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _activeModelId = MutableStateFlow(MODEL_GEMMA_3_1B)
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

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

    fun getInstalledModelInfo(): InstalledModelInfo {
        val exists = modelFile.exists() && modelFile.length() > 0
        val sizeBytes = if (exists) modelFile.length() else 0L
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
            filePath = modelFile.absolutePath,
            fileName = MODEL_FILE_NAME
        )
    }

    fun getAvailableModels(): List<ModelOption> {
        val info = getInstalledModelInfo()
        return listOf(
            ModelOption(
                id = MODEL_GEMMA_3_1B,
                name = "Gemma 3 1B (Int4)",
                sizeDescription = if (info.isInstalled) "Installed • ${info.formattedSize}" else "1.1 GB • ~1.9 GB RAM",
                ramRequiredMb = 2048,
                isInstalled = info.isInstalled,
                isLoaded = info.isInstalled && _isLoaded.value,
                localPath = if (info.isInstalled) modelFile.absolutePath else null,
                isPrimary = true,
                description = "Dedicated on-device personal assistant. Instruction-tuned for task planning, tool invocation, and private conversational assistance."
            )
        )
    }

    fun selectModel(modelId: String) {
        _activeModelId.value = modelId
        _isLoaded.value = modelFile.exists() && modelFile.length() > 0
    }

    fun deleteModel(modelId: String): Boolean {
        val target = modelFile
        val deleted = if (target.exists()) target.delete() else false
        if (deleted) {
            _isLoaded.value = false
        }
        return deleted
    }

    /**
     * Imports a model from an Android content URI (e.g. from Storage Access Framework / File Picker)
     * into private sandboxed storage with progress reporting and integrity verification.
     */
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
        } catch (_: Exception) {}

        val targetDestination = if (displayName.endsWith(".task", ignoreCase = true)) {
            File(modelsDir, MODEL_FILE_NAME)
        } else {
            File(modelsDir, MODEL_FILE_NAME_LITERTLM)
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
                        "Selected model file is only ${bytesCopied / (1024 * 1024)} MB. " +
                            "This is not a complete Gemma 3 1B bundle; import the full .litertlm or .task bundle."
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
     * Installs or imports a model file from a local File path.
     */
    fun installModelFile(sourceFile: File, expectedSha256: String? = null): Result<File> {
        if (!sourceFile.exists()) {
            return Result.failure(IllegalArgumentException("Source file does not exist: ${sourceFile.absolutePath}"))
        }

        if (sourceFile.length() < MIN_MODEL_BYTES) {
            return Result.failure(
                IllegalArgumentException(
                    "Source model file is only ${sourceFile.length() / (1024 * 1024)} MB. " +
                        "This is not a complete Gemma 3 1B bundle; import the full .task file."
                )
            )
        }

        val tempTarget = File(modelsDir, "${MODEL_FILE_NAME}.tmp")
        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempTarget).use { output ->
                    input.copyTo(output)
                }
            }

            if (!expectedSha256.isNullOrBlank()) {
                val actualHash = computeSha256(tempTarget)
                if (!actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                    tempTarget.delete()
                    return Result.failure(SecurityException("SHA-256 verification failed. Expected: $expectedSha256, Actual: $actualHash"))
                }
            }

            if (modelFile.exists()) modelFile.delete()
            if (!tempTarget.renameTo(modelFile)) {
                tempTarget.copyTo(modelFile, overwrite = true)
                tempTarget.delete()
            }

            _isLoaded.value = true
            return Result.success(modelFile)
        } catch (e: Exception) {
            tempTarget.delete()
            return Result.failure(e)
        }
    }

    /**
     * Downloads model weights from an HTTP/HTTPS endpoint with redirect resolution,
     * streaming directly to a temporary file, computing SHA-256 integrity, and reporting progress.
     */
    suspend fun downloadModel(
        url: String = DEFAULT_MODEL_DOWNLOAD_URL,
        expectedSha256: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDestination = if (url.contains(".task", ignoreCase = true)) {
            File(modelsDir, MODEL_FILE_NAME)
        } else {
            File(modelsDir, MODEL_FILE_NAME_LITERTLM)
        }
        val tempTarget = File(modelsDir, "${targetDestination.name}.download.tmp")
        var connection: java.net.HttpURLConnection? = null

        try {
            var currentUrl = url
            var redirects = 0
            val maxRedirects = 8

            // Handle HTTP 301, 302, 307, 308 redirects automatically (e.g. Hugging Face CDN links)
            while (redirects < maxRedirects) {
                val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "Tavi-Android-Assistant/1.0")

                val status = conn.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return@withContext Result.failure(java.io.IOException("Redirect with no Location header from $currentUrl"))
                    }
                    currentUrl = if (location.startsWith("http")) location else java.net.URL(java.net.URL(currentUrl), location).toString()
                    redirects++
                } else if (status in 200..299) {
                    connection = conn
                    break
                } else {
                    conn.disconnect()
                    return@withContext Result.failure(java.io.IOException("HTTP download error: $status ${conn.responseMessage}"))
                }
            }

            val finalConn = connection ?: return@withContext Result.failure(java.io.IOException("Too many redirects: $redirects"))
            val totalBytes = finalConn.contentLengthLong

            val digest = MessageDigest.getInstance("SHA-256")
            var bytesCopied = 0L

            finalConn.inputStream.use { input ->
                FileOutputStream(tempTarget).use { output ->
                    val buffer = ByteArray(65536)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesCopied += read
                        val progressRatio = if (totalBytes > 0) (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        _downloadProgress.value = mapOf(MODEL_GEMMA_3_1B to progressRatio)
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
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!expectedSha256.isNullOrBlank()) {
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
            tempTarget.delete()
            _downloadProgress.value = emptyMap()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // --- ComponentCallbacks2 Low-Memory Handling ---

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            _isLoaded.value = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        _isLoaded.value = false
    }
}
