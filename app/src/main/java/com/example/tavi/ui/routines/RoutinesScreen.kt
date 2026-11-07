package com.example.tavi.ui.routines

import android.Manifest
import android.os.Build
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
import com.example.tavi.routines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(onBack: () -> Unit, onUse: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { RoutineStore(context) }
    val scope = rememberCoroutineScope()
    var routines by remember { mutableStateOf(emptyList<PersonalRoutine>()) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PersonalRoutine?>(null) }
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var weekdays by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<PersonalRoutine?>(null) }
    fun save(routine: PersonalRoutine) { scope.launch {
        try { withContext(Dispatchers.IO) { store.save(routine); RoutineScheduler.schedule(context, routine) }
            routines = withContext(Dispatchers.IO) { store.list() }; creating = false; error = null }
        catch (e: Exception) { error = e.message ?: "Could not save routine." }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val routine = pendingSave; pendingSave = null
        if (granted && routine != null) save(routine) else error = "Notifications are needed for scheduled routines. You can save without a time."
    }
    LaunchedEffect(Unit) { routines = withContext(Dispatchers.IO) { store.list() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Personal routines") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Save a reusable assistant request. Scheduled routines remind you to review and run it; consequential steps still need confirmation. Android may delay routine reminders.")
                Button(onClick = { editing = null; title = ""; prompt = ""; time = ""; creating = true }) { Text("New routine") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            items(routines, key = { it.id }) { routine -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(routine.title, style = MaterialTheme.typography.titleMedium); Text(routine.prompt)
                Text(if (routine.time.isBlank()) "Run when needed" else "${routine.time} • ${if (routine.weekdaysOnly) "weekdays" else "daily"}")
                Row {
                    TextButton(onClick = { onUse(routine.prompt) }) { Text("Review and run") }
                    TextButton(onClick = { editing = routine; title = routine.title; prompt = routine.prompt; time = routine.time; weekdays = routine.weekdaysOnly; creating = true }) { Text("Edit") }
                    Switch(checked = routine.enabled, onCheckedChange = { enabled -> scope.launch {
                        withContext(Dispatchers.IO) { store.setEnabled(routine.id, enabled); RoutineScheduler.schedule(context, routine.copy(enabled = enabled)) }
                        routines = withContext(Dispatchers.IO) { store.list() }
                    } })
                    TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { RoutineScheduler.cancel(context, routine.id); store.delete(routine.id) }; routines = withContext(Dispatchers.IO) { store.list() } } }) { Text("Delete") }
                }
            } } }
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("Save routine") }, text = { Column {
        Row { TextButton(onClick = { title = "Morning briefing"; prompt = "morning routine" }) { Text("Morning") }
            TextButton(onClick = { title = "Meeting preparation"; prompt = "where is my next meeting and what notes do i have" }) { Text("Meeting") }
            TextButton(onClick = { title = "Bedtime"; prompt = "bedtime routine" }) { Text("Bedtime") } }
        OutlinedTextField(title, { title = it }, label = { Text("Name") })
        OutlinedTextField(prompt, { prompt = it }, label = { Text("Assistant request") })
        OutlinedTextField(time, { time = it }, label = { Text("Optional reminder time (HH:mm)") })
        Row { Text("Weekdays only"); Switch(weekdays, { weekdays = it }) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = {
        try {
            require(title.isNotBlank() && prompt.isNotBlank()) { "Enter a name and request." }
            if (time.isNotBlank()) java.time.LocalTime.parse(time)
            val routine = PersonalRoutine(id = editing?.id ?: java.util.UUID.randomUUID().toString(), title = title, prompt = prompt, time = time, weekdaysOnly = weekdays)
            if (time.isNotBlank() && Build.VERSION.SDK_INT >= 33 && !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                pendingSave = routine; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else save(routine)
        } catch (e: Exception) { error = "Enter a name, request, and a valid 24-hour time such as 07:30." }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
}
