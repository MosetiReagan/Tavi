package com.example.tavi.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConfirmationParserTest {

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
