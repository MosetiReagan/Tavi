package com.example.tavi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    /**
     * Simulated voice state coordinator to verify state machine transitions
     * independent of Android framework JNI audio binders.
     */
    class TestVoiceStateCoordinator(
        var continuousVoiceEnabled: Boolean = true,
        var bargeInEnabled: Boolean = true,
        var followupWindowSec: Int = 5
    ) {
        var currentMode = VoiceSessionMode.IDLE
        var isTtsPlaying = false
        var ttsStoppedCount = 0
        var remainingFollowupSec = 0

        fun onMicTriggered() {
            currentMode = VoiceSessionMode.LISTENING
        }

        fun onUserFinishedSpeaking() {
            currentMode = VoiceSessionMode.PROCESSING
        }

        fun onAssistantStartsSpeaking() {
            isTtsPlaying = true
            currentMode = VoiceSessionMode.SPEAKING
        }

        fun onAssistantSpeechFinished() {
            isTtsPlaying = false
            if (continuousVoiceEnabled) {
                currentMode = VoiceSessionMode.AWAITING_FOLLOWUP
                remainingFollowupSec = followupWindowSec
            } else {
                currentMode = VoiceSessionMode.IDLE
                remainingFollowupSec = 0
            }
        }

        fun onFollowupTick() {
            if (currentMode == VoiceSessionMode.AWAITING_FOLLOWUP) {
                if (remainingFollowupSec > 1) {
                    remainingFollowupSec--
                } else {
                    remainingFollowupSec = 0
                    currentMode = VoiceSessionMode.IDLE
                }
            }
        }

        fun onSpeechDetected() {
            if (currentMode == VoiceSessionMode.SPEAKING && bargeInEnabled) {
                isTtsPlaying = false
                ttsStoppedCount++
                currentMode = VoiceSessionMode.LISTENING
            } else if (currentMode == VoiceSessionMode.AWAITING_FOLLOWUP) {
                currentMode = VoiceSessionMode.LISTENING
                remainingFollowupSec = 0
            }
        }

        fun dismissFollowup() {
            remainingFollowupSec = 0
            currentMode = VoiceSessionMode.IDLE
        }
    }

    @Test
    fun testFullContinuousVoiceLoopCycle() {
        val coordinator = TestVoiceStateCoordinator(continuousVoiceEnabled = true, followupWindowSec = 5)
        assertEquals(VoiceSessionMode.IDLE, coordinator.currentMode)

        // 1. User activates mic
        coordinator.onMicTriggered()
        assertEquals(VoiceSessionMode.LISTENING, coordinator.currentMode)

        // 2. User finishes speech -> processing
        coordinator.onUserFinishedSpeaking()
        assertEquals(VoiceSessionMode.PROCESSING, coordinator.currentMode)

        // 3. Assistant generates reply and starts speaking
        coordinator.onAssistantStartsSpeaking()
        assertEquals(VoiceSessionMode.SPEAKING, coordinator.currentMode)
        assertTrue(coordinator.isTtsPlaying)

        // 4. Assistant finishes speech -> transitions to follow-up listening
        coordinator.onAssistantSpeechFinished()
        assertEquals(VoiceSessionMode.AWAITING_FOLLOWUP, coordinator.currentMode)
        assertEquals(5, coordinator.remainingFollowupSec)
        assertFalse(coordinator.isTtsPlaying)

        // 5. Follow-up countdown ticks
        coordinator.onFollowupTick()
        assertEquals(4, coordinator.remainingFollowupSec)
        assertEquals(VoiceSessionMode.AWAITING_FOLLOWUP, coordinator.currentMode)

        // 6. User speaks follow-up question before timeout
        coordinator.onSpeechDetected()
        assertEquals(VoiceSessionMode.LISTENING, coordinator.currentMode)
        assertEquals(0, coordinator.remainingFollowupSec)
    }

    @Test
    fun testFollowupTimeoutTransitionsToIdle() {
        val coordinator = TestVoiceStateCoordinator(continuousVoiceEnabled = true, followupWindowSec = 3)
        coordinator.onAssistantStartsSpeaking()
        coordinator.onAssistantSpeechFinished()
        assertEquals(VoiceSessionMode.AWAITING_FOLLOWUP, coordinator.currentMode)
        assertEquals(3, coordinator.remainingFollowupSec)

        coordinator.onFollowupTick() // 2
        assertEquals(2, coordinator.remainingFollowupSec)
        coordinator.onFollowupTick() // 1
        assertEquals(1, coordinator.remainingFollowupSec)
        coordinator.onFollowupTick() // 0 -> timeout
        assertEquals(VoiceSessionMode.IDLE, coordinator.currentMode)
        assertEquals(0, coordinator.remainingFollowupSec)
    }

    @Test
    fun testBargeInStopsTtsAndTransitionsToListening() {
        val coordinator = TestVoiceStateCoordinator(bargeInEnabled = true)
        coordinator.onAssistantStartsSpeaking()
        assertEquals(VoiceSessionMode.SPEAKING, coordinator.currentMode)
        assertTrue(coordinator.isTtsPlaying)
        assertEquals(0, coordinator.ttsStoppedCount)

        // User speaks during TTS -> barge-in
        coordinator.onSpeechDetected()
        assertEquals(VoiceSessionMode.LISTENING, coordinator.currentMode)
        assertFalse(coordinator.isTtsPlaying)
        assertEquals(1, coordinator.ttsStoppedCount)
    }

    @Test
    fun testBargeInDisabledDoesNotInterruptTts() {
        val coordinator = TestVoiceStateCoordinator(bargeInEnabled = false)
        coordinator.onAssistantStartsSpeaking()
        assertEquals(VoiceSessionMode.SPEAKING, coordinator.currentMode)
        assertTrue(coordinator.isTtsPlaying)

        coordinator.onSpeechDetected()
        assertEquals(VoiceSessionMode.SPEAKING, coordinator.currentMode)
        assertTrue(coordinator.isTtsPlaying)
        assertEquals(0, coordinator.ttsStoppedCount)
    }

    @Test
    fun testDismissFollowupManually() {
        val coordinator = TestVoiceStateCoordinator(continuousVoiceEnabled = true)
        coordinator.onAssistantStartsSpeaking()
        coordinator.onAssistantSpeechFinished()
        assertEquals(VoiceSessionMode.AWAITING_FOLLOWUP, coordinator.currentMode)

        coordinator.dismissFollowup()
        assertEquals(VoiceSessionMode.IDLE, coordinator.currentMode)
        assertEquals(0, coordinator.remainingFollowupSec)
    }
}
