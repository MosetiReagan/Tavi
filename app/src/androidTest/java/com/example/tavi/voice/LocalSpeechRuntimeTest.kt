package com.example.tavi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tavi.models.LocalModelPacks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class LocalSpeechRuntimeTest {
    @Test fun englishAndKiswahiliGenerateRealOfflineAudioAndEnglishDecodes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packs = LocalModelPacks.getInstance(context)
        assumeTrue(listOf("speech-recognition", "voice-en", "voice-sw").all(packs::isInstalled))
        val runtime = LocalSpeechRuntime.getInstance(context)
        try {
            for ((language, text) in listOf("en" to "What time is it now?", "sw" to "Saa ngapi sasa?")) {
                val start = System.currentTimeMillis()
                val audio = runtime.synthesize(text, language)
                assertTrue(audio.sampleRate in 16000..24000)
                assertTrue(audio.samples.size > audio.sampleRate / 2)
                assertTrue(audio.samples.all { it.isFinite() })
                assertTrue(audio.samples.any { kotlin.math.abs(it) > .01f })
                if (language == "en") {
                    val decoded = runtime.transcribe(audio.samples, audio.sampleRate, language)
                    Log.i("TaviSpeechTest", "$language: '$text' -> '$decoded' in ${System.currentTimeMillis() - start} ms")
                    assertTrue(decoded.lowercase().contains("time"))
                }
            }
        } finally { runtime.release() }
    }
    @Test fun kiswahiliRecognizesLicensedHumanSpeechWithBoundedWordErrors() = runBlocking {
        val runtime = LocalSpeechRuntime.getInstance(ApplicationProvider.getApplicationContext<Context>())
        assumeTrue(runtime.recognitionAvailable("sw"))
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val bytes = testContext.assets.open("fleurs-sw-human.wav").use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
        assertEquals("data", String(bytes.copyOfRange(36, 40)))
        assertEquals(1, pcm.getShort(20).toInt()); assertEquals(1, pcm.getShort(22).toInt())
        assertEquals(16000, pcm.getInt(24)); assertEquals(16, pcm.getShort(34).toInt())
        pcm.position(44)
        val samples = FloatArray((bytes.size - 44) / 2) { pcm.short / 32768f }
        val expected = "utafiti wa karne ya ishirini umeonyesha kwamba kuna makundi mawili ya utofauti wa jeni fiche na zilizoelezewa"
        try {
            val start = System.currentTimeMillis()
            val decoded = runtime.transcribe(samples, 16000, "sw")
            val reference = Regex("\\w+").findAll(expected).map { it.value }.toList()
            val actual = Regex("\\w+").findAll(decoded.lowercase()).map { it.value }.toList()
            var row = IntArray(actual.size + 1) { it }
            reference.forEachIndexed { i, word ->
                val next = IntArray(actual.size + 1); next[0] = i + 1
                actual.forEachIndexed { j, token ->
                    next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (word == token) 0 else 1)
                }
                row = next
            }
            val errors = row.last()
            Log.i("TaviSpeechTest", "Human sw: '$decoded'; $errors/${reference.size} word errors in ${System.currentTimeMillis() - start} ms")
            assertTrue("Kiswahili accuracy regressed: $errors/${reference.size}: $decoded", errors.toDouble() / reference.size <= .45)
        } finally { runtime.release() }
    }
    @Test fun localPlaybackCancelsAndCanStartAgain() = runBlocking {
        val runtime = LocalSpeechRuntime.getInstance(ApplicationProvider.getApplicationContext<Context>())
        assumeTrue(runtime.outputAvailable("en"))
        try {
            val audio = runtime.synthesize("This is local voice playback.", "en")
            val playback = launch { LocalAudioPlayback.play(audio) }
            delay(100); playback.cancelAndJoin()
            LocalAudioPlayback.play(audio)
        } finally { runtime.release() }
    }
    @Test fun packCatalogAndInstalledAssetsPassIntegrityChecks() = runBlocking {
        val packs = LocalModelPacks.getInstance(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(4, packs.catalog.count { it.id in setOf("speech-recognition", "speech-recognition-sw", "voice-en", "voice-sw") })
        for (pack in packs.catalog.filter { packs.isInstalled(it.id) }) assertTrue(packs.requireVerified(pack.id).isDirectory)
    }
}
