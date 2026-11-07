package com.example.tavi.tools

import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import java.security.MessageDigest
import org.json.JSONObject

data class ExecutionReceipt(val fingerprint: String, val result: ToolResult?)

interface ExecutionReceiptStore {
    fun claim(id: String, fingerprint: String): Boolean
    fun read(id: String): ExecutionReceipt?
    fun complete(id: String, result: ToolResult)
}

/** Crash-safe at-most-once writes. An interrupted dispatch needs manual inspection, never replay. */
class ExecutionReceipts(private val store: ExecutionReceiptStore) {
    suspend fun execute(call: ToolCall, action: suspend () -> ToolResult): ToolResult {
        val fingerprint = fingerprint(call)
        if (!store.claim(call.id, fingerprint)) {
            val receipt = store.read(call.id)
            if (receipt?.fingerprint == fingerprint && receipt.result != null) return receipt.result
            return ToolResult(call.id, call.toolName, "{}", false, errorMessage =
                "This action may already have run. Inspect the destination before starting a new action.")
        }
        val result = action()
        store.complete(call.id, result)
        return result
    }

    companion object {
        fun fingerprint(call: ToolCall): String {
            fun canonical(value: Any?): String = when (value) {
                null -> "null"
                is Map<*, *> -> value.keys.map { it.toString() }.sorted().joinToString(",", "{", "}") {
                    JSONObject.quote(it) + ":" + canonical(value[it])
                }
                is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
                is String -> JSONObject.quote(value)
                else -> value.toString()
            }
            val payload = call.toolName + ":" + canonical(call.arguments.filterKeys { !it.startsWith("__") })
            return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
