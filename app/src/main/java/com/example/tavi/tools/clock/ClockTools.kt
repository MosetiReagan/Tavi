package com.example.tavi.tools.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AlarmCreateTool : AssistantTool {
    override val name: String = "alarm_create"
    override val category: ToolCategory = ToolCategory.CLOCK
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val specialAccessType: SpecialAccessType = SpecialAccessType.EXACT_ALARM
    override val description: String = "Sets an alarm in the Android system Clock app."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "hour": {"type": "integer", "description": "Hour of day (0-23)"},
            "minutes": {"type": "integer", "description": "Minute of hour (0-59)"},
            "title": {"type": "string", "description": "Optional label for the alarm"},
            "skipUi": {"type": "boolean", "description": "True to set alarm directly without opening the clock UI (default false)"}
          },
          "required": ["hour", "minutes"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf("com.android.alarm.permission.SET_ALARM")
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val hour = arguments["hour"]?.toString()?.toIntOrNull()
        val minutes = arguments["minutes"]?.toString()?.toIntOrNull() ?: 0
        val title = arguments["title"]?.toString()?.trim() ?: "Tavi Alarm"
        val skipUi = arguments["skipUi"]?.toString()?.toBooleanStrictOrNull() ?: true

        if (hour == null || hour !in 0..23 || minutes !in 0..59) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Valid 'hour' (0-23) and 'minutes' (0-59) are required."
            )
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minutes)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("verified", skipUi)
                    put("time", timeString)
                    put("hour", hour)
                    put("minutes", minutes)
                    put("title", title)
                    put("skipUi", skipUi)
                    put("status", if (skipUi) "ALARM_REQUEST_SUBMITTED" else "CLOCK_APP_OPENED")
                    put("message", if (skipUi) "Alarm scheduled for $timeString via system Clock." else "Opened system Clock. Tap Save to set the alarm.")
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
                errorMessage = "Failed to launch Clock alarm intent: ${e.message}"
            )
        }
    }
}

class AlarmShowTool : AssistantTool {
    override val name: String = "alarm_show"
    override val category: ToolCategory = ToolCategory.CLOCK
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens the device Clock app to display all configured alarms."
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
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply { put("status", "CLOCK_ALARMS_DISPLAYED") }.toString(),
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
                errorMessage = "Failed to open Clock alarms: ${e.message}"
            )
        }
    }
}

class TimerSetTool : AssistantTool {
    override val name: String = "timer_set"
    override val category: ToolCategory = ToolCategory.CLOCK
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val description: String = "Starts a countdown timer in the device Clock app."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "seconds": {"type": "integer", "description": "Duration of timer in seconds"},
            "title": {"type": "string", "description": "Optional label for timer"},
            "skipUi": {"type": "boolean", "description": "True to start timer without opening the clock UI"}
          },
          "required": ["seconds"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf("com.android.alarm.permission.SET_ALARM")
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val seconds = arguments["seconds"]?.toString()?.toIntOrNull()
        val title = arguments["title"]?.toString()?.trim() ?: "Tavi Timer"
        val skipUi = arguments["skipUi"]?.toString()?.toBooleanStrictOrNull() ?: false

        if (seconds == null || seconds <= 0) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Timer duration must be positive seconds."
            )
        }

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("verified", true)
                    put("seconds", seconds)
                    put("title", title)
                    put("status", "TIMER_SET")
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
                errorMessage = "Failed to launch Clock timer intent: ${e.message}"
            )
        }
    }
}

class ClockQueryTimeTool : AssistantTool {
    override val name: String = "clock_query_time"
    override val category: ToolCategory = ToolCategory.CLOCK
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Queries the current device time, timezone, UTC offset, and locale."
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
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val loc = Locale.getDefault()
        val sdfTime = SimpleDateFormat("h:mm:ss a", loc).apply { timeZone = tz }
        val sdfDate = SimpleDateFormat("EEEE, MMMM d, yyyy", loc).apply { timeZone = tz }

        val obj = JSONObject().apply {
            put("currentTime", sdfTime.format(Date(now)))
            put("currentDate", sdfDate.format(Date(now)))
            put("timezoneId", tz.id)
            put("timezoneDisplayName", tz.getDisplayName(tz.inDaylightTime(Date(now)), TimeZone.LONG, loc))
            put("utcOffsetHours", tz.getOffset(now) / (1000.0 * 3600.0))
            put("locale", loc.toLanguageTag())
            put("epochMillis", now)
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = obj.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}
