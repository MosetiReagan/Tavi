package com.example.tavi.tools.memory

import android.content.Context
import com.example.tavi.data.TaviDatabase
import com.example.tavi.memory.ConsentStatus
import com.example.tavi.memory.MemoryProvenance
import com.example.tavi.memory.MemoryState
import com.example.tavi.memory.MemoryType
import com.example.tavi.memory.PersistentMemoryManager
import com.example.tavi.memory.PersistentMemoryRecord
import com.example.tavi.memory.SensitivityLevel
import com.example.tavi.memory.VerificationStatus
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemorySaveTool : AssistantTool {
    override val name: String = "memory_save"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
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
                state = ToolExecutionState.ERROR,
                errorMessage = "Both 'key' and 'value' are required to save a memory."
            )
        }

        // Screen sensitive credentials
        val sensitivityCheck = PersistentMemoryManager.checkSensitivity("$key $value")
        if (sensitivityCheck.isSensitive) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("blocked", true)
                    put("reason", sensitivityCheck.reason)
                }.toString(),
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = sensitivityCheck.reason ?: "Sensitive data cannot be saved to persistent memory."
            )
        }

        val memType = when (category.lowercase()) {
            "preference" -> MemoryType.PREFERENCE
            "personal" -> MemoryType.PROFILE_FACT
            "work" -> MemoryType.SAVED_FACT
            else -> MemoryType.SAVED_FACT
        }

        val record = PersistentMemoryRecord(
            id = UUID.randomUUID().toString(),
            memoryType = memType,
            key = key,
            value = value,
            category = category,
            provenance = MemoryProvenance.USER_EXPLICIT,
            confidence = 1.0f,
            verificationStatus = VerificationStatus.CONFIRMED,
            consentStatus = ConsentStatus.EXPLICITLY_GRANTED,
            sensitivity = SensitivityLevel.NORMAL,
            state = MemoryState.ACTIVE
        )

        val saveResult = PersistentMemoryManager.saveOrUpdateMemory(context, record)

        return when (saveResult) {
            is PersistentMemoryManager.SaveResult.Saved -> {
                // Also update legacy table for backward compatibility
                try {
                    val db = TaviDatabase.getInstance(context)
                    db.saveMemory(key, value, category)
                } catch (e: Exception) {
                    android.util.Log.w("MemoryTools", "Failed saving memory to legacy table", e)
                }

                val resultObj = JSONObject().apply {
                    put("verified", true)
                    put("id", saveResult.record.id)
                    put("key", key)
                    put("value", value)
                    put("category", category)
                    if (saveResult.supersededId != null) {
                        put("supersededMemoryId", saveResult.supersededId)
                        put("action", "UPDATED_SUPERSEDED_PRIOR_MEMORY")
                    } else {
                        put("action", "SAVED_NEW_MEMORY")
                    }
                    put("status", "SAVED_TO_PERSISTENT_MEMORY")
                }
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = resultObj.toString(),
                    isSuccess = true,
                    state = ToolExecutionState.SUCCESS
                )
            }
            is PersistentMemoryManager.SaveResult.Blocked -> {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply { put("blocked", true); put("reason", saveResult.reason) }.toString(),
                    isSuccess = false,
                    state = ToolExecutionState.ERROR,
                    errorMessage = saveResult.reason
                )
            }
            is PersistentMemoryManager.SaveResult.Error -> {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    state = ToolExecutionState.ERROR,
                    errorMessage = saveResult.message
                )
            }
        }
    }
}

class MemoryRetrieveTool : AssistantTool {
    override val name: String = "memory_retrieve"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
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
                state = ToolExecutionState.ERROR,
                errorMessage = "'keyOrQuery' is required."
            )
        }

        val db = TaviDatabase.getInstance(context)
        val activeMemories = db.searchPersistentMemories(query, maxResults = 5)

        val resultObj = JSONObject().apply {
            put("query", query)
            val resultsArray = JSONArray()
            for (entry in activeMemories) {
                val item = JSONObject().apply {
                    put("id", entry.id)
                    put("key", entry.key)
                    put("value", entry.value)
                    put("category", entry.category)
                    put("type", entry.memoryType.name)
                    put("provenance", entry.provenance.name)
                    put("updatedAt", entry.updatedAt)
                }
                resultsArray.put(item)
            }
            put("resultsCount", activeMemories.size)
            put("results", resultsArray)
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

class MemoryDeleteTool : AssistantTool {
    override val name: String = "memory_delete"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val description: String = "Deletes a specific remembered item from local memory by key or ID."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "keyOrId": {"type": "string", "description": "Key or ID of the memory entry to delete"}
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
                state = ToolExecutionState.ERROR,
                errorMessage = "'keyOrId' argument is required."
            )
        }

        val deletedPersistent = PersistentMemoryManager.deleteMemory(context, target)
        val db = TaviDatabase.getInstance(context)
        val deletedLegacy = db.deleteMemory(target)
        val anyDeleted = deletedPersistent || deletedLegacy

        val resultObj = JSONObject().apply {
            put("verified", true)
            put("target", target)
            put("deleted", anyDeleted)
            put("status", if (anyDeleted) "MEMORY_DELETED" else "KEY_NOT_FOUND")
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

class MemoryClearAllTool : AssistantTool {
    override val name: String = "memory_clear_all"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL
    override val description: String = "Permanently clears all personal memories and notes. This is a consequential action requiring user confirmation."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {},
          "required": []
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        PersistentMemoryManager.clearAllMemories(context)

        val resultObj = JSONObject().apply {
            put("verified", true)
            put("action", "CLEAR_ALL_MEMORIES")
            put("status", "ALL_MEMORIES_CLEARED")
            put("timestamp", System.currentTimeMillis())
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

class EpisodicRecordTool : AssistantTool {
    override val name: String = "memory_record_episode"
    override val category: ToolCategory = ToolCategory.NOTES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Records a completed task, project decision, or workflow rationale as an episodic memory for future recall."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Concise title for the event or decision"},
            "summary": {"type": "string", "description": "Summary of what occurred and what was done"},
            "decisionRationale": {"type": "string", "description": "Why a particular approach or decision was made"},
            "outcome": {"type": "string", "description": "The result or status of the task"},
            "taskStatus": {"type": "string", "description": "Task status (COMPLETED, IN_PROGRESS, ABANDONED)"}
          },
          "required": ["title", "summary"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val title = arguments["title"]?.toString()?.trim()
        val summary = arguments["summary"]?.toString()?.trim()
        val decisionRationale = arguments["decisionRationale"]?.toString()?.trim()
        val outcome = arguments["outcome"]?.toString()?.trim()
        val taskStatus = arguments["taskStatus"]?.toString()?.trim() ?: "COMPLETED"

        if (title.isNullOrEmpty() || summary.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Both 'title' and 'summary' are required."
            )
        }

        val record = PersistentMemoryManager.recordEpisodicMemory(
            context = context,
            title = title,
            summary = summary,
            decisionRationale = decisionRationale,
            outcome = outcome,
            taskStatus = taskStatus
        )

        val resultObj = JSONObject().apply {
            put("verified", true)
            put("id", record?.id)
            put("title", title)
            put("taskStatus", taskStatus)
            put("status", "EPISODIC_MEMORY_RECORDED")
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
