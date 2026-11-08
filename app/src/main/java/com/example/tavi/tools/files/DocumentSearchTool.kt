package com.example.tavi.tools.files

import android.content.Context
import com.example.tavi.documents.DocumentLibrary
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolResult
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject

class DocumentSearchTool : AssistantTool {
    override val name = "documents_search"
    override val description = "Search imported local documents and saved notes; returns source and page citations."
    override val category = ToolCategory.FILES
    override val parametersSchema = """{"type":"object","properties":{"query":{"type":"string","minLength":2}},"required":["query"]}"""
    override val requiredPermissions = emptyList<String>()
    override val requiresConfirmation = false
    override val isReadOnly = true
    override suspend fun execute(toolCallId: String, arguments: Map<String, Any?>, context: Context): ToolResult {
        val result = DocumentLibrary.getInstance(context).searchHybrid(arguments["query"].toString())
        val hits = result.hits
        return ToolResult(toolCallId, name, JSONObject().put("searchMode", if (result.semantic) "semantic+keyword" else "keyword")
            .put("status", result.message ?: result.coverage?.let { "${it.indexed}/${it.total} excerpts indexed" }.orEmpty()).put("matches", JSONArray().apply {
            hits.forEach { put(JSONObject().put("sourceId", it.sourceId).put("source", it.reference).put("excerpt", it.excerpt)) }
        }).toString())
    }
}
