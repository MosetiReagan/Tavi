package com.example.tavi.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.browser.BrowserOpenPageTool
import com.example.tavi.tools.browser.BrowserResearchTool
import com.example.tavi.tools.calendar.CalendarCreateEventTool
import com.example.tavi.tools.calendar.CalendarDeleteEventTool
import com.example.tavi.tools.calendar.CalendarListEventsTool
import com.example.tavi.tools.everyday.CalculatorTool
import com.example.tavi.tools.everyday.DateCalculationTool
import com.example.tavi.tools.everyday.NoteCreateTool
import com.example.tavi.tools.everyday.ReminderCreateTool
import com.example.tavi.tools.memory.MemoryDeleteTool
import com.example.tavi.tools.memory.MemoryRetrieveTool
import com.example.tavi.tools.memory.MemorySaveTool
import com.example.tavi.tools.messages.MessagesDraftTool
import com.example.tavi.tools.messages.MessagesSearchTool
import com.example.tavi.tools.messages.MessagesSendTool
import com.example.tavi.tools.messages.NotificationSummaryTool
import org.json.JSONArray
import org.json.JSONObject

class ToolRegistry(private val context: Context) {

    private val tools = mutableMapOf<String, AssistantTool>()

    init {
        register(CalendarListEventsTool())
        register(CalendarCreateEventTool())
        register(CalendarDeleteEventTool())
        register(MessagesDraftTool())
        register(MessagesSendTool())
        register(MessagesSearchTool())
        register(NotificationSummaryTool())
        register(BrowserResearchTool())
        register(BrowserOpenPageTool())
        register(MemorySaveTool())
        register(MemoryRetrieveTool())
        register(MemoryDeleteTool())
        register(CalculatorTool())
        register(DateCalculationTool())
        register(ReminderCreateTool())
        register(NoteCreateTool())
    }

    fun register(tool: AssistantTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AssistantTool? = tools[name]

    fun getAllTools(): List<AssistantTool> = tools.values.toList()

    /**
     * Executes a tool call with permission checks, consequential confirmation enforcement,
     * and tamper-evident local audit logging.
     */
    suspend fun executeToolCall(toolCall: ToolCall, userConfirmed: Boolean = false): ToolResult {
        val tool = tools[toolCall.toolName]
            ?: return ToolResult(
                toolCallId = toolCall.id,
                toolName = toolCall.toolName,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Unknown tool '${toolCall.toolName}'. Check available tools in registry."
            )

        // 1. Permission checks
        for (perm in tool.requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                val db = TaviDatabase.getInstance(context)
                db.insertAuditLog(
                    SecurityGuard.createAuditLog(
                        toolName = tool.name,
                        status = "BLOCKED_PERMISSION_DENIED",
                        summary = "Denied: missing permission $perm",
                        verified = false
                    )
                )
                return ToolResult(
                    toolCallId = toolCall.id,
                    toolName = tool.name,
                    resultJson = "{}",
                    isSuccess = false,
                    errorMessage = "Action blocked: Android permission '$perm' is required but not granted."
                )
            }
        }

        // 2. Consequential action confirmation check
        if (tool.requiresConfirmation && !userConfirmed) {
            val confReq = SecurityGuard.createConfirmationRequest(tool.name, toolCall.arguments)
            val db = TaviDatabase.getInstance(context)
            db.insertAuditLog(
                SecurityGuard.createAuditLog(
                    toolName = tool.name,
                    status = "AWAITING_USER_CONFIRMATION",
                    summary = "Paused action: ${confReq.title}",
                    verified = false
                )
            )
            return ToolResult(
                toolCallId = toolCall.id,
                toolName = tool.name,
                resultJson = "{}",
                isSuccess = false,
                requiresUserConfirmation = true,
                confirmationPayload = confReq,
                errorMessage = "This is a consequential action (${confReq.title}) that requires your explicit confirmation before proceeding."
            )
        }

        // 3. Inject confirmation flag if confirmed
        val effectiveArgs = if (userConfirmed) {
            toolCall.arguments.toMutableMap().apply { put("__user_confirmed", true) }
        } else {
            toolCall.arguments
        }

        // 4. Execute tool
        val result = try {
            tool.execute(toolCall.id, effectiveArgs, context)
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCall.id,
                toolName = tool.name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Tool execution failure: ${e.message}"
            )
        }

        // 5. Local Audit Logging
        val db = TaviDatabase.getInstance(context)
        db.insertAuditLog(
            SecurityGuard.createAuditLog(
                toolName = tool.name,
                status = if (result.isSuccess) "SUCCESS" else "FAILED",
                summary = if (result.isSuccess) "Tool executed successfully" else (result.errorMessage ?: "Unknown error"),
                verified = result.isSuccess
            )
        )

        return result
    }

    /**
     * Generates a concise system prompt describing all registered tools and their schemas.
     */
    fun generateToolsSystemPrompt(): String {
        val toolsArray = JSONArray()
        for (t in tools.values) {
            val obj = JSONObject().apply {
                put("name", t.name)
                put("description", t.description)
                put("parameters", JSONObject(t.parametersSchema))
                put("requiresConfirmation", t.requiresConfirmation)
            }
            toolsArray.put(obj)
        }
        return """
            You are Tavi, a helpful and honest personal AI assistant running locally on the user's phone.
            When the user requests an action, you must use available tools to fulfill it.
            To invoke a tool, respond with a JSON block in this exact format:
            ```tool_call
            {"tool": "<tool_name>", "arguments": {<arguments_json>}}
            ```
            
            Available Tools:
            $toolsArray
        """.trimIndent()
    }
}
