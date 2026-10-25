package com.example.tavi.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicIntentRoutingTest {

    private val engine = DeterministicAssistantEngine()

    @Test
    fun testCalendarQueryRouting() = runTest {
        val tools1 = engine.proposeToolCalls("What's on my calendar tomorrow?", emptyList())
        assertEquals(1, tools1.size)
        assertEquals("calendar_list_events", tools1[0].toolName)
        assertEquals("tomorrow", tools1[0].arguments["startDate"])

        val tools2 = engine.proposeToolCalls("Create an event Friday at 10 AM", emptyList())
        assertEquals(1, tools2.size)
        assertEquals("calendar_create_event", tools2[0].toolName)
    }

    @Test
    fun testContactsLookupRouting() = runTest {
        val tools = engine.proposeToolCalls("Find John's number", emptyList())
        assertEquals(1, tools.size)
        assertEquals("contacts_search", tools[0].toolName)
        assertEquals("John", tools[0].arguments["name"])
    }

    @Test
    fun testAlarmAndClockRouting() = runTest {
        val tools1 = engine.proposeToolCalls("Set an alarm for 6:30 AM", emptyList())
        assertEquals(1, tools1.size)
        assertEquals("alarm_create", tools1[0].toolName)
        assertEquals(6, tools1[0].arguments["hour"])
        assertEquals(30, tools1[0].arguments["minutes"])

        val tools2 = engine.proposeToolCalls("Set a timer for 15 minutes", emptyList())
        assertEquals(1, tools2.size)
        assertEquals("timer_set", tools2[0].toolName)
        assertEquals(900, tools2[0].arguments["seconds"])

        val tools3 = engine.proposeToolCalls("What time is it?", emptyList())
        assertEquals(1, tools3.size)
        assertEquals("clock_query_time", tools3[0].toolName)
    }

    @Test
    fun testAppLaunchRouting() = runTest {
        val tools = engine.proposeToolCalls("Open WhatsApp", emptyList())
        assertEquals(1, tools.size)
        assertEquals("app_launch", tools[0].toolName)
        assertEquals("whatsapp", tools[0].arguments["appName"]?.toString()?.lowercase())
    }

    @Test
    fun testNotificationSummaryRouting() = runTest {
        val tools = engine.proposeToolCalls("Show my notifications", emptyList())
        assertEquals(1, tools.size)
        assertEquals("notifications_read_summary", tools[0].toolName)
    }

    @Test
    fun testMessagingRouting() = runTest {
        val tools = engine.proposeToolCalls("Draft a message to Mary saying I will be there soon", emptyList())
        assertEquals(1, tools.size)
        assertEquals("messages_draft", tools[0].toolName)
        assertEquals("Mary", tools[0].arguments["recipient"])
        assertEquals("I will be there soon", tools[0].arguments["body"])
    }

    @Test
    fun testSettingsRouting() = runTest {
        val tools = engine.proposeToolCalls("Open Wi-Fi settings", emptyList())
        assertEquals(1, tools.size)
        assertEquals("settings_open", tools[0].toolName)
        assertEquals("wifi", tools[0].arguments["panel"])
    }

    @Test
    fun testNotesRouting() = runTest {
        val tools1 = engine.proposeToolCalls("Save this as a note: Buy groceries tomorrow", emptyList())
        assertEquals(1, tools1.size)
        assertEquals("note_create", tools1[0].toolName)

        val tools2 = engine.proposeToolCalls("Show my notes", emptyList())
        assertEquals(1, tools2.size)
        assertEquals("notes_list", tools2[0].toolName)
    }

    @Test
    fun testDeviceStateAndNavigationRouting() = runTest {
        val tools1 = engine.proposeToolCalls("Battery level", emptyList())
        assertEquals(1, tools1.size)
        assertEquals("device_get_status", tools1[0].toolName)

        val tools2 = engine.proposeToolCalls("Where am I?", emptyList())
        assertEquals(1, tools2.size)
        assertEquals("location_get_current", tools2[0].toolName)

        val tools3 = engine.proposeToolCalls("Navigate to Nairobi Airport", emptyList())
        assertEquals(1, tools3.size)
        assertEquals("maps_navigate", tools3[0].toolName)
        assertEquals("Nairobi Airport", tools3[0].arguments["destination"])

        val tools4 = engine.proposeToolCalls("Open camera", emptyList())
        assertEquals(1, tools4.size)
        assertEquals("camera_launch", tools4[0].toolName)
    }

    @Test
    fun testTelephonyCallRouting() = runTest {
        val tools = engine.proposeToolCalls("Call John", emptyList())
        assertEquals(1, tools.size)
        assertEquals("phone_call", tools[0].toolName)
        assertEquals("John", tools[0].arguments["recipient"])
    }
}
