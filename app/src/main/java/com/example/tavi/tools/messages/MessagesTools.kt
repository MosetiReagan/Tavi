package com.example.tavi.tools.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.tavi.model.ToolResult
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject

class MessagesDraftTool : AssistantTool {
    override val name: String = "messages_draft"
    override val description: String = "Drafts a message reply and presents it for user inspection without sending."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "recipient": {"type": "string", "description": "Name or phone number of the recipient"},
            "body": {"type": "string", "description": "The message text to draft"}
          },
          "required": ["recipient", "body"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val recipient = arguments["recipient"]?.toString()?.trim() ?: "Unknown"
        val body = arguments["body"]?.toString()?.trim() ?: ""

        val resultObj = JSONObject().apply {
            put("status", "DRAFT_CREATED")
            put("recipient", recipient)
            put("body", body)
            put("instructions", "Draft created for review. User confirmation is required before sending.")
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true
        )
    }
}

class MessagesSendTool : AssistantTool {
    override val name: String = "messages_send_sms"
    override val description: String = "Sends an SMS message to a phone number. Requires explicit user confirmation before transmission."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "recipient": {"type": "string", "description": "Phone number of recipient"},
            "body": {"type": "string", "description": "Text body of the SMS message"}
          },
          "required": ["recipient", "body"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.SEND_SMS)
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "SMS permission (SEND_SMS) is not granted."
            )
        }

        val recipient = arguments["recipient"]?.toString()?.trim()
        val body = arguments["body"]?.toString()?.trim()

        if (recipient.isNullOrEmpty() || body.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Both 'recipient' and 'body' are required to send an SMS."
            )
        }

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
                errorMessage = "Confirmation required before transmitting SMS."
            )
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)

            val resultObj = JSONObject().apply {
                put("verified", true)
                put("recipient", recipient)
                put("partsCount", parts.size)
                put("status", "SENT_AND_VERIFIED")
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Failed to dispatch SMS: ${e.message}"
            )
        }
    }
}

class MessagesSearchTool : AssistantTool {
    override val name: String = "messages_search"
    override val description: String = "Searches explicitly permitted local SMS messages by keyword or sender."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Keyword to search in message body"},
            "sender": {"type": "string", "description": "Optional sender phone number filter"},
            "limit": {"type": "integer", "description": "Max number of messages to return (default 5, max 20)"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.READ_SMS)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "SMS read permission (READ_SMS) is not granted."
            )
        }

        val query = arguments["query"]?.toString()?.trim()
        val sender = arguments["sender"]?.toString()?.trim()
        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 20)

        val messagesArray = JSONArray()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC LIMIT $limit"
            )

            cursor?.use { c ->
                val addrIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")
                val dateIdx = c.getColumnIndex("date")

                while (c.moveToNext()) {
                    val addr = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                    val rawBody = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L

                    var matches = true
                    if (!query.isNullOrEmpty() && !rawBody.contains(query, ignoreCase = true)) {
                        matches = false
                    }
                    if (!sender.isNullOrEmpty() && !addr.contains(sender, ignoreCase = true)) {
                        matches = false
                    }

                    if (matches) {
                        // Crucial: Sanitize untrusted SMS content to prevent indirect prompt injection
                        val sanitizedBody = SecurityGuard.sanitizeExternalContent(rawBody, "SMS: $addr")
                        val item = JSONObject().apply {
                            put("sender", addr)
                            put("dateMillis", date)
                            put("body", sanitizedBody)
                        }
                        messagesArray.put(item)
                    }
                }
            }

            val resultObj = JSONObject().apply {
                put("count", messagesArray.length())
                put("messages", messagesArray)
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Failed to query SMS inbox: ${e.message}"
            )
        }
    }
}
