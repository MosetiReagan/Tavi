package com.example.tavi.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.tavi.tools.everyday.ReminderReceiver
import com.example.tavi.data.TaviDatabase
import java.time.Instant
import java.time.ZoneId

object ReminderScheduler {
    fun pendingIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("tavi://reminder/$id"))
            .putExtra("reminderId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, id: String, epoch: Long): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        val trigger = epoch.coerceAtLeast(System.currentTimeMillis() + 1000)
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(context, id))
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(context, id))
        return exact
    }
    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(context, id))
        // Cancel legacy hash-code alarms from earlier app versions too.
        val old = PendingIntent.getBroadcast(context, (id.hashCode() and 0x7FFFFFFF) % 1000000,
            Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (old != null) { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(old); old.cancel() }
    }
    fun rescheduleAll(context: Context) {
        TaviDatabase.getInstance(context).getActiveReminders().forEach {
            val id = it["id"].toString()
            cancel(context, id)
            schedule(context, id, it["triggerTime"] as Long)
        }
    }
    fun nextOccurrence(previous: Long, recurrence: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (recurrence == "none") return null
        var next = Instant.ofEpochMilli(previous).atZone(zone)
        do { next = if (recurrence == "weekly") next.plusWeeks(1) else next.plusDays(1) } while (next.toInstant().toEpochMilli() <= now)
        return next.toInstant().toEpochMilli()
    }
}
