package com.example.tavi.tools.everyday

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.tavi.MainActivity
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.calendar.CalendarNaturalDateResolver
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

class CalculatorTool : AssistantTool {
    override val name: String = "calculator"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Evaluates deterministic mathematical expressions (e.g., '145 * 12', 'sqrt(144) + 2^4'). Safe and local."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "expression": {"type": "string", "description": "Mathematical expression to compute"}
          },
          "required": ["expression"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val expr = arguments["expression"]?.toString()?.trim()
        if (expr.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Expression cannot be empty."
            )
        }

        return try {
            val result = evaluate(expr)
            val resultObj = JSONObject().apply {
                put("expression", expr)
                put("result", result)
                put("verified", true)
            }
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
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
                errorMessage = "Calculation error: ${e.message}"
            )
        }
    }

    private fun evaluate(str: String): Double {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            var x = 0.0
            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var v: Double
                val startPos = pos
                if (eat('('.code)) {
                    v = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    v = str.substring(startPos, pos).toDouble()
                } else if (ch in 'a'.code..'z'.code) {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val func = str.substring(startPos, pos)
                    if (eat('('.code)) {
                        val arg = parseExpression()
                        eat(')'.code)
                        v = when (func) {
                            "sqrt" -> sqrt(arg)
                            else -> throw RuntimeException("Unknown function: $func")
                        }
                    } else {
                        throw RuntimeException("Expected '(' after function $func")
                    }
                } else {
                    throw RuntimeException("Unexpected character: " + ch.toChar())
                }

                if (eat('^'.code)) v = v.pow(parseFactor())
                return v
            }

            fun parseTerm(): Double {
                var v = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> v *= parseFactor()
                        eat('/'.code) -> {
                            val divisor = parseFactor()
                            if (divisor == 0.0) throw ArithmeticException("Division by zero")
                            v /= divisor
                        }
                        eat('%'.code) -> v %= parseFactor()
                        else -> return v
                    }
                }
            }

            x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        nextChar()
        val res = parseExpression()
        if (pos < str.length) throw RuntimeException("Unexpected character at end: " + ch.toChar())
        return res
    }
}

class DateCalculationTool : AssistantTool {
    override val name: String = "date_calculation"
    override val category: ToolCategory = ToolCategory.CLOCK
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Performs deterministic date math (e.g., days until a future date, adding days/weeks, timezone offset)."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "operation": {"type": "string", "enum": ["days_between", "add_duration", "current_datetime", "day_of_week"]},
            "targetDate": {"type": "string", "description": "Date to inspect (natural or ISO)"},
            "fromDate": {"type": "string", "description": "Optional start date for days_between (defaults to now)"},
            "duration": {"type": "string", "description": "Duration to add (e.g., '14 days', '3 weeks', '2 months')"}
          },
          "required": ["operation"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val op = arguments["operation"]?.toString()?.trim() ?: "current_datetime"
        val targetRaw = arguments["targetDate"]?.toString()?.trim()
        val fromRaw = arguments["fromDate"]?.toString()?.trim()
        val duration = arguments["duration"]?.toString()?.trim()

        val now = System.currentTimeMillis()

        return try {
            val resultObj = JSONObject().apply {
                put("operation", op)
                put("timezone", TimeZone.getDefault().id)
                when (op) {
                    "current_datetime" -> {
                        put("formatted", CalendarNaturalDateResolver.formatDateTime(now))
                        put("epochMillis", now)
                    }
                    "days_between" -> {
                        val toMillis = if (!targetRaw.isNullOrEmpty()) {
                            CalendarNaturalDateResolver.resolveToEpochMillis(targetRaw) ?: now
                        } else now
                        val fromMillis = if (!fromRaw.isNullOrEmpty()) {
                            CalendarNaturalDateResolver.resolveToEpochMillis(fromRaw) ?: now
                        } else now

                        val diffMillis = toMillis - fromMillis
                        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
                        put("days", days)
                        put("fromFormatted", CalendarNaturalDateResolver.formatDateTime(fromMillis))
                        put("toFormatted", CalendarNaturalDateResolver.formatDateTime(toMillis))
                    }
                    "add_duration" -> {
                        val baseMillis = if (!targetRaw.isNullOrEmpty()) {
                            CalendarNaturalDateResolver.resolveToEpochMillis(targetRaw) ?: now
                        } else now

                        val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }
                        val durLower = duration?.lowercase(Locale.ROOT) ?: "0 days"
                        when {
                            durLower.contains("day") -> {
                                val d = durLower.filter { it.isDigit() }.toIntOrNull() ?: 0
                                cal.add(Calendar.DAY_OF_YEAR, d)
                            }
                            durLower.contains("week") -> {
                                val w = durLower.filter { it.isDigit() }.toIntOrNull() ?: 0
                                cal.add(Calendar.WEEK_OF_YEAR, w)
                            }
                            durLower.contains("month") -> {
                                val m = durLower.filter { it.isDigit() }.toIntOrNull() ?: 0
                                cal.add(Calendar.MONTH, m)
                            }
                        }
                        put("initialDate", CalendarNaturalDateResolver.formatDateTime(baseMillis))
                        put("resultDate", CalendarNaturalDateResolver.formatDateTime(cal.timeInMillis))
                    }
                    "day_of_week" -> {
                        val targetMillis = if (!targetRaw.isNullOrEmpty()) {
                            CalendarNaturalDateResolver.resolveToEpochMillis(targetRaw) ?: now
                        } else now
                        val cal = Calendar.getInstance().apply { timeInMillis = targetMillis }
                        val dayOfWeek = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
                        put("date", CalendarNaturalDateResolver.formatDateTime(targetMillis))
                        put("dayOfWeek", dayOfWeek)
                    }
                }
            }

            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
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
                errorMessage = "Date computation failed: ${e.message}"
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val channelId = "tavi_reminders"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Tavi Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Tavi Reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}

class ReminderCreateTool : AssistantTool {
    override val name: String = "reminder_create"
    override val category: ToolCategory = ToolCategory.CLOCK
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val specialAccessType: SpecialAccessType = SpecialAccessType.EXACT_ALARM
    override val description: String = "Schedules an Android system reminder notification at a designated natural time."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Reminder text"},
            "time": {"type": "string", "description": "Natural date/time string (e.g., 'in 30 minutes', 'tomorrow at 8am')"}
          },
          "required": ["title", "time"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val title = arguments["title"]?.toString()?.trim()
        val timeRaw = arguments["time"]?.toString()?.trim()

        if (title.isNullOrEmpty() || timeRaw.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Both 'title' and 'time' are required."
            )
        }

        val triggerMillis = CalendarNaturalDateResolver.resolveToEpochMillis(timeRaw)
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Could not resolve time: '$timeRaw'."
            )

        if (triggerMillis <= System.currentTimeMillis()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Reminder time must be in the future."
            )
        }

        val reminderId = java.util.UUID.randomUUID().toString()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("reminderId", reminderId)
        }
        val reqCode = (reminderId.hashCode() and 0x7FFFFFFF) % 1000000
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true

            if (hasExactAlarm) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }

            // Persist reminder in local database so user can view/cancel it
            TaviDatabase.getInstance(context).insertReminder(reminderId, title, triggerMillis)

            val resultObj = JSONObject().apply {
                put("verified", true)
                put("reminderId", reminderId)
                put("title", title)
                put("exactTiming", hasExactAlarm)
                put("triggerTime", CalendarNaturalDateResolver.formatDateTime(triggerMillis))
                put("triggerEpoch", triggerMillis)
                put("status", "REMINDER_SCHEDULED")
                put("timingNote", if (hasExactAlarm) "Exact delivery scheduled." else "Inexact delivery window scheduled (Exact alarm permission not granted).")
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to schedule reminder alarm: ${e.message}"
            )
        }
    }
}

class ReminderListTool : AssistantTool {
    override val name: String = "reminder_list"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Lists all upcoming active reminders scheduled on device."
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
            val db = TaviDatabase.getInstance(context)
            val reminders = db.getActiveReminders()
            val arr = JSONArray()
            for (r in reminders) {
                val epoch = r["triggerTime"] as Long
                arr.put(
                    JSONObject().apply {
                        put("id", r["id"])
                        put("title", r["title"])
                        put("triggerTime", CalendarNaturalDateResolver.formatDateTime(epoch))
                        put("epoch", epoch)
                    }
                )
            }
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("count", reminders.size)
                    put("reminders", arr)
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
                errorMessage = "Failed to list reminders: ${e.message}"
            )
        }
    }
}

class ReminderCancelTool : AssistantTool {
    override val name: String = "reminder_cancel"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val description: String = "Cancels or deletes a scheduled reminder by title or ID."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": {
              "type": "string",
              "description": "The title or ID of the reminder to cancel"
            }
          },
          "required": ["query"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val query = arguments["query"]?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Query parameter required to identify reminder."
            )
        }

        return try {
            val db = TaviDatabase.getInstance(context)
            val all = db.getActiveReminders()
            val match = all.firstOrNull {
                (it["id"] as? String)?.equals(query, ignoreCase = true) == true ||
                (it["title"] as? String)?.contains(query, ignoreCase = true) == true
            }

            if (match == null) {
                return ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    state = ToolExecutionState.NOT_FOUND,
                    errorMessage = "No active reminder found matching '$query'."
                )
            }

            val id = match["id"] as String
            val title = match["title"] as String

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("reminderId", id)
            }
            val reqCode = (id.hashCode() and 0x7FFFFFFF) % 1000000
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }

            db.deleteReminder(id)

            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("cancelled", true)
                    put("id", id)
                    put("title", title)
                    put("status", "REMINDER_CANCELLED")
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
                errorMessage = "Failed to cancel reminder: ${e.message}"
            )
        }
    }
}

class NoteCreateTool : AssistantTool {
    override val name: String = "note_create"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val description: String = "Creates a persistent local note."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Note title"},
            "content": {"type": "string", "description": "Note body"}
          },
          "required": ["title", "content"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val title = arguments["title"]?.toString()?.trim()
        val content = arguments["content"]?.toString()?.trim()

        if (title.isNullOrEmpty() || content.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Both 'title' and 'content' are required to save a note."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val noteId = db.saveNote(title, content)

        val resultObj = JSONObject().apply {
            put("verified", true)
            put("id", noteId)
            put("noteId", noteId)
            put("title", title)
            put("content", content)
            put("status", "NOTE_SAVED")
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}

class NotesListTool : AssistantTool {
    override val name: String = "notes_list"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Lists or searches locally saved notes."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Optional search term to filter note titles or bodies"}
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
        val db = TaviDatabase.getInstance(context)
        val notes = if (!query.isNullOrEmpty()) db.searchNotes(query) else db.getAllNotes()

        val notesArray = JSONArray()
        for (n in notes) {
            notesArray.put(JSONObject().apply {
                put("id", n["id"])
                put("title", n["title"])
                put("content", n["content"])
                put("timestamp", n["timestamp"])
            })
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = JSONObject().apply {
                put("count", notes.size)
                put("notes", notesArray)
            }.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}

class NoteDeleteTool : AssistantTool {
    override val name: String = "note_delete"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL
    override val description: String = "Deletes a saved note by ID. Consequential action requiring confirmation."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "id": {"type": "string", "description": "The unique note ID to delete"}
          },
          "required": ["id"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val noteId = arguments["id"]?.toString()?.trim()
        if (noteId.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Note ID is required to delete."
            )
        }

        val isConfirmed = arguments["__user_confirmed"] == true
        if (!isConfirmed) {
            val confReq = SecurityGuard.createConfirmationRequest(name, mapOf("id" to noteId))
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                requiresUserConfirmation = true,
                confirmationPayload = confReq,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                errorMessage = "Confirmation required before deleting note."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val deleted = db.deleteNote(noteId)
        return if (deleted) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply { put("id", noteId); put("status", "DELETED") }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } else {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.NOT_FOUND,
                errorMessage = "Note with ID $noteId not found."
            )
        }
    }
}

class ClipboardReadTool : AssistantTool {
    override val name: String = "clipboard_read"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Reads current text content from the Android system clipboard."
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
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val primaryClip = cm?.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val rawText = primaryClip.getItemAt(0)?.text?.toString() ?: ""
                val sanitized = SecurityGuard.sanitizeExternalContent(rawText, "Clipboard")
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply {
                        put("hasContent", rawText.isNotEmpty())
                        put("text", sanitized)
                    }.toString(),
                    isSuccess = true,
                    state = ToolExecutionState.SUCCESS
                )
            } else {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply {
                        put("hasContent", false)
                        put("text", "")
                    }.toString(),
                    isSuccess = true,
                    state = ToolExecutionState.SUCCESS
                )
            }
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to access clipboard: ${e.message}"
            )
        }
    }
}

class ClipboardCopyTool : AssistantTool {
    override val name: String = "clipboard_copy"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Copies text to the Android system clipboard."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "text": {"type": "string", "description": "Text to copy to clipboard"}
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
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Tavi", text)
            cm?.setPrimaryClip(clip)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "COPIED_TO_CLIPBOARD")
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
                errorMessage = "Failed to copy to clipboard: ${e.message}"
            )
        }
    }
}
