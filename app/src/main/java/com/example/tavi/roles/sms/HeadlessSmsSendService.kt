package com.example.tavi.roles.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

/**
 * Required system service for default SMS app eligibility.
 * Action: android.intent.action.RESPOND_VIA_MESSAGE
 * Permission: android.permission.SEND_RESPOND_VIA_MESSAGE
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "android.intent.action.RESPOND_VIA_MESSAGE") {
            val uri = intent.data
            val extras = intent.extras
            val recipient = uri?.schemeSpecificPart
            val message = extras?.getString(Intent.EXTRA_TEXT)

            if (!recipient.isNullOrEmpty() && !message.isNullOrEmpty()) {
                Log.d(TAG, "Quick respond via message triggered for $recipient")
                try {
                    @Suppress("DEPRECATION")
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(recipient, null, message, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send auto-reply SMS", e)
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "TaviHeadlessSms"
    }
}
