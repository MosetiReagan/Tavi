package com.example.tavi.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tavi.data.TaviDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @Test fun deliveryPostsNotificationAdvancesRecurrenceAndSnoozes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissionWasGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (!permissionWasGranted) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db = TaviDatabase.getInstance(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = "delivery-test-${UUID.randomUUID()}"
        try {
            val now = System.currentTimeMillis()
            assertTrue(db.insertReminder(id, "Test reminder", now - 1000, "daily"))
            ReminderDelivery.deliver(context, Intent().putExtra("reminderId", id))
            withTimeout(5000) {
                while (manager.activeNotifications.none { it.tag == id }) delay(50)
            }
            assertTrue((db.getReminder(id)?.get("triggerTime") as Long) > now)

            val snoozeStarted = System.currentTimeMillis()
            ReminderDelivery.deliver(context, Intent("SNOOZE").putExtra("reminderId", id))
            val snoozed = db.getReminder("$id-snooze")
            assertNotNull(snoozed)
            val trigger = snoozed!!["triggerTime"] as Long
            assertTrue(trigger in (snoozeStarted + 600_000)..(System.currentTimeMillis() + 600_000))
        } finally {
            listOf(id, "$id-snooze").forEach { reminderId ->
                ReminderScheduler.cancel(context, reminderId)
                db.writableDatabase.delete("reminders", "id = ?", arrayOf(reminderId))
                manager.cancel(reminderId, 1)
            }
        }
    }
}
