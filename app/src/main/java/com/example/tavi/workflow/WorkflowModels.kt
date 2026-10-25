package com.example.tavi.workflow

import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import java.util.UUID

/**
 * Explicit workflow states representing the complete lifecycle of a multi-step task.
 */
enum class WorkflowState {
    PLANNING,
    AWAITING_USER_INPUT,
    AWAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Execution status for individual steps within a workflow plan.
 */
enum class StepExecutionStatus {
    PENDING,
    EXECUTING,
    AWAITING_CONFIRMATION,
    VERIFYING,
    VERIFIED,
    COMPLETED_UNVERIFIED,
    SKIPPED,
    FAILED,
    CANCELLED
}

/**
 * Result of post-execution state verification.
 */
enum class StepVerificationStatus {
    NOT_APPLICABLE, // Read-only query steps (e.g., search, query)
    PENDING,
    VERIFIED,       // Confirmed by reading back native state
    UNVERIFIED,     // Tool succeeded, but native API does not expose read-back verification
    FAILED          // Read-back confirmed the requested outcome did NOT occur
}

/**
 * Clear attribution for plan generation: honest distinction between deterministic
 * native intent routing and on-device Gemma 3 1B LLM planning.
 */
enum class PlanSource {
    DETERMINISTIC,
    GEMMA_ON_DEVICE,
    USER_ROUTINE
}

/**
 * Dynamic parameter binding allowing downstream steps to consume validated
 * outputs from upstream steps.
 * E.g., step 1 passes resolved contact phone number into step 2's SMS recipient.
 */
data class DynamicBinding(
    val targetArg: String,
    val fromStepId: String,
    val fromPropertyPath: String,
    val defaultValue: String? = null,
    val transform: String? = null
)

/**
 * Typed individual action step in a multi-step workflow.
 */
data class WorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val stepIndex: Int,
    val title: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val dynamicBindings: List<DynamicBinding> = emptyList(),
    val isReadOnly: Boolean = false,
    val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW,
    val requiredPermission: String? = null,
    val status: StepExecutionStatus = StepExecutionStatus.PENDING,
    val verificationStatus: StepVerificationStatus = StepVerificationStatus.NOT_APPLICABLE,
    val result: ToolResult? = null,
    val verificationDetail: String? = null,
    val errorMessage: String? = null,
    val retriesAttempted: Int = 0,
    val maxRetries: Int = 1,
    val dependsOnStepIds: List<String> = emptyList(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val executedAt: Long? = null
)

/**
 * Complete structured workflow task plan with bounded execution parameters.
 */
data class WorkflowPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val userQuery: String,
    val state: WorkflowState = WorkflowState.PLANNING,
    val planSource: PlanSource = PlanSource.DETERMINISTIC,
    val steps: List<WorkflowStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val missingInputPrompt: String? = null,
    val missingInputParam: String? = null,
    val pendingConfirmationStepId: String? = null,
    val error: String? = null,
    val executionSummary: WorkflowExecutionSummary? = null
)

/**
 * Comprehensive truthful execution summary distinguishing verified outcomes
 * from unverified actions.
 */
data class WorkflowExecutionSummary(
    val totalSteps: Int,
    val completedSteps: Int,
    val verifiedSteps: Int,
    val failedSteps: Int,
    val skippedSteps: Int,
    val isFullyVerified: Boolean,
    val humanReadableText: String,
    val verifiedOutcomes: List<String> = emptyList(),
    val unverifiedOutcomes: List<String> = emptyList()
)

/**
 * Outcome of a step verifier check.
 */
data class StepVerificationResult(
    val status: StepVerificationStatus,
    val detail: String,
    val isConfirmedState: Boolean = false
)
