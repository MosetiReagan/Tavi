package com.example.tavi.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM
}

data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val arguments: Map<String, Any?>
)

data class ToolResult(
    val toolCallId: String,
    val toolName: String,
    val resultJson: String,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val requiresUserConfirmation: Boolean = false,
    val confirmationPayload: ConfirmationRequest? = null
)

data class ConfirmationRequest(
    val id: String = UUID.randomUUID().toString(),
    val actionType: String,
    val title: String,
    val details: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val timestamp: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val isStreaming: Boolean = false
)

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String,
    val status: String, // "SUCCESS", "FAILED", "BLOCKED", "CONFIRMED"
    val summary: String,
    val verified: Boolean = true
)

data class ModelOption(
    val id: String,
    val name: String,
    val sizeDescription: String,
    val ramRequiredMb: Int,
    val isInstalled: Boolean,
    val isLoaded: Boolean,
    val localPath: String? = null,
    val isPrimary: Boolean = false,
    val description: String
)
