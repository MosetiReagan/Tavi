package com.example.tavi.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTreeReaderTest {

    @Test
    fun testSanitizeTextRedactsPasswordsAndPII() {
        // Password
        assertEquals("[PASSWORD REDACTED]", AccessibilityTreeReader.sanitizeText("superSecretP@ss123", isPassword = true))

        // Credit cards
        val cc = "Card number 4532 0123 4567 8910 expires 12/28"
        val sanitizedCc = AccessibilityTreeReader.sanitizeText(cc, isPassword = false)
        assertTrue(sanitizedCc.contains("[CARD_REDACTED]"))
        assertFalse(sanitizedCc.contains("4532 0123 4567 8910"))

        // Verification codes / OTPs
        val otp = "Your login code is 849201"
        val sanitizedOtp = AccessibilityTreeReader.sanitizeText(otp, isPassword = false)
        assertTrue(sanitizedOtp.contains("[CODE_REDACTED]"))
        assertFalse(sanitizedOtp.contains("849201"))
    }

    @Test
    fun testTreeDepthLimitAndFormatting() {
        // Construct a deeply nested tree exceeding MAX_DEPTH (6)
        fun buildDeepNode(depth: Int): CompactTreeNode {
            val child = if (depth < 10) listOf(buildDeepNode(depth + 1)) else emptyList()
            return CompactTreeNode(
                id = "node_$depth",
                text = "Depth $depth Label",
                contentDescription = "",
                className = "TextView",
                isClickable = depth % 2 == 0,
                isEditable = false,
                bounds = "[0,0][100,50]",
                children = child
            )
        }

        val root = buildDeepNode(0)
        val sb = StringBuilder()
        AccessibilityTreeReader.renderTreeString(root, sb, indent = 0)
        val output = sb.toString()

        // Depth 0 to 6 should be rendered, depth 7+ must not be rendered
        assertTrue(output.contains("node_0"))
        assertTrue(output.contains("node_6"))
        assertFalse(output.contains("node_7"))
        assertFalse(output.contains("node_8"))
    }

    @Test
    fun testTokenBudgetTruncation() {
        // Construct node with lots of text
        val bigText = "A".repeat(2500)
        val node = CompactTreeNode(
            id = "huge_text_view",
            text = bigText,
            contentDescription = "",
            className = "TextView",
            isClickable = false,
            isEditable = false
        )
        val sb = StringBuilder()
        AccessibilityTreeReader.renderTreeString(node, sb, indent = 0)
        assertTrue(sb.length <= AccessibilityTreeReader.MAX_OUTPUT_CHARS + 500)
    }
}
