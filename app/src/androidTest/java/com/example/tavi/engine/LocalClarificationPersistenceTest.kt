package com.example.tavi.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tavi.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalClarificationPersistenceTest {
    @Test fun slotsPersistAndStayScopedToTheirConversation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conversation = "clarification-${UUID.randomUUID()}"
        val store = LocalClarificationStore(context)
        val engine = LocalRequestEngine()
        val value = (engine.route("send sms to Alice") as LocalRequestEngine.Decision.Reply).clarification!!
        try {
            store.save(conversation, value)
            assertEquals(value, LocalClarificationStore(context).get(conversation))
            assertNull(store.get("$conversation-other"))
            assertNull(store.get(conversation, value.createdAt + LocalClarification.TTL_MS))
        } finally { store.clear(conversation) }
    }

    @Test fun completingMessageDetailsStillRequiresExplicitConfirmation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = LocalRequestEngine()
        val pending = (engine.route("send sms to +254700000000") as LocalRequestEngine.Decision.Reply).clarification!!
        val call = (engine.route("Hello", clarification = pending) as LocalRequestEngine.Decision.Tools).calls.single()
        val registry = ToolRegistry.getInstance(context)
        assertTrue(registry.getTool(call.toolName)!!.requiresConfirmation)
        assertFalse(registry.executeToolCall(call).isSuccess)
    }
}
