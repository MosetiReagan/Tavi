package com.example.tavi.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.tavi.context.ContextBudgetManager
import com.example.tavi.context.ConversationLedger
import com.example.tavi.model.Message
import com.example.tavi.model.ToolCall
import com.example.tavi.tools.ToolRegistry
import com.example.tavi.workflow.WorkflowPlan
import com.example.tavi.workflow.WorkflowPlanner
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Modern On-Device LLM Inference Engine for Gemma 3 1B using Google's LiteRT-LM runtime.
 *
 * Implements hardware-optimized local inference with bounded CPU/GPU resource management,
 * token streaming, structured tool-call proposal extraction, and transparent fallback
 * to deterministic execution when weights are not present.
 */
class LiteRtLmBackendEngine(
    private val context: Context,
    val modelPath: String? = null,
    private val preferGpu: Boolean = false,
    private val cpuThreads: Int = 4,
    toolRegistry: ToolRegistry? = null
) : InferenceEngine, AutoCloseable {

    companion object {
        private const val TAG = "LiteRtLmBackendEngine"
        const val MODEL_IDENTIFIER = "gemma-3-1b-it"
        const val RUNTIME_VERSION = "LiteRT-LM 0.18.1"
        private const val MAX_TOKENS = 2048
        private const val MAX_RESPONSE_CHARS = 1600
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024
    }

    private val fallbackEngine = DeterministicAssistantEngine()
    private val toolRegistry: ToolRegistry = toolRegistry ?: ToolRegistry(context)
    private val inferenceMutex = Mutex()

    @Volatile
    private var engine: Engine? = null
    @Volatile
    private var session: Session? = null
    @Volatile
    private var activeBackend: String = "NONE"
    @Volatile
    private var initializationError: String? = null

    @Volatile
    private var cachedResponseQuery: String? = null
    @Volatile
    private var cachedResponseText: String? = null

    override val name: String
        get() = if (isGenuineInferenceActive) {
            "Gemma 3 1B (LiteRT-LM • $activeBackend)"
        } else if (hasModelWeights) {
            "Gemma 3 1B (LiteRT-LM • Uninitialized)"
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

    /**
     * Initializes the LiteRT-LM Engine and opens an active session.
     * Attempts GPU backend if configured; otherwise configures bounded CPU execution (4 threads).
     */
    @Synchronized
    fun loadModel(): Result<Unit> {
        if (engine != null && engine?.isInitialized() == true) return Result.success(Unit)

        if (modelPath == null || !File(modelPath).exists()) {
            val err = "Model weights file not found at: ${modelPath ?: "unspecified"}"
            initializationError = err
            return Result.failure(IllegalStateException(err))
        }

        val targetFile = File(modelPath)
        if (targetFile.length() == 0L) {
            val err = "Model weights file at '$modelPath' is empty (0 bytes)."
            initializationError = err
            return Result.failure(IllegalStateException(err))
        }

        if (targetFile.length() < MIN_MODEL_BYTES) {
            val err = "Model file is only ${targetFile.length() / (1024 * 1024)} MB. " +
                "The Gemma 3 1B bundle is incomplete or truncated; re-import the full .litertlm/.task bundle."
            initializationError = err
            return Result.failure(IllegalStateException(err))
        }

        // Try GPU if preferred
        if (preferGpu) {
            try {
                Log.i(TAG, "Initializing LiteRT-LM with GPU backend...")
                val gpuConfig = EngineConfig(
                    modelPath = targetFile.absolutePath,
                    backend = Backend.GPU(),
                    maxNumTokens = MAX_TOKENS,
                    cacheDir = context.cacheDir.absolutePath
                )
                val newEngine = Engine(gpuConfig)
                newEngine.initialize()
                val newSession = newEngine.createSession(
                    SessionConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0))
                )
                engine = newEngine
                session = newSession
                activeBackend = "GPU"
                initializationError = null
                Log.i(TAG, "LiteRT-LM Gemma 3 1B loaded successfully on GPU.")
                return Result.success(Unit)
            } catch (gpuError: Throwable) {
                Log.w(TAG, "LiteRT-LM GPU init failed, falling back to CPU: ${gpuError.message}")
            }
        }

        // Standard CPU execution with bounded thread count (Galaxy A14 4-thread CPU affinity)
        return try {
            Log.i(TAG, "Initializing LiteRT-LM with CPU backend ($cpuThreads threads)...")
            val cpuConfig = EngineConfig(
                modelPath = targetFile.absolutePath,
                backend = Backend.CPU(threadCount = cpuThreads),
                maxNumTokens = MAX_TOKENS,
                cacheDir = context.cacheDir.absolutePath
            )
            val newEngine = Engine(cpuConfig)
            newEngine.initialize()
            val newSession = newEngine.createSession(
                SessionConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0))
            )
            engine = newEngine
            session = newSession
            activeBackend = "CPU ($cpuThreads threads)"
            initializationError = null
            Log.i(TAG, "LiteRT-LM Gemma 3 1B loaded successfully on CPU.")
            Result.success(Unit)
        } catch (cpuError: Throwable) {
            val finalErr = "Failed to load Gemma 3 1B via LiteRT-LM runtime: ${cpuError.message}"
            Log.e(TAG, finalErr, cpuError)
            initializationError = finalErr
            unloadModel()
            Result.failure(cpuError)
        }
    }

    /**
     * Unloads model allocations and closes active LiteRT-LM session/engine.
     */
    @Synchronized
    fun unloadModel() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiteRT-LM session: ${e.message}")
        } finally {
            session = null
        }

        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiteRT-LM engine: ${e.message}")
        } finally {
            engine = null
            activeBackend = "NONE"
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
        if (engine == null && hasModelWeights) {
            val loadResult = loadModel()
            if (loadResult.isFailure) {
                val err = "⚠️ Gemma 3 1B failed to initialize from '${modelPath}': ${initializationError ?: loadResult.exceptionOrNull()?.message}.\n\nModel weights are installed on device, but the LiteRT-LM runtime failed to load. Please verify that the artifact is a valid Gemma 3 1B bundle."
                Log.e(TAG, err)
                onToken(err)
                return err
            }
        }

        val activeSession = session
        if (activeSession != null && isGenuineInferenceActive) {
            val clean = prompt.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
            if (LiteRtLlmEngine.isConversationalIdentity(clean)) {
                val reply = "I am Tavi, your private on-device assistant running locally on Android. Powered by Gemma 3 1B via Google LiteRT-LM, I can help you search contacts, send messages, check your calendar schedule, set reminders, and take notes—all 100% offline."
                onToken(reply)
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
                onToken(reply)
                return reply
            }

            consumeCachedConversationalResponse(prompt)?.let { cached ->
                onToken(cached)
                return cached
            }

            return generateLiteRtLmResponse(activeSession, prompt, conversationHistory, onToken)
        }

        return fallbackEngine.generateResponse(prompt, conversationHistory, onToken)
    }

    private suspend fun generateLiteRtLmResponse(
        activeSession: Session,
        prompt: String,
        conversationHistory: List<Message>,
        onToken: (String) -> Unit
    ): String = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val formattedPrompt = formatGemmaPrompt(prompt, conversationHistory)
            val startedAt = SystemClock.elapsedRealtime()
            val fullResponse = StringBuilder()
            var loopDetected = false

            // Allocate a clean generation session to guarantee zero KV cache duplication across multi-turn exchanges
            val targetEngine = engine
            val genSession = if (targetEngine != null && targetEngine.isInitialized()) {
                try {
                    targetEngine.createSession(
                        SessionConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3, seed = 0))
                    )
                } catch (_: Exception) {
                    activeSession
                }
            } else {
                activeSession
            }

            try {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : ResponseCallback {
                        override fun onNext(chunk: String) {
                            if (chunk.isNotEmpty() && !loopDetected) {
                                fullResponse.append(chunk)

                                if (fullResponse.length >= MAX_RESPONSE_CHARS) {
                                    loopDetected = true
                                    val capped = fullResponse.toString()
                                    try {
                                        genSession.cancelProcess()
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
                                        genSession.cancelProcess()
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed cancelling LiteRT-LM process on repetition loop", e)
                                    }
                                    if (continuation.isActive) continuation.resume(cleaned)
                                    return
                                }

                                onToken(chunk)
                            }
                        }

                        override fun onDone() {
                            Log.i(TAG, "LiteRT-LM generation finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
                            if (!loopDetected && continuation.isActive) {
                                continuation.resume(fullResponse.toString())
                            }
                        }

                        override fun onError(error: Throwable) {
                            Log.e(TAG, "LiteRT-LM streaming error: ${error.message}", error)
                            if (!loopDetected && continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        try {
                            genSession.cancelProcess()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error cancelling LiteRT-LM session: ${e.message}")
                        }
                    }

                    try {
                        genSession.generateContentStream(listOf(InputData.Text(formattedPrompt)), callback)
                    } catch (e: CancellationException) {
                        if (continuation.isActive) continuation.cancel(e)
                    } catch (e: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            } finally {
                if (genSession !== activeSession) {
                    try {
                        genSession.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed closing generation session", e)
                    }
                }
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
                Log.e(TAG, "LiteRT-LM Gemma 3 1B failed to load for tool proposal: $initializationError")
                return emptyList()
            }
        }

        if (LiteRtLlmEngine.isConversationalGreeting(userQuery)) return emptyList()

        val fastPathCalls = fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
        if (fastPathCalls.isNotEmpty()) return fastPathCalls

        if (!LiteRtLlmEngine.isLikelyActionRequest(userQuery)) return emptyList()

        val activeSession = session
        if (activeSession != null && isGenuineInferenceActive) {
            return proposeLiteRtLmToolCalls(activeSession, userQuery, conversationHistory)
        }

        return emptyList()
    }

    private suspend fun proposeLiteRtLmToolCalls(
        activeSession: Session,
        userQuery: String,
        conversationHistory: List<Message>
    ): List<ToolCall> = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val systemPrompt = toolRegistry.generateToolsSystemPrompt()
            val formattedPrompt = formatGemmaToolPrompt(userQuery, conversationHistory, systemPrompt)

            try {
                val startedAt = SystemClock.elapsedRealtime()
                val modelOutput = activeSession.generateContent(listOf(InputData.Text(formattedPrompt)))
                Log.i(TAG, "LiteRT-LM tool proposal finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
                val parsedCalls = parseToolCallsFromModelOutput(modelOutput)
                if (parsedCalls.isNotEmpty()) {
                    parsedCalls
                } else {
                    val fallbackCalls = fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
                    if (fallbackCalls.isEmpty() && modelOutput.isNotBlank() && !modelOutput.contains("```tool_call")) {
                        cacheConversationalResponse(userQuery, modelOutput.trim())
                    }
                    fallbackCalls
                }
            } catch (e: Exception) {
                Log.w(TAG, "LiteRT-LM tool proposal failed, falling back to deterministic: ${e.message}")
                fallbackEngine.proposeToolCalls(userQuery, conversationHistory)
            }
        }
    }

    suspend fun planWorkflowWithGemma(
        userQuery: String,
        planner: WorkflowPlanner
    ): WorkflowPlan? = inferenceMutex.withLock {
        val activeSession = session ?: return null
        withContext(Dispatchers.IO) {
            val schemaPrompt = """
                You are Tavi's multi-step task planner. For complex or ambiguous requests, produce a JSON plan:
                {
                  "title": "Short title",
                  "steps": [
                    {
                      "title": "Step description",
                      "toolName": "registered_tool_name",
                      "arguments": { "param": "val" },
                      "dynamicBindings": []
                    }
                  ]
                }
                Output ONLY the raw JSON object. Do not output markdown code fences or conversational text.
            """.trimIndent()
            val formatted = formatGemmaPrompt(
                userPrompt = "Plan the following multi-step task:\n$userQuery",
                conversationHistory = emptyList(),
                systemPrompt = schemaPrompt
            )
            try {
                val output = activeSession.generateContent(listOf(InputData.Text(formatted)))
                planner.parseAndValidateGemmaPlan(output, userQuery)
            } catch (e: Exception) {
                Log.w(TAG, "LiteRT-LM Gemma workflow planning failed: ${e.message}")
                null
            }
        }
    }

    private fun cacheConversationalResponse(query: String, response: String) {
        cachedResponseQuery = query
        cachedResponseText = response
    }

    private fun consumeCachedConversationalResponse(query: String): String? {
        if (cachedResponseQuery != query) return null
        val response = cachedResponseText
        cachedResponseQuery = null
        cachedResponseText = null
        return response
    }

    fun formatGemmaPrompt(
        userPrompt: String,
        conversationHistory: List<Message>,
        systemPrompt: String? = null
    ): String {
        val defaultSystem = "You are Tavi, a helpful and concise on-device AI assistant for Android. Your name is Tavi. You are not any contact or person mentioned in previous messages. Always respond politely and concisely as Tavi."
        val effectiveSystem = if (!systemPrompt.isNullOrBlank()) systemPrompt.trim() else defaultSystem
        val memories = try { com.example.tavi.memory.PersistentMemoryManager.retrieveRelevantMemories(context, userPrompt) } catch (_: Exception) { emptyList() }
        val episodes = try { com.example.tavi.memory.PersistentMemoryManager.retrieveRelevantEpisodes(context, userPrompt) } catch (_: Exception) { emptyList() }
        return ContextBudgetManager.buildGemma3Prompt(
            history = conversationHistory,
            currentPrompt = userPrompt,
            baseSystemInstruction = effectiveSystem,
            persistentMemories = memories,
            episodicMemories = episodes
        )
    }

    fun formatGemmaToolPrompt(
        userQuery: String,
        conversationHistory: List<Message>,
        toolsSystemPrompt: String
    ): String {
        val instructions = buildString {
            append(toolsSystemPrompt.trim()).append("\n\n")
            append("INSTRUCTIONS:\n")
            append("- If a tool call is required to fulfill the user request, output ONLY a ```tool_call JSON block.\n")
            append("- Do not include markdown preamble before or after the ```tool_call block.\n")
            append("- If no tool is needed (e.g. conversational questions or greetings), respond directly with conversational text without any tool block.\n\n")
            append("EXAMPLES:\n")
            append("User: Find Dennis in my contacts\n")
            append("Assistant: ```tool_call\n{\"tool\": \"contacts_search\", \"arguments\": {\"name\": \"Dennis\"}}\n```\n\n")
            append("User: What's on my calendar today?\n")
            append("Assistant: ```tool_call\n{\"tool\": \"calendar_list_events\", \"arguments\": {}}\n```\n\n")
            append("User: Message Dennis: Are we meeting at 3?\n")
            append("Assistant: ```tool_call\n{\"tool\": \"messages_send_sms\", \"arguments\": {\"recipient\": \"Dennis\", \"body\": \"Are we meeting at 3?\"}}\n```")
        }
        val memories = try { com.example.tavi.memory.PersistentMemoryManager.retrieveRelevantMemories(context, userQuery) } catch (_: Exception) { emptyList() }
        val episodes = try { com.example.tavi.memory.PersistentMemoryManager.retrieveRelevantEpisodes(context, userQuery) } catch (_: Exception) { emptyList() }
        return ContextBudgetManager.buildGemma3Prompt(
            history = conversationHistory,
            currentPrompt = userQuery,
            baseSystemInstruction = instructions,
            persistentMemories = memories,
            episodicMemories = episodes
        )
    }

    suspend fun synthesizeToolResultWithGemma(
        userQuery: String,
        toolName: String,
        resultJson: String
    ): String = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val activeSession = session ?: return@withContext "Action executed ($toolName): $resultJson"
            val prompt = """
                <start_of_turn>user
                You are Tavi, an honest on-device assistant.
                A tool action was executed on the device to fulfill the user's request.
                Summarize the result clearly and truthfully for the user.
                Never claim an action succeeded if the result shows an error.

                User Request: $userQuery
                Tool Executed: $toolName
                Result Data: $resultJson
                <end_of_turn>
                <start_of_turn>model
            """.trimIndent() + "\n"

            try {
                val output = activeSession.generateContent(listOf(InputData.Text(prompt))).trim()
                if (output.isNotBlank()) output else "Action executed ($toolName): $resultJson"
            } catch (e: Throwable) {
                Log.w(TAG, "Tool synthesis with LiteRT-LM failed: ${e.message}")
                "Action executed ($toolName): $resultJson"
            }
        }
    }

    fun parseToolCallsFromModelOutput(output: String): List<ToolCall> {
        val trimmed = output.trim()
        val toolCallRegex = Regex("""```(?:tool_call|json)?\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        val match = toolCallRegex.find(trimmed)
        val jsonCandidate = match?.groupValues?.get(1) ?: run {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed else null
        }

        if (jsonCandidate == null) return emptyList()

        return try {
            val json = JSONObject(jsonCandidate)
            val toolName = json.optString("tool").ifEmpty { json.optString("toolName") }
            if (toolName.isBlank()) return emptyList()

            val tool = toolRegistry.getTool(toolName) ?: return emptyList()

            val argsObj = json.optJSONObject("arguments") ?: JSONObject()
            val required = try {
                JSONObject(tool.parametersSchema).optJSONArray("required")
            } catch (_: Exception) {
                null
            }
            if (required != null) {
                for (i in 0 until required.length()) {
                    val requiredKey = required.optString(i)
                    if (requiredKey.isBlank() || !argsObj.has(requiredKey) || argsObj.isNull(requiredKey)) {
                        return emptyList()
                    }
                }
            }

            val arguments = mutableMapOf<String, Any>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "__user_confirmed") continue
                arguments[key] = argsObj.get(key)
            }

            listOf(
                ToolCall(
                    id = "call_${System.currentTimeMillis()}",
                    toolName = toolName,
                    arguments = arguments
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getEngineDiagnostics(): Map<String, Any> {
        val targetFile = modelPath?.let { File(it) }
        val fileExists = targetFile?.exists() == true
        val fileSizeBytes = if (fileExists && targetFile != null) targetFile.length() else 0L

        return mapOf(
            "engineName" to name,
            "modelIdentifier" to MODEL_IDENTIFIER,
            "runtime" to RUNTIME_VERSION,
            "hasWeights" to fileExists,
            "weightsSizeBytes" to fileSizeBytes,
            "isModelLoaded" to isGenuineInferenceActive,
            "activeBackend" to activeBackend,
            "isGenuineInferenceActive" to isGenuineInferenceActive,
            "fallbackActive" to !isGenuineInferenceActive,
            "initializationError" to (initializationError ?: "None")
        )
    }

    fun verifyModelIntegrity(expectedSha256: String): Boolean {
        if (!hasModelWeights) return false
        val file = File(modelPath!!)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return actualSha256.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
