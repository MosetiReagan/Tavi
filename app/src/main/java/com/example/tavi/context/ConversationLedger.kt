package com.example.tavi.context

import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import org.json.JSONObject

/**
 * Structured Conversation State Ledger.
 *
 * Deterministically extracts and maintains:
 * 1. Explicit user-provided facts (names, projects, technologies, preferences).
 * 2. Active task constraints and requirements.
 * 3. User corrections and updated values (superseding older values).
 * 4. Grounded tool execution results (IDs, contact details, timestamps).
 *
 * Keeps older relevant context accessible to Gemma 3 1B even after raw turns
 * are trimmed by the token budget manager.
 */
class ConversationLedger {

    val explicitFacts = LinkedHashMap<String, String>()
    val activeConstraints = LinkedHashSet<String>()
    val corrections = LinkedHashMap<String, String>()
    val recentToolSummaries = mutableListOf<String>()

    fun clear() {
        explicitFacts.clear()
        activeConstraints.clear()
        corrections.clear()
        recentToolSummaries.clear()
    }

    /**
     * Ingests a full conversation history and builds the authoritative ledger.
     */
    fun buildFromHistory(history: List<Message>) {
        clear()
        for (msg in history) {
            updateFromMessage(msg)
        }
    }

    /**
     * Inspects a single message turn and extracts relevant facts or corrections.
     */
    fun updateFromMessage(message: Message) {
        val text = message.content.trim()

        if (message.role == MessageRole.USER) {
            extractUserFacts(text)
            extractCorrections(text)
        } else if (message.role == MessageRole.ASSISTANT) {
            extractToolOutcomes(message)
        }
    }

    private fun extractUserFacts(text: String) {
        // Project name
        val projectMatch = Regex("(?i)\\b(?:my\\s+)?project(?:\\s+is)?\\s+(?:called|named)?\\s*([a-zA-Z0-9_-]+)\\b").find(text)
        if (projectMatch != null) {
            val name = projectMatch.groupValues[1]
            if (!name.equals("called", true) && !name.equals("named", true) && !name.equals("is", true)) {
                explicitFacts["project_name"] = name
            }
        }

        // Primary editor / tool
        val editorMatch = Regex("(?i)\\b(?:I\\s+use|using)\\s+([a-zA-Z0-9_-]+)\\s+as\\s+(?:my\\s+)?primary\\s+(?:code\\s+)?editor\\b").find(text)
        if (editorMatch != null) {
            explicitFacts["primary_editor"] = editorMatch.groupValues[1]
        }

        // Destination / travel location
        val travelMatch = Regex("(?i)\\b(?:traveling|going|flying)\\s+to\\s+([a-zA-Z\\s]+?)(?:\\s+next|\\s+tomorrow|\\s+today|\\.)").find(text)
        if (travelMatch != null) {
            explicitFacts["travel_destination"] = travelMatch.groupValues[1].trim()
        }

        // Dietary restrictions
        val dietMatch = Regex("(?i)\\bI\\s+am\\s+(strictly\\s+)?(vegan|vegetarian|gluten-free|keto|pescatarian)\\b").find(text)
        if (dietMatch != null) {
            explicitFacts["dietary_preference"] = dietMatch.groupValues[2]
        }

        // Pet name
        val petMatch = Regex("(?i)\\bmy\\s+(cat|dog)(?:'s)?\\s+name\\s+is\\s+([a-zA-Z0-9_-]+)\\b").find(text)
        if (petMatch != null) {
            explicitFacts["pet"] = "${petMatch.groupValues[2]} (${petMatch.groupValues[1]})"
        }

        // Server IP / host
        val hostMatch = Regex("(?i)\\b(?:server|host|target\\s+server|backend\\s+server)\\s+(?:IP\\s+)?(?:is|runs\\s+on)\\s+([a-zA-Z0-9_.-]+)\\b").find(text)
        if (hostMatch != null) {
            explicitFacts["server_host"] = hostMatch.groupValues[1]
        }

        // Port
        val portMatch = Regex("(?i)\\bport\\s+(\\d+)\\b").find(text)
        if (portMatch != null) {
            explicitFacts["port"] = portMatch.groupValues[1]
        }

        // Code / secret keyword
        val secretMatch = Regex("(?i)\\b(?:passcode|keyword|secret|code)\\s+(?:keyword\\s+)?is\\s+([a-zA-Z0-9_-]+)\\b").find(text)
        if (secretMatch != null) {
            explicitFacts["secret_keyword"] = secretMatch.groupValues[1]
        }

        // Favorite color
        val colorMatch = Regex("(?i)\\bmy\\s+favorite\\s+color\\s+is\\s+([a-zA-Z]+)\\b").find(text)
        if (colorMatch != null) {
            explicitFacts["favorite_color"] = colorMatch.groupValues[1]
        }

        // Primary physician / doctor
        val doctorMatch = Regex("(?i)\\b(Dr\\.?\\s+[a-zA-Z\\s]+?)\\s+is\\s+my\\s+primary\\b").find(text)
        if (doctorMatch != null) {
            explicitFacts["primary_physician"] = doctorMatch.groupValues[1].trim()
        }

        // Constraints
        if (text.contains("minimum", ignoreCase = true) || text.contains("require", ignoreCase = true)) {
            val constraintClean = text.replace(Regex("""^[•\-\*]\s*"""), "").trim()
            if (constraintClean.length in 5..80) {
                activeConstraints.add(constraintClean)
            }
        }
    }

    private fun extractCorrections(text: String) {
        // Time correction: "Actually, change that to 4 PM instead"
        val timeCorrection = Regex("(?i)(?:change\\s+that\\s+to|rescheduled\\s+to|reschedule\\s+to|make\\s+it)\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM|am|pm)?)").find(text)
        if (timeCorrection != null) {
            val newTime = timeCorrection.groupValues[1].trim()
            corrections["meeting_time"] = newTime
            explicitFacts["meeting_time"] = newTime
        }

        // Project name correction: "Correction: the name was changed to Artemis"
        val projectCorrection = Regex("(?i)(?:changed\\s+to|renamed\\s+to|correction:?\\s+(?:the\\s+name\\s+was\\s+changed\\s+to)?)\\s*([a-zA-Z0-9_-]+)").find(text)
        if (projectCorrection != null) {
            val newName = projectCorrection.groupValues[1].trim()
            if (!newName.equals("called", true) && !newName.equals("named", true)) {
                corrections["project_name"] = newName
                explicitFacts["project_name"] = newName
            }
        }

        // Budget correction: "make the budget $1,200"
        val budgetMatch = Regex("(?i)\\b(?:budget\\s+(?:is|set\\s+to|make\\s+the\\s+budget)|budget:?)\\s*\\$?([0-9,]+)\\b").find(text)
        if (budgetMatch != null) {
            val amount = budgetMatch.groupValues[1].trim()
            explicitFacts["budget"] = "$$amount"
            if (text.contains("make", true) || text.contains("updated", true) || text.contains("change", true)) {
                corrections["budget"] = "$$amount"
            }
        }

        // Recipient correction: "Wait, not Mark! Send it to Kevin"
        val recipientCorrection = Regex("(?i)(?:not\\s+[a-zA-Z]+!\\s*(?:Send\\s+it\\s+to|use)\\s+([a-zA-Z]+))").find(text)
        if (recipientCorrection != null) {
            val newRecipient = recipientCorrection.groupValues[1].trim()
            corrections["recipient"] = newRecipient
            explicitFacts["recipient"] = newRecipient
        }
    }

    private fun extractToolOutcomes(message: Message) {
        val tools = message.toolCalls ?: emptyList()
        val results = message.toolResults ?: emptyList()

        for (i in tools.indices) {
            val tool = tools[i]
            val result = results.getOrNull(i) ?: continue
            if (!result.isSuccess || result.resultJson.isBlank()) continue

            try {
                val json = JSONObject(result.resultJson)
                when (tool.toolName) {
                    "contacts_search" -> {
                        val arr = json.optJSONArray("contacts")
                        if (arr != null && arr.length() > 0) {
                            val c = arr.getJSONObject(0)
                            val name = c.optString("name")
                            val phone = c.optString("phone")
                            recentToolSummaries.add("Contact found: $name (phone: $phone)")
                        }
                    }
                    "calendar_create_event" -> {
                        val eventId = json.optLong("eventId", json.optLong("event_id", -1L))
                        val title = json.optString("title", tool.arguments["title"]?.toString() ?: "Event")
                        if (eventId > 0) {
                            recentToolSummaries.add("Created calendar event '$title' with event_id: $eventId")
                        }
                    }
                    "calendar_list_events" -> {
                        val events = json.optJSONArray("events")
                        if (events != null && events.length() > 0) {
                            val e = events.getJSONObject(0)
                            val title = e.optString("title")
                            val loc = e.optString("location")
                            val time = e.optString("start_time", e.optString("startTime"))
                            recentToolSummaries.add("Calendar event '$title' at $time (Location: $loc)")
                        }
                    }
                    "device_get_battery" -> {
                        val level = json.optInt("level", -1)
                        val isCharging = json.optBoolean("is_charging", false)
                        recentToolSummaries.add("Battery status: $level%, charging: $isCharging")
                    }
                    "apps_get_usage_stats" -> {
                        val top = json.optString("top_app")
                        val mins = json.optInt("total_foreground_minutes")
                        recentToolSummaries.add("Screen time: top app is $top ($mins minutes)")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Formats a concise Markdown context block for inclusion in the system turn.
     * Bounded to <= 180 tokens to avoid crowding conversational space.
     */
    fun formatLedgerHeader(): String {
        if (explicitFacts.isEmpty() && activeConstraints.isEmpty() && recentToolSummaries.isEmpty()) {
            return ""
        }

        val sb = StringBuilder("\n[ACTIVE CONVERSATION CONTEXT & EXPLICIT FACTS]\n")
        for ((k, v) in explicitFacts) {
            sb.append("• ").append(k.replace("_", " ")).append(": ").append(v).append("\n")
        }
        for (c in activeConstraints.toList().takeLast(3)) {
            sb.append("• constraint: ").append(c).append("\n")
        }
        for (s in recentToolSummaries.takeLast(2)) {
            sb.append("• tool outcome: ").append(s).append("\n")
        }
        return sb.toString().trimEnd()
    }
}
