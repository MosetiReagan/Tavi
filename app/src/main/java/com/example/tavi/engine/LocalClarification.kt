package com.example.tavi.engine

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.tavi.data.TaviDatabase
import org.json.JSONObject

enum class ClarificationSlot { ALARM_TIME, TIMER_DURATION, MESSAGE_RECIPIENT, MESSAGE_BODY }

data class LocalClarification(
    val slot: ClarificationSlot,
    val toolName: String,
    val createdAt: Long,
    val recipient: String? = null,
    val body: String? = null,
    val language: String = "en"
) {
    fun isCurrent(now: Long) = now >= createdAt && now - createdAt < TTL_MS
    companion object { const val TTL_MS = 10 * 60_000L }
}

object LocalClarificationCodec {
    fun encode(value: LocalClarification): String = JSONObject().put("slot", value.slot.name)
        .put("tool", value.toolName).put("createdAt", value.createdAt)
        .put("recipient", value.recipient).put("body", value.body).put("language", value.language).toString()

    fun decode(raw: String): LocalClarification? = runCatching {
        require(raw.length <= 16_000)
        val json = JSONObject(raw)
        val slot = ClarificationSlot.valueOf(json.getString("slot"))
        val tool = json.getString("tool")
        require(tool in when (slot) {
            ClarificationSlot.ALARM_TIME -> setOf("alarm_create")
            ClarificationSlot.TIMER_DURATION -> setOf("timer_set")
            else -> setOf("messages_send_sms", "messages_draft")
        })
        fun string(name: String): String? {
            if (!json.has(name) || json.isNull(name)) return null
            return (json.get(name) as String).takeIf { it.isNotBlank() }
        }
        val recipient = string("recipient")
        val body = string("body")
        val language = json.optString("language", "en")
        require(language in setOf("en", "sw"))
        require(recipient == null || recipient.length <= 200)
        require(body == null || body.length <= 4000)
        require(slot != ClarificationSlot.MESSAGE_BODY || recipient != null)
        val createdAt = json.getLong("createdAt")
        require(createdAt >= 0)
        LocalClarification(slot, tool, createdAt, recipient, body, language)
    }.getOrNull()
}

/** Only explicit missing slots are retained, scoped to a conversation and expiring after ten minutes. */
class LocalClarificationStore(context: Context) {
    private val db = TaviDatabase.getInstance(context)
    fun get(conversationId: String, now: Long = System.currentTimeMillis()): LocalClarification? {
        db.writableDatabase.delete("local_clarifications", "created_at <= ? OR created_at > ?",
            arrayOf((now - LocalClarification.TTL_MS).toString(), now.toString()))
        val value = db.readableDatabase.query("local_clarifications", arrayOf("request_json"),
            "conversation_id = ?", arrayOf(conversationId), null, null, null).use {
            if (it.moveToFirst()) LocalClarificationCodec.decode(it.getString(0)) else null
        }
        if (value == null || !value.isCurrent(now)) { clear(conversationId); return null }
        return value
    }
    fun save(conversationId: String, value: LocalClarification?) {
        if (value == null) { clear(conversationId); return }
        require(conversationId.isNotBlank())
        val values = ContentValues().apply {
            put("conversation_id", conversationId); put("request_json", LocalClarificationCodec.encode(value)); put("created_at", value.createdAt)
        }
        check(db.writableDatabase.insertWithOnConflict("local_clarifications", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    fun clear(conversationId: String) { db.writableDatabase.delete("local_clarifications", "conversation_id = ?", arrayOf(conversationId)) }
    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS local_clarifications (conversation_id TEXT PRIMARY KEY, request_json TEXT NOT NULL, created_at INTEGER NOT NULL)")
        }
    }
}
