package com.example.tavi.models

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PackIntegrityTest {
    private fun asset(path: String, data: ByteArray) = PackAsset(path, "https://example.com/weights", data.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
    @Test fun truncatedAndSameSizeTamperedFilesAreRejected() {
        val file = File.createTempFile("tavi-integrity", ".model")
        try {
            val original = byteArrayOf(1, 2, 3, 4); val spec = asset("weights.onnx", original)
            file.writeBytes(original); assertTrue(PackIntegrity.matches(file, spec))
            file.writeBytes(byteArrayOf(1, 2)); assertFalse(PackIntegrity.matches(file, spec))
            file.writeBytes(byteArrayOf(1, 2, 3, 5)); assertFalse(PackIntegrity.matches(file, spec))
        } finally { file.delete() }
    }
    @Test fun unsafePathsAndNonHttpsHostsAreRejected() {
        for (path in listOf("../weights", "/weights", "a/../b", "a//b", "a\\b", "x\u0000y")) {
            try { asset(path, byteArrayOf(1)); fail(path) } catch (_: IllegalArgumentException) {}
        }
        try { PackAsset("ok", "http://example.com", 1, "0".repeat(64)); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun fingerprintChangesWithAssetVersion() {
        val a = LocalModelPack("test", "Test", "Test", listOf(asset("weights", byteArrayOf(1))), "https://example.com")
        assertNotEquals(a.fingerprint, a.copy(assets = listOf(asset("weights", byteArrayOf(2)))).fingerprint)
    }
}
