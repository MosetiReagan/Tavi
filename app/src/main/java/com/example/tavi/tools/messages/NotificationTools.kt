package com.example.tavi.tools.messages

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.PermissionCoordinator
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

data class NotificationItem(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val key: String = id
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

    companion object {
        var instance: TaviNotificationListenerService? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

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
                    postTime = sbn.postTime,
                    key = sbn.key ?: sbn.id.toString()
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

class NotificationSummaryTool : AssistantTool {
    override val name: String = "notifications_read_summary"
    override val category: ToolCategory = ToolCategory.NOTIFICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val specialAccessType: SpecialAccessType = SpecialAccessType.NOTIFICATION_LISTENER
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
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (!PermissionCoordinator.hasSpecialAccess(context, SpecialAccessType.NOTIFICATION_LISTENER)) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
                errorMessage = "Notification Listener Access is not granted. Please enable 'Tavi Notification Service' in Android Notification Access settings."
            )
        }

        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 15)
        val packageFilter = arguments["packageFilter"]?.toString()?.trim()

        val notifArray = JSONArray()

        // 1. First inspect currently active status bar notifications from active service instance if available
        val activeSbns = try {
            TaviNotificationListenerService.instance?.activeNotifications?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val seenKeys = mutableSetOf<String>()

        for (sbn in activeSbns) {
            if (sbn.packageName == context.packageName) continue
            if (!packageFilter.isNullOrEmpty() && !sbn.packageName.contains(packageFilter, ignoreCase = true)) continue

            val extras = sbn.notification.extras ?: continue
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isEmpty() && text.isEmpty()) continue

            val key = sbn.key ?: sbn.id.toString()
            seenKeys.add(key)

            val sanitizedText = SecurityGuard.sanitizeExternalContent("$title: $text", "Notification: ${sbn.packageName}")
            val jsonItem = JSONObject().apply {
                put("key", key)
                put("app", sbn.packageName)
                put("title", title)
                put("postTime", sbn.postTime)
                put("content", sanitizedText)
                put("isActive", true)
            }
            notifArray.put(jsonItem)
            if (notifArray.length() >= limit) break
        }

        // 2. If active notifications are fewer than limit, supplement with buffered notifications
        if (notifArray.length() < limit) {
            val buffered = NotificationStore.getRecent(limit)
            for (item in buffered) {
                if (seenKeys.contains(item.key)) continue
                if (!packageFilter.isNullOrEmpty() && !item.packageName.contains(packageFilter, ignoreCase = true)) continue

                val sanitizedText = SecurityGuard.sanitizeExternalContent("${item.title}: ${item.text}", "Notification: ${item.packageName}")
                val jsonItem = JSONObject().apply {
                    put("key", item.key)
                    put("app", item.packageName)
                    put("title", item.title)
                    put("postTime", item.postTime)
                    put("content", sanitizedText)
                    put("isActive", false)
                }
                notifArray.put(jsonItem)
                if (notifArray.length() >= limit) break
            }
        }

        val resultObj = JSONObject().apply {
            put("count", notifArray.length())
            put("notifications", notifArray)
            if (notifArray.length() == 0) {
                put("note", "No active notifications present on device right now.")
            }
        }

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultObj.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}

class NotificationDismissTool : AssistantTool {
    override val name: String = "notifications_dismiss"
    override val category: ToolCategory = ToolCategory.NOTIFICATIONS
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM
    override val specialAccessType: SpecialAccessType = SpecialAccessType.NOTIFICATION_LISTENER
    override val description: String = "Dismisses a notification by its key or clears notifications."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "key": {"type": "string", "description": "The unique key of the notification to dismiss"}
          },
          "required": ["key"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val key = arguments["key"]?.toString()?.trim()
        if (key.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Notification key is required to dismiss."
            )
        }

        val listener = TaviNotificationListenerService.instance
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
                errorMessage = "Notification listener service is not actively connected. Please verify notification access."
            )

        return try {
            listener.cancelNotification(key)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("key", key)
                    put("status", "DISMISSED")
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to dismiss notification: ${e.message}"
            )
        }
    }
}
