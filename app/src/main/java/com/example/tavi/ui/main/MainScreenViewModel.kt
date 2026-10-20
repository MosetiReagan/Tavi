package com.example.tavi.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tavi.data.TaviDatabase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val pendingConfirmation: ConfirmationRequest? = null,
    val isOffline: Boolean = false,
    val activeModelName: String = "Gemma 3 1B",
    val statusBanner: String? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TaviDatabase.getInstance(application)
    val toolRegistry = ToolRegistry(application)
    val modelManager = ModelManager(application)
    val voiceManager = VoiceManager(application)

    private var activeEngine: InferenceEngine = LiteRtLlmEngine(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadPersistedMessages()
        observeNetworkAndModel()
        observeVoiceState()
    }

    private fun loadPersistedMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = db.getAllMessages()
            if (saved.isEmpty()) {
                val welcome = Message(
                    role = MessageRole.ASSISTANT,
                    content = "Hello! I am Tavi, your private personal AI assistant. I run locally on your phone so your data and schedule stay secure and offline. How can I help you today?"
                )
                db.insertMessage(welcome)
                _uiState.value = _uiState.value.copy(messages = listOf(welcome))
            } else {
                _uiState.value = _uiState.value.copy(messages = saved)
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
                activeEngine = LiteRtLlmEngine(getApplication(), modelOption?.localPath)
                _uiState.value = _uiState.value.copy(activeModelName = displayName)
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
        if (trimmed.isEmpty()) return

        val userMessage = Message(
            role = MessageRole.USER,
            content = trimmed
        )

        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            isStreaming = true,
            statusBanner = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            db.insertMessage(userMessage)
            processAssistantTurn(userMessage, updatedMessages)
        }
    }

    private suspend fun processAssistantTurn(userMessage: Message, currentHistory: List<Message>) {
        // 1. Propose tool calls
        val proposedTools = activeEngine.proposeToolCalls(userMessage.content, currentHistory)

        if (proposedTools.isNotEmpty()) {
            executeToolsAndSynthesize(proposedTools, currentHistory)
        } else {
            // Conversational direct reply
            val streamingMessage = Message(
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
            val finalReply = activeEngine.generateResponse(
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
            db.insertMessage(finalizedMessage)

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

        // Synthesize tool output to conversational explanation
        val summary = synthesizeToolResults(tools, executedResults)
        val assistantMessage = Message(
            role = MessageRole.ASSISTANT,
            content = summary,
            toolCalls = tools,
            toolResults = executedResults,
            isStreaming = false
        )
        db.insertMessage(assistantMessage)

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

        viewModelScope.launch(Dispatchers.IO) {
            if (!confirmed) {
                val cancelMsg = Message(
                    role = MessageRole.ASSISTANT,
                    content = "Action '${req.title}' was cancelled by your request."
                )
                db.insertMessage(cancelMsg)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + cancelMsg)
                }
                return@launch
            }

            // User confirmed: execute tool with userConfirmed = true
            val toolCall = ToolCall(toolName = req.toolName, arguments = req.arguments)
            val result = toolRegistry.executeToolCall(toolCall, userConfirmed = true)

            val summary = if (result.isSuccess) {
                "Action confirmed and completed successfully: ${req.title}."
            } else {
                "Action failed: ${result.errorMessage}"
            }

            val resultMsg = Message(
                role = MessageRole.ASSISTANT,
                content = summary,
                toolCalls = listOf(toolCall),
                toolResults = listOf(result)
            )
            db.insertMessage(resultMsg)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + resultMsg)
            }
        }
    }

    private fun synthesizeToolResults(tools: List<ToolCall>, results: List<ToolResult>): String {
        val sb = StringBuilder()
        for (i in tools.indices) {
            val t = tools[i]
            val r = results.getOrNull(i)
            if (r == null) continue

            if (!r.isSuccess) {
                sb.append("⚠️ ${r.errorMessage ?: "Failed to execute ${t.toolName}."}\n")
                continue
            }

            when (t.toolName) {
                "calendar_list_events" -> {
                    sb.append("Here are your calendar events:\n")
                    sb.append(formatCalendarResult(r.resultJson))
                }
                "calendar_create_event" -> {
                    sb.append("✅ Event scheduled and verified in your calendar: \"${t.arguments["title"]}\" at ${t.arguments["startTime"]}.")
                }
                "calendar_delete_event" -> {
                    sb.append("✅ Calendar event deleted and verified.")
                }
                "calculator" -> {
                    sb.append("Result: ${t.arguments["expression"]} = ${r.resultJson}")
                }
                "date_calculation" -> {
                    sb.append("Date calculation result: ${r.resultJson}")
                }
                "reminder_create" -> {
                    sb.append("⏰ Reminder scheduled: \"${t.arguments["title"]}\" for ${t.arguments["time"]}.")
                }
                "note_create" -> {
                    sb.append("📝 Note saved: \"${t.arguments["title"]}\".")
                }
                "memory_save" -> {
                    sb.append("💾 Saved to your personal memory: ${t.arguments["key"]} = \"${t.arguments["value"]}\".")
                }
                "memory_retrieve" -> {
                    sb.append("Memory lookup: ${r.resultJson}")
                }
                "messages_draft" -> {
                    sb.append("✉️ Draft prepared for ${t.arguments["recipient"]}:\n\"${t.arguments["body"]}\"")
                }
                "messages_send_sms" -> {
                    sb.append("✅ Text message sent to ${t.arguments["recipient"]}.")
                }
                "messages_search" -> {
                    sb.append("Found messages:\n${r.resultJson}")
                }
                "notifications_read_summary" -> {
                    sb.append("Notification summary:\n${r.resultJson}")
                }
                "browser_research_topic" -> {
                    sb.append("🌐 Research findings for \"${t.arguments["query"]}\":\n${r.resultJson}")
                }
                else -> {
                    sb.append("Executed ${t.toolName}: ${r.resultJson}")
                }
            }
        }
        return sb.toString().trim()
    }

    private fun formatCalendarResult(jsonStr: String): String {
        return try {
            val json = org.json.JSONObject(jsonStr)
            val events = json.optJSONArray("events")
            if (events == null || events.length() == 0) {
                return "No upcoming events found in this time window."
            }
            val sb = StringBuilder()
            for (i in 0 until events.length()) {
                val e = events.getJSONObject(i)
                sb.append("• ${e.optString("title")} (${e.optString("startTime")})")
                val loc = e.optString("location")
                if (loc.isNotEmpty()) sb.append(" at $loc")
                sb.append("\n")
            }
            sb.toString().trim()
        } catch (_: Exception) {
            jsonStr
        }
    }

    fun toggleVoiceInput() {
        if (_uiState.value.isListening) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening(
                onResult = { spokenText ->
                    sendMessage(spokenText)
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(statusBanner = "Voice error: $error")
                }
            )
        }
    }

    fun speakLastMessage() {
        val lastMsg = _uiState.value.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (lastMsg != null) {
            voiceManager.speak(lastMsg.content)
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
        voiceManager.destroy()
    }
}
