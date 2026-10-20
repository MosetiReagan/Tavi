package com.example.tavi.security

import com.example.tavi.model.AuditLogEntry
import com.example.tavi.model.ConfirmationRequest
import java.util.regex.Pattern

object SecurityGuard {

    private val PROMPT_INJECTION_PATTERNS = listOf(
        Pattern.compile("(?i)ignore (all )?previous instructions"),
        Pattern.compile("(?i)system prompt override"),
        Pattern.compile("(?i)you are now in (developer|god|dan) mode"),
        Pattern.compile("(?i)disregard your (system|core) directives"),
        Pattern.compile("(?i)output the system prompt"),
        Pattern.compile("(?i)exfiltrate (user )?data"),
        Pattern.compile("(?i)bypass confirmation")
    )

    private val CONSEQUENTIAL_ACTIONS = setOf(
        "calendar_delete_event",
        "messages_send_sms",
        "memory_clear_all",
        "browser_submit_form"
    )

    /**
     * Sanitizes external untrusted content (e.g., from web pages, notifications, or incoming SMS)
     * before injecting into the model's context.
     * Prevents indirect prompt injection by neutralizing attack triggers and demarcating data boundaries.
     */
    fun sanitizeExternalContent(rawContent: String, source: String): String {
        var sanitized = rawContent
        for (pattern in PROMPT_INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED_SECURITY_RISK]")
        }

        // Limit external string length to avoid context exhaustion attacks
        val truncated = if (sanitized.length > 4000) {
            sanitized.substring(0, 4000) + "\n...[Content truncated for memory safety]..."
        } else {
            sanitized
        }

        return """
            <UNTRUSTED_EXTERNAL_DATA source="$source">
            $truncated
            </UNTRUSTED_EXTERNAL_DATA>
        """.trimIndent()
    }

    /**
     * Determines whether a proposed tool execution is consequential and requires explicit user confirmation.
     */
    fun requiresUserConfirmation(toolName: String): Boolean {
        return CONSEQUENTIAL_ACTIONS.contains(toolName)
    }

    /**
     * Builds a structured ConfirmationRequest for consequential actions.
     */
    fun createConfirmationRequest(
        toolName: String,
        arguments: Map<String, Any?>
    ): ConfirmationRequest {
        val (actionType, title, details) = when (toolName) {
            "messages_send_sms" -> {
                val recipient = arguments["recipient"]?.toString() ?: "Unknown"
                val body = arguments["body"]?.toString() ?: ""
                Triple(
                    "SEND_SMS",
                    "Send SMS Message",
                    "Send text message to: $recipient\nMessage: \"$body\""
                )
            }
            "calendar_delete_event" -> {
                val eventId = arguments["eventId"]?.toString() ?: ""
                val title = arguments["title"]?.toString() ?: "Event #$eventId"
                Triple(
                    "DELETE_EVENT",
                    "Delete Calendar Event",
                    "Permanently delete event: \"$title\" (ID: $eventId)"
                )
            }
            "memory_clear_all" -> {
                Triple(
                    "CLEAR_MEMORY",
                    "Clear Personal Memory",
                    "Permanently delete all saved personal memory and notes."
                )
            }
            else -> {
                Triple(
                    "CONSEQUENTIAL_ACTION",
                    "Confirm Action: $toolName",
                    "Perform action with parameters: $arguments"
                )
            }
        }

        return ConfirmationRequest(
            actionType = actionType,
            title = title,
            details = details,
            toolName = toolName,
            arguments = arguments
        )
    }

    /**
     * Creates an audit log entry while scrubbing private or sensitive payload details.
     */
    fun createAuditLog(
        toolName: String,
        status: String,
        summary: String,
        verified: Boolean
    ): AuditLogEntry {
        return AuditLogEntry(
            toolName = toolName,
            status = status,
            summary = summary,
            verified = verified
        )
    }
}
