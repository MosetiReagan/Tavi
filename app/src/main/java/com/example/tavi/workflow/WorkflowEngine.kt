package com.example.tavi.workflow

import android.content.Context
import android.util.Log
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.ConfirmationRequest
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.PermissionCoordinator
import com.example.tavi.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Robust Multi-Step Workflow Orchestration Engine.
 *
 * Coordinates execution of [WorkflowPlan]s across the 51 native tools:
 * - Dynamic parameter binding between sequential steps
 * - Native permission pre-checking and risk assessment
 * - Explicit, scoped confirmation for consequential operations
 * - Bounded retries and execution time limits (max 30s per workflow)
 * - Duplicate-action prevention (idempotency key tracking)
 * - Mandatory post-execution state verification via [WorkflowStepVerifier]
 * - Truthful completion reporting distinguishing verified outcomes from estimates
 */
class WorkflowEngine(
    private val context: Context,
    private val toolRegistry: ToolRegistry = ToolRegistry.getInstance(context),
    private val db: TaviDatabase? = null,
    private val persist: ((WorkflowPlan) -> Unit)? = null,
    private val execute: (suspend (ToolCall, Boolean) -> ToolResult)? = null,
    private val verify: ((WorkflowStep, ToolResult) -> StepVerificationResult)? = null
) {

    companion object {
        private const val TAG = "WorkflowEngine"
        const val MAX_WORKFLOW_TIMEOUT_MS = 30_000L
        const val MAX_STEP_TIMEOUT_MS = 10_000L
        const val MAX_STEPS_LIMIT = 10
    }

    /**
     * Executes the workflow plan step-by-step with state notifications.
     */
    suspend fun executePlan(
        initialPlan: WorkflowPlan,
        authorizedStepId: String? = null,
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan = withContext(Dispatchers.IO) {
        if (initialPlan.state in setOf(WorkflowState.CANCELLED, WorkflowState.COMPLETED)) return@withContext initialPlan
        if (initialPlan.state == WorkflowState.AWAITING_USER_INPUT) {
            notifyUpdate(initialPlan, onPlanUpdated)
            return@withContext initialPlan
        }
        var plan = initialPlan.copy(state = WorkflowState.EXECUTING, updatedAt = System.currentTimeMillis())
        if (plan.steps.size > MAX_STEPS_LIMIT || plan.steps.isEmpty()) {
            plan = plan.copy(state = WorkflowState.FAILED, error = "A task must have 1–$MAX_STEPS_LIMIT steps.")
            notifyUpdate(plan, onPlanUpdated)
            return@withContext plan
        }
        val steps = plan.steps.toMutableList()
        suspend fun update() {
            plan = plan.copy(steps = steps.toList(), updatedAt = System.currentTimeMillis())
            notifyUpdate(plan, onPlanUpdated)
        }
        val outcome = withTimeoutOrNull(MAX_WORKFLOW_TIMEOUT_MS) {
            for (i in plan.currentStepIndex until steps.size) {
                var step = steps[i]
                if (step.status in setOf(StepExecutionStatus.VERIFIED, StepExecutionStatus.COMPLETED_UNVERIFIED, StepExecutionStatus.SKIPPED)) continue
                plan = plan.copy(currentStepIndex = i)
                if (step.dependsOnStepIds.any { id -> steps.none { it.id == id && it.status in setOf(StepExecutionStatus.VERIFIED, StepExecutionStatus.COMPLETED_UNVERIFIED) } }) {
                    steps[i] = step.copy(status = StepExecutionStatus.SKIPPED)
                    update()
                    continue
                }
                val args = resolveDynamicBindings(step, steps)
                step = step.copy(arguments = args)
                steps[i] = step
                val missing = step.dynamicBindings.firstOrNull { args[it.targetArg]?.toString().isNullOrBlank() }
                if (missing != null) {
                    plan = plan.copy(state = WorkflowState.AWAITING_USER_INPUT,
                        missingInputParam = missing.targetArg,
                        missingInputPrompt = "Please provide ${missing.targetArg} for ${step.title}.")
                    update()
                    return@withTimeoutOrNull plan
                }
                val consequential = toolRegistry.getTool(step.toolName)?.requiresConfirmation == true ||
                    SecurityGuard.requiresUserConfirmation(step.toolName) || step.riskLevel == ToolRiskLevel.HIGH_CONSEQUENTIAL
                if (consequential && authorizedStepId != step.id) {
                    steps[i] = step.copy(status = StepExecutionStatus.AWAITING_CONFIRMATION)
                    plan = plan.copy(state = WorkflowState.AWAITING_CONFIRMATION, pendingConfirmationStepId = step.id)
                    update()
                    return@withTimeoutOrNull plan
                }
                steps[i] = step.copy(status = StepExecutionStatus.EXECUTING)
                update()
                val call = ToolCall(id = step.idempotencyKey, toolName = step.toolName, arguments = args)
                val result = withTimeoutOrNull(MAX_STEP_TIMEOUT_MS) {
                    executeToolWithRetry(call, step.maxRetries, consequential && authorizedStepId == step.id)
                } ?: ToolResult(call.id, call.toolName, "{}", false,
                    errorMessage = "Step timed out. Inspect the destination before retrying a write.")
                val verification = verify?.invoke(step, result) ?: WorkflowStepVerifier.verifyStep(context, step, result)
                val status = when {
                    !result.isSuccess || verification.status == StepVerificationStatus.FAILED -> StepExecutionStatus.FAILED
                    verification.status == StepVerificationStatus.UNVERIFIED -> StepExecutionStatus.COMPLETED_UNVERIFIED
                    else -> StepExecutionStatus.VERIFIED
                }
                steps[i] = step.copy(result = result, status = status, verificationStatus = verification.status,
                    verificationDetail = verification.detail, errorMessage = result.errorMessage,
                    executedAt = System.currentTimeMillis())
                plan = plan.copy(pendingConfirmationStepId = null)
                if (status == StepExecutionStatus.FAILED) {
                    for (j in i + 1 until steps.size) steps[j] = steps[j].copy(status = StepExecutionStatus.SKIPPED)
                    plan = plan.copy(state = WorkflowState.PARTIALLY_COMPLETED, error = result.errorMessage ?: verification.detail,
                        executionSummary = buildSummary(steps))
                    update()
                    return@withTimeoutOrNull plan
                }
                update()
            }
            plan = plan.copy(state = if (steps.all { it.status in setOf(StepExecutionStatus.VERIFIED, StepExecutionStatus.COMPLETED_UNVERIFIED) })
                WorkflowState.COMPLETED else WorkflowState.PARTIALLY_COMPLETED,
                completedAt = System.currentTimeMillis(), executionSummary = buildSummary(steps))
            update()
            plan
        }
        if (outcome == null) {
            plan = plan.copy(state = WorkflowState.FAILED, error = "Task timed out; inspect any unfinished action before retrying.",
                executionSummary = buildSummary(steps))
            update()
        }
        plan
    }

    /** Approval exists only for the current step of this conversation, never in persisted arguments. */
    suspend fun confirmStep(
        currentPlan: WorkflowPlan,
        stepId: String,
        confirmed: Boolean,
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan {
        if (currentPlan.state != WorkflowState.AWAITING_CONFIRMATION || currentPlan.pendingConfirmationStepId != stepId) return currentPlan
        if (currentPlan.steps.getOrNull(currentPlan.currentStepIndex)?.id != stepId) return currentPlan
        if (!confirmed) return cancelWorkflow(currentPlan, onPlanUpdated)
        return executePlan(currentPlan, authorizedStepId = stepId, onPlanUpdated = onPlanUpdated)
    }

    /**
     * Resumes a workflow that was paused in [WorkflowState.AWAITING_USER_INPUT].
     */
    suspend fun provideMissingInput(
        currentPlan: WorkflowPlan,
        inputValue: String,
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan = withContext(Dispatchers.IO) {
        val paramName = currentPlan.missingInputParam ?: "body"
        val steps = currentPlan.steps.toMutableList()
        val currentIdx = currentPlan.currentStepIndex.coerceIn(0, steps.lastIndex.coerceAtLeast(0))

        if (steps.isNotEmpty()) {
            val step = steps[currentIdx]
            steps[currentIdx] = step.copy(arguments = step.arguments + (paramName to inputValue))
        }

        val updatedPlan = currentPlan.copy(
            state = WorkflowState.EXECUTING,
            steps = steps,
            missingInputPrompt = null,
            missingInputParam = null,
            updatedAt = System.currentTimeMillis()
        )
        notifyUpdate(updatedPlan, onPlanUpdated)
        executePlan(updatedPlan, onPlanUpdated = onPlanUpdated)
    }

    /**
     * Cancels an active or paused workflow.
     */
    suspend fun cancelWorkflow(
        currentPlan: WorkflowPlan,
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan = withContext(Dispatchers.IO) {
        val steps = currentPlan.steps.map {
            if (it.status in setOf(StepExecutionStatus.PENDING, StepExecutionStatus.EXECUTING, StepExecutionStatus.VERIFYING, StepExecutionStatus.AWAITING_CONFIRMATION)) {
                it.copy(status = StepExecutionStatus.CANCELLED)
            } else it
        }
        val plan = currentPlan.copy(
            state = WorkflowState.CANCELLED,
            steps = steps,
            pendingConfirmationStepId = null,
            missingInputPrompt = null,
            executionSummary = buildSummary(steps),
            updatedAt = System.currentTimeMillis()
        )
        notifyUpdate(plan, onPlanUpdated)
        plan
    }

    // =========================================================================
    // Dynamic Binding Resolution
    // =========================================================================

    private fun resolveDynamicBindings(step: WorkflowStep, allSteps: List<WorkflowStep>): Map<String, Any?> {
        val resolved = step.arguments.toMutableMap()

        for (binding in step.dynamicBindings) {
            val sourceStep = allSteps.find { it.id == binding.fromStepId } ?: continue
            val result = sourceStep.result ?: continue
            if (!result.isSuccess) continue

            val extractedValue = extractValueFromJson(result.resultJson, binding.fromPropertyPath)
            if (extractedValue != null) {
                resolved[binding.targetArg] = extractedValue
            } else if (binding.defaultValue != null) {
                resolved[binding.targetArg] = binding.defaultValue
            }
        }

        return resolved
    }

    private fun extractValueFromJson(jsonStr: String, path: String): String? {
        return try {
            val json = JSONObject(jsonStr)
            if (json.has(path)) {
                return json.optString(path)
            }
            // Check top-level arrays (e.g. notifications, contacts)
            for (key in json.keys()) {
                val array = json.optJSONArray(key)
                if (array != null && array.length() > 0) {
                    val firstItem = array.optJSONObject(0)
                    if (firstItem != null && firstItem.has(path)) {
                        return firstItem.optString(path)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun executeToolWithRetry(toolCall: ToolCall, maxRetries: Int, userConfirmed: Boolean): ToolResult {
        var attempts = 0
        var lastResult: ToolResult? = null

        val retries = if (com.example.tavi.tools.NativeToolPolicy.isReadOnly(toolCall.toolName)) maxRetries.coerceIn(0, 2) else 0
        while (attempts <= retries) {
            attempts++
            lastResult = execute?.invoke(toolCall, userConfirmed) ?: toolRegistry.executeToolCall(toolCall, userConfirmed)
            if (lastResult.isSuccess || attempts > retries) {
                break
            }
            // Delay before bounded retry
            kotlinx.coroutines.delay(200L * attempts)
        }

        return lastResult ?: ToolResult(
            toolCallId = toolCall.id,
            toolName = toolCall.toolName,
            resultJson = "{}",
            isSuccess = false,
            errorMessage = "Execution failed after $attempts attempt(s)"
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun buildSummary(steps: List<WorkflowStep>): WorkflowExecutionSummary {
        val total = steps.size
        val completed = steps.count { it.status == StepExecutionStatus.VERIFIED || it.status == StepExecutionStatus.COMPLETED_UNVERIFIED }
        val verified = steps.count { it.status == StepExecutionStatus.VERIFIED }
        val failed = steps.count { it.status == StepExecutionStatus.FAILED }
        val skipped = steps.count { it.status == StepExecutionStatus.SKIPPED }

        val verifiedList = steps.filter { it.status == StepExecutionStatus.VERIFIED }.map {
            it.verificationDetail ?: "Verified: ${it.title}"
        }
        val unverifiedList = steps.filter { it.status == StepExecutionStatus.COMPLETED_UNVERIFIED }.map {
            it.verificationDetail ?: "Completed (Unverified): ${it.title}"
        }

        val text = buildString {
            append("Completed $completed of $total steps ($verified verified). ")
            if (failed > 0) append("$failed failed. ")
            if (skipped > 0) append("$skipped skipped. ")
        }

        return WorkflowExecutionSummary(
            totalSteps = total,
            completedSteps = completed,
            verifiedSteps = verified,
            failedSteps = failed,
            skippedSteps = skipped,
            isFullyVerified = verified == total && failed == 0,
            humanReadableText = text,
            verifiedOutcomes = verifiedList,
            unverifiedOutcomes = unverifiedList
        )
    }

    private suspend fun notifyUpdate(plan: WorkflowPlan, onPlanUpdated: (suspend (WorkflowPlan) -> Unit)?) {
        if (persist != null) persist.invoke(plan) else (db ?: TaviDatabase.getInstance(context)).saveWorkflow(plan)
        onPlanUpdated?.invoke(plan)
    }
}
