package com.example.tavi.roles.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class TaviRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Report error gracefully if standalone speech model is not initialized
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {
    }

    override fun onStopListening(listener: Callback?) {
    }
}
