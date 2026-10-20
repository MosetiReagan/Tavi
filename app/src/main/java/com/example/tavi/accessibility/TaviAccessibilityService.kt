package com.example.tavi.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

data class ScreenElement(
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean
)

data class ScreenInspectionResult(
    val packageName: String,
    val elementCount: Int,
    val elements: List<ScreenElement>,
    val screenSummary: String
)

class TaviAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TaviAccessibility"
        var instance: TaviAccessibilityService? = null
            private set

        private val CREDIT_CARD_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
        private val OTP_CONTEXT_REGEX = Regex("""(?i)\b(?:code|otp|pin|verification(?: code)?|security code)[\s:]+(\d{4,8})\b""")
        private val STANDALONE_OTP_REGEX = Regex("""(?i)\b(?:your code is|verification code is)\s+(\d{4,8})\b""")

        fun sanitizeText(raw: String, isPassword: Boolean): String {
            if (isPassword) {
                return "[PASSWORD FIELD REDACTED]"
            }
            var sanitized = raw
            // Redact credit cards
            sanitized = CREDIT_CARD_REGEX.replace(sanitized, "[CARD_REDACTED]")
            // Redact verification codes & OTPs in context
            sanitized = OTP_CONTEXT_REGEX.replace(sanitized) { matchResult ->
                matchResult.value.replace(matchResult.groupValues[1], "[CODE_REDACTED]")
            }
            sanitized = STANDALONE_OTP_REGEX.replace(sanitized) { matchResult ->
                matchResult.value.replace(matchResult.groupValues[1], "[CODE_REDACTED]")
            }
            return sanitized
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TaviAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event stream monitoring (optional on-screen activity observation)
    }

    override fun onInterrupt() {
        Log.w(TAG, "TaviAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "TaviAccessibilityService destroyed.")
    }

    /**
     * Inspects the current screen window node tree with strict password & privacy boundaries.
     */
    fun inspectCurrentScreen(): ScreenInspectionResult {
        val rootNode = rootInActiveWindow
            ?: return ScreenInspectionResult(
                packageName = "unknown",
                elementCount = 0,
                elements = emptyList(),
                screenSummary = "No active window content accessible."
            )

        val elements = mutableListOf<ScreenElement>()
        val packageName = rootNode.packageName?.toString() ?: "unknown"

        traverseNode(rootNode, elements, depth = 0, maxNodes = 150)

        val summaryBuilder = StringBuilder()
        for (el in elements) {
            val label = when {
                el.text.isNotBlank() -> el.text
                el.contentDescription.isNotBlank() -> "[${el.contentDescription}]"
                else -> ""
            }
            if (label.isNotBlank()) {
                if (summaryBuilder.isNotEmpty()) summaryBuilder.append(" | ")
                summaryBuilder.append(label)
                if (summaryBuilder.length > 1000) {
                    summaryBuilder.append("... [truncated]")
                    break
                }
            }
        }

        return ScreenInspectionResult(
            packageName = packageName,
            elementCount = elements.size,
            elements = elements,
            screenSummary = summaryBuilder.toString()
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        outList: MutableList<ScreenElement>,
        depth: Int,
        maxNodes: Int
    ) {
        if (node == null || outList.size >= maxNodes || depth > 15) return

        val isPassword = node.isPassword
        val rawText = node.text?.toString() ?: ""
        val rawDesc = node.contentDescription?.toString() ?: ""

        val safeText = sanitizeText(rawText, isPassword)
        val safeDesc = sanitizeText(rawDesc, isPassword)

        if (safeText.isNotBlank() || safeDesc.isNotBlank() || node.isClickable) {
            outList.add(
                ScreenElement(
                    text = safeText,
                    contentDescription = safeDesc,
                    className = node.className?.toString() ?: "",
                    viewId = node.viewIdResourceName ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable
                )
            )
        }

        for (i in 0 until node.childCount) {
            if (outList.size >= maxNodes) break
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                traverseNode(child, outList, depth + 1, maxNodes)
            }
        }
    }

    /**
     * Executes standard Android global accessibility actions.
     */
    fun performAction(actionName: String): Boolean {
        return when (actionName.lowercase().trim()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else false
            }
            "take_screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else false
            }
            else -> false
        }
    }
}
