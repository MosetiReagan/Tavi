package com.example.tavi.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tavi.documents.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.example.tavi.models.LocalModelPacks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val library = remember { DocumentLibrary.getInstance(context) }
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf(emptyList<LocalDocument>()) }
    var hits by remember { mutableStateOf(emptyList<DocumentHit>()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var deleting by remember { mutableStateOf<LocalDocument?>(null) }
    val packs = remember { LocalModelPacks.getInstance(context) }
    val packRevision by packs.revision.collectAsState()
    var searchStatus by remember { mutableStateOf("Keyword search") }
    var searching by remember { mutableStateOf(false) }
    var indexing by remember { mutableStateOf(false) }
    var indexJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) { documents = withContext(Dispatchers.IO) { library.list() } }
    LaunchedEffect(query, documents, packRevision, indexing) {
        hits = emptyList()
        if (query.isBlank()) { searching = false; return@LaunchedEffect }
        searching = true
        try {
            delay(250)
            val result = library.searchHybrid(query, onProgress = { done, total ->
                scope.launch { searchStatus = "Indexing $done of $total excerpts…" }
            })
            hits = result.hits
            searchStatus = result.message ?: "Search by meaning • ${result.coverage?.indexed}/${result.coverage?.total} excerpts indexed"
        } finally { searching = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importJob = scope.launch {
            importing = true
            try {
                val document = LocalDocumentImporter(context).import(uri) { message -> scope.launch { status = message } }
                documents = withContext(Dispatchers.IO) { library.list() }
                status = "Imported ${document.title}. OCR results can contain mistakes; check source excerpts."
            } catch (e: CancellationException) { status = "Import cancelled."; throw e }
            catch (e: Exception) { status = e.message ?: "Import failed." }
            finally { importing = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Local documents") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Search text files, PDFs, images, and your notes. PDF and image text is recognized on this device. References identify the file and page.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "application/pdf", "image/*")) }, enabled = !importing) { Text("Import document") }
                if (importing) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = { importJob?.cancel() }) { Text("Cancel import") } }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search documents and notes") }, modifier = Modifier.fillMaxWidth())
                Text(searchStatus, style = MaterialTheme.typography.bodySmall)
                if (packs.isInstalled("semantic-search")) {
                    if (indexing) TextButton(onClick = { indexJob?.cancel() }) { Text("Cancel indexing") }
                    else TextButton(onClick = {
                        indexJob = scope.launch {
                            indexing = true
                            try {
                                do {
                                    val coverage = library.indexSemantics(onProgress = { done, total -> scope.launch { searchStatus = "Indexing $done of $total excerpts…" } })
                                } while (!coverage.complete)
                                searchStatus = "Documents and notes indexed for search by meaning."
                            } catch (e: CancellationException) { searchStatus = "Indexing paused; completed excerpts are saved."; throw e }
                            catch (e: Exception) { searchStatus = e.message ?: "Indexing failed." }
                            finally { indexing = false }
                        }
                    }) { Text("Index documents and notes") }
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (query.isNotBlank()) {
                if (hits.isEmpty() && !searching) item { Text("No matching excerpts.") }
                items(hits) { hit -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(hit.reference, style = MaterialTheme.typography.titleSmall)
                    Text(hit.excerpt)
                } } }
            } else {
                if (documents.isEmpty()) item { Text("Your library is empty. Import a file to begin.") }
                items(documents, key = { it.id }) { document -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(document.title, style = MaterialTheme.typography.titleMedium)
                    Text("${document.pages} pages • stored locally")
                    Row { TextButton(onClick = { query = document.title }) { Text("Search") }
                        TextButton(onClick = { deleting = document }) { Text("Delete") } }
                } } }
            }
        }
    }
    deleting?.let { document -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${document.title}?") },
        text = { Text("Remove its extracted text and search index from this device.") },
        confirmButton = { TextButton(onClick = {
            scope.launch { withContext(Dispatchers.IO) { library.delete(document.id) }; documents = withContext(Dispatchers.IO) { library.list() } }
            deleting = null
        }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}
