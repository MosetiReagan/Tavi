package com.example.tavi.tools.messages

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SmsMessage
import com.example.tavi.data.TaviDatabase
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

object SmsSendState {
    fun aggregate(sent: List<Int?>, delivered: List<Int?>): String = when {
        sent.any { it != null && it != Activity.RESULT_OK } -> "FAILED"
        delivered.any { it != null && it >= 64 } -> "DELIVERY_FAILED"
        delivered.isNotEmpty() && delivered.all { it == 0 } -> "DELIVERED"
        sent.isNotEmpty() && sent.all { it == Activity.RESULT_OK } -> "SENT"
        else -> "QUEUED"
    }
}

class SmsDispatchTracker(private val context: Context) {
    private val db = TaviDatabase.getInstance(context)
    fun start(id: String, recipient: String, parts: Int) {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            database.insertOrThrow("sms_dispatches", null, ContentValues().apply { put("id", id); put("recipient", recipient); put("parts", parts); put("created_at", System.currentTimeMillis()) })
            for (index in 0 until parts) database.insertOrThrow("sms_parts", null, ContentValues().apply { put("dispatch_id", id); put("part", index) })
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }
    fun callback(id: String, part: Int, sent: Boolean, code: Int) {
        db.writableDatabase.update("sms_parts", ContentValues().apply { put(if (sent) "sent_code" else "delivery_code", code) },
            "dispatch_id = ? AND part = ?", arrayOf(id, part.toString()))
    }
    fun status(id: String): JSONObject? {
        val actualId = if (id == "latest") db.readableDatabase.rawQuery("SELECT id FROM sms_dispatches ORDER BY created_at DESC LIMIT 1", null).use { if (it.moveToFirst()) it.getString(0) else null } ?: return null else id
        val recipient = db.readableDatabase.rawQuery("SELECT recipient FROM sms_dispatches WHERE id = ?", arrayOf(actualId)).use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        val sent = mutableListOf<Int?>(); val delivered = mutableListOf<Int?>()
        db.readableDatabase.rawQuery("SELECT sent_code,delivery_code FROM sms_parts WHERE dispatch_id = ? ORDER BY part", arrayOf(actualId)).use { c ->
            while (c.moveToNext()) { sent.add(if (c.isNull(0)) null else c.getInt(0)); delivered.add(if (c.isNull(1)) null else c.getInt(1)) }
        }
        return JSONObject().put("dispatchId", actualId).put("recipient", recipient).put("status", SmsSendState.aggregate(sent, delivered))
            .put("sentParts", sent.count { it == Activity.RESULT_OK }).put("partsCount", sent.size)
            .put("errorCodes", JSONArray(sent.filterNotNull().filter { it != Activity.RESULT_OK }))
    }
    fun pending(id: String, part: Int, sent: Boolean): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, SmsCallbackReceiver::class.java).setData(Uri.parse("tavi://sms/$id/$part/${if (sent) "sent" else "delivered"}"))
            .putExtra("id", id).putExtra("part", part).putExtra("sent", sent),
        PendingIntent.FLAG_UPDATE_CURRENT or if (sent) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_MUTABLE)
    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sms_dispatches (id TEXT PRIMARY KEY, recipient TEXT NOT NULL, parts INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS sms_parts (dispatch_id TEXT NOT NULL, part INTEGER NOT NULL, sent_code INTEGER, delivery_code INTEGER, PRIMARY KEY(dispatch_id,part))")
        }
    }
}

class SmsCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val part = intent.getIntExtra("part", -1)
        if (part !in 0..20) return
        val sent = intent.getBooleanExtra("sent", true)
        val code = if (sent) resultCode else {
            val pdu = intent.getByteArrayExtra("pdu") ?: return
            if (pdu.size > 2048) return
            runCatching { SmsMessage.createFromPdu(pdu, intent.getStringExtra("format"))?.status }.getOrNull() ?: return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { SmsDispatchTracker(context).callback(id, part, sent, code) }
            catch (e: Exception) { android.util.Log.e("SmsCallback", "Could not record SMS status", e) }
            finally { pending.finish() }
        }
    }
}
