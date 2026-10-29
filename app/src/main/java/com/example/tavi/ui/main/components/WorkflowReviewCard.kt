package com.example.tavi.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tavi.theme.BrandAccent
import com.example.tavi.theme.BrandPrimary
import com.example.tavi.theme.SuccessGreen
import com.example.tavi.theme.WarningAmber
import com.example.tavi.workflow.PlanSource
import com.example.tavi.workflow.StepExecutionStatus
import com.example.tavi.workflow.WorkflowPlan
import com.example.tavi.workflow.WorkflowState

@Composable
fun WorkflowReviewCard(
    workflow: WorkflowPlan,
    onConfirm: (String) -> Unit,
    onCancelConfirm: (String) -> Unit,
    onCancelWorkflow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateColor = when (workflow.state) {
        WorkflowState.COMPLETED -> SuccessGreen
        WorkflowState.EXECUTING, WorkflowState.VERIFYING -> BrandPrimary
        WorkflowState.AWAITING_CONFIRMATION -> WarningAmber
        WorkflowState.AWAITING_USER_INPUT -> BrandAccent
        WorkflowState.PARTIALLY_COMPLETED -> WarningAmber
        WorkflowState.FAILED -> MaterialTheme.colorScheme.error
        WorkflowState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        WorkflowState.PLANNING -> BrandPrimary
    }

    val stateText = when (workflow.state) {
        WorkflowState.COMPLETED -> "Completed & Verified"
        WorkflowState.EXECUTING -> "Executing Step ${workflow.currentStepIndex + 1}/${workflow.steps.size}"
        WorkflowState.VERIFYING -> "Verifying Result"
        WorkflowState.AWAITING_CONFIRMATION -> "Requires Confirmation"
        WorkflowState.AWAITING_USER_INPUT -> "Input Required"
        WorkflowState.PARTIALLY_COMPLETED -> "Partially Completed"
        WorkflowState.FAILED -> "Workflow Failed"
        WorkflowState.CANCELLED -> "Cancelled"
        WorkflowState.PLANNING -> "Planning"
    }

    val planSourceText = when (workflow.planSource) {
        PlanSource.DETERMINISTIC -> "Fast-Path Rule"
        PlanSource.GEMMA_ON_DEVICE -> "On-Device AI Plan"
        PlanSource.USER_ROUTINE -> "Automated Routine"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = planSourceText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = stateColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stateText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar
            if (workflow.state == WorkflowState.EXECUTING || workflow.state == WorkflowState.VERIFYING) {
                val progress = if (workflow.steps.isNotEmpty()) {
                    (workflow.currentStepIndex.toFloat() / workflow.steps.size.toFloat()).coerceIn(0.1f, 0.95f)
                } else 0.5f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = BrandPrimary,
                    trackColor = BrandPrimary.copy(alpha = 0.2f)
                )
            }

            // Steps list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                workflow.steps.forEachIndexed { idx, step ->
                    val isPendingStep = workflow.state == WorkflowState.AWAITING_CONFIRMATION && step.id == workflow.pendingConfirmationStepId
                    val stepBg = if (isPendingStep) {
                        WarningAmber.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = stepBg,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (step.isReadOnly) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (step.isReadOnly) "Read-Only" else "Modifies Data",
                                tint = if (step.isReadOnly) BrandPrimary else WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${idx + 1}. ${step.title}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (step.isReadOnly) "Read-Only" else "Action",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (step.requiredPermission != null) {
                                    Text(
                                        text = "Permission: ${step.requiredPermission}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                if (step.verificationDetail != null) {
                                    Text(
                                        text = step.verificationDetail,
                                        fontSize = 11.sp,
                                        color = SuccessGreen
                                    )
                                } else if (step.errorMessage != null) {
                                    Text(
                                        text = step.errorMessage,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Status badge / icon
                            when (step.status) {
                                StepExecutionStatus.VERIFIED -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.COMPLETED_UNVERIFIED -> Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.EXECUTING, StepExecutionStatus.VERIFYING -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandPrimary
                                )
                                StepExecutionStatus.AWAITING_CONFIRMATION -> Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Confirmation Needed",
                                    tint = WarningAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.FAILED -> Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                StepExecutionStatus.PENDING -> Text(
                                    "Pending",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                StepExecutionStatus.SKIPPED -> Text(
                                    "Skipped",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                StepExecutionStatus.CANCELLED -> Text(
                                    "Cancelled",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Awaiting Confirmation UI
            if (workflow.state == WorkflowState.AWAITING_CONFIRMATION && workflow.pendingConfirmationStepId != null) {
                val step = workflow.steps.find { it.id == workflow.pendingConfirmationStepId }
                Surface(
                    color = WarningAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Action Confirmation Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = WarningAmber
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Please verify the details below before proceeding:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        step?.arguments?.filter { !it.key.startsWith("__") }?.forEach { (key, value) ->
                            val cleanKey = key.replace("_", " ").replaceFirstChar { it.uppercase() }
                            Text(
                                text = "• $cleanKey: $value",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onCancelConfirm(workflow.pendingConfirmationStepId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onConfirm(workflow.pendingConfirmationStepId) },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Text("Confirm & Proceed", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Awaiting User Input prompt
            if (workflow.state == WorkflowState.AWAITING_USER_INPUT && workflow.missingInputPrompt != null) {
                Surface(
                    color = BrandAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Additional Detail Needed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BrandAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = workflow.missingInputPrompt,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Dismiss / Cancel Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (workflow.state == WorkflowState.COMPLETED || workflow.state == WorkflowState.FAILED || workflow.state == WorkflowState.CANCELLED || workflow.state == WorkflowState.PARTIALLY_COMPLETED) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", fontSize = 12.sp)
                    }
                } else if (workflow.state == WorkflowState.EXECUTING || workflow.state == WorkflowState.VERIFYING) {
                    TextButton(onClick = onCancelWorkflow) {
                        Text("Cancel Workflow", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
