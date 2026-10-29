package com.example.tavi.engine

import com.example.tavi.model.Message
import com.example.tavi.model.ToolCall
import com.example.tavi.security.SecurityGuard
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.regex.Pattern

class DeterministicAssistantEngine : InferenceEngine {
    override val name: String = "Tavi Deterministic Local Core"
    override val isLocal: Boolean = true
    override val isLoaded: Boolean = true

    override fun getEngineDiagnostics(): Map<String, Any> {
        return mapOf(
            "engineName" to name,
            "isLocal" to isLocal,
            "isLoaded" to isLoaded,
            "mode" to "Deterministic Typed Intent Router"
        )
    }

    override suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<Message>,
        onToken: (String) -> Unit
    ): String {
        // 1. Check for prompt injection attacks
        for (msg in conversationHistory) {
            if (containsInjectionAttempt(msg.content)) {
                val refusal = "I noticed an untrusted instruction in this conversation that attempts to bypass security boundaries. For your privacy and safety, I cannot process it."
                streamText(refusal, onToken)
                return refusal
            }
        }
        if (containsInjectionAttempt(prompt)) {
            val refusal = "I cannot process instructions that attempt to bypass safety boundaries or override security rules."
            streamText(refusal, onToken)
            return refusal
        }

        // 2. Synthesize conversational reply
        val response = synthesizeResponse(prompt, conversationHistory)
        streamText(response, onToken)
        return response
    }

    override suspend fun proposeToolCalls(
        userQuery: String,
        conversationHistory: List<Message>
    ): List<ToolCall> {
        val q = userQuery.trim().lowercase(Locale.ROOT).replace(Regex("[?!.,]+$"), "").trim()
        val toolCalls = mutableListOf<ToolCall>()

        // 1. Calculator expressions
        val mathMatch = Pattern.compile("(?i)(?:calculate|compute|what is|eval)\\s+([0-9\\+\\-\\*/\\^%\\(\\)\\.\\s]+|sqrt\\([0-9\\.]+\\))").matcher(userQuery)
        if (mathMatch.find() && mathMatch.group(1)?.any { it.isDigit() } == true) {
            val expr = mathMatch.group(1)?.trim() ?: ""
            if (expr.isNotEmpty()) {
                toolCalls.add(ToolCall(toolName = "calculator", arguments = mapOf("expression" to expr)))
                return toolCalls
            }
        }

        // 2. Alarms & Clock Tools
        val alarmMatch = Pattern.compile("(?i)(?:set\\s+(?:an?\\s+)?alarm|alarm\\s+(?:for|at))\\s+(?:for\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matcher(userQuery)
        if (alarmMatch.find()) {
            val rawHour = alarmMatch.group(1)?.toIntOrNull() ?: 7
            val min = alarmMatch.group(2)?.toIntOrNull() ?: 0
            val ampm = alarmMatch.group(3)?.lowercase(Locale.ROOT)
            val hour = when {
                ampm == "pm" && rawHour < 12 -> rawHour + 12
                ampm == "am" && rawHour == 12 -> 0
                else -> rawHour
            }
            toolCalls.add(
                ToolCall(
                    toolName = "alarm_create",
                    arguments = mapOf("hour" to hour, "minutes" to min, "title" to "Alarm")
                )
            )
            return toolCalls
        } else if (q == "show alarms" || q == "show my alarms" || q == "open alarms" || q == "view alarms") {
            toolCalls.add(ToolCall(toolName = "alarm_show", arguments = emptyMap()))
            return toolCalls
        }

        // 3. Timers
        val timerMatch = Pattern.compile("(?i)(?:set\\s+(?:a\\s+)?timer|timer\\s+(?:for)?)\\s+(?:for\\s+)?(\\d+)\\s*(min|minute|sec|second|hour)s?").matcher(userQuery)
        if (timerMatch.find()) {
            val num = timerMatch.group(1)?.toIntOrNull() ?: 60
            val unit = timerMatch.group(2)?.lowercase(Locale.ROOT) ?: "minute"
            val seconds = when {
                unit.startsWith("sec") -> num
                unit.startsWith("hour") -> num * 3600
                else -> num * 60
            }
            toolCalls.add(
                ToolCall(
                    toolName = "timer_set",
                    arguments = mapOf("seconds" to seconds, "title" to "Timer")
                )
            )
            return toolCalls
        }

        // 4. Current Time & Clock Query
        if (q == "what time is it" || q == "what is the time" || q == "current time" || q == "tell me the time") {
            toolCalls.add(ToolCall(toolName = "clock_query_time", arguments = emptyMap()))
            return toolCalls
        }

        // 5. Date Calculations
        if (q.contains("days until") || q.contains("days between") || q.contains("how many days to")) {
            val target = userQuery.replace(Regex("(?i).*?(?:days until|how many days to)\\s+"), "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "date_calculation",
                    arguments = mapOf("operation" to "days_between", "targetDate" to target)
                )
            )
            return toolCalls
        } else if (q.contains("day of the week") || q.contains("what day is")) {
            val target = userQuery.replace(Regex("(?i).*?(?:day of the week is|what day is)\\s+"), "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "date_calculation",
                    arguments = mapOf("operation" to "day_of_week", "targetDate" to target)
                )
            )
            return toolCalls
        } else if (q.contains("date in ") || q.contains("what is the date after")) {
            val duration = userQuery.replace(Regex("(?i).*?(?:date in|date after)\\s+"), "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "date_calculation",
                    arguments = mapOf("operation" to "add_duration", "duration" to duration)
                )
            )
            return toolCalls
        }

        // 6. Camera
        if (q == "open camera" || q == "take a picture" || q == "take a photo" || q == "launch camera") {
            toolCalls.add(ToolCall(toolName = "camera_launch", arguments = emptyMap()))
            return toolCalls
        }

        // 7. System Settings Panels
        if (q.contains("wifi settings") || q.contains("wi-fi settings") || q == "open wifi" || q == "open wi-fi") {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "wifi")))
            return toolCalls
        } else if (q.contains("bluetooth settings") || q == "open bluetooth") {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "bluetooth")))
            return toolCalls
        } else if (q.contains("display settings") || q == "open display") {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "display")))
            return toolCalls
        } else if (q.contains("sound settings") || q.contains("volume settings")) {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "sound")))
            return toolCalls
        } else if (q.contains("notification settings")) {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "notifications")))
            return toolCalls
        } else if (q.contains("battery settings")) {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "battery")))
            return toolCalls
        } else if (q.contains("app settings") || q == "application settings") {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "apps")))
            return toolCalls
        } else if (q == "open settings" || q == "system settings") {
            toolCalls.add(ToolCall(toolName = "settings_open", arguments = mapOf("panel" to "general")))
            return toolCalls
        }

        // 8. Device State & Connectivity
        if (q.contains("battery level") || q.contains("battery percentage") || q.contains("how much battery") || q == "battery") {
            toolCalls.add(ToolCall(toolName = "device_get_status", arguments = emptyMap()))
            return toolCalls
        } else if (q.contains("storage") || q.contains("free space") || q == "device status") {
            toolCalls.add(ToolCall(toolName = "device_get_status", arguments = emptyMap()))
            return toolCalls
        } else if (q.contains("network status") || q.contains("am i connected") || q.contains("am i online") || q.contains("wifi status")) {
            toolCalls.add(ToolCall(toolName = "network_get_status", arguments = emptyMap()))
            return toolCalls
        }

        // 8a. Device Flashlight
        if (q.contains("flashlight") || q.contains("torch") || q == "💡 flashlight" || q.contains("turn on flashlight") || q.contains("turn off flashlight")) {
            val enabled = !q.contains("off")
            toolCalls.add(
                ToolCall(
                    toolName = "device_toggle_flashlight",
                    arguments = mapOf("enabled" to enabled)
                )
            )
            return toolCalls
        }

        // 8b. Level 2 Access: Usage Stats & Screen Time
        if (q.contains("screen time") || q.contains("usage stats") || q.contains("app usage") ||
            q.contains("how much did i use") || q.contains("phone usage") || q == "usage"
        ) {
            val pkgFilter = if (q.contains("how much did i use")) {
                userQuery.replace(Regex("[?!.,]+$"), "")
                    .replace(Regex("(?i).*?how much did i use\\s+"), "")
                    .replace(Regex("(?i)\\s+(?:today|this week|yesterday)"), "")
                    .trim()
            } else null
            val args = mutableMapOf<String, Any?>("days" to 1, "limit" to 10)
            if (!pkgFilter.isNullOrBlank()) {
                args["packageFilter"] = pkgFilter
            }
            toolCalls.add(ToolCall(toolName = "usage_get_stats", arguments = args))
            return toolCalls
        }

        // 8c. Level 2 Access: Accessibility & Screen Inspection
        if (q == "read my screen" || q == "what's on my screen" || q == "what is on my screen" ||
            q == "read screen" || q == "inspect screen" || q == "what am i looking at"
        ) {
            toolCalls.add(ToolCall(toolName = "accessibility_read_screen", arguments = emptyMap()))
            return toolCalls
        } else if (q == "press back" || q == "go back" || q == "navigate back") {
            toolCalls.add(ToolCall(toolName = "accessibility_perform_action", arguments = mapOf("action" to "back")))
            return toolCalls
        } else if (q == "go home" || q == "press home") {
            toolCalls.add(ToolCall(toolName = "accessibility_perform_action", arguments = mapOf("action" to "home")))
            return toolCalls
        } else if (q == "show recents" || q == "open recents" || q == "recent apps") {
            toolCalls.add(ToolCall(toolName = "accessibility_perform_action", arguments = mapOf("action" to "recents")))
            return toolCalls
        } else if (q.contains("notification shade") || q == "pull down notifications" || q == "show notifications panel") {
            toolCalls.add(ToolCall(toolName = "accessibility_perform_action", arguments = mapOf("action" to "notifications")))
            return toolCalls
        } else if (q == "open quick settings" || q == "show quick settings") {
            toolCalls.add(ToolCall(toolName = "accessibility_perform_action", arguments = mapOf("action" to "quick_settings")))
            return toolCalls
        }

        // 9. Location & Maps
        if (q == "where am i" || q == "what is my location" || q == "current location" || q == "my coordinates") {
            toolCalls.add(ToolCall(toolName = "location_get_current", arguments = emptyMap()))
            return toolCalls
        }
        val navMatch = Pattern.compile("(?i)(?:navigate to|directions to|take me to|maps to|open maps for)\\s+(.+)$").matcher(userQuery)
        if (navMatch.find()) {
            val dest = navMatch.group(1)?.trim() ?: ""
            if (dest.isNotEmpty()) {
                toolCalls.add(ToolCall(toolName = "maps_navigate", arguments = mapOf("destination" to dest)))
                return toolCalls
            }
        }

        // 10. Phone Calls & Dialer
        val callMatch = Pattern.compile("(?i)(?:call|phone)\\s+([a-zA-Z0-9\\+\\s]+?)(?:\\s+now)?$").matcher(userQuery)
        val dialMatch = Pattern.compile("(?i)(?:dial|open dialer for)\\s+([a-zA-Z0-9\\+\\s]+)$").matcher(userQuery)
        if (q.startsWith("call ") && callMatch.find()) {
            val person = callMatch.group(1)?.trim() ?: ""
            if (person.isNotEmpty() && !person.equals("me", ignoreCase = true)) {
                toolCalls.add(ToolCall(toolName = "phone_call", arguments = mapOf("recipient" to person)))
                return toolCalls
            }
        } else if (dialMatch.find()) {
            val person = dialMatch.group(1)?.trim() ?: ""
            toolCalls.add(ToolCall(toolName = "phone_dial", arguments = mapOf("recipient" to person)))
            return toolCalls
        } else if (q == "recent calls" || q == "call history" || q == "call log") {
            toolCalls.add(ToolCall(toolName = "call_log_search", arguments = mapOf("limit" to 5)))
            return toolCalls
        }

        // 11. Applications: Launch & List
        val appLaunchMatch = Pattern.compile("(?i)(?:open|launch)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+app)?$").matcher(userQuery)
        if (appLaunchMatch.find()) {
            val appTarget = appLaunchMatch.group(1)?.trim() ?: ""
            val ignoredKeywords = setOf("calendar", "settings", "alarms", "notes", "camera", "my notes", "wifi", "bluetooth", "sms", "messages")
            if (appTarget.isNotEmpty() && appTarget.lowercase() !in ignoredKeywords) {
                toolCalls.add(ToolCall(toolName = "app_launch", arguments = mapOf("appName" to appTarget)))
                return toolCalls
            }
        }
        if (q == "what apps do i have" || q == "list apps" || q == "show installed apps" || q == "show apps") {
            toolCalls.add(ToolCall(toolName = "app_list_launchable", arguments = emptyMap()))
            return toolCalls
        }

        // 12. Clipboard
        if (q == "what is on my clipboard" || q == "read clipboard" || q == "clipboard content" || q == "paste") {
            toolCalls.add(ToolCall(toolName = "clipboard_read", arguments = emptyMap()))
            return toolCalls
        }
        val copyMatch = Pattern.compile("(?i)(?:copy|copy to clipboard)\\s+(.+)$").matcher(userQuery)
        if (copyMatch.find()) {
            val textToCopy = copyMatch.group(1)?.trim() ?: ""
            toolCalls.add(ToolCall(toolName = "clipboard_copy", arguments = mapOf("text" to textToCopy)))
            return toolCalls
        }

        // 13. Reminders
        if (q == "show reminders" || q == "list reminders" || q == "show my reminders" || q == "view reminders" || q == "what reminders do i have") {
            toolCalls.add(ToolCall(toolName = "reminder_list", arguments = emptyMap()))
            return toolCalls
        }
        val cancelRemindMatch = Pattern.compile("(?i)(?:cancel|delete|remove)\\s+(?:reminder|my reminder)\\s+(?:for\\s+|to\\s+)?(.+)$").matcher(userQuery)
        if (cancelRemindMatch.find()) {
            val target = cancelRemindMatch.group(1)?.trim() ?: ""
            toolCalls.add(ToolCall(toolName = "reminder_cancel", arguments = mapOf("query" to target)))
            return toolCalls
        }
        val remindMatch = Pattern.compile("(?i)(?:remind me to|set a reminder to|reminder:?)\\s+(.+?)(?:\\s+(?:at|in|on)\\s+(.+))?$").matcher(userQuery)
        if (remindMatch.find()) {
            val title = remindMatch.group(1)?.trim() ?: "Reminder"
            val time = remindMatch.group(2)?.trim() ?: "in 1 hour"
            toolCalls.add(
                ToolCall(
                    toolName = "reminder_create",
                    arguments = mapOf("title" to title, "time" to time)
                )
            )
            return toolCalls
        }

        // 14. Notes
        if (q == "show my notes" || q == "show notes" || q == "list notes" || q == "read my notes" || q == "view notes") {
            toolCalls.add(ToolCall(toolName = "notes_list", arguments = emptyMap()))
            return toolCalls
        }
        val searchNotesMatch = Pattern.compile("(?i)(?:search notes for|find note)\\s+(.+)$").matcher(userQuery)
        if (searchNotesMatch.find()) {
            val term = searchNotesMatch.group(1)?.trim() ?: ""
            toolCalls.add(ToolCall(toolName = "notes_list", arguments = mapOf("query" to term)))
            return toolCalls
        }
        val noteMatch = Pattern.compile("(?i)(?:take a note|save note|note that|create note|save as a note|save this as a note):?\\s+(.+?)(?:\\s+titled?\\s+(.+))?$").matcher(userQuery)
        if (noteMatch.find()) {
            val content = noteMatch.group(1)?.trim() ?: ""
            val title = noteMatch.group(2)?.trim() ?: (if (content.length > 20) content.take(20) + "..." else content)
            toolCalls.add(
                ToolCall(
                    toolName = "note_create",
                    arguments = mapOf("title" to title, "content" to content)
                )
            )
            return toolCalls
        }

        // 15. Personal Memory
        val rememberMatch = Pattern.compile("(?i)(?:remember that|save preference|my)\\s+([a-zA-Z0-9_\\s]+?)(?:\\s+is|\\s+expires in|\\s+expires on|\\s*:\\s*)(.+)$").matcher(userQuery)
        if (rememberMatch.find()) {
            val key = rememberMatch.group(1)?.trim()?.replace(" ", "_") ?: "general"
            val value = rememberMatch.group(2)?.trim() ?: ""
            toolCalls.add(
                ToolCall(
                    toolName = "memory_save",
                    arguments = mapOf("key" to key, "value" to value, "category" to "personal")
                )
            )
            return toolCalls
        } else if (q.contains("what is my") || q.contains("what did i say about") || q.contains("do you remember")) {
            val keyOrQuery = userQuery.replace(Regex("(?i).*?(?:what is my|what did i say about|do you remember)\\s+"), "").replace("?", "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "memory_retrieve",
                    arguments = mapOf("keyOrQuery" to keyOrQuery)
                )
            )
            return toolCalls
        } else if (q.contains("clear all memories") || q.contains("delete all memories") || q.contains("wipe memories") || q.contains("erase all memories")) {
            toolCalls.add(
                ToolCall(
                    toolName = "memory_clear_all",
                    arguments = emptyMap()
                )
            )
            return toolCalls
        } else if (q.contains("delete memory") || q.contains("forget that") || q.contains("forget my")) {
            val target = userQuery.replace(Regex("(?i).*?(?:delete memory|forget that|forget my)\\s+"), "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "memory_delete",
                    arguments = mapOf("keyOrId" to target)
                )
            )
            return toolCalls
        }

        // 16. Calendar: List Events
        val isCalendarListQuery = (
            q.contains("calendar") || q.contains("events") || q.contains("schedule") || q.contains("agenda") || q.contains("meetings") ||
            q.contains("what is up") || q.contains("what's up") || q.contains("what do i have") || q.contains("my day") ||
            q.contains("what's in") || q.contains("what is in") || q.contains("what's on") || q.contains("what is on")
        ) && !q.startsWith("schedule ") && !q.startsWith("create ") && !q.startsWith("add ") && !q.startsWith("delete ") && !q.startsWith("cancel ") && !q.startsWith("remove ")

        if (isCalendarListQuery) {
            val start = if (q.contains("tomorrow")) "tomorrow" else "today"
            val end = if (q.contains("tomorrow")) "in 2 days" else if (q.contains("today") || q.contains("what is up") || q.contains("what's up") || q.contains("my day") || q.contains("schedule")) "today" else "in 7 days"
            toolCalls.add(
                ToolCall(
                    toolName = "calendar_list_events",
                    arguments = mapOf("startDate" to start, "endDate" to end)
                )
            )
            return toolCalls
        }

        // 17. Calendar: Create Event
        val eventCreateMatch = Pattern.compile("(?i)(?:schedule|create(?:\\s+an?)?\\s+event|add(?:\\s+an?)?\\s+event|set meeting)\\s+(?:called|titled)?\\s*(.+?)(?:\\s+(?:on|at|for)\\s+(.+))?$").matcher(userQuery)
        if (eventCreateMatch.find()) {
            val p1 = eventCreateMatch.group(1)?.trim() ?: ""
            val p2 = eventCreateMatch.group(2)?.trim() ?: ""
            val title = if (p2.isNotEmpty()) p1 else "Event"
            val time = if (p2.isNotEmpty()) p2 else p1
            if (title.isNotEmpty() && time.isNotEmpty()) {
                toolCalls.add(
                    ToolCall(
                        toolName = "calendar_create_event",
                        arguments = mapOf("title" to title, "startTime" to time)
                    )
                )
                return toolCalls
            }
        }

        // 18. Calendar: Delete Event
        val eventDeleteMatch = Pattern.compile("(?i)(?:delete|cancel|remove)\\s+(?:calendar\\s+)?(?:event|meeting)\\s+(?:with\\s+)?(?:#|id\\s+)?([0-9]+)").matcher(userQuery)
        if (eventDeleteMatch.find()) {
            val eventId = eventDeleteMatch.group(1)?.toLongOrNull() ?: -1L
            toolCalls.add(
                ToolCall(
                    toolName = "calendar_delete_event",
                    arguments = mapOf("eventId" to eventId)
                )
            )
            return toolCalls
        }

        // 19. Messages: Draft
        val draftMatchWithBody = Pattern.compile("(?i)(?:can you\\s+)?(?:draft|prepare message|compose)(?:\\s+an?\\s*(?:test|sample)?\\s*(?:sms|message|text))?(?:\\s+to\\s+([a-zA-Z0-9\\+\\s]+?))?\\s*(?:saying|body:?|:)\\s+(.+)$").matcher(userQuery)
        val draftMatchSimple = Pattern.compile("(?i)(?:can you\\s+)?(?:draft|prepare|compose)(?:\\s+an?\\s*(?:test|sample)?\\s*(?:sms|message|text))?(?:\\s+to\\s+([a-zA-Z0-9\\+\\s]+))?$").matcher(userQuery)
        if (q.contains("draft") || q.contains("prepare message") || (q.contains("compose") && q.contains("message"))) {
            if (draftMatchWithBody.find()) {
                val rawRecipient = draftMatchWithBody.group(1)?.trim() ?: "Contact"
                val recipient = rawRecipient.replace(Regex("(?i)^to\\s+"), "").trim()
                val body = draftMatchWithBody.group(2)?.trim() ?: ""
                toolCalls.add(
                    ToolCall(
                        toolName = "messages_draft",
                        arguments = mapOf("recipient" to recipient, "body" to body)
                    )
                )
                return toolCalls
            } else if (draftMatchSimple.find()) {
                val rawRecipient = draftMatchSimple.group(1)?.trim() ?: "Contact"
                val recipient = rawRecipient.replace(Regex("(?i)^to\\s+"), "").trim()
                val body = if (q.contains("test") || q.contains("sample")) {
                    "This is a test message from Tavi."
                } else if (recipient != "Contact") {
                    "Hello $recipient, let me know when you are free."
                } else {
                    "Draft message from Tavi assistant."
                }
                toolCalls.add(
                    ToolCall(
                        toolName = "messages_draft",
                        arguments = mapOf("recipient" to recipient, "body" to body)
                    )
                )
                return toolCalls
            }
        }

        // 20. Messages: Send SMS
        val sendSmsMatch = Pattern.compile(
            "(?i)(?:actually\\s+|please\\s+)?(?:send\\s+(?:an?\\s+|it\\s+|this\\s+)?(?:sms|message|text)?\\s*(?:to)?|do\\s+(?:an?\\s+|it\\s+|this\\s+)?(?:sms|message|text)?\\s*to|write\\s+(?:an?\\s+|it\\s+|this\\s+)?(?:sms|message|text)?\\s*to|text|tell|message\\s+to|message)\\s+([a-zA-Z0-9\\+\\-\\(\\)\\s]+?)(?:\\s*:\\s*|\\s+(?:and\\s+(?:tell|say|ask)(?:\\s+(?:him|her|them))?(?:\\s+to)?|saying|telling (?:him|her|them) to|with body|that|to|message:?)\\s+)(.+)$"
        ).matcher(userQuery)
        val sendSmsSimple = Pattern.compile("(?i)(?:actually\\s+|please\\s+)?(?:send\\s+(?:an?\\s+|it\\s+|this\\s+)?(?:sms|message|text)?\\s*to|do\\s+(?:an?\\s+|it\\s+|this\\s+)?(?:sms|message|text)?\\s*to|write\\s+(?:an?\\s+|it\\s+|this\\s+)?(?:sms|message|text)?\\s*to|text|message\\s+to)\\s+([a-zA-Z0-9\\+\\-\\(\\)\\s]+)$").matcher(userQuery)
        if (sendSmsMatch.find()) {
            val rawRecipient = sendSmsMatch.group(1)?.trim() ?: ""
            val recipient = rawRecipient.replace(Regex("(?i)^to\\s+"), "").trim()
            val body = sendSmsMatch.group(2)?.trim() ?: ""
            if (recipient.isNotEmpty() && body.isNotEmpty()) {
                toolCalls.add(
                    ToolCall(
                        toolName = "messages_send_sms",
                        arguments = mapOf("recipient" to recipient, "body" to body)
                    )
                )
                return toolCalls
            }
        } else if (sendSmsSimple.find() && !q.contains("draft") && !q.contains("search") && !q.contains("find")) {
            val rawRecipient = sendSmsSimple.group(1)?.trim() ?: ""
            val recipient = rawRecipient.replace(Regex("(?i)^to\\s+"), "").trim()
            if (recipient.isNotEmpty()) {
                toolCalls.add(
                    ToolCall(
                        toolName = "messages_send_sms",
                        arguments = mapOf("recipient" to recipient, "body" to "Hello $recipient, please call me when you are free.")
                    )
                )
                return toolCalls
            }
        }

        // 21. Messages: Search
        if (q.contains("search messages") || q.contains("search sms") || q.contains("find message")) {
            val term = userQuery.replace(Regex("(?i).*?(?:search messages for|search sms for|find message containing)\\s+"), "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "messages_search",
                    arguments = mapOf("query" to term, "limit" to 5)
                )
            )
            return toolCalls
        }

        // 22. Notifications: Summary
        if (q.contains("notification") || q.contains("recent alerts") || q.contains("what did i miss")) {
            toolCalls.add(
                ToolCall(
                    toolName = "notifications_read_summary",
                    arguments = mapOf("limit" to 5)
                )
            )
            return toolCalls
        }

        // 23. Contacts: Search
        val contactPatterns = listOf(
            Pattern.compile("(?i)(?:search contacts for|search for|find contact|look up contact|find|look up|get number for|phone number of|contact details for)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+in\\s+(?:my\\s+)?contacts)?$"),
            Pattern.compile("(?i)([a-zA-Z0-9\\s]+?)(?:'s|’s)?\\s+(?:phone\\s+)?number\\??$"),
            Pattern.compile("(?i)(?:who is|call details for)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+in\\s+(?:my\\s+)?contacts)?$")
        )

        for (cp in contactPatterns) {
            val matcher = cp.matcher(userQuery.trim())
            if (matcher.find()) {
                val rawName = matcher.group(1)?.trim() ?: ""
                val name = rawName.replace(Regex("(?i)^(?:find|search|look up|get)\\s+"), "").trim()
                val stopWords = setOf("you", "this", "it", "something", "anything", "help", "me", "a", "the", "out", "now", "here", "today", "tomorrow")
                if (name.isNotEmpty() && name.lowercase() !in stopWords) {
                    toolCalls.add(
                        ToolCall(
                            toolName = "contacts_search",
                            arguments = mapOf("name" to name, "query" to name)
                        )
                    )
                    return toolCalls
                }
            }
        }

        // 24. Browser Tasks: Research
        if (q.startsWith("research ") || q.startsWith("search web ") || q.startsWith("browse ") || q.startsWith("look up ")) {
            val topic = userQuery.replace(Regex("(?i)^(?:research|search web for|browse|look up)\\s+"), "").trim()
            toolCalls.add(
                ToolCall(
                    toolName = "browser_research_topic",
                    arguments = mapOf("query" to topic)
                )
            )
            return toolCalls
        }

        return toolCalls
    }

    private fun synthesizeResponse(prompt: String, conversationHistory: List<Message>): String {
        val q = prompt.trim().lowercase(Locale.ROOT)

        if (q == "schedule meeting" || q == "add event" || q == "create an event") {
            return "What is the title and date/time for the event you would like me to schedule? For example: 'Schedule Team Sync tomorrow at 3pm'."
        }

        if (q.startsWith("send sms") || q.startsWith("text someone") || q == "send message" || q == "send a message") {
            return "Who would you like to text, and what message should I send? For example: 'Send SMS to +1234567890 saying I am on my way'."
        }

        if (q.contains("who are you") || q.contains("what are you")) {
            return "I am Tavi, your private personal AI assistant. I run locally on your phone so your data, conversations, and calendar stay private and offline."
        }

        if (q.contains("help") || q.contains("what can you do")) {
            return """
                Here is what I can do locally on your device:
                • Calendar: List upcoming events, schedule meetings with conflict detection, or delete events.
                • Contacts: Search contacts by name, phone, or email with ambiguity resolution.
                • Calls & Messages: Open dialer, initiate calls, draft messages, or send confirmed SMS.
                • Clock: Set real alarms, countdown timers, and exact reminder notifications.
                • Apps: Launch installed apps, search apps, open app settings, or share content.
                • Device & Settings: Query battery, storage, and open specific system Settings panels.
                • Notes & Clipboard: Save persistent notes, search notes, read and copy clipboard text.
                • Location & Maps: Query current GPS location and launch navigation.
                • Camera & Media: Launch camera and control media playback.
                • Notifications: Read and summarize active alerts locally.
            """.trimIndent()
        }

        return "I am ready to assist you offline. You can ask me to check your calendar, set an alarm, find a contact, open an app, check device battery, or take a note."
    }

    private suspend fun streamText(text: String, onToken: (String) -> Unit) {
        val words = text.split(" ")
        for (i in words.indices) {
            val w = words[i]
            onToken(if (i == 0) w else " $w")
            delay(10)
        }
    }

    private fun containsInjectionAttempt(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return SecurityGuard.isInjectionAttempt(lower)
    }
}
