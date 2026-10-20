package com.example.tavi.roles.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Declared system VoiceInteractionService for default Assistant eligibility.
 * Permission: android.permission.BIND_VOICE_INTERACTION
 */
class TaviVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "TaviVoiceInteractionService is ready")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d(TAG, "TaviVoiceInteractionService shutdown")
    }

    companion object {
        private const val TAG = "TaviVoiceService"
    }
}
