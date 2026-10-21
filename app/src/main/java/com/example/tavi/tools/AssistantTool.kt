package com.example.tavi.tools

import android.content.Context
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel

interface AssistantTool {
    val name: String
    val description: String
    val parametersSchema: String
    val requiredPermissions: List<String>
    val requiresConfirmation: Boolean
    val category: ToolCategory get() = ToolCategory.NOTES
    val riskLevel: ToolRiskLevel get() = if (requiresConfirmation) ToolRiskLevel.HIGH_CONSEQUENTIAL else ToolRiskLevel.LOW
    val specialAccessType: SpecialAccessType get() = SpecialAccessType.NONE
    val minApiLevel: Int get() = 24

    suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult
}
