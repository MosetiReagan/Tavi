package com.example.tavi.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tavi.models.LocalModelPacks
import com.example.tavi.voice.LocalSpeechRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LocalPacksPanel(ids: Set<String>) {
    val context = LocalContext.current
    val packs = remember { LocalModelPacks.getInstance(context) }
    val scope = rememberCoroutineScope()
    val download by packs.download.collectAsState()
    val revision by packs.revision.collectAsState()
    val errors by packs.errors.collectAsState()
    var job by remember { mutableStateOf<Job?>(null) }
    var active by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Downloads are optional. After installation, processing stays on this device. Downloading contacts the model host; model and dataset terms are linked below.", style = MaterialTheme.typography.bodySmall)
        packs.catalog.filter { it.id in ids }.forEach { pack ->
            val installed = remember(revision, pack.id) { packs.isInstalled(pack.id) }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(pack.title, style = MaterialTheme.typography.titleSmall)
                Text(pack.description, style = MaterialTheme.typography.bodySmall)
                Text(String.format(Locale.ROOT, "%.1f MB • %s", pack.bytes / 1_000_000.0, if (installed) "Installed" else "Optional download"))
                if (download?.id == pack.id) {
                    val progress = download!!
                    LinearProgressIndicator(progress = { (progress.received.toFloat() / progress.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                errors[pack.id]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row {
                    if (active == pack.id) TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
                    else TextButton(enabled = active == null && download == null, onClick = {
                        active = pack.id
                        job = scope.launch {
                            try {
                                if (installed) {
                                    if (pack.id == "semantic-search") {
                                        com.example.tavi.documents.LocalEmbeddingRuntime.getInstance(context).removePack()
                                        com.example.tavi.documents.DocumentLibrary.getInstance(context).clearSemanticIndex()
                                    } else LocalSpeechRuntime.getInstance(context).removePack(pack.id)
                                } else packs.install(pack.id)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { /* The pack manager exposes its download error. */ }
                            finally { active = null }
                        }
                    }) { Text(if (installed) "Remove" else "Download") }
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pack.source))) } }) { Text("Source & terms") }
                }
            } }
        }
    }
}
