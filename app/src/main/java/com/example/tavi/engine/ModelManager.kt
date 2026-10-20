package com.example.tavi.engine

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.tavi.model.ModelOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ModelManager(private val context: Context) : ComponentCallbacks2 {

    companion object {
        const val MODEL_GEMMA_3_1B = "gemma-3-1b-it"
        const val MODEL_QWEN_0_5B = "qwen2.5-0.5b-instruct"
        const val MODEL_GEMMA_3N_E2B = "gemma-3n-e2b"
    }

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _activeModelId = MutableStateFlow(MODEL_GEMMA_3_1B)
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isLoaded = MutableStateFlow(true)
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

    fun getAvailableModels(): List<ModelOption> {
        val gemma1bFile = File(modelsDir, "$MODEL_GEMMA_3_1B.task")
        val qwen05bFile = File(modelsDir, "$MODEL_QWEN_0_5B.litertlm")
        val gemma3nFile = File(modelsDir, "$MODEL_GEMMA_3N_E2B.task")

        return listOf(
            ModelOption(
                id = MODEL_GEMMA_3_1B,
                name = "Gemma 3 1B (Int4)",
                sizeDescription = "1.1 GB • ~1.9 GB RAM",
                ramRequiredMb = 2048,
                isInstalled = gemma1bFile.exists(),
                isLoaded = _activeModelId.value == MODEL_GEMMA_3_1B && _isLoaded.value,
                localPath = if (gemma1bFile.exists()) gemma1bFile.absolutePath else null,
                isPrimary = true,
                description = "Primary on-device assistant. Excellent instruction following and tool usage for 4GB+ RAM devices."
            ),
            ModelOption(
                id = MODEL_QWEN_0_5B,
                name = "Qwen2.5 0.5B (Int4)",
                sizeDescription = "350 MB • ~800 MB RAM",
                ramRequiredMb = 1024,
                isInstalled = qwen05bFile.exists(),
                isLoaded = _activeModelId.value == MODEL_QWEN_0_5B && _isLoaded.value,
                localPath = if (qwen05bFile.exists()) qwen05bFile.absolutePath else null,
                isPrimary = false,
                description = "Ultra-compact profile. Optimized for memory-constrained phones (<3GB RAM)."
            ),
            ModelOption(
                id = MODEL_GEMMA_3N_E2B,
                name = "Gemma 3n E2B (Int4)",
                sizeDescription = "2.2 GB • ~4.5 GB RAM",
                ramRequiredMb = 4600,
                isInstalled = gemma3nFile.exists(),
                isLoaded = _activeModelId.value == MODEL_GEMMA_3N_E2B && _isLoaded.value,
                localPath = if (gemma3nFile.exists()) gemma3nFile.absolutePath else null,
                isPrimary = false,
                description = "High-tier multimodal model. Designed for flagship phones with 8GB+ RAM."
            )
        )
    }

    fun selectModel(modelId: String) {
        _activeModelId.value = modelId
        _isLoaded.value = true
    }

    fun deleteModel(modelId: String): Boolean {
        val target = File(modelsDir, "$modelId.task").takeIf { it.exists() }
            ?: File(modelsDir, "$modelId.litertlm")
        return target.delete()
    }

    // --- ComponentCallbacks2 Low-Memory Handling ---

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            // Evict resident heavy model cache under extreme RAM pressure to prevent OS OOM kill
            _isLoaded.value = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        _isLoaded.value = false
    }
}
