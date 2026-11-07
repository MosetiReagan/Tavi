package com.example.tavi.engine

import org.junit.Assert.*
import org.junit.Test

class LocalClarificationTest {
    private val engine = LocalRequestEngine()
    private val now = 1_000_000L
    private fun pending(query: String, language: String = "en") =
        (engine.route(query, language, now = now) as LocalRequestEngine.Decision.Reply).clarification!!
    private fun resume(answer: String, value: LocalClarification, time: Long = now + 1000) = engine.route(answer, "auto", value, time)

    @Test fun bareTimeCompletesThePendingAlarm() {
        val call = (resume("7:30 AM", pending("set alarm")) as LocalRequestEngine.Decision.Tools).calls.single()
        assertEquals("alarm_create", call.toolName)
        assertEquals(7, call.arguments["hour"])
        assertEquals(30, call.arguments["minutes"])
    }
    @Test fun invalidRepliesPreserveTheOriginalExpiry() {
        val original = pending("set alarm")
        val retry = resume("25:90", original) as LocalRequestEngine.Decision.Reply
        assertEquals(original.createdAt, retry.clarification!!.createdAt)
        assertFalse(retry.clarification!!.isCurrent(now + LocalClarification.TTL_MS))
    }
    @Test fun spokenEnglishAndKiswahiliDurationsAreUnderstood() {
        val value = pending("weka kipima muda", "sw")
        for (answer in listOf("five minutes", "dakika tano", "5 minutes")) {
            val call = (resume(answer, value) as LocalRequestEngine.Decision.Tools).calls.single()
            assertEquals(300, call.arguments["seconds"])
        }
    }
    @Test fun recipientAndBodyAreCollectedSeparatelyWithoutInventingEither() {
        val recipient = pending("send a message")
        assertEquals(ClarificationSlot.MESSAGE_RECIPIENT, recipient.slot)
        val askBody = resume("Alice", recipient) as LocalRequestEngine.Decision.Reply
        assertEquals(ClarificationSlot.MESSAGE_BODY, askBody.clarification!!.slot)
        val call = (resume("Hello! Bring Tea.", askBody.clarification!!) as LocalRequestEngine.Decision.Tools).calls.single()
        assertEquals(mapOf("recipient" to "Alice", "body" to "Hello! Bring Tea."), call.arguments)
        assertEquals("messages_send_sms", call.toolName)
        assertFalse(call.arguments.keys.any { it.startsWith("__") })
    }
    @Test fun knownMessageBodySurvivesAnAskedForRecipient() {
        val call = (resume("José", pending("send sms saying Meet me at 07:30.")) as LocalRequestEngine.Decision.Tools).calls.single()
        assertEquals("José", call.arguments["recipient"])
        assertEquals("Meet me at 07:30.", call.arguments["body"])
    }
    @Test fun draftsRemainDraftsAfterClarification() {
        val call = (resume("Hello", pending("draft a message to Alice")) as LocalRequestEngine.Decision.Tools).calls.single()
        assertEquals("messages_draft", call.toolName)
        val sw = (engine.route("andaa ujumbe kwa Alice ukisema Hello!", "sw") as LocalRequestEngine.Decision.Tools).calls.single()
        assertEquals("messages_draft", sw.toolName)
        assertEquals("Hello!", sw.arguments["body"])
    }
    @Test fun cancellationAndLiteralNoAreDistinct() {
        val value = pending("send sms to Alice")
        assertNull((resume("no", value) as LocalRequestEngine.Decision.Reply).clarification)
        assertNull((resume("hapana", value) as LocalRequestEngine.Decision.Reply).clarification)
        assertEquals("No", (resume("say No", value) as LocalRequestEngine.Decision.Tools).calls.single().arguments["body"])
    }
    @Test fun aNewCommandReplacesTheOldRequest() {
        val call = (resume("what time is it", pending("set alarm")) as LocalRequestEngine.Decision.Tools).calls.single()
        assertEquals("clock_query_time", call.toolName)
    }
    @Test fun expiredRequestsCannotExecuteFromShortAnswers() {
        assertTrue(resume("7:30 AM", pending("set alarm"), now + LocalClarification.TTL_MS) is LocalRequestEngine.Decision.Reply)
        assertFalse(pending("set alarm").isCurrent(now - 1))
    }
    @Test fun persistedSlotsRoundTripAndRejectDifferentTools() {
        val value = pending("tuma sms kwa Alice", "sw")
        assertEquals(value, LocalClarificationCodec.decode(LocalClarificationCodec.encode(value)))
        assertNull(LocalClarificationCodec.decode(LocalClarificationCodec.encode(value).replace("messages_send_sms", "phone_call")))
        assertNull(LocalClarificationCodec.decode("{}"))
    }
}
