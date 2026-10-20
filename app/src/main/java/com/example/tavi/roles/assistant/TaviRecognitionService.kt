package com.example.tavi.roles.assistant

import android.content.Intent
import android.speech.RecognitionService

class TaviRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition delegate placeholder for offline engine
    }

    override fun onCancel(listener: Callback?) {
    }

    override fun onStopListening(listener: Callback?) {
    }
}
