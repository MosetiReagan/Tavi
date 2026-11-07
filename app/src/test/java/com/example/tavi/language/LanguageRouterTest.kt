package com.example.tavi.language

import com.example.tavi.engine.DeterministicAssistantEngine
import com.example.tavi.voice.VoiceConfirmationDecision
import com.example.tavi.voice.VoiceConfirmationParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LanguageRouterTest {
    @Test fun preservesNamesAndMessageBodies() {
        val original = "tuma message to José saying Usibadilishe maneno yangu!"
        assertEquals("send sms to José saying Usibadilishe maneno yangu!", LanguageRouter.route(original))
        assertEquals("take a note: Résumé ya mkutano", LanguageRouter.route("andika note: Résumé ya mkutano"))
        assertEquals("Unknown free-form Kiswahili", LanguageRouter.route("Unknown free-form Kiswahili"))
    }
    @Test fun supportsCalendarAndDocuments() = runTest {
        val engine = DeterministicAssistantEngine()
        assertEquals("calendar_list_events", engine.proposeToolCalls("onyesha ratiba yangu kesho", emptyList()).first().toolName)
        val call = engine.proposeToolCalls("tafuta nyaraka mkutano Nairobi", emptyList()).first()
        assertEquals("documents_search", call.toolName)
        assertEquals("mkutano Nairobi", call.arguments["query"])
    }
    @Test fun swahiliNegationAlwaysWins() {
        assertEquals(VoiceConfirmationDecision.AFFIRMATIVE, VoiceConfirmationParser.parse("ndiyo"))
        assertEquals(VoiceConfirmationDecision.NEGATIVE, VoiceConfirmationParser.parse("ndiyo lakini usitume"))
        assertEquals(VoiceConfirmationDecision.NEGATIVE, VoiceConfirmationParser.parse("hapana endelea"))
        assertEquals(VoiceConfirmationDecision.AMBIGUOUS, VoiceConfirmationParser.parse("nadhani ndiyo kesho"))
    }
}
