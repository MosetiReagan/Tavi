package com.example.tavi.engine

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

class ModelManagerDownloadTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private var server: HttpServer? = null
    private var serverPort: Int = 0

    @Before
    fun setUp() {
        tempDir = File.createTempFile("model_test_", "_dir").apply {
            delete()
            mkdirs()
        }

        val memoryPrefs = HashMap<String, Any>()
        val mockSharedPreferences = object : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = memoryPrefs
            override fun getString(key: String?, defValue: String?): String? = memoryPrefs[key] as? String ?: defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
            override fun getInt(key: String?, defValue: Int): Int = memoryPrefs[key] as? Int ?: defValue
            override fun getLong(key: String?, defValue: Long): Long = memoryPrefs[key] as? Long ?: defValue
            override fun getFloat(key: String?, defValue: Float): Float = memoryPrefs[key] as? Float ?: defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = memoryPrefs[key] as? Boolean ?: defValue
            override fun contains(key: String?): Boolean = memoryPrefs.containsKey(key)
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                private val temp = HashMap<String, Any?>()
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
                override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key!!] = value; return this }
                override fun remove(key: String?): SharedPreferences.Editor { temp.remove(key); return this }
                override fun clear(): SharedPreferences.Editor { temp.clear(); return this }
                override fun commit(): Boolean { memoryPrefs.putAll(temp.filterValues { it != null } as Map<String, Any>); return true }
                override fun apply() { commit() }
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        mockContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = mockSharedPreferences
            override fun registerComponentCallbacks(callback: ComponentCallbacks?) {}
            override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {}
            override fun getSystemService(name: String): Any? = null
        }
    }

    @After
    fun tearDown() {
        server?.stop(0)
        tempDir.deleteRecursively()
    }

    @Test
    fun testDownloadFailsOnInvalidUrlOrNetworkError() = runTest {
        val modelManager = ModelManager(mockContext)
        val result = modelManager.downloadModel(url = "http://127.0.0.1:59999/non_existent_weights.litertlm") { _, _ -> }

        assertTrue("Download should fail on non-existent endpoint", result.isFailure)
        assertFalse("Model should not be marked as loaded", modelManager.isLoaded.value)
    }

    @Test
    fun testDownloadRejectsUndersizedPayload() = runTest {
        // Start local test server that serves 512 bytes (under MIN_MODEL_BYTES = 100 MB)
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/small_model.litertlm") { exchange ->
                val response = "Tiny mock content for Gemma model".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        serverPort = server!!.address.port

        val modelManager = ModelManager(mockContext)
        val result = modelManager.downloadModel(url = "http://127.0.0.1:$serverPort/small_model.litertlm") { _, _ -> }

        assertTrue("Download should reject incomplete weights < 100 MB", result.isFailure)
        assertTrue(
            "Error should indicate incomplete download",
            result.exceptionOrNull()?.message?.contains("Incomplete download") == true
        )
        assertFalse("Model should not be loaded", modelManager.isLoaded.value)
    }

    @Test
    fun testDownloadFollowsRedirectAndCompletesSuccessfully() = runTest {
        // Compute SHA-256 for a simulated 101 MB payload
        val oneMb = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val digest = MessageDigest.getInstance("SHA-256")
        for (i in 0 until 101) {
            digest.update(oneMb)
        }
        val expectedSha = digest.digest().joinToString("") { "%02x".format(it) }

        // Local server with 302 redirect: /redirect -> /final_destination.litertlm
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/redirect") { exchange ->
                exchange.responseHeaders.add("Location", "/final_destination.litertlm")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/final_destination.litertlm") { exchange ->
                val totalBytes = 101L * 1024 * 1024
                exchange.sendResponseHeaders(200, totalBytes)
                exchange.responseBody.use { out ->
                    for (i in 0 until 101) {
                        out.write(oneMb)
                        out.flush()
                    }
                }
            }
            start()
        }
        serverPort = server!!.address.port

        val modelManager = ModelManager(mockContext)
        var reportedBytes = 0L
        var totalBytesReported = 0L

        val result = modelManager.downloadModel(
            url = "http://127.0.0.1:$serverPort/redirect",
            expectedSha256 = expectedSha
        ) { copied, total ->
            reportedBytes = copied
            totalBytesReported = total
        }

        assertTrue("Download through redirect with valid SHA-256 should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val installedFile = result.getOrThrow()
        assertTrue("Installed file must exist", installedFile.exists())
        assertEquals(101L * 1024 * 1024, installedFile.length())
        assertEquals(expectedSha, modelManager.computeSha256(installedFile))
        assertTrue("ModelManager should report loaded", modelManager.isLoaded.value)
        assertEquals(101L * 1024 * 1024, reportedBytes)
        assertEquals(101L * 1024 * 1024, totalBytesReported)
    }

    @Test
    fun testDownloadRejectsSha256Mismatch() = runTest {
        val oneMb = ByteArray(1024 * 1024) { 0x42 }
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/mismatch_test.litertlm") { exchange ->
                val totalBytes = 101L * 1024 * 1024
                exchange.sendResponseHeaders(200, totalBytes)
                exchange.responseBody.use { out ->
                    for (i in 0 until 101) {
                        out.write(oneMb)
                    }
                }
            }
            start()
        }
        serverPort = server!!.address.port

        val modelManager = ModelManager(mockContext)
        val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"

        val result = modelManager.downloadModel(
            url = "http://127.0.0.1:$serverPort/mismatch_test.litertlm",
            expectedSha256 = wrongHash
        ) { _, _ -> }

        assertTrue("Download with mismatched SHA-256 must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()?.message?.contains("SHA-256 mismatch") == true)
        assertFalse("Corrupt model must not be loaded", modelManager.isLoaded.value)
        assertFalse("Destination model file must not exist after failed hash check", modelManager.modelFile.exists())
    }
}
