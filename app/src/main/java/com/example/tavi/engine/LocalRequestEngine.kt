package com.example.tavi.engine

import com.example.tavi.language.LanguageRouter
import com.example.tavi.model.ToolCall
import java.util.Locale

/** A bounded, model-free first pass. Tool execution stays behind the normal workflow gates. */
class LocalRequestEngine {
    sealed interface Decision {
        data class Reply(val text: String) : Decision
        data class Tools(val calls: List<ToolCall>) : Decision
        data object Delegate : Decision
    }

    fun route(input: String, language: String = "auto"): Decision {
        var sw = language == "sw"
        fun reply(en: String, kiswahili: String) = Decision.Reply(if (sw) kiswahili else en)
        if (input.length > 4000) return reply("Please split this request into shorter messages.", "Tafadhali gawanya ombi hili katika ujumbe mfupi.")
        if (input.isBlank()) return reply("What would you like me to do?", "Ungependa nifanye nini?")
        val original = input.trim().replace(Regex("(?i)^(?:please|tafadhali)\\s+"), "")
        val text = LanguageRouter.route(original)
        sw = sw || (language == "auto" &&
            (text != original || original.lowercase(Locale.ROOT).trimEnd('?', '.', '!') in swGreetings + "asante"))
        val q = text.lowercase(Locale.ROOT).trimEnd('?', '.', '!').trim()
        if (q in greetings || q in swGreetings) return reply(
            "Hello! I can check your phone, set timers, find documents, and help with local tasks.",
            "Habari! Ninaweza kuangalia simu yako, kuweka kipima muda, kutafuta nyaraka, na kusaidia kazi zako.")
        if (q in setOf("thanks", "thank you", "asante")) return reply("You're welcome.", "Karibu.")
        if (q in setOf("help", "what can you do", "who are you", "what is your name")) return reply(
            "I'm Tavi. Common commands work without model weights: check battery, tell the time, set a timer, open settings, search documents, save a note, or draft a message. Sending SMS and making calls require confirmation. A local model handles open-ended questions.",
            "Mimi ni Tavi. Amri za kawaida hazihitaji modeli: angalia betri, uliza saa, weka kipima muda, fungua mipangilio, tafuta nyaraka, hifadhi note, au andaa ujumbe. Kutuma SMS na kupiga simu kunahitaji idhini. Modeli ya ndani inajibu maswali mengine.")

        simpleTools[q]?.let { return tools(it) }
        if (q in setOf("open settings", "system settings")) return tools("settings_open", mapOf("panel" to "general"))
        calendar.matchEntire(q)?.let {
            val day = it.groupValues[1].ifBlank { "today" }
            return tools("calendar_list_events", mapOf("startDate" to day, "endDate" to if (day == "tomorrow") "in 2 days" else "today"))
        }
        alarm.matchEntire(q)?.let {
            val hour = it.groupValues[1].toIntOrNull()
            val minutes = it.groupValues[2].ifBlank { "0" }.toIntOrNull()
            val period = it.groupValues[3]
            if (hour == null || minutes == null || minutes !in 0..59 ||
                hour !in (if (period.isEmpty()) 0..23 else 1..12)) {
                return reply("Use an alarm time such as 07:30 or 7:30 AM.", "Tumia saa ya kengele kama 07:30 au 7:30 AM.")
            }
            val normalizedHour = if (period.isEmpty()) hour else hour % 12 + if (period == "pm") 12 else 0
            return tools("alarm_create", mapOf("hour" to normalizedHour, "minutes" to minutes, "title" to "Alarm"))
        }
        timer.matchEntire(q)?.let {
            val count = it.groupValues[1].toLongOrNull()
            val multiplier = when { it.groupValues[2].startsWith("hour") -> 3600; it.groupValues[2].startsWith("min") -> 60; else -> 1 }
            if (count == null || count <= 0 || count > Int.MAX_VALUE / multiplier) {
                return reply("Choose a positive timer duration under 2,147,483,647 seconds.", "Chagua muda chanya ulio chini ya sekunde 2,147,483,647.")
            }
            return tools("timer_set", mapOf("seconds" to (count * multiplier).toInt(), "title" to "Timer"))
        }
        calculation.matchEntire(text)?.let { return tools("calculator", mapOf("expression" to it.groupValues[1].trim())) }
        flashlight.matchEntire(q)?.let { return tools("device_toggle_flashlight", mapOf("enabled" to (it.groupValues[1] == "on"))) }

        // Payload patterns use the original text so quoted message and note contents stay intact.
        message.matchEntire(text)?.let {
            return tools(if (it.groupValues[1].equals("send", true)) "messages_send_sms" else "messages_draft",
                mapOf("recipient" to it.groupValues[2].trim(), "body" to it.groupValues[3]))
        }
        note.matchEntire(text)?.let { return tools("note_create", mapOf("title" to it.groupValues[1].take(60), "content" to it.groupValues[1])) }
        document.matchEntire(text)?.let { return tools("documents_search", mapOf("query" to it.groupValues[1])) }
        contact.matchEntire(text)?.let { return tools("contacts_search", mapOf("name" to it.groupValues[1].trim())) }
        settings.matchEntire(q)?.let {
            val panel = when (val name = it.groupValues[1]) { "wi-fi" -> "wifi"; "volume" -> "sound"; else -> name }
            return tools("settings_open", mapOf("panel" to panel))
        }

        if (Regex("^(?:set (?:an? )?alarm|alarm (?:at|for))(?:\\s|$)").containsMatchIn(q))
            return reply("What exact alarm time should I use? For example, 07:30.", "Niweke kengele saa ngapi? Kwa mfano, 07:30.")
        if (Regex("^(?:set (?:a )?timer|timer)(?:\\s|$)").containsMatchIn(q))
            return reply("How long should the timer run? For example, 5 minutes.", "Kipima muda kiwe cha muda gani? Kwa mfano, dakika 5.")
        if (Regex("^(?:send|draft|compose) (?:an? )?(?:sms|message|text)(?:\\s|$)").containsMatchIn(q))
            return reply("Provide the recipient and the exact message: send SMS to Alice saying I'll be there.", "Taja mpokeaji na ujumbe kamili: tuma SMS kwa Alice ukisema Nitafika.")
        return Decision.Delegate
    }

    private fun tools(name: String, arguments: Map<String, Any?> = emptyMap()) =
        Decision.Tools(listOf(ToolCall(toolName = name, arguments = arguments)))

    companion object {
        private val greetings = setOf("hi", "hello", "hey", "good morning", "good evening", "how are you")
        private val swGreetings = setOf("habari", "hujambo", "jambo", "mambo")
        private val simpleTools = mapOf(
            "what time is it" to "clock_query_time", "what is the time" to "clock_query_time", "current time" to "clock_query_time", "tell me the time" to "clock_query_time",
            "battery" to "device_get_status", "battery level" to "device_get_status", "check battery" to "device_get_status", "device status" to "device_get_status", "free storage" to "device_get_status",
            "network status" to "network_get_status", "am i online" to "network_get_status", "am i connected" to "network_get_status",
            "show notes" to "notes_list", "show my notes" to "notes_list", "show reminders" to "reminder_list", "show my reminders" to "reminder_list",
            "show alarms" to "alarm_show", "show my alarms" to "alarm_show", "open camera" to "camera_launch", "show my notifications" to "notifications_read_summary",
            "read my screen" to "accessibility_read_screen", "where am i" to "location_get_current")
        private val calendar = Regex("(?:show (?:my )?calendar|what(?:'s| is) on my calendar)(?: (today|tomorrow))?")
        private val alarm = Regex("(?:set (?:an? )?alarm|alarm (?:for|at)) (?:for |at )?(\\d{1,10})(?::(\\d{1,10}))?\\s*(am|pm)?")
        private val timer = Regex("(?:set (?:a )?timer|timer) (?:for )?(\\d+) (min(?:ute)?|sec(?:ond)?|hour)s?")
        private val calculation = Regex("(?:calculate|compute|what is)\\s+([0-9+*/^%().\\s-]+|sqrt\\([0-9.]+\\))", RegexOption.IGNORE_CASE)
        private val flashlight = Regex("turn (on|off) (?:the )?(?:flashlight|torch)")
        private val message = Regex("(send|draft|compose) (?:an? )?(?:sms|message|text) to ([\\p{L}\\p{N} .+'()\\-]+?) (?:saying|:) (.+)", RegexOption.IGNORE_CASE)
        private val note = Regex("(?:take a note:|save (?:a )?note:|note:)\\s*(.+)", RegexOption.IGNORE_CASE)
        private val document = Regex("(?:search|find) (?:in )?(?:my )?(?:documents|document library|library) (?:for )?(.+)", RegexOption.IGNORE_CASE)
        private val contact = Regex("(?:find contact|search contacts for|look up contact) ([\\p{L}\\p{N} .+'()\\-]+)", RegexOption.IGNORE_CASE)
        private val settings = Regex("open (wifi|wi-fi|bluetooth|display|sound|volume|notifications|battery|apps|date|storage)(?: settings)?")
    }
}
