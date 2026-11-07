package com.example.tavi.engine

import com.example.tavi.model.ToolCall
import com.example.tavi.tools.ToolArgumentValidator
import com.example.tavi.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRequestEngineTest {
    private val engine = LocalRequestEngine()
    private fun call(query: String): ToolCall = (engine.route(query) as LocalRequestEngine.Decision.Tools).calls.single()

    @Test fun englishAndKiswahiliCommandsRouteWithoutWeights() {
        mapOf("What time is it?" to "clock_query_time", "saa ngapi sasa?" to "clock_query_time",
            "Please check battery" to "device_get_status", "kiwango cha betri" to "device_get_status",
            "am I online?" to "network_get_status", "onyesha vikumbusho" to "reminder_list",
            "show my notes" to "notes_list", "tafuta nyaraka safari" to "documents_search")
            .forEach { (query, tool) -> assertEquals(query, tool, call(query).toolName) }
    }

    @Test fun explanatoryNegativeAndCompoundPromptsDoNotTriggerAnAction() {
        listOf("Explain how to turn on the flashlight", "Do not turn on the flashlight", "If I open wifi settings, what happens?",
            "Tell me a story about a timer", "\"turn on flashlight\"", "check battery and tell me the time",
            "calculate 2 + 2 and send a message", "What is the capital of Kenya?")
            .forEach { assertEquals(it, LocalRequestEngine.Decision.Delegate, engine.route(it)) }
        assertTrue(engine.route("set an alarm for 07:30 and a timer for 5 minutes") is LocalRequestEngine.Decision.Reply)
    }

    @Test fun alarmTimesAreNormalizedAndInvalidTimesNeedClarification() {
        assertEquals(0, call("set an alarm for 12:30 AM").arguments["hour"])
        assertEquals(12, call("set an alarm for 12 PM").arguments["hour"])
        assertEquals(23, call("set alarm at 23:59").arguments["hour"])
        listOf("set alarm at 25:00", "set alarm at 07:99", "set alarm at 13 PM", "set alarm for tomorrow",
            "set alarm at 9999999999:30").forEach { assertTrue(it, engine.route(it) is LocalRequestEngine.Decision.Reply) }
    }

    @Test fun timerConversionCannotOverflowOrScheduleNegativeDurations() {
        assertEquals(300, call("set a timer for 5 minutes").arguments["seconds"])
        assertEquals(7200, call("timer for 2 hours").arguments["seconds"])
        assertEquals(30, call("timer for 30 seconds").arguments["seconds"])
        listOf("timer for 0 minutes", "timer for 999999999999999999999 hours", "timer for 2147483647 hours",
            "set a timer for -1 minutes").forEach { assertTrue(it, engine.route(it) is LocalRequestEngine.Decision.Reply) }
    }

    @Test fun messageTextAndUnicodeRecipientsStayIntact() {
        val body = "Jambo! Meet me at 07:30, and bring the PDF."
        val sms = call("tuma sms kwa José ukisema $body")
        assertEquals("messages_send_sms", sms.toolName)
        assertEquals("José", sms.arguments["recipient"])
        assertEquals(body, sms.arguments["body"])
        assertEquals("messages_draft", call("draft a message to Mary saying $body").toolName)
    }

    @Test fun missingMessageDetailsNeverInventTextOrARecipient() {
        listOf("send sms", "send sms to Alice", "send a message", "draft a message to Mary")
            .forEach { assertTrue(it, engine.route(it) is LocalRequestEngine.Decision.Reply) }
    }

    @Test fun notesAndSearchPayloadsPreserveCaseAndPunctuation() {
        val note = call("hifadhi note: Buy milk, bread and Tea!")
        assertEquals("Buy milk, bread and Tea!", note.arguments["content"])
        assertEquals(note.arguments["content"], note.arguments["title"])
        assertEquals("Project Alpha", call("search documents for Project Alpha").arguments["query"])
        assertEquals("José", call("find contact José").arguments["name"])
    }

    @Test fun repliesRespectSelectedLanguageAndBoundInputWork() {
        assertTrue((engine.route("help", "sw") as LocalRequestEngine.Decision.Reply).text.startsWith("Mimi ni Tavi"))
        assertTrue((engine.route("msaada") as LocalRequestEngine.Decision.Reply).text.startsWith("Mimi ni Tavi"))
        assertTrue((engine.route("msaada", "en") as LocalRequestEngine.Decision.Reply).text.startsWith("I'm Tavi"))
        assertTrue(engine.route(" ") is LocalRequestEngine.Decision.Reply)
        assertTrue(engine.route("send sms to Alice saying " + "x".repeat(4000)) is LocalRequestEngine.Decision.Reply)
    }

    @Test fun everySupportedCommandProducesArgumentsAcceptedByItsRegisteredTool() {
        val registry = ToolRegistry(android.content.ContextWrapper(null))
        listOf("check battery", "what time is it", "show calendar tomorrow", "open settings", "open wi-fi settings",
            "turn off flashlight", "timer for 5 minutes", "set alarm at 07:30", "calculate 5 * 9", "take a note: A note",
            "send sms to Alice saying Hello", "draft a message to Alice saying Hello", "find contact Alice", "search documents receipt")
            .forEach { query ->
                val call = call(query)
                val tool = registry.getTool(call.toolName)!!
                assertEquals(query, null, ToolArgumentValidator.validate(tool.parametersSchema, call.arguments))
            }
    }
}
