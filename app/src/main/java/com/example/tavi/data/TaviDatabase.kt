package com.example.tavi.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.tavi.model.AuditLogEntry
import com.example.tavi.model.MemoryEntry
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import org.json.JSONArray
import java.util.UUID

class TaviDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "tavi_local.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: TaviDatabase? = null

        fun getInstance(context: Context): TaviDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaviDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                tool_calls_json TEXT,
                tool_results_json TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS memory (
                id TEXT PRIMARY KEY,
                mem_key TEXT NOT NULL UNIQUE,
                mem_value TEXT NOT NULL,
                category TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notes (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reminders (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                trigger_time INTEGER NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id TEXT PRIMARY KEY,
                tool_name TEXT NOT NULL,
                status TEXT NOT NULL,
                summary TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                verified INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS memory")
        db.execSQL("DROP TABLE IF EXISTS notes")
        db.execSQL("DROP TABLE IF EXISTS reminders")
        db.execSQL("DROP TABLE IF EXISTS audit_logs")
        onCreate(db)
    }

    // --- MESSAGES ---

    fun insertMessage(msg: Message) {
        val values = ContentValues().apply {
            put("id", msg.id)
            put("role", msg.role.name)
            put("content", msg.content)
            put("timestamp", msg.timestamp)
            put("tool_calls_json", JSONArray().toString())
            put("tool_results_json", JSONArray().toString())
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllMessages(): List<Message> {
        val list = mutableListOf<Message>()
        readableDatabase.query("messages", null, null, null, null, null, "timestamp ASC").use { c ->
            val idIdx = c.getColumnIndex("id")
            val roleIdx = c.getColumnIndex("role")
            val contentIdx = c.getColumnIndex("content")
            val timeIdx = c.getColumnIndex("timestamp")

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val roleStr = c.getString(roleIdx)
                val content = c.getString(contentIdx)
                val time = c.getLong(timeIdx)
                val role = try { MessageRole.valueOf(roleStr) } catch (_: Exception) { MessageRole.USER }
                list.add(Message(id = id, role = role, content = content, timestamp = time))
            }
        }
        return list
    }

    fun clearMessages() {
        writableDatabase.delete("messages", null, null)
    }

    // --- MEMORY ---

    fun saveMemory(key: String, value: String, category: String = "general"): String {
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("mem_key", key.trim().lowercase())
            put("mem_value", value.trim())
            put("category", category.trim().lowercase())
            put("timestamp", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("memory", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return id
    }

    fun getMemory(key: String): String? {
        val cursor = readableDatabase.query(
            "memory",
            arrayOf("mem_value"),
            "mem_key = ?",
            arrayOf(key.trim().lowercase()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun searchMemory(query: String): List<MemoryEntry> {
        val list = mutableListOf<MemoryEntry>()
        val wild = "%${query.trim().lowercase()}%"
        val cursor = readableDatabase.query(
            "memory",
            null,
            "mem_key LIKE ? OR mem_value LIKE ? OR category LIKE ?",
            arrayOf(wild, wild, wild),
            null,
            null,
            "timestamp DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndex("id")
            val keyIdx = c.getColumnIndex("mem_key")
            val valIdx = c.getColumnIndex("mem_value")
            val catIdx = c.getColumnIndex("category")
            val timeIdx = c.getColumnIndex("timestamp")
            while (c.moveToNext()) {
                list.add(
                    MemoryEntry(
                        id = c.getString(idIdx),
                        key = c.getString(keyIdx),
                        value = c.getString(valIdx),
                        category = c.getString(catIdx),
                        timestamp = c.getLong(timeIdx)
                    )
                )
            }
        }
        return list
    }

    fun getAllMemory(): List<MemoryEntry> {
        val list = mutableListOf<MemoryEntry>()
        val cursor = readableDatabase.query("memory", null, null, null, null, null, "timestamp DESC")
        cursor.use { c ->
            val idIdx = c.getColumnIndex("id")
            val keyIdx = c.getColumnIndex("mem_key")
            val valIdx = c.getColumnIndex("mem_value")
            val catIdx = c.getColumnIndex("category")
            val timeIdx = c.getColumnIndex("timestamp")
            while (c.moveToNext()) {
                list.add(
                    MemoryEntry(
                        id = c.getString(idIdx),
                        key = c.getString(keyIdx),
                        value = c.getString(valIdx),
                        category = c.getString(catIdx),
                        timestamp = c.getLong(timeIdx)
                    )
                )
            }
        }
        return list
    }

    fun deleteMemory(keyOrId: String): Boolean {
        val rows = writableDatabase.delete("memory", "mem_key = ? OR id = ?", arrayOf(keyOrId.lowercase(), keyOrId))
        return rows > 0
    }

    fun clearAllMemory() {
        writableDatabase.delete("memory", null, null)
    }

    // --- NOTES ---

    fun saveNote(title: String, content: String): String {
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("title", title.trim())
            put("content", content.trim())
            put("timestamp", System.currentTimeMillis())
        }
        writableDatabase.insert("notes", null, values)
        return id
    }

    fun getAllNotes(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        readableDatabase.query("notes", null, null, null, null, null, "timestamp DESC").use { c ->
            val idIdx = c.getColumnIndex("id")
            val titleIdx = c.getColumnIndex("title")
            val contentIdx = c.getColumnIndex("content")
            val timeIdx = c.getColumnIndex("timestamp")
            while (c.moveToNext()) {
                list.add(
                    mapOf(
                        "id" to c.getString(idIdx),
                        "title" to c.getString(titleIdx),
                        "content" to c.getString(contentIdx),
                        "timestamp" to c.getLong(timeIdx)
                    )
                )
            }
        }
        return list
    }

    // --- AUDIT LOGS ---

    fun insertAuditLog(entry: AuditLogEntry) {
        val values = ContentValues().apply {
            put("id", entry.id)
            put("tool_name", entry.toolName)
            put("status", entry.status)
            put("summary", entry.summary)
            put("timestamp", entry.timestamp)
            put("verified", if (entry.verified) 1 else 0)
        }
        writableDatabase.insert("audit_logs", null, values)
    }

    fun getAllAuditLogs(): List<AuditLogEntry> {
        val list = mutableListOf<AuditLogEntry>()
        readableDatabase.query("audit_logs", null, null, null, null, null, "timestamp DESC LIMIT 100").use { c ->
            val idIdx = c.getColumnIndex("id")
            val toolIdx = c.getColumnIndex("tool_name")
            val statusIdx = c.getColumnIndex("status")
            val summaryIdx = c.getColumnIndex("summary")
            val timeIdx = c.getColumnIndex("timestamp")
            val verIdx = c.getColumnIndex("verified")
            while (c.moveToNext()) {
                list.add(
                    AuditLogEntry(
                        id = c.getString(idIdx),
                        toolName = c.getString(toolIdx),
                        status = c.getString(statusIdx),
                        summary = c.getString(summaryIdx),
                        timestamp = c.getLong(timeIdx),
                        verified = c.getInt(verIdx) == 1
                    )
                )
            }
        }
        return list
    }

    fun clearAuditLogs() {
        writableDatabase.delete("audit_logs", null, null)
    }
}
