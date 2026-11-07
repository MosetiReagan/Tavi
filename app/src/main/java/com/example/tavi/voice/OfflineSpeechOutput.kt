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
        val local = LocalSpeechRuntime.getInstance(context)
        if (local.outputAvailable()) {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            val focus = android.media.AudioManager.OnAudioFocusChangeListener { change ->
                if (change == android.media.AudioManager.AUDIOFOCUS_LOSS || change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) job?.cancel()
            }
            if (manager.requestAudioFocus(focus, android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@withContext false
            try {
                for (chunk in LocalAudioPlayback.chunks(text)) LocalAudioPlayback.play(local.synthesize(chunk))
                return@withContext true
            } finally { manager.abandonAudioFocus(focus) }
        }
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
                        it.locale.language == com.example.tavi.language.AssistantLanguage.locale(context).language }?.maxByOrNull { it.quality }
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
