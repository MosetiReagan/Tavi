package com.example.tavi.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM
}

enum class ToolExecutionState {
    SUCCESS,
    PERMISSION_REQUIRED,
    USER_ACTION_REQUIRED,
    UNSUPPORTED,
    NOT_FOUND,
    AMBIGUOUS,
    CANCELLED,
    ERROR
}

enum class ToolRiskLevel {
    LOW,
    MEDIUM,
    HIGH_CONSEQUENTIAL
}

enum class ToolCategory {
    CALENDAR,
    CONTACTS,
    NOTIFICATIONS,
    TELEPHONY,
    CLOCK,
    APPLICATIONS,
    FILES,
    DEVICE,
    LOCATION,
    MEDIA,
    NOTES,
    CONNECTIVITY,
    RESEARCH,
    USAGE,
    ACCESSIBILITY
}

enum class SpecialAccessType {
    NONE,
    NOTIFICATION_LISTENER,
    EXACT_ALARM,
    USAGE_STATS,
    ACCESSIBILITY_SERVICE,
    DEFAULT_ASSISTANT,
    DEFAULT_SMS_APP,
    DEFAULT_DIALER,
    OVERLAY_PERMISSION,
    ALL_FILES_ACCESS,
    BATTERY_OPTIMIZATION
}

enum class AccessStatus {
    GRANTED,
    NOT_GRANTED,
    ROLE_HELD,
    ROLE_AVAILABLE,
    ROLE_UNAVAILABLE,
    UNSUPPORTED
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
    val confirmationPayload: ConfirmationRequest? = null,
    val state: ToolExecutionState = if (isSuccess) ToolExecutionState.SUCCESS else ToolExecutionState.ERROR,
    val requiredPermission: String? = null,
    val settingsAction: String? = null,
    val ambiguousOptions: List<String> = emptyList()
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

data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String = "default",
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val isStreaming: Boolean = false,
    val thinkingContent: String? = null
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
