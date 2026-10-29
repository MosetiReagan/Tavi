package com.example.tavi.tools.messages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.contacts.ContactResolutionResult
import com.example.tavi.tools.contacts.ContactResolver
import org.json.JSONArray
import org.json.JSONObject

class MessagesDraftTool : AssistantTool {
    override val name: String = "messages_draft"
    override val category: ToolCategory = ToolCategory.TELEPHONY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
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
        val rawRecipient = arguments["recipient"]?.toString()?.trim() ?: "Unknown"
        val body = arguments["body"]?.toString()?.trim() ?: ""

        val (resolvedNumber, resolvedName) = ContactResolver.resolvePhoneNumber(context, rawRecipient)
        val displayRecipient = if (resolvedName != null) "$resolvedName ($resolvedNumber)" else rawRecipient

        val resultObj = JSONObject().apply {
            put("status", "DRAFT_CREATED")
            put("recipient", displayRecipient)
            put("phoneNumber", resolvedNumber)
            put("body", body)
            put("instructions", "Draft created for review. User confirmation is required before sending.")
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}

class MessagesSendTool : AssistantTool {
    override val name: String = "messages_send_sms"
    override val category: ToolCategory = ToolCategory.TELEPHONY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL
    override val description: String = "Sends an SMS message to a phone number. Requires explicit user confirmation before transmission."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "recipient": {"type": "string", "description": "Phone number or contact name of recipient"},
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
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.SEND_SMS,
                errorMessage = "SMS permission (SEND_SMS) is not granted."
            )
        }

        val rawRecipient = arguments["recipient"]?.toString()?.trim()
        val body = arguments["body"]?.toString()?.trim()

        if (rawRecipient.isNullOrEmpty() || body.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Both 'recipient' and 'body' are required to send an SMS."
            )
        }

        // Strict contact resolution with ambiguity check
        val resolution = ContactResolver.resolveContactStrict(context, rawRecipient)
        val (resolvedNumber, resolvedName) = when (resolution) {
            is ContactResolutionResult.Single -> Pair(resolution.contact.phoneNumber, resolution.contact.displayName)
            is ContactResolutionResult.Ambiguous -> {
                val candidateStrings = resolution.candidates.map { "${it.displayName} (${it.phoneNumber})" }
                return ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply {
                        put("query", rawRecipient)
                        put("candidates", JSONArray(candidateStrings))
                    }.toString(),
                    isSuccess = false,
                    state = ToolExecutionState.AMBIGUOUS,
                    ambiguousOptions = candidateStrings,
                    errorMessage = "Found multiple contacts matching '$rawRecipient'. Please specify which one: ${candidateStrings.joinToString(", ")}."
                )
            }
            is ContactResolutionResult.NotFound -> {
                // If not found as name, check if it's already a valid numeric phone number
                val cleanDigits = rawRecipient.replace(Regex("[^0-9]"), "")
                if (cleanDigits.length >= 4) {
                    Pair(rawRecipient, null)
                } else {
                    return ToolResult(
                        toolCallId = toolCallId,
                        toolName = name,
                        resultJson = "{}",
                        isSuccess = false,
                        state = ToolExecutionState.NOT_FOUND,
                        errorMessage = "Contact '$rawRecipient' was not found in your phone contacts. Please provide their phone number."
                    )
                }
            }
            is ContactResolutionResult.PermissionDenied -> {
                val cleanDigits = rawRecipient.replace(Regex("[^0-9]"), "")
                if (cleanDigits.length >= 4) {
                    Pair(rawRecipient, null)
                } else {
                    return ToolResult(
                        toolCallId = toolCallId,
                        toolName = name,
                        resultJson = "{}",
                        isSuccess = false,
                        state = ToolExecutionState.PERMISSION_REQUIRED,
                        requiredPermission = Manifest.permission.READ_CONTACTS,
                        errorMessage = "Cannot resolve contact '$rawRecipient' because Contacts permission is not granted."
                    )
                }
            }
        }

        val isConfirmed = arguments["__user_confirmed"] == true
        if (!isConfirmed) {
            val displayRecipient = if (resolvedName != null) "$resolvedName ($resolvedNumber)" else resolvedNumber
            val confirmationArgs = arguments.toMutableMap().apply {
                put("recipient", displayRecipient)
                put("phoneNumber", resolvedNumber)
            }
            val confReq = SecurityGuard.createConfirmationRequest(name, confirmationArgs)
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                requiresUserConfirmation = true,
                confirmationPayload = confReq,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
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
            smsManager.sendMultipartTextMessage(resolvedNumber, null, parts, null, null)

            // Persist into telephony provider so it appears in device's Messages app thread
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.Telephony.Sms.ADDRESS, resolvedNumber)
                    put(android.provider.Telephony.Sms.BODY, body)
                    put(android.provider.Telephony.Sms.DATE, System.currentTimeMillis())
                    put(android.provider.Telephony.Sms.READ, 1)
                    put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_SENT)
                }
                context.contentResolver.insert(android.provider.Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (e: Exception) {
                android.util.Log.w("MessagesTools", "Failed logging sent SMS to system telephony provider", e)
            }

            val resultObj = JSONObject().apply {
                put("verified", true)
                put("carrierDeliveryPending", true)
                put("recipient", resolvedName ?: resolvedNumber)
                put("phoneNumber", resolvedNumber)
                put("partsCount", parts.size)
                put("status", "DISPATCHED_TO_CARRIER")
                put("summary", "Message handed off to cellular radio for transmission.")
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to dispatch SMS: ${e.message}"
            )
        }
    }
}

class MessagesSearchTool : AssistantTool {
    override val name: String = "messages_search"
    override val category: ToolCategory = ToolCategory.TELEPHONY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
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
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.READ_SMS,
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
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to query SMS inbox: ${e.message}"
            )
        }
    }
}

class PhoneDialTool : AssistantTool {
    override val name: String = "phone_dial"
    override val category: ToolCategory = ToolCategory.TELEPHONY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens the device dialer with the requested number prefilled. Does not place the call automatically."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "recipient": {"type": "string", "description": "Phone number or contact name to dial"}
          },
          "required": ["recipient"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val rawRecipient = arguments["recipient"]?.toString()?.trim() ?: ""
        if (rawRecipient.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Recipient number or name is required."
            )
        }

        val (resolvedNumber, resolvedName) = ContactResolver.resolvePhoneNumber(context, rawRecipient)
        val cleanNumber = resolvedNumber.replace(Regex("[^0-9\\+]"), "")

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(dialIntent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "DIALER_OPENED")
                    put("number", cleanNumber)
                    put("contactName", resolvedName ?: "")
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to open phone dialer: ${e.message}"
            )
        }
    }
}

class PhoneCallTool : AssistantTool {
    override val name: String = "phone_call"
    override val category: ToolCategory = ToolCategory.TELEPHONY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL
    override val description: String = "Initiates a direct phone call to a contact or number. Consequential action requiring explicit user confirmation."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "recipient": {"type": "string", "description": "Contact name or phone number to call"}
          },
          "required": ["recipient"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.CALL_PHONE)
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.CALL_PHONE,
                errorMessage = "Phone call permission (CALL_PHONE) is not granted."
            )
        }

        val rawRecipient = arguments["recipient"]?.toString()?.trim() ?: ""
        if (rawRecipient.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Recipient number or name is required."
            )
        }

        val (resolvedNumber, resolvedName) = ContactResolver.resolvePhoneNumber(context, rawRecipient)
        val cleanNumber = resolvedNumber.replace(Regex("[^0-9\\+]"), "")

        val isConfirmed = arguments["__user_confirmed"] == true
        if (!isConfirmed) {
            val display = if (resolvedName != null) "$resolvedName ($cleanNumber)" else cleanNumber
            val confReq = SecurityGuard.createConfirmationRequest(name, mapOf("recipient" to display, "phoneNumber" to cleanNumber))
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                requiresUserConfirmation = true,
                confirmationPayload = confReq,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                errorMessage = "Explicit confirmation required before placing phone call."
            )
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "CALL_INITIATED")
                    put("number", cleanNumber)
                    put("contactName", resolvedName ?: "")
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to place call: ${e.message}"
            )
        }
    }
}

class CallLogSearchTool : AssistantTool {
    override val name: String = "call_log_search"
    override val category: ToolCategory = ToolCategory.TELEPHONY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Searches recent call history records."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "limit": {"type": "integer", "description": "Max number of calls to retrieve (default 5, max 20)"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf("android.permission.READ_CALL_LOG")
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, "android.permission.READ_CALL_LOG") != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = "android.permission.READ_CALL_LOG",
                errorMessage = "Call log permission (READ_CALL_LOG) is not granted."
            )
        }

        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 20)
        val callsArray = JSONArray()

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )

            cursor?.use { c ->
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = c.getColumnIndex(CallLog.Calls.DURATION)

                while (c.moveToNext()) {
                    val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                    val cachedName = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                    val type = if (typeIdx >= 0) c.getInt(typeIdx) else 0
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val dur = if (durIdx >= 0) c.getLong(durIdx) else 0L

                    val typeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "OTHER"
                    }

                    callsArray.put(JSONObject().apply {
                        put("number", num)
                        put("name", cachedName)
                        put("type", typeStr)
                        put("dateMillis", date)
                        put("durationSeconds", dur)
                    })
                }
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("count", callsArray.length())
                    put("calls", callsArray)
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to query call log: ${e.message}"
            )
        }
    }
}
