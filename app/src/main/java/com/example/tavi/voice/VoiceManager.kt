package com.example.tavi.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceManager"
        private const val PREFS_NAME = "tavi_voice_prefs"
        const val KEY_SPEECH_RATE = "tts_speech_rate"
        const val KEY_PITCH = "tts_pitch"
        const val KEY_AUTO_READ = "tts_auto_read"
        const val KEY_CONTINUOUS_VOICE = "voice_continuous_loop"
        const val KEY_FOLLOWUP_WINDOW_SECONDS = "voice_followup_window_sec"
        const val KEY_BARGE_IN = "voice_barge_in"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var followupJob: Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _sessionMode = MutableStateFlow(VoiceSessionMode.IDLE)
    val sessionMode: StateFlow<VoiceSessionMode> = _sessionMode.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_SPEECH_RATE, 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(prefs.getFloat(KEY_PITCH, 1.0f))
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _autoReadReplies = MutableStateFlow(prefs.getBoolean(KEY_AUTO_READ, false))
    val autoReadReplies: StateFlow<Boolean> = _autoReadReplies.asStateFlow()

    private val _continuousVoice = MutableStateFlow(prefs.getBoolean(KEY_CONTINUOUS_VOICE, true))
    val continuousVoice: StateFlow<Boolean> = _continuousVoice.asStateFlow()

    private val _followupWindowSeconds = MutableStateFlow(prefs.getInt(KEY_FOLLOWUP_WINDOW_SECONDS, 5).coerceIn(3, 8))
    val followupWindowSeconds: StateFlow<Int> = _followupWindowSeconds.asStateFlow()

    private val _bargeIn = MutableStateFlow(prefs.getBoolean(KEY_BARGE_IN, true))
    val bargeIn: StateFlow<Boolean> = _bargeIn.asStateFlow()

    private val _followupRemainingSeconds = MutableStateFlow(0)
    val followupRemainingSeconds: StateFlow<Int> = _followupRemainingSeconds.asStateFlow()

    // Callbacks for follow-up continuation
    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnPartialResult: ((String) -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed initializing TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
            textToSpeech?.setSpeechRate(_speechRate.value)
            textToSpeech?.setPitch(_pitch.value)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _sessionMode.value = VoiceSessionMode.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    handleSpeechEnded()
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    handleSpeechEnded()
                }
            })
            isTtsReady = true
        }
    }

    private fun handleSpeechEnded() {
        if (_continuousVoice.value && (activeOnResult != null || _autoReadReplies.value)) {
            startFollowupWindow()
        } else {
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
        textToSpeech?.setSpeechRate(clamped)
    }

    fun setPitch(pitchVal: Float) {
        val clamped = pitchVal.coerceIn(0.5f, 2.0f)
        _pitch.value = clamped
        prefs.edit().putFloat(KEY_PITCH, clamped).apply()
        textToSpeech?.setPitch(clamped)
    }

    fun setAutoReadReplies(enabled: Boolean) {
        _autoReadReplies.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_READ, enabled).apply()
    }

    fun setContinuousVoice(enabled: Boolean) {
        _continuousVoice.value = enabled
        prefs.edit().putBoolean(KEY_CONTINUOUS_VOICE, enabled).apply()
        if (!enabled && _sessionMode.value == VoiceSessionMode.AWAITING_FOLLOWUP) {
            dismissFollowup()
        }
    }

    fun setFollowupWindowSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(3, 8)
        _followupWindowSeconds.value = clamped
        prefs.edit().putInt(KEY_FOLLOWUP_WINDOW_SECONDS, clamped).apply()
    }

    fun setBargeIn(enabled: Boolean) {
        _bargeIn.value = enabled
        prefs.edit().putBoolean(KEY_BARGE_IN, enabled).apply()
    }

    fun startListening(
        onPartialResult: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        activeOnPartialResult = onPartialResult
        activeOnResult = onResult
        activeOnError = onError

        cancelFollowupCountdown()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device.")
            _sessionMode.value = VoiceSessionMode.IDLE
            return
        }

        stopListeningInternal()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _sessionMode.value = VoiceSessionMode.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        cancelFollowupCountdown()
                        // Barge-in interruption check
                        if (_isSpeaking.value && _bargeIn.value) {
                            Log.d(TAG, "Barge-in triggered: speech started during TTS playback")
                            stopSpeaking()
                        }
                        _sessionMode.value = VoiceSessionMode.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (_isSpeaking.value && _bargeIn.value && rmsdB > 5.0f) {
                            Log.d(TAG, "Barge-in triggered via RMS threshold ($rmsdB dB)")
                            stopSpeaking()
                            _sessionMode.value = VoiceSessionMode.LISTENING
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _sessionMode.value = VoiceSessionMode.PROCESSING
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        if (_sessionMode.value != VoiceSessionMode.AWAITING_FOLLOWUP) {
                            _sessionMode.value = VoiceSessionMode.IDLE
                        }
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Speech recognition error ($error)"
                        }
                        activeOnError?.invoke(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _sessionMode.value = VoiceSessionMode.PROCESSING
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()
                        if (!spoken.isNullOrEmpty()) {
                            activeOnResult?.invoke(spoken)
                        } else {
                            _sessionMode.value = VoiceSessionMode.IDLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrEmpty()) {
                            if (_isSpeaking.value && _bargeIn.value) {
                                stopSpeaking()
                            }
                            _sessionMode.value = VoiceSessionMode.LISTENING
                            activeOnPartialResult?.invoke(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating or starting speech recognizer", e)
            onError("Unable to initialize speech recognizer: ${e.message}")
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    private fun startFollowupWindow() {
        scope.launch {
            cancelFollowupCountdown()
            _sessionMode.value = VoiceSessionMode.AWAITING_FOLLOWUP
            val windowSec = _followupWindowSeconds.value
            _followupRemainingSeconds.value = windowSec

            val resultCb = activeOnResult
            val partialCb = activeOnPartialResult

            if (resultCb != null) {
                startListening(
                    onPartialResult = { partialCb?.invoke(it) },
                    onResult = { result ->
                        cancelFollowupCountdown()
                        resultCb(result)
                    },
                    onError = {
                        // In follow-up mode, a timeout error simply completes the follow-up window quietly
                        dismissFollowup()
                    }
                )
            }

            followupJob = scope.launch {
                for (remaining in windowSec downTo 1) {
                    _followupRemainingSeconds.value = remaining
                    delay(1000L)
                    if (!isActive) return@launch
                }
                _followupRemainingSeconds.value = 0
                if (_sessionMode.value == VoiceSessionMode.AWAITING_FOLLOWUP) {
                    dismissFollowup()
                }
            }
        }
    }

    fun dismissFollowup() {
        cancelFollowupCountdown()
        stopListeningInternal()
        _sessionMode.value = VoiceSessionMode.IDLE
        _followupRemainingSeconds.value = 0
    }

    private fun cancelFollowupCountdown() {
        followupJob?.cancel()
        followupJob = null
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
        _isListening.value = false
    }

    fun stopListening() {
        cancelFollowupCountdown()
        stopListeningInternal()
        if (_sessionMode.value == VoiceSessionMode.LISTENING || _sessionMode.value == VoiceSessionMode.AWAITING_FOLLOWUP) {
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    fun speak(text: String, onDoneCallback: (() -> Unit)? = null) {
        if (!isTtsReady || text.isEmpty()) {
            onDoneCallback?.invoke()
            return
        }
        stopSpeaking()
        _isSpeaking.value = true
        _sessionMode.value = VoiceSessionMode.SPEAKING
        val utteranceId = "TAVI_TTS_${System.currentTimeMillis()}"
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        if (_sessionMode.value == VoiceSessionMode.SPEAKING) {
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    fun destroy() {
        cancelFollowupCountdown()
        scope.cancel()
        stopListeningInternal()
        stopSpeaking()
        textToSpeech?.shutdown()
        textToSpeech = null
        activeOnResult = null
        activeOnPartialResult = null
        activeOnError = null
    }
}
