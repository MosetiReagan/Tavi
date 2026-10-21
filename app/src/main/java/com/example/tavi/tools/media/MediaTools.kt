package com.example.tavi.tools.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.view.KeyEvent
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.AssistantTool
import org.json.JSONObject

class CameraLaunchTool : AssistantTool {
    override val name: String = "camera_launch"
    override val category: ToolCategory = ToolCategory.MEDIA
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens the device Camera application to take a photo."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {}
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply { put("status", "CAMERA_LAUNCHED") }.toString(),
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
                errorMessage = "Failed to launch camera: ${e.message}"
            )
        }
    }
}

class MediaControlTool : AssistantTool {
    override val name: String = "media_control"
    override val category: ToolCategory = ToolCategory.MEDIA
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Sends media playback control commands (play_pause, next, previous)."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "action": {
              "type": "string",
              "enum": ["play_pause", "next", "previous"],
              "description": "Media playback action"
            }
          },
          "required": ["action"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val action = arguments["action"]?.toString()?.trim() ?: "play_pause"
        val keyCode = when (action) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

            ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = JSONObject().apply {
                    put("status", "MEDIA_COMMAND_DISPATCHED")
                    put("action", action)
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
                errorMessage = "Failed to dispatch media command: ${e.message}"
            )
        }
    }
}

class VoiceSpeakTool : AssistantTool {
    override val name: String = "voice_speak"
    override val category: ToolCategory = ToolCategory.MEDIA
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Speaks the provided text aloud using Android Text-To-Speech."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "text": {"type": "string", "description": "Text to read aloud"}
          },
          "required": ["text"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val text = arguments["text"]?.toString()?.trim() ?: ""
        return ToolResult(
            toolCallId = toolCallId,
            toolName = name,
            resultJson = JSONObject().apply {
                put("status", "TTS_REQUESTED")
                put("text", text)
            }.toString(),
            isSuccess = true,
            state = ToolExecutionState.SUCCESS
        )
    }
}
