package com.example.tavi.calendar

import com.example.tavi.tools.calendar.CalendarNaturalDateResolver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class CalendarNaturalDateResolverTest {
    @Test
    fun parses24HourTimeWithoutDroppingMinutes() {
        val base = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 7, 12, 0, 0) }.timeInMillis
        val actual = Calendar.getInstance().apply {
            timeInMillis = CalendarNaturalDateResolver.resolveToEpochMillis("tomorrow at 16:30", base)!!
        }
        assertEquals(8, actual.get(Calendar.DAY_OF_MONTH))
        assertEquals(16, actual.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, actual.get(Calendar.MINUTE))
    }

    @Test
    fun rejectsInvalidOrPartiallyParsedInput() {
        for (input in listOf("nonsense", "tomorrow at 25:70", "2026-02-30", "2026-11-07 garbage", "at 4", "in 0 minutes")) {
            assertNull(input, CalendarNaturalDateResolver.resolveToEpochMillis(input))
        }
    }

    @Test
    fun testRelativeDurationResolution() {
        val baseTime = 1700000000000L // arbitrary fixed epoch

        val inHours = CalendarNaturalDateResolver.resolveToEpochMillis("in 2 hours", baseTime)
        assertNotNull(inHours)
        assertTrue(inHours == baseTime + (2 * 3600 * 1000L))

        val inMinutes = CalendarNaturalDateResolver.resolveToEpochMillis("in 30 mins", baseTime)
        assertNotNull(inMinutes)
        assertTrue(inMinutes == baseTime + (30 * 60 * 1000L))

        val inDays = CalendarNaturalDateResolver.resolveToEpochMillis("in 3 days", baseTime)
        assertNotNull(inDays)
        assertTrue(inDays!! > baseTime)
    }

    @Test
    fun testTomorrowResolution() {
        val baseTime = System.currentTimeMillis()
        val tomorrow = CalendarNaturalDateResolver.resolveToEpochMillis("tomorrow at 3pm", baseTime)

        assertNotNull(tomorrow)
        val cal = Calendar.getInstance().apply { timeInMillis = tomorrow!! }
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) == 15)
        assertTrue(cal.get(Calendar.MINUTE) == 0)
    }

    @Test
    fun testDateTimeFormatting() {
        val formatted = CalendarNaturalDateResolver.formatDateTime(1700000000000L)
        assertNotNull(formatted)
        assertTrue(formatted.isNotEmpty())
    }
}
