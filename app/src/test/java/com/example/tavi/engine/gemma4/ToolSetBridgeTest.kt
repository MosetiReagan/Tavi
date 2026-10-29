package com.example.tavi.engine.gemma4

import android.content.Context
import android.content.ContextWrapper
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.tools.ToolRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolSetBridgeTest {

    private val dummyContext: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var bridge: ToolSetBridge

    @Before
    fun setUp() {
        toolRegistry = ToolRegistry(dummyContext)
        bridge = ToolSetBridge(toolRegistry)
    }

    @Test
    fun testAllRegisteredToolsPassSchemaRoundTrip() {
        val tools = toolRegistry.getAllTools()
        assertTrue("Expected at least 50 tools registered, found ${tools.size}", tools.size >= 50)

        for (tool in tools) {
            val valid = bridge.validateSchemaRoundTrip(tool)
            assertTrue("Tool '${tool.name}' failed OpenAPI schema round-trip validation", valid)

            val jsonStr = bridge.createToolDescription(tool)
            val json = JSONObject(jsonStr)
            assertEquals("Tool name mismatch in OpenAPI json", tool.name, json.getString("name"))
            assertTrue("Description should not be empty", json.getString("description").isNotBlank())
            assertNotNull("Parameters schema must be present", json.getJSONObject("parameters"))
        }
    }

    @Test
    fun testConsequentialToolBlocksDirectUnconfirmedExecution() {
        val consequentialTools = toolRegistry.getAllTools().filter { it.requiresConfirmation }
        assertTrue("Expected consequential tools in registry", consequentialTools.isNotEmpty())

        val dangerousTool = consequentialTools.first()
        val unconfirmedResultJson = bridge.executeSafe(dangerousTool, "{\"param\": \"value\"}")
        val res = JSONObject(unconfirmedResultJson)
        assertEquals(dangerousTool.name, res.getString("toolName"))
        assertEquals(ToolExecutionState.USER_ACTION_REQUIRED.name, res.getString("state"))
        assertTrue(res.getBoolean("requiresConfirmation"))
    }

    @Test
    fun testMapSdkToolCallMapsCorrectly() {
        val domainCall = bridge.mapSdkToolCall(
            name = "alarm_create",
            arguments = mapOf("hour" to 7, "minutes" to 30, "label" to "Wake up")
        )
        assertEquals("alarm_create", domainCall.toolName)
        assertEquals(7, domainCall.arguments["hour"])
        assertEquals(30, domainCall.arguments["minutes"])
        assertEquals("Wake up", domainCall.arguments["label"])
    }

    @Test
    fun testParseJsonArgumentsHandlesEdgeCases() {
        val emptyMap = ToolSetBridge.parseJsonArguments("")
        assertTrue(emptyMap.isEmpty())

        val corruptMap = ToolSetBridge.parseJsonArguments("{invalid_json: true")
        assertTrue(corruptMap.isEmpty())

        val validMap = ToolSetBridge.parseJsonArguments("{\"recipient\":\"Alice\",\"count\":2}")
        assertEquals("Alice", validMap["recipient"])
        assertEquals(2, validMap["count"])
    }
}
