package com.example.tavi.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.tavi.accessibility.TaviAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ScreenAgentService {

    private const val TAG = "ScreenAgentService"

    val isAvailable: Boolean
        get() = TaviAccessibilityService.instance != null

    val activeService: TaviAccessibilityService?
        get() = TaviAccessibilityService.instance

    /**
     * Dispatches a tap gesture asynchronously at (x, y).
     */
    suspend fun dispatchTap(x: Float, y: Float): Boolean {
        val service = activeService ?: return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }
    }

    /**
     * Dispatches a swipe gesture from (x1, y1) to (x2, y2) over durationMs.
     */
    suspend fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val service = activeService ?: return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val clampedDur = durationMs.coerceIn(100L, 2000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDur)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }
    }

    /**
     * Finds and clicks a node by text, content description, or view ID.
     */
    fun clickNode(selector: String, expected: CompactTreeNode? = null): Boolean {
        val root = activeService?.rootInActiveWindow ?: return false
        val node = findMatchingNode(root, selector) ?: return false
        if (!matchesExpected(node, expected) || !node.isClickable || !node.isEnabled || !node.isVisibleToUser) return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun matchesExpected(node: AccessibilityNodeInfo, expected: CompactTreeNode?): Boolean = expected == null ||
        (AccessibilityTreeReader.sanitizeText(node.text?.toString(), node.isPassword) == expected.text &&
         AccessibilityTreeReader.sanitizeText(node.contentDescription?.toString(), node.isPassword) == expected.contentDescription)

    /**
     * Types text into a targeted editable node.
     */
    fun typeText(selector: String, text: String, expected: CompactTreeNode? = null): Boolean {
        val root = activeService?.rootInActiveWindow ?: return false
        val node = findMatchingNode(root, selector) ?: return false
        if (!matchesExpected(node, expected) || !node.isEditable || node.isPassword || !node.isEnabled || !node.isVisibleToUser) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Performs a scroll on the scrollable container.
     */
    fun scroll(direction: ScrollDirection): Boolean {
        val root = activeService?.rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(root) ?: return false
        val action = when (direction) {
            ScrollDirection.UP -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            ScrollDirection.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return scrollableNode.performAction(action)
    }

    fun pressBack(): Boolean {
        return activeService?.performAction("back") ?: false
    }

    fun pressHome(): Boolean {
        return activeService?.performAction("home") ?: false
    }

    private fun findMatchingNode(root: AccessibilityNodeInfo, selector: String): AccessibilityNodeInfo? {
        if (selector.isBlank()) return null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        var count = 0
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 12 || count++ > 300) return
            val id = node.viewIdResourceName.orEmpty()
            if (node.isVisibleToUser && (id == selector || id.substringAfterLast('/') == selector ||
                node.text?.toString() == selector || node.contentDescription?.toString() == selector)) matches.add(node)
            for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
        }
        visit(root, 0)
        return matches.singleOrNull()
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }
}
