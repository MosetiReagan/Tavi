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

    override suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<Message>,
        onToken: (String) -> Unit
    ): String {
        // 1. Check for prompt injection attacks
        for (msg in conversationHistory) {
            if (containsInjectionAttempt(msg.content)) {
                val refusal = "I detected an attempt to override my security instructions or exfiltrate private data in external content. As a privacy-focused assistant, I strictly refuse to execute untrusted directives."
                streamText(refusal, onToken)
                return refusal
            }
        }
        if (containsInjectionAttempt(prompt)) {
            val refusal = "I cannot process instructions that attempt to override my security parameters or safety boundaries."
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
        val q = userQuery.trim().lowercase(Locale.ROOT)
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

        // 2. Date Calculations
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

        // 3. Reminders
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

        // 4. Notes
        val noteMatch = Pattern.compile("(?i)(?:take a note|save note|note that|create note):?\\s+(.+?)(?:\\s+titled?\\s+(.+))?$").matcher(userQuery)
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

        // 5. Personal Memory
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

        // 6. Calendar: List Events
        if (q.contains("calendar") || q.contains("events") || q.contains("schedule") || q.contains("agenda") || q.contains("meetings")) {
            if (q.contains("list") || q.contains("show") || q.contains("what's on") || q.contains("check") || q.contains("upcoming") || q.contains("any meetings")) {
                val start = if (q.contains("tomorrow")) "tomorrow" else "today"
                val end = if (q.contains("tomorrow")) "in 2 days" else "in 7 days"
                toolCalls.add(
                    ToolCall(
                        toolName = "calendar_list_events",
                        arguments = mapOf("startDate" to start, "endDate" to end)
                    )
                )
                return toolCalls
            }
        }

        // 7. Calendar: Create Event
        val eventCreateMatch = Pattern.compile("(?i)(?:schedule|create event|add event|set meeting)\\s+(?:called|titled)?\\s*(.+?)(?:\\s+(?:on|at|for)\\s+(.+))?$").matcher(userQuery)
        if (eventCreateMatch.find()) {
            val title = eventCreateMatch.group(1)?.trim() ?: ""
            val time = eventCreateMatch.group(2)?.trim() ?: ""
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

        // 8. Calendar: Delete Event
        val eventDeleteMatch = Pattern.compile("(?i)(?:delete|cancel|remove)\\s+(?:event|meeting)\\s+(?:#|id\\s+)?([0-9]+)").matcher(userQuery)
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

        // 9. Messages: Send SMS
        val sendSmsMatch = Pattern.compile("(?i)(?:send sms|text|send message)\\s+to\\s+([0-9\\+\\-\\(\\)\\s]+|Mom|Dad|John|Sarah|Work)\\s+(?:saying|with body|message:?)\\s+(.+)$").matcher(userQuery)
        if (sendSmsMatch.find()) {
            val recipient = sendSmsMatch.group(1)?.trim() ?: ""
            val body = sendSmsMatch.group(2)?.trim() ?: ""
            toolCalls.add(
                ToolCall(
                    toolName = "messages_send_sms",
                    arguments = mapOf("recipient" to recipient, "body" to body)
                )
            )
            return toolCalls
        }

        // 10. Messages: Draft
        val draftMatch = Pattern.compile("(?i)(?:draft|prepare message|compose)\\s+(?:to\\s+([a-zA-Z0-9\\+\\s]+))?\\s*(?:saying|body:?)\\s+(.+)$").matcher(userQuery)
        if (draftMatch.find()) {
            val recipient = draftMatch.group(1)?.trim() ?: "Contact"
            val body = draftMatch.group(2)?.trim() ?: ""
            toolCalls.add(
                ToolCall(
                    toolName = "messages_draft",
                    arguments = mapOf("recipient" to recipient, "body" to body)
                )
            )
            return toolCalls
        }

        // 11. Messages: Search
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

        // 12. Notifications: Summary
        if (q.contains("notification") || q.contains("recent alerts") || q.contains("what did i miss")) {
            toolCalls.add(
                ToolCall(
                    toolName = "notifications_read_summary",
                    arguments = mapOf("limit" to 5)
                )
            )
            return toolCalls
        }

        // 13. Browser Tasks: Research
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

        // Ambiguity detection & Clarification requirements
        if (q == "schedule meeting" || q == "add event" || q == "create an event") {
            return "What is the title and date/time for the event you would like me to schedule? For example: 'Schedule Team Sync tomorrow at 3pm'."
        }

        if (q.startsWith("send sms") || q.startsWith("text someone") || q == "send message") {
            return "Who would you like to text, and what message should I send? For example: 'Send SMS to +1234567890 saying I am on my way'."
        }

        if (q.contains("who are you") || q.contains("what are you")) {
            return "I am Tavi, your private personal AI assistant. I run locally on your phone so your data, conversations, and calendar stay private and offline."
        }

        if (q.contains("help") || q.contains("what can you do")) {
            return """
                Here is what I can do locally on your device:
                • Calendar: List upcoming events, schedule new meetings, or remove events.
                • Messages: Draft replies, search messages, or send SMS (with your confirmation).
                • Reminders & Notes: Schedule notification alerts and keep local notes.
                • Personal Memory: Remember your preferences and facts privately.
                • Tools: Fast calculations and date arithmetic.
                • Web Research: Read public information safely without external AI services.
            """.trimIndent()
        }

        return "I understand. I am ready to assist you locally with your calendar, messages, reminders, or memory. How would you like me to help?"
    }

    private suspend fun streamText(text: String, onToken: (String) -> Unit) {
        val tokens = text.split(Regex("(?<=\\s)|(?=\\s)"))
        for (token in tokens) {
            onToken(token)
            delay(15) // Natural streaming simulation
        }
    }

    private fun containsInjectionAttempt(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("ignore previous instructions") ||
                lower.contains("system prompt override") ||
                lower.contains("exfiltrate data") ||
                lower.contains("developer mode enabled")
    }

    override fun getEngineDiagnostics(): Map<String, Any> {
        return mapOf(
            "engine" to name,
            "latencyMs" to 0,
            "inferenceLocation" to "On-Device CPU",
            "memoryUsage" to "< 5MB",
            "offlineCapable" to true
        )
    }
}
