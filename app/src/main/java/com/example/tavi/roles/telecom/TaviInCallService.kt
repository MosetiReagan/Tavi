package com.example.tavi.roles.telecom

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * Required system InCallService for default dialer role eligibility.
 * Permission: android.permission.BIND_INCALL_SERVICE
 */
class TaviInCallService : InCallService() {

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: state=${call?.state}")
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
    }

    companion object {
        private const val TAG = "TaviInCallService"
    }
}
