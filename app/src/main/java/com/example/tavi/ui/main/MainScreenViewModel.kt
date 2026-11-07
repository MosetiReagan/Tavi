package com.example.tavi.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tavi.context.ContextBudgetManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
    val isAwaitingFollowup: Boolean = false,
    val followupRemainingSeconds: Int = 0,
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
    val toolRegistry = ToolRegistry.getInstance(application)
    val modelManager = ModelManager.getInstance(application)
    val voiceManager = VoiceManager(application)
    val workflowPlanner = WorkflowPlanner(application, toolRegistry)
    val workflowEngine = WorkflowEngine(application, toolRegistry, db)

    private var taskJob: Job? = null
    private var preloadJob: Job? = null

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

    private fun speakReply(text: String) {
        if (voiceManager.autoReadReplies.value ||
            (voiceManager.continuousVoice.value && voiceManager.isVoiceSessionActive)) voiceManager.speak(text)
    }

    init {
        toolRegistry.screenAgentEngineProvider = { resolveActiveEngine() }
        checkModelInstallation()
        loadPersistedMessages()
        observeNetworkAndModel()
        observeVoiceState()
        observeDownloadProgress()
        preloadModel()
    }

    fun checkModelInstallation() {
        val info = modelManager.getInstalledModelInfo()
        _uiState.value = _uiState.value.copy(
            isModelInstalled = info.isInstalled,
            activeModelName = if (info.isInstalled) "Model installed • ready to load" else "Basic commands • model optional"
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
        preloadJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
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
                        activeModelName = if (engine.isGenuineInferenceActive) engine.name else "Basic commands • AI unavailable",
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

    private fun restoreWorkflow(conversationId: String): WorkflowPlan? {
        val plan = db.getActiveWorkflow(conversationId) ?: return null
        if (plan.state !in setOf(WorkflowState.EXECUTING, WorkflowState.VERIFYING, WorkflowState.PLANNING)) return plan
        val interrupted = plan.copy(state = WorkflowState.FAILED,
            error = "Task was interrupted. Inspect any dispatched action before starting a new task.")
        db.saveWorkflow(interrupted)
        return interrupted
    }

    @Synchronized
    private fun resolveActiveEngine(): InferenceEngine {
        val targetFile = modelManager.modelFile
        val path = if (targetFile.exists() && targetFile.length() > 0) targetFile.absolutePath else null
        val runtime = modelManager.activeRuntime.value
        val currentEngine = activeEngine
        if (currentEngine is LiteRtLlmEngine && currentEngine.modelPath == path && currentEngine.requestedRuntime == runtime) {
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
                val restoredWorkflow = restoreWorkflow(activeConv.id)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        conversations = convs.ifEmpty { listOf(activeConv) },
                        currentConversationId = activeConv.id,
                        currentConversationTitle = activeConv.title,
                        messages = saved,
                        activeWorkflow = restoredWorkflow
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
                if (currentEngine is LiteRtLlmEngine && currentEngine.modelPath == path && currentEngine.requestedRuntime == runtime) {
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
                if (currentEngine is LiteRtLlmEngine && currentEngine.requestedRuntime != runtime) {
                    taskJob?.cancelAndJoin()
                    preloadJob?.cancelAndJoin()
                    currentEngine.switchRuntime(runtime)
                    val modelOption = modelManager.getAvailableModels().find { it.id == modelManager.activeModelId.value }
                    val baseName = modelOption?.name ?: "Gemma 3 1B"
                    _uiState.value = _uiState.value.copy(activeModelName = "$baseName (${runtime.displayName})")
                }
            }
        }
        viewModelScope.launch {
            modelManager.memoryPressure.drop(1).collect {
                cancelTask()
                taskJob?.join()
                preloadJob?.cancelAndJoin()
                withContext(Dispatchers.IO) { (activeEngine as? LiteRtLlmEngine)?.unloadModel() }
                _uiState.value = _uiState.value.copy(activeModelName = "Basic commands • model released",
                    statusBanner = "Android reported low memory. Model memory was released; basic commands remain available.")
            }
        }
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceManager.sessionMode.collect { mode ->
                _uiState.value = _uiState.value.copy(
                    isListening = (mode == com.example.tavi.voice.VoiceSessionMode.LISTENING),
                    isSpeaking = (mode == com.example.tavi.voice.VoiceSessionMode.SPEAKING),
                    isAwaitingFollowup = (mode == com.example.tavi.voice.VoiceSessionMode.AWAITING_FOLLOWUP)
                )
            }
        }
        viewModelScope.launch {
            voiceManager.followupRemainingSeconds.collect { sec ->
                _uiState.value = _uiState.value.copy(followupRemainingSeconds = sec)
            }
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isStreaming) return

        val currentConvId = _uiState.value.currentConversationId
        val waiting = _uiState.value.activeWorkflow
        if (waiting?.state == WorkflowState.AWAITING_CONFIRMATION) {
            handleSpokenInput(trimmed)
            return
        }

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

            taskJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
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

        taskJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                db.insertMessage(userMessage, currentConvId)

                // 1.5. Explicit Personal Memory Management (Instant deterministic handling)
                val memCommand = PersistentMemoryManager.parseMemoryCommand(trimmed, currentConvId, userMessage.id)
                if (memCommand != null) {
                    handleMemoryCommand(memCommand, userMessage, updatedMessages)
                    return@launch
                }

                // 2. Multi-Step Workflow Planning (Deterministic fast-path or Gemma 3 1B)
                val plannedWorkflow = (workflowPlanner.planWorkflow(trimmed) ?: run {
                    if (!workflowPlanner.shouldAttemptModelPlanning(trimmed)) {
                        null
                    } else {
                        val engine = resolveActiveEngine()
                        if (engine is LiteRtLlmEngine) {
                            engine.planWorkflowWithGemma(trimmed, workflowPlanner)
                        } else null
                    }
                })?.copy(conversationId = currentConvId)

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = Message(
                    conversationId = currentConvId,
                    role = MessageRole.ASSISTANT,
                    content = "⚠️ An error occurred while generating a response: ${e.message ?: "Unknown error"}"
                )
                db.insertMessage(errorMsg, currentConvId)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages + errorMsg,
                        isStreaming = false
                    )
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (_uiState.value.currentConversationId == currentConvId)
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                }
            }
        }
    }

    private suspend fun handleWorkflowCompletion(workflow: WorkflowPlan, currentMessages: List<Message>) {
        if (workflow.state == WorkflowState.AWAITING_CONFIRMATION || workflow.state == WorkflowState.AWAITING_USER_INPUT) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isStreaming = false)
                val step = workflow.steps.find { it.id == workflow.pendingConfirmationStepId }
                speakReply(workflow.missingInputPrompt ?: "${step?.title}. ${step?.arguments?.entries?.joinToString { "${it.key}: ${it.value}" }}. Say yes to proceed, or no to cancel.")
            }
            return
        }

        val outcomes = workflow.steps.filter { it.result != null }
        val dataSummary = ToolResultSynthesizer.synthesize(
            outcomes.map { ToolCall(it.idempotencyKey, it.toolName, it.arguments) }, outcomes.mapNotNull { it.result })
        val summary = dataSummary.takeIf { it.isNotBlank() } ?: workflow.executionSummary?.humanReadableText
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
            conversationId = workflow.conversationId,
            role = MessageRole.ASSISTANT,
            content = formattedContent.trim(),
            toolResults = workflow.steps.mapNotNull { it.result }
        )
        db.insertMessage(assistantMsg, workflow.conversationId)

        if (workflow.state == WorkflowState.COMPLETED) {
            try {
                PersistentMemoryManager.recordEpisodicMemory(
                    context = getApplication(),
                    title = workflow.title,
                    summary = summary,
                    decisionRationale = "Completed workflow with ${workflow.steps.size} steps",
                    outcome = "SUCCESS",
                    taskStatus = "COMPLETED",
                    sourceConversationId = workflow.conversationId,
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
            speakReply(summary)
        }
    }

    fun confirmWorkflowStep(stepId: String, confirmed: Boolean) {
        if (_uiState.value.isStreaming) return
        val workflow = _uiState.value.activeWorkflow ?: return
        if (workflow.conversationId != _uiState.value.currentConversationId) return
        _uiState.value = _uiState.value.copy(isStreaming = true)
        taskJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val updated = workflowEngine.confirmStep(workflow, stepId, confirmed) { plan ->
                    withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(activeWorkflow = plan) }
                }
                handleWorkflowCompletion(updated, _uiState.value.messages)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(errorMessage = e.message) }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { _uiState.value = _uiState.value.copy(isStreaming = false) }
            }
        }
    }

    fun cancelWorkflow() = cancelTask()

    fun cancelTask() {
        com.example.tavi.agent.AgentStopOverlay.hide()
        val running = taskJob
        running?.cancel()
        voiceManager.dismissFollowup()
        voiceManager.stopSpeaking()
        val workflow = _uiState.value.activeWorkflow
        viewModelScope.launch {
            running?.join()
            if (workflow != null && workflow.state !in setOf(WorkflowState.COMPLETED, WorkflowState.CANCELLED)) {
                val latest = withContext(Dispatchers.IO) { db.getWorkflow(workflow.id) } ?: workflow
                val cancelled = workflowEngine.cancelWorkflow(latest)
                _uiState.value = _uiState.value.copy(activeWorkflow = cancelled)
            }
            _uiState.value = _uiState.value.copy(isStreaming = false, pendingConfirmation = null,
                messages = _uiState.value.messages.filterNot { it.isStreaming }, statusBanner = "Task stopped. Inspect any action already dispatched.")
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
                    provenance = MemoryProvenance.USER_EXPLICIT,
                    sourceConversationId = userMessage.conversationId,
                    sourceMessageId = userMessage.id
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
                    speakReply("Clear all saved memories? Say yes to confirm, or no to cancel.")
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
            speakReply(replyText)
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

            val prunedNotice = if (ContextBudgetManager.lastPrunedMessageCount > 0) {
                "Context limit reached: ${ContextBudgetManager.lastPrunedMessageCount} earlier messages were pruned to fit memory."
            } else null

            withContext(Dispatchers.Main) {
                val currentMsgs = _uiState.value.messages.toMutableList()
                if (currentMsgs.isNotEmpty() && currentMsgs.last().isStreaming) {
                    currentMsgs[currentMsgs.lastIndex] = finalizedMessage
                } else {
                    currentMsgs.add(finalizedMessage)
                }
                _uiState.value = _uiState.value.copy(
                    messages = currentMsgs,
                    isStreaming = false,
                    statusBanner = prunedNotice ?: _uiState.value.statusBanner
                )
                if (finalReply.isNotBlank()) speakReply(finalReply)
            }
        }
    }

    private suspend fun executeToolsAndSynthesize(
        tools: List<ToolCall>,
        currentHistory: List<Message>
    ) {
        val conversationId = currentHistory.lastOrNull()?.conversationId ?: _uiState.value.currentConversationId
        val plan = WorkflowPlan(
            title = if (tools.size == 1) toolRegistry.getTool(tools.first().toolName)?.description?.substringBefore('.') ?: "Assistant action" else "Assistant task",
            userQuery = currentHistory.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty(),
            conversationId = conversationId,
            steps = tools.mapIndexed { index, call ->
                val tool = toolRegistry.getTool(call.toolName)
                com.example.tavi.workflow.WorkflowStep(id = call.id, idempotencyKey = call.id,
                    stepIndex = index, title = tool?.name?.replace('_', ' ') ?: call.toolName,
                    toolName = call.toolName, arguments = call.arguments,
                    isReadOnly = tool?.isReadOnly == true, riskLevel = tool?.riskLevel ?: com.example.tavi.model.ToolRiskLevel.LOW)
            })
        val updated = workflowEngine.executePlan(plan) { update ->
            withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(activeWorkflow = update) }
        }
        handleWorkflowCompletion(updated, currentHistory)
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
                val toolCall = ToolCall(id = req.id, toolName = req.toolName, arguments = req.arguments)
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
                    speakReply(finalSummary)
                }
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Error executing confirmed action", e)
            }
        }
    }

    fun handleSpokenInput(spokenText: String, onFinalText: ((String) -> Unit)? = null) {
        val workflow = _uiState.value.activeWorkflow
        val pending = _uiState.value.pendingConfirmation
        if (workflow?.state == WorkflowState.AWAITING_CONFIRMATION) {
            when (com.example.tavi.voice.VoiceConfirmationParser.parse(spokenText)) {
                com.example.tavi.voice.VoiceConfirmationDecision.AFFIRMATIVE -> workflow.pendingConfirmationStepId?.let { confirmWorkflowStep(it, true) }
                com.example.tavi.voice.VoiceConfirmationDecision.NEGATIVE -> workflow.pendingConfirmationStepId?.let { confirmWorkflowStep(it, false) }
                else -> voiceManager.speak("Please say yes to proceed, or no to cancel.")
            }
        } else if (pending != null) {
            when (com.example.tavi.voice.VoiceConfirmationParser.parse(spokenText)) {
                com.example.tavi.voice.VoiceConfirmationDecision.AFFIRMATIVE -> {
                    confirmPendingAction(true)
                }
                com.example.tavi.voice.VoiceConfirmationDecision.NEGATIVE -> {
                    confirmPendingAction(false)
                    voiceManager.speak("Action cancelled.")
                }
                com.example.tavi.voice.VoiceConfirmationDecision.AMBIGUOUS -> {
                    voiceManager.speak("Please say yes to proceed, or no to cancel.")
                }
            }
        } else {
            if (onFinalText != null) {
                onFinalText(spokenText)
            } else {
                sendMessage(spokenText)
            }
        }
    }

    fun dismissFollowup() {
        voiceManager.dismissFollowup()
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
                if (voiceManager.continuousVoice.value) {
                    onFinalText("")
                    handleSpokenInput(spokenText)
                } else handleSpokenInput(spokenText, onFinalText)
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
                    handleSpokenInput(spokenText)
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
        voiceManager.dismissFollowup()
        voiceManager.stopSpeaking()
        val running = taskJob
        running?.cancel()
        _uiState.value = _uiState.value.copy(isStreaming = true, pendingConfirmation = null)
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            running?.join()
            db.insertOrUpdateConversation(newSession)
            val allConvs = db.getConversations()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    pendingConfirmation = null,
                    activeWorkflow = null,
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
        voiceManager.dismissFollowup()
        voiceManager.stopSpeaking()
        val running = taskJob
        running?.cancel()
        _uiState.value = _uiState.value.copy(isStreaming = true, pendingConfirmation = null)
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            running?.join()
            val convs = db.getConversations()
            val target = convs.find { it.id == convId } ?: return@launch
            val messages = db.getMessagesForConversation(convId)
            val restoredWorkflow = restoreWorkflow(convId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    pendingConfirmation = null,
                    activeWorkflow = restoredWorkflow,
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
        voiceManager.dismissFollowup()
        voiceManager.stopSpeaking()
        val running = taskJob
        running?.cancel()
        _uiState.value = _uiState.value.copy(isStreaming = true, pendingConfirmation = null)
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            running?.join()
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
                    isStreaming = false,
                    pendingConfirmation = null,
                    activeWorkflow = null,
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
        voiceManager.dismissFollowup()
        voiceManager.stopSpeaking()
        val running = taskJob
        running?.cancel()
        _uiState.value = _uiState.value.copy(isStreaming = true, pendingConfirmation = null)
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            running?.join()
            db.insertOrUpdateConversation(backup.first)
            for (m in backup.second) {
                db.insertMessage(m, backup.first.id)
            }
            val remaining = db.getConversations()
            val messages = db.getMessagesForConversation(backup.first.id)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    pendingConfirmation = null,
                    activeWorkflow = null,
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
        voiceManager.dismissFollowup()
        voiceManager.stopSpeaking()
        val running = taskJob
        running?.cancel()
        _uiState.value = _uiState.value.copy(isStreaming = true, pendingConfirmation = null)
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            running?.join()
            db.clearMessagesForConversation(currentId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false, activeWorkflow = null, pendingConfirmation = null,
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
