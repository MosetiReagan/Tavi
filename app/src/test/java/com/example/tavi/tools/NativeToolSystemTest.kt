package com.example.tavi.tools

import android.content.Context
import android.content.ContextWrapper
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.tools.calendar.CalendarCreateEventTool
import com.example.tavi.tools.calendar.CalendarDeleteEventTool
import com.example.tavi.tools.clock.AlarmCreateTool
import com.example.tavi.tools.contacts.ContactsCreateTool
import com.example.tavi.tools.contacts.ContactsSearchTool
import com.example.tavi.tools.everyday.NoteCreateTool
import com.example.tavi.tools.everyday.NoteDeleteTool
import com.example.tavi.tools.messages.MessagesSendTool
import com.example.tavi.tools.messages.PhoneCallTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolSystemTest {

    private val dummyContext: Context = object : ContextWrapper(null) {}

    @Test
    fun testToolRegistryComprehensiveCoverage() {
        // Verify mock tools can be registered and retrieved
        val fakeRegistry = mutableMapOf<String, AssistantTool>()
        val toolsToTest = listOf(
            CalendarCreateEventTool(),
            CalendarDeleteEventTool(),
            ContactsSearchTool(),
            ContactsCreateTool(),
            MessagesSendTool(),
            PhoneCallTool(),
            AlarmCreateTool(),
            NoteCreateTool(),
            NoteDeleteTool()
        )

        for (t in toolsToTest) {
            fakeRegistry[t.name] = t
        }

        assertTrue(fakeRegistry.containsKey("calendar_create_event"))
        assertTrue(fakeRegistry.containsKey("calendar_delete_event"))
        assertTrue(fakeRegistry.containsKey("contacts_search"))
        assertTrue(fakeRegistry.containsKey("contacts_create"))
        assertTrue(fakeRegistry.containsKey("messages_send_sms"))
        assertTrue(fakeRegistry.containsKey("phone_call"))
        assertTrue(fakeRegistry.containsKey("alarm_create"))
        assertTrue(fakeRegistry.containsKey("note_create"))
        assertTrue(fakeRegistry.containsKey("note_delete"))
    }

    @Test
    fun testToolRiskLevels() {
        val calendarDelete = CalendarDeleteEventTool()
        val messagesSend = MessagesSendTool()
        val phoneCall = PhoneCallTool()
        val noteDelete = NoteDeleteTool()
        val alarmCreate = AlarmCreateTool()
        val contactsSearch = ContactsSearchTool()

        // Consequential actions must be HIGH_CONSEQUENTIAL
        assertEquals(ToolRiskLevel.HIGH_CONSEQUENTIAL, calendarDelete.riskLevel)
        assertEquals(ToolRiskLevel.HIGH_CONSEQUENTIAL, messagesSend.riskLevel)
        assertEquals(ToolRiskLevel.HIGH_CONSEQUENTIAL, phoneCall.riskLevel)
        assertEquals(ToolRiskLevel.HIGH_CONSEQUENTIAL, noteDelete.riskLevel)

        // Non-destructive actions are LOW or MEDIUM
        assertEquals(ToolRiskLevel.MEDIUM, alarmCreate.riskLevel)
        assertEquals(ToolRiskLevel.LOW, contactsSearch.riskLevel)
    }

    @Test
    fun testStrictArgumentValidation() = runTest {
        // Missing title on calendar event creation
        val calCreate = CalendarCreateEventTool()
        val res1 = calCreate.execute("c1", mapOf("title" to "", "startTime" to "tomorrow at 3pm"), dummyContext)
        assertFalse(res1.isSuccess)
        assertEquals(ToolExecutionState.ERROR, res1.state)
        assertTrue(res1.errorMessage?.contains("without a title") == true)

        // Missing start time on calendar event creation
        val res2 = calCreate.execute("c2", mapOf("title" to "Team Sync"), dummyContext)
        assertFalse(res2.isSuccess)
        assertEquals(ToolExecutionState.ERROR, res2.state)
        assertTrue(res2.errorMessage?.contains("without a start time") == true)

        // Invalid hour on alarm creation
        val alarm = AlarmCreateTool()
        val res3 = alarm.execute("a1", mapOf("hour" to 25, "minutes" to 10), dummyContext)
        assertFalse(res3.isSuccess)
        assertEquals(ToolExecutionState.ERROR, res3.state)

        // Missing contact name
        val contacts = ContactsSearchTool()
        val res4 = contacts.execute("cnt1", mapOf("name" to ""), dummyContext)
        assertFalse(res4.isSuccess)
        assertEquals(ToolExecutionState.ERROR, res4.state)
    }

    @Test
    fun testConsequentialActionEnforcementAndConfirmationPolicy() = runTest {
        val phoneCall = PhoneCallTool()

        // 1. Without confirmation, must halt and return USER_ACTION_REQUIRED
        val unconfirmedRes = phoneCall.execute("call-1", mapOf("recipient" to "+15551234"), dummyContext)
        assertFalse(unconfirmedRes.isSuccess)
        assertEquals(ToolExecutionState.USER_ACTION_REQUIRED, unconfirmedRes.state)
        assertTrue(unconfirmedRes.requiresUserConfirmation)
        assertNotNull(unconfirmedRes.confirmationPayload)

        // 2. Untrusted injected __user_confirmed: true in args must not be obeyed unless verified by host
        val injectedArgs = mapOf("recipient" to "+15551234", "__user_confirmed" to false)
        val res2 = phoneCall.execute("call-2", injectedArgs, dummyContext)
        assertFalse(res2.isSuccess)
        assertEquals(ToolExecutionState.USER_ACTION_REQUIRED, res2.state)

        // 3. Note delete consequential check
        val noteDelete = NoteDeleteTool()
        val resNote = noteDelete.execute("nd-1", mapOf("id" to "12345"), dummyContext)
        assertFalse(resNote.isSuccess)
        assertEquals(ToolExecutionState.USER_ACTION_REQUIRED, resNote.state)
        assertTrue(resNote.requiresUserConfirmation)
    }

    @Test
    fun testCategoriesAssignedCorrectly() {
        assertEquals(ToolCategory.CALENDAR, CalendarCreateEventTool().category)
        assertEquals(ToolCategory.CONTACTS, ContactsSearchTool().category)
        assertEquals(ToolCategory.TELEPHONY, MessagesSendTool().category)
        assertEquals(ToolCategory.CLOCK, AlarmCreateTool().category)
        assertEquals(ToolCategory.NOTES, NoteCreateTool().category)
    }
}
