package com.example.tavi.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.accessibility.AccessibilityPerformActionTool
import com.example.tavi.tools.accessibility.AccessibilityReadScreenTool
import com.example.tavi.tools.apps.AppLaunchTool
import com.example.tavi.tools.apps.AppListLaunchableTool
import com.example.tavi.tools.apps.AppOpenSettingsTool
import com.example.tavi.tools.apps.BrowserOpenUrlTool
import com.example.tavi.tools.apps.ShareContentTool
import com.example.tavi.tools.browser.BrowserOpenPageTool
import com.example.tavi.tools.browser.BrowserResearchTool
import com.example.tavi.tools.calendar.CalendarCreateEventTool
import com.example.tavi.tools.calendar.CalendarDeleteEventTool
import com.example.tavi.tools.calendar.CalendarGetEventTool
import com.example.tavi.tools.calendar.CalendarListEventsTool
import com.example.tavi.tools.calendar.CalendarUpdateEventTool
import com.example.tavi.tools.clock.AlarmCreateTool
import com.example.tavi.tools.clock.AlarmShowTool
import com.example.tavi.tools.clock.ClockQueryTimeTool
import com.example.tavi.tools.clock.TimerSetTool
import com.example.tavi.tools.connectivity.NetworkGetStatusTool
import com.example.tavi.tools.contacts.ContactsCreateTool
import com.example.tavi.tools.contacts.ContactsResolveTool
import com.example.tavi.tools.contacts.ContactsSearchTool
import com.example.tavi.tools.device.DeviceGetStatusTool
import com.example.tavi.tools.device.DeviceToggleFlashlightTool
import com.example.tavi.tools.device.SettingsOpenTool
import com.example.tavi.tools.everyday.CalculatorTool
import com.example.tavi.tools.everyday.ClipboardCopyTool
import com.example.tavi.tools.everyday.ClipboardReadTool
import com.example.tavi.tools.everyday.DateCalculationTool
import com.example.tavi.tools.everyday.NoteCreateTool
import com.example.tavi.tools.everyday.NoteDeleteTool
import com.example.tavi.tools.everyday.NotesListTool
import com.example.tavi.tools.everyday.ReminderCancelTool
import com.example.tavi.tools.everyday.ReminderCreateTool
import com.example.tavi.tools.everyday.ReminderListTool
import com.example.tavi.tools.files.FilesBrowseDocumentTool
import com.example.tavi.tools.files.FilesExportNoteTool
import com.example.tavi.tools.location.LocationGetCurrentTool
import com.example.tavi.tools.location.MapsNavigateTool
import com.example.tavi.tools.media.CameraLaunchTool
import com.example.tavi.tools.media.MediaControlTool
import com.example.tavi.tools.media.VoiceSpeakTool
import com.example.tavi.tools.usage.UsageStatsTool
import com.example.tavi.tools.memory.EpisodicRecordTool
import com.example.tavi.tools.memory.MemoryClearAllTool
import com.example.tavi.tools.memory.MemoryDeleteTool
import com.example.tavi.tools.memory.MemoryRetrieveTool
import com.example.tavi.tools.memory.MemorySaveTool
import com.example.tavi.tools.messages.CallLogSearchTool
import com.example.tavi.tools.messages.MessagesDraftTool
import com.example.tavi.tools.messages.MessagesSearchTool
import com.example.tavi.tools.messages.MessagesSendTool
import com.example.tavi.tools.messages.NotificationDismissTool
import com.example.tavi.tools.messages.NotificationSummaryTool
import com.example.tavi.tools.messages.PhoneCallTool
import com.example.tavi.tools.messages.PhoneDialTool
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

class ToolRegistry(private val context: Context) {

    private val tools = mutableMapOf<String, AssistantTool>()
    var screenAgentEngineProvider: (() -> com.example.tavi.engine.InferenceEngine)? = null

    init {
        // Calendar
        register(CalendarListEventsTool())
        register(CalendarGetEventTool())
        register(CalendarCreateEventTool())
        register(CalendarUpdateEventTool())
        register(CalendarDeleteEventTool())

        // Contacts
        register(ContactsSearchTool())
        register(ContactsResolveTool())
        register(ContactsCreateTool())

        // Telephony & SMS
        register(MessagesDraftTool())
        register(MessagesSendTool())
        register(MessagesSearchTool())
        register(PhoneDialTool())
        register(PhoneCallTool())
        register(CallLogSearchTool())

        // Notifications
        register(NotificationSummaryTool())
        register(NotificationDismissTool())

        // Clock & Time
        register(AlarmCreateTool())
        register(AlarmShowTool())
        register(TimerSetTool())
        register(ClockQueryTimeTool())
        register(ReminderCreateTool())
        register(ReminderListTool())
        register(ReminderCancelTool())

        // Applications & System Navigation
        register(AppListLaunchableTool())
        register(AppLaunchTool())
        register(AppOpenSettingsTool())
        register(BrowserOpenUrlTool())
        register(ShareContentTool())

        // Files & SAF
        register(FilesBrowseDocumentTool())
        register(FilesExportNoteTool())
        register(com.example.tavi.tools.files.DocumentSearchTool())

        // Device & Settings
        register(DeviceGetStatusTool())
        register(DeviceToggleFlashlightTool())
        register(SettingsOpenTool())

        // Location & Maps
        register(LocationGetCurrentTool())
        register(MapsNavigateTool())

        // Media & Voice
        register(CameraLaunchTool())
        register(MediaControlTool())
        register(VoiceSpeakTool())

        // Notes & Clipboard
        register(NoteCreateTool())
        register(NotesListTool())
        register(NoteDeleteTool())
        register(ClipboardReadTool())
        register(ClipboardCopyTool())

        // Personal Memory
        register(MemorySaveTool())
        register(MemoryRetrieveTool())
        register(MemoryDeleteTool())
        register(MemoryClearAllTool())
        register(EpisodicRecordTool())

        // Everyday & Math
        register(CalculatorTool())
        register(DateCalculationTool())

        // Connectivity
        register(NetworkGetStatusTool())

        // Level 2 System Access: Usage & Accessibility
        register(UsageStatsTool())
        register(AccessibilityReadScreenTool())
        register(AccessibilityPerformActionTool())
        register(com.example.tavi.tools.agent.ScreenAgentRunTool { screenAgentEngineProvider?.invoke() })
        register(com.example.tavi.tools.agent.ScreenAgentResumeTool { screenAgentEngineProvider?.invoke() })

        // Research
        register(BrowserResearchTool())
        register(BrowserOpenPageTool())
    }

    fun register(tool: AssistantTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AssistantTool? = tools[name]

    fun getAllTools(): List<AssistantTool> = tools.values.toList()

    fun getToolsByCategory(category: ToolCategory): List<AssistantTool> {
        return tools.values.filter { it.category == category }
    }

    /**
     * Executes a tool call with permission checks, consequential confirmation enforcement,
     * and tamper-evident local audit logging.
     */
    suspend fun executeToolCall(toolCall: ToolCall, userConfirmed: Boolean = false): ToolResult {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val tool = tools[toolCall.toolName]
            ?: return ToolResult(
                toolCallId = toolCall.id,
                toolName = toolCall.toolName,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.NOT_FOUND,
                errorMessage = "Unknown tool '${toolCall.toolName}'. Check available tools in registry."
            )

        ToolArgumentValidator.validate(tool.parametersSchema, toolCall.arguments)?.let { error ->
            return ToolResult(toolCall.id, tool.name, "{}", false, errorMessage = error)
        }

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
                    state = ToolExecutionState.PERMISSION_REQUIRED,
                    requiredPermission = perm,
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
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                errorMessage = "This is a consequential action (${confReq.title}) that requires your explicit confirmation before proceeding."
            )
        }

        // 3. Inject confirmation flag ONLY if trusted host confirmed, explicitly stripping any untrusted injected flags
        val effectiveArgs = toolCall.arguments.toMutableMap().apply {
            if (userConfirmed) {
                put("__user_confirmed", true)
            } else {
                remove("__user_confirmed")
            }
        }

        // 4. Execute tool
        val result = try {
            val action: suspend () -> ToolResult = { tool.execute(toolCall.id, effectiveArgs, context) }
            if (tool.isReadOnly) action() else
                ExecutionReceipts(TaviDatabase.getInstance(context).executionReceiptStore()).execute(toolCall, action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCall.id,
                toolName = tool.name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Tool execution failure: ${e.message}"
            )
        }

        // 5. Local Audit Logging (privacy-safe: logs tool name and result status, never private message bodies)
        val db = TaviDatabase.getInstance(context)
        db.insertAuditLog(
            SecurityGuard.createAuditLog(
                toolName = tool.name,
                status = if (result.isSuccess) "SUCCESS" else result.state.name,
                summary = if (result.isSuccess) "Tool executed successfully" else (result.errorMessage ?: "Unknown error"),
                verified = false
            )
        )

        return result
    }

    /**
     * Generates a concise, token-efficient system prompt describing registered tools.
     * Keeps token size compact to avoid native context-window overflow.
     */
    fun selectRelevantTools(query: String, history: List<com.example.tavi.model.Message> = emptyList()): List<AssistantTool> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.length > 2 }.toSet()
        val recent = history.takeLast(4).flatMap { it.toolCalls }.map { it.toolName }.toSet()
        val synonyms = mapOf(
            ToolCategory.CALENDAR to "calendar meeting appointment schedule agenda event",
            ToolCategory.TELEPHONY to "sms text message call dial phone recipient",
            ToolCategory.CONTACTS to "contact contacts person number recipient",
            ToolCategory.CLOCK to "alarm timer time reminder remind countdown",
            ToolCategory.NOTES to "note notes memory remember forget calculate calculation",
            ToolCategory.APPLICATIONS to "app apps open launch settings browser url share",
            ToolCategory.ACCESSIBILITY to "screen tap click scroll agent",
            ToolCategory.NOTIFICATIONS to "notification notifications unread",
            ToolCategory.LOCATION to "map maps navigate navigation location directions",
            ToolCategory.DEVICE to "battery device flashlight torch brightness",
            ToolCategory.FILES to "file files document documents pdf export import",
            ToolCategory.MEDIA to "music play pause camera photo voice speak",
            ToolCategory.RESEARCH to "research web website browse",
            ToolCategory.USAGE to "usage screentime",
            ToolCategory.CONNECTIVITY to "wifi network bluetooth internet")
        val ranked = tools.values.sortedByDescending { tool ->
            val text = (tool.name.replace('_', ' ') + " " + tool.description).lowercase()
            words.count { text.contains(it) } + words.count { synonyms[tool.category].orEmpty().split(' ').contains(it) } * 3 +
                if (tool.name in recent) 2 else 0
        }
        var tokens = 0
        return ranked.filter { tool ->
            val cost = com.example.tavi.context.ContextBudgetManager.estimateTokens(tool.parametersSchema + tool.description)
            if (tokens + cost <= 800) { tokens += cost; true } else false
        }.take(6)
    }

    fun generateToolsSystemPrompt(selectedTools: List<AssistantTool> = tools.values.toList()): String {
        val toolList = StringBuilder()
        for (t in selectedTools) {
            val paramsList = try {
                val schema = JSONObject(t.parametersSchema)
                val props = schema.optJSONObject("properties")
                if (props != null) {
                    val keys = mutableListOf<String>()
                    val it = props.keys()
                    while (it.hasNext()) {
                        keys.add(it.next())
                    }
                    keys.joinToString(", ")
                } else ""
            } catch (_: Exception) {
                ""
            }
            toolList.append("- ${t.name}($paramsList): ${t.description.trim()}\n")
        }
        return """
            You are Tavi, an honest on-device personal assistant running locally on Android.
            Available Tools:
            $toolList
            INSTRUCTIONS:
            - If a tool is needed, respond ONLY with a ```tool_call block:
            ```tool_call
            {"tool": "<tool_name>", "arguments": {<arguments>}}
            ```
            - If no tool is needed, respond directly with normal text.
        """.trimIndent()
    }

    companion object {
        @Volatile
        private var instance: ToolRegistry? = null

        fun getInstance(context: Context): ToolRegistry {
            return instance ?: synchronized(this) {
                val appContext = try { context.applicationContext ?: context } catch (_: Throwable) { context }
                instance ?: ToolRegistry(appContext).also { instance = it }
            }
        }
    }
}
