package com.example.tavi.tools.calendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object CalendarNaturalDateResolver {

    /**
     * Resolves a natural language date/time string into epoch milliseconds using the device's timezone.
     */
    fun resolveToEpochMillis(input: String, baseTimeMillis: Long = System.currentTimeMillis()): Long? {
        val clean = input.trim().lowercase(Locale.ROOT)
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = baseTimeMillis
        }

        // 1. Check relative duration "in X hours", "in X minutes", "in X days"
        val inMinutesMatch = Pattern.compile("in\\s+(\\d+)\\s+min(ute)?s?").matcher(clean)
        if (inMinutesMatch.find()) {
            val minutes = inMinutesMatch.group(1)?.toIntOrNull() ?: 0
            return cal.timeInMillis + (minutes * 60 * 1000L)
        }

        val inHoursMatch = Pattern.compile("in\\s+(\\d+)\\s+hours?").matcher(clean)
        if (inHoursMatch.find()) {
            val hours = inHoursMatch.group(1)?.toIntOrNull() ?: 0
            return cal.timeInMillis + (hours * 3600 * 1000L)
        }

        val inDaysMatch = Pattern.compile("in\\s+(\\d+)\\s+days?").matcher(clean)
        if (inDaysMatch.find()) {
            val days = inDaysMatch.group(1)?.toIntOrNull() ?: 0
            cal.add(Calendar.DAY_OF_YEAR, days)
            return cal.timeInMillis
        }

        // 2. Extract time component (e.g. "4pm", "16:30", "10:00 am")
        val timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        var hour = 9 // Default morning hour if unspecified
        var minute = 0
        var foundTime = false

        // Search for time near the end or preceded by "at"
        val atTimeMatcher = Pattern.compile("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").matcher(clean)
        if (atTimeMatcher.find()) {
            val rawHour = atTimeMatcher.group(1)?.toIntOrNull() ?: 9
            minute = atTimeMatcher.group(2)?.toIntOrNull() ?: 0
            val ampm = atTimeMatcher.group(3)?.lowercase(Locale.ROOT)
            hour = when {
                ampm == "pm" && rawHour < 12 -> rawHour + 12
                ampm == "am" && rawHour == 12 -> 0
                else -> rawHour
            }
            foundTime = true
        }

        // 3. Resolve day
        when {
            clean.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            clean.contains("today") -> {
                // Keep same day
            }
            clean.contains("next monday") -> setNextDayOfWeek(cal, Calendar.MONDAY)
            clean.contains("next tuesday") -> setNextDayOfWeek(cal, Calendar.TUESDAY)
            clean.contains("next wednesday") -> setNextDayOfWeek(cal, Calendar.WEDNESDAY)
            clean.contains("next thursday") -> setNextDayOfWeek(cal, Calendar.THURSDAY)
            clean.contains("next friday") -> setNextDayOfWeek(cal, Calendar.FRIDAY)
            clean.contains("next saturday") -> setNextDayOfWeek(cal, Calendar.SATURDAY)
            clean.contains("next sunday") -> setNextDayOfWeek(cal, Calendar.SUNDAY)
            else -> {
                // Try standard ISO-8601 or YYYY-MM-DD
                val formats = listOf(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm",
                    "yyyy-MM-dd HH:mm",
                    "yyyy-MM-dd",
                    "MM/dd/yyyy HH:mm",
                    "MM/dd/yyyy"
                )
                for (fmt in formats) {
                    try {
                        val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                            timeZone = TimeZone.getDefault()
                            isLenient = false
                        }
                        val parsed = sdf.parse(input.trim())
                        if (parsed != null) return parsed.time
                    } catch (_: Exception) {}
                }
            }
        }

        if (foundTime) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }

        return cal.timeInMillis
    }

    private fun setNextDayOfWeek(cal: Calendar, targetDay: Int) {
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var daysToAdd = (targetDay - currentDay + 7) % 7
        if (daysToAdd == 0) daysToAdd = 7
        cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
    }

    fun formatDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a (z)", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return sdf.format(Date(epochMillis))
    }
}
