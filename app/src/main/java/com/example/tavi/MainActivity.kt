package com.example.tavi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.tavi.theme.TaviTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

  companion object {
    val sharedIncomingText = MutableStateFlow<String?>(null)
    val triggerAssistantVoice = MutableStateFlow(false)
    val shortcutAction = MutableStateFlow<String?>(null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIncomingIntent(intent)

    enableEdgeToEdge()
    setContent {
      TaviTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return

    // 1. Assistant invocation (Long-press power / Corner swipe / Voice interaction)
    if (intent.getBooleanExtra("EXTRA_FROM_ASSISTANT_INVOCATION", false) ||
        intent.action == Intent.ACTION_ASSIST ||
        intent.action == Intent.ACTION_VOICE_COMMAND ||
        intent.action == "com.example.tavi.shortcut.VOICE"
    ) {
      triggerAssistantVoice.value = true
    }

    // 2. App Shortcuts
    if (intent.action?.startsWith("com.example.tavi.shortcut.") == true) {
      shortcutAction.value = intent.action
    }

    // 3. Share target
    if (intent.action == Intent.ACTION_SEND && (intent.type == "text/plain" || intent.type?.startsWith("text/") == true)) {
      val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
      if (!sharedText.isNullOrEmpty()) {
        sharedIncomingText.value = sharedText
      }
    }
  }
}
