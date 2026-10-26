package com.example.tavi.ui.capabilities

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.AccessStatus
import com.example.tavi.model.AuditLogEntry
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.roles.RoleManagerHelper
import com.example.tavi.tools.CapabilityGroup
import com.example.tavi.tools.PermissionCoordinator
import com.example.tavi.tools.SpecialAccessItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilitiesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { TaviDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var specialAccessItems by remember { mutableStateOf(PermissionCoordinator.getSpecialAccessItems(context)) }
    var capabilityGroups by remember { mutableStateOf(PermissionCoordinator.getCapabilityGroups(context)) }
    var auditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }

    fun refresh() {
        specialAccessItems = PermissionCoordinator.getSpecialAccessItems(context)
        capabilityGroups = PermissionCoordinator.getCapabilityGroups(context)
        scope.launch(Dispatchers.IO) {
            val logs = db.getAllAuditLogs()
            withContext(Dispatchers.Main) {
                auditLogs = logs
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    // Automatically refresh status whenever user returns from Settings or role sheet
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { refresh() }
    )

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { refresh() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Android Access & Roles", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Device & Platform Overview
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Platform: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "Unified Level 2 Android System Access Manager. Tavi uses official Android roles and special permissions to integrate with system features while operating strictly on-device without cloud telemetry.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Section 1: System Roles & Special Access
            item {
                Text(
                    text = "System Roles & Special Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "High-privilege system integrations requiring explicit user enablement in Android Settings or system role dialogues.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(specialAccessItems, key = { it.title }) { item ->
                SpecialAccessCard(
                    item = item,
                    onAction = {
                        try {
                            if (item.isRole) {
                                val intent = RoleManagerHelper.createRequestRoleIntent(context, item.type)
                                if (intent != null) {
                                    roleLauncher.launch(intent)
                                } else {
                                    context.startActivity(PermissionCoordinator.getSettingsIntent(item.type, context))
                                }
                            } else {
                                context.startActivity(PermissionCoordinator.getSettingsIntent(item.type, context))
                            }
                        } catch (_: Exception) {
                            context.startActivity(PermissionCoordinator.getAppDetailsSettingsIntent(context))
                        }
                    }
                )
            }

            // Section 2: Standard Android Capabilities
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Standard Runtime Capabilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Device tools governed by standard Android runtime permissions and native sandboxing.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(capabilityGroups, key = { it.title }) { group ->
                CapabilityCard(
                    group = group,
                    onRequestPermissions = { perms ->
                        permissionLauncher.launch(perms.toTypedArray())
                    },
                    onOpenSettings = { accessType ->
                        try {
                            val intent = PermissionCoordinator.getSettingsIntent(accessType, context)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            context.startActivity(PermissionCoordinator.getAppDetailsSettingsIntent(context))
                        }
                    }
                )
            }

            // Section 3: Audit Trail
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Native Action Audit Trail",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Local tamper-evident log of native tool requests. Message contents and sensitive arguments are never logged.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (auditLogs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No native tool calls executed yet. Try asking: 'What's on my calendar tomorrow?', 'Check my screen time', or 'Set an alarm for 7am'.",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(auditLogs.take(10), key = { it.id }) { log ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when {
                                log.status == "SUCCESS" -> Icons.Default.CheckCircle
                                log.status.contains("BLOCKED") -> Icons.Default.Lock
                                log.status.contains("AWAITING") -> Icons.Default.Warning
                                else -> Icons.Default.Info
                            }
                            val tint = when {
                                log.status == "SUCCESS" -> Color(0xFF2E7D32)
                                log.status.contains("BLOCKED") -> MaterialTheme.colorScheme.error
                                log.status.contains("AWAITING") -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.toolName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(log.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(log.status, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SpecialAccessCard(
    item: SpecialAccessItem,
    onAction: () -> Unit
) {
    val isGrantedOrHeld = item.status == AccessStatus.GRANTED || item.status == AccessStatus.ROLE_HELD
    val isAvailable = item.status == AccessStatus.ROLE_AVAILABLE || item.status == AccessStatus.NOT_GRANTED
    val isUnsupported = item.status == AccessStatus.ROLE_UNAVAILABLE || item.status == AccessStatus.UNSUPPORTED

    val badgeColor = when {
        isGrantedOrHeld -> Color(0xFF2E7D32)
        isAvailable -> Color(0xFFE65100)
        else -> Color.Gray
    }

    val badgeText = when (item.status) {
        AccessStatus.ROLE_HELD -> "ROLE HELD"
        AccessStatus.GRANTED -> "GRANTED"
        AccessStatus.ROLE_AVAILABLE -> "AVAILABLE"
        AccessStatus.NOT_GRANTED -> "NOT GRANTED"
        AccessStatus.ROLE_UNAVAILABLE -> "OEM UNAVAILABLE"
        AccessStatus.UNSUPPORTED -> "NOT SUPPORTED"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isRole) Icons.Default.Shield else Icons.Default.Security,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Text(item.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Security & Privacy notice
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.securityNotice,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Action button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when {
                    isGrantedOrHeld -> {
                        OutlinedButton(
                            onClick = onAction,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Active / Manage", fontSize = 12.sp)
                        }
                    }
                    isAvailable -> {
                        Button(
                            onClick = onAction,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (item.isRole) "Request Role" else "Enable in Settings", fontSize = 12.sp)
                        }
                    }
                    isUnsupported -> {
                        OutlinedButton(onClick = {}, enabled = false) {
                            Text("Unavailable on Device", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityCard(
    group: CapabilityGroup,
    onRequestPermissions: (List<String>) -> Unit,
    onOpenSettings: (SpecialAccessType) -> Unit
) {
    val categoryIcon = getCategoryIcon(group.category)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (group.isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = if (group.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (group.isGranted) "Granted & Active" else "Action Required",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (group.isGranted) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }
            }

            Text(group.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Supported Tools
            Text(
                text = "Native Tools: " + group.supportedOperations.joinToString(", "),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )

            // Platform Limitation
            Text(
                text = "Platform Limits: " + group.limitations,
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // Action button if not granted
            if (!group.isGranted) {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (group.specialAccessType != SpecialAccessType.NONE) {
                        Button(
                            onClick = { onOpenSettings(group.specialAccessType) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Open Settings", fontSize = 12.sp)
                        }
                    } else if (group.requiredPermissions.isNotEmpty()) {
                        Button(
                            onClick = { onRequestPermissions(group.requiredPermissions) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Grant Permission", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

fun getCategoryIcon(cat: ToolCategory): ImageVector {
    return when (cat) {
        ToolCategory.CALENDAR -> Icons.Default.CalendarMonth
        ToolCategory.CONTACTS -> Icons.Default.Contacts
        ToolCategory.NOTIFICATIONS -> Icons.Default.Notifications
        ToolCategory.TELEPHONY -> Icons.Default.Phone
        ToolCategory.CLOCK -> Icons.Default.Alarm
        ToolCategory.APPLICATIONS -> Icons.Default.Apps
        ToolCategory.FILES -> Icons.Default.Folder
        ToolCategory.DEVICE -> Icons.Default.Settings
        ToolCategory.LOCATION -> Icons.Default.LocationOn
        ToolCategory.MEDIA -> Icons.Default.Mic
        ToolCategory.NOTES -> Icons.Default.Security
        ToolCategory.CONNECTIVITY -> Icons.Default.Wifi
        ToolCategory.USAGE -> Icons.Default.Android
        ToolCategory.ACCESSIBILITY -> Icons.Default.Visibility
        ToolCategory.RESEARCH -> Icons.Default.Info
    }
}
