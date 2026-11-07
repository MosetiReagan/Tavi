package com.example.tavi.agent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.tavi.data.TaviDatabase
import org.json.JSONObject
import java.util.UUID

data class AgentContinuation(val goal: String, val target: String, val expected: String?, val approval: AgentApproval, val stepsUsed: Int = 0)

class AgentContinuationStore(context: Context) {
    private val db = TaviDatabase.getInstance(context)
    fun save(continuation: AgentContinuation): String {
        val token = UUID.randomUUID().toString()
        db.writableDatabase.delete("agent_continuations", "expires_at < ?", arrayOf(System.currentTimeMillis().toString()))
        db.writableDatabase.insertOrThrow("agent_continuations", null, ContentValues().apply {
            put("id", token); put("expires_at", System.currentTimeMillis() + 120_000)
            put("payload", JSONObject().put("goal", continuation.goal).put("target", continuation.target).put("expected", continuation.expected)
                .put("screen", continuation.approval.screenFingerprint).put("action", AgentGrounding.json(continuation.approval.action)).toString())
            put("payload", JSONObject(getAsString("payload")).put("steps", continuation.stepsUsed).toString())
        })
        return token
    }
    fun consume(token: String): AgentContinuation? {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            val saved = database.rawQuery("SELECT payload FROM agent_continuations WHERE id = ? AND expires_at > ?",
                arrayOf(token, System.currentTimeMillis().toString())).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            database.delete("agent_continuations", "id = ?", arrayOf(token))
            database.setTransactionSuccessful()
            if (saved == null) return null
            val obj = JSONObject(saved)
            val action = AgentActionParser.parse(obj.getJSONObject("action").toString()) ?: return null
            return AgentContinuation(obj.getString("goal"), obj.getString("target"), if (obj.isNull("expected")) null else obj.getString("expected"),
                AgentApproval(action, obj.getString("screen")), obj.optInt("steps"))
        } finally { database.endTransaction() }
    }
    companion object {
        fun createSchema(db: SQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS agent_continuations (id TEXT PRIMARY KEY, payload TEXT NOT NULL, expires_at INTEGER NOT NULL)") }
    }
}
