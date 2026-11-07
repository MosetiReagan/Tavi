package com.example.tavi.reminders

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderRecurrenceTest {
    @Test fun dailyRecurrenceKeepsLocalTimeAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val before = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderScheduler.nextOccurrence(before, "daily", before, zone)!!
        val time = java.time.Instant.ofEpochMilli(next).atZone(zone)
        assertEquals(9, time.hour)
        assertEquals(23 * 3600_000L, next - before)
    }
    @Test fun weeklySkipsMissedIntervalsAndOneOffDoesNotRepeat() {
        val zone = ZoneId.of("Africa/Nairobi")
        val first = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertNull(ReminderScheduler.nextOccurrence(first, "none", first, zone))
        assertEquals(first + 21 * 86400_000L, ReminderScheduler.nextOccurrence(first, "weekly", first + 15 * 86400_000L, zone))
    }
}
