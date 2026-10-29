package com.example.tavi.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class CompactTreeNode(
    val id: String,
    val text: String,
    val contentDescription: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: String = "",
    val children: List<CompactTreeNode> = emptyList()
)

data class CompactScreenTree(
    val packageName: String,
    val nodeCount: Int,
    val treeString: String,
    val interactiveElements: List<CompactTreeNode>
)

object AccessibilityTreeReader {

    const val MAX_DEPTH = 6
    const val MAX_NODES = 80
    const val MAX_OUTPUT_CHARS = 1800

    private val CREDIT_CARD_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val OTP_REGEX = Regex("""(?i)\b(?:code|otp|pin|verification(?: code)?|security code|login code)(?:\s+is)?[\s:]+(\d{4,8})\b""")
    private val STANDALONE_OTP_REGEX = Regex("""(?i)\b(?:your (?:[a-z]+ )?code is|verification code is)\s+(\d{4,8})\b""")

    fun sanitizeText(raw: String?, isPassword: Boolean): String {
        if (raw.isNullOrBlank()) return ""
        if (isPassword) return "[PASSWORD REDACTED]"
        var s = raw
        s = CREDIT_CARD_REGEX.replace(s, "[CARD_REDACTED]")
        s = OTP_REGEX.replace(s) { match ->
            match.value.replace(match.groupValues[1], "[CODE_REDACTED]")
        }
        s = STANDALONE_OTP_REGEX.replace(s) { match ->
            match.value.replace(match.groupValues[1], "[CODE_REDACTED]")
        }
        return s.trim()
    }

    /**
     * Reads and compacts from an Android framework AccessibilityNodeInfo root.
     */
    fun readScreenTree(rootNode: AccessibilityNodeInfo?): CompactScreenTree {
        if (rootNode == null) {
            return CompactScreenTree(
                packageName = "none",
                nodeCount = 0,
                treeString = "(No active window)",
                interactiveElements = emptyList()
            )
        }

        val packageName = rootNode.packageName?.toString() ?: "unknown"
        val interactive = mutableListOf<CompactTreeNode>()
        val compactRoot = parseNode(rootNode, depth = 0, interactiveList = interactive)

        val sb = StringBuilder()
        sb.append("App: $packageName\n")
        renderTreeString(compactRoot, sb, indent = 0)

        var resultStr = sb.toString()
        if (resultStr.length > MAX_OUTPUT_CHARS) {
            resultStr = resultStr.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]"
        }

        return CompactScreenTree(
            packageName = packageName,
            nodeCount = interactive.size,
            treeString = resultStr,
            interactiveElements = interactive
        )
    }

    private fun parseNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        interactiveList: MutableList<CompactTreeNode>
    ): CompactTreeNode? {
        if (node == null || depth > MAX_DEPTH || interactiveList.size >= MAX_NODES) return null

        val isPassword = node.isPassword
        val text = sanitizeText(node.text?.toString(), isPassword)
        val desc = sanitizeText(node.contentDescription?.toString(), isPassword)
        val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: ""
        val isClickable = node.isClickable
        val isEditable = node.isEditable

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val boundsStr = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"

        val children = mutableListOf<CompactTreeNode>()
        for (i in 0 until node.childCount) {
            if (interactiveList.size >= MAX_NODES) break
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            val parsedChild = parseNode(child, depth + 1, interactiveList)
            if (parsedChild != null) {
                children.add(parsedChild)
            }
        }

        val compactNode = CompactTreeNode(
            id = viewId,
            text = text,
            contentDescription = desc,
            className = className,
            isClickable = isClickable,
            isEditable = isEditable,
            bounds = boundsStr,
            children = children
        )

        if (isClickable || isEditable || text.isNotBlank() || desc.isNotBlank()) {
            interactiveList.add(compactNode)
        }

        return compactNode
    }

    /**
     * Renders a tree of CompactTreeNode into a clean, depth-indented representation.
     */
    fun renderTreeString(node: CompactTreeNode?, sb: StringBuilder, indent: Int) {
        if (node == null || indent > MAX_DEPTH || sb.length >= MAX_OUTPUT_CHARS) return

        val indentStr = "  ".repeat(indent)
        val textSnippet = if (node.text.length > 150) node.text.take(150) + "..." else node.text
        val descSnippet = if (node.contentDescription.length > 150) node.contentDescription.take(150) + "..." else node.contentDescription

        val label = when {
            textSnippet.isNotBlank() && descSnippet.isNotBlank() -> "\"$textSnippet\" ($descSnippet)"
            textSnippet.isNotBlank() -> "\"$textSnippet\""
            descSnippet.isNotBlank() -> "($descSnippet)"
            else -> ""
        }

        val flags = buildList {
            if (node.isClickable) add("clickable")
            if (node.isEditable) add("editable")
            if (node.id.isNotBlank()) add("id=${node.id}")
        }.joinToString(",")

        val flagSuffix = if (flags.isNotBlank()) " [$flags]" else ""
        val boundsSuffix = if (node.bounds.isNotBlank()) " ${node.bounds}" else ""

        if (label.isNotBlank() || flags.isNotBlank()) {
            val line = "$indentStr- ${node.className} $label$flagSuffix$boundsSuffix\n"
            if (sb.length + line.length > MAX_OUTPUT_CHARS) {
                sb.append(line.take((MAX_OUTPUT_CHARS - sb.length).coerceAtLeast(0)))
                return
            }
            sb.append(line)
        }

        for (child in node.children) {
            if (sb.length >= MAX_OUTPUT_CHARS) break
            renderTreeString(child, sb, indent + 1)
        }
    }
}
