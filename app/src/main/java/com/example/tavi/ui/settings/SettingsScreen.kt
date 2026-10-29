package com.example.tavi.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.example.tavi.theme.SuccessGreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import com.example.tavi.engine.InferenceRuntime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    onNavigateToCapabilities: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { TaviDatabase.getInstance(context) }
    val modelManager = remember { ModelManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var models by remember { mutableStateOf(modelManager.getAvailableModels()) }
    var selectedModelId by remember { mutableStateOf(modelManager.activeModelId.value) }
    var modelInfo by remember { mutableStateOf(modelManager.getInstalledModelInfo()) }
    var activeRuntime by remember { mutableStateOf(modelManager.activeRuntime.value) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }
    var importStatusText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var showImportSuccessDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDownloadSuccessDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadUrlInput by remember { mutableStateOf(ModelManager.DEFAULT_MODEL_DOWNLOAD_URL) }
    var showDeleteModelConfirm by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var memoryToDelete by remember { mutableStateOf<MemoryEntry?>(null) }
    var memories by remember { mutableStateOf<List<MemoryEntry>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var showClearMemoryConfirm by remember { mutableStateOf(false) }
    var isMemoryEnabled by remember { mutableStateOf(com.example.tavi.memory.PersistentMemoryManager.isMemoryEnabled(context)) }
    val voiceManager = remember { com.example.tavi.voice.VoiceManager(context) }
    var speechRate by remember { mutableFloatStateOf(voiceManager.speechRate.value) }
    var pitch by remember { mutableFloatStateOf(voiceManager.pitch.value) }
    var autoReadReplies by remember { mutableStateOf(voiceManager.autoReadReplies.value) }
    var continuousVoice by remember { mutableStateOf(voiceManager.continuousVoice.value) }
    var followupWindowSeconds by remember { androidx.compose.runtime.mutableIntStateOf(voiceManager.followupWindowSeconds.value) }
    var bargeIn by remember { mutableStateOf(voiceManager.bargeIn.value) }
    var agentModeEnabled by remember { mutableStateOf(com.example.tavi.agent.AgentPreferences.isAgentModeEnabled(context)) }
    var showAgentExplanationDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch(Dispatchers.IO) {
            val persistentList = db.getActivePersistentMemories()
            val memList = if (persistentList.isNotEmpty()) {
                persistentList.map { MemoryEntry(it.id, it.key, it.value, it.category, it.updatedAt) }
            } else {
                db.getAllMemory()
            }
            val logsList = db.getAllAuditLogs()
            val mList = modelManager.getAvailableModels()
            val mInfo = modelManager.getInstalledModelInfo()
            val runtime = modelManager.activeRuntime.value
            val memEnabled = com.example.tavi.memory.PersistentMemoryManager.isMemoryEnabled(context)
            withContext(Dispatchers.Main) {
                memories = memList
                auditLogs = logsList
                models = mList
                modelInfo = mInfo
                activeRuntime = runtime
                isMemoryEnabled = memEnabled
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // In-app file picker for Gemma 3 1B (.task bundle)
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                isImporting = true
                importProgress = 0f
                importStatusText = "Connecting to selected file..."
                scope.launch {
                    val result = modelManager.importModelFromUri(context, uri) { copied, total ->
                        if (total > 0) {
                            importProgress = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            val mbCopied = copied / (1024 * 1024)
                            val mbTotal = total / (1024 * 1024)
                            importStatusText = "Importing Gemma weights: $mbCopied MB / $mbTotal MB (${(importProgress * 100).toInt()}%)"
                        } else {
                            val mbCopied = copied / (1024 * 1024)
                            importStatusText = "Importing Gemma weights: $mbCopied MB..."
                        }
                    }
                    isImporting = false
                    if (result.isSuccess) {
                        showImportSuccessDialog = true
                        refreshData()
                    } else {
                        importErrorMessage = result.exceptionOrNull()?.message ?: "Failed to import model file."
                    }
                }
            }
        }
    )

    // Permission launchers
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { refreshData() }
    )
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { refreshData() }
    )
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { refreshData() }
    )
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { refreshData() }
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // --- BRAND HEADER ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp
                        ) {
                            Image(
                                painter = painterResource(id = com.example.tavi.R.drawable.tavi_icon),
                                contentDescription = "Tavi App Icon",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tavi Assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("100% On-Device • Private AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            Text("Gemma 3 1B IT • SQLite Memory Store", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gemma 3 1B Instruction-Tuned", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = if (modelInfo.isInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (modelInfo.isInstalled) "Installed • ${modelInfo.formattedSize}" else "Model Not Downloaded",
                                    fontSize = 11.sp,
                                    color = if (modelInfo.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Current Engine: ${activeRuntime.displayName} • 100% on-device",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "AI Engine",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isGemma4 = activeRuntime == InferenceRuntime.GEMMA_4_E2B
                            Button(
                                onClick = {
                                    modelManager.setRuntime(InferenceRuntime.GEMMA_4_E2B)
                                    activeRuntime = InferenceRuntime.GEMMA_4_E2B
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (isGemma4) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.example.tavi.R.string.runtime_gemma4_e2b),
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            val isGemma3 = activeRuntime == InferenceRuntime.GEMMA_3_1B
                            Button(
                                onClick = {
                                    modelManager.setRuntime(InferenceRuntime.GEMMA_3_1B)
                                    activeRuntime = InferenceRuntime.GEMMA_3_1B
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (isGemma3) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.example.tavi.R.string.runtime_gemma3_1b),
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            val isDeterministic = activeRuntime == InferenceRuntime.DETERMINISTIC_ONLY
                            Button(
                                onClick = {
                                    modelManager.setRuntime(InferenceRuntime.DETERMINISTIC_ONLY)
                                    activeRuntime = InferenceRuntime.DETERMINISTIC_ONLY
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (isDeterministic) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.example.tavi.R.string.runtime_rules_only),
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (activeRuntime) {
                                InferenceRuntime.GEMMA_4_E2B, InferenceRuntime.LITERTLM -> "✨ Recommended: Gemma 4 E2B (~2.58 GB). Native OpenAPI tool calling, thinking reasoning channel, and full context window."
                                InferenceRuntime.GEMMA_3_1B -> "🔋 Low Memory: Gemma 3 1B (~1.1 GB). Lightweight on-device model optimized for budget devices (4 GB RAM)."
                                InferenceRuntime.DETERMINISTIC_ONLY -> "⚡ Rules Only: Pure regex intent routing with zero neural memory usage."
                                InferenceRuntime.MEDIAPIPE -> "🛡️ Compatibility: MediaPipe GenAI fallback engine with GPU/CPU support."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        if (modelInfo.isInstalled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Model installed and verified on this device (${modelInfo.formattedSize})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDownloadDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { showDeleteModelConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Model weights not installed. Fast-path offline rules active.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(12.dp))

                            if (isDownloading) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearProgressIndicator(
                                        progress = downloadProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = downloadStatusText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else if (isImporting) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearProgressIndicator(
                                        progress = importProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = importStatusText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.model_download_warning),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        modelManager.selectModel(ModelManager.MODEL_GEMMA_4_E2B)
                                        downloadUrlInput = ModelManager.DEFAULT_DOWNLOAD_URL_E2B
                                        showDownloadDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(androidx.compose.ui.res.stringResource(com.example.tavi.R.string.model_download_e2b_button))
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        modelManager.selectModel(ModelManager.MODEL_GEMMA_3_1B)
                                        downloadUrlInput = ModelManager.DEFAULT_DOWNLOAD_URL_3_1B
                                        showDownloadDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(androidx.compose.ui.res.stringResource(com.example.tavi.R.string.model_download_31b_button))
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Import Model Bundle (.litertlm / .task)")
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
                Spacer(Modifier.height(10.dp))

                // Master Toggle Switch Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Persistent Memory",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isMemoryEnabled) "Active: Remembers preferences and facts across sessions" else "Disabled: Memory retrieval & saves dormant",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMemoryEnabled,
                            onCheckedChange = { checked ->
                                com.example.tavi.memory.PersistentMemoryManager.setMemoryEnabled(context, checked)
                                isMemoryEnabled = checked
                            }
                        )
                    }
                }
            }

            if (memories.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mem.key.replace("_", " "),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = mem.category.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = mem.value,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                memoryToDelete = mem
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }

            // --- SECTION 3: VOICE & SPEECH (TTS) ---
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Voice & Speech (TTS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Configure on-device text-to-speech rate, voice pitch, and hands-free automatic read aloud.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Auto-Read Replies Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Read Assistant Replies",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (autoReadReplies) "Enabled: Speaks replies automatically" else "Disabled: Tap speaker icon to hear replies",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoReadReplies,
                                onCheckedChange = { checked ->
                                    voiceManager.setAutoReadReplies(checked)
                                    autoReadReplies = checked
                                }
                            )
                        }

                        HorizontalDivider()

                        // Continuous Voice Loop Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Continuous Voice Loop (Hands-Free)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (continuousVoice) "Enabled: Keeps listening for follow-up questions" else "Disabled: Push to talk only",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = continuousVoice,
                                onCheckedChange = { checked ->
                                    voiceManager.setContinuousVoice(checked)
                                    continuousVoice = checked
                                }
                            )
                        }

                        if (continuousVoice) {
                            // Follow-up Window Slider (3s to 8s)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Follow-up Listening Window",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${followupWindowSeconds}s",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = followupWindowSeconds.toFloat(),
                                    onValueChange = {
                                        val sec = it.toInt()
                                        followupWindowSeconds = sec
                                        voiceManager.setFollowupWindowSeconds(sec)
                                    },
                                    valueRange = 3f..8f,
                                    steps = 4
                                )
                            }
                        }

                        HorizontalDivider()

                        // Barge-in Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Barge-in (Interrupt by Talking)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (bargeIn) "Enabled: Talking stops assistant speech immediately" else "Disabled: Assistant finishes full sentence",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = bargeIn,
                                onCheckedChange = { checked ->
                                    voiceManager.setBargeIn(checked)
                                    bargeIn = checked
                                }
                            )
                        }

                        HorizontalDivider()

                        // Speech Rate Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Speech Speed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "%.1fx".format(speechRate),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = speechRate,
                                onValueChange = {
                                    speechRate = it
                                    voiceManager.setSpeechRate(it)
                                },
                                valueRange = 0.5f..2.0f,
                                steps = 14
                            )
                        }

                        // Speech Pitch Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Voice Pitch",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "%.1fx".format(pitch),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = pitch,
                                onValueChange = {
                                    pitch = it
                                    voiceManager.setPitch(it)
                                },
                                valueRange = 0.5f..1.5f,
                                steps = 9
                            )
                        }

                        // Test Voice Button
                        OutlinedButton(
                            onClick = {
                                voiceManager.speak("Hello! I am Tavi, your private on-device assistant.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test Voice Synthesis")
                        }
                    }
                }
            }

            // --- SECTION 3.5: SCREEN AGENT MODE (ACCESSIBILITY) ---
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                val isAccessibilityGranted = remember(agentModeEnabled) {
                    com.example.tavi.tools.PermissionCoordinator.hasSpecialAccess(
                        context,
                        com.example.tavi.model.SpecialAccessType.ACCESSIBILITY_SERVICE
                    )
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_toggle_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        !agentModeEnabled -> androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_status_disabled)
                                        !isAccessibilityGranted -> androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_status_needs_permission)
                                        else -> androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_status_active)
                                    },
                                    fontSize = 11.sp,
                                    color = if (agentModeEnabled && !isAccessibilityGranted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = agentModeEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!com.example.tavi.agent.AgentPreferences.isFirstRunExplanationShown(context)) {
                                            showAgentExplanationDialog = true
                                        } else {
                                            com.example.tavi.agent.AgentPreferences.setAgentModeEnabled(context, true)
                                            agentModeEnabled = true
                                        }
                                    } else {
                                        com.example.tavi.agent.AgentPreferences.setAgentModeEnabled(context, false)
                                        agentModeEnabled = false
                                    }
                                }
                            )
                        }

                        if (agentModeEnabled && !isAccessibilityGranted) {
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_accessibility_btn),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 4: PERMISSIONS & SECURITY ---
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Android Capabilities & Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onNavigateToCapabilities) {
                        Text("View All", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))

                Card(
                    onClick = onNavigateToCapabilities,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Capability & Permission Manager", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Inspect all 15 native categories, special access, platform limits, and audit trails.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        PermissionRow(
                            title = "Contacts Access",
                            subtitle = "Resolve contact names and phone numbers",
                            isGranted = hasContacts,
                            onRequest = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        PermissionRow(
                            title = "Microphone",
                            subtitle = "Voice transcription input",
                            isGranted = hasAudio,
                            onRequest = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            PermissionRow(
                                title = "Notifications",
                                subtitle = "Show reminders and scheduled alerts",
                                isGranted = hasNotifications,
                                onRequest = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            )
                        }
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
                                    showClearHistoryConfirm = true
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

        // Model Import Error Dialog
        importErrorMessage?.let { err ->
            AlertDialog(
                onDismissRequest = { importErrorMessage = null },
                title = { Text("Model Import Failed") },
                text = { Text(err) },
                confirmButton = {
                    TextButton(onClick = { importErrorMessage = null }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        // Model Download Error Dialog
        downloadErrorMessage?.let { err ->
            AlertDialog(
                onDismissRequest = { downloadErrorMessage = null },
                title = { Text("Model Download Failed") },
                text = { Text(err) },
                confirmButton = {
                    TextButton(onClick = { downloadErrorMessage = null }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        // Model Download Confirm Dialog
        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text("Download Gemma 3 1B Weights") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Download official instruction-tuned model bundle directly to private app storage. (~529 MB - 1.1 GB).")
                        OutlinedTextField(
                            value = downloadUrlInput,
                            onValueChange = { downloadUrlInput = it },
                            label = { Text("Download URL (HTTPS)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Supports automatic redirect handling (e.g., Hugging Face CDN) and SHA-256 integrity validation.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showDownloadDialog = false
                        isDownloading = true
                        downloadProgress = 0f
                        downloadStatusText = "Connecting to download server..."
                        scope.launch {
                            val result = modelManager.downloadModel(url = downloadUrlInput) { copied, total ->
                                if (total > 0) {
                                    downloadProgress = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    val mbCopied = copied / (1024 * 1024)
                                    val mbTotal = total / (1024 * 1024)
                                    downloadStatusText = "Downloading Gemma weights: $mbCopied MB / $mbTotal MB (${(downloadProgress * 100).toInt()}%)"
                                } else {
                                    val mbCopied = copied / (1024 * 1024)
                                    downloadStatusText = "Downloading Gemma weights: $mbCopied MB..."
                                }
                            }
                            isDownloading = false
                            if (result.isSuccess) {
                                showDownloadSuccessDialog = true
                                refreshData()
                            } else {
                                downloadErrorMessage = result.exceptionOrNull()?.message ?: "Failed to download model weights."
                            }
                        }
                    }) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Model Download Success Dialog
        if (showDownloadSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadSuccessDialog = false },
                title = { Text("Model Downloaded Successfully") },
                text = { Text("Gemma 3 1B model downloaded and verified. 100% private, on-device assistant is ready.") },
                confirmButton = {
                    TextButton(onClick = { showDownloadSuccessDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Model Import Success Dialog
        if (showImportSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showImportSuccessDialog = false },
                title = { Text("Model Ready") },
                text = { Text("Gemma 3 1B instruction-tuned model weights installed and verified. On-device ${activeRuntime.displayName} inference is now active!") },
                confirmButton = {
                    TextButton(onClick = { showImportSuccessDialog = false }) {
                        Text("Start Using Tavi")
                    }
                }
            )
        }

        // Delete Model Confirmation Dialog
        if (showDeleteModelConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteModelConfirm = false },
                title = { Text("Delete Model Weights?") },
                text = { Text("This will remove the ${modelInfo.formattedSize} Gemma model weights from sandboxed storage. Tavi will return to its rule-based fallback until weights are re-acquired.") },
                confirmButton = {
                    TextButton(onClick = {
                        modelManager.deleteModel(ModelManager.MODEL_GEMMA_3_1B)
                        showDeleteModelConfirm = false
                        refreshData()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteModelConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clear History Confirmation Dialog
        if (showClearHistoryConfirm) {
            AlertDialog(
                onDismissRequest = { showClearHistoryConfirm = false },
                title = { Text("Clear All History?") },
                text = { Text("This will permanently delete all conversation messages and audit logs from your device SQLite database. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.clearMessages()
                            db.clearAuditLogs()
                            refreshData()
                        }
                        showClearHistoryConfirm = false
                    }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Memory Confirmation Dialog
        memoryToDelete?.let { mem ->
            AlertDialog(
                onDismissRequest = { memoryToDelete = null },
                title = { Text("Delete Memory?") },
                text = { Text("Remove \"${mem.key}\" (\"${mem.value}\") from your remembered personal profile?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            com.example.tavi.memory.PersistentMemoryManager.deleteMemory(context, mem.id)
                            db.deleteMemory(mem.id)
                            refreshData()
                        }
                        memoryToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memoryToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
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
                                        Text(log.status, fontSize = 11.sp, color = if (log.verified) SuccessGreen else MaterialTheme.colorScheme.error)
                                    }
                                    Text(log.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
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
                            com.example.tavi.memory.PersistentMemoryManager.clearAllMemories(context)
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

        // Screen Agent First-Run Explanation Dialog
        if (showAgentExplanationDialog) {
            AlertDialog(
                onDismissRequest = { showAgentExplanationDialog = false },
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_dialog_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_dialog_explanation),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            com.example.tavi.agent.AgentPreferences.setFirstRunExplanationShown(context, true)
                            com.example.tavi.agent.AgentPreferences.setAgentModeEnabled(context, true)
                            agentModeEnabled = true
                            showAgentExplanationDialog = false
                        }
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_dialog_accept))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAgentExplanationDialog = false
                        }
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.example.tavi.R.string.agent_mode_dialog_cancel))
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
                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Granted", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(onClick = onRequest, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("Grant", fontSize = 12.sp)
            }
        }
    }
}
