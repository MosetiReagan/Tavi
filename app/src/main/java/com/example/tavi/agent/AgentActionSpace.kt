package com.example.tavi.agent

import org.json.JSONObject

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

sealed class AgentAction {
    data class Tap(val x: Float, val y: Float) : AgentAction()
    data class TapNode(val selector: String) : AgentAction()
    data class LongPress(val x: Float, val y: Float) : AgentAction()
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long = 300L) : AgentAction()
    data class TypeText(val selector: String, val text: String) : AgentAction()
    object PressBack : AgentAction()
    object PressHome : AgentAction()
    data class OpenApp(val packageName: String) : AgentAction()
    data class Scroll(val direction: ScrollDirection) : AgentAction()
    data class Complete(val summary: String) : AgentAction()
    data class Fail(val reason: String) : AgentAction()
}

object AgentActionParser {

    private val TAP_REGEX = Regex("""(?i)tap\s*\(\s*([\d.]+)\s*,\s*([\d.]+)\s*\)""")
    private val TAP_NODE_REGEX = Regex("""(?i)tap_node\s*\(\s*["']?([^"')]+)["']?\s*\)""")
    private val LONG_PRESS_REGEX = Regex("""(?i)long_press\s*\(\s*([\d.]+)\s*,\s*([\d.]+)\s*\)""")
    private val SWIPE_REGEX = Regex("""(?i)swipe\s*\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*(\d+))?\s*\)""")
    private val TYPE_REGEX = Regex("""(?i)type_text\s*\(\s*["']?([^"',]+)["']?\s*,\s*["']?([^"']+)["']?\s*\)""")
    private val OPEN_APP_REGEX = Regex("""(?i)open_app\s*\(\s*["']?([^"')]+)["']?\s*\)""")
    private val SCROLL_REGEX = Regex("""(?i)scroll\s*\(\s*["']?(up|down|left|right)["']?\s*\)""")
    private val COMPLETE_REGEX = Regex("""(?i)complete\s*\(\s*["']?([^"')]+)["']?\s*\)""")
    private val FAIL_REGEX = Regex("""(?i)fail\s*\(\s*["']?([^"')]+)["']?\s*\)""")

    fun parse(raw: String): AgentAction? {
        val trimmed = raw.trim()

        // 1. JSON-based parsing
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseJson(trimmed)
        }

        // 2. Functional syntax parsing
        TAP_REGEX.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toFloatOrNull() ?: return null
            val y = match.groupValues[2].toFloatOrNull() ?: return null
            return AgentAction.Tap(x, y)
        }

        TAP_NODE_REGEX.find(trimmed)?.let { match ->
            return AgentAction.TapNode(match.groupValues[1].trim())
        }

        LONG_PRESS_REGEX.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toFloatOrNull() ?: return null
            val y = match.groupValues[2].toFloatOrNull() ?: return null
            return AgentAction.LongPress(x, y)
        }

        SWIPE_REGEX.find(trimmed)?.let { match ->
            val x1 = match.groupValues[1].toFloatOrNull() ?: return null
            val y1 = match.groupValues[2].toFloatOrNull() ?: return null
            val x2 = match.groupValues[3].toFloatOrNull() ?: return null
            val y2 = match.groupValues[4].toFloatOrNull() ?: return null
            val dur = match.groupValues.getOrNull(5)?.toLongOrNull() ?: 300L
            return AgentAction.Swipe(x1, y1, x2, y2, dur)
        }

        TYPE_REGEX.find(trimmed)?.let { match ->
            val sel = match.groupValues[1].trim()
            val txt = match.groupValues[2].trim()
            return AgentAction.TypeText(sel, txt)
        }

        OPEN_APP_REGEX.find(trimmed)?.let { match ->
            return AgentAction.OpenApp(match.groupValues[1].trim())
        }

        SCROLL_REGEX.find(trimmed)?.let { match ->
            val dir = when (match.groupValues[1].lowercase()) {
                "up" -> ScrollDirection.UP
                "left" -> ScrollDirection.LEFT
                "right" -> ScrollDirection.RIGHT
                else -> ScrollDirection.DOWN
            }
            return AgentAction.Scroll(dir)
        }

        COMPLETE_REGEX.find(trimmed)?.let { match ->
            return AgentAction.Complete(match.groupValues[1].trim())
        }

        FAIL_REGEX.find(trimmed)?.let { match ->
            return AgentAction.Fail(match.groupValues[1].trim())
        }

        if (trimmed.equals("press_back()", ignoreCase = true) || trimmed.equals("back()", ignoreCase = true)) {
            return AgentAction.PressBack
        }

        if (trimmed.equals("press_home()", ignoreCase = true) || trimmed.equals("home()", ignoreCase = true)) {
            return AgentAction.PressHome
        }

        return null
    }

    private fun parseJson(jsonStr: String): AgentAction? {
        return try {
            val obj = JSONObject(jsonStr)
            val action = obj.optString("action").lowercase()
            when (action) {
                "tap" -> {
                    val x = obj.optDouble("x", -1.0).toFloat()
                    val y = obj.optDouble("y", -1.0).toFloat()
                    if (x >= 0 && y >= 0) AgentAction.Tap(x, y) else null
                }
                "tap_node" -> {
                    val sel = obj.optString("selector", obj.optString("id", obj.optString("text")))
                    if (sel.isNotBlank()) AgentAction.TapNode(sel) else null
                }
                "long_press" -> {
                    val x = obj.optDouble("x", -1.0).toFloat()
                    val y = obj.optDouble("y", -1.0).toFloat()
                    if (x >= 0 && y >= 0) AgentAction.LongPress(x, y) else null
                }
                "swipe" -> {
                    val x1 = obj.optDouble("x1", 0.0).toFloat()
                    val y1 = obj.optDouble("y1", 0.0).toFloat()
                    val x2 = obj.optDouble("x2", 0.0).toFloat()
                    val y2 = obj.optDouble("y2", 0.0).toFloat()
                    val dur = obj.optLong("duration_ms", 300L)
                    AgentAction.Swipe(x1, y1, x2, y2, dur)
                }
                "type_text" -> {
                    val sel = obj.optString("selector", obj.optString("node_id"))
                    val txt = obj.optString("text", "")
                    AgentAction.TypeText(sel, txt)
                }
                "open_app" -> {
                    val pkg = obj.optString("package_name", obj.optString("package"))
                    if (pkg.isNotBlank()) AgentAction.OpenApp(pkg) else null
                }
                "scroll" -> {
                    val dirStr = obj.optString("direction", "down").lowercase()
                    val dir = when (dirStr) {
                        "up" -> ScrollDirection.UP
                        "left" -> ScrollDirection.LEFT
                        "right" -> ScrollDirection.RIGHT
                        else -> ScrollDirection.DOWN
                    }
                    AgentAction.Scroll(dir)
                }
                "press_back", "back" -> AgentAction.PressBack
                "press_home", "home" -> AgentAction.PressHome
                "complete" -> AgentAction.Complete(obj.optString("summary", "Complete"))
                "fail" -> AgentAction.Fail(obj.optString("reason", "Failed"))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
