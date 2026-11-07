package com.example.tavi.routines

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tavi.MainActivity
import kotlinx.coroutines.*

object RoutineScheduler {
    private fun pending(context: Context, id: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, RoutineReceiver::class.java).setData(Uri.parse("tavi://routine/$id")).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context, routine: PersonalRoutine) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pending(context, routine.id))
        if (routine.enabled && routine.time.isNotBlank()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            RoutineTiming.next(routine.time, routine.weekdaysOnly), pending(context, routine.id))
    }
    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, id))
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id, 2)
    }
    fun restore(context: Context) { RoutineStore(context).list().forEach { schedule(context, it) } }
}

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val routine = RoutineStore(context).get(id) ?: return@launch
                if (!routine.enabled) return@launch
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("tavi_routines", "Personal routines", NotificationManager.IMPORTANCE_DEFAULT))
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                    .setData(Uri.parse("tavi://routine/$id/open")).putExtra("EXTRA_ROUTINE_ID", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) manager.notify(id, 2,
                    NotificationCompat.Builder(context, "tavi_routines").setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle(routine.title).setContentText("Open your routine to review and run it.").setContentIntent(open).setAutoCancel(true).build())
                RoutineScheduler.schedule(context, routine)
            } catch (e: Exception) { android.util.Log.e("RoutineReceiver", "Routine reminder failed", e) }
            finally { pending.finish() }
        }
    }
}
