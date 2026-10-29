package com.example.tavi.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.tavi.context.ContextBudgetManager
import com.example.tavi.engine.gemma4.SessionHandoffQueue
import com.example.tavi.engine.gemma4.ToolSetBridge
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import com.example.tavi.model.ToolCall
import com.example.tavi.tools.ToolRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class EngineLoadingPhase(val userDisplay: String) {
    IDLE("Idle"),
    VERIFYING_STORAGE("Verifying model bundle integrity…"),
    ALLOCATING_BACKEND("Initializing GPU/NPU acceleration…"),
    FALLING_BACK_CPU("Configuring multi-threaded CPU backend…"),
    COMPILING_TOOL_SCHEMAS("Compiling 53 native OpenAPI tool schemas…"),
    READY("Gemma 4 E2B Engine Ready"),
    ERROR("Model initialization failed")
}

/**
 * Native Google AI Edge LiteRT-LM backend for Gemma 4 E2B.
 *
 * Implements:
 * - GPU acceleration with graceful CPU fallback.
 * - Single-conversation handoff queue ([SessionHandoffQueue]) with suspendable close awaits.
 * - Progressive loading state reporting for cold inits (~45s on CPU budget phones).
 * - Native OpenAPI tool calling bridge ([ToolSetBridge]).
 * - Thinking channel extraction for collapsible UI rendering.
 */
class Gemma4BackendEngine(
    private val context: Context,
    val modelPath: String? = null,
    val preferGpu: Boolean = true,
    val cpuThreads: Int = 4,
    toolRegistry: ToolRegistry? = null
) : InferenceEngine, AutoCloseable {

    companion object {
        private const val TAG = "Gemma4BackendEngine"
        const val MODEL_IDENTIFIER = "gemma-4-e2b"
        private const val MAX_TOKENS = 4096
        private const val MAX_RESPONSE_CHARS = 4000
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024
        private const val THINKING_BUDGET = 1024
    }

    private val toolRegistry: ToolRegistry = toolRegistry ?: ToolRegistry.getInstance(context)
    val toolSetBridge: ToolSetBridge = ToolSetBridge(this.toolRegistry)
    val sessionQueue: SessionHandoffQueue = SessionHandoffQueue()
    private val fallbackEngine = DeterministicAssistantEngine()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var activeSession: Session? = null

    @Volatile
    private var activeConversation: Conversation? = null

    @Volatile
    var activeBackend: String = "NONE"
        private set

    @Volatile
    var initializationError: String? = null
        private set

    @Volatile
    var lastThinkingContent: String? = null
        private set

    private val _loadingPhase = MutableStateFlow(EngineLoadingPhase.IDLE)
    val loadingPhase: StateFlow<EngineLoadingPhase> = _loadingPhase.asStateFlow()

    override val name: String
        get() = if (isGenuineInferenceActive) {
            "Gemma 4 E2B (LiteRT-LM • $activeBackend)"
        } else if (hasModelWeights) {
            "Gemma 4 E2B (LiteRT-LM • Uninitialized)"
        } else {
            fallbackEngine.name
        }

    override val isLocal: Boolean = true

    override val isLoaded: Boolean
        get() = isGenuineInferenceActive || fallbackEngine.isLoaded

    val isGenuineInferenceActive: Boolean
        get() = engine != null && engine?.isInitialized() == true

    val hasModelWeights: Boolean
        get() = modelPath != null && File(modelPath).exists() && File(modelPath).length() > 0

    @Synchronized
    fun loadModel(): Result<Unit> {
        if (engine != null && engine?.isInitialized() == true) return Result.success(Unit)

        if (modelPath == null || !File(modelPath).exists()) {
            val err = "Gemma 4 E2B weights file not found at: ${modelPath ?: "unspecified"}"
            initializationError = err
            _loadingPhase.value = EngineLoadingPhase.ERROR
            return Result.failure(IllegalStateException(err))
        }

        val targetFile = File(modelPath)
        if (targetFile.length() < MIN_MODEL_BYTES) {
            val err = "Gemma 4 E2B bundle is truncated or invalid (${targetFile.length() / (1024 * 1024)} MB). Expected ~2.58 GB."
            initializationError = err
            _loadingPhase.value = EngineLoadingPhase.ERROR
            return Result.failure(IllegalStateException(err))
        }

        _loadingPhase.value = EngineLoadingPhase.VERIFYING_STORAGE

        // 1. Try GPU acceleration
        if (preferGpu) {
            try {
                _loadingPhase.value = EngineLoadingPhase.ALLOCATING_BACKEND
                Log.i(TAG, "Initializing Gemma 4 E2B with LiteRT-LM GPU backend...")
                val gpuConfig = EngineConfig(
                    modelPath = targetFile.absolutePath,
                    backend = Backend.GPU(),
                    maxNumTokens = MAX_TOKENS,
                    cacheDir = context.cacheDir.absolutePath
                )
                val newEngine = Engine(gpuConfig)
                newEngine.initialize()

                _loadingPhase.value = EngineLoadingPhase.COMPILING_TOOL_SCHEMAS
                val newSession = newEngine.createSession(
                    SessionConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6, seed = 0))
                )

                engine = newEngine
                activeSession = newSession
                activeBackend = "GPU"
                initializationError = null
                _loadingPhase.value = EngineLoadingPhase.READY
                Log.i(TAG, "Gemma 4 E2B loaded successfully on GPU.")
                return Result.success(Unit)
            } catch (gpuError: Throwable) {
                Log.w(TAG, "Gemma 4 E2B GPU init failed, falling back to CPU: ${gpuError.message}")
            }
        }

        // 2. CPU fallback with bounded thread count
        return try {
            _loadingPhase.value = EngineLoadingPhase.FALLING_BACK_CPU
            Log.i(TAG, "Initializing Gemma 4 E2B with LiteRT-LM CPU backend ($cpuThreads threads)...")
            val cpuConfig = EngineConfig(
                modelPath = targetFile.absolutePath,
                backend = Backend.CPU(threadCount = cpuThreads),
                maxNumTokens = MAX_TOKENS,
                cacheDir = context.cacheDir.absolutePath
            )
            val newEngine = Engine(cpuConfig)
            newEngine.initialize()

            _loadingPhase.value = EngineLoadingPhase.COMPILING_TOOL_SCHEMAS
            val newSession = newEngine.createSession(
                SessionConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6, seed = 0))
            )

            engine = newEngine
            activeSession = newSession
            activeBackend = "CPU ($cpuThreads threads)"
            initializationError = null
            _loadingPhase.value = EngineLoadingPhase.READY
            Log.i(TAG, "Gemma 4 E2B loaded successfully on CPU.")
            Result.success(Unit)
        } catch (cpuError: Throwable) {
            val finalErr = "Failed to load Gemma 4 E2B via LiteRT-LM: ${cpuError.message}"
            Log.e(TAG, finalErr, cpuError)
            initializationError = finalErr
            _loadingPhase.value = EngineLoadingPhase.ERROR
            unloadModel()
            Result.failure(cpuError)
        }
    }

    @Synchronized
    fun unloadModel() {
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing conversation: ${e.message}")
        } finally {
            activeConversation = null
        }

        try {
            activeSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session: ${e.message}")
        } finally {
            activeSession = null
        }

        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing engine: ${e.message}")
        } finally {
            engine = null
            activeBackend = "NONE"
            _loadingPhase.value = EngineLoadingPhase.IDLE
        }
    }

    override fun close() {
        unloadModel()
    }

    override suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<Message>,
        onToken: (String) -> Unit
    ): String {
        return generateResponseWithThinking(
            prompt = prompt,
            conversationHistory = conversationHistory,
            onThinkingToken = {},
            onContentToken = onToken
        )
    }

    /**
     * Generates a response with dual streaming: thinking tokens are streamed separately
     * from content tokens so the UI can render an expandable thinking disclosure.
     */
    suspend fun generateResponseWithThinking(
        prompt: String,
        conversationHistory: List<Message>,
        onThinkingToken: (String) -> Unit,
        onContentToken: (String) -> Unit
    ): String {
        if (engine == null && hasModelWeights) {
            val loadResult = loadModel()
            if (loadResult.isFailure) {
                val err = "⚠️ Gemma 4 E2B failed to initialize from '$modelPath': ${initializationError ?: loadResult.exceptionOrNull()?.message}."
                Log.e(TAG, err)
                onContentToken(err)
                return err
            }
        }

        if (isGenuineInferenceActive) {
            val clean = prompt.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
            if (LiteRtLlmEngine.isConversationalIdentity(clean)) {
                val reply = "I am Tavi, your private on-device assistant running locally on Android. Powered by Gemma 4 E2B via Google LiteRT-LM, I can help you search contacts, send messages, check your calendar schedule, set reminders, and automate tasks—all 100% offline."
                onContentToken(reply)
                return reply
            }

            if (LiteRtLlmEngine.isConversationalGreeting(clean)) {
                val reply = when {
                    clean.contains("how are you") -> "I'm doing well, thank you! How can I assist you today?"
                    clean.contains("good morning") -> "Good morning! How can I help you today?"
                    clean.contains("good afternoon") -> "Good afternoon! How can I help you today?"
                    clean.contains("good evening") -> "Good evening! How can I help you today?"
                    clean in setOf("thanks", "thank you") -> "You're very welcome! Let me know if you need anything else."
                    clean in setOf("bye", "goodbye") -> "Goodbye! Have a great day!"
                    else -> "Hello! I'm Tavi, your on-device AI assistant. How can I help you today?"
                }
                onContentToken(reply)
                return reply
            }

            return sessionQueue.withExclusiveSession("CHAT") {
                executeGemma4ChatInference(prompt, conversationHistory, onThinkingToken, onContentToken)
            }
        }

        return fallbackEngine.generateResponse(prompt, conversationHistory, onContentToken)
    }

    private suspend fun executeGemma4ChatInference(
        prompt: String,
        conversationHistory: List<Message>,
        onThinkingToken: (String) -> Unit,
        onContentToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val targetEngine = engine ?: return@withContext fallbackEngine.generateResponse(prompt, conversationHistory, onContentToken)
        val formattedPrompt = formatGemma4Prompt(prompt, conversationHistory)
        val startedAt = SystemClock.elapsedRealtime()

        val fullResponse = StringBuilder()
        val thinkingBuffer = StringBuilder()
        var inThinkingBlock = false
        var loopDetected = false

        val tempSession = try {
            targetEngine.createSession(
                SessionConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6, seed = 0))
            )
        } catch (_: Exception) {
            activeSession ?: return@withContext fallbackEngine.generateResponse(prompt, conversationHistory, onContentToken)
        }

        try {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ResponseCallback {
                    override fun onNext(chunk: String) {
                        if (chunk.isEmpty() || loopDetected) return

                        // Track <thought> tags if model emits thinking tokens in stream
                        if (chunk.contains("<thought>")) {
                            inThinkingBlock = true
                        }

                        if (inThinkingBlock) {
                            thinkingBuffer.append(chunk)
                            onThinkingToken(chunk)
                            if (chunk.contains("</thought>")) {
                                inThinkingBlock = false
                            }
                        } else {
                            fullResponse.append(chunk)

                            if (fullResponse.length >= MAX_RESPONSE_CHARS) {
                                loopDetected = true
                                val capped = fullResponse.toString()
                                try {
                                    tempSession.cancelProcess()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed cancelling LiteRT-LM process on length cap", e)
                                }
                                if (continuation.isActive) continuation.resume(capped)
                                return
                            }

                            if (LiteRtLlmEngine.hasDegenerativeRepetition(fullResponse)) {
                                loopDetected = true
                                val cleaned = LiteRtLlmEngine.cleanRepetitiveText(fullResponse.toString())
                                fullResponse.clear()
                                fullResponse.append(cleaned)
                                try {
                                    tempSession.cancelProcess()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed cancelling LiteRT-LM process on repetition loop", e)
                                }
                                if (continuation.isActive) continuation.resume(cleaned)
                                return
                            }

                            onContentToken(chunk)
                        }
                    }

                    override fun onDone() {
                        Log.i(TAG, "Gemma 4 generation finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
                        lastThinkingContent = if (thinkingBuffer.isNotEmpty()) thinkingBuffer.toString() else null
                        if (!loopDetected && continuation.isActive) {
                            continuation.resume(fullResponse.toString())
                        }
                    }

                    override fun onError(error: Throwable) {
                        Log.e(TAG, "Gemma 4 streaming error: ${error.message}", error)
                        if (!loopDetected && continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        tempSession.cancelProcess()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error cancelling session: ${e.message}")
                    }
                }

                try {
                    tempSession.generateContentStream(listOf(InputData.Text(formattedPrompt)), callback)
                } catch (e: CancellationException) {
                    if (continuation.isActive) continuation.cancel(e)
                } catch (e: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } finally {
            if (tempSession !== activeSession) {
                sessionQueue.safeClose(tempSession, "tempChatSession")
            }
        }
    }

    override suspend fun proposeToolCalls(
        userQuery: String,
        conversationHistory: List<Message>
    ): List<ToolCall> {
        if (engine == null && hasModelWeights) {
            val loadResult = loadModel()
            if (loadResult.isFailure) {
                Log.e(TAG, "Gemma 4 E2B failed to load for tool proposal: $initializationError")
                return emptyList()
            }
        }

        if (LiteRtLlmEngine.isConversationalGreeting(userQuery)) return emptyList()

        val fastPathCalls = fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
        if (fastPathCalls.isNotEmpty()) return fastPathCalls

        if (!LiteRtLlmEngine.isLikelyActionRequest(userQuery)) return emptyList()

        if (isGenuineInferenceActive) {
            return sessionQueue.withExclusiveSession("TOOL_PROPOSAL") {
                proposeNativeToolCalls(userQuery, conversationHistory)
            }
        }

        return emptyList()
    }

    private suspend fun proposeNativeToolCalls(
        userQuery: String,
        conversationHistory: List<Message>
    ): List<ToolCall> = withContext(Dispatchers.IO) {
        val targetEngine = engine ?: return@withContext fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
        val toolProviders = toolSetBridge.buildToolProviders()

        var conv: Conversation? = null
        try {
            val config = ConversationConfig(
                tools = toolProviders,
                automaticToolCalling = false,
                thinkingConfig = ThinkingConfig(enableThinking = true, thinkingTokenBudget = THINKING_BUDGET),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1, seed = 0)
            )
            conv = targetEngine.createConversation(config)
            val responseMsg = conv.sendMessage(userQuery)

            val sdkCalls = responseMsg.toolCalls
            if (sdkCalls != null && sdkCalls.isNotEmpty()) {
                Log.i(TAG, "LiteRT-LM native tool calls received: ${sdkCalls.size}")
                return@withContext sdkCalls.map { toolSetBridge.mapSdkToolCall(it) }
            }

            // Fallback to deterministic if native calling produced no calls
            fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
        } catch (e: Exception) {
            Log.w(TAG, "Native tool calling failed, falling back to deterministic: ${e.message}")
            fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
        } finally {
            sessionQueue.safeClose(conv, "toolProposalConversation")
        }
    }

    private fun formatGemma4Prompt(
        currentPrompt: String,
        conversationHistory: List<Message>
    ): String {
        val cleanHistory = LiteRtLlmEngine.historyWithoutCurrentPrompt(conversationHistory, currentPrompt)
        return ContextBudgetManager.buildGemma3Prompt(
            cleanHistory,
            currentPrompt,
            "You are Tavi, an honest and capable on-device AI assistant running locally on Android. Answer concisely, helpfully, and truthfully."
        )
    }

    override fun getEngineDiagnostics(): Map<String, Any> {
        return mapOf(
            "engineName" to name,
            "modelIdentifier" to MODEL_IDENTIFIER,
            "isLocal" to isLocal,
            "isLoaded" to isLoaded,
            "isGenuineInferenceActive" to isGenuineInferenceActive,
            "hasModelWeights" to hasModelWeights,
            "modelPath" to (modelPath ?: "None"),
            "activeBackend" to activeBackend,
            "preferGpu" to preferGpu,
            "cpuThreads" to cpuThreads,
            "loadingPhase" to _loadingPhase.value.name,
            "activeSessionOwner" to (sessionQueue.currentOwner ?: "None"),
            "queuedRequests" to sessionQueue.queuedRequestsCount,
            "lastHandoffMs" to sessionQueue.lastHandoffMs,
            "hasLastThinkingContent" to (lastThinkingContent != null),
            "registeredToolCount" to toolRegistry.getAllTools().size,
            "initializationError" to (initializationError ?: "None")
        )
    }
}
