package com.example.tavi.context

import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import org.junit.Assert.*
import org.junit.Test

class PromptBudgetTest {
    @Test fun oversizedNewestHistoryDoesNotSlipThrough() {
        val history = listOf(Message(role = MessageRole.USER, content = "x".repeat(9000)),
            Message(role = MessageRole.ASSISTANT, content = "y".repeat(9000)))
        val prompt = ContextBudgetManager.buildGemma3Prompt(history, "Hello", "You are Tavi.")
        assertTrue(ContextBudgetManager.estimateTokens(prompt) <= ContextBudgetManager.MAX_CONTEXT_TOKENS - ContextBudgetManager.RESERVED_GENERATION_TOKENS)
        assertFalse(prompt.contains("x".repeat(500)))
    }
    @Test fun nonAsciiAndCurrentInputCountTowardActualLimit() {
        assertTrue(ContextBudgetManager.estimateTokens("你好你好") > ContextBudgetManager.estimateTokens("test"))
        try {
            ContextBudgetManager.buildGemma3Prompt(emptyList(), "好".repeat(2000), "You are Tavi.")
            fail("Oversized current input must be rejected")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("context limit")) }
    }
    @Test fun fullSystemPromptIsCountedAndNeverTruncated() {
        try {
            ContextBudgetManager.buildGemma3Prompt(emptyList(), "Hi", "instructions ".repeat(500))
            fail("Oversized instructions must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
