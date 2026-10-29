package com.example.tavi.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActionParserTest {

    @Test
    fun testParseFunctionalTap() {
        val action = AgentActionParser.parse("tap(150.5, 420.0)")
        assertNotNull(action)
        assertTrue(action is AgentAction.Tap)
        val tap = action as AgentAction.Tap
        assertEquals(150.5f, tap.x, 0.01f)
        assertEquals(420.0f, tap.y, 0.01f)
    }

    @Test
    fun testParseFunctionalTapNode() {
        val action = AgentActionParser.parse("tap_node(\"submit_button\")")
        assertNotNull(action)
        assertTrue(action is AgentAction.TapNode)
        assertEquals("submit_button", (action as AgentAction.TapNode).selector)
    }

    @Test
    fun testParseFunctionalSwipe() {
        val action = AgentActionParser.parse("swipe(500, 1500, 500, 300, 450)")
        assertNotNull(action)
        assertTrue(action is AgentAction.Swipe)
        val swipe = action as AgentAction.Swipe
        assertEquals(500f, swipe.x1, 0.01f)
        assertEquals(1500f, swipe.y1, 0.01f)
        assertEquals(500f, swipe.x2, 0.01f)
        assertEquals(300f, swipe.y2, 0.01f)
        assertEquals(450L, swipe.durationMs)
    }

    @Test
    fun testParseFunctionalTypeText() {
        val action = AgentActionParser.parse("type_text(\"search_query\", \"gemma on device\")")
        assertNotNull(action)
        assertTrue(action is AgentAction.TypeText)
        val type = action as AgentAction.TypeText
        assertEquals("search_query", type.selector)
        assertEquals("gemma on device", type.text)
    }

    @Test
    fun testParseNavigationAndControl() {
        assertEquals(AgentAction.PressBack, AgentActionParser.parse("press_back()"))
        assertEquals(AgentAction.PressHome, AgentActionParser.parse("press_home()"))

        val openApp = AgentActionParser.parse("open_app(\"com.android.settings\")")
        assertTrue(openApp is AgentAction.OpenApp)
        assertEquals("com.android.settings", (openApp as AgentAction.OpenApp).packageName)

        val scroll = AgentActionParser.parse("scroll(\"down\")")
        assertTrue(scroll is AgentAction.Scroll)
        assertEquals(ScrollDirection.DOWN, (scroll as AgentAction.Scroll).direction)
    }

    @Test
    fun testParseJsonAction() {
        val json = """{"action": "tap", "x": 200, "y": 600}"""
        val action = AgentActionParser.parse(json)
        assertNotNull(action)
        assertTrue(action is AgentAction.Tap)
        val tap = action as AgentAction.Tap
        assertEquals(200f, tap.x, 0.01f)
        assertEquals(600f, tap.y, 0.01f)
    }
}
