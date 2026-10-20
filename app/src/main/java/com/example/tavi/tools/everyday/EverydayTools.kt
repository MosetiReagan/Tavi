package com.example.tavi.tools.everyday

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.tavi.MainActivity
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.ToolResult
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.calendar.CalendarNaturalDateResolver
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
                isSuccess = true
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Calculation error: ${e.message}"
            )
        }
    }

    /**
     * Deterministic, safe expression evaluator supporting +, -, *, /, ^, %, sqrt, parentheses.
     */
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
                isSuccess = true
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
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
    override val description: String = "Schedules a real Android system reminder notification at a designated natural time."
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
                errorMessage = "Both 'title' and 'time' are required."
            )
        }

        val triggerMillis = CalendarNaturalDateResolver.resolveToEpochMillis(timeRaw)
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Could not resolve time: '$timeRaw'."
            )

        if (triggerMillis <= System.currentTimeMillis()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Reminder time must be in the future."
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
        }
        val reqCode = (triggerMillis % 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)

            val resultObj = JSONObject().apply {
                put("verified", true)
                put("title", title)
                put("triggerTime", CalendarNaturalDateResolver.formatDateTime(triggerMillis))
                put("triggerEpoch", triggerMillis)
                put("status", "REMINDER_SCHEDULED")
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Failed to schedule reminder alarm: ${e.message}"
            )
        }
    }
}

class NoteCreateTool : AssistantTool {
    override val name: String = "note_create"
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
                errorMessage = "Both 'title' and 'content' are required to save a note."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val noteId = db.saveNote(title, content)

        val resultObj = JSONObject().apply {
            put("verified", true)
            put("id", noteId)
            put("title", title)
            put("content", content)
            put("status", "NOTE_SAVED")
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true
        )
    }
}
