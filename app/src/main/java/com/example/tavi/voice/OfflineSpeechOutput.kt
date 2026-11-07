package com.example.tavi.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.util.Locale

object OfflineSpeechOutput {
    suspend fun speak(context: Context, text: String): Boolean = withContext(Dispatchers.Main) {
        withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { continuation ->
                var tts: TextToSpeech? = null
                fun finish(success: Boolean) {
                    tts?.shutdown()
                    if (continuation.isActive) continuation.resume(success)
                }
                tts = TextToSpeech(context.applicationContext) { status ->
                    val engine = tts
                    val voice = engine?.voices?.filter { !it.isNetworkConnectionRequired &&
                        it.locale.language == Locale.getDefault().language }?.maxByOrNull { it.quality }
                    if (status != TextToSpeech.SUCCESS || voice == null || !continuation.isActive) finish(false)
                    else {
                        engine.voice = voice
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) {}
                            override fun onDone(id: String?) = finish(true)
                            override fun onError(id: String?) = finish(false)
                        })
                        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tavi_tool") == TextToSpeech.ERROR) finish(false)
                    }
                }
                continuation.invokeOnCancellation { tts?.stop(); tts?.shutdown() }
            }
        } ?: false
    }
}
