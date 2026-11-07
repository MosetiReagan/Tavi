package com.example.tavi.voice

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.example.tavi.language.AssistantLanguage
import com.example.tavi.models.LocalModelPacks
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class SpeechAudio(val samples: FloatArray, val sampleRate: Int)

/** CPU inference only. Native handles are never freed while a decode/generation is running. */
class LocalSpeechRuntime private constructor(private val context: Context) : ComponentCallbacks2 {
    private val packs = LocalModelPacks.getInstance(context)
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idle: Job? = null
    private var recognizer: OfflineRecognizer? = null
    private var recognizerKey: String? = null
    private var tts: OfflineTts? = null
    private var voiceId: String? = null
    init { context.registerComponentCallbacks(this) }
    fun language(): String = if (AssistantLanguage.locale(context).language == "sw") "sw" else "en"
    private fun recognitionPack(language: String) = if (language == "sw") "speech-recognition-sw" else "speech-recognition"
    fun recognitionAvailable(language: String = language()) = packs.isInstalled(recognitionPack(language))
    fun outputAvailable(language: String = language()) = packs.isInstalled("voice-$language")

    suspend fun transcribe(samples: FloatArray, sampleRate: Int, language: String = language()): String =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(language in setOf("en", "sw"))
            require(sampleRate in 8000..48000 && samples.isNotEmpty() && samples.size <= sampleRate * 30)
            idle?.cancel()
            val id = recognitionPack(language)
            val dir = packs.requireVerified(id)
            val key = "$id:$language"
            if (recognizer == null || recognizerKey != key) {
                recognizer?.release(); recognizer = null
                val model = if (language == "sw") OfflineModelConfig(
                    omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = File(dir, "model.int8.onnx").path),
                    tokens = File(dir, "tokens.txt").path, numThreads = 2, provider = "cpu")
                else OfflineModelConfig(whisper = OfflineWhisperModelConfig(encoder = File(dir, "tiny-encoder.int8.onnx").path,
                        decoder = File(dir, "tiny-decoder.int8.onnx").path, language = language, task = "transcribe"),
                    tokens = File(dir, "tiny-tokens.txt").path, numThreads = 2, provider = "cpu")
                recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model))
                recognizerKey = key
            }
            val engine = recognizer!!
            val stream = engine.createStream()
            try {
                currentCoroutineContext().ensureActive()
                stream.acceptWaveform(samples, sampleRate)
                engine.decode(stream)
                currentCoroutineContext().ensureActive()
                engine.getResult(stream).text.trim()
            } finally { stream.release(); scheduleRelease() }
        } }

    suspend fun synthesize(text: String, language: String = language(), speed: Float = 1f): SpeechAudio =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(language in setOf("en", "sw") && text.isNotBlank() && text.length <= 400)
            idle?.cancel()
            val id = "voice-$language"
            val dir = packs.requireVerified(id)
            if (tts == null || voiceId != id) {
                tts?.release(); tts = null
                val model = packs.pack(id).assets.first { it.path.endsWith(".onnx") }
                tts = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(model = File(dir, model.path).path,
                        tokens = File(dir, "tokens.txt").path, dataDir = File(dir, "espeak-ng-data").path),
                    numThreads = 2, provider = "cpu")))
                voiceId = id
            }
            val job = currentCoroutineContext()[Job]
            try {
                currentCoroutineContext().ensureActive()
                val audio = tts!!.generateWithCallback(text, speed = speed.coerceIn(.5f, 2f), callback = SpeechGenerationCallback(job))
                currentCoroutineContext().ensureActive()
                check(audio.samples.isNotEmpty()) { "The local voice produced no audio." }
                SpeechAudio(audio.samples, audio.sampleRate)
            } finally { scheduleRelease() }
        } }

    suspend fun release() = withContext(Dispatchers.IO) { mutex.withLock {
        releaseLocked()
    } }
    suspend fun removePack(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(id in setOf("speech-recognition", "speech-recognition-sw", "voice-en", "voice-sw"))
        releaseLocked()
        packs.remove(id)
    } }
    private fun releaseLocked() {
        idle?.cancel(); idle = null
        recognizer?.release(); recognizer = null; recognizerKey = null
        tts?.release(); tts = null; voiceId = null
    }
    private fun scheduleRelease() {
        idle?.cancel()
        idle = cleanupScope.launch { delay(45_000); release() }
    }
    override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cleanupScope.launch { release() } }
    override fun onLowMemory() { cleanupScope.launch { release() } }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    companion object {
        @Volatile private var instance: LocalSpeechRuntime? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: LocalSpeechRuntime(context.applicationContext).also { instance = it }
        }
    }
}
