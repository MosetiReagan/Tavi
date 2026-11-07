package com.example.tavi.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class PackAsset(val path: String, val url: String, val bytes: Long, val sha256: String) {
    init {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && path.none { it.code < 32 } && path.split('/').none { it in setOf(".", "..", "") })
        require(URI(url).scheme == "https" && bytes > 0 && sha256.matches(Regex("[a-f0-9]{64}")))
    }
}
data class LocalModelPack(val id: String, val title: String, val description: String, val assets: List<PackAsset>, val source: String) {
    init { require(id.matches(Regex("[a-z0-9-]+")) && assets.isNotEmpty() && assets.map { it.path }.distinct().size == assets.size) }
    val bytes: Long get() = assets.sumOf { it.bytes }
    val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(
        assets.joinToString("\n") { "${it.path}:${it.bytes}:${it.sha256}" }.toByteArray()).joinToString("") { "%02x".format(it) }
}
data class PackDownload(val id: String, val received: Long, val total: Long)

object PackIntegrity {
    fun matches(file: File, asset: PackAsset): Boolean {
        if (!file.isFile || file.length() != asset.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256
    }
}

/** Model acquisition is user initiated. Partial or checksum-mismatched files are never loaded. */
class LocalModelPacks private constructor(context: Context) {
    private val root = File(context.filesDir, "local_packs").apply { mkdirs() }
    val catalog: List<LocalModelPack> = context.assets.open("local-model-packs.json").bufferedReader().use {
        val items = JSONObject(it.readText()).getJSONArray("packs")
        (0 until items.length()).map { index ->
            val item = items.getJSONObject(index); val files = item.getJSONArray("assets")
            LocalModelPack(item.getString("id"), item.getString("title"), item.getString("description"),
                (0 until files.length()).map { n -> files.getJSONObject(n).let { asset ->
                    PackAsset(asset.getString("path"), asset.getString("url"), asset.getLong("bytes"), asset.getString("sha256"))
                } }, item.getString("source"))
        }
    }
    private val downloadMutex = Mutex()
    private val verifyMutex = Mutex()
    private val verified = mutableMapOf<String, List<Pair<Long, Long>>>()
    private val _download = MutableStateFlow<PackDownload?>(null)
    val download = _download.asStateFlow()
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors = _errors.asStateFlow()
    fun pack(id: String) = catalog.first { it.id == id }
    fun directory(id: String) = File(root, pack(id).id)
    fun isInstalled(id: String): Boolean {
        val pack = pack(id); val dir = directory(id)
        return runCatching { File(dir, ".installed").readText() == pack.fingerprint &&
            pack.assets.all { File(dir, it.path).let { f -> f.isFile && f.length() == it.bytes } } }.getOrDefault(false)
    }
    suspend fun requireVerified(id: String): File = withContext(Dispatchers.IO) { verifyMutex.withLock {
        check(isInstalled(id)) { "Download ${pack(id).title} in Settings first." }
        val pack = pack(id); val dir = directory(id)
        val stamp = pack.assets.map { File(dir, it.path).let { f -> f.length() to f.lastModified() } }
        if (verified[id] != stamp) {
            for (asset in pack.assets) {
                currentCoroutineContext().ensureActive()
                if (!PackIntegrity.matches(File(dir, asset.path), asset)) {
                    File(dir, ".installed").delete(); verified.remove(id); _revision.value++
                    error("${pack.title} failed its integrity check. Remove it and download again.")
                }
            }
            verified[id] = stamp
        }
        dir
    } }
    suspend fun install(id: String) = withContext(Dispatchers.IO) { downloadMutex.withLock {
        val pack = pack(id)
        val staging = File(root, "$id.partial").apply { mkdirs() }
        _errors.value = _errors.value - id
        var complete = 0L
        try {
            for (asset in pack.assets) {
                currentCoroutineContext().ensureActive()
                val target = File(staging, asset.path).apply { parentFile!!.mkdirs() }
                if (!PackIntegrity.matches(target, asset)) {
                    val partial = File(target.path + ".part")
                    require(root.usableSpace > asset.bytes - partial.length() + 64L * 1024 * 1024) { "Free more storage before downloading this pack." }
                    if (!PackIntegrity.matches(partial, asset))
                        fetch(asset, partial) { received -> _download.value = PackDownload(id, complete + received, pack.bytes) }
                    check(PackIntegrity.matches(partial, asset)) { "Downloaded pack failed SHA-256 verification." }
                    check(partial.renameTo(target)) { "Cannot finalize the downloaded file." }
                }
                complete += asset.bytes
                _download.value = PackDownload(id, complete, pack.bytes)
            }
            currentCoroutineContext().ensureActive()
            File(staging, ".installed").writeText(pack.fingerprint)
            val final = directory(id)
            if (final.exists()) check(final.deleteRecursively()) { "Cannot replace the old pack." }
            check(staging.renameTo(final)) { "Cannot install the verified pack." }
            verifyMutex.withLock { verified.remove(id) }
            _revision.value++
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _errors.value = _errors.value + (id to (e.message ?: "Download failed.")); throw e }
        finally { _download.value = null }
    } }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) { downloadMutex.withLock {
        verifyMutex.withLock {
            verified.remove(id)
            check(directory(id).deleteRecursively() && File(root, "$id.partial").deleteRecursively()) { "Cannot remove the pack. Try again." }
        }
        _errors.value = _errors.value - id; _revision.value++
    } }
    private suspend fun fetch(asset: PackAsset, output: File, progress: (Long) -> Unit) {
        var offset = output.length().takeIf { it in 1 until asset.bytes } ?: 0
        val connection = URI(asset.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000; connection.readTimeout = 15_000
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = connection.responseCode
            check(connection.url.protocol == "https") { "Pack downloads require HTTPS." }
            check(status == 200 || status == 206) { "Model host returned HTTP $status. Try downloading again later." }
            if (status == 200) offset = 0
            else check(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) { "Invalid download range." }
            var received = offset
            connection.inputStream.use { input -> FileOutputStream(output, offset > 0).use { destination ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    received += n; check(received <= asset.bytes) { "Model download exceeds the verified file size." }
                    destination.write(buffer, 0, n); progress(received)
                }
                destination.fd.sync()
            } }
            check(received == asset.bytes) { "Model download was interrupted; tap Download to resume." }
            if (!PackIntegrity.matches(output, asset)) { output.delete(); error("Model checksum mismatch. Download again.") }
        } finally { connection.disconnect() }
    }
    companion object {
        @Volatile private var instance: LocalModelPacks? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: LocalModelPacks(context.applicationContext).also { instance = it }
        }
    }
}
