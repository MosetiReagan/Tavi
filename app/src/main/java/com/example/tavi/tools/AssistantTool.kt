package com.example.tavi.tools

import android.content.Context
import com.example.tavi.model.ToolResult

interface AssistantTool {
    val name: String
    val description: String
    val parametersSchema: String
    val requiredPermissions: List<String>
    val requiresConfirmation: Boolean

    suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult
}
