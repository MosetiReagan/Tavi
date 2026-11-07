package com.example.tavi.routines

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RoutineTimingTest {
    @Test fun weekdayScheduleSkipsWeekend() {
        val zone = ZoneId.of("Africa/Nairobi")
        val now = ZonedDateTime.of(2026, 11, 6, 18, 0, 0, 0, zone).toInstant()
        val next = Instant.ofEpochMilli(RoutineTiming.next("07:30", true, now, zone)).atZone(zone)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(7, next.hour)
        assertEquals(30, next.minute)
    }
    @Test fun passedTimeMovesToNextDayAndInvalidTimesFail() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-11-07T10:00:00Z")
        assertEquals(Instant.parse("2026-11-08T08:00:00Z").toEpochMilli(), RoutineTiming.next("08:00", false, now, zone))
        try { RoutineTiming.next("25:10", false, now, zone); fail("Invalid time accepted") }
        catch (_: java.time.format.DateTimeParseException) { }
    }
}
