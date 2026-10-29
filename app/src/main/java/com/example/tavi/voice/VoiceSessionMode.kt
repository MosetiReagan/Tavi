package com.example.tavi.voice

/**
 * State machine representing continuous hands-free voice loop.
 */
enum class VoiceSessionMode {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    AWAITING_FOLLOWUP
}
