package com.example.tavi.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.tavi.language.AssistantLanguage
import com.example.tavi.voice.VoiceManager

@Composable
fun LanguageSettingsPanel(voice: VoiceManager) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AssistantLanguage.selected(context)) }
    val available by voice.offlineVoiceAvailable.collectAsState()
    Column {
        Text("Assistant language", style = MaterialTheme.typography.titleMedium)
        Row { listOf("auto" to "Device", "en" to "English", "sw" to "Kiswahili").forEach { (code, label) ->
            TextButton(onClick = { AssistantLanguage.set(context, code); selected = code }) { Text(if (selected == code) "✓ $label" else label) }
        } }
        Text(if (available) "Offline speech voice available." else "Download a voice below for independent offline speech output.")
        LocalPacksPanel(setOf("speech-recognition", "speech-recognition-sw", "voice-en", "voice-sw"))
        Text("Common commands support English/Kiswahili mixing. Use 24-hour reminder times: ‘nikumbushe kupiga simu kesho saa 14:30’. Neural language quality depends on your model.", style = MaterialTheme.typography.bodySmall)
    }
}
