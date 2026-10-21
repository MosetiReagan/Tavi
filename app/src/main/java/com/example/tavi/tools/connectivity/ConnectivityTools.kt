package com.example.tavi.tools.connectivity

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONObject

class NetworkGetStatusTool : AssistantTool {
    override val name: String = "network_get_status"
    override val category: ToolCategory = ToolCategory.CONNECTIVITY
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Inspects local network connectivity (Wi-Fi, cellular, metered status, offline state)."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {}
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(Manifest.permission.ACCESS_NETWORK_STATE)
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.ACCESS_NETWORK_STATE,
                errorMessage = "ACCESS_NETWORK_STATE permission is required."
            )
        }

        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)

            val isConnected = caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isMetered = cm?.isActiveNetworkMetered == true

            val isAirplaneMode = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            } catch (_: Exception) {
                false
            }

            val transport = when {
                isWifi -> "WIFI"
                isCellular -> "CELLULAR"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
                else -> "NONE"
            }

            val obj = JSONObject().apply {
                put("isConnected", isConnected)
                put("transport", transport)
                put("isMetered", isMetered)
                put("isAirplaneMode", isAirplaneMode)
                put("isOffline", !isConnected || isAirplaneMode)
            }

            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = obj.toString(),
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
                errorMessage = "Failed to inspect connectivity: ${e.message}"
            )
        }
    }
}
