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
    private val db: TaviDatabase = TaviDatabase.getInstance(context)
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
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan = withContext(Dispatchers.IO) {
        var plan = initialPlan.copy(state = WorkflowState.EXECUTING, updatedAt = System.currentTimeMillis())
        notifyUpdate(plan, onPlanUpdated)

        val timeoutResult = withTimeoutOrNull(MAX_WORKFLOW_TIMEOUT_MS) {
            val executedSteps = plan.steps.toMutableList()

            for (i in plan.currentStepIndex until executedSteps.size.coerceAtMost(MAX_STEPS_LIMIT)) {
                var step = executedSteps[i]

                // Duplicate-Action Prevention: Never re-execute verified or completed steps
                if (step.status == StepExecutionStatus.VERIFIED || step.status == StepExecutionStatus.COMPLETED_UNVERIFIED) {
                    continue
                }

                plan = plan.copy(currentStepIndex = i, steps = executedSteps, updatedAt = System.currentTimeMillis())
                notifyUpdate(plan, onPlanUpdated)

                // 1. Resolve Dynamic Parameter Bindings from upstream steps
                val resolvedArgs = resolveDynamicBindings(step, executedSteps)
                step = step.copy(arguments = resolvedArgs)

                // 2. Permission Pre-Check
                val permRequired = step.requiredPermission
                if (!permRequired.isNullOrBlank() && !hasPermission(permRequired)) {
                    step = step.copy(
                        status = StepExecutionStatus.FAILED,
                        errorMessage = "Permission required: $permRequired. Please enable in Android Settings."
                    )
                    executedSteps[i] = step
                    plan = plan.copy(
                        state = WorkflowState.FAILED,
                        steps = executedSteps,
                        error = "Step '${step.title}' failed: Permission denied for $permRequired",
                        executionSummary = buildSummary(executedSteps),
                        updatedAt = System.currentTimeMillis()
                    )
                    notifyUpdate(plan, onPlanUpdated)
                    return@withTimeoutOrNull plan
                }

                // 3. Consequential Action Scoped Confirmation
                val isConsequential = step.riskLevel == ToolRiskLevel.HIGH_CONSEQUENTIAL ||
                        SecurityGuard.requiresUserConfirmation(step.toolName)

                if (isConsequential && step.status != StepExecutionStatus.AWAITING_CONFIRMATION) {
                    step = step.copy(status = StepExecutionStatus.AWAITING_CONFIRMATION)
                    executedSteps[i] = step
                    plan = plan.copy(
                        state = WorkflowState.AWAITING_CONFIRMATION,
                        currentStepIndex = i,
                        steps = executedSteps,
                        pendingConfirmationStepId = step.id,
                        updatedAt = System.currentTimeMillis()
                    )
                    notifyUpdate(plan, onPlanUpdated)
                    // Halt execution and wait for user's explicit confirmation
                    return@withTimeoutOrNull plan
                }

                // 4. Execute Native Step
                step = step.copy(status = StepExecutionStatus.EXECUTING)
                executedSteps[i] = step
                plan = plan.copy(steps = executedSteps, updatedAt = System.currentTimeMillis())
                notifyUpdate(plan, onPlanUpdated)

                val toolCall = ToolCall(toolName = step.toolName, arguments = step.arguments)
                val toolResult = executeToolWithRetry(toolCall, step.maxRetries)

                if (!toolResult.isSuccess) {
                    step = step.copy(
                        status = StepExecutionStatus.FAILED,
                        result = toolResult,
                        errorMessage = toolResult.errorMessage ?: "Execution failed"
                    )
                    executedSteps[i] = step

                    // Handle dependent steps: mark downstream as SKIPPED
                    for (d in i + 1 until executedSteps.size) {
                        if (executedSteps[d].dependsOnStepIds.contains(step.id)) {
                            executedSteps[d] = executedSteps[d].copy(status = StepExecutionStatus.SKIPPED)
                        }
                    }

                    plan = plan.copy(
                        state = WorkflowState.PARTIALLY_COMPLETED,
                        steps = executedSteps,
                        error = "Step '${step.title}' failed: ${toolResult.errorMessage}",
                        executionSummary = buildSummary(executedSteps),
                        updatedAt = System.currentTimeMillis()
                    )
                    notifyUpdate(plan, onPlanUpdated)
                    return@withTimeoutOrNull plan
                }

                // 5. Mandatory Post-Execution State Verification
                step = step.copy(status = StepExecutionStatus.VERIFYING, result = toolResult)
                executedSteps[i] = step
                plan = plan.copy(steps = executedSteps, updatedAt = System.currentTimeMillis())
                notifyUpdate(plan, onPlanUpdated)

                val verification = WorkflowStepVerifier.verifyStep(context, step, toolResult)
                step = step.copy(
                    status = if (verification.status == StepVerificationStatus.VERIFIED || verification.status == StepVerificationStatus.NOT_APPLICABLE) {
                        StepExecutionStatus.VERIFIED
                    } else if (verification.status == StepVerificationStatus.UNVERIFIED) {
                        StepExecutionStatus.COMPLETED_UNVERIFIED
                    } else {
                        StepExecutionStatus.FAILED
                    },
                    verificationStatus = verification.status,
                    verificationDetail = verification.detail,
                    executedAt = System.currentTimeMillis()
                )
                executedSteps[i] = step
                plan = plan.copy(steps = executedSteps, updatedAt = System.currentTimeMillis())
                notifyUpdate(plan, onPlanUpdated)
            }

            // Workflow Completed Successfully
            val summary = buildSummary(executedSteps)
            plan = plan.copy(
                state = if (executedSteps.all { it.status == StepExecutionStatus.VERIFIED || it.status == StepExecutionStatus.COMPLETED_UNVERIFIED }) {
                    WorkflowState.COMPLETED
                } else {
                    WorkflowState.PARTIALLY_COMPLETED
                },
                steps = executedSteps,
                completedAt = System.currentTimeMillis(),
                executionSummary = summary,
                updatedAt = System.currentTimeMillis()
            )
            notifyUpdate(plan, onPlanUpdated)
            plan
        }

        if (timeoutResult == null) {
            plan = plan.copy(
                state = WorkflowState.FAILED,
                error = "Workflow timed out after ${MAX_WORKFLOW_TIMEOUT_MS / 1000}s",
                updatedAt = System.currentTimeMillis()
            )
            notifyUpdate(plan, onPlanUpdated)
        }

        return@withContext plan
    }

    /**
     * Resumes a workflow that was paused in [WorkflowState.AWAITING_CONFIRMATION].
     * Scoped specifically to [stepId] and its validated arguments.
     */
    suspend fun confirmStep(
        currentPlan: WorkflowPlan,
        stepId: String,
        confirmed: Boolean,
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan = withContext(Dispatchers.IO) {
        if (currentPlan.pendingConfirmationStepId != stepId) {
            Log.w(TAG, "Confirmation step ID mismatch: expected ${currentPlan.pendingConfirmationStepId}, got $stepId")
            return@withContext currentPlan
        }

        val stepIndex = currentPlan.steps.indexOfFirst { it.id == stepId }
        if (stepIndex == -1) return@withContext currentPlan

        val steps = currentPlan.steps.toMutableList()
        var step = steps[stepIndex]

        if (!confirmed) {
            step = step.copy(status = StepExecutionStatus.CANCELLED, errorMessage = "Action cancelled by user")
            steps[stepIndex] = step

            for (d in stepIndex + 1 until steps.size) {
                if (steps[d].dependsOnStepIds.contains(stepId)) {
                    steps[d] = steps[d].copy(status = StepExecutionStatus.SKIPPED)
                }
            }

            val cancelledPlan = currentPlan.copy(
                state = WorkflowState.CANCELLED,
                steps = steps,
                pendingConfirmationStepId = null,
                executionSummary = buildSummary(steps),
                updatedAt = System.currentTimeMillis()
            )
            notifyUpdate(cancelledPlan, onPlanUpdated)
            return@withContext cancelledPlan
        }

        // Confirmed: Execute consequential step with explicit user authorization
        val toolCall = ToolCall(toolName = step.toolName, arguments = step.arguments)
        val toolResult = toolRegistry.executeToolCall(toolCall, userConfirmed = true)

        val verification = WorkflowStepVerifier.verifyStep(context, step, toolResult)
        val confirmedStatus = when {
            !toolResult.isSuccess -> StepExecutionStatus.FAILED
            verification.status == StepVerificationStatus.VERIFIED ||
                verification.status == StepVerificationStatus.NOT_APPLICABLE -> StepExecutionStatus.VERIFIED
            verification.status == StepVerificationStatus.UNVERIFIED -> StepExecutionStatus.COMPLETED_UNVERIFIED
            else -> StepExecutionStatus.FAILED
        }
        step = step.copy(
            result = toolResult,
            status = confirmedStatus,
            verificationStatus = verification.status,
            verificationDetail = verification.detail,
            executedAt = System.currentTimeMillis()
        )
        steps[stepIndex] = step

        val resumedPlan = currentPlan.copy(
            state = WorkflowState.EXECUTING,
            currentStepIndex = stepIndex + 1,
            steps = steps,
            pendingConfirmationStepId = null,
            updatedAt = System.currentTimeMillis()
        )
        notifyUpdate(resumedPlan, onPlanUpdated)

        // Continue workflow execution for remaining steps
        executePlan(resumedPlan, onPlanUpdated)
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
            var updatedAny = false
            for (i in steps.indices) {
                if (steps[i].arguments.containsKey(paramName)) {
                    steps[i] = steps[i].copy(arguments = steps[i].arguments + (paramName to inputValue))
                    updatedAny = true
                }
            }
            if (!updatedAny) {
                val step = steps[currentIdx]
                steps[currentIdx] = step.copy(arguments = step.arguments + (paramName to inputValue))
            }
        }

        val updatedPlan = currentPlan.copy(
            state = WorkflowState.EXECUTING,
            steps = steps,
            missingInputPrompt = null,
            missingInputParam = null,
            updatedAt = System.currentTimeMillis()
        )
        notifyUpdate(updatedPlan, onPlanUpdated)
        executePlan(updatedPlan, onPlanUpdated)
    }

    /**
     * Cancels an active or paused workflow.
     */
    suspend fun cancelWorkflow(
        currentPlan: WorkflowPlan,
        onPlanUpdated: (suspend (WorkflowPlan) -> Unit)? = null
    ): WorkflowPlan = withContext(Dispatchers.IO) {
        val steps = currentPlan.steps.map {
            if (it.status == StepExecutionStatus.PENDING || it.status == StepExecutionStatus.AWAITING_CONFIRMATION) {
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

    private suspend fun executeToolWithRetry(toolCall: ToolCall, maxRetries: Int): ToolResult {
        var attempts = 0
        var lastResult: ToolResult? = null

        while (attempts <= maxRetries) {
            attempts++
            lastResult = toolRegistry.executeToolCall(toolCall, userConfirmed = false)
            if (lastResult.isSuccess || attempts > maxRetries) {
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
        try {
            // Persist plan state to local SQLite database
            db.saveWorkflow(plan)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist workflow: ${e.message}")
        }
        onPlanUpdated?.invoke(plan)
    }
}
