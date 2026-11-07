package com.example.tavi.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tavi.data.TaviDatabase
import com.example.tavi.memory.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun PersonalMemoryPanel() {
    val context = LocalContext.current
    val db = remember { TaviDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(emptyList<PersistentMemoryRecord>()) }
    var enabled by remember { mutableStateOf(PersistentMemoryManager.isMemoryEnabled(context)) }
    var editing by remember { mutableStateOf<PersistentMemoryRecord?>(null) }
    var value by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<PersistentMemoryRecord?>(null) }
    var clear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun refresh() { scope.launch { records = withContext(Dispatchers.IO) { db.getActivePersistentMemories() } } }
    LaunchedEffect(Unit) { records = withContext(Dispatchers.IO) { PersistentMemoryManager.migrateLegacyMemories(context); db.getActivePersistentMemories() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personal memory", style = MaterialTheme.typography.titleMedium)
        Row { Text("Remember and recall personal facts", Modifier.weight(1f)); Switch(enabled, { enabled = it; PersistentMemoryManager.setMemoryEnabled(context, it) }) }
        Text("Tell Tavi ‘remember that my doctor is Dr. Smith’. Memories are stored in local SQLite. You can correct or delete them here.", style = MaterialTheme.typography.bodySmall)
        if (records.isEmpty()) Text("No saved memories.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        records.forEach { record -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(record.key.replace('_', ' '), style = MaterialTheme.typography.titleSmall); Text(record.value)
            Text("${record.provenance.name.replace('_', ' ').lowercase()} • ${java.text.DateFormat.getDateInstance().format(java.util.Date(record.updatedAt))}", style = MaterialTheme.typography.bodySmall)
            Text(if (record.sourceConversationId != null) "Source chat: ${record.sourceConversationId}" else "Source: saved directly or unavailable", style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = { editing = record; value = record.value }) { Text("Correct") }
                TextButton(onClick = { deleting = record }) { Text("Delete") } }
        } } }
        TextButton(onClick = { clear = true }) { Text("Clear all memories") }
    }
    editing?.let { record -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("Correct ${record.key.replace('_', ' ')}") },
        text = { OutlinedTextField(value, { value = it }, label = { Text("Current value") }) }, confirmButton = { TextButton(onClick = {
            if (value.isNotBlank()) scope.launch {
                val updated = record.copy(id = UUID.randomUUID().toString(), value = value.trim(), provenance = MemoryProvenance.USER_EXPLICIT,
                    createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), sourceConversationId = null, sourceMessageId = null)
                when (val result = withContext(Dispatchers.IO) { PersistentMemoryManager.saveOrUpdateMemory(context, updated) }) {
                    is PersistentMemoryManager.SaveResult.Saved -> { editing = null; refresh() }
                    is PersistentMemoryManager.SaveResult.Blocked -> error = result.reason
                    is PersistentMemoryManager.SaveResult.Error -> error = result.message
                }
            }
        }) { Text("Save correction") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }) }
    if (deleting != null || clear) AlertDialog(onDismissRequest = { deleting = null; clear = false }, title = { Text(if (clear) "Clear all memories?" else "Delete this memory?") },
        confirmButton = { TextButton(onClick = { val target = deleting; val all = clear; scope.launch {
            withContext(Dispatchers.IO) { if (all) PersistentMemoryManager.clearAllMemories(context) else target?.let { PersistentMemoryManager.deleteMemory(context, it.key) } }
            deleting = null; clear = false; refresh()
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null; clear = false }) { Text("Keep") } })
}
