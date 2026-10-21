package com.example.tavi.tools

import android.content.Context
import android.content.ContextWrapper
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.tools.clock.ClockQueryTimeTool
import com.example.tavi.tools.clock.TimerSetTool
import com.example.tavi.tools.device.SettingsOpenTool
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAndDeviceToolsTest {

    private val dummyContext: Context = object : ContextWrapper(null) {}

    @Test
    fun testClockQueryTimeExecution() = runTest {
        val tool = ClockQueryTimeTool()
        val res = tool.execute("clk1", emptyMap(), dummyContext)
        assertTrue(res.isSuccess)
        assertEquals(ToolExecutionState.SUCCESS, res.state)

        val json = JSONObject(res.resultJson)
        assertTrue(json.has("currentTime"))
        assertTrue(json.has("currentDate"))
        assertTrue(json.has("timezoneId"))
        assertTrue(json.has("utcOffsetHours"))
        assertTrue(json.has("locale"))
        assertTrue(json.has("epochMillis"))
    }

    @Test
    fun testTimerValidation() = runTest {
        val timer = TimerSetTool()

        // Negative or zero duration must fail gracefully
        val res1 = timer.execute("t1", mapOf("seconds" to 0), dummyContext)
        assertFalse(res1.isSuccess)
        assertEquals(ToolExecutionState.ERROR, res1.state)

        val res2 = timer.execute("t2", mapOf("seconds" to -10), dummyContext)
        assertFalse(res2.isSuccess)
        assertEquals(ToolExecutionState.ERROR, res2.state)
    }

    @Test
    fun testSettingsPanelsDeclaredCorrectly() {
        val tool = SettingsOpenTool()
        val schema = JSONObject(tool.parametersSchema)
        val props = schema.getJSONObject("properties")
        val panel = props.getJSONObject("panel")
        val enums = panel.getJSONArray("enum")

        val enumList = mutableListOf<String>()
        for (i in 0 until enums.length()) {
            enumList.add(enums.getString(i))
        }

        assertTrue(enumList.contains("wifi"))
        assertTrue(enumList.contains("bluetooth"))
        assertTrue(enumList.contains("display"))
        assertTrue(enumList.contains("notifications"))
        assertTrue(enumList.contains("sound"))
        assertTrue(enumList.contains("battery"))
        assertTrue(enumList.contains("apps"))
        assertTrue(enumList.contains("date"))
        assertTrue(enumList.contains("storage"))
    }
}
