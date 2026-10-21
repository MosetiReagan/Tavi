package com.example.tavi.tools.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject

class ContactsSearchTool : AssistantTool {
    override val name: String = "contacts_search"
    override val category: ToolCategory = ToolCategory.CONTACTS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Searches device contacts by name or phone number to retrieve contact names and phone numbers."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "name": {"type": "string", "description": "Name or phone number query of contact to search"}
          },
          "required": ["name"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.READ_CONTACTS)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.READ_CONTACTS,
                errorMessage = "Contacts permission (READ_CONTACTS) is not granted. Please enable Contacts Access in Settings."
            )
        }

        val queryName = arguments["name"]?.toString()?.trim()
            ?: arguments["query"]?.toString()?.trim()
            ?: ""
        if (queryName.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Missing contact 'name' to search."
            )
        }

        val contacts = ContactResolver.searchContacts(context, queryName, limit = 10)
        val contactsArray = JSONArray()
        for (c in contacts) {
            val obj = JSONObject().apply {
                put("name", c.displayName)
                put("phoneNumber", c.phoneNumber)
                put("type", c.type)
            }
            contactsArray.put(obj)
        }

        val resultObj = JSONObject().apply {
            put("query", queryName)
            put("count", contacts.size)
            put("contacts", contactsArray)
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

class ContactsResolveTool : AssistantTool {
    override val name: String = "contacts_resolve"
    override val category: ToolCategory = ToolCategory.CONTACTS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Resolves a contact query. If multiple ambiguous contacts match, exposes the ambiguity instead of guessing."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "name": {"type": "string", "description": "Name or query to resolve"}
          },
          "required": ["name"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.READ_CONTACTS)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val query = arguments["name"]?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Contact name parameter is missing."
            )
        }

        return when (val res = ContactResolver.resolveContactStrict(context, query)) {
            is ContactResolutionResult.PermissionDenied -> {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    state = ToolExecutionState.PERMISSION_REQUIRED,
                    requiredPermission = res.permission,
                    errorMessage = "READ_CONTACTS permission is required."
                )
            }
            is ContactResolutionResult.NotFound -> {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply { put("query", query); put("found", false) }.toString(),
                    isSuccess = false,
                    state = ToolExecutionState.NOT_FOUND,
                    errorMessage = "No contact found matching '$query'."
                )
            }
            is ContactResolutionResult.Single -> {
                val obj = JSONObject().apply {
                    put("query", query)
                    put("status", "RESOLVED_SINGLE")
                    put("name", res.contact.displayName)
                    put("phoneNumber", res.contact.phoneNumber)
                    put("type", res.contact.type)
                }
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = obj.toString(),
                    isSuccess = true,
                    state = ToolExecutionState.SUCCESS
                )
            }
            is ContactResolutionResult.Ambiguous -> {
                val candidateList = res.candidates.map { "${it.displayName} (${it.phoneNumber})" }
                val candArray = JSONArray()
                for (c in res.candidates) {
                    candArray.put(JSONObject().apply {
                        put("name", c.displayName)
                        put("phoneNumber", c.phoneNumber)
                        put("type", c.type)
                    })
                }
                val obj = JSONObject().apply {
                    put("query", query)
                    put("status", "AMBIGUOUS_MULTIPLE_MATCHES")
                    put("count", res.candidates.size)
                    put("candidates", candArray)
                }
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = obj.toString(),
                    isSuccess = false,
                    state = ToolExecutionState.AMBIGUOUS,
                    ambiguousOptions = candidateList,
                    errorMessage = "Found multiple contacts matching '$query'. Please specify which one you mean: ${candidateList.joinToString(", ")}."
                )
            }
        }
    }
}

class ContactsCreateTool : AssistantTool {
    override val name: String = "contacts_create"
    override val category: ToolCategory = ToolCategory.CONTACTS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val description: String = "Creates a new contact with display name, phone number, and optional email."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "name": {"type": "string", "description": "Full name of the contact"},
            "phoneNumber": {"type": "string", "description": "Phone number"},
            "email": {"type": "string", "description": "Optional email address"}
          },
          "required": ["name", "phoneNumber"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.WRITE_CONTACTS)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.WRITE_CONTACTS,
                errorMessage = "Contacts write permission (WRITE_CONTACTS) is not granted."
            )
        }

        val nameStr = arguments["name"]?.toString()?.trim() ?: ""
        val phoneStr = arguments["phoneNumber"]?.toString()?.trim() ?: ""
        val emailStr = arguments["email"]?.toString()?.trim()

        if (nameStr.isEmpty() || phoneStr.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Both contact 'name' and 'phoneNumber' are required."
            )
        }

        val success = ContactResolver.createContact(context, nameStr, phoneStr, emailStr)
        return if (success) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("verified", true)
                    put("name", nameStr)
                    put("phoneNumber", phoneStr)
                    put("email", emailStr ?: "")
                    put("status", "CONTACT_CREATED")
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } else {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to create contact in device contacts provider."
            )
        }
    }
}
