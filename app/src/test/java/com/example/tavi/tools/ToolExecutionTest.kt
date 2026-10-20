package com.example.tavi.tools

import android.content.Context
import android.content.ContextWrapper
import com.example.tavi.tools.everyday.CalculatorTool
import com.example.tavi.tools.everyday.DateCalculationTool
import com.example.tavi.tools.messages.MessagesDraftTool
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutionTest {

    private val dummyContext: Context = object : ContextWrapper(null) {}

    @Test
    fun testCalculatorExecution() = runTest {
        val calc = CalculatorTool()

        // Simple arithmetic
        val res1 = calc.execute("1", mapOf("expression" to "145 * 12"), dummyContext)
        assertTrue(res1.isSuccess)
        val json1 = JSONObject(res1.resultJson)
        assertEquals(1740.0, json1.getDouble("result"), 0.001)

        // Complex with sqrt and precedence
        val res2 = calc.execute("2", mapOf("expression" to "sqrt(144) + 2^4"), dummyContext)
        assertTrue(res2.isSuccess)
        val json2 = JSONObject(res2.resultJson)
        assertEquals(28.0, json2.getDouble("result"), 0.001)

        // Division by zero error handling
        val res3 = calc.execute("3", mapOf("expression" to "10 / 0"), dummyContext)
        assertFalse(res3.isSuccess)
        assertTrue(res3.errorMessage?.contains("Division by zero") == true)
    }

    @Test
    fun testDateCalculationExecution() = runTest {
        val dateTool = DateCalculationTool()

        // Current datetime
        val res1 = dateTool.execute("1", mapOf("operation" to "current_datetime"), dummyContext)
        assertTrue(res1.isSuccess)
        val json1 = JSONObject(res1.resultJson)
        assertTrue(json1.has("formatted"))

        // Add duration
        val res2 = dateTool.execute("2", mapOf("operation" to "add_duration", "duration" to "14 days"), dummyContext)
        assertTrue(res2.isSuccess)
        val json2 = JSONObject(res2.resultJson)
        assertTrue(json2.has("resultDate"))
    }

    @Test
    fun testMessagesDraftExecution() = runTest {
        val draftTool = MessagesDraftTool()

        val res = draftTool.execute(
            "1",
            mapOf("recipient" to "Sarah", "body" to "See you soon"),
            dummyContext
        )
        assertTrue(res.isSuccess)
        val json = JSONObject(res.resultJson)
        assertEquals("DRAFT_CREATED", json.getString("status"))
        assertEquals("Sarah", json.getString("recipient"))
        assertEquals("See you soon", json.getString("body"))
    }
}
