package com.example.tavi.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.tavi.context.ContextBudgetManager
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import com.example.tavi.model.ToolCall
import com.example.tavi.tools.ToolRegistry
import com.example.tavi.workflow.WorkflowPlan
import com.example.tavi.workflow.WorkflowPlanner
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Primary on-device LLM Inference Engine for Tavi.
 *
 * Implements an interchangeable backend architecture supporting:
 * - [InferenceRuntime.GEMMA_4_E2B]: Google's modern Gemma 4 E2B LiteRT-LM runtime (Default).
 * - [InferenceRuntime.GEMMA_3_1B]: Low-RAM Gemma 3 1B LiteRT-LM runtime (Budget phones).
 * - [InferenceRuntime.MEDIAPIPE]: Legacy MediaPipe Tasks GenAI runtime (Rollback option).
 * - [InferenceRuntime.DETERMINISTIC_ONLY]: Pure deterministic rules-only operation.
 *
 * Guaranteed fallback chain:
 * Gemma 4 E2B -> Gemma 3 1B (if weights present) -> DeterministicAssistantEngine.
 * Never leaves the user in an unresponsive state.
 */
class LiteRtLlmEngine(
    private val context: Context,
    val modelPath: String? = null,
    initialRuntime: InferenceRuntime = InferenceRuntime.GEMMA_4_E2B,
    toolRegistry: ToolRegistry? = null
) : InferenceEngine, AutoCloseable {

    companion object {
        private const val TAG = "LiteRtLlmEngine"
        const val MODEL_IDENTIFIER = "gemma-4-e2b"
        const val RUNTIME_VERSION = "LiteRT-LM 0.18.1 (Gemma 4 E2B + Gemma 3 1B)"
        private const val MAX_TOKENS = 4096
        private const val MAX_RESPONSE_CHARS = 4000
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024

        private val CONVERSATIONAL_GREETINGS = setOf(
            "hi", "hello", "hey", "hey there", "hi there", "greetings", "good morning",
            "good afternoon", "good evening", "howdy", "sup", "yo", "how are you",
            "how are you doing", "help", "thanks", "thank you", "ok", "okay", "cool",
            "nice", "bye", "goodbye"
        )

        private val IDENTITY_QUERIES = setOf(
            "who are you", "what is your name", "whats your name", "what are you",
            "who is tavi", "what can you do", "introduce yourself", "tell me about yourself",
            "who created you", "who made you"
        )

        private val ACTION_INTENT_PATTERN = Regex(
            "(?i)\\b(send|text|sms|message|email|call|dial|phone|schedule|calendar|meeting|appointment|event|remind|reminder|alarm|timer|open|launch|play|pause|resume|stop|navigate|directions|map|search|find|look\\s+up|create|add|delete|cancel|update|set|turn\\s+(?:on|off)|enable|disable|contact|contacts|note|notes|battery|wifi|bluetooth|volume|brightness|flashlight|location|weather|agenda|todo|task|calculate|compute|convert)\\b"
        )
        private val MATH_INTENT_PATTERN = Regex(
            "(?i)\\b(calculate|compute|what\\s+is|what'?s)\\b.*\\d\\s*[+\\-*/x]\\s*\\d"
        )

        fun isLikelyActionRequest(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            return ACTION_INTENT_PATTERN.containsMatchIn(trimmed) ||
                MATH_INTENT_PATTERN.containsMatchIn(trimmed)
        }

        fun isConversationalGreeting(text: String): Boolean {
            val clean = text.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
            return clean in CONVERSATIONAL_GREETINGS ||
                   clean.startsWith("hi ") ||
                   clean.startsWith("hello ") ||
                   clean.startsWith("hey ")
        }

        fun isConversationalIdentity(text: String): Boolean {
            val clean = text.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")
            return clean in IDENTITY_QUERIES ||
                   clean.startsWith("who are you") ||
                   clean.startsWith("what is your name")
        }

        fun hasDegenerativeRepetition(text: CharSequence): Boolean {
            if (text.length < 16) return false
            val tail = text.takeLast(80).toString()
            if (Regex("""([a-zA-Z\s.,!?-])\1{6,}""").containsMatchIn(tail)) return true
            if (Regex("""([a-zA-Z\s.,!?-]{2,24}?)\1{2,}""").containsMatchIn(tail)) return true
            return false
        }

        fun cleanRepetitiveText(text: String): String {
            return text.replace(Regex("""([a-zA-Z\s.,!?-])\1{5,}"""), "$1").trimEnd()
        }

        fun historyWithoutCurrentPrompt(
            conversationHistory: List<Message>,
            currentPrompt: String
        ): List<Message> {
            val last = conversationHistory.lastOrNull()
            return if (last?.role == MessageRole.USER && last.content.trim() == currentPrompt.trim()) {
                conversationHistory.dropLast(1)
            } else {
                conversationHistory
            }
        }

        fun buildGemmaPrompt(
            history: List<Message>,
            currentPrompt: String,
            systemInstruction: String,
            maxHistoryMessages: Int = 10
        ): String {
            return ContextBudgetManager.buildGemma3Prompt(history, currentPrompt, systemInstruction)
        }
    }

    private val deterministicEngine = DeterministicAssistantEngine()
    private val toolRegistry: ToolRegistry = toolRegistry ?: ToolRegistry.getInstance(context)

    @Volatile
    var activeRuntime: InferenceRuntime = initialRuntime
        private set

    @Volatile
    private var gemma4Backend: Gemma4BackendEngine? = null

    @Volatile
    private var litertLmBackend: LiteRtLmBackendEngine? = null

    @Volatile
    private var mediaPipeBackend: MediaPipeGenAiBackendEngine? = null

    init {
        initActiveBackend(initialRuntime)
    }

    @Synchronized
    private fun initActiveBackend(runtime: InferenceRuntime) {
        when (runtime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> {
                if (gemma4Backend == null) {
                    gemma4Backend = Gemma4BackendEngine(context, modelPath, toolRegistry = toolRegistry)
                }
            }
            InferenceRuntime.GEMMA_3_1B -> {
                if (litertLmBackend == null) {
                    litertLmBackend = LiteRtLmBackendEngine(context, modelPath, toolRegistry = toolRegistry)
                }
            }
            InferenceRuntime.MEDIAPIPE -> {
                if (mediaPipeBackend == null) {
                    mediaPipeBackend = MediaPipeGenAiBackendEngine(context, modelPath, toolRegistry = toolRegistry)
                }
            }
            InferenceRuntime.DETERMINISTIC_ONLY -> Unit
        }
    }

    @Synchronized
    fun switchRuntime(newRuntime: InferenceRuntime) {
        if (activeRuntime == newRuntime) return
        Log.i(TAG, "Switching inference runtime: $activeRuntime -> $newRuntime")

        // Unload old engine to free memory
        when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> gemma4Backend?.unloadModel()
            InferenceRuntime.GEMMA_3_1B -> litertLmBackend?.unloadModel()
            InferenceRuntime.MEDIAPIPE -> mediaPipeBackend?.unloadModel()
            InferenceRuntime.DETERMINISTIC_ONLY -> Unit
        }

        activeRuntime = newRuntime
        initActiveBackend(newRuntime)
    }

    override val name: String
        get() = when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> gemma4Backend?.name ?: "Gemma 4 E2B (LiteRT-LM)"
            InferenceRuntime.GEMMA_3_1B -> litertLmBackend?.name ?: "Gemma 3 1B (LiteRT-LM)"
            InferenceRuntime.MEDIAPIPE -> mediaPipeBackend?.name ?: "Gemma 3 1B (MediaPipe Rollback)"
            InferenceRuntime.DETERMINISTIC_ONLY -> deterministicEngine.name
        }

    override val isLocal: Boolean = true

    override val isLoaded: Boolean
        get() = when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> gemma4Backend?.isLoaded ?: deterministicEngine.isLoaded
            InferenceRuntime.GEMMA_3_1B -> litertLmBackend?.isLoaded ?: deterministicEngine.isLoaded
            InferenceRuntime.MEDIAPIPE -> mediaPipeBackend?.isLoaded ?: deterministicEngine.isLoaded
            InferenceRuntime.DETERMINISTIC_ONLY -> deterministicEngine.isLoaded
        }

    val isGenuineInferenceActive: Boolean
        get() = when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> gemma4Backend?.isGenuineInferenceActive == true
            InferenceRuntime.GEMMA_3_1B -> litertLmBackend?.isGenuineInferenceActive == true
            InferenceRuntime.MEDIAPIPE -> mediaPipeBackend?.isGenuineInferenceActive == true
            InferenceRuntime.DETERMINISTIC_ONLY -> false
        }

    val hasModelWeights: Boolean
        get() = modelPath != null && File(modelPath).exists() && File(modelPath).length() > 0

    @Synchronized
    fun loadModel(): Result<Unit> {
        return when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> {
                initActiveBackend(activeRuntime)
                val primaryResult = gemma4Backend?.loadModel()
                if (primaryResult?.isSuccess == true) {
                    primaryResult
                } else {
                    // Fallback to 3-1B if present
                    initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                    val fallback31B = litertLmBackend?.loadModel()
                    if (fallback31B?.isSuccess == true) {
                        Log.i(TAG, "Gemma 4 E2B load failed; fallback to Gemma 3 1B succeeded")
                        fallback31B
                    } else {
                        primaryResult ?: Result.failure(IllegalStateException("No inference backend available"))
                    }
                }
            }
            InferenceRuntime.GEMMA_3_1B -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                litertLmBackend?.loadModel() ?: Result.failure(IllegalStateException("LiteRT-LM backend uninitialized"))
            }
            InferenceRuntime.MEDIAPIPE -> {
                initActiveBackend(InferenceRuntime.MEDIAPIPE)
                mediaPipeBackend?.loadModel() ?: Result.failure(IllegalStateException("MediaPipe backend uninitialized"))
            }
            InferenceRuntime.DETERMINISTIC_ONLY -> Result.success(Unit)
        }
    }

    @Synchronized
    fun unloadModel() {
        gemma4Backend?.unloadModel()
        litertLmBackend?.unloadModel()
        mediaPipeBackend?.unloadModel()
    }

    override fun close() {
        unloadModel()
        gemma4Backend?.close()
        litertLmBackend?.close()
        mediaPipeBackend?.close()
        gemma4Backend = null
        litertLmBackend = null
        mediaPipeBackend = null
    }

    override suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<Message>,
        onToken: (String) -> Unit
    ): String {
        if (hasModelWeights) {
            val loadRes = loadModel()
            if (loadRes.isFailure) {
                val err = "⚠️ Model failed to initialize from '${modelPath}': ${loadRes.exceptionOrNull()?.message}"
                onToken(err)
                return err
            }
        }

        return when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> {
                initActiveBackend(activeRuntime)
                val g4 = gemma4Backend
                if (g4 != null && (g4.isGenuineInferenceActive || g4.hasModelWeights)) {
                    val resp = g4.generateResponse(prompt, conversationHistory, onToken)
                    if (!resp.startsWith("⚠️ Gemma 4 E2B failed to initialize")) {
                        return resp
                    }
                }
                // Fallback to 3-1B
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                val g3 = litertLmBackend
                if (g3 != null && (g3.isGenuineInferenceActive || g3.hasModelWeights)) {
                    val resp = g3.generateResponse(prompt, conversationHistory, onToken)
                    if (!resp.startsWith("⚠️ Gemma 3 1B failed to initialize")) {
                        return resp
                    }
                }
                // Fallback to deterministic
                deterministicEngine.generateResponse(prompt, conversationHistory, onToken)
            }
            InferenceRuntime.GEMMA_3_1B -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                litertLmBackend?.generateResponse(prompt, conversationHistory, onToken)
                    ?: deterministicEngine.generateResponse(prompt, conversationHistory, onToken)
            }
            InferenceRuntime.MEDIAPIPE -> {
                initActiveBackend(InferenceRuntime.MEDIAPIPE)
                mediaPipeBackend?.generateResponse(prompt, conversationHistory, onToken)
                    ?: deterministicEngine.generateResponse(prompt, conversationHistory, onToken)
            }
            InferenceRuntime.DETERMINISTIC_ONLY -> {
                deterministicEngine.generateResponse(prompt, conversationHistory, onToken)
            }
        }
    }

    override suspend fun proposeToolCalls(
        userQuery: String,
        conversationHistory: List<Message>
    ): List<ToolCall> {
        if (hasModelWeights) {
            val loadRes = loadModel()
            if (loadRes.isFailure) {
                return emptyList()
            }
        }

        return when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> {
                initActiveBackend(activeRuntime)
                val g4 = gemma4Backend
                if (g4 != null && (g4.isGenuineInferenceActive || g4.hasModelWeights)) {
                    val calls = g4.proposeToolCalls(userQuery, conversationHistory)
                    if (calls.isNotEmpty()) return calls
                }
                // Fallback to 3-1B
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                val g3 = litertLmBackend
                if (g3 != null && (g3.isGenuineInferenceActive || g3.hasModelWeights)) {
                    val calls = g3.proposeToolCalls(userQuery, conversationHistory)
                    if (calls.isNotEmpty()) return calls
                }
                // Fallback to deterministic rules
                deterministicEngine.proposeToolCalls(userQuery, conversationHistory)
            }
            InferenceRuntime.GEMMA_3_1B -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                litertLmBackend?.proposeToolCalls(userQuery, conversationHistory) ?: emptyList()
            }
            InferenceRuntime.MEDIAPIPE -> {
                initActiveBackend(InferenceRuntime.MEDIAPIPE)
                mediaPipeBackend?.proposeToolCalls(userQuery, conversationHistory) ?: emptyList()
            }
            InferenceRuntime.DETERMINISTIC_ONLY -> {
                deterministicEngine.proposeToolCalls(userQuery, conversationHistory)
            }
        }
    }

    suspend fun planWorkflowWithGemma(
        userQuery: String,
        planner: WorkflowPlanner
    ): WorkflowPlan? {
        return when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                litertLmBackend?.planWorkflowWithGemma(userQuery, planner)
            }
            InferenceRuntime.GEMMA_3_1B -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                litertLmBackend?.planWorkflowWithGemma(userQuery, planner)
            }
            InferenceRuntime.MEDIAPIPE -> {
                initActiveBackend(InferenceRuntime.MEDIAPIPE)
                mediaPipeBackend?.planWorkflowWithGemma(userQuery, planner)
            }
            InferenceRuntime.DETERMINISTIC_ONLY -> null
        }
    }

    suspend fun synthesizeToolResultWithGemma(
        userQuery: String,
        toolName: String,
        resultJson: String
    ): String {
        return when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                val synthesized = litertLmBackend?.synthesizeToolResultWithGemma(userQuery, toolName, resultJson)
                if (!synthesized.isNullOrBlank()) synthesized else "Action executed ($toolName): $resultJson"
            }
            InferenceRuntime.GEMMA_3_1B -> {
                initActiveBackend(InferenceRuntime.GEMMA_3_1B)
                val synthesized = litertLmBackend?.synthesizeToolResultWithGemma(userQuery, toolName, resultJson)
                if (!synthesized.isNullOrBlank()) synthesized else "Action executed ($toolName): $resultJson"
            }
            InferenceRuntime.MEDIAPIPE -> {
                initActiveBackend(InferenceRuntime.MEDIAPIPE)
                val synthesized = mediaPipeBackend?.synthesizeToolResultWithGemma(userQuery, toolName, resultJson)
                if (!synthesized.isNullOrBlank()) synthesized else "Action executed ($toolName): $resultJson"
            }
            InferenceRuntime.DETERMINISTIC_ONLY -> "Action executed ($toolName): $resultJson"
        }
    }

    fun formatGemmaPrompt(
        userPrompt: String,
        conversationHistory: List<Message>,
        systemPrompt: String? = null
    ): String {
        val defaultSystem = "You are Tavi, an honest and concise on-device AI assistant for Android. Always respond politely and concisely as Tavi."
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
            append("- If no tool is needed, respond directly with conversational text without any tool block.\n\n")
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
        val baseDiagnostics = when (activeRuntime) {
            InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> gemma4Backend?.getEngineDiagnostics()
            InferenceRuntime.GEMMA_3_1B -> litertLmBackend?.getEngineDiagnostics()
            InferenceRuntime.MEDIAPIPE -> mediaPipeBackend?.getEngineDiagnostics()
            InferenceRuntime.DETERMINISTIC_ONLY -> deterministicEngine.getEngineDiagnostics()
        } ?: emptyMap()

        val targetFile = modelPath?.let { File(it) }
        val fileExists = targetFile?.exists() == true
        val fileSizeBytes = if (fileExists && targetFile != null) targetFile.length() else 0L

        val effectiveModelId = when {
            modelPath?.contains("e2b") == true || modelPath?.contains("gemma-4") == true -> "gemma-4-e2b"
            modelPath?.endsWith(".task") == true || modelPath?.contains("gemma-3") == true || modelPath?.contains("gemma_model") == true -> "gemma-3-1b-it"
            activeRuntime == InferenceRuntime.GEMMA_4_E2B -> "gemma-4-e2b"
            else -> "gemma-3-1b-it"
        }

        val mutableDiag = HashMap(baseDiagnostics)
        mutableDiag["activeRuntime"] = activeRuntime.name
        mutableDiag["runtimeDisplayName"] = activeRuntime.displayName
        mutableDiag["engineName"] = name
        mutableDiag["modelIdentifier"] = effectiveModelId
        mutableDiag["hasWeights"] = fileExists
        mutableDiag["weightsSizeBytes"] = fileSizeBytes
        mutableDiag["isGenuineInferenceActive"] = isGenuineInferenceActive
        mutableDiag["fallbackActive"] = !isGenuineInferenceActive

        if (!mutableDiag.containsKey("runtime")) {
            mutableDiag["runtime"] = when (activeRuntime) {
                InferenceRuntime.GEMMA_4_E2B -> "LiteRT-LM 0.18.1 (Gemma 4 E2B)"
                InferenceRuntime.LITERTLM -> LiteRtLmBackendEngine.RUNTIME_VERSION
                InferenceRuntime.GEMMA_3_1B -> LiteRtLmBackendEngine.RUNTIME_VERSION
                InferenceRuntime.MEDIAPIPE -> MediaPipeGenAiBackendEngine.RUNTIME_VERSION
                InferenceRuntime.DETERMINISTIC_ONLY -> "Native Deterministic Engine"
            }
        }

        return mutableDiag
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
