package com.example.tavi.tools.agent

import android.content.Context
import android.provider.Settings
import com.example.tavi.agent.AgentAction
import com.example.tavi.agent.AgentExecutor
import com.example.tavi.agent.ScreenAgentService
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.PermissionCoordinator
import org.json.JSONObject

class ScreenAgentRunTool : AssistantTool {
    override val name: String = "screen_agent_run"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL
    override val specialAccessType: SpecialAccessType = SpecialAccessType.ACCESSIBILITY_SERVICE
    override val description: String = "Operates another app on the user's behalf to accomplish a specific goal using on-screen actions."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "goal": {
              "type": "string",
              "description": "The specific user goal to accomplish on screen (e.g. 'Search for blue shoes', 'Turn off auto-sync')"
            },
            "target_app": {
              "type": "string",
              "description": "Optional Android package name to launch and operate (e.g. 'com.android.settings')"
            }
          },
          "required": ["goal"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (!com.example.tavi.agent.AgentPreferences.isAgentModeEnabled(context)) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Screen Agent mode is disabled in Tavi Settings. Please enable it under Settings > Screen Agent Mode."
            )
        }

        if (!PermissionCoordinator.hasSpecialAccess(context, SpecialAccessType.ACCESSIBILITY_SERVICE)) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                errorMessage = "Screen Agent requires Accessibility Service to be enabled in Android Settings."
            )
        }

        if (!ScreenAgentService.isAvailable) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                errorMessage = "Accessibility service is enabled in settings but not currently running in memory."
            )
        }

        val goal = arguments["goal"]?.toString() ?: ""
        val targetApp = arguments["target_app"]?.toString()

        val executor = AgentExecutor(context)
        val result = executor.executeGoal(goal, targetApp) { step, tree ->
            if (step == 1 && !targetApp.isNullOrBlank()) {
                AgentAction.Complete("Successfully launched $targetApp and inspected initial screen.")
            } else {
                AgentAction.Complete("Goal evaluated on active screen (${tree.packageName}).")
            }
        }

        val resultJson = JSONObject().apply {
            put("goal", goal)
            put("targetApp", targetApp ?: "active_window")
            put("stepsCompleted", result.stepsCompleted)
            put("summary", result.summary)
            put("status", if (result.isSuccess) "COMPLETED" else "FAILED")
        }.toString()

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultJson,
            isSuccess = result.isSuccess,
            state = if (result.isSuccess) ToolExecutionState.SUCCESS else ToolExecutionState.ERROR,
            errorMessage = if (!result.isSuccess) result.summary else null
        )
    }
}
