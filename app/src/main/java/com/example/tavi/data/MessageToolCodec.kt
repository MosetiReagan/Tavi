package com.example.tavi.data

import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolExecutionState
import org.json.JSONArray
import org.json.JSONObject

/** Structured evidence survives restart. Host authorization is deliberately never persisted. */
object MessageToolCodec {
    fun encodeCalls(calls: List<ToolCall>): String = JSONArray().apply {
        calls.forEach { call -> put(JSONObject().put("id", call.id).put("tool", call.toolName)
            .put("arguments", JSONObject(call.arguments.filterKeys { !it.startsWith("__") }))) }
    }.toString()

    fun decodeCalls(json: String?): List<ToolCall> = entries(json).mapNotNull { item ->
        val name = item.optString("tool")
        if (name.isBlank()) null else ToolCall(id = item.optString("id"), toolName = name,
            arguments = toMap(item.optJSONObject("arguments") ?: JSONObject()).filterKeys { !it.startsWith("__") })
    }

    fun encodeResults(results: List<ToolResult>): String = JSONArray().apply {
        results.forEach { result -> put(JSONObject().put("id", result.toolCallId).put("tool", result.toolName)
            .put("json", result.resultJson).put("success", result.isSuccess).put("state", result.state.name)
            .put("error", result.errorMessage).put("permission", result.requiredPermission)
            .put("settings", result.settingsAction).put("options", JSONArray(result.ambiguousOptions))) }
    }.toString()

    fun decodeResults(json: String?): List<ToolResult> = entries(json).mapNotNull { item ->
        val name = item.optString("tool")
        if (name.isBlank()) null else ToolResult(toolCallId = item.optString("id"), toolName = name,
            resultJson = item.optString("json", "{}"), isSuccess = item.optBoolean("success", false),
            state = runCatching { ToolExecutionState.valueOf(item.optString("state")) }.getOrDefault(ToolExecutionState.ERROR),
            errorMessage = item.nullable("error"), requiredPermission = item.nullable("permission"),
            settingsAction = item.nullable("settings"), ambiguousOptions = (item.optJSONArray("options") ?: JSONArray())
                .let { arr -> (0 until arr.length()).map { arr.optString(it) } })
    }

    private fun entries(json: String?): List<JSONObject> = runCatching {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else optString(key)
    fun toMap(obj: JSONObject): Map<String, Any?> = obj.keys().asSequence().associateWith { key -> value(obj.opt(key)) }
    private fun value(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { value(v.opt(it)) }
        else -> v
    }
}
