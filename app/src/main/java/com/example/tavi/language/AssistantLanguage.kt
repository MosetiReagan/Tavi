package com.example.tavi.language

import android.content.Context
import java.util.Locale

object AssistantLanguage {
    const val PREFS = "tavi_voice_prefs"
    const val KEY = "assistant_language"
    fun selected(context: Context) = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "auto") ?: "auto" }.getOrDefault("auto")
    fun locale(context: Context): Locale = when (selected(context)) { "sw" -> Locale.forLanguageTag("sw-KE"); "en" -> Locale.forLanguageTag("en-US"); else -> Locale.getDefault() }
    fun set(context: Context, code: String) {
        require(code in setOf("auto", "en", "sw"))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }
    fun replyInstruction(context: Context): String = if (selected(context) == "auto")
        "Reply in the user's language; preserve names, numbers, and quoted message text."
    else "Reply in ${if (selected(context) == "sw") "Kiswahili" else "English"}; preserve names, numbers, and quoted message text."
}

/** Only command framing is translated; names, message bodies, and note text stay intact. */
object LanguageRouter {
    fun route(input: String): String {
        val text = input.trim()
        val direct = mapOf("saa ngapi" to "what time is it", "saa ngapi sasa" to "what time is it",
            "betri" to "battery", "kiwango cha betri" to "battery", "onyesha vikumbusho" to "show reminders",
            "onyesha notes" to "show notes", "onyesha kumbukumbu" to "show notes", "ratiba ya asubuhi" to "morning routine",
            "ratiba ya kulala" to "bedtime routine", "unaitwa nani" to "who are you", "wewe ni nani" to "who are you",
            "msaada" to "help", "unaweza kufanya nini" to "what can you do")
        direct[text.lowercase().trimEnd('?', '.', '!')]?.let { return it }
        Regex("(?i)^(?:onyesha|angalia)\\s+(?:kalenda|ratiba)(?:\\s+yangu)?(?:\\s+ya)?\\s+(leo|kesho)$").matchEntire(text)?.let {
            return "show calendar ${if (it.groupValues[1].equals("leo", true)) "today" else "tomorrow"}"
        }
        Regex("(?i)^(tuma|andaa)\\s+(?:ujumbe|sms|message)\\s+(?:kwa|to)\\s+(.+?)\\s+(?:ukisema|saying|sema)\\s+(.+)$").matchEntire(text)?.let {
            return (if (it.groupValues[1].equals("tuma", true)) "send sms" else "draft message") +
                " to ${it.groupValues[2]} saying ${it.groupValues[3]}"
        }
        Regex("(?i)^(tuma|andaa)\\s+(?:ujumbe|sms|message)(?:\\s+(?:kwa|to)\\s+(.+))?$").matchEntire(text)?.let {
            return (if (it.groupValues[1].equals("tuma", true)) "send sms" else "draft message") +
                it.groupValues[2].takeIf { recipient -> recipient.isNotBlank() }?.let { recipient -> " to $recipient" }.orEmpty()
        }
        Regex("(?i)^weka\\s+kengele(?:\\s+saa\\s+(.+))?$").matchEntire(text)?.let {
            return "set alarm" + it.groupValues[1].takeIf { time -> time.isNotBlank() }?.let { time -> " at $time" }.orEmpty()
        }
        Regex("(?i)^weka\\s+(?:kipima muda|timer)$").matchEntire(text)?.let { return "set a timer" }
        Regex("(?i)^nikumbushe\\s+(.+?)\\s+(leo|kesho)\\s+(?:saa\\s+)?(\\d{2}:\\d{2})$").matchEntire(text)?.let {
            return "remind me to ${it.groupValues[1]} at ${if (it.groupValues[2].equals("leo", true)) "today" else "tomorrow"} at ${it.groupValues[3]}"
        }
        val prefixes = listOf(
            "(?i)^fungua\\s+(.+)$" to "open ",
            "(?i)^tafuta\\s+(?:namba\\s+ya|mawasiliano\\s+ya|contact)\\s+(.+)$" to "find contact ",
            "(?i)^tafuta\\s+(?:katika\\s+)?(?:nyaraka|documents)\\s+(.+)$" to "search documents ",
            "(?i)^(?:andika|hifadhi)\\s+(?:note|kumbukumbu):?\\s+(.+)$" to "take a note: ",
            "(?i)^kumbuka\\s+(?:kwamba\\s+)?(.+)$" to "remember that ",
            "(?i)^sahau\\s+(.+)$" to "forget ",
            "(?i)^hesabu\\s+(.+)$" to "calculate ")
        prefixes.forEach { (pattern, prefix) -> Regex(pattern).matchEntire(text)?.let { return prefix + it.groupValues[1] } }
        return text
    }
}
