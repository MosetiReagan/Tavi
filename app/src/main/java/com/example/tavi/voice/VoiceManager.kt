package com.example.tavi.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Build
import android.media.AudioManager
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
import kotlinx.coroutines.CancellationException
import com.example.tavi.models.LocalModelPacks
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
    private var localListenJob: Job? = null
    private var localSpeakJob: Job? = null
    private val localRuntime = LocalSpeechRuntime.getInstance(context)
    private val packs = LocalModelPacks.getInstance(context)

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val _offlineVoiceAvailable = MutableStateFlow(false)
    val offlineVoiceAvailable = _offlineVoiceAvailable.asStateFlow()
    private var followupListening = false
    var isVoiceSessionActive = false
        private set
    private val doneCallbacks = mutableMapOf<String, () -> Unit>()
    private var activeUtteranceId: String? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            scope.launch { dismissFollowup(); stopSpeaking() }
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        _autoReadReplies.value = prefs.getBoolean(KEY_AUTO_READ, false)
        _continuousVoice.value = prefs.getBoolean(KEY_CONTINUOUS_VOICE, true)
        _followupWindowSeconds.value = prefs.getInt(KEY_FOLLOWUP_WINDOW_SECONDS, 5).coerceIn(3, 8)
        if (key == com.example.tavi.language.AssistantLanguage.KEY) {
            dismissFollowup(); stopSpeaking(); configureOfflineVoice()
        }
    }

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
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        scope.launch { packs.revision.collect { configureOfflineVoice() } }
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed initializing TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            configureOfflineVoice()
            textToSpeech?.setSpeechRate(_speechRate.value)
            textToSpeech?.setPitch(_pitch.value)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId == activeUtteranceId) {
                            _isSpeaking.value = true
                            _sessionMode.value = VoiceSessionMode.SPEAKING
                        }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId != activeUtteranceId) return@launch
                        activeUtteranceId = null
                        _isSpeaking.value = false
                        audioManager?.abandonAudioFocus(focusListener)
                        doneCallbacks.remove(utteranceId)?.invoke()
                        handleSpeechEnded()
                    }
                }

                override fun onError(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId != activeUtteranceId) return@launch
                        activeUtteranceId = null
                        _isSpeaking.value = false
                        audioManager?.abandonAudioFocus(focusListener)
                        doneCallbacks.remove(utteranceId)?.invoke()
                        handleSpeechEnded()
                    }
                }
            })
        }
    }

    private fun configureOfflineVoice() {
        val locale = com.example.tavi.language.AssistantLanguage.locale(context)
        val voice = textToSpeech?.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == locale.language }?.maxByOrNull { it.quality }
        isTtsReady = voice != null
        _offlineVoiceAvailable.value = isTtsReady || localRuntime.outputAvailable()
        if (voice != null) textToSpeech?.voice = voice
    }

    private fun handleSpeechEnded() {
        if (_continuousVoice.value && isVoiceSessionActive && activeOnResult != null) {
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
        stopSpeaking()
        isVoiceSessionActive = true
        beginListening(false)
    }

    private fun beginListening(isFollowup: Boolean) {
        followupListening = isFollowup

        cancelFollowupCountdown()

        stopListeningInternal()
        if (localRuntime.recognitionAvailable()) {
            localListenJob = scope.launch {
                try {
                    val samples = LocalMicrophone.capture(context, onReady = {
                        _isListening.value = true
                        _sessionMode.value = if (followupListening) VoiceSessionMode.AWAITING_FOLLOWUP else VoiceSessionMode.LISTENING
                    }, onSpeech = {
                        cancelFollowupCountdown(); followupListening = false
                        _sessionMode.value = VoiceSessionMode.LISTENING
                    })
                    _isListening.value = false
                    _sessionMode.value = VoiceSessionMode.PROCESSING
                    val spoken = localRuntime.transcribe(samples, 16000)
                    if (spoken.isBlank()) error("No speech recognized. Try again.")
                    activeOnResult?.invoke(spoken)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    _isListening.value = false
                    if (followupListening) dismissFollowup()
                    else {
                        isVoiceSessionActive = false
                        _sessionMode.value = VoiceSessionMode.IDLE
                        activeOnError?.invoke(e.message ?: "Local speech recognition failed.")
                    }
                }
            }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            isVoiceSessionActive = false
            activeOnError?.invoke("Download the offline recognition pack in Tavi Settings, or install an Android on-device speech provider.")
            _sessionMode.value = VoiceSessionMode.IDLE
            return
        }

        stopListeningInternal()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, com.example.tavi.language.AssistantLanguage.locale(context).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _sessionMode.value = if (followupListening) VoiceSessionMode.AWAITING_FOLLOWUP else VoiceSessionMode.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        cancelFollowupCountdown()
                        followupListening = false
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
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This language has no installed on-device speech pack. Install it in Android speech settings or use text."
                            else -> "Speech recognition error ($error)"
                        }
                        if (followupListening) dismissFollowup() else activeOnError?.invoke(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _sessionMode.value = VoiceSessionMode.PROCESSING
                        followupListening = false
                        cancelFollowupCountdown()
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
                            if (!followupListening) _sessionMode.value = VoiceSessionMode.LISTENING
                            activeOnPartialResult?.invoke(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating or starting speech recognizer", e)
            isVoiceSessionActive = false
            activeOnError?.invoke("Unable to initialize offline speech recognizer: ${e.message}")
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    private fun startFollowupWindow() {
        scope.launch {
            cancelFollowupCountdown()
            _sessionMode.value = VoiceSessionMode.AWAITING_FOLLOWUP
            val windowSec = _followupWindowSeconds.value
            _followupRemainingSeconds.value = windowSec

            if (activeOnResult != null) beginListening(true)

            followupJob = scope.launch {
                for (remaining in windowSec downTo 1) {
                    _followupRemainingSeconds.value = remaining
                    delay(1000L)
                    if (!isActive) return@launch
                }
                _followupRemainingSeconds.value = 0
                if (followupListening) {
                    dismissFollowup()
                }
            }
        }
    }

    fun dismissFollowup() {
        isVoiceSessionActive = false
        followupListening = false
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
        localListenJob?.cancel(); localListenJob = null
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
        isVoiceSessionActive = false
        cancelFollowupCountdown()
        stopListeningInternal()
        if (_sessionMode.value == VoiceSessionMode.LISTENING || _sessionMode.value == VoiceSessionMode.AWAITING_FOLLOWUP) {
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    fun speak(text: String, onDoneCallback: (() -> Unit)? = null) {
        val useLocal = localRuntime.outputAvailable()
        if ((!isTtsReady && !useLocal) || text.isEmpty()) {
            onDoneCallback?.invoke()
            handleSpeechEnded()
            return
        }
        stopSpeaking()
        stopListeningInternal()
        val granted = audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        if (granted != null && granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onDoneCallback?.invoke()
            dismissFollowup()
            return
        }
        _isSpeaking.value = true
        _sessionMode.value = VoiceSessionMode.SPEAKING
        val utteranceId = "TAVI_TTS_${System.currentTimeMillis()}"
        activeUtteranceId = utteranceId
        if (onDoneCallback != null) doneCallbacks[utteranceId] = onDoneCallback
        if (useLocal) {
            localSpeakJob = scope.launch {
                try {
                    for (chunk in LocalAudioPlayback.chunks(text)) {
                        val audio = localRuntime.synthesize(chunk, speed = _speechRate.value)
                        LocalAudioPlayback.play(audio, _pitch.value)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    Log.w(TAG, "Local voice output failed", e)
                    isVoiceSessionActive = false
                    activeOnError?.invoke(e.message ?: "Local voice output failed.")
                }
                if (activeUtteranceId == utteranceId) {
                    activeUtteranceId = null
                    _isSpeaking.value = false
                    audioManager?.abandonAudioFocus(focusListener)
                    doneCallbacks.remove(utteranceId)?.invoke()
                    handleSpeechEnded()
                }
            }
            return
        }
        if (textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            activeUtteranceId = null
            doneCallbacks.remove(utteranceId)?.invoke()
            _isSpeaking.value = false
            audioManager?.abandonAudioFocus(focusListener)
            handleSpeechEnded()
        }
    }

    fun stopSpeaking() {
        localSpeakJob?.cancel(); localSpeakJob = null
        activeUtteranceId = null
        doneCallbacks.clear()
        audioManager?.abandonAudioFocus(focusListener)
        textToSpeech?.stop()
        _isSpeaking.value = false
        if (_sessionMode.value == VoiceSessionMode.SPEAKING) {
            _sessionMode.value = VoiceSessionMode.IDLE
        }
    }

    fun destroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        cancelFollowupCountdown()
        scope.cancel()
        stopListeningInternal()
        stopSpeaking()
        textToSpeech?.shutdown()
        textToSpeech = null
        activeOnResult = null
        activeOnPartialResult = null
        activeOnError = null
        CoroutineScope(Dispatchers.IO).launch { localRuntime.release() }
    }
}
