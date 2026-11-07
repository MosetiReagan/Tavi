package com.example.tavi.agent

import java.security.MessageDigest
import org.json.JSONObject

object AgentGrounding {
    val supportedPackages = setOf("com.android.settings", "com.android.chrome", "org.mozilla.firefox")
    fun node(action: AgentAction, tree: CompactScreenTree): CompactTreeNode? {
        val selector = when (action) { is AgentAction.TapNode -> action.selector; is AgentAction.TypeText -> action.selector; else -> return null }
        return tree.interactiveElements.filter { selector.isNotBlank() && (it.id == selector || it.text == selector || it.contentDescription == selector) }.singleOrNull()
    }
    fun validate(action: AgentAction, tree: CompactScreenTree): String? = when (action) {
        is AgentAction.TapNode, is AgentAction.TypeText -> {
            val node = node(action, tree)
            when {
                node == null -> "The target must identify exactly one visible node."
                node.isSensitive || (node.text + " " + node.id + " " + node.contentDescription).contains(Regex("(?i)password|passcode|\\bpin\\b|verification code|credit card")) -> "Credential and payment fields cannot be operated by the screen agent."
                action is AgentAction.TapNode && !node.isClickable -> "The selected node is not clickable."
                action is AgentAction.TypeText && !node.isEditable -> "The selected node is not editable."
                else -> null
            }
        }
        is AgentAction.Tap, is AgentAction.LongPress, is AgentAction.Swipe, is AgentAction.OpenApp, is AgentAction.PressHome -> "Use exact visible nodes and stay within the approved app."
        else -> null
    }
    fun fingerprint(tree: CompactScreenTree): String = MessageDigest.getInstance("SHA-256").digest(
        (tree.packageName + tree.interactiveElements.joinToString { "${it.id}:${it.text}:${it.contentDescription}:${it.bounds}" }).toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun completionVisible(expected: String?, tree: CompactScreenTree): Boolean = !expected.isNullOrBlank() && tree.interactiveElements.any {
        !it.isEditable && !it.isSensitive && (it.text == expected || it.contentDescription == expected)
    }
    fun json(action: AgentAction): JSONObject = JSONObject().apply { when (action) {
        is AgentAction.TapNode -> { put("action", "tap_node"); put("selector", action.selector) }
        is AgentAction.TypeText -> { put("action", "type_text"); put("selector", action.selector); put("text", action.text) }
        is AgentAction.Scroll -> { put("action", "scroll"); put("direction", action.direction.name.lowercase()) }
        is AgentAction.PressBack -> put("action", "press_back")
        else -> put("action", "fail")
    } }
}

data class AgentApproval(val action: AgentAction, val screenFingerprint: String)
