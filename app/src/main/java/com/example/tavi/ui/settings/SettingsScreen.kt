package com.example.tavi.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tavi.data.TaviDatabase
import com.example.tavi.engine.ModelManager
import com.example.tavi.model.AuditLogEntry
import com.example.tavi.model.MemoryEntry
import com.example.tavi.model.ModelOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { TaviDatabase.getInstance(context) }
    val modelManager = remember { ModelManager(context) }
    val scope = rememberCoroutineScope()

    var models by remember { mutableStateOf(modelManager.getAvailableModels()) }
    var selectedModelId by remember { mutableStateOf(modelManager.activeModelId.value) }
    var memories by remember { mutableStateOf<List<MemoryEntry>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var showClearMemoryConfirm by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch(Dispatchers.IO) {
            val memList = db.getAllMemory()
            val logsList = db.getAllAuditLogs()
            val mList = modelManager.getAvailableModels()
            withContext(Dispatchers.Main) {
                memories = memList
                auditLogs = logsList
                models = mList
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Permission launchers
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { refreshData() }
    )
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { refreshData() }
    )
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { refreshData() }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION 1: MODEL SELECTION ---
            item {
                Text(
                    text = "Local Language Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        models.forEach { modelOption ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        modelManager.selectModel(modelOption.id)
                                        selectedModelId = modelOption.id
                                        refreshData()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedModelId == modelOption.id,
                                    onClick = {
                                        modelManager.selectModel(modelOption.id)
                                        selectedModelId = modelOption.id
                                        refreshData()
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(modelOption.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        if (modelOption.isPrimary) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("Primary", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Text(modelOption.sizeDescription, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(modelOption.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: PERSONAL MEMORY ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Personal Memory (${memories.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (memories.isNotEmpty()) {
                        TextButton(onClick = { showClearMemoryConfirm = true }) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Personal preferences and facts remembered by Tavi. Stored strictly in local SQLite and never shared.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (memories.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No saved personal memory entries. Tell Tavi 'remember that my doctor is Dr. Smith' to save information.",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(memories, key = { it.id }) { mem ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mem.key, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(mem.value, fontSize = 13.sp)
                                Text("Category: ${mem.category}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    db.deleteMemory(mem.id)
                                    refreshData()
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // --- SECTION 3: PERMISSIONS & SECURITY ---
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Permission Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PermissionRow(
                            title = "Calendar Access",
                            subtitle = "Read & create upcoming schedule events",
                            isGranted = hasCalendar,
                            onRequest = {
                                calendarPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                )
                            }
                        )
                        Divider(Modifier.padding(vertical = 8.dp))
                        PermissionRow(
                            title = "SMS & Messages",
                            subtitle = "Draft and send SMS with confirmation",
                            isGranted = hasSms,
                            onRequest = {
                                smsPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
                                )
                            }
                        )
                        Divider(Modifier.padding(vertical = 8.dp))
                        PermissionRow(
                            title = "Microphone",
                            subtitle = "Voice transcription input",
                            isGranted = hasAudio,
                            onRequest = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                }
            }

            // --- SECTION 4: PRIVACY & AUDIT LOGS ---
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Privacy & Local Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("100% Local Privacy Guarantee", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            "No model prompts, user conversations, or calendar events are transmitted to any cloud servers. All inference runs strictly on your device hardware.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAuditDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Audit Logs (${auditLogs.size})", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        db.clearMessages()
                                        db.clearAuditLogs()
                                        refreshData()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear History", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // Audit Log Dialog
        if (showAuditDialog) {
            AlertDialog(
                onDismissRequest = { showAuditDialog = false },
                title = { Text("Local Tool Audit Log") },
                text = {
                    if (auditLogs.isEmpty()) {
                        Text("No tools executed yet.")
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(auditLogs) { log ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(log.toolName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(log.status, fontSize = 11.sp, color = if (log.verified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                                    }
                                    Text(log.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Divider(Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAuditDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Clear Memory Confirmation Dialog
        if (showClearMemoryConfirm) {
            AlertDialog(
                onDismissRequest = { showClearMemoryConfirm = false },
                title = { Text("Clear All Memory?") },
                text = { Text("This will permanently delete all saved personal facts and preferences from your device.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.clearAllMemory()
                            refreshData()
                        }
                        showClearMemoryConfirm = false
                    }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearMemoryConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Granted", fontSize = 12.sp, color = Color(0xFF2E7D32))
            }
        } else {
            Button(onClick = onRequest, modifier = Modifier.height(34.dp)) {
                Text("Grant", fontSize = 11.sp)
            }
        }
    }
}
