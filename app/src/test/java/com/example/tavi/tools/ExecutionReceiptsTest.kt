package com.example.tavi.tools

import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExecutionReceiptsTest {
    private class Store : ExecutionReceiptStore {
        val entries = mutableMapOf<String, ExecutionReceipt>()
        override fun claim(id: String, fingerprint: String): Boolean {
            if (entries.containsKey(id)) return false
            entries[id] = ExecutionReceipt(fingerprint, null)
            return true
        }
        override fun read(id: String) = entries[id]
        override fun complete(id: String, result: ToolResult) { entries[id] = entries.getValue(id).copy(result = result) }
    }
    @Test fun completedWriteIsReusedAcrossExecutorInstances() = runTest {
        val store = Store()
        val call = ToolCall("stable", "messages_send_sms", mapOf("body" to "hello"))
        var writes = 0
        val action: suspend () -> ToolResult = { writes++; ToolResult(call.id, call.toolName, "{}") }
        ExecutionReceipts(store).execute(call, action)
        assertTrue(ExecutionReceipts(store).execute(call, action).isSuccess)
        assertEquals(1, writes)
        assertFalse(ExecutionReceipts(store).execute(call.copy(arguments = mapOf("body" to "changed")), action).isSuccess)
        assertEquals(1, writes)
    }
    @Test fun interruptedWriteCannotBeReplayed() = runTest {
        val store = Store()
        val call = ToolCall("stable", "calendar_create_event", emptyMap())
        try { ExecutionReceipts(store).execute(call) { throw CancellationException("stopped") } }
        catch (_: CancellationException) { }
        var retried = false
        val result = ExecutionReceipts(store).execute(call) { retried = true; ToolResult(call.id, call.toolName, "{}") }
        assertFalse(retried)
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("may already have run"))
    }
    @Test fun fingerprintIsIndependentOfMapOrder() {
        val a = ToolCall("a", "note_create", linkedMapOf("title" to "x", "content" to "y"))
        assertEquals(ExecutionReceipts.fingerprint(a), ExecutionReceipts.fingerprint(a.copy(arguments = linkedMapOf("content" to "y", "title" to "x"))))
    }
}
