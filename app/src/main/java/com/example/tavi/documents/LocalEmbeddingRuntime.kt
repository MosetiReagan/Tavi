package com.example.tavi.documents

import ai.onnxruntime.*
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.example.tavi.models.LocalModelPacks
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

interface DocumentEmbedder {
    val fingerprint: String
    fun available(): Boolean
    suspend fun embed(text: String): FloatArray
}

class LocalEmbeddingRuntime private constructor(context: Context) : DocumentEmbedder, ComponentCallbacks2 {
    private val packs = LocalModelPacks.getInstance(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idle: Job? = null
    private var session: OrtSession? = null
    private var tokenizer: E5Tokenizer? = null
    override val fingerprint get() = "e5-mean-256-v1:${packs.pack("semantic-search").fingerprint}"
    init { context.registerComponentCallbacks(this) }
    override fun available() = packs.isInstalled("semantic-search")
    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) { mutex.withLock {
        require(text.isNotBlank())
        idle?.cancel()
        try {
            val dir = packs.requireVerified("semantic-search")
            if (session == null) {
                val parsed = E5Tokenizer.fromReader(File(dir, "tokenizer.json").reader())
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    session = OrtEnvironment.getEnvironment().createSession(File(dir, "model.onnx").path, options)
                }
                tokenizer = parsed
            }
            currentCoroutineContext().ensureActive()
            val ids = tokenizer!!.encode(text.take(16000))
            val shape = longArrayOf(1, ids.size.toLong())
            val env = OrtEnvironment.getEnvironment()
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { input ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1 }), shape).use { mask ->
                    val inputs = mutableMapOf("input_ids" to input, "attention_mask" to mask)
                    val typeIds = if ("token_type_ids" in session!!.inputNames) OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape) else null
                    try {
                        if (typeIds != null) inputs["token_type_ids"] = typeIds
                        session!!.run(inputs).use { result ->
                            currentCoroutineContext().ensureActive()
                            @Suppress("UNCHECKED_CAST")
                            val hidden = result[0].value as Array<Array<FloatArray>>
                            EmbeddingMath.meanNormalize(hidden.single())
                        }
                    } finally { typeIds?.close() }
                }
            }
        } finally {
            idle = scope.launch { delay(45_000); release() }
        }
    } }
    suspend fun release() = withContext(Dispatchers.IO) { mutex.withLock {
        releaseLocked()
    } }
    suspend fun removePack() = withContext(Dispatchers.IO) { mutex.withLock {
        releaseLocked()
        packs.remove("semantic-search")
    } }
    private fun releaseLocked() {
        idle?.cancel(); idle = null
        session?.close(); session = null; tokenizer = null
    }
    override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) scope.launch { release() } }
    override fun onLowMemory() { scope.launch { release() } }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    companion object {
        @Volatile private var instance: LocalEmbeddingRuntime? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: LocalEmbeddingRuntime(context.applicationContext).also { instance = it }
        }
    }
}

object EmbeddingMath {
    fun meanNormalize(hidden: Array<FloatArray>): FloatArray {
        require(hidden.isNotEmpty() && hidden.all { it.size == 384 })
        val sum = DoubleArray(384)
        for (token in hidden) for (i in sum.indices) { require(token[i].isFinite()); sum[i] += token[i] / hidden.size }
        val length = sqrt(sum.sumOf { it * it })
        require(length > 0 && length.isFinite()) { "Invalid embedding output." }
        return FloatArray(384) { (sum[it] / length).toFloat() }
    }
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size && a.isNotEmpty())
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa > 0 && bb > 0) (dot / sqrt(aa * bb)).coerceIn(-1.0, 1.0) else 0.0
    }
}
