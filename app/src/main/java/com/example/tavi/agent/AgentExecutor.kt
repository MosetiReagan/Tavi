package com.example.tavi.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.AuditLogEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean

data class AgentExecutionStep(
    val stepIndex: Int,
    val action: AgentAction,
    val targetPackage: String,
    val success: Boolean,
    val description: String
)

data class AgentExecutionResult(
    val isSuccess: Boolean,
    val stepsCompleted: Int,
    val summary: String,
    val requiresUserConfirmation: Boolean = false,
    val pendingConfirmationAction: AgentAction? = null,
    val screenFingerprint: String? = null,
    val executionHistory: List<AgentExecutionStep> = emptyList()
)

class AgentExecutor(
    private val context: Context,
    private val db: TaviDatabase? = null
) {
    companion object {
        private const val TAG = "AgentExecutor"
        val killSwitch = AtomicBoolean(false)

        fun triggerKillSwitch() {
            killSwitch.set(true)
            Log.w(TAG, "Agent kill switch triggered!")
        }

        fun resetKillSwitch() {
            killSwitch.set(false)
        }
    }

    /**
     * Executes an agentic goal up to a maximum of 12 steps.
     */
    suspend fun executeGoal(
        goal: String,
        targetApp: String? = null,
        expectedText: String? = null,
        approval: AgentApproval? = null,
        initialSteps: Int = 0,
        actionProvider: suspend (step: Int, currentTree: CompactScreenTree) -> AgentAction?
    ): AgentExecutionResult {
        resetKillSwitch()

        if (!ScreenAgentService.isAvailable) {
            return AgentExecutionResult(
                isSuccess = false,
                stepsCompleted = 0,
                summary = "Screen Agent requires Accessibility Service to be enabled in Android Settings."
            )
        }

        if (targetApp !in AgentGrounding.supportedPackages) return AgentExecutionResult(false, 0, "Choose a supported target app: Android Settings, Chrome, or Firefox.")

        // Open target app if requested
        if (!targetApp.isNullOrBlank()) {
            if (AgentSafetyGuard.isPackageProhibited(targetApp)) {
                return AgentExecutionResult(
                    isSuccess = false,
                    stepsCompleted = 0,
                    summary = "Target app '$targetApp' is prohibited by safety policy."
                )
            }
            if (!openAppPackage(targetApp)) return AgentExecutionResult(false, 0, "The target app could not be opened.")
            delay(1000L) // Wait for app launch
        }

        val history = mutableListOf<AgentExecutionStep>()
        var stepCount = initialSteps
        var firstStep = true
        var unchanged = 0
        var previousFingerprint: String? = null

        while (stepCount < AgentSafetyGuard.MAX_AGENT_STEPS) {
            currentCoroutineContext().ensureActive()
            // Check kill switch
            if (killSwitch.get()) {
                logAudit("agent_killed", "Agent stopped by kill switch at step $stepCount")
                return AgentExecutionResult(
                    isSuccess = false,
                    stepsCompleted = stepCount,
                    summary = "Screen Agent stopped immediately by user kill switch.",
                    executionHistory = history
                )
            }

            // 1. Read screen tree
            val root = ScreenAgentService.activeService?.rootInActiveWindow
            val screenTree = AccessibilityTreeReader.readScreenTree(root)

            // Package safety boundary
            if (screenTree.packageName != targetApp || AgentSafetyGuard.isPackageProhibited(screenTree.packageName)) {
                logAudit("agent_prohibited_pkg", "Blocked interaction with ${screenTree.packageName}")
                return AgentExecutionResult(
                    isSuccess = false,
                    stepsCompleted = stepCount,
                    summary = "Screen Agent paused: active application '${screenTree.packageName}' is blocked for privacy and security.",
                    executionHistory = history
                )
            }

            val fingerprint = AgentGrounding.fingerprint(screenTree)
            unchanged = if (fingerprint == previousFingerprint) unchanged + 1 else 0
            if (unchanged >= 2) return AgentExecutionResult(false, stepCount, "Stopped because the screen did not change after repeated actions.", executionHistory = history)
            previousFingerprint = fingerprint
            if (firstStep && approval != null && approval.screenFingerprint != fingerprint)
                return AgentExecutionResult(false, stepCount, "The screen changed after confirmation. Start a new request and review its target.")
            stepCount++

            // 2. Obtain next action
            val authorized = firstStep && approval != null
            val action = if (authorized) approval!!.action else actionProvider(stepCount, screenTree) ?: AgentAction.Fail("No action decided")
            firstStep = false

            // Terminal actions
            if (action is AgentAction.Complete) {
                val finalTree = AccessibilityTreeReader.readScreenTree(ScreenAgentService.activeService?.rootInActiveWindow)
                val visible = finalTree.packageName == targetApp && AgentGrounding.completionVisible(expectedText, finalTree)
                return AgentExecutionResult(isSuccess = visible, stepsCompleted = stepCount,
                    summary = if (visible) "Verified visible result: $expectedText" else "The model proposed completion. Inspect the screen; no visible success condition was verified.",
                    executionHistory = history)
            }

            if (action is AgentAction.Fail) {
                logAudit("agent_failed", "Agent failed at step $stepCount: ${action.reason}")
                return AgentExecutionResult(
                    isSuccess = false,
                    stepsCompleted = stepCount,
                    summary = "Agent failed: ${action.reason}",
                    executionHistory = history
                )
            }

            AgentGrounding.validate(action, screenTree)?.let { reason ->
                return AgentExecutionResult(false, stepCount, reason, executionHistory = history)
            }
            val target = AgentGrounding.node(action, screenTree)
            val nodeContext = target?.let { "${it.text} ${it.contentDescription} ${it.id}" }
            // 3. Safety inspection of the actual visible target
            if (!authorized && AgentSafetyGuard.isConsequentialAction(action, nodeContext)) {
                logAudit("agent_confirmation_required", "Consequential action requires confirmation: $action")
                return AgentExecutionResult(
                    isSuccess = false,
                    stepsCompleted = stepCount,
                    summary = "Action requires your explicit confirmation: $action",
                    requiresUserConfirmation = true,
                    pendingConfirmationAction = action,
                    screenFingerprint = fingerprint,
                    executionHistory = history
                )
            }

            // 4. Dispatch action
            currentCoroutineContext().ensureActive()
            val latest = AccessibilityTreeReader.readScreenTree(ScreenAgentService.activeService?.rootInActiveWindow)
            if (AgentGrounding.fingerprint(latest) != fingerprint) return AgentExecutionResult(false, stepCount, "The target screen changed before dispatch.")
            val success = dispatchAction(action, screenTree)
            val stepRecord = AgentExecutionStep(
                stepIndex = stepCount,
                action = action,
                targetPackage = screenTree.packageName,
                success = success,
                description = "Dispatched $action (success=$success)"
            )
            history.add(stepRecord)
            if (!success) return AgentExecutionResult(false, stepCount, "Action dispatch failed; stopped without further actions.", executionHistory = history)
            logAudit("agent_step", stepRecord.description)

            // 5. Await UI settle
            delay(500L)
        }

        // Budget reached
        logAudit("agent_budget_exceeded", "Reached hard budget of ${AgentSafetyGuard.MAX_AGENT_STEPS} steps")
        return AgentExecutionResult(
            isSuccess = false,
            stepsCompleted = stepCount,
            summary = "Screen Agent reached maximum step budget (${AgentSafetyGuard.MAX_AGENT_STEPS} steps) without completing goal.",
            executionHistory = history
        )
    }

    private suspend fun dispatchAction(action: AgentAction, tree: CompactScreenTree): Boolean {
        return when (action) {
            is AgentAction.Tap -> ScreenAgentService.dispatchTap(action.x, action.y)
            is AgentAction.TapNode -> ScreenAgentService.clickNode(action.selector, AgentGrounding.node(action, tree))
            is AgentAction.LongPress -> ScreenAgentService.dispatchSwipe(action.x, action.y, action.x, action.y, 800L)
            is AgentAction.Swipe -> ScreenAgentService.dispatchSwipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)
            is AgentAction.TypeText -> ScreenAgentService.typeText(action.selector, action.text, AgentGrounding.node(action, tree))
            is AgentAction.PressBack -> ScreenAgentService.pressBack()
            is AgentAction.PressHome -> ScreenAgentService.pressHome()
            is AgentAction.OpenApp -> openAppPackage(action.packageName)
            is AgentAction.Scroll -> ScreenAgentService.scroll(action.direction)
            else -> false
        }
    }

    private fun openAppPackage(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "Failed launching app $packageName", e)
            false
        }
    }

    private fun logAudit(action: String, details: String) {
        try {
            db?.insertAuditLog(
                AuditLogEntry(
                    toolName = action,
                    status = "SUCCESS",
                    summary = details
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist audit log entry for $action: ${e.message}")
        }
    }
}
