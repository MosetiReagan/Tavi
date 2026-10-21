package com.example.tavi.tools.accessibility

import android.content.Context
import android.provider.Settings
import com.example.tavi.accessibility.TaviAccessibilityService
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.PermissionCoordinator
import org.json.JSONArray
import org.json.JSONObject

class AccessibilityReadScreenTool : AssistantTool {
    override val name: String = "accessibility_read_screen"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val specialAccessType: SpecialAccessType = SpecialAccessType.ACCESSIBILITY_SERVICE
    override val description: String = "Inspects the visible text and UI elements currently displayed on the screen with strict password and privacy redactions."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "maxElements": {"type": "integer", "description": "Maximum number of visible UI elements to return (default 25, max 100)"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (!PermissionCoordinator.hasSpecialAccess(context, SpecialAccessType.ACCESSIBILITY_SERVICE)) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                errorMessage = "Accessibility Service is not enabled. Please enable 'Tavi Accessibility Assistant' in Android Accessibility Settings."
            )
        }

        val service = TaviAccessibilityService.instance
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                errorMessage = "Accessibility service is enabled in settings but not currently running in memory."
            )

        val maxElements = (arguments["maxElements"]?.toString()?.toIntOrNull() ?: 25).coerceIn(1, 100)
        val inspection = service.inspectCurrentScreen()

        val elementsJson = JSONArray()
        val pkg = inspection.packageName
        for (el in inspection.elements.take(maxElements)) {
            val item = JSONObject().apply {
                if (el.text.isNotBlank()) {
                    put("text", SecurityGuard.sanitizeExternalContent(el.text, "Screen: $pkg"))
                }
                if (el.contentDescription.isNotBlank()) {
                    put("contentDescription", SecurityGuard.sanitizeExternalContent(el.contentDescription, "Screen: $pkg"))
                }
                if (el.viewId.isNotBlank()) put("id", el.viewId)
                put("isClickable", el.isClickable)
            }
            elementsJson.put(item)
        }

        val sanitizedSummary = SecurityGuard.sanitizeExternalContent(inspection.screenSummary, "Screen: $pkg")

        val resultJson = JSONObject().apply {
            put("foregroundPackage", pkg)
            put("elementCount", inspection.elementCount)
            put("summary", sanitizedSummary)
            put("elements", elementsJson)
        }.toString()

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultJson,
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}

class AccessibilityPerformActionTool : AssistantTool {
    override val name: String = "accessibility_perform_action"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val specialAccessType: SpecialAccessType = SpecialAccessType.ACCESSIBILITY_SERVICE
    override val description: String = "Performs global system UI navigation actions like pressing back, going home, showing recents, opening notifications, or quick settings."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "action": {
              "type": "string",
              "enum": ["back", "home", "recents", "notifications", "quick_settings", "lock_screen", "take_screenshot"],
              "description": "The global UI action to perform"
            }
          },
          "required": ["action"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (!PermissionCoordinator.hasSpecialAccess(context, SpecialAccessType.ACCESSIBILITY_SERVICE)) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                errorMessage = "Accessibility Service is not enabled. Please enable 'Tavi Accessibility Assistant' in Android Accessibility Settings."
            )
        }

        val service = TaviAccessibilityService.instance
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                errorMessage = "Accessibility service is enabled in settings but not currently running in memory."
            )

        val action = arguments["action"]?.toString()?.lowercase()?.trim() ?: "back"
        val success = service.performAction(action)

        return if (success) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("action", action)
                    put("status", "SUCCESS")
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } else {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to perform accessibility action: $action (action may not be supported on this Android API version)."
            )
        }
    }
}
