package com.example.tavi.context

import com.example.tavi.data.MessageToolCodec
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolExecutionState
import org.junit.Assert.*
import org.junit.Test

class MessageToolCodecTest {
    @Test fun preservesEvidenceAndNestedArgumentsWithoutApproval() {
        val call = ToolCall("call1", "calendar_update_event", mapOf("eventId" to 42,
            "nested" to mapOf("values" to listOf(true, null, "title")), "__user_confirmed" to true))
        val restored = MessageToolCodec.decodeCalls(MessageToolCodec.encodeCalls(listOf(call))).single()
        assertEquals("call1", restored.id)
        assertEquals(42, restored.arguments["eventId"])
        assertFalse(restored.arguments.containsKey("__user_confirmed"))
        assertEquals(listOf(true, null, "title"), (restored.arguments["nested"] as Map<*, *>)["values"])
        val result = ToolResult("call1", call.toolName, "{\"eventId\":42}", false,
            "Permission needed", state = ToolExecutionState.PERMISSION_REQUIRED, requiredPermission = "WRITE_CALENDAR")
        assertEquals(result, MessageToolCodec.decodeResults(MessageToolCodec.encodeResults(listOf(result))).single())
    }
    @Test fun toleratesLegacyEmptyAndCorruptRecords() {
        assertTrue(MessageToolCodec.decodeCalls(null).isEmpty())
        assertTrue(MessageToolCodec.decodeResults("broken").isEmpty())
    }
}
