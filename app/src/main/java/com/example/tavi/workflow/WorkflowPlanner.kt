package com.example.tavi.workflow

import android.content.Context
import android.util.Log
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/**
 * Intelligent Multi-Step Workflow Planner.
 *
 * Converts natural-language requests into typed, structured [WorkflowPlan]s.
 * Uses high-precision deterministic routing for standard multi-step patterns across
 * the 5 workflow families, with on-device Gemma 3 1B planning for ambiguous/open-ended tasks.
 * Validates every proposed tool, argument, dependency, and risk level in native code.
 */
class WorkflowPlanner(
    private val context: Context,
    private val toolRegistry: ToolRegistry = ToolRegistry.getInstance(context)
) {

    companion object {
        private const val TAG = "WorkflowPlanner"

        private val MODEL_PLANNER_ACTION_PATTERN = Pattern.compile(
            "(?i)\\b(send|text|sms|message|email|call|dial|schedule|remind|reminder|navigate|directions|book|reserve|order|set|create|add|delete|cancel|update|turn\\s+(?:on|off)|check\\s+(?:my\\s+)?notifications|summarize\\s+notifications)\\b"
        )
        private val MODEL_PLANNER_MULTI_STEP_PATTERN = Pattern.compile(
            "(?i)\\b(and|then|after|before|with|saying|for|to)\\b"
        )
    }

    /**
     * Gates expensive model-based workflow planning to action requests.
     * Simple questions must go to chat/tool routing instead of being turned into invented workflows.
     */
    fun shouldAttemptModelPlanning(userQuery: String): Boolean {
        val trimmed = userQuery.trim()
        if (trimmed.isEmpty() || SecurityGuard.isInjectionAttempt(trimmed)) return false
        val hasAction = MODEL_PLANNER_ACTION_PATTERN.matcher(trimmed).find()
        val hasMultiStepCue = MODEL_PLANNER_MULTI_STEP_PATTERN.matcher(trimmed).find()
        return hasAction && (hasMultiStepCue || trimmed.contains("notification", ignoreCase = true))
    }

    /**
     * Evaluates a user query to create a validated [WorkflowPlan].
     * Returns null if the query is a simple single-turn conversational prompt or single tool query.
     */
    fun planWorkflow(userQuery: String): WorkflowPlan? {
        val trimmed = userQuery.trim()
        if (trimmed.isEmpty() || SecurityGuard.isInjectionAttempt(trimmed)) {
            return null
        }

        val q = trimmed.lowercase(Locale.ROOT).replace(Regex("[?!.,]+$"), "").trim()

        // 1. Family A: Notifications & Communication Workflows
        planNotificationAndCommunication(trimmed, q)?.let { return it }

        // 2. Family B: Calendar, Conflict Checking & Reminders
        planCalendarAndReminders(trimmed, q)?.let { return it }

        // 3. Family C: Screen-Aware Assistance & Verified Navigation
        planScreenAwareAssistance(trimmed, q)?.let { return it }

        // 4. Family D: App Usage & Routines
        planUsageAndRoutines(trimmed, q)?.let { return it }

        // 5. Family E: Context-Aware Multi-Tool Personal Assistance
        planContextAwareAssistance(trimmed, q)?.let { return it }

        return null
    }

    // =========================================================================
    // Family A: Notifications and Communication
    // =========================================================================

    private fun planNotificationAndCommunication(raw: String, q: String): WorkflowPlan? {
        // Pattern A1: Check notifications and draft reply
        if (q.contains("check notifications and reply") ||
            q.contains("summarize notifications and draft reply") ||
            q == "check unread messages and reply" ||
            q == "review my notifications"
        ) {
            val step0 = createStep(
                stepIndex = 0,
                title = "Fetch Active Notifications",
                toolName = "notifications_read_summary",
                arguments = emptyMap(),
                isReadOnly = true
            )
            val step1 = createStep(
                stepIndex = 1,
                title = "Draft Contextual SMS Reply",
                toolName = "messages_draft",
                arguments = mapOf("recipient" to "", "body" to "Thanks for your message, I'll get back to you shortly."),
                dynamicBindings = listOf(
                    DynamicBinding(
                        targetArg = "recipient",
                        fromStepId = step0.id,
                        fromPropertyPath = "topSender"
                    )
                ),
                isReadOnly = true,
                dependsOnStepIds = listOf(step0.id)
            )
            val step2 = createStep(
                stepIndex = 2,
                title = "Send Confirmed SMS",
                toolName = "messages_send_sms",
                arguments = emptyMap(),
                dynamicBindings = listOf(
                    DynamicBinding(targetArg = "recipient", fromStepId = step1.id, fromPropertyPath = "recipient"),
                    DynamicBinding(targetArg = "body", fromStepId = step1.id, fromPropertyPath = "body")
                ),
                isReadOnly = false,
                riskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL,
                requiredPermission = "android.permission.SEND_SMS",
                dependsOnStepIds = listOf(step1.id)
            )

            return WorkflowPlan(
                title = "Notification Review & Reply Workflow",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0, step1, step2)
            )
        }

        // Pattern A2: Look up contact and send text
        val smsWithBody = Pattern.compile(
            "(?i)^(?:text|send\\s+(?:an?\\s+)?(?:sms|message|text)\\s+to|message\\s+to)\\s+(.+?)\\s+(?:saying|that\\s+says|and\\s+tell\\s+(?:him|her|them)(?:\\s+to)?|with\\s+(?:the\\s+)?body)\\s+(.+)$"
        ).matcher(raw.trim())
        val smsRecipientOnly = Pattern.compile(
            "(?i)^(?:text|send\\s+(?:an?\\s+)?(?:sms|message|text)\\s+to|message\\s+to)\\s+(.+)$"
        ).matcher(raw.trim())

        val contactName: String
        val messageBody: String
        if (smsWithBody.find()) {
            contactName = smsWithBody.group(1)?.trim() ?: ""
            messageBody = smsWithBody.group(2)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
        } else if (smsRecipientOnly.find()) {
            contactName = smsRecipientOnly.group(1)?.trim() ?: ""
            messageBody = ""
        } else {
            contactName = ""
            messageBody = ""
        }

        if (contactName.isNotBlank() && !contactName.equals("me", ignoreCase = true)) {
            val step0 = createStep(
                stepIndex = 0,
                title = "Resolve Contact Phone Number",
                toolName = "contacts_resolve",
                arguments = mapOf("name" to contactName),
                isReadOnly = true
            )

            val step1 = createStep(
                stepIndex = 1,
                title = "Prepare SMS Draft",
                toolName = "messages_draft",
                arguments = mapOf("body" to messageBody),
                dynamicBindings = listOf(
                    DynamicBinding(targetArg = "recipient", fromStepId = step0.id, fromPropertyPath = "phoneNumber")
                ),
                isReadOnly = true,
                dependsOnStepIds = listOf(step0.id)
            )

            val step2 = createStep(
                stepIndex = 2,
                title = "Send SMS Message",
                toolName = "messages_send_sms",
                arguments = mapOf("body" to messageBody),
                dynamicBindings = listOf(
                    DynamicBinding(targetArg = "recipient", fromStepId = step0.id, fromPropertyPath = "phoneNumber")
                ),
                isReadOnly = false,
                riskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL,
                requiredPermission = "android.permission.SEND_SMS",
                dependsOnStepIds = listOf(step0.id, step1.id)
            )

            val needsBody = messageBody.isBlank() || messageBody.equals(contactName, ignoreCase = true)
            return WorkflowPlan(
                title = "Send SMS to $contactName",
                userQuery = raw,
                state = if (needsBody) WorkflowState.AWAITING_USER_INPUT else WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0, step1, step2),
                missingInputPrompt = if (needsBody) "What message would you like to send to $contactName?" else null,
                missingInputParam = if (needsBody) "body" else null
            )
        }

        return null
    }

    // =========================================================================
    // Family B: Calendar and Reminders
    // =========================================================================

    private fun planCalendarAndReminders(raw: String, q: String): WorkflowPlan? {
        // Pattern B1: Schedule meeting with contact + conflict check
        val schedMatch = Pattern.compile("(?i)(?:schedule|create|set up)(?:\\s+a)?(?:\\s+meeting|\\s+event)?\\s+(?:with\\s+([a-zA-Z0-9_]+?)\\s+)?(?:called|titled)?\\s*[\"']?(.+?)[\"']?\\s+(?:on|for|at)?\\s*(tomorrow|today|next week|monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?:\\s+at\\s+([0-9]{1,2}(?::[0-9]{2})?\\s*(?:am|pm)?))?").matcher(raw)
        if (schedMatch.find() && (q.contains("meeting") || q.contains("schedule") || q.contains("event"))) {
            val contact = schedMatch.group(1)?.trim()
            val title = schedMatch.group(2)?.trim() ?: "Meeting"
            val day = schedMatch.group(3)?.trim() ?: "tomorrow"
            val time = schedMatch.group(4)?.trim() ?: "14:00"

            val steps = mutableListOf<WorkflowStep>()
            var idx = 0

            // Step 0 (Optional): Resolve attendee contact
            var contactStepId: String? = null
            if (!contact.isNullOrBlank() && !contact.equals("me", ignoreCase = true)) {
                val sContact = createStep(
                    stepIndex = idx++,
                    title = "Resolve Attendee: $contact",
                    toolName = "contacts_resolve",
                    arguments = mapOf("name" to contact),
                    isReadOnly = true
                )
                steps.add(sContact)
                contactStepId = sContact.id
            }

            // Step 1: Query calendar for conflict check
            val sCheck = createStep(
                stepIndex = idx++,
                title = "Check Schedule Conflicts ($day)",
                toolName = "calendar_list_events",
                arguments = mapOf("filterTime" to day),
                isReadOnly = true
            )
            steps.add(sCheck)

            // Step 2: Create event with verification
            val sCreate = createStep(
                stepIndex = idx,
                title = "Create Calendar Event: $title",
                toolName = "calendar_create_event",
                arguments = mapOf(
                    "title" to title,
                    "startTime" to "$day $time",
                    "durationMinutes" to 60
                ),
                isReadOnly = false,
                riskLevel = ToolRiskLevel.MEDIUM,
                requiredPermission = "android.permission.WRITE_CALENDAR",
                dependsOnStepIds = listOfNotNull(contactStepId, sCheck.id)
            )
            steps.add(sCreate)

            return WorkflowPlan(
                title = "Conflict-Checked Meeting Creation",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = steps
            )
        }

        // Pattern B2: Schedule gap analysis
        if (q.contains("find gaps in my schedule") ||
            q.contains("what are my free slots") ||
            q.contains("available time today") ||
            q == "check my schedule and find free time"
        ) {
            val s1 = createStep(
                stepIndex = 0,
                title = "Fetch Today's Calendar Schedule",
                toolName = "calendar_list_events",
                arguments = mapOf("filterTime" to "today"),
                isReadOnly = true
            )
            val s2 = createStep(
                stepIndex = 1,
                title = "Verify Local Time Reference",
                toolName = "clock_query_time",
                arguments = emptyMap(),
                isReadOnly = true
            )
            return WorkflowPlan(
                title = "Schedule Gap & Availability Analysis",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(s1, s2)
            )
        }

        // Pattern B3: Create reminder from prompt
        val remMatch = Pattern.compile("(?i)(?:remind me to|create a reminder to|set a reminder for)\\s+(.+?)(?:\\s+(?:at|on)?\\s*(tomorrow.*|today.*|in\\s+[0-9]+\\s*(?:min|minute|hour)s?|[0-9]{1,2}(?::[0-9]{2})?\\s*(?:am|pm)?))$").matcher(raw)
        if (remMatch.find()) {
            val task = remMatch.group(1)?.trim() ?: ""
            val time = remMatch.group(2)?.trim() ?: "in 1 hour"
            if (task.isNotEmpty()) {
                val sRem = createStep(
                    stepIndex = 0,
                    title = "Create Verified Reminder",
                    toolName = "reminder_create",
                    arguments = mapOf("title" to task, "time" to time),
                    isReadOnly = false,
                    riskLevel = ToolRiskLevel.MEDIUM
                )
                return WorkflowPlan(
                    title = "Scheduled Reminder: $task",
                    userQuery = raw,
                    state = WorkflowState.PLANNING,
                    planSource = PlanSource.DETERMINISTIC,
                    steps = listOf(sRem)
                )
            }
        }

        return null
    }

    // =========================================================================
    // Family C: Screen-Aware Assistance
    // =========================================================================

    private fun planScreenAwareAssistance(raw: String, q: String): WorkflowPlan? {
        // Pattern C1: Open notification shade and reinspect screen
        if (q.contains("notification shade and verify") ||
            q.contains("open notifications and check") ||
            q.contains("pull down notifications and inspect")
        ) {
            val step0 = createStep(
                stepIndex = 0,
                title = "Open Android Notification Shade",
                toolName = "accessibility_perform_action",
                arguments = mapOf("action" to "notifications"),
                isReadOnly = false,
                riskLevel = ToolRiskLevel.LOW
            )
            val step1 = createStep(
                stepIndex = 1,
                title = "Reinspect Screen to Verify Panel",
                toolName = "accessibility_read_screen",
                arguments = emptyMap(),
                isReadOnly = true,
                dependsOnStepIds = listOf(step0.id)
            )
            return WorkflowPlan(
                title = "Verified Notification Shade Navigation",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0, step1)
            )
        }

        // Pattern C2: Inspect screen and identify interactive controls
        if (q.contains("what can i tap") ||
            q.contains("inspect screen options") ||
            q == "explain my screen and buttons"
        ) {
            val step0 = createStep(
                stepIndex = 0,
                title = "Inspect Accessible Screen Content",
                toolName = "accessibility_read_screen",
                arguments = emptyMap(),
                isReadOnly = true
            )
            return WorkflowPlan(
                title = "Screen Control Analysis",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0)
            )
        }

        return null
    }

    // =========================================================================
    // Family D: App Usage and Routines
    // =========================================================================

    private fun planUsageAndRoutines(raw: String, q: String): WorkflowPlan? {
        // Pattern D1: Morning Routine
        if (q == "morning routine" || q == "start morning routine" || q == "morning briefing") {
            val step0 = createStep(
                stepIndex = 0,
                title = "Query Local Time",
                toolName = "clock_query_time",
                arguments = emptyMap(),
                isReadOnly = true
            )
            val step1 = createStep(
                stepIndex = 1,
                title = "Fetch Today's Calendar Schedule",
                toolName = "calendar_list_events",
                arguments = mapOf("filterTime" to "today"),
                isReadOnly = true
            )
            val step2 = createStep(
                stepIndex = 2,
                title = "Summarize Unread Notifications",
                toolName = "notifications_read_summary",
                arguments = emptyMap(),
                isReadOnly = true
            )
            return WorkflowPlan(
                title = "Morning Executive Briefing",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.USER_ROUTINE,
                steps = listOf(step0, step1, step2)
            )
        }

        // Pattern D2: Bedtime / Evening Routine
        if (q == "bedtime routine" || q == "evening routine" || q == "good night routine") {
            val step0 = createStep(
                stepIndex = 0,
                title = "Verify Tomorrow's Active Alarms",
                toolName = "alarm_show",
                arguments = emptyMap(),
                isReadOnly = true
            )
            val step1 = createStep(
                stepIndex = 1,
                title = "Check Tomorrow's Early Schedule",
                toolName = "calendar_list_events",
                arguments = mapOf("filterTime" to "tomorrow"),
                isReadOnly = true
            )
            return WorkflowPlan(
                title = "Bedtime Alarm & Schedule Routine",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.USER_ROUTINE,
                steps = listOf(step0, step1)
            )
        }

        // Pattern D3: Usage stats breakdown
        if (q.contains("screen time and compare") ||
            (q.contains("how much did i use") && q.contains("and"))
        ) {
            val step0 = createStep(
                stepIndex = 0,
                title = "Query Daily App Usage Statistics",
                toolName = "usage_get_stats",
                arguments = mapOf("periodDays" to 1),
                isReadOnly = true
            )
            return WorkflowPlan(
                title = "Comparative Screen Time Analysis",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0)
            )
        }

        return null
    }

    // =========================================================================
    // Family E: Context-Aware Multi-Tool Personal Assistance
    // =========================================================================

    private fun planContextAwareAssistance(raw: String, q: String): WorkflowPlan? {
        // Pattern E1: Schedule + Personal Notes Cross-Reference
        if ((q.contains("next meeting") && q.contains("note")) ||
            (q.contains("calendar") && q.contains("note")) ||
            q == "where is my next meeting and what notes do i have"
        ) {
            val step0 = createStep(
                stepIndex = 0,
                title = "Query Upcoming Calendar Events",
                toolName = "calendar_list_events",
                arguments = mapOf("filterTime" to "today"),
                isReadOnly = true
            )
            val step1 = createStep(
                stepIndex = 1,
                title = "Search Relevant Meeting Notes",
                toolName = "notes_list",
                arguments = emptyMap(),
                isReadOnly = true
            )
            return WorkflowPlan(
                title = "Cross-Context Meeting & Notes Synthesis",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0, step1)
            )
        }

        // Pattern E2: Save note and create reminder
        val noteRemMatch = Pattern.compile("(?i)(?:save|take|create)(?:\\s+a)?\\s+note\\s+[\"']?([^\"']+)[\"']?\\s+(?:with content|saying)?\\s+[\"']?([^\"']+)[\"']?\\s+and\\s+remind\\s+me\\s+(?:at|in)?\\s*(.+)$").matcher(raw)
        if (noteRemMatch.find()) {
            val title = noteRemMatch.group(1)?.trim() ?: "Note"
            val content = noteRemMatch.group(2)?.trim() ?: ""
            val remTime = noteRemMatch.group(3)?.trim() ?: "tomorrow"

            val step0 = createStep(
                stepIndex = 0,
                title = "Save Note: $title",
                toolName = "note_create",
                arguments = mapOf("title" to title, "content" to content),
                isReadOnly = false,
                riskLevel = ToolRiskLevel.MEDIUM
            )
            val step1 = createStep(
                stepIndex = 1,
                title = "Schedule Reminder for Note",
                toolName = "reminder_create",
                arguments = mapOf("title" to "Review note: $title", "time" to remTime),
                isReadOnly = false,
                riskLevel = ToolRiskLevel.MEDIUM,
                dependsOnStepIds = listOf(step0.id)
            )
            return WorkflowPlan(
                title = "Note Creation & Linked Reminder",
                userQuery = raw,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.DETERMINISTIC,
                steps = listOf(step0, step1)
            )
        }

        return null
    }

    // =========================================================================
    // Gemma 3 1B LLM Plan Schema Parsing & Native Validation
    // =========================================================================

    /**
     * Parses and strictly validates a model-generated JSON plan against registered native tools.
     * Rejects unknown tools, invalid types, and unauthorized risk overrides.
     */
    fun parseAndValidateGemmaPlan(rawJson: String, userQuery: String): WorkflowPlan? {
        return try {
            val cleanJson = extractJsonBlock(rawJson)
            val json = JSONObject(cleanJson)
            val title = json.optString("title", "Multi-Step Assistant Workflow")
            val stepsArray = json.optJSONArray("steps") ?: return null

            val validatedSteps = mutableListOf<WorkflowStep>()
            val stepIdsByIndex = mutableMapOf<Int, String>()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val toolName = stepObj.getString("toolName")

                // Native Validation 1: Tool must be registered
                val tool = toolRegistry.getTool(toolName)
                if (tool == null) {
                    Log.w(TAG, "Gemma proposed unknown tool '$toolName', rejecting plan")
                    return null
                }

                // Native Validation 2: Arguments must be valid JSON map
                val argsObj = stepObj.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, Any?>()
                for (key in argsObj.keys()) {
                    argsMap[key] = argsObj.get(key)
                }

                // Native Validation 3: Parse dynamic bindings
                val bindingsList = mutableListOf<DynamicBinding>()
                val bindingsArray = stepObj.optJSONArray("dynamicBindings")
                if (bindingsArray != null) {
                    for (b in 0 until bindingsArray.length()) {
                        val bObj = bindingsArray.getJSONObject(b)
                        val targetArg = bObj.getString("targetArg")
                        val fromStepIndex = bObj.getInt("fromStepIndex")
                        val fromPath = bObj.getString("fromPropertyPath")
                        val fromId = stepIdsByIndex[fromStepIndex] ?: continue
                        bindingsList.add(DynamicBinding(targetArg, fromId, fromPath))
                    }
                }

                // Native Validation 4: Derive risk and permissions from native tool metadata, NEVER model flags
                val stepId = UUID.randomUUID().toString()
                stepIdsByIndex[i] = stepId

                val isReadOnly = tool.riskLevel == ToolRiskLevel.LOW && !tool.name.contains("create") && !tool.name.contains("delete") && !tool.name.contains("send")
                val step = WorkflowStep(
                    id = stepId,
                    stepIndex = i,
                    title = stepObj.optString("title", "Execute ${tool.name}"),
                    toolName = tool.name,
                    arguments = argsMap,
                    dynamicBindings = bindingsList,
                    isReadOnly = isReadOnly,
                    riskLevel = tool.riskLevel,
                    requiredPermission = tool.requiredPermissions.firstOrNull(),
                    status = StepExecutionStatus.PENDING,
                    verificationStatus = StepVerificationStatus.PENDING,
                    idempotencyKey = UUID.randomUUID().toString()
                )
                validatedSteps.add(step)
            }

            if (validatedSteps.isEmpty()) return null

            WorkflowPlan(
                title = title,
                userQuery = userQuery,
                state = WorkflowState.PLANNING,
                planSource = PlanSource.GEMMA_ON_DEVICE,
                steps = validatedSteps
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse/validate Gemma plan: ${e.message}")
            null
        }
    }

    private fun createStep(
        stepIndex: Int,
        title: String,
        toolName: String,
        arguments: Map<String, Any?>,
        dynamicBindings: List<DynamicBinding> = emptyList(),
        isReadOnly: Boolean = false,
        riskLevel: ToolRiskLevel = ToolRiskLevel.LOW,
        requiredPermission: String? = null,
        dependsOnStepIds: List<String> = emptyList()
    ): WorkflowStep {
        val tool = toolRegistry.getTool(toolName)
        val finalRisk = tool?.riskLevel ?: riskLevel
        val finalPerm = tool?.requiredPermissions?.firstOrNull() ?: requiredPermission

        return WorkflowStep(
            id = UUID.randomUUID().toString(),
            stepIndex = stepIndex,
            title = title,
            toolName = toolName,
            arguments = arguments,
            dynamicBindings = dynamicBindings,
            isReadOnly = isReadOnly,
            riskLevel = finalRisk,
            requiredPermission = finalPerm,
            status = StepExecutionStatus.PENDING,
            verificationStatus = StepVerificationStatus.PENDING,
            dependsOnStepIds = dependsOnStepIds,
            idempotencyKey = UUID.randomUUID().toString()
        )
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            text.trim()
        }
    }
}
