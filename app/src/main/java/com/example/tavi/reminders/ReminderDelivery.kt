package com.example.tavi.reminders

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tavi.MainActivity
import com.example.tavi.data.TaviDatabase
import com.example.tavi.tools.everyday.ReminderReceiver

object ReminderDelivery {
    fun deliver(context: Context, intent: Intent) {
        val id = intent.getStringExtra("reminderId") ?: return
        val db = TaviDatabase.getInstance(context)
        val reminder = db.getReminder(id) ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (intent.action == "SNOOZE") {
            val next = System.currentTimeMillis() + 10 * 60_000
            val snoozeId = "$id-snooze"
            check(db.insertReminder(snoozeId, reminder["title"].toString(), next))
            ReminderScheduler.schedule(context, snoozeId, next)
            manager.cancel(id, 1)
            return
        }
        if (reminder["completed"] == 1 || (reminder["triggerTime"] as Long) > System.currentTimeMillis() + 2000) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val channelId = "tavi_reminders"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Tavi Reminders", NotificationManager.IMPORTANCE_HIGH))
            if (manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return
        }
        val launch = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java).setAction("SNOOZE")
            .setData(Uri.parse("tavi://reminder/$id/snooze")).putExtra("reminderId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, 1, NotificationCompat.Builder(context, channelId).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Tavi Reminder").setContentText(reminder["title"].toString()).setAutoCancel(true)
            .setContentIntent(launch).addAction(0, "Snooze 10 min", snooze).build())
        val next = ReminderScheduler.nextOccurrence(reminder["triggerTime"] as Long, reminder["recurrence"].toString(), System.currentTimeMillis())
        if (next == null) db.completeReminder(id)
        else { check(db.insertReminder(id, reminder["title"].toString(), next, reminder["recurrence"].toString())); ReminderScheduler.schedule(context, id, next) }
    }
}
