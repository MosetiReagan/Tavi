package com.example.tavi.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

/** No recordings are written to storage. Capture ends on silence or after 30 seconds. */
object LocalMicrophone {
    suspend fun capture(context: Context, onReady: () -> Unit, onSpeech: () -> Unit): FloatArray = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission required." }
        val rate = 16000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "This microphone does not support 16 kHz recording." }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Unable to initialize microphone." }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Unable to start microphone." }
            withContext(Dispatchers.Main) { onReady() }
            val samples = FloatArray(rate * 30)
            val buffer = ShortArray(320)
            var count = 0; var voiced = 0; var silence = 0; var started = false
            while (count < samples.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, samples.size - count))
                check(n > 0) { "Microphone recording interrupted ($n)." }
                var energy = 0.0
                for (i in 0 until n) { val v = buffer[i] / 32768f; samples[count + i] = v; energy += v * v }
                count += n
                if (sqrt(energy / n) > .012) {
                    voiced += n; silence = 0
                    if (!started) { started = true; withContext(Dispatchers.Main) { onSpeech() } }
                } else silence += n
                if (!started && count >= rate * 7) break
                if (voiced >= rate / 4 && silence >= rate * 9 / 10) break
            }
            check(voiced >= rate / 4) { "No speech recognized. Try again closer to the microphone." }
            samples.copyOf(count)
        } finally { runCatching { recorder.stop() }; recorder.release() }
    }
}

/** Short streaming writes allow cancellation without leaving a playing AudioTrack behind. */
object LocalAudioPlayback {
    suspend fun play(audio: SpeechAudio, pitch: Float = 1f) = withContext(Dispatchers.IO) {
        val rate = audio.sampleRate
        val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported voice output sample rate." }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum, rate / 5 * 2)).setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Unable to initialize audio output." }
            track.play()
            track.playbackParams = PlaybackParams().setPitch(pitch.coerceIn(.5f, 2f)).setSpeed(1f)
            val buffer = ShortArray(1024)
            var offset = 0
            while (offset < audio.samples.size) {
                currentCoroutineContext().ensureActive()
                val size = minOf(buffer.size, audio.samples.size - offset)
                for (i in 0 until size) buffer[i] = (audio.samples[offset + i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
                var written = 0
                while (written < size) {
                    currentCoroutineContext().ensureActive()
                    val n = track.write(buffer, written, size - written, AudioTrack.WRITE_BLOCKING)
                    check(n > 0) { "Audio playback interrupted ($n)." }; written += n
                }
                offset += size
            }
            withTimeout((audio.samples.size.toLong() * 1000 / rate) + 3000) {
                while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < audio.samples.size) delay(20)
            }
        } finally { runCatching { track.stop(); track.flush() }; track.release() }
    }
    fun chunks(text: String): List<String> {
        val result = mutableListOf<String>(); var remaining = text.trim().take(12000)
        while (remaining.isNotEmpty()) {
            var end = minOf(350, remaining.length)
            if (end < remaining.length) {
                val boundary = remaining.lastIndexOf(' ', end)
                if (boundary > 150) end = boundary
            }
            result += remaining.take(end); remaining = remaining.drop(end).trimStart()
        }
        return result
    }
}
