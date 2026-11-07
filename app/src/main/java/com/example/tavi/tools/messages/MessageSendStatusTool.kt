package com.example.tavi.tools.messages

import android.content.Context
import com.example.tavi.model.*
import com.example.tavi.tools.AssistantTool

class MessageSendStatusTool : AssistantTool {
    override val name = "messages_get_send_status"
    override val category = ToolCategory.TELEPHONY
    override val description = "Check Android SMS send and delivery callbacks for a previous dispatch ID."
    override val parametersSchema = """{"type":"object","properties":{"dispatchId":{"type":"string"}},"required":["dispatchId"]}"""
    override val requiredPermissions = emptyList<String>()
    override val requiresConfirmation = false
    override val isReadOnly = true
    override suspend fun execute(toolCallId: String, arguments: Map<String, Any?>, context: Context): ToolResult {
        val status = SmsDispatchTracker(context).status(arguments["dispatchId"].toString())
            ?: return ToolResult(toolCallId, name, "{}", false, errorMessage = "No SMS dispatch with that ID.")
        return ToolResult(toolCallId, name, status.toString())
    }
}
