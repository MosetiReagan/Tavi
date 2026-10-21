package com.example.tavi.tools.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject

class AppListLaunchableTool : AssistantTool {
    override val name: String = "app_list_launchable"
    override val category: ToolCategory = ToolCategory.APPLICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Lists visible launchable applications installed on the device."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Optional search term to filter app names"}
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
        val query = arguments["query"]?.toString()?.trim()
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)
        val appsArray = JSONArray()

        for (resolveInfo in apps) {
            val label = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName

            if (query.isNullOrEmpty() || label.contains(query, ignoreCase = true) || packageName.contains(query, ignoreCase = true)) {
                appsArray.put(JSONObject().apply {
                    put("appName", label)
                    put("packageName", packageName)
                })
            }
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = JSONObject().apply {
                put("count", appsArray.length())
                put("apps", appsArray)
            }.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}

class AppLaunchTool : AssistantTool {
    override val name: String = "app_launch"
    override val category: ToolCategory = ToolCategory.APPLICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Launches an installed application by its name or package name."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "appName": {"type": "string", "description": "Name or package name of the app to launch"}
          },
          "required": ["appName"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val appName = arguments["appName"]?.toString()?.trim() ?: ""
        if (appName.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "App name is required."
            )
        }

        val pm = context.packageManager

        // 1. Check if directly matches a package name
        var launchIntent = pm.getLaunchIntentForPackage(appName)
        var matchedLabel = appName

        // 2. Otherwise search installed launcher apps by display label
        if (launchIntent == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val matched = apps.find { it.loadLabel(pm).toString().equals(appName, ignoreCase = true) }
                ?: apps.find { it.loadLabel(pm).toString().contains(appName, ignoreCase = true) }

            if (matched != null) {
                matchedLabel = matched.loadLabel(pm).toString()
                launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            }
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(launchIntent)
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply {
                        put("status", "APP_LAUNCHED")
                        put("appName", matchedLabel)
                        put("packageName", launchIntent.component?.packageName ?: "")
                    }.toString(),
                    isSuccess = true,
                    state = ToolExecutionState.SUCCESS
                )
            } catch (e: Exception) {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    state = ToolExecutionState.ERROR,
                    errorMessage = "Failed to launch $matchedLabel: ${e.message}"
                )
            }
        } else {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.NOT_FOUND,
                errorMessage = "Could not find an installed app matching '$appName'."
            )
        }
    }
}

class AppOpenSettingsTool : AssistantTool {
    override val name: String = "app_open_settings"
    override val category: ToolCategory = ToolCategory.APPLICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens the Android system App Info settings page for Tavi or a specified app."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "packageName": {"type": "string", "description": "Optional package name (defaults to Tavi)"}
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
        val pkg = arguments["packageName"]?.toString()?.trim()?.ifEmpty { context.packageName } ?: context.packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "SETTINGS_OPENED")
                    put("packageName", pkg)
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to open app settings: ${e.message}"
            )
        }
    }
}

class BrowserOpenUrlTool : AssistantTool {
    override val name: String = "browser_open_url"
    override val category: ToolCategory = ToolCategory.APPLICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens a web URL in the device's default web browser."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "url": {"type": "string", "description": "The web URL to open (e.g. https://...)"}
          },
          "required": ["url"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        var url = arguments["url"]?.toString()?.trim() ?: ""
        if (url.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "URL is required."
            )
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "URL_OPENED")
                    put("url", url)
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to open URL: ${e.message}"
            )
        }
    }
}

class ShareContentTool : AssistantTool {
    override val name: String = "share_content"
    override val category: ToolCategory = ToolCategory.APPLICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Shares text content using the Android system share sheet."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "text": {"type": "string", "description": "Content to share"},
            "title": {"type": "string", "description": "Optional title for share dialog"}
          },
          "required": ["text"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val text = arguments["text"]?.toString() ?: ""
        val title = arguments["title"]?.toString() ?: "Share with"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooserIntent = Intent.createChooser(sendIntent, title).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(chooserIntent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "SHARE_SHEET_OPENED")
                    put("length", text.length)
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to open share sheet: ${e.message}"
            )
        }
    }
}
