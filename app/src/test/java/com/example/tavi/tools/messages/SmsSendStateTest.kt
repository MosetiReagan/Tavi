package com.example.tavi.tools.messages

import android.app.Activity
import org.junit.Assert.*
import org.junit.Test

class SmsSendStateTest {
    @Test fun doesNotTreatQueuedOrPartialDispatchAsSent() {
        assertEquals("QUEUED", SmsSendState.aggregate(listOf(null, null), listOf(null, null)))
        assertEquals("QUEUED", SmsSendState.aggregate(listOf(Activity.RESULT_OK, null), listOf(null, null)))
        assertEquals("SENT", SmsSendState.aggregate(listOf(Activity.RESULT_OK, Activity.RESULT_OK), listOf(null, null)))
        assertEquals("FAILED", SmsSendState.aggregate(listOf(Activity.RESULT_OK, 4), listOf(null, null)))
    }
    @Test fun deliveryRequiresAllPartsAndReportsFailures() {
        assertEquals("SENT", SmsSendState.aggregate(listOf(-1, -1), listOf(0, null)))
        assertEquals("DELIVERED", SmsSendState.aggregate(listOf(-1, -1), listOf(0, 0)))
        assertEquals("DELIVERY_FAILED", SmsSendState.aggregate(listOf(-1, -1), listOf(0, 64)))
    }
}
