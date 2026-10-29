package com.example.tavi.ui.main

import android.util.Log
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import org.json.JSONObject
import java.util.Locale

/**
 * Synthesizes and formats tool execution results into human-readable UI assistant responses.
 * Decouples presentation formatting from [MainScreenViewModel].
 */
object ToolResultSynthesizer {

    private const val TAG = "ToolResultSynthesizer"

    fun synthesize(tools: List<ToolCall>, results: List<ToolResult>): String {
        val sb = StringBuilder()
        for (i in tools.indices) {
            val t = tools[i]
            val r = results.getOrNull(i) ?: continue

            if (!r.isSuccess) {
                sb.append("⚠️ ${r.errorMessage ?: "Failed to execute ${t.toolName}."}\n")
                continue
            }

            when (t.toolName) {
                "calendar_list_events" -> {
                    sb.append("Here are your calendar events:\n")
                    sb.append(formatCalendarResult(r.resultJson))
                }
                "calendar_get_event" -> {
                    sb.append("Event details:\n${r.resultJson}")
                }
                "calendar_create_event" -> {
                    val conf = try {
                        JSONObject(r.resultJson).optBoolean("hasConflicts")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading hasConflicts flag", e)
                        false
                    }
                    if (conf) {
                        sb.append("⚠️ Note: Event overlaps with an existing meeting. Scheduled: \"${t.arguments["title"]}\" at ${t.arguments["startTime"]}.")
                    } else {
                        sb.append("✅ Event scheduled and verified in your calendar: \"${t.arguments["title"]}\" at ${t.arguments["startTime"]}.")
                    }
                }
                "calendar_update_event" -> {
                    sb.append("✅ Calendar event updated successfully.")
                }
                "calendar_delete_event" -> {
                    sb.append("✅ Calendar event deleted and verified.")
                }
                "calculator" -> {
                    val displayResult = try {
                        JSONObject(r.resultJson).opt("result")?.toString() ?: r.resultJson
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading calculator result", e)
                        r.resultJson
                    }
                    sb.append("Result: ${t.arguments["expression"]} = $displayResult")
                }
                "date_calculation" -> {
                    sb.append("Date calculation result: ${r.resultJson}")
                }
                "alarm_create" -> {
                    val minutes = t.arguments["minutes"]?.toString()?.toIntOrNull() ?: 0
                    sb.append("⏰ Alarm set for ${t.arguments["hour"]}:${String.format(Locale.getDefault(), "%02d", minutes)}.")
                }
                "alarm_show" -> {
                    sb.append("⏰ System Clock alarms opened.")
                }
                "timer_set" -> {
                    sb.append("⏳ Timer started for ${t.arguments["seconds"]} seconds.")
                }
                "clock_query_time" -> {
                    sb.append(formatTimeResult(r.resultJson))
                }
                "reminder_create" -> {
                    val timingNote = if (r.resultJson.contains("\"exactTiming\":false")) " (inexact window)" else ""
                    sb.append("⏰ Reminder scheduled: \"${t.arguments["title"]}\" for ${t.arguments["time"]}$timingNote.")
                }
                "reminder_list" -> {
                    try {
                        val count = JSONObject(r.resultJson).optInt("count", 0)
                        if (count == 0) {
                            sb.append("⏰ You have no active reminders scheduled.")
                        } else {
                            val arr = JSONObject(r.resultJson).getJSONArray("reminders")
                            sb.append("⏰ Upcoming Reminders ($count):\n")
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                sb.append("• ${item.getString("title")} — ${item.getString("triggerTime")}\n")
                            }
                        }
                    } catch (_: Exception) {
                        sb.append("⏰ Reminders: ${r.resultJson}")
                    }
                }
                "reminder_cancel" -> {
                    sb.append("🗑️ Reminder cancelled successfully.")
                }
                "app_launch" -> {
                    sb.append("🚀 Launched app: ${t.arguments["appName"]}.")
                }
                "app_list_launchable" -> {
                    sb.append(formatAppListResult(r.resultJson))
                }
                "app_open_settings" -> {
                    sb.append("⚙️ Opened App Info settings.")
                }
                "settings_open" -> {
                    sb.append("⚙️ Opened ${t.arguments["panel"] ?: "system"} settings.")
                }
                "device_get_status" -> {
                    sb.append(formatDeviceStatusResult(r.resultJson))
                }
                "network_get_status" -> {
                    sb.append(formatNetworkStatusResult(r.resultJson))
                }
                "location_get_current" -> {
                    sb.append(formatLocationResult(r.resultJson))
                }
                "maps_navigate" -> {
                    sb.append("🗺️ Opened maps for: ${t.arguments["destination"]}.")
                }
                "camera_launch" -> {
                    sb.append("📷 Camera opened.")
                }
                "media_control" -> {
                    sb.append("🎵 Media control dispatched: ${t.arguments["action"] ?: "play/pause"}.")
                }
                "phone_dial" -> {
                    sb.append("📞 Opened dialer with ${t.arguments["recipient"]}.")
                }
                "phone_call" -> {
                    sb.append("📞 Initiating call to ${t.arguments["recipient"]}.")
                }
                "call_log_search" -> {
                    sb.append(formatCallLogResult(r.resultJson))
                }
                "note_create" -> {
                    sb.append("📝 Note saved: \"${t.arguments["title"]}\".")
                }
                "notes_list" -> {
                    sb.append(formatNotesListResult(r.resultJson))
                }
                "note_delete" -> {
                    sb.append("🗑️ Note deleted.")
                }
                "clipboard_read" -> {
                    sb.append(formatClipboardResult(r.resultJson))
                }
                "clipboard_copy" -> {
                    sb.append("📋 Copied text to clipboard.")
                }
                "memory_save" -> {
                    sb.append("💾 Saved to your personal memory: ${t.arguments["key"]} = \"${t.arguments["value"]}\".")
                }
                "memory_retrieve" -> {
                    sb.append("Memory lookup: ${r.resultJson}")
                }
                "memory_delete" -> {
                    sb.append("🗑️ Removed from memory: ${t.arguments["keyOrId"]}.")
                }
                "messages_draft" -> {
                    sb.append("✉️ Draft prepared for ${t.arguments["recipient"]}:\n\"${t.arguments["body"]}\"")
                }
                "messages_send_sms" -> {
                    sb.append("✅ Text message sent to ${t.arguments["recipient"]}.")
                }
                "messages_search" -> {
                    sb.append("Found messages:\n${r.resultJson}")
                }
                "contacts_search" -> {
                    sb.append(formatContactsResult(r.resultJson))
                }
                "contacts_resolve" -> {
                    sb.append("Contact resolved: ${r.resultJson}")
                }
                "contacts_create" -> {
                    sb.append("👤 Contact created: ${t.arguments["name"]} (${t.arguments["phoneNumber"]}).")
                }
                "notifications_read_summary" -> {
                    sb.append("Notification summary:\n${r.resultJson}")
                }
                "notifications_dismiss" -> {
                    sb.append("Notification dismissed.")
                }
                "browser_open_url" -> {
                    sb.append("🌐 Opened browser URL: ${t.arguments["url"]}.")
                }
                "share_content" -> {
                    sb.append("📤 Share sheet displayed.")
                }
                "files_browse_document" -> {
                    sb.append("📁 Opened document picker.")
                }
                "files_export_note" -> {
                    sb.append("📤 Exported note to share sheet.")
                }
                "browser_research_topic" -> {
                    sb.append("🌐 Research findings for \"${t.arguments["query"]}\":\n${r.resultJson}")
                }
                "device_toggle_flashlight" -> {
                    val enabled = t.arguments["enabled"] != false && !r.resultJson.contains("\"enabled\":false")
                    if (r.isSuccess) {
                        sb.append(if (enabled) "💡 Flashlight turned on." else "💡 Flashlight turned off.")
                    } else {
                        sb.append("⚠️ Could not toggle flashlight: ${r.errorMessage ?: "Hardware unavailable"}")
                    }
                }
                else -> {
                    if (r.isSuccess) {
                        val friendlyName = t.toolName.replace("_", " ").replaceFirstChar { it.uppercase() }
                        sb.append("✅ $friendlyName completed successfully.")
                    } else {
                        sb.append("⚠️ Action failed: ${r.errorMessage ?: "Unknown error"}.")
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    private fun formatCalendarResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val events = json.optJSONArray("events")
            if (events == null || events.length() == 0) {
                return "No upcoming events found in this time window."
            }
            val sb = StringBuilder()
            for (i in 0 until events.length()) {
                val e = events.getJSONObject(i)
                sb.append("• ${e.optString("title")} (${e.optString("startTime")})")
                val loc = e.optString("location")
                if (loc.isNotEmpty()) sb.append(" at $loc")
                sb.append("\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing calendar result: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatContactsResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val contacts = json.optJSONArray("contacts")
            if (contacts == null || contacts.length() == 0) {
                return "No matching contacts found on device."
            }
            val sb = StringBuilder("Contacts matching \"${json.optString("query")}\":\n")
            for (i in 0 until contacts.length()) {
                val c = contacts.getJSONObject(i)
                sb.append("• ${c.optString("name")}: ${c.optString("phoneNumber")} (${c.optString("type", "Mobile")})\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing contacts result: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatTimeResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            "🕒 Current Time: ${json.optString("currentTime")}\n📅 ${json.optString("currentDate")} (${json.optString("timezoneId")})"
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing time result: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatAppListResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val apps = json.optJSONArray("apps") ?: return jsonStr
            val sb = StringBuilder("Installed launchable apps (${apps.length()}):\n")
            for (i in 0 until minOf(apps.length(), 15)) {
                val a = apps.getJSONObject(i)
                sb.append("• ${a.optString("appName")}\n")
            }
            if (apps.length() > 15) {
                sb.append("... and ${apps.length() - 15} more.")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing app list result: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatDeviceStatusResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val charging = if (json.optBoolean("isCharging")) "⚡ Charging" else "Discharging"
            "🔋 Battery: ${json.optInt("batteryPercent")}% ($charging)\n💾 Storage: ${json.optString("freeStorageGb")} GB free / ${json.optString("totalStorageGb")} GB total\n📱 Android ${json.optString("androidVersion")} (${json.optString("deviceModel")})"
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing device status: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatNetworkStatusResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val status = if (json.optBoolean("isOffline")) "Offline" else "Online (${json.optString("transport")})"
            val metered = if (json.optBoolean("isMetered")) " [Metered]" else ""
            "📶 Network: $status$metered"
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing network status: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatLocationResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("latitude")) {
                "📍 Location: ${json.optDouble("latitude")}, ${json.optDouble("longitude")} (Accuracy: ~${json.optDouble("accuracyMeters").toInt()}m)"
            } else {
                json.optString("message", "No recent location fix.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing location result: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatCallLogResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val calls = json.optJSONArray("calls") ?: return jsonStr
            val sb = StringBuilder("Recent Calls:\n")
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val name = c.optString("name").ifEmpty { c.optString("number") }
                sb.append("• [${c.optString("type")}] $name (${c.optString("number")})\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing call log: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatNotesListResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val notes = json.optJSONArray("notes") ?: return "No notes found."
            if (notes.length() == 0) return "No notes saved yet."
            val sb = StringBuilder("Your Saved Notes (${notes.length()}):\n")
            for (i in 0 until notes.length()) {
                val n = notes.getJSONObject(i)
                sb.append("• ${n.optString("title")}: \"${n.optString("content")}\"\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing notes list: $jsonStr", e)
            jsonStr
        }
    }

    private fun formatClipboardResult(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            if (json.optBoolean("hasContent")) {
                "📋 Clipboard: \"${json.optString("text")}\""
            } else {
                "📋 Clipboard is currently empty."
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing clipboard result: $jsonStr", e)
            jsonStr
        }
    }
}
