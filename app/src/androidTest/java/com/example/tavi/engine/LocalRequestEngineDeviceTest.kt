package com.example.tavi.engine

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tavi.data.TaviDatabase
import com.example.tavi.language.AssistantLanguage
import com.example.tavi.tools.ToolRegistry
import com.example.tavi.ui.main.MainScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRequestEngineDeviceTest {
    @Test fun modelFreeRequestUsesNativeClockAndCannotSendUnconfirmedSms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = LocalRequestEngine()
        val registry = ToolRegistry.getInstance(context)
        val clock = (engine.route("saa ngapi") as LocalRequestEngine.Decision.Tools).calls.single()
        val result = registry.executeToolCall(clock)
        assertTrue(result.isSuccess)
        assertTrue(JSONObject(result.resultJson).getString("currentTime").isNotBlank())
        val sms = (engine.route("send SMS to +254700000000 saying Test") as LocalRequestEngine.Decision.Tools).calls.single()
        assertTrue(registry.getTool(sms.toolName)!!.requiresConfirmation)
        assertFalse(registry.executeToolCall(sms).isSuccess)
    }

    @Test fun chatRoutesCommonRequestsAndPersistsClarificationBeforeModelPlanning() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = TaviDatabase.getInstance(app)
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val previousLanguage = AssistantLanguage.selected(app)
        var testConversation: String? = null
        try {
            AssistantLanguage.set(app, "en")
            val vm = withContext(Dispatchers.Main) {
                ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(app))[MainScreenViewModel::class.java]
            }
            withTimeout(5000) { while (vm.uiState.value.conversations.isEmpty()) delay(20) }
            val oldConversation = vm.uiState.value.currentConversationId
            withContext(Dispatchers.Main) { vm.startNewChat() }
            withTimeout(5000) { while (vm.uiState.value.currentConversationId == oldConversation || vm.uiState.value.isStreaming) delay(20) }
            testConversation = vm.uiState.value.currentConversationId

            withContext(Dispatchers.Main) { vm.sendMessage("set alarm for tomorrow") }
            withTimeout(5000) { while (vm.uiState.value.isStreaming) delay(20) }
            val clarification = vm.uiState.value.messages.last()
            assertTrue(clarification.content.contains("exact alarm time"))
            assertTrue(clarification.toolCalls.isEmpty())
            assertEquals(clarification.content, db.getMessagesForConversation(testConversation).last().content)

            withContext(Dispatchers.Main) { vm.sendMessage("what time is it") }
            withTimeout(5000) { while (vm.uiState.value.isStreaming) delay(20) }
            val answer = vm.uiState.value.messages.last()
            assertTrue(answer.toolResults.any { it.toolName == "clock_query_time" && it.isSuccess })
            assertTrue(answer.content.isNotBlank())
            assertFalse(vm.uiState.value.statusBanner?.contains("Loading") == true)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            testConversation?.let {
                db.deleteConversation(it)
                db.writableDatabase.delete("workflows", "conversation_id = ?", arrayOf(it))
            }
            AssistantLanguage.set(app, previousLanguage)
        }
    }
}
