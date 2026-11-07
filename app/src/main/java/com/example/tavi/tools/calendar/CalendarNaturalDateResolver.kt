package com.example.tavi.tools.calendar

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CalendarNaturalDateResolver {
    private val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val relative = Regex("in\\s+(\\d+)\\s+(minutes?|mins?|hours?|days?|weeks?)")
    private val time = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
    private val formats = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mmXXX",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd", "MM/dd/yyyy HH:mm", "MM/dd/yyyy")

    fun hasExplicitTime(input: String): Boolean {
        val clean = input.trim().lowercase(Locale.ROOT)
        return relative.matches(clean) || Regex("\\d{1,2}:\\d{2}|\\d\\s*(am|pm)\\b").containsMatchIn(clean)
    }

    /** Date-only queries resolve to midnight; time-only queries use the next occurrence. */
    fun resolveToEpochMillis(input: String, baseTimeMillis: Long = System.currentTimeMillis()): Long? {
        val raw = input.trim()
        val clean = raw.lowercase(Locale.ROOT)
        if (clean.isEmpty()) return null
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = baseTimeMillis }
        relative.matchEntire(clean)?.let { match ->
            val count = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..36500 } ?: return null
            when {
                match.groupValues[2].startsWith("min") -> cal.add(Calendar.MINUTE, count)
                match.groupValues[2].startsWith("hour") -> cal.add(Calendar.HOUR_OF_DAY, count)
                match.groupValues[2].startsWith("week") -> cal.add(Calendar.DAY_OF_YEAR, count * 7)
                else -> cal.add(Calendar.DAY_OF_YEAR, count)
            }
            return cal.timeInMillis
        }
        for (format in formats) {
            val parser = SimpleDateFormat(format, Locale.US).apply { isLenient = false; timeZone = TimeZone.getDefault() }
            val position = ParsePosition(0)
            val date = parser.parse(raw, position)
            if (date != null && position.index == raw.length) return date.time
        }
        val dayMatch = Regex("^(today|tomorrow|(?:next )?(?:" + days.joinToString("|") + "))(?:\\s+(?:at\\s+)?(.+))?$").matchEntire(clean)
        val timeText = if (dayMatch != null) dayMatch.groupValues[2] else clean.removePrefix("at ")
        if (dayMatch != null) {
            val day = dayMatch.groupValues[1]
            if (day == "tomorrow") cal.add(Calendar.DAY_OF_YEAR, 1)
            else if (day != "today") {
                val target = days.indexOf(day.removePrefix("next ")) + 1
                val offset = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, if (offset == 0) 7 else offset)
            }
        }
        var hour = 0
        var minute = 0
        if (timeText.isNotBlank()) {
            val match = time.matchEntire(timeText) ?: return null
            hour = match.groupValues[1].toIntOrNull() ?: return null
            minute = match.groupValues[2].ifEmpty { "0" }.toInt()
            val meridiem = match.groupValues[3]
            if (minute !in 0..59 || (meridiem.isEmpty() && hour !in 0..23) ||
                (meridiem.isNotEmpty() && hour !in 1..12)) return null
            if (meridiem.isEmpty() && match.groupValues[2].isEmpty()) return null
            if (meridiem.isNotEmpty()) hour = hour % 12 + if (meridiem == "pm") 12 else 0
        } else if (dayMatch == null) return null
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.isLenient = false
        return runCatching {
            if (dayMatch == null && cal.timeInMillis <= baseTimeMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.timeInMillis
        }.getOrNull()
    }

    fun formatDateTime(epochMillis: Long): String = SimpleDateFormat(
        "EEE, MMM d, yyyy 'at' h:mm a (z)", Locale.getDefault()
    ).apply { timeZone = TimeZone.getDefault() }.format(Date(epochMillis))
}
