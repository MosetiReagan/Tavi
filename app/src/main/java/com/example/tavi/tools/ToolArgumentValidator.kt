package com.example.tavi.tools

import org.json.JSONObject

/** Checks untrusted model arguments before permissions, confirmation, or native execution. */
object ToolArgumentValidator {
    fun validate(schemaJson: String, arguments: Map<String, Any?>): String? = try {
        if (arguments.keys.any { it.startsWith("__") }) "Reserved host arguments are not allowed."
        else check(JSONObject(schemaJson), arguments, "arguments", 0)
    } catch (_: Exception) { "Invalid tool parameter schema." }

    private fun check(schema: JSONObject, value: Any?, path: String, depth: Int): String? {
        if (depth > 12) return "$path is nested too deeply."
        val valid = when (schema.optString("type")) {
            "object" -> value is Map<*, *>
            "array" -> value is List<*>
            "string" -> value is String
            "boolean" -> value is Boolean
            "number" -> value is Number && value.toDouble().isFinite()
            "integer" -> value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0
            "null" -> value == null
            else -> true
        }
        if (!valid) return "$path must be ${schema.optString("type")}."
        schema.optJSONArray("enum")?.let { options ->
            if ((0 until options.length()).none { options.opt(it) == value }) return "$path is not an allowed value."
        }
        if (value is String) {
            if (value.length < schema.optInt("minLength", 0)) return "$path is too short."
            if (value.length > schema.optInt("maxLength", 100_000)) return "$path is too long."
        }
        if (value is Number) {
            if (schema.has("minimum") && value.toDouble() < schema.getDouble("minimum")) return "$path is below the minimum."
            if (schema.has("maximum") && value.toDouble() > schema.getDouble("maximum")) return "$path exceeds the maximum."
        }
        if (value is Map<*, *>) {
            val required = schema.optJSONArray("required")
            if (required != null) for (i in 0 until required.length()) {
                val key = required.getString(i)
                if (!value.containsKey(key) || value[key] == null) return "$path.$key is required."
            }
            val properties = schema.optJSONObject("properties") ?: JSONObject()
            for ((key, item) in value) {
                if (key !is String || key.startsWith("__")) return "$path contains an invalid key."
                val child = properties.optJSONObject(key)
                if (child != null) check(child, item, "$path.$key", depth + 1)?.let { return it }
                else if (schema.opt("additionalProperties") == false) return "$path.$key is not supported."
            }
        }
        if (value is List<*>) {
            if (value.size > schema.optInt("maxItems", 1000)) return "$path contains too many items."
            schema.optJSONObject("items")?.let { itemSchema ->
                value.forEachIndexed { index, item -> check(itemSchema, item, "$path[$index]", depth + 1)?.let { return it } }
            }
        }
        return null
    }
}
