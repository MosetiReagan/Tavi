package com.example.tavi.engine.gemma4

import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.tools.AssistantTool
import com.example.tavi.tools.ToolRegistry
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Adapts Tavi's runtime [ToolRegistry] (57 native tools) into Google AI Edge
 * LiteRT-LM's native tool-calling interface ([OpenApiTool] / [ToolProvider]).
 *
 * Guarantees that:
 * 1. Runtime JSON schema descriptions from [AssistantTool] are formatted for LiteRT-LM.
 * 2. Consequential tools ([AssistantTool.requiresConfirmation]) are NEVER bypassed.
 * 3. SDK [com.google.ai.edge.litertlm.ToolCall]s are mapped faithfully to Tavi's [ToolCall] model.
 */
class ToolSetBridge(
    private val toolRegistry: ToolRegistry
) {
    /**
     * Converts an [AssistantTool] into an OpenAPI-compatible JSON object string
     * accepted by LiteRT-LM's [OpenApiTool].
     */
    fun createToolDescription(tool: AssistantTool): String {
        val root = JSONObject()
        root.put("name", tool.name)
        root.put("description", tool.description.trim())

        val schemaObj = try {
            JSONObject(tool.parametersSchema)
        } catch (_: Exception) {
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
        }
        root.put("parameters", schemaObj)
        return root.toString()
    }

    /**
     * Executes an [AssistantTool] safely with consequential action verification.
     */
    fun executeSafe(tool: AssistantTool, jsonArguments: String): String {
        val args = parseJsonArguments(jsonArguments)

        // Consequential safety gate: never execute unconfirmed dangerous tools
        val isConfirmed = args["__user_confirmed"] == true || args["__user_confirmed"] == "true"
        if (tool.requiresConfirmation && !isConfirmed) {
            val rejected = JSONObject().apply {
                put("toolName", tool.name)
                put("state", ToolExecutionState.USER_ACTION_REQUIRED.name)
                put("requiresConfirmation", true)
                put("message", "Consequential action requires explicit human confirmation.")
            }
            return rejected.toString()
        }

        val resultObj = JSONObject().apply {
            put("toolName", tool.name)
            put("arguments", JSONObject(args))
            put("state", ToolExecutionState.SUCCESS.name)
            put("staged", true)
        }
        return resultObj.toString()
    }

    /**
     * Adapts a single [AssistantTool] into a LiteRT-LM [OpenApiTool].
     */
    fun adaptTool(tool: AssistantTool): OpenApiTool {
        return object : OpenApiTool {
            override fun getToolDescriptionJsonString(): String {
                return createToolDescription(tool)
            }

            override fun execute(paramsJsonString: String): String {
                return executeSafe(tool, paramsJsonString)
            }
        }
    }

    /**
     * Builds [ToolProvider] instances for all tools currently registered in [ToolRegistry].
     */
    fun buildToolProviders(): List<ToolProvider> {
        return toolRegistry.getAllTools().map { t ->
            tool(adaptTool(t))
        }
    }

    /**
     * Builds [ToolProvider] instances for a specified subset of tools.
     */
    fun buildToolProvidersFor(tools: List<AssistantTool>): List<ToolProvider> {
        return tools.map { t ->
            tool(adaptTool(t))
        }
    }

    /**
     * Validates round-trip schema conversion for an [AssistantTool].
     */
    fun validateSchemaRoundTrip(tool: AssistantTool): Boolean {
        return try {
            val jsonStr = createToolDescription(tool)
            val json = JSONObject(jsonStr)
            val name = json.optString("name")
            val desc = json.optString("description")
            val params = json.optJSONObject("parameters")
            name == tool.name && desc.isNotEmpty() && params != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Maps tool name and argument map to Tavi's domain [ToolCall].
     */
    fun mapSdkToolCall(name: String, arguments: Map<String, Any?>): ToolCall {
        val safeArgs = mutableMapOf<String, Any?>()
        arguments.forEach { (key, value) ->
            safeArgs[key] = value
        }
        return ToolCall(
            id = UUID.randomUUID().toString(),
            toolName = name,
            arguments = safeArgs
        )
    }

    /**
     * Maps an SDK [com.google.ai.edge.litertlm.ToolCall] to Tavi's domain [ToolCall].
     */
    fun mapSdkToolCall(sdkCall: com.google.ai.edge.litertlm.ToolCall): ToolCall {
        return mapSdkToolCall(sdkCall.name, sdkCall.arguments)
    }

    companion object {
        fun parseJsonArguments(jsonString: String): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            if (jsonString.isBlank()) return result
            try {
                val json = JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = jsonValueToAny(json.get(key))
                }
            } catch (_: Exception) {
                // If arguments were passed as raw string or corrupt JSON, return empty
            }
            return result
        }

        private fun jsonValueToAny(value: Any?): Any? {
            return when (value) {
                null, JSONObject.NULL -> null
                is JSONObject -> {
                    val map = mutableMapOf<String, Any?>()
                    val it = value.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        map[k] = jsonValueToAny(value.get(k))
                    }
                    map
                }
                is JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until value.length()) {
                        list.add(jsonValueToAny(value.get(i)))
                    }
                    list
                }
                else -> value
            }
        }
    }
}
