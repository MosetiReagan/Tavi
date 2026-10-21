package com.example.tavi.tools.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONObject
import java.net.URLEncoder

class LocationGetCurrentTool : AssistantTool {
    override val name: String = "location_get_current"
    override val category: ToolCategory = ToolCategory.LOCATION
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Reads device's current foreground location coordinates if permission is granted."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {}
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse && !hasFine) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.PERMISSION_REQUIRED,
                requiredPermission = Manifest.permission.ACCESS_COARSE_LOCATION,
                errorMessage = "Location permission is not granted. Please enable Location in Settings or grant the permission."
            )
        }

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val location: Location? = try {
                val gpsLoc = if (hasFine) lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                val netLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val passLoc = lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                gpsLoc ?: netLoc ?: passLoc
            } catch (_: SecurityException) {
                null
            }

            if (location != null) {
                val obj = JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracyMeters", location.accuracy)
                    put("timeEpochMillis", location.time)
                    put("status", "LOCATION_ACQUIRED")
                }
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = obj.toString(),
                    isSuccess = true,
                    state = ToolExecutionState.SUCCESS
                )
            } else {
                ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = JSONObject().apply {
                        put("status", "NO_RECENT_FIX")
                        put("message", "Location enabled but no recent fix available from GPS or network provider.")
                    }.toString(),
                    isSuccess = false,
                    state = ToolExecutionState.NOT_FOUND,
                    errorMessage = "Location is active, but no recent coordinates are cached on the device."
                )
            }
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Failed to query location: ${e.message}"
            )
        }
    }
}

class MapsNavigateTool : AssistantTool {
    override val name: String = "maps_navigate"
    override val category: ToolCategory = ToolCategory.LOCATION
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens maps or navigation to a destination or search query."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "destination": {"type": "string", "description": "Destination address, place name, or coordinates"}
          },
          "required": ["destination"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val dest = arguments["destination"]?.toString()?.trim() ?: ""
        if (dest.isEmpty()) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                state = ToolExecutionState.ERROR,
                errorMessage = "Destination is required."
            )
        }

        val encoded = URLEncoder.encode(dest, "UTF-8")
        val uri = Uri.parse("geo:0,0?q=$encoded")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(mapIntent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "MAPS_LAUNCHED")
                    put("destination", dest)
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
                errorMessage = "Failed to open maps application: ${e.message}"
            )
        }
    }
}
