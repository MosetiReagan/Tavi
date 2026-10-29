package com.example.tavi.ui.capabilities

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.tavi.MainActivity
import com.example.tavi.data.TaviDatabase
import com.example.tavi.model.AccessStatus
import com.example.tavi.model.AuditLogEntry
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.roles.RoleManagerHelper
import com.example.tavi.theme.BrandPrimary
import com.example.tavi.theme.SuccessGreen
import com.example.tavi.theme.WarningAmber
import com.example.tavi.tools.CapabilityGroup
import com.example.tavi.tools.PermissionCoordinator
import com.example.tavi.tools.SpecialAccessItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogToolItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val examplePrompt: String,
    val permissionNote: String,
    val icon: ImageVector
)

private val ALL_CATALOG_TOOLS = listOf(
    // 1. Telephony, Messages & Notifications (11)
    CatalogToolItem("messages_send_sms", "Send Text Message", "Messages & Calls", "Sends an SMS to any contact or phone number with on-device confirmation.", "Message Dennis: Are we still meeting?", "Requires: SEND_SMS", Icons.Default.Sms),
    CatalogToolItem("messages_draft", "Draft Message", "Messages & Calls", "Prepares an SMS draft for review without sending.", "Draft a message to Dennis: On my way", "No permissions required", Icons.Default.Sms),
    CatalogToolItem("messages_search", "Search Messages", "Messages & Calls", "Searches your local SMS inbox by query or sender name.", "Search messages from Dennis", "Requires: READ_SMS", Icons.Default.Sms),
    CatalogToolItem("phone_call", "Make Phone Call", "Messages & Calls", "Initiates an outgoing phone call directly with confirmation.", "Call Mom", "Requires: CALL_PHONE", Icons.Default.Phone),
    CatalogToolItem("phone_dial", "Open Dialer", "Messages & Calls", "Pre-fills a phone number into your default dialer.", "Dial 555-0199", "No permissions required", Icons.Default.Phone),
    CatalogToolItem("call_log_search", "Recent Call History", "Messages & Calls", "Searches recent incoming and outgoing phone calls.", "Show recent calls", "Requires: READ_CALL_LOG", Icons.Default.Phone),
    CatalogToolItem("contacts_search", "Search Contacts", "Messages & Calls", "Finds contact phone numbers and emails in your address book.", "Find contact Dennis", "Requires: READ_CONTACTS", Icons.Default.Contacts),
    CatalogToolItem("contacts_resolve", "Resolve Contact Details", "Messages & Calls", "Retrieves complete contact card details including addresses and notes.", "Get details for Alice", "Requires: READ_CONTACTS", Icons.Default.Contacts),
    CatalogToolItem("contacts_create", "Create New Contact", "Messages & Calls", "Adds a new contact to device address book with name and phone number.", "Save Dennis with number 555-0199", "Requires: WRITE_CONTACTS", Icons.Default.Contacts),
    CatalogToolItem("notification_summary", "Summarize Notifications", "Messages & Calls", "Reads recent unread device notifications safely on-device.", "Check my notifications", "Requires: Notification Access", Icons.Default.Notifications),
    CatalogToolItem("notification_dismiss", "Dismiss Notification", "Messages & Calls", "Dismisses a notification by package name or notification key.", "Clear notifications from Twitter", "Requires: Notification Access", Icons.Default.Notifications),

    // 2. Calendar (5)
    CatalogToolItem("calendar_list_events", "View Calendar Schedule", "Calendar", "Lists upcoming events and meetings from your local calendar.", "What's on my agenda today?", "Requires: READ_CALENDAR", Icons.Default.CalendarMonth),
    CatalogToolItem("calendar_get_event", "Event Details", "Calendar", "Fetches complete event details, location, and guest lists.", "Show details for event #10", "Requires: READ_CALENDAR", Icons.Default.CalendarMonth),
    CatalogToolItem("calendar_create_event", "Create Calendar Event", "Calendar", "Schedules an event with smart natural date and time parsing.", "Schedule meeting tomorrow at 3 PM", "Requires: WRITE_CALENDAR", Icons.Default.CalendarMonth),
    CatalogToolItem("calendar_update_event", "Update Calendar Event", "Calendar", "Modifies title, start/end time, or location of an existing event.", "Move my 3 PM meeting to 4 PM", "Requires: WRITE_CALENDAR", Icons.Default.CalendarMonth),
    CatalogToolItem("calendar_delete_event", "Cancel Calendar Event", "Calendar", "Removes an event from your local calendar by title or ID.", "Cancel event #12", "Requires: WRITE_CALENDAR", Icons.Default.CalendarMonth),

    // 3. Alarms & Timers (7)
    CatalogToolItem("alarm_create", "Set Alarm", "Alarms & Timers", "Schedules an alarm directly in the system Clock app.", "Set an alarm for 7:30 AM", "No special permissions", Icons.Default.Alarm),
    CatalogToolItem("alarm_show", "Show Alarms", "Alarms & Timers", "Opens the system Clock app to view all scheduled alarms.", "Show my alarms", "No special permissions", Icons.Default.Alarm),
    CatalogToolItem("timer_set", "Set Timer", "Alarms & Timers", "Starts a countdown timer for seconds, minutes, or hours.", "Set a timer for 10 minutes", "No special permissions", Icons.Default.Timer),
    CatalogToolItem("clock_query_time", "World Clock", "Alarms & Timers", "Looks up the current time across any time zone.", "What time is it in Tokyo?", "No special permissions", Icons.Default.Alarm),
    CatalogToolItem("reminder_create", "Create Reminder", "Alarms & Timers", "Schedules a persistent reminder notification on your device.", "Remind me to take medicine at 8 PM", "Requires: POST_NOTIFICATIONS", Icons.Default.Notifications),
    CatalogToolItem("reminder_list", "View Reminders", "Alarms & Timers", "Lists all active upcoming reminders saved in Tavi.", "Show my reminders", "No special permissions", Icons.Default.Notifications),
    CatalogToolItem("reminder_cancel", "Cancel Reminder", "Alarms & Timers", "Cancels a scheduled reminder by title or query.", "Cancel reminder to take medicine", "No special permissions", Icons.Default.Notifications),

    // 4. Notes, Memory & Math (10)
    CatalogToolItem("notes_list", "View Notes", "Notes & Memory", "Lists private local notes saved on device.", "Show my notes", "Stored in app database", Icons.Default.Folder),
    CatalogToolItem("note_create", "Take a Note", "Notes & Memory", "Creates a persistent note stored privately on your phone.", "Take a note: Buy groceries", "Stored in app database", Icons.Default.Folder),
    CatalogToolItem("note_delete", "Delete Note", "Notes & Memory", "Deletes a private local note by title or identifier.", "Delete note #5", "Stored in app database", Icons.Default.Folder),
    CatalogToolItem("memory_save", "Remember Preference", "Notes & Memory", "Saves personal facts and preferences strictly on-device.", "Remember that my gate code is 4421", "100% private & on-device", Icons.Default.AutoAwesome),
    CatalogToolItem("memory_retrieve", "Recall Saved Facts", "Notes & Memory", "Answers questions using your stored private memories.", "What is my gate code?", "100% private & on-device", Icons.Default.AutoAwesome),
    CatalogToolItem("memory_delete", "Delete Memory", "Notes & Memory", "Deletes a specific remembered key/fact from local SQLite.", "Forget my gate code", "Stored in app database", Icons.Default.AutoAwesome),
    CatalogToolItem("memory_clear_all", "Clear All Memories", "Notes & Memory", "Wipes all stored personal memories with user confirmation.", "Clear all memories", "Requires confirmation", Icons.Default.AutoAwesome),
    CatalogToolItem("episodic_record", "Record Routine Record", "Notes & Memory", "Records episodic outcome and decisions for complex workflow steps.", "Save workflow record", "Stored in app database", Icons.Default.AutoAwesome),
    CatalogToolItem("calculator", "Math & Calculation", "Notes & Memory", "Computes arithmetic and math expressions offline.", "What is 15% tip on 85 dollars?", "Runs locally", Icons.Default.Calculate),
    CatalogToolItem("date_calculation", "Date & Days Calculation", "Notes & Memory", "Calculates elapsed days, future dates, and weekdays offline.", "How many days until Christmas?", "Runs locally", Icons.Default.CalendarMonth),

    // 5. Device & Settings (9)
    CatalogToolItem("device_toggle_flashlight", "Flashlight / Torch", "Device & Settings", "Turns your phone flashlight on or off.", "Turn on the flashlight", "Uses Camera torch API", Icons.Default.FlashlightOn),
    CatalogToolItem("device_get_status", "Battery & Storage", "Device & Settings", "Reads current battery percentage, charging state, and available storage.", "Check device battery and storage", "Reads local hardware status", Icons.Default.Settings),
    CatalogToolItem("network_get_status", "WiFi & Network", "Device & Settings", "Checks active WiFi, cellular data, and offline status.", "Am I connected to WiFi?", "Requires: ACCESS_NETWORK_STATE", Icons.Default.Wifi),
    CatalogToolItem("settings_open", "Open System Settings", "Device & Settings", "Opens specific Android settings panels (WiFi, Bluetooth, Display, Apps).", "Open WiFi settings", "Standard Android Intent", Icons.Default.Settings),
    CatalogToolItem("location_get_current", "Current Location", "Device & Settings", "Reads your current GPS coordinates locally.", "Where am I right now?", "Requires: ACCESS_FINE_LOCATION", Icons.Default.LocationOn),
    CatalogToolItem("maps_navigate", "Maps Navigation", "Device & Settings", "Opens turn-by-turn navigation in your maps app.", "Directions to nearest pharmacy", "Standard Maps Intent", Icons.Default.LocationOn),
    CatalogToolItem("camera_launch", "Open Camera", "Device & Settings", "Launches the default system camera app.", "Open camera", "Standard Camera Intent", Icons.Default.CameraAlt),
    CatalogToolItem("media_control", "Media Controls", "Device & Settings", "Controls active media playback (play, pause, next).", "Pause music", "Dispatches media key event", Icons.Default.MusicNote),
    CatalogToolItem("voice_speak", "Speak Aloud (TTS)", "Device & Settings", "Speaks assistant text output through on-device text-to-speech engine.", "Read aloud: Hello world", "Runs via TextToSpeech", Icons.Default.Mic),

    // 6. System & Apps (14)
    CatalogToolItem("app_launch", "Open App", "System & Apps", "Launches any installed app on your device.", "Open Spotify", "Standard Launcher Intent", Icons.Default.Apps),
    CatalogToolItem("app_list_launchable", "List Installed Apps", "System & Apps", "Lists all launchable applications installed on your phone.", "What apps do I have?", "Requires: QUERY_ALL_PACKAGES", Icons.Default.Apps),
    CatalogToolItem("app_open_settings", "App Info & Settings", "System & Apps", "Opens the system App Info settings screen for a specified app.", "Open app info for WhatsApp", "Standard Android Intent", Icons.Default.Apps),
    CatalogToolItem("browser_open_url", "Open Web Link", "System & Apps", "Opens a URL safely in your default browser.", "Open https://wikipedia.org", "Standard Browser Intent", Icons.Default.Language),
    CatalogToolItem("browser_open_page", "Open Browser Page", "System & Apps", "Directly opens a specific webpage URL.", "Go to https://news.ycombinator.com", "Standard Browser Intent", Icons.Default.Language),
    CatalogToolItem("browser_research", "Offline Browser Research", "System & Apps", "Extracts readable text and topics from a local or open page.", "Research this page", "Runs locally on-device", Icons.Default.Language),
    CatalogToolItem("files_browse_document", "Browse Files", "System & Apps", "Opens system Storage Access Framework document picker.", "Open documents", "Standard SAF Intent", Icons.Default.Folder),
    CatalogToolItem("files_export_note", "Export Note to File", "System & Apps", "Exports local note text into an external text file.", "Export my meeting note", "Standard SAF Intent", Icons.Default.Folder),
    CatalogToolItem("share_content", "Share Sheet", "System & Apps", "Displays the native Android share sheet with custom text.", "Share this note", "Standard Share Intent", Icons.Default.Share),
    CatalogToolItem("clipboard_read", "Read Clipboard", "System & Apps", "Reads text currently copied to your system clipboard.", "What is on my clipboard?", "Uses ClipboardManager", Icons.Default.ContentCopy),
    CatalogToolItem("clipboard_copy", "Copy to Clipboard", "System & Apps", "Copies specified text to your device clipboard.", "Copy to clipboard: Meeting notes", "Uses ClipboardManager", Icons.Default.ContentCopy),
    CatalogToolItem("accessibility_read_screen", "Read Active Screen", "System & Apps", "Reads UI elements and text on your screen for accessibility.", "Read my screen", "Requires: Accessibility Service", Icons.Default.Visibility),
    CatalogToolItem("accessibility_perform_action", "Perform Screen Action", "System & Apps", "Clicks or scrolls UI elements on-screen on your behalf.", "Click the Submit button", "Requires: Accessibility Service", Icons.Default.Visibility),
    CatalogToolItem("usage_stats", "Screen Time & Usage", "System & Apps", "Summarizes app usage and screen time for today.", "How much did I use my phone today?", "Requires: Usage Access", Icons.Default.Android)
)

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

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

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

    val categories = listOf("All", "Messages & Calls", "Calendar", "Alarms & Timers", "Notes & Memory", "Device & Settings", "System & Apps")

    val filteredTools = remember(searchQuery, selectedCategory) {
        ALL_CATALOG_TOOLS.filter { tool ->
            val matchesCategory = selectedCategory == "All" || tool.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                tool.title.contains(searchQuery, ignoreCase = true) ||
                tool.description.contains(searchQuery, ignoreCase = true) ||
                tool.examplePrompt.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Capabilities & Access", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = BrandPrimary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("What Can I Ask?", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("System Permissions", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            if (selectedTabIndex == 0) {
                // TAB 0: Interactive Feature Catalog
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search tools or actions (e.g. flashlight, sms, alarm)...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        // Category Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = cat == selectedCategory
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${filteredTools.size} actions available • Tap 'Try asking' to run in chat",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(filteredTools, key = { it.id }) { tool ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(tool.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = tool.permissionNote,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = tool.description,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(10.dp))
                                // Try Asking Button
                                Surface(
                                    onClick = {
                                        MainActivity.sharedIncomingText.value = tool.examplePrompt
                                        onBack()
                                    },
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Try asking: ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "\"${tool.examplePrompt}\"",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "RUN ➔",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            } else {
                // TAB 1: System Permissions & Roles
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        // Platform Overview Card
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
                                    text = "Level 2 Android System Access Manager. Tavi uses official Android roles and system permissions to integrate with device features while operating strictly on-device without cloud telemetry.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "System Roles & Special Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Privileged system integrations requiring explicit enablement in Android Settings.",
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

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Standard Android Capabilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Device tools governed by standard Android runtime permissions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(capabilityGroups, key = { it.title }) { group ->
                        CapabilityCard(
                            group = group,
                            onRequestPermissions = { permissions -> permissionLauncher.launch(permissions.toTypedArray()) },
                            onOpenSettings = { accessType ->
                                try {
                                    context.startActivity(PermissionCoordinator.getSettingsIntent(accessType, context))
                                } catch (_: Exception) {
                                    context.startActivity(PermissionCoordinator.getAppDetailsSettingsIntent(context))
                                }
                            }
                        )
                    }

                    // Audit Log
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Audit Log & Verifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tamper-evident record of consequential tool executions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (auditLogs.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No audit log entries yet. Executed tools will record verified actions here.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(auditLogs.take(15), key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when {
                                        log.status == "SUCCESS" -> Icons.Default.CheckCircle
                                        log.status.contains("BLOCKED") -> Icons.Default.Lock
                                        log.status.contains("AWAITING") -> Icons.Default.Warning
                                        else -> Icons.Default.Info
                                    }
                                    val tint = when {
                                        log.status == "SUCCESS" -> SuccessGreen
                                        log.status.contains("BLOCKED") -> MaterialTheme.colorScheme.error
                                        log.status.contains("AWAITING") -> WarningAmber
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(log.toolName.replace("_", " ").replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        isGrantedOrHeld -> SuccessGreen
        isAvailable -> WarningAmber
        else -> MaterialTheme.colorScheme.outline
    }

    val badgeText = when (item.status) {
        AccessStatus.ROLE_HELD -> "ACTIVE"
        AccessStatus.GRANTED -> "ENABLED"
        AccessStatus.ROLE_AVAILABLE -> "AVAILABLE"
        AccessStatus.NOT_GRANTED -> "NOT ENABLED"
        AccessStatus.ROLE_UNAVAILABLE -> "NOT SUPPORTED ON DEVICE"
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
                    val icon = if (item.isRole) Icons.Default.Shield else Icons.Default.Security
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (item.isRole) "System Role" else "Special Access",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(item.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Security Note
            Text(
                text = "Privacy: " + item.securityNotice,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            // Action button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when {
                    isGrantedOrHeld -> {
                        OutlinedButton(
                            onClick = onAction,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
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
                        color = if (group.isGranted) SuccessGreen else WarningAmber
                    )
                }
            }

            Text(group.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Supported Tools with human readable names
            Text(
                text = "Features: " + group.supportedOperations.map { it.replace("_", " ").replaceFirstChar { c -> c.uppercase() } }.joinToString(", "),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
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
