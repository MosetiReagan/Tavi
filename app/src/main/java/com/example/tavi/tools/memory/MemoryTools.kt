package com.example.tavi.tools.memory

import android.content.Context
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.ToolResult
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject

class MemorySaveTool : AssistantTool {
    override val name: String = "memory_save"
    override val description: String = "Saves user-approved personal preferences, facts, or notes into persistent local memory."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "key": {"type": "string", "description": "Short identifier or topic (e.g., 'dietary_preference', 'work_address')"},
            "value": {"type": "string", "description": "The exact fact or preference to remember"},
            "category": {"type": "string", "description": "Optional category (e.g., 'personal', 'work', 'preference')"}
          },
          "required": ["key", "value"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val key = arguments["key"]?.toString()?.trim()
        val value = arguments["value"]?.toString()?.trim()
        val category = arguments["category"]?.toString()?.trim() ?: "general"

        if (key.isNullOrEmpty() || value.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Both 'key' and 'value' are required to save a memory."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val memoryId = db.saveMemory(key, value, category)

        val resultObj = JSONObject().apply {
            put("verified", true)
            put("id", memoryId)
            put("key", key)
            put("value", value)
            put("category", category)
            put("status", "SAVED_TO_LOCAL_MEMORY")
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true
        )
    }
}

class MemoryRetrieveTool : AssistantTool {
    override val name: String = "memory_retrieve"
    override val description: String = "Retrieves relevant saved personal facts, preferences, or notes from local memory."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "keyOrQuery": {"type": "string", "description": "Key or search query to look up in personal memory"}
          },
          "required": ["keyOrQuery"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val query = arguments["keyOrQuery"]?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "'keyOrQuery' is required."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val exact = db.getMemory(query)
        val searchResults = db.searchMemory(query)

        val array = JSONArray()
        for (item in searchResults) {
            array.put(JSONObject().apply {
                put("id", item.id)
                put("key", item.key)
                put("value", item.value)
                put("category", item.category)
            })
        }

        val resultObj = JSONObject().apply {
            put("query", query)
            put("exactMatch", exact ?: "")
            put("count", array.length())
            put("results", array)
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true
        )
    }
}

class MemoryDeleteTool : AssistantTool {
    override val name: String = "memory_delete"
    override val description: String = "Deletes a specific memory entry by key or ID."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "keyOrId": {"type": "string", "description": "The key or ID of the memory to remove"}
          },
          "required": ["keyOrId"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val target = arguments["keyOrId"]?.toString()?.trim()
        if (target.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "'keyOrId' is required."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val success = db.deleteMemory(target)

        val resultObj = JSONObject().apply {
            put("verified", success)
            put("target", target)
            put("status", if (success) "DELETED" else "NOT_FOUND")
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = success,
            errorMessage = if (!success) "Memory item '$target' was not found." else null
        )
    }
}
