package com.example.tavi.workflow

import android.content.ContextWrapper
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.ToolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DurableWorkflowTest {
    private val context = object : ContextWrapper(null) { override fun getPackageName() = "com.tavi.assistant" }
    private fun engine(calls: MutableList<Pair<ToolCall, Boolean>>, saved: MutableList<WorkflowPlan>) = WorkflowEngine(
        context, ToolRegistry(context), persist = { saved.add(it) },
        execute = { call, confirmed -> calls.add(call to confirmed); ToolResult(call.id, call.toolName, "{}") },
        verify = { _, _ -> StepVerificationResult(StepVerificationStatus.UNVERIFIED, "Dispatched") })

    @Test fun confirmationPreservesAndResumesRemainingSteps() = runTest {
        val calls = mutableListOf<Pair<ToolCall, Boolean>>()
        val saved = mutableListOf<WorkflowPlan>()
        val steps = listOf(
            WorkflowStep(stepIndex = 0, title = "Read", toolName = "clock_query_time", arguments = emptyMap()),
            WorkflowStep(stepIndex = 1, title = "Send", toolName = "messages_send_sms", arguments = emptyMap(), riskLevel = ToolRiskLevel.HIGH_CONSEQUENTIAL),
            WorkflowStep(stepIndex = 2, title = "Note", toolName = "note_create", arguments = emptyMap()))
        val runner = engine(calls, saved)
        val paused = runner.executePlan(WorkflowPlan(title = "Task", userQuery = "task", conversationId = "chat-2", steps = steps))
        assertEquals(WorkflowState.AWAITING_CONFIRMATION, paused.state)
        assertEquals(1, calls.size)
        assertEquals(paused, runner.confirmStep(paused, "wrong-step", true))
        // Restart from persisted snapshot; no host approval is retained.
        assertEquals(WorkflowState.AWAITING_CONFIRMATION, engine(calls, saved).executePlan(saved.last()).state)
        val completed = runner.confirmStep(paused, steps[1].id, true)
        assertEquals(WorkflowState.COMPLETED, completed.state)
        assertEquals(3, calls.size)
        assertTrue(calls[1].second)
        assertFalse(calls[2].second)
        assertEquals(steps[1].idempotencyKey, calls[1].first.id)
        assertEquals("chat-2", saved.last().conversationId)
    }

    @Test fun rejectedConfirmationCancelsRemainingActions() = runTest {
        val calls = mutableListOf<Pair<ToolCall, Boolean>>()
        val runner = engine(calls, mutableListOf())
        val step = WorkflowStep(stepIndex = 0, title = "Delete", toolName = "calendar_delete_event", arguments = emptyMap())
        val paused = runner.executePlan(WorkflowPlan(title = "Task", userQuery = "delete", steps = listOf(step)))
        assertEquals(WorkflowState.CANCELLED, runner.confirmStep(paused, step.id, false).state)
        assertTrue(calls.isEmpty())
    }
    @Test fun writesAreNeverRetriedOnFailure() = runTest {
        var attempts = 0
        val runner = WorkflowEngine(context, ToolRegistry(context), persist = {},
            execute = { call, _ -> attempts++; ToolResult(call.id, call.toolName, "{}", false) },
            verify = { _, _ -> StepVerificationResult(StepVerificationStatus.FAILED, "failed") })
        val step = WorkflowStep(stepIndex = 0, title = "Write", toolName = "note_create", arguments = emptyMap(), maxRetries = 2)
        runner.executePlan(WorkflowPlan(title = "Task", userQuery = "write", steps = listOf(step)))
        assertEquals(1, attempts)
    }
}
