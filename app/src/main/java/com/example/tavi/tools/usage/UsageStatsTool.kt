package com.example.tavi.tools.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.PermissionCoordinator
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UsageStatsTool : AssistantTool {
    override val name: String = "usage_get_stats"
    override val category: ToolCategory = ToolCategory.USAGE
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val specialAccessType: SpecialAccessType = SpecialAccessType.USAGE_STATS
    override val description: String = "Retrieves authorized app usage statistics, daily screen time, and recently used apps."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "days": {"type": "integer", "description": "Number of days of history to inspect (1-7, default 1)"},
            "limit": {"type": "integer", "description": "Maximum number of top used apps to return (default 10)"},
            "packageFilter": {"type": "string", "description": "Optional package name or app name query"}
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
        if (!PermissionCoordinator.hasSpecialAccess(context, SpecialAccessType.USAGE_STATS)) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.USER_ACTION_REQUIRED,
                settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS,
                errorMessage = "Usage Access is not granted. Please enable Usage Access for Tavi in Android Settings to inspect screen time and app usage."
            )
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "UsageStatsManager is unavailable on this device."
            )

        val days = (arguments["days"]?.toString()?.toIntOrNull() ?: 1).coerceIn(1, 7)
        val limit = (arguments["limit"]?.toString()?.toIntOrNull() ?: 10).coerceIn(1, 25)
        val packageFilter = arguments["packageFilter"]?.toString()?.trim()

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(days.toLong())

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (stats.isNullOrEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("count", 0)
                    put("note", "No usage data recorded for the requested period.")
                }.toString(),
                isSuccess = true,
                state = ToolExecutionState.SUCCESS
            )
        }

        val pm = context.packageManager
        // Aggregate foreground time by package
        val aggregated = mutableMapOf<String, Pair<Long, Long>>() // pkg -> (totalForegroundTime, lastTimeUsed)

        for (u in stats) {
            val current = aggregated[u.packageName]
            val totalTime = (current?.first ?: 0L) + u.totalTimeInForeground
            val lastUsed = maxOf(current?.second ?: 0L, u.lastTimeUsed)
            aggregated[u.packageName] = Pair(totalTime, lastUsed)
        }

        // Filter and sort by total time descending
        val sortedList = aggregated.entries
            .filter { it.value.first > 1000 } // only apps with > 1 second usage
            .sortedByDescending { it.value.first }

        val appsArray = JSONArray()
        var totalScreenTimeMs = 0L

        for ((pkg, data) in sortedList) {
            val totalTime = data.first
            val lastUsed = data.second

            val appLabel = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }

            if (!packageFilter.isNullOrEmpty()) {
                val matchesPkg = pkg.contains(packageFilter, ignoreCase = true)
                val matchesLabel = appLabel.contains(packageFilter, ignoreCase = true)
                if (!matchesPkg && !matchesLabel) continue
            }

            totalScreenTimeMs += totalTime

            if (appsArray.length() < limit) {
                appsArray.put(
                    JSONObject().apply {
                        put("appName", appLabel)
                        put("package", pkg)
                        put("foregroundTimeFormatted", formatDuration(totalTime))
                        put("foregroundTimeMs", totalTime)
                        put("lastTimeUsed", lastUsed)
                    }
                )
            }
        }

        val resultJson = JSONObject().apply {
            put("periodDays", days)
            put("totalScreenTimeFormatted", formatDuration(totalScreenTimeMs))
            put("totalScreenTimeMs", totalScreenTimeMs)
            put("topAppsCount", appsArray.length())
            put("topApps", appsArray)
        }.toString()

        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = resultJson,
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
