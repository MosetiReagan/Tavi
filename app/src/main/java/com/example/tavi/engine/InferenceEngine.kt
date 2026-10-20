package com.example.tavi.engine

import com.example.tavi.model.Message
import com.example.tavi.model.ToolCall

interface InferenceEngine {
    val name: String
    val isLocal: Boolean
    val isLoaded: Boolean

    suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<Message>,
        onToken: (String) -> Unit
    ): String

    suspend fun proposeToolCalls(
        userQuery: String,
        conversationHistory: List<Message>
    ): List<ToolCall>

    fun getEngineDiagnostics(): Map<String, Any>
}
