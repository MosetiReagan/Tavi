package com.example.tavi.roles.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Required system receiver for default SMS app eligibility.
 * Broadcast action: android.provider.Telephony.WAP_PUSH_DELIVER
 * Permission: android.permission.BROADCAST_WAP_PUSH
 */
class WapPushDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        try {
            val mimeType = intent.type
            Log.d(TAG, "Delivered WAP Push received of type: $mimeType")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WAP Push", e)
        }
    }

    companion object {
        private const val TAG = "TaviWapPush"
    }
}
