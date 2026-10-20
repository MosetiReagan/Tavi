package com.example.tavi.roles.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.messages.NotificationItem
import com.example.tavi.tools.messages.NotificationStore

/**
 * Required system receiver for default SMS app eligibility.
 * Broadcast action: android.provider.Telephony.SMS_DELIVER
 * Permission: android.permission.BROADCAST_SMS
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""
                val timestamp = sms.timestampMillis

                val sanitizedBody = SecurityGuard.sanitizeExternalContent(body, "SMS: $sender")

                Log.d(TAG, "Delivered SMS from: $sender (length=${sanitizedBody.length})")

                // Add to NotificationStore so assistant can summarize received SMS
                NotificationStore.add(
                    NotificationItem(
                        id = "sms_${timestamp}_$sender",
                        packageName = "telephony.sms",
                        title = "SMS from $sender",
                        text = sanitizedBody,
                        postTime = timestamp,
                        key = "sms_${timestamp}"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing delivered SMS", e)
        }
    }

    companion object {
        private const val TAG = "TaviSmsDeliver"
    }
}
