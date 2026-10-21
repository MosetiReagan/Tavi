package com.example.tavi.tools.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONObject
import java.util.Locale

class DeviceGetStatusTool : AssistantTool {
    override val name: String = "device_get_status"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Reads battery level, charging state, available storage, and system platform status."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {}
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        return try {
            // 1. Battery status
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, batteryFilter)
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // 2. Storage status
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val bytesTotal = stat.blockCountLong * stat.blockSizeLong
            val freeGb = bytesAvailable / (1024.0 * 1024.0 * 1024.0)
            val totalGb = bytesTotal / (1024.0 * 1024.0 * 1024.0)

            val obj = JSONObject().apply {
                put("batteryPercent", batteryPct)
                put("isCharging", isCharging)
                put("freeStorageGb", String.format(Locale.US, "%.1f", freeGb))
                put("totalStorageGb", String.format(Locale.US, "%.1f", totalGb))
                put("androidVersion", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            }

            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = obj.toString(),
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
                errorMessage = "Failed to query device state: ${e.message}"
            )
        }
    }
}

class SettingsOpenTool : AssistantTool {
    override val name: String = "settings_open"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens specific Android system Settings panels (wifi, bluetooth, display, sound, notifications, battery, apps, date, storage)."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "panel": {
              "type": "string",
              "enum": ["wifi", "bluetooth", "display", "sound", "notifications", "battery", "apps", "date", "storage", "general"],
              "description": "The specific settings panel to open"
            }
          },
          "required": ["panel"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val panel = arguments["panel"]?.toString()?.trim()?.lowercase(Locale.ROOT) ?: "general"

        val action = when (panel) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                } else {
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                }
            }
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (panel == "notifications" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }

        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "SETTINGS_PANEL_OPENED")
                    put("panel", panel)
                    put("action", action)
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
                errorMessage = "Failed to open settings panel '$panel': ${e.message}"
            )
        }
    }
}
