package com.example.tavi.tools.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.tavi.model.ToolResult
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

class CalendarListEventsTool : AssistantTool {
    override val name: String = "calendar_list_events"
    override val description: String = "Lists or searches upcoming calendar events within an optional date range or query."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "startDate": {"type": "string", "description": "Natural date string for start of search window, e.g. 'today' or 'tomorrow'"},
            "endDate": {"type": "string", "description": "Natural date string for end of search window, e.g. 'in 7 days'"},
            "query": {"type": "string", "description": "Optional search term to filter event titles or descriptions"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.READ_CALENDAR)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Calendar permission (READ_CALENDAR) is not granted. Please enable it in Settings or grant the permission prompt."
            )
        }

        val startRaw = arguments["startDate"]?.toString() ?: "today"
        val endRaw = arguments["endDate"]?.toString() ?: "in 7 days"
        val query = arguments["query"]?.toString()?.trim()

        val startMillis = CalendarNaturalDateResolver.resolveToEpochMillis(startRaw) ?: System.currentTimeMillis()
        val endMillis = CalendarNaturalDateResolver.resolveToEpochMillis(endRaw) ?: (startMillis + (7 * 86400 * 1000L))

        val eventsArray = JSONArray()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION
        )

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        try {
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                val locIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val descIdx = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val title = if (titleIdx >= 0) c.getString(titleIdx) ?: "(No title)" else "(No title)"
                    val begin = if (beginIdx >= 0) c.getLong(beginIdx) else 0L
                    val end = if (endIdx >= 0) c.getLong(endIdx) else 0L
                    val loc = if (locIdx >= 0) c.getString(locIdx) ?: "" else ""
                    val desc = if (descIdx >= 0) c.getString(descIdx) ?: "" else ""

                    if (query.isNullOrEmpty() || title.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)) {
                        val item = JSONObject().apply {
                            put("eventId", id)
                            put("title", title)
                            put("startTime", CalendarNaturalDateResolver.formatDateTime(begin))
                            put("endTime", CalendarNaturalDateResolver.formatDateTime(end))
                            put("startMillis", begin)
                            put("endMillis", end)
                            put("location", loc)
                            put("description", desc)
                        }
                        eventsArray.put(item)
                    }
                }
            }

            val summaryObj = JSONObject().apply {
                put("eventsCount", eventsArray.length())
                put("events", eventsArray)
                put("searchWindowStart", CalendarNaturalDateResolver.formatDateTime(startMillis))
                put("searchWindowEnd", CalendarNaturalDateResolver.formatDateTime(endMillis))
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = summaryObj.toString(),
                isSuccess = true
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Failed to query calendar: ${e.message}"
            )
        }
    }
}

class CalendarCreateEventTool : AssistantTool {
    override val name: String = "calendar_create_event"
    override val description: String = "Creates a new calendar event with natural date/time resolution."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Title or summary of the event"},
            "startTime": {"type": "string", "description": "Natural date/time string, e.g. 'tomorrow at 3pm'"},
            "endTime": {"type": "string", "description": "Optional natural date/time string, e.g. 'tomorrow at 4pm'"},
            "location": {"type": "string", "description": "Optional location"},
            "description": {"type": "string", "description": "Optional description or notes"}
          },
          "required": ["title", "startTime"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Calendar permission (WRITE_CALENDAR) is not granted."
            )
        }

        val title = arguments["title"]?.toString()?.trim()
        val startRaw = arguments["startTime"]?.toString()?.trim()
        val endRaw = arguments["endTime"]?.toString()?.trim()
        val location = arguments["location"]?.toString()?.trim() ?: ""
        val description = arguments["description"]?.toString()?.trim() ?: ""

        if (title.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Cannot create event without a title. Please specify what the event is called."
            )
        }

        if (startRaw.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Cannot create event without a start time. Please provide when it begins (e.g. 'tomorrow at 2pm')."
            )
        }

        val startMillis = CalendarNaturalDateResolver.resolveToEpochMillis(startRaw)
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Could not resolve the date/time '$startRaw'. Please clarify with a format like 'tomorrow at 3pm' or 'YYYY-MM-DD HH:MM'."
            )

        val endMillis = if (!endRaw.isNullOrEmpty()) {
            CalendarNaturalDateResolver.resolveToEpochMillis(endRaw, startMillis) ?: (startMillis + 3600 * 1000L)
        } else {
            startMillis + 3600 * 1000L // Default 1 hour duration
        }

        // Get primary calendar ID
        val calId = getPrimaryCalendarId(context) ?: 1L

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }

        try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val newEventId = uri.lastPathSegment?.toLongOrNull() ?: -1L
                val resultObj = JSONObject().apply {
                    put("verified", true)
                    put("eventId", newEventId)
                    put("title", title)
                    put("startTime", CalendarNaturalDateResolver.formatDateTime(startMillis))
                    put("endTime", CalendarNaturalDateResolver.formatDateTime(endMillis))
                    put("location", location)
                    put("status", "CREATED_AND_VERIFIED")
                }
                return ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = resultObj.toString(),
                    isSuccess = true
                )
            } else {
                return ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    errorMessage = "Calendar provider returned null URI. Failed to persist event."
                )
            }
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Error inserting calendar event: ${e.message}"
            )
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                var fallbackId: Long? = null
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val priIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    if (fallbackId == null) fallbackId = id
                    if (priIdx >= 0 && cursor.getInt(priIdx) == 1) {
                        return id
                    }
                }
                fallbackId
            }
        } catch (_: Exception) {
            null
        }
    }
}

class CalendarDeleteEventTool : AssistantTool {
    override val name: String = "calendar_delete_event"
    override val description: String = "Deletes a calendar event by its event ID. This is a consequential action that requires user confirmation."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "eventId": {"type": "integer", "description": "The unique ID of the event to delete"},
            "title": {"type": "string", "description": "Optional title for confirmation prompt display"}
          },
          "required": ["eventId"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.WRITE_CALENDAR)
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Calendar permission (WRITE_CALENDAR) is not granted."
            )
        }

        val eventId = arguments["eventId"]?.toString()?.toLongOrNull()
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Invalid or missing 'eventId' argument."
            )

        // Check if user confirmed the action
        val isConfirmed = arguments["__user_confirmed"] == true
        if (!isConfirmed) {
            val confReq = SecurityGuard.createConfirmationRequest(name, arguments)
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                requiresUserConfirmation = true,
                confirmationPayload = confReq,
                errorMessage = "Confirmation required before deleting calendar event."
            )
        }

        val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        try {
            val rows = context.contentResolver.delete(deleteUri, null, null)
            if (rows > 0) {
                val resultObj = JSONObject().apply {
                    put("verified", true)
                    put("eventId", eventId)
                    put("deletedRows", rows)
                    put("status", "DELETED_AND_VERIFIED")
                }
                return ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = resultObj.toString(),
                    isSuccess = true
                )
            } else {
                return ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    errorMessage = "Event with ID $eventId was not found or could not be deleted."
                )
            }
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Failed to delete calendar event: ${e.message}"
            )
        }
    }
}
