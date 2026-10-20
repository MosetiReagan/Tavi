package com.example.tavi.tools.messages

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.tavi.model.ToolResult
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

data class NotificationItem(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

object NotificationStore {
    private const val MAX_SAVED = 30
    private val buffer = ConcurrentLinkedQueue<NotificationItem>()

    fun add(item: NotificationItem) {
        buffer.add(item)
        while (buffer.size > MAX_SAVED) {
            buffer.poll()
        }
    }

    fun getRecent(limit: Int = 10): List<NotificationItem> {
        return buffer.toList().takeLast(limit)
    }

    fun clear() {
        buffer.clear()
    }
}

class TaviNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        // Ignore our own notifications
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isNotEmpty() || text.isNotEmpty()) {
            NotificationStore.add(
                NotificationItem(
                    id = sbn.key ?: sbn.id.toString(),
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postTime = sbn.postTime
                )
            )
        }
    }
}

class NotificationSummaryTool : AssistantTool {
    override val name: String = "notifications_read_summary"
    override val description: String = "Reads authorized recent notifications and summarizes active alerts or messages."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "limit": {"type": "integer", "description": "Number of recent notifications to inspect (default 5, max 15)"},
            "packageFilter": {"type": "string", "description": "Optional app package name filter"}
          }
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList() // Uses NotificationListenerService access
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 15)
        val packageFilter = arguments["packageFilter"]?.toString()?.trim()

        val recent = NotificationStore.getRecent(limit)
        val notifArray = JSONArray()

        for (item in recent) {
            if (!packageFilter.isNullOrEmpty() && !item.packageName.contains(packageFilter, ignoreCase = true)) {
                continue
            }
            // Sanitize notification text as untrusted external content
            val sanitizedText = SecurityGuard.sanitizeExternalContent(
                "${item.title}: ${item.text}",
                "Notification from ${item.packageName}"
            )
            val jsonItem = JSONObject().apply {
                put("app", item.packageName)
                put("postTime", item.postTime)
                put("content", sanitizedText)
            }
            notifArray.put(jsonItem)
        }

        val resultObj = JSONObject().apply {
            put("count", notifArray.length())
            put("notifications", notifArray)
            if (notifArray.length() == 0) {
                put("note", "No active notifications captured in buffer. Ensure Tavi Notification Listener access is enabled in Android Settings.")
            }
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true
        )
    }
}
