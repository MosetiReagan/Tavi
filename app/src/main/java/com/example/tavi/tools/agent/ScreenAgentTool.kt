package com.example.tavi.tools.agent

import android.content.Context
import android.provider.Settings
import com.example.tavi.agent.*
import com.example.tavi.engine.InferenceEngine
import com.example.tavi.engine.LiteRtLlmEngine
import com.example.tavi.model.*
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.PermissionCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

class ScreenAgentRunTool(private val engineProvider: () -> InferenceEngine? = { null }) : AssistantTool {
    override val name = "screen_agent_run"
    override val category = ToolCategory.ACCESSIBILITY
    override val riskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL
    override val specialAccessType = SpecialAccessType.ACCESSIBILITY_SERVICE
    override val description = "Use a local model to navigate Settings, Chrome, or Firefox with exact visible nodes and individual confirmations."
    override val parametersSchema = """{"type":"object","properties":{
        "goal":{"type":"string","minLength":1,"maxLength":300},
        "target_app":{"type":"string","enum":["com.android.settings","com.android.chrome","org.mozilla.firefox"]},
        "success_text":{"type":"string","description":"Exact non-editable text expected on the final screen; completion is otherwise unverified."}},
        "required":["goal","target_app"]}"""
    override val requiredPermissions = emptyList<String>()
    override val requiresConfirmation = true
    override suspend fun execute(toolCallId: String, arguments: Map<String, Any?>, context: Context): ToolResult =
        ScreenAgentRuntime.run(context, toolCallId, arguments["goal"].toString(), arguments["target_app"].toString(),
            arguments["success_text"] as? String, engineProvider())
}

class ScreenAgentResumeTool(private val engineProvider: () -> InferenceEngine? = { null }) : AssistantTool {
    override val name = "screen_agent_resume"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Resume the exact paused screen action after explicit user confirmation; rejects expired or changed targets."
    override val parametersSchema = """{"type":"object","properties":{"token":{"type":"string"}},"required":["token"]}"""
    override val requiredPermissions = emptyList<String>()
    override val requiresConfirmation = true
    override suspend fun execute(toolCallId: String, arguments: Map<String, Any?>, context: Context): ToolResult {
        val continuation = AgentContinuationStore(context).consume(arguments["token"].toString())
            ?: return ToolResult(toolCallId, name, "{}", false, errorMessage = "This screen approval expired or was already used. Start a new request.")
        return ScreenAgentRuntime.run(context, toolCallId, continuation.goal, continuation.target, continuation.expected,
            engineProvider(), continuation.approval, continuation.stepsUsed)
    }
}

private object ScreenAgentRuntime {
    private val mutex = Mutex()
    suspend fun run(context: Context, id: String, goal: String, target: String, expected: String?,
        inference: InferenceEngine?, approval: AgentApproval? = null, steps: Int = 0): ToolResult {
        val name = if (approval == null) "screen_agent_run" else "screen_agent_resume"
        fun failure(text: String) = ToolResult(id, name, "{}", false, errorMessage = text)
        if (!AgentPreferences.isAgentModeEnabled(context)) return failure("Enable Screen Agent mode in Tavi Settings first.")
        if (!PermissionCoordinator.hasSpecialAccess(context, SpecialAccessType.ACCESSIBILITY_SERVICE) || !ScreenAgentService.isAvailable)
            return ToolResult(id, name, "{}", false, settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                state = ToolExecutionState.USER_ACTION_REQUIRED, errorMessage = "Enable Tavi's accessibility service to use the screen agent.")
        val engine = inference as? LiteRtLlmEngine ?: return failure("Load a local model in Tavi before using screen actions.")
        if (engine.loadModel().isFailure || !engine.isGenuineInferenceActive) return failure("Screen actions need a working local model. Check model settings.")
        if (!mutex.tryLock()) return failure("Another screen task is already running.")
        var paused = false
        try {
            AgentStopOverlay.show(currentCoroutineContext().job)
            val planner = LocalScreenActionPlanner(engine, goal)
            val result = withTimeoutOrNull(120_000) {
                AgentExecutor(context).executeGoal(goal, target, expected, approval, steps) { step, tree -> planner.next(step, tree) }
            } ?: return failure("The screen task timed out. Inspect the app before starting again.")
            if (result.requiresUserConfirmation) {
                val action = result.pendingConfirmationAction ?: return failure("Missing paused action.")
                val token = AgentContinuationStore(context).save(AgentContinuation(goal, target, expected,
                    AgentApproval(action, result.screenFingerprint ?: return failure("Missing target evidence.")), result.stepsCompleted - 1))
                paused = true
                AgentStopOverlay.review()
                val args = mapOf("token" to token)
                val request = ConfirmationRequest(actionType = "SCREEN_ACTION", title = "${AgentGrounding.json(action)} in $target",
                    details = "Goal: $goal\nExact action: ${AgentGrounding.json(action)}\nThe screen must match when resumed. Approval expires in two minutes.", toolName = "screen_agent_resume", arguments = args)
                return ToolResult(id, name, "{}", false, requiresUserConfirmation = true, confirmationPayload = request,
                    state = ToolExecutionState.USER_ACTION_REQUIRED, errorMessage = "Review the next screen action in Tavi.")
            }
            return ToolResult(id, name, JSONObject().put("summary", result.summary).put("stepsCompleted", result.stepsCompleted)
                .put("verified", result.isSuccess).toString(), result.isSuccess, errorMessage = result.summary.takeIf { !result.isSuccess })
        } finally {
            if (!paused) withContext(NonCancellable + Dispatchers.Main) { AgentStopOverlay.hide() }
            mutex.unlock()
        }
    }
}
