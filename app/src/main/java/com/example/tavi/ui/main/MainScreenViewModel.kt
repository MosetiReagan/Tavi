package com.example.tavi.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tavi.data.TaviDatabase
import com.example.tavi.memory.PersistentMemoryManager
import com.example.tavi.memory.PersistentMemoryRecord
import com.example.tavi.memory.MemoryProvenance
import com.example.tavi.engine.DeterministicAssistantEngine
import com.example.tavi.engine.InferenceEngine
import com.example.tavi.engine.LiteRtLlmEngine
import com.example.tavi.engine.ModelManager
import com.example.tavi.model.ConfirmationRequest
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.tools.ToolRegistry
import com.example.tavi.voice.VoiceManager
import com.example.tavi.workflow.WorkflowEngine
import com.example.tavi.workflow.WorkflowPlan
import com.example.tavi.workflow.WorkflowPlanner
import com.example.tavi.workflow.WorkflowState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.UUID
import com.example.tavi.model.ConversationSession

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val conversations: List<com.example.tavi.model.ConversationSession> = emptyList(),
    val currentConversationId: String = "default",
    val currentConversationTitle: String = "New Chat",
    val isStreaming: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val pendingConfirmation: ConfirmationRequest? = null,
    val activeWorkflow: WorkflowPlan? = null,
    val isOffline: Boolean = false,
    val activeModelName: String = "Gemma 3 1B",
    val statusBanner: String? = null,
    val isModelInstalled: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatusText: String? = null,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val interimVoiceTranscript: String? = null,
    val userName: String? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TaviDatabase.getInstance(application)
    val toolRegistry = ToolRegistry(application)
    val modelManager = ModelManager.getInstance(application)
    val voiceManager = VoiceManager(application)
    val workflowPlanner = WorkflowPlanner(application, toolRegistry)
    val workflowEngine = WorkflowEngine(application, toolRegistry, db)

    private var activeEngine: InferenceEngine = LiteRtLlmEngine(
        application,
        modelManager.modelFile.let { if (it.exists() && it.length() > 0) it.absolutePath else null },
        modelManager.activeRuntime.value,
        toolRegistry
    )

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainScreenViewModel", "Unhandled coroutine error: ${throwable.message}", throwable)
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        checkModelInstallation()
        loadPersistedMessages()
        loadPersistedWorkflow()
        observeNetworkAndModel()
        observeVoiceState()
        observeDownloadProgress()
        preloadModel()
    }

    fun checkModelInstallation() {
        val info = modelManager.getInstalledModelInfo()
        _uiState.value = _uiState.value.copy(
            isModelInstalled = info.isInstalled,
            activeModelName = if (info.isInstalled) "Gemma 3 1B" else "Model Setup Required"
        )
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            modelManager.downloadProgress.collect { progressMap ->
                val progress = progressMap[ModelManager.MODEL_GEMMA_3_1B]
                if (progress != null && progress in 0f..1f) {
                    _uiState.value = _uiState.value.copy(
                        isDownloadingModel = true,
                        downloadProgress = progress,
                        downloadStatusText = "Downloading weights: ${(progress * 100).toInt()}%"
                    )
                } else if (_uiState.value.isDownloadingModel) {
                    val installed = modelManager.getInstalledModelInfo().isInstalled
                    _uiState.value = _uiState.value.copy(
                        isDownloadingModel = false,
                        isModelInstalled = installed,
                        downloadProgress = 0f,
                        downloadStatusText = null
                    )
                }
            }
        }
    }

    private fun preloadModel() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val engine = resolveActiveEngine()
                if (engine !is LiteRtLlmEngine || !engine.hasModelWeights || engine.isGenuineInferenceActive) {
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusBanner = "Loading Gemma 3 1B on device...")
                }

                val result = engine.loadModel()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        statusBanner = result.exceptionOrNull()?.let {
                            "Gemma 3 1B failed to load: ${it.message}"
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Model preload failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        statusBanner = "Gemma 3 1B failed to load: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun loadPersistedWorkflow() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val active = db.getActiveWorkflow()
                if (active != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(activeWorkflow = active)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Failed to load persisted workflow", e)
            }
        }
    }

    private fun resolveActiveEngine(): InferenceEngine {
        val targetFile = modelManager.modelFile
        val path = if (targetFile.exists() && targetFile.length() > 0) targetFile.absolutePath else null
        val runtime = modelManager.activeRuntime.value
        val currentEngine = activeEngine
        if (currentEngine is LiteRtLlmEngine && currentEngine.modelPath == path && currentEngine.activeRuntime == runtime) {
            return currentEngine
        }
        (currentEngine as? AutoCloseable)?.close()
        val newEngine = LiteRtLlmEngine(getApplication(), path, runtime, toolRegistry)
        activeEngine = newEngine
        return newEngine
    }

    private fun loadPersistedMessages() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val convs = db.getConversations()
                val activeConv = convs.firstOrNull() ?: run {
                    val initial = com.example.tavi.model.ConversationSession(id = "default", title = "New Chat")
                    db.insertOrUpdateConversation(initial)
                    initial
                }
                val saved = db.getMessagesForConversation(activeConv.id)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        conversations = convs.ifEmpty { listOf(activeConv) },
                        currentConversationId = activeConv.id,
                        currentConversationTitle = activeConv.title,
                        messages = saved
                    )
                }
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Failed to load persisted messages", e)
            }
        }
    }

    private fun observeNetworkAndModel() {
        viewModelScope.launch {
            modelManager.isOffline.collect { offline ->
                _uiState.value = _uiState.value.copy(isOffline = offline)
            }
        }
        viewModelScope.launch {
            modelManager.activeModelId.collect { modelId ->
                val modelOption = modelManager.getAvailableModels().find { it.id == modelId }
                val displayName = modelOption?.name ?: "Local Assistant"
                val path = modelOption?.localPath
                val runtime = modelManager.activeRuntime.value
                val currentEngine = activeEngine
                if (currentEngine is LiteRtLlmEngine && currentEngine.modelPath == path && currentEngine.activeRuntime == runtime) {
                    _uiState.value = _uiState.value.copy(activeModelName = displayName)
                    return@collect
                }
                (currentEngine as? AutoCloseable)?.close()
                activeEngine = LiteRtLlmEngine(getApplication(), path, runtime, toolRegistry)
                _uiState.value = _uiState.value.copy(activeModelName = displayName)
            }
        }
        viewModelScope.launch {
            modelManager.activeRuntime.collect { runtime ->
                val currentEngine = activeEngine
                if (currentEngine is LiteRtLlmEngine && currentEngine.activeRuntime != runtime) {
                    currentEngine.switchRuntime(runtime)
                    val modelOption = modelManager.getAvailableModels().find { it.id == modelManager.activeModelId.value }
                    val baseName = modelOption?.name ?: "Gemma 3 1B"
                    _uiState.value = _uiState.value.copy(activeModelName = "$baseName (${runtime.displayName})")
                }
            }
        }
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceManager.isListening.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }
        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isStreaming) return

        val currentConvId = _uiState.value.currentConversationId

        // Auto-update conversation title from initial message if default
        if (_uiState.value.currentConversationTitle == "New Chat" || _uiState.value.currentConversationTitle.isBlank()) {
            val smartTitle = trimmed.lines().firstOrNull()?.trim()?.take(36)?.let {
                if (it.length >= 36) "$it..." else it
            } ?: "Conversation"
            viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                db.updateConversationTitle(currentConvId, smartTitle)
                val updatedConvs = db.getConversations()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        currentConversationTitle = smartTitle,
                        conversations = updatedConvs
                    )
                }
            }
        }

        // 1. If an active workflow is waiting for user input, feed input directly
        val currentWorkflow = _uiState.value.activeWorkflow
        if (currentWorkflow != null && currentWorkflow.state == WorkflowState.AWAITING_USER_INPUT) {
            val userMsg = Message(conversationId = currentConvId, role = MessageRole.USER, content = trimmed)
            val updatedMessages = _uiState.value.messages + userMsg
            _uiState.value = _uiState.value.copy(messages = updatedMessages, isStreaming = true)

            viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                try {
                    db.insertMessage(userMsg, currentConvId)
                    val resumed = workflowEngine.provideMissingInput(currentWorkflow, trimmed) { updated ->
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(activeWorkflow = updated)
                        }
                    }
                    handleWorkflowCompletion(resumed, updatedMessages)
                } catch (e: Exception) {
                    Log.e("MainScreenViewModel", "Error resuming workflow with input", e)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                    }
                }
            }
            return
        }

        val userMessage = Message(
            conversationId = currentConvId,
            role = MessageRole.USER,
            content = trimmed
        )

        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            isStreaming = true,
            statusBanner = null
        )

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                db.insertMessage(userMessage, currentConvId)

                // 1.5. Explicit Personal Memory Management (Instant deterministic handling)
                val memCommand = PersistentMemoryManager.parseMemoryCommand(trimmed)
                if (memCommand != null) {
                    handleMemoryCommand(memCommand, userMessage, updatedMessages)
                    return@launch
                }

                // 2. Multi-Step Workflow Planning (Deterministic fast-path or Gemma 3 1B)
                val plannedWorkflow = workflowPlanner.planWorkflow(trimmed) ?: run {
                    if (!workflowPlanner.shouldAttemptModelPlanning(trimmed)) {
                        null
                    } else {
                        val engine = resolveActiveEngine()
                        if (engine is LiteRtLlmEngine) {
                            engine.planWorkflowWithGemma(trimmed, workflowPlanner)
                        } else null
                    }
                }

                if (plannedWorkflow != null && plannedWorkflow.steps.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(activeWorkflow = plannedWorkflow)
                    }
                    val finalWorkflow = workflowEngine.executePlan(plannedWorkflow) { updated ->
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(activeWorkflow = updated)
                        }
                    }
                    handleWorkflowCompletion(finalWorkflow, updatedMessages)
                } else {
                    processAssistantTurn(userMessage, updatedMessages)
                }
            } catch (e: Throwable) {
                val errorMsg = Message(
                    role = MessageRole.ASSISTANT,
                    content = "⚠️ An error occurred while generating a response: ${e.message ?: "Unknown error"}"
                )
                db.insertMessage(errorMsg)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages + errorMsg,
                        isStreaming = false
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isStreaming = false)
                }
            }
        }
    }

    private suspend fun handleWorkflowCompletion(workflow: WorkflowPlan, currentMessages: List<Message>) {
        if (workflow.state == WorkflowState.AWAITING_CONFIRMATION || workflow.state == WorkflowState.AWAITING_USER_INPUT) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isStreaming = false)
            }
            return
        }

        val summary = workflow.executionSummary?.humanReadableText
            ?: when (workflow.state) {
                WorkflowState.COMPLETED -> "Workflow completed successfully."
                WorkflowState.PARTIALLY_COMPLETED -> "Workflow completed with some skipped or unavailable steps."
                WorkflowState.CANCELLED -> "Workflow was cancelled."
                WorkflowState.FAILED -> "Workflow could not be completed."
                else -> "Action updated."
            }

        val verifiedOutcomes = workflow.executionSummary?.verifiedOutcomes ?: emptyList()
        val formattedContent = buildString {
            append("📋 **${workflow.title}**\n\n$summary")
            if (verifiedOutcomes.isNotEmpty()) {
                append("\n\n**Verified State Changes:**\n")
                for (v in verifiedOutcomes) {
                    append("• $v\n")
                }
            }
        }

        val assistantMsg = Message(
            role = MessageRole.ASSISTANT,
            content = formattedContent.trim(),
            toolResults = workflow.steps.mapNotNull { it.result }
        )
        db.insertMessage(assistantMsg)

        if (workflow.state == WorkflowState.COMPLETED) {
            try {
                PersistentMemoryManager.recordEpisodicMemory(
                    context = getApplication(),
                    title = workflow.title,
                    summary = summary,
                    decisionRationale = "Completed workflow with ${workflow.steps.size} steps",
                    outcome = "SUCCESS",
                    taskStatus = "COMPLETED",
                    tags = listOf("workflow", workflow.title.lowercase())
                )
            } catch (e: Exception) {
                Log.w("MainScreenViewModel", "Failed to record episodic memory for workflow", e)
            }
        }

        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                messages = currentMessages + assistantMsg,
                isStreaming = false
            )
        }
    }

    fun confirmWorkflowStep(stepId: String, confirmed: Boolean) {
        val workflow = _uiState.value.activeWorkflow ?: return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val updated = workflowEngine.confirmStep(workflow, stepId, confirmed) { plan ->
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(activeWorkflow = plan)
                    }
                }
                handleWorkflowCompletion(updated, _uiState.value.messages)
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Error confirming workflow step", e)
            }
        }
    }

    fun cancelWorkflow() {
        val workflow = _uiState.value.activeWorkflow ?: return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val cancelled = workflowEngine.cancelWorkflow(workflow) { plan ->
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(activeWorkflow = plan)
                    }
                }
                handleWorkflowCompletion(cancelled, _uiState.value.messages)
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Error cancelling workflow", e)
            }
        }
    }

    fun dismissWorkflow() {
        _uiState.value = _uiState.value.copy(activeWorkflow = null)
    }

    private suspend fun handleMemoryCommand(
        command: PersistentMemoryManager.MemoryExtractionResult,
        userMessage: Message,
        currentMessages: List<Message>
    ) {
        val app = getApplication<Application>()
        val replyText = when (command) {
            is PersistentMemoryManager.MemoryExtractionResult.SaveCommand -> {
                when (val res = PersistentMemoryManager.saveOrUpdateMemory(app, command.record)) {
                    is PersistentMemoryManager.SaveResult.Saved -> {
                        "Got it. I'll remember that your ${command.record.key.replace('_', ' ')} is ${command.record.value}."
                    }
                    is PersistentMemoryManager.SaveResult.Blocked -> {
                        "⚠️ I cannot save that to memory: ${res.reason}"
                    }
                    is PersistentMemoryManager.SaveResult.Error -> {
                        "Failed to save memory: ${res.message}"
                    }
                }
            }
            is PersistentMemoryManager.MemoryExtractionResult.CorrectionCommand -> {
                val record = PersistentMemoryRecord(
                    key = command.key,
                    value = command.newValue,
                    category = command.category,
                    provenance = MemoryProvenance.USER_EXPLICIT
                )
                when (val res = PersistentMemoryManager.saveOrUpdateMemory(app, record)) {
                    is PersistentMemoryManager.SaveResult.Saved -> {
                        "I've updated your memory: your ${command.key.replace('_', ' ')} is now ${command.newValue}."
                    }
                    is PersistentMemoryManager.SaveResult.Blocked -> {
                        "⚠️ I cannot update that in memory: ${res.reason}"
                    }
                    is PersistentMemoryManager.SaveResult.Error -> {
                        "Failed to update memory: ${res.message}"
                    }
                }
            }
            is PersistentMemoryManager.MemoryExtractionResult.ForgetCommand -> {
                val deleted = PersistentMemoryManager.deleteMemory(app, command.targetKeyOrTopic)
                if (deleted) {
                    "I've forgotten your memory of ${command.targetKeyOrTopic.replace('_', ' ')}."
                } else {
                    "I couldn't find an active memory for '${command.targetKeyOrTopic.replace('_', ' ')}'."
                }
            }
            is PersistentMemoryManager.MemoryExtractionResult.QueryCommand -> {
                if (!PersistentMemoryManager.isMemoryEnabled(app)) {
                    "Persistent memory is currently disabled in settings."
                } else {
                    val active = db.getActivePersistentMemories()
                    if (active.isEmpty()) {
                        "I don't have any saved personal memories for you yet."
                    } else {
                        buildString {
                            append("Here are your saved memories:\n")
                            for (m in active) {
                                append("• ").append(m.key.replace('_', ' ')).append(": ").append(m.value).append("\n")
                            }
                        }.trimEnd()
                    }
                }
            }
            is PersistentMemoryManager.MemoryExtractionResult.ClearAllCommand -> {
                val req = com.example.tavi.security.SecurityGuard.createConfirmationRequest("memory_clear_all", emptyMap())
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        pendingConfirmation = req,
                        isStreaming = false
                    )
                }
                return
            }
        }

        val assistantMsg = Message(
            conversationId = userMessage.conversationId,
            role = MessageRole.ASSISTANT,
            content = replyText
        )
        db.insertMessage(assistantMsg, userMessage.conversationId)
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                messages = currentMessages + assistantMsg,
                isStreaming = false
            )
        }
    }

    private suspend fun processAssistantTurn(userMessage: Message, currentHistory: List<Message>) {
        val engine = resolveActiveEngine()
        val needsModelLoad = engine is LiteRtLlmEngine && engine.hasModelWeights && !engine.isGenuineInferenceActive
        if (needsModelLoad) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(statusBanner = "Loading Gemma 3 1B on device...")
            }
        }

        // 1. Propose tool calls
        val proposedTools = try {
            engine.proposeToolCalls(userMessage.content, currentHistory)
        } finally {
            if (needsModelLoad) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusBanner = null)
                }
            }
        }

        if (proposedTools.isNotEmpty()) {
            executeToolsAndSynthesize(proposedTools, currentHistory)
        } else {
            // Conversational direct reply
            val streamingMessage = Message(
                conversationId = userMessage.conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true
            )

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    messages = currentHistory + streamingMessage,
                    isStreaming = true
                )
            }

            val buffer = StringBuilder()
            val finalReply = engine.generateResponse(
                prompt = userMessage.content,
                conversationHistory = currentHistory,
                onToken = { token ->
                    buffer.append(token)
                    val currentMsgs = _uiState.value.messages.toMutableList()
                    if (currentMsgs.isNotEmpty() && currentMsgs.last().isStreaming) {
                        val updatedLast = currentMsgs.last().copy(content = buffer.toString())
                        currentMsgs[currentMsgs.lastIndex] = updatedLast
                        _uiState.value = _uiState.value.copy(messages = currentMsgs)
                    }
                }
            )

            val finalizedMessage = streamingMessage.copy(
                content = finalReply,
                isStreaming = false
            )
            db.insertMessage(finalizedMessage, userMessage.conversationId)

            withContext(Dispatchers.Main) {
                val currentMsgs = _uiState.value.messages.toMutableList()
                if (currentMsgs.isNotEmpty() && currentMsgs.last().isStreaming) {
                    currentMsgs[currentMsgs.lastIndex] = finalizedMessage
                } else {
                    currentMsgs.add(finalizedMessage)
                }
                _uiState.value = _uiState.value.copy(
                    messages = currentMsgs,
                    isStreaming = false
                )
            }
        }
    }

    private suspend fun executeToolsAndSynthesize(
        tools: List<ToolCall>,
        currentHistory: List<Message>
    ) {
        val executedResults = mutableListOf<ToolResult>()
        var pendingConfirmationReq: ConfirmationRequest? = null

        for (toolCall in tools) {
            val result = toolRegistry.executeToolCall(toolCall, userConfirmed = false)
            executedResults.add(result)
            if (result.requiresUserConfirmation) {
                pendingConfirmationReq = result.confirmationPayload
                break // Pause execution and wait for user confirmation
            }
        }

        if (pendingConfirmationReq != null) {
            // Need user confirmation dialog
            val pauseMsg = Message(
                role = MessageRole.ASSISTANT,
                content = "This action requires your explicit confirmation before proceeding.",
                toolCalls = tools,
                toolResults = executedResults
            )
            db.insertMessage(pauseMsg)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    messages = currentHistory + pauseMsg,
                    isStreaming = false,
                    pendingConfirmation = pendingConfirmationReq
                )
            }
            return
        }

        // Synthesize tool output to truthful, verified local explanation
        val deterministicSummary = ToolResultSynthesizer.synthesize(tools, executedResults)
        val finalSummary = if (deterministicSummary.isNotBlank()) {
            deterministicSummary
        } else {
            "Action executed: ${tools.firstOrNull()?.toolName ?: "Success"}"
        }

        val currentConvId = _uiState.value.currentConversationId
        val assistantMessage = Message(
            conversationId = currentConvId,
            role = MessageRole.ASSISTANT,
            content = finalSummary,
            toolCalls = tools,
            toolResults = executedResults,
            isStreaming = false
        )
        db.insertMessage(assistantMessage, currentConvId)

        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                messages = currentHistory + assistantMessage,
                isStreaming = false
            )
        }
    }

    fun confirmPendingAction(confirmed: Boolean) {
        val req = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)

        val currentConvId = _uiState.value.currentConversationId
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                if (!confirmed) {
                    val cancelMsg = Message(
                        conversationId = currentConvId,
                        role = MessageRole.ASSISTANT,
                        content = "Action '${req.title}' was cancelled by your request."
                    )
                    db.insertMessage(cancelMsg, currentConvId)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + cancelMsg)
                    }
                    return@launch
                }

                // User confirmed: execute tool with userConfirmed = true
                val toolCall = ToolCall(toolName = req.toolName, arguments = req.arguments)
                val result = toolRegistry.executeToolCall(toolCall, userConfirmed = true)

                val finalSummary = if (result.isSuccess) {
                    if (req.toolName == "messages_send_sms") {
                        val recipient = req.arguments["recipient"] ?: req.arguments["phoneNumber"] ?: "recipient"
                        val body = req.arguments["body"] ?: ""
                        "✅ SMS text dispatched to $recipient: \"$body\"."
                    } else {
                        "Action confirmed and completed successfully: ${req.title}."
                    }
                } else {
                    "Action failed: ${result.errorMessage}"
                }

                val resultMsg = Message(
                    conversationId = currentConvId,
                    role = MessageRole.ASSISTANT,
                    content = finalSummary,
                    toolCalls = listOf(toolCall),
                    toolResults = listOf(result)
                )
                db.insertMessage(resultMsg, currentConvId)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + resultMsg)
                }
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Error executing confirmed action", e)
            }
        }
    }

    fun startVoiceInput(onPartialText: (String) -> Unit, onFinalText: (String) -> Unit) {
        if (_uiState.value.isListening) {
            voiceManager.stopListening()
            _uiState.value = _uiState.value.copy(interimVoiceTranscript = null)
            return
        }
        voiceManager.startListening(
            onPartialResult = { partial ->
                _uiState.value = _uiState.value.copy(interimVoiceTranscript = partial)
                onPartialText(partial)
            },
            onResult = { spokenText ->
                _uiState.value = _uiState.value.copy(interimVoiceTranscript = null)
                onFinalText(spokenText)
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    interimVoiceTranscript = null,
                    errorMessage = "Voice error: $error"
                )
            }
        )
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
        _uiState.value = _uiState.value.copy(interimVoiceTranscript = null)
    }

    fun toggleVoiceInput() {
        if (_uiState.value.isListening) {
            stopVoiceInput()
        } else {
            voiceManager.startListening(
                onPartialResult = { partial ->
                    _uiState.value = _uiState.value.copy(interimVoiceTranscript = partial)
                },
                onResult = { spokenText ->
                    _uiState.value = _uiState.value.copy(interimVoiceTranscript = null)
                    sendMessage(spokenText)
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        interimVoiceTranscript = null,
                        errorMessage = "Voice error: $error"
                    )
                }
            )
        }
    }

    fun speakMessage(content: String) {
        if (content.isNotBlank()) {
            voiceManager.speak(content)
        }
    }

    fun speakLastMessage() {
        val lastMsg = _uiState.value.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (lastMsg != null) {
            voiceManager.speak(lastMsg.content)
        }
    }

    fun downloadModel() {
        if (_uiState.value.isDownloadingModel) return
        _uiState.value = _uiState.value.copy(
            isDownloadingModel = true,
            downloadProgress = 0.01f,
            downloadStatusText = "Connecting to download server (557 MB)...",
            errorMessage = null
        )
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val result = modelManager.downloadModel { downloaded, total ->
                val ratio = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                val mbDownloaded = downloaded / (1024 * 1024)
                val mbTotal = total / (1024 * 1024)
                _uiState.value = _uiState.value.copy(
                    downloadProgress = ratio,
                    downloadStatusText = "$mbDownloaded MB / $mbTotal MB (${(ratio * 100).toInt()}%)"
                )
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    activeEngine = LiteRtLlmEngine(
                        getApplication(),
                        modelManager.modelFile.absolutePath,
                        modelManager.activeRuntime.value,
                        toolRegistry
                    )
                    _uiState.value = _uiState.value.copy(
                        isDownloadingModel = false,
                        isModelInstalled = true,
                        downloadProgress = 1f,
                        downloadStatusText = null,
                        activeModelName = "Gemma 3 1B",
                        snackbarMessage = "Gemma 3 1B model downloaded and verified! Local inference ready."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDownloadingModel = false,
                        downloadProgress = 0f,
                        downloadStatusText = null,
                        errorMessage = "Download failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun startNewChat() {
        val newId = UUID.randomUUID().toString()
        val newSession = ConversationSession(id = newId, title = "New Chat")
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            db.insertOrUpdateConversation(newSession)
            val allConvs = db.getConversations()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    conversations = allConvs,
                    currentConversationId = newId,
                    currentConversationTitle = "New Chat",
                    messages = emptyList(),
                    statusBanner = null
                )
            }
        }
    }

    fun selectConversation(convId: String) {
        if (convId == _uiState.value.currentConversationId) return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val convs = db.getConversations()
            val target = convs.find { it.id == convId } ?: return@launch
            val messages = db.getMessagesForConversation(convId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    conversations = convs,
                    currentConversationId = target.id,
                    currentConversationTitle = target.title,
                    messages = messages,
                    statusBanner = null
                )
            }
        }
    }

    private var lastDeletedConversation: Pair<ConversationSession, List<Message>>? = null

    fun deleteConversation(convId: String) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val session = db.getConversations().find { it.id == convId }
            val msgs = db.getMessagesForConversation(convId)
            if (session != null) {
                lastDeletedConversation = Pair(session, msgs)
            }
            db.deleteConversation(convId)
            val remaining = db.getConversations()
            val next = remaining.firstOrNull() ?: run {
                val fresh = ConversationSession(id = UUID.randomUUID().toString(), title = "New Chat")
                db.insertOrUpdateConversation(fresh)
                fresh
            }
            val messages = db.getMessagesForConversation(next.id)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    conversations = db.getConversations(),
                    currentConversationId = next.id,
                    currentConversationTitle = next.title,
                    messages = messages,
                    snackbarMessage = "Chat deleted."
                )
            }
        }
    }

    fun undoDeleteConversation() {
        val backup = lastDeletedConversation ?: return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            db.insertOrUpdateConversation(backup.first)
            for (m in backup.second) {
                db.insertMessage(m, backup.first.id)
            }
            val remaining = db.getConversations()
            val messages = db.getMessagesForConversation(backup.first.id)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    conversations = remaining,
                    currentConversationId = backup.first.id,
                    currentConversationTitle = backup.first.title,
                    messages = messages,
                    snackbarMessage = "Chat restored."
                )
                lastDeletedConversation = null
            }
        }
    }

    fun clearCurrentConversation() {
        val currentId = _uiState.value.currentConversationId
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            db.clearMessagesForConversation(currentId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    messages = emptyList(),
                    statusBanner = "Conversation cleared."
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearMessages()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    messages = emptyList(),
                    statusBanner = "Conversation history cleared."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        (activeEngine as? AutoCloseable)?.close()
        voiceManager.destroy()
    }
}
