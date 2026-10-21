package com.example.tavi.tools.files

import android.content.Context
import android.content.Intent
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONObject

class FilesBrowseDocumentTool : AssistantTool {
    override val name: String = "files_browse_document"
    override val category: ToolCategory = ToolCategory.FILES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Launches the Android Storage Access Framework (SAF) document picker."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "mimeType": {"type": "string", "description": "MIME type filter (default '*/*')"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val mime = arguments["mimeType"]?.toString()?.trim()?.ifEmpty { "*/*" } ?: "*/*"
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "SAF_DOCUMENT_PICKER_LAUNCHED")
                    put("mimeType", mime)
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
                errorMessage = "Failed to launch Storage Access Framework: ${e.message}"
            )
        }
    }
}

class FilesExportNoteTool : AssistantTool {
    override val name: String = "files_export_note"
    override val category: ToolCategory = ToolCategory.FILES
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Exports a local note via the Android system share sheet."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "noteId": {"type": "string", "description": "The ID of the note to export"},
            "title": {"type": "string", "description": "Optional title if noteId not specified"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val noteId = arguments["noteId"]?.toString()?.trim()
        val titleQuery = arguments["title"]?.toString()?.trim()

        val db = TaviDatabase.getInstance(context)
        val allNotes = db.getAllNotes()
        val note = if (!noteId.isNullOrEmpty()) {
            allNotes.find { it["id"] == noteId }
        } else if (!titleQuery.isNullOrEmpty()) {
            allNotes.find { (it["title"] as? String)?.contains(titleQuery, ignoreCase = true) == true }
        } else {
            allNotes.firstOrNull()
        }

        if (note == null) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.NOT_FOUND,
                errorMessage = "Could not find note to export."
            )
        }

        val noteTitle = note["title"]?.toString() ?: "Note"
        val noteContent = note["content"]?.toString() ?: ""
        val exportText = "# $noteTitle\n\n$noteContent"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, noteTitle)
            putExtra(Intent.EXTRA_TEXT, exportText)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Export Note: $noteTitle").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(chooser)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "NOTE_EXPORT_SHARED")
                    put("title", noteTitle)
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
                errorMessage = "Failed to export note: ${e.message}"
            )
        }
    }
}
