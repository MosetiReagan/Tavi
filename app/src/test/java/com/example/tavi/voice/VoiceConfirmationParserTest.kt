package com.example.tavi.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConfirmationParserTest {

    @Test
    fun negationCannotAuthorizeAnAction() {
        for (phrase in listOf("don't send it", "no, don't do it", "do not proceed",
            "yes, actually no", "don’t send it", "okay cancel")) {
            assertEquals(phrase, VoiceConfirmationDecision.NEGATIVE, VoiceConfirmationParser.parse(phrase))
        }
    }

    @Test
    fun incidentalApprovalWordsAreNotConsent() {
        for (phrase in listOf("did I say yes", "what happens if I confirm", "sure about that",
            "yesterday", "send it to someone else")) {
            assertEquals(phrase, VoiceConfirmationDecision.AMBIGUOUS, VoiceConfirmationParser.parse(phrase))
        }
        assertEquals(VoiceConfirmationDecision.AFFIRMATIVE, VoiceConfirmationParser.parse("yes please"))
    }

    @Test
    fun testAffirmativeKeywords() {
        val affirmations = listOf(
            "yes",
            "YES",
            "yeah",
            "Yep",
            "sure",
            "do it",
            "confirm",
            "go ahead",
            "proceed",
            "okay",
            "ok",
            "please do",
            "send it"
        )

        for (phrase in affirmations) {
            val decision = VoiceConfirmationParser.parse(phrase)
            assertEquals("Expected AFFIRMATIVE for '$phrase'", VoiceConfirmationDecision.AFFIRMATIVE, decision)
        }
    }

    @Test
    fun testNegativeKeywords() {
        val negations = listOf(
            "no",
            "NO",
            "nope",
            "stop",
            "cancel",
            "don't",
            "dont",
            "never mind",
            "nevermind",
            "abort",
            "dismiss",
            "do not"
        )

        for (phrase in negations) {
            val decision = VoiceConfirmationParser.parse(phrase)
            assertEquals("Expected NEGATIVE for '$phrase'", VoiceConfirmationDecision.NEGATIVE, decision)
        }
    }

    @Test
    fun testAmbiguousKeywords() {
        val ambiguous = listOf(
            null,
            "",
            "   ",
            "what time is it",
            "who are you",
            "tell me a joke",
            "maybe later",
            "how is the weather"
        )

        for (phrase in ambiguous) {
            val decision = VoiceConfirmationParser.parse(phrase)
            assertEquals("Expected AMBIGUOUS for '$phrase'", VoiceConfirmationDecision.AMBIGUOUS, decision)
        }
    }
}
