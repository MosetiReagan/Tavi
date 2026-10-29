package com.example.tavi.roles.assistant

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

class TaviRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "TaviRecognitionService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeRecognizer: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        if (listener == null) return

        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this@TaviRecognitionService)) {
                    Log.w(TAG, "Speech recognition not available on system")
                    listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                    return@post
                }

                activeRecognizer?.destroy()
                activeRecognizer = SpeechRecognizer.createSpeechRecognizer(this@TaviRecognitionService).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            try { listener.readyForSpeech(params) } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onBeginningOfSpeech() {
                            try { listener.beginningOfSpeech() } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onRmsChanged(rmsdB: Float) {
                            try { listener.rmsChanged(rmsdB) } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {
                            try { listener.bufferReceived(buffer) } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onEndOfSpeech() {
                            try { listener.endOfSpeech() } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onError(error: Int) {
                            try { listener.error(error) } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onResults(results: Bundle?) {
                            try { listener.results(results) } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            try { listener.partialResults(partialResults) } catch (e: Exception) { Log.w(TAG, "listener error", e) }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    startListening(recognizerIntent ?: Intent())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognition proxy", e)
                listener.error(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    override fun onCancel(listener: Callback?) {
        mainHandler.post {
            try {
                activeRecognizer?.cancel()
                activeRecognizer?.destroy()
                activeRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling recognizer", e)
            }
        }
    }

    override fun onStopListening(listener: Callback?) {
        mainHandler.post {
            try {
                activeRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeRecognizer?.destroy()
        activeRecognizer = null
    }
}
