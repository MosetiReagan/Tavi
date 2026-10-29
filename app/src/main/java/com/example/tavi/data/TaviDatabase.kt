package com.example.tavi.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.tavi.memory.ConsentStatus
import com.example.tavi.memory.EpisodicMemoryRecord
import com.example.tavi.memory.MemoryProvenance
import com.example.tavi.memory.MemoryState
import com.example.tavi.memory.MemoryType
import com.example.tavi.memory.PersistentMemoryRecord
import com.example.tavi.memory.SensitivityLevel
import com.example.tavi.memory.VerificationStatus
import com.example.tavi.model.AuditLogEntry
import com.example.tavi.model.ConversationSession
import com.example.tavi.model.MemoryEntry
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.workflow.DynamicBinding
import com.example.tavi.workflow.PlanSource
import com.example.tavi.workflow.StepExecutionStatus
import com.example.tavi.workflow.StepVerificationStatus
import com.example.tavi.workflow.WorkflowExecutionSummary
import com.example.tavi.workflow.WorkflowPlan
import com.example.tavi.workflow.WorkflowState
import com.example.tavi.workflow.WorkflowStep
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TaviDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "tavi_local.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var INSTANCE: TaviDatabase? = null

        fun getInstance(context: Context): TaviDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaviDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureAllTables(db)
    }

    override fun onCreate(db: SQLiteDatabase) {
        ensureAllTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureAllTables(db)
    }

    private fun ensureAllTables(db: SQLiteDatabase) {
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_updated ON conversations(updated_at DESC)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL DEFAULT 'default',
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    tool_calls_json TEXT,
                    tool_results_json TEXT
                )
            """.trimIndent())

            try {
                val cursor = db.rawQuery("PRAGMA table_info(messages)", null)
                var hasConvId = false
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && cursor.getString(nameIdx) == "conversation_id") {
                        hasConvId = true
                        break
                    }
                }
                cursor.close()
                if (!hasConvId) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN conversation_id TEXT NOT NULL DEFAULT 'default'")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id, timestamp ASC)")
            } catch (e: Exception) {
                android.util.Log.e("TaviDatabase", "Error checking conversation_id column", e)
            }

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

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workflows (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    user_query TEXT NOT NULL,
                    state TEXT NOT NULL,
                    plan_source TEXT NOT NULL,
                    current_step_index INTEGER NOT NULL,
                    steps_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    completed_at INTEGER,
                    error TEXT,
                    missing_input_prompt TEXT,
                    missing_input_param TEXT,
                    pending_confirmation_step_id TEXT,
                    summary_text TEXT
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS persistent_memories (
                    id TEXT PRIMARY KEY,
                    memory_type TEXT NOT NULL,
                    mem_key TEXT NOT NULL,
                    mem_value TEXT NOT NULL,
                    category TEXT NOT NULL,
                    provenance TEXT NOT NULL,
                    confidence REAL NOT NULL DEFAULT 1.0,
                    verification_status TEXT NOT NULL,
                    consent_status TEXT NOT NULL,
                    sensitivity TEXT NOT NULL,
                    state TEXT NOT NULL,
                    superseded_by_id TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_confirmed_at INTEGER NOT NULL,
                    expires_at INTEGER,
                    source_conversation_id TEXT,
                    source_message_id TEXT
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pmem_key_state ON persistent_memories(mem_key, state)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pmem_type_state ON persistent_memories(memory_type, state)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS episodic_memories (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    decision_rationale TEXT,
                    outcome TEXT,
                    task_status TEXT NOT NULL,
                    source_conversation_id TEXT,
                    created_at INTEGER NOT NULL,
                    tags_json TEXT NOT NULL DEFAULT '[]'
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episode_time ON episodic_memories(created_at DESC)")
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error ensuring database tables", e)
        }
    }

    // --- CONVERSATIONS ---

    fun getConversations(): List<ConversationSession> {
        val list = mutableListOf<ConversationSession>()
        try {
            readableDatabase.query("conversations", null, null, null, null, null, "updated_at DESC").use { c ->
                val idIdx = c.getColumnIndex("id")
                val titleIdx = c.getColumnIndex("title")
                val createIdx = c.getColumnIndex("created_at")
                val updateIdx = c.getColumnIndex("updated_at")

                while (c.moveToNext()) {
                    val id = c.getString(idIdx)
                    val title = c.getString(titleIdx)
                    val createdAt = c.getLong(createIdx)
                    val updatedAt = c.getLong(updateIdx)
                    list.add(ConversationSession(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt))
                }
            }
            if (list.isEmpty()) {
                val defaultSession = ConversationSession(id = "default", title = "New Chat")
                insertOrUpdateConversation(defaultSession)
                list.add(defaultSession)
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting conversations", e)
        }
        return list
    }

    fun insertOrUpdateConversation(session: ConversationSession) {
        try {
            val values = ContentValues().apply {
                put("id", session.id)
                put("title", session.title)
                put("created_at", session.createdAt)
                put("updated_at", session.updatedAt)
            }
            writableDatabase.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error saving conversation", e)
        }
    }

    fun updateConversationTitle(id: String, newTitle: String) {
        try {
            val values = ContentValues().apply {
                put("title", newTitle)
                put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.update("conversations", values, "id = ?", arrayOf(id))
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error updating conversation title", e)
        }
    }

    fun deleteConversation(id: String) {
        try {
            writableDatabase.delete("conversations", "id = ?", arrayOf(id))
            writableDatabase.delete("messages", "conversation_id = ?", arrayOf(id))
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error deleting conversation", e)
        }
    }

    // --- MESSAGES ---

    fun insertMessage(msg: Message, conversationId: String = msg.conversationId) {
        try {
            val targetConvId = if (conversationId.isBlank()) "default" else conversationId
            val values = ContentValues().apply {
                put("id", msg.id)
                put("conversation_id", targetConvId)
                put("role", msg.role.name)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                put("tool_calls_json", JSONArray().toString())
                put("tool_results_json", JSONArray().toString())
            }
            writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)

            // Touch conversation timestamp
            val touchValues = ContentValues().apply {
                put("updated_at", msg.timestamp)
            }
            writableDatabase.update("conversations", touchValues, "id = ?", arrayOf(targetConvId))
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error inserting message", e)
        }
    }

    fun getMessagesForConversation(conversationId: String): List<Message> {
        val list = mutableListOf<Message>()
        try {
            readableDatabase.query(
                "messages",
                null,
                "conversation_id = ?",
                arrayOf(conversationId),
                null,
                null,
                "timestamp ASC"
            ).use { c ->
                val idIdx = c.getColumnIndex("id")
                val convIdx = c.getColumnIndex("conversation_id")
                val roleIdx = c.getColumnIndex("role")
                val contentIdx = c.getColumnIndex("content")
                val timeIdx = c.getColumnIndex("timestamp")

                while (c.moveToNext()) {
                    val id = c.getString(idIdx)
                    val conv = if (convIdx >= 0) c.getString(convIdx) else conversationId
                    val roleStr = c.getString(roleIdx)
                    val content = c.getString(contentIdx)
                    val time = c.getLong(timeIdx)
                    val role = try { MessageRole.valueOf(roleStr) } catch (_: Exception) { MessageRole.USER }
                    list.add(Message(id = id, conversationId = conv, role = role, content = content, timestamp = time))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting messages for conversation", e)
        }
        return list
    }

    fun getAllMessages(): List<Message> {
        val list = mutableListOf<Message>()
        try {
            readableDatabase.query("messages", null, null, null, null, null, "timestamp ASC").use { c ->
                val idIdx = c.getColumnIndex("id")
                val convIdx = c.getColumnIndex("conversation_id")
                val roleIdx = c.getColumnIndex("role")
                val contentIdx = c.getColumnIndex("content")
                val timeIdx = c.getColumnIndex("timestamp")

                while (c.moveToNext()) {
                    val id = c.getString(idIdx)
                    val conv = if (convIdx >= 0) c.getString(convIdx) else "default"
                    val roleStr = c.getString(roleIdx)
                    val content = c.getString(contentIdx)
                    val time = c.getLong(timeIdx)
                    val role = try { MessageRole.valueOf(roleStr) } catch (_: Exception) { MessageRole.USER }
                    list.add(Message(id = id, conversationId = conv, role = role, content = content, timestamp = time))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting messages", e)
        }
        return list
    }

    fun clearMessages() {
        try {
            writableDatabase.delete("messages", null, null)
            writableDatabase.delete("conversations", null, null)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing messages", e)
        }
    }

    fun clearMessagesForConversation(conversationId: String) {
        try {
            writableDatabase.delete("messages", "conversation_id = ?", arrayOf(conversationId))
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing messages for conversation", e)
        }
    }

    // --- MEMORY ---

    fun saveMemory(key: String, value: String, category: String = "general"): String {
        val id = UUID.randomUUID().toString()
        try {
            val values = ContentValues().apply {
                put("id", id)
                put("mem_key", key.trim().lowercase())
                put("mem_value", value.trim())
                put("category", category.trim().lowercase())
                put("timestamp", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("memory", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error saving memory", e)
        }
        return id
    }

    fun getMemory(key: String): String? {
        return try {
            readableDatabase.query(
                "memory",
                arrayOf("mem_value"),
                "mem_key = ?",
                arrayOf(key.trim().lowercase()),
                null,
                null,
                null
            ).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting memory", e)
            null
        }
    }

    fun searchMemory(query: String): List<MemoryEntry> {
        val list = mutableListOf<MemoryEntry>()
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error searching memory", e)
        }
        return list
    }

    fun getAllMemory(): List<MemoryEntry> {
        val list = mutableListOf<MemoryEntry>()
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting all memory", e)
        }
        return list
    }

    fun deleteMemory(keyOrId: String): Boolean {
        return try {
            val rows = writableDatabase.delete("memory", "mem_key = ? OR id = ?", arrayOf(keyOrId.lowercase(), keyOrId))
            rows > 0
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error deleting memory", e)
            false
        }
    }

    fun clearAllMemory() {
        try {
            writableDatabase.delete("memory", null, null)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing all memory", e)
        }
    }

    // --- PERSISTENT PERSONAL MEMORY (PHASE 4) ---

    fun insertPersistentMemory(record: PersistentMemoryRecord) {
        try {
            val values = ContentValues().apply {
                put("id", record.id)
                put("memory_type", record.memoryType.name)
                put("mem_key", record.key.trim().lowercase())
                put("mem_value", record.value.trim())
                put("category", record.category.trim().lowercase())
                put("provenance", record.provenance.name)
                put("confidence", record.confidence)
                put("verification_status", record.verificationStatus.name)
                put("consent_status", record.consentStatus.name)
                put("sensitivity", record.sensitivity.name)
                put("state", record.state.name)
                put("superseded_by_id", record.supersededById)
                put("created_at", record.createdAt)
                put("updated_at", record.updatedAt)
                put("last_confirmed_at", record.lastConfirmedAt)
                put("expires_at", record.expiresAt)
                put("source_conversation_id", record.sourceConversationId)
                put("source_message_id", record.sourceMessageId)
            }
            writableDatabase.insertWithOnConflict("persistent_memories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error inserting persistent memory", e)
        }
    }

    fun getPersistentMemory(id: String): PersistentMemoryRecord? {
        return try {
            readableDatabase.query(
                "persistent_memories",
                null,
                "id = ?",
                arrayOf(id),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursorToPersistentMemory(cursor) else null
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting persistent memory by id", e)
            null
        }
    }

    fun getActivePersistentMemories(): List<PersistentMemoryRecord> {
        val list = mutableListOf<PersistentMemoryRecord>()
        try {
            readableDatabase.query(
                "persistent_memories",
                null,
                "state = ?",
                arrayOf(MemoryState.ACTIVE.name),
                null, null,
                "updated_at DESC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToPersistentMemory(cursor))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting active persistent memories", e)
        }
        return list
    }

    fun searchPersistentMemories(query: String, maxResults: Int = 10): List<PersistentMemoryRecord> {
        val list = mutableListOf<PersistentMemoryRecord>()
        try {
            val wild = "%${query.trim().lowercase()}%"
            readableDatabase.query(
                "persistent_memories",
                null,
                "state = ? AND (mem_key LIKE ? OR mem_value LIKE ? OR category LIKE ?)",
                arrayOf(MemoryState.ACTIVE.name, wild, wild, wild),
                null, null,
                "updated_at DESC",
                maxResults.toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToPersistentMemory(cursor))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error searching persistent memories", e)
        }
        return list
    }

    fun supersedePersistentMemory(oldId: String, newId: String) {
        try {
            val values = ContentValues().apply {
                put("state", MemoryState.SUPERSEDED.name)
                put("superseded_by_id", newId)
                put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.update("persistent_memories", values, "id = ?", arrayOf(oldId))
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error superseding persistent memory", e)
        }
    }

    fun deletePersistentMemory(keyOrId: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put("state", MemoryState.DELETED.name)
                put("updated_at", System.currentTimeMillis())
            }
            val rows = writableDatabase.update(
                "persistent_memories",
                values,
                "(id = ? OR mem_key = ?) AND state = ?",
                arrayOf(keyOrId, keyOrId.trim().lowercase(), MemoryState.ACTIVE.name)
            )
            rows > 0
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error deleting persistent memory", e)
            false
        }
    }

    fun clearAllPersistentMemories() {
        try {
            writableDatabase.delete("persistent_memories", null, null)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing all persistent memories", e)
        }
    }

    private fun cursorToPersistentMemory(c: android.database.Cursor): PersistentMemoryRecord {
        return PersistentMemoryRecord(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            memoryType = try { MemoryType.valueOf(c.getString(c.getColumnIndexOrThrow("memory_type"))) } catch (_: Exception) { MemoryType.SAVED_FACT },
            key = c.getString(c.getColumnIndexOrThrow("mem_key")),
            value = c.getString(c.getColumnIndexOrThrow("mem_value")),
            category = c.getString(c.getColumnIndexOrThrow("category")),
            provenance = try { MemoryProvenance.valueOf(c.getString(c.getColumnIndexOrThrow("provenance"))) } catch (_: Exception) { MemoryProvenance.USER_EXPLICIT },
            confidence = c.getFloat(c.getColumnIndexOrThrow("confidence")),
            verificationStatus = try { VerificationStatus.valueOf(c.getString(c.getColumnIndexOrThrow("verification_status"))) } catch (_: Exception) { VerificationStatus.CONFIRMED },
            consentStatus = try { ConsentStatus.valueOf(c.getString(c.getColumnIndexOrThrow("consent_status"))) } catch (_: Exception) { ConsentStatus.EXPLICITLY_GRANTED },
            sensitivity = try { SensitivityLevel.valueOf(c.getString(c.getColumnIndexOrThrow("sensitivity"))) } catch (_: Exception) { SensitivityLevel.NORMAL },
            state = try { MemoryState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))) } catch (_: Exception) { MemoryState.ACTIVE },
            supersededById = c.getString(c.getColumnIndexOrThrow("superseded_by_id")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            lastConfirmedAt = c.getLong(c.getColumnIndexOrThrow("last_confirmed_at")),
            expiresAt = if (c.isNull(c.getColumnIndexOrThrow("expires_at"))) null else c.getLong(c.getColumnIndexOrThrow("expires_at")),
            sourceConversationId = c.getString(c.getColumnIndexOrThrow("source_conversation_id")),
            sourceMessageId = c.getString(c.getColumnIndexOrThrow("source_message_id"))
        )
    }

    // --- EPISODIC MEMORIES (PHASE 4) ---

    fun insertEpisodicMemory(record: EpisodicMemoryRecord) {
        try {
            val values = ContentValues().apply {
                put("id", record.id)
                put("title", record.title.trim())
                put("summary", record.summary.trim())
                put("decision_rationale", record.decisionRationale?.trim())
                put("outcome", record.outcome?.trim())
                put("task_status", record.taskStatus)
                put("source_conversation_id", record.sourceConversationId)
                put("created_at", record.createdAt)
                put("tags_json", JSONArray(record.tags).toString())
            }
            writableDatabase.insertWithOnConflict("episodic_memories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            // Prevent unbounded episodic table growth (prune to 200 most recent records)
            writableDatabase.execSQL("""
                DELETE FROM episodic_memories WHERE id NOT IN (
                    SELECT id FROM episodic_memories ORDER BY created_at DESC LIMIT 200
                )
            """.trimIndent())
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error inserting episodic memory", e)
        }
    }

    fun getEpisodicMemories(limit: Int = 10): List<EpisodicMemoryRecord> {
        val list = mutableListOf<EpisodicMemoryRecord>()
        try {
            readableDatabase.query(
                "episodic_memories",
                null,
                null,
                null,
                null, null,
                "created_at DESC",
                limit.toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToEpisodicMemory(cursor))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting episodic memories", e)
        }
        return list
    }

    fun searchEpisodicMemories(query: String, limit: Int = 5): List<EpisodicMemoryRecord> {
        val list = mutableListOf<EpisodicMemoryRecord>()
        try {
            val wild = "%${query.trim().lowercase()}%"
            readableDatabase.query(
                "episodic_memories",
                null,
                "title LIKE ? OR summary LIKE ? OR decision_rationale LIKE ? OR tags_json LIKE ?",
                arrayOf(wild, wild, wild, wild),
                null, null,
                "created_at DESC",
                limit.toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToEpisodicMemory(cursor))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error searching episodic memories", e)
        }
        return list
    }

    fun clearAllEpisodicMemories() {
        try {
            writableDatabase.delete("episodic_memories", null, null)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing all episodic memories", e)
        }
    }

    private fun cursorToEpisodicMemory(c: android.database.Cursor): EpisodicMemoryRecord {
        val tagsJson = c.getString(c.getColumnIndexOrThrow("tags_json")) ?: "[]"
        val tagsList = mutableListOf<String>()
        try {
            val arr = JSONArray(tagsJson)
            for (i in 0 until arr.length()) {
                tagsList.add(arr.getString(i))
            }
        } catch (e: Exception) {
            android.util.Log.w("TaviDatabase", "Failed parsing episodic tags_json: $tagsJson", e)
        }

        return EpisodicMemoryRecord(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            summary = c.getString(c.getColumnIndexOrThrow("summary")),
            decisionRationale = c.getString(c.getColumnIndexOrThrow("decision_rationale")),
            outcome = c.getString(c.getColumnIndexOrThrow("outcome")),
            taskStatus = c.getString(c.getColumnIndexOrThrow("task_status")),
            sourceConversationId = c.getString(c.getColumnIndexOrThrow("source_conversation_id")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            tags = tagsList
        )
    }

    // --- NOTES ---

    fun saveNote(title: String, content: String): String {
        val id = UUID.randomUUID().toString()
        try {
            val values = ContentValues().apply {
                put("id", id)
                put("title", title.trim())
                put("content", content.trim())
                put("timestamp", System.currentTimeMillis())
            }
            writableDatabase.insert("notes", null, values)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error saving note", e)
        }
        return id
    }

    fun getAllNotes(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting all notes", e)
        }
        return list
    }

    fun searchNotes(query: String): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val wild = "%${query.trim()}%"
            readableDatabase.query(
                "notes",
                null,
                "title LIKE ? OR content LIKE ?",
                arrayOf(wild, wild),
                null,
                null,
                "timestamp DESC"
            ).use { c ->
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
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error searching notes", e)
        }
        return list
    }

    fun deleteNote(id: String): Boolean {
        return try {
            val rows = writableDatabase.delete("notes", "id = ?", arrayOf(id))
            rows > 0
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error deleting note", e)
            false
        }
    }

    // --- REMINDERS ---

    fun insertReminder(id: String, title: String, triggerTime: Long): Boolean {
        return try {
            val values = ContentValues().apply {
                put("id", id)
                put("title", title)
                put("trigger_time", triggerTime)
                put("is_completed", 0)
            }
            val rowId = writableDatabase.insertWithOnConflict("reminders", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            rowId != -1L
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error inserting reminder", e)
            false
        }
    }

    fun getActiveReminders(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            readableDatabase.query(
                "reminders",
                null,
                "is_completed = 0",
                null,
                null,
                null,
                "trigger_time ASC"
            ).use { c ->
                val idIdx = c.getColumnIndex("id")
                val titleIdx = c.getColumnIndex("title")
                val timeIdx = c.getColumnIndex("trigger_time")
                while (c.moveToNext()) {
                    list.add(
                        mapOf(
                            "id" to c.getString(idIdx),
                            "title" to c.getString(titleIdx),
                            "triggerTime" to c.getLong(timeIdx)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error querying reminders", e)
        }
        return list
    }

    fun deleteReminder(id: String): Boolean {
        return try {
            val rows = writableDatabase.delete("reminders", "id = ?", arrayOf(id))
            rows > 0
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error deleting reminder", e)
            false
        }
    }

    // --- AUDIT LOGS ---

    fun insertAuditLog(entry: AuditLogEntry) {
        try {
            val values = ContentValues().apply {
                put("id", entry.id)
                put("tool_name", entry.toolName)
                put("status", entry.status)
                put("summary", entry.summary)
                put("timestamp", entry.timestamp)
                put("verified", if (entry.verified) 1 else 0)
            }
            writableDatabase.insert("audit_logs", null, values)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error inserting audit log", e)
        }
    }

    fun getAllAuditLogs(): List<AuditLogEntry> {
        val list = mutableListOf<AuditLogEntry>()
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting all audit logs", e)
        }
        return list
    }

    fun clearAuditLogs() {
        try {
            writableDatabase.delete("audit_logs", null, null)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing audit logs", e)
        }
    }

    // --- WORKFLOWS ---

    fun saveWorkflow(plan: WorkflowPlan) {
        try {
            val stepsJson = serializeSteps(plan.steps)
            val values = ContentValues().apply {
                put("id", plan.id)
                put("title", plan.title)
                put("user_query", plan.userQuery)
                put("state", plan.state.name)
                put("plan_source", plan.planSource.name)
                put("current_step_index", plan.currentStepIndex)
                put("steps_json", stepsJson)
                put("created_at", plan.createdAt)
                put("updated_at", plan.updatedAt)
                put("completed_at", plan.completedAt)
                put("error", plan.error)
                put("missing_input_prompt", plan.missingInputPrompt)
                put("missing_input_param", plan.missingInputParam)
                put("pending_confirmation_step_id", plan.pendingConfirmationStepId)
                put("summary_text", plan.executionSummary?.humanReadableText)
            }
            writableDatabase.insertWithOnConflict("workflows", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error saving workflow", e)
        }
    }

    fun getWorkflow(id: String): WorkflowPlan? {
        return try {
            readableDatabase.query("workflows", null, "id = ?", arrayOf(id), null, null, null).use { c ->
                if (c.moveToFirst()) {
                    parseWorkflowCursor(c)
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting workflow", e)
            null
        }
    }

    fun getActiveWorkflow(): WorkflowPlan? {
        return try {
            val activeStates = arrayOf(
                WorkflowState.PLANNING.name,
                WorkflowState.AWAITING_USER_INPUT.name,
                WorkflowState.AWAITING_CONFIRMATION.name,
                WorkflowState.EXECUTING.name,
                WorkflowState.VERIFYING.name
            )
            val placeholders = activeStates.joinToString(",") { "?" }
            readableDatabase.query(
                "workflows",
                null,
                "state IN ($placeholders)",
                activeStates,
                null,
                null,
                "updated_at DESC LIMIT 1"
            ).use { c ->
                if (c.moveToFirst()) {
                    parseWorkflowCursor(c)
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error getting active workflow", e)
            null
        }
    }

    fun clearWorkflows() {
        try {
            writableDatabase.delete("workflows", null, null)
        } catch (e: Exception) {
            android.util.Log.e("TaviDatabase", "Error clearing workflows", e)
        }
    }

    private fun parseWorkflowCursor(c: android.database.Cursor): WorkflowPlan {
        val id = c.getString(c.getColumnIndexOrThrow("id"))
        val title = c.getString(c.getColumnIndexOrThrow("title"))
        val query = c.getString(c.getColumnIndexOrThrow("user_query"))
        val stateStr = c.getString(c.getColumnIndexOrThrow("state"))
        val planSourceStr = c.getString(c.getColumnIndexOrThrow("plan_source"))
        val stepIdx = c.getInt(c.getColumnIndexOrThrow("current_step_index"))
        val stepsJson = c.getString(c.getColumnIndexOrThrow("steps_json"))
        val created = c.getLong(c.getColumnIndexOrThrow("created_at"))
        val updated = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        val completed = if (c.isNull(c.getColumnIndexOrThrow("completed_at"))) null else c.getLong(c.getColumnIndexOrThrow("completed_at"))
        val err = c.getString(c.getColumnIndexOrThrow("error"))
        val prompt = c.getString(c.getColumnIndexOrThrow("missing_input_prompt"))
        val param = c.getString(c.getColumnIndexOrThrow("missing_input_param"))
        val pendingStepId = c.getString(c.getColumnIndexOrThrow("pending_confirmation_step_id"))
        val summaryTxt = c.getString(c.getColumnIndexOrThrow("summary_text"))

        val state = try { WorkflowState.valueOf(stateStr) } catch (_: Exception) { WorkflowState.PLANNING }
        val source = try { PlanSource.valueOf(planSourceStr) } catch (_: Exception) { PlanSource.DETERMINISTIC }
        val steps = deserializeSteps(stepsJson)

        val summary = if (summaryTxt != null) {
            WorkflowExecutionSummary(
                totalSteps = steps.size,
                completedSteps = steps.count { it.status == StepExecutionStatus.VERIFIED || it.status == StepExecutionStatus.COMPLETED_UNVERIFIED },
                verifiedSteps = steps.count { it.status == StepExecutionStatus.VERIFIED },
                failedSteps = steps.count { it.status == StepExecutionStatus.FAILED },
                skippedSteps = steps.count { it.status == StepExecutionStatus.SKIPPED },
                isFullyVerified = steps.all { it.status == StepExecutionStatus.VERIFIED },
                humanReadableText = summaryTxt
            )
        } else null

        return WorkflowPlan(
            id = id,
            title = title,
            userQuery = query,
            state = state,
            planSource = source,
            steps = steps,
            currentStepIndex = stepIdx,
            createdAt = created,
            updatedAt = updated,
            completedAt = completed,
            error = err,
            missingInputPrompt = prompt,
            missingInputParam = param,
            pendingConfirmationStepId = pendingStepId,
            executionSummary = summary
        )
    }

    private fun serializeSteps(steps: List<WorkflowStep>): String {
        val array = JSONArray()
        for (step in steps) {
            val obj = JSONObject()
            obj.put("id", step.id)
            obj.put("stepIndex", step.stepIndex)
            obj.put("title", step.title)
            obj.put("toolName", step.toolName)

            val argsObj = JSONObject()
            for ((k, v) in step.arguments) {
                argsObj.put(k, v ?: JSONObject.NULL)
            }
            obj.put("arguments", argsObj)

            val bindingsArr = JSONArray()
            for (b in step.dynamicBindings) {
                val bObj = JSONObject()
                bObj.put("targetArg", b.targetArg)
                bObj.put("fromStepId", b.fromStepId)
                bObj.put("fromPropertyPath", b.fromPropertyPath)
                if (b.defaultValue != null) bObj.put("defaultValue", b.defaultValue)
                bindingsArr.put(bObj)
            }
            obj.put("dynamicBindings", bindingsArr)

            obj.put("isReadOnly", step.isReadOnly)
            obj.put("riskLevel", step.riskLevel.name)
            if (step.requiredPermission != null) obj.put("requiredPermission", step.requiredPermission)
            obj.put("status", step.status.name)
            obj.put("verificationStatus", step.verificationStatus.name)
            if (step.verificationDetail != null) obj.put("verificationDetail", step.verificationDetail)
            if (step.errorMessage != null) obj.put("errorMessage", step.errorMessage)
            obj.put("idempotencyKey", step.idempotencyKey)
            obj.put("maxRetries", step.maxRetries)
            obj.put("retriesAttempted", step.retriesAttempted)
            val dependsOnArr = JSONArray()
            for (dep in step.dependsOnStepIds) {
                dependsOnArr.put(dep)
            }
            obj.put("dependsOnStepIds", dependsOnArr)
            if (step.executedAt != null) obj.put("executedAt", step.executedAt)

            if (step.result != null) {
                val rObj = JSONObject()
                rObj.put("toolCallId", step.result.toolCallId)
                rObj.put("toolName", step.result.toolName)
                rObj.put("resultJson", step.result.resultJson)
                rObj.put("isSuccess", step.result.isSuccess)
                if (step.result.errorMessage != null) rObj.put("errorMessage", step.result.errorMessage)
                obj.put("result", rObj)
            }

            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeSteps(jsonStr: String): List<WorkflowStep> {
        val list = mutableListOf<WorkflowStep>()
        if (jsonStr.isBlank()) return list

        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val stepIdx = obj.getInt("stepIndex")
                val title = obj.getString("title")
                val toolName = obj.getString("toolName")

                val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
                val args = mutableMapOf<String, Any?>()
                for (k in argsObj.keys()) {
                    args[k] = if (argsObj.isNull(k)) null else argsObj.get(k)
                }

                val bindingsList = mutableListOf<DynamicBinding>()
                val bindingsArr = obj.optJSONArray("dynamicBindings")
                if (bindingsArr != null) {
                    for (b in 0 until bindingsArr.length()) {
                        val bObj = bindingsArr.getJSONObject(b)
                        bindingsList.add(
                            DynamicBinding(
                                targetArg = bObj.getString("targetArg"),
                                fromStepId = bObj.getString("fromStepId"),
                                fromPropertyPath = bObj.getString("fromPropertyPath"),
                                defaultValue = if (bObj.has("defaultValue") && !bObj.isNull("defaultValue")) bObj.getString("defaultValue") else null
                            )
                        )
                    }
                }

                val isReadOnly = obj.optBoolean("isReadOnly", false)
                val risk = try { ToolRiskLevel.valueOf(obj.getString("riskLevel")) } catch (_: Exception) { ToolRiskLevel.LOW }
                val perm = if (obj.has("requiredPermission") && !obj.isNull("requiredPermission")) obj.getString("requiredPermission") else null
                val status = try { StepExecutionStatus.valueOf(obj.getString("status")) } catch (_: Exception) { StepExecutionStatus.PENDING }
                val verStatus = try { StepVerificationStatus.valueOf(obj.getString("verificationStatus")) } catch (_: Exception) { StepVerificationStatus.NOT_APPLICABLE }
                val verDetail = if (obj.has("verificationDetail") && !obj.isNull("verificationDetail")) obj.getString("verificationDetail") else null
                val errMsg = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.getString("errorMessage") else null
                val idempotencyKey = obj.optString("idempotencyKey", UUID.randomUUID().toString())
                val maxRetries = obj.optInt("maxRetries", 1)
                val retriesAttempted = obj.optInt("retriesAttempted", 0)
                val dependsOnList = mutableListOf<String>()
                val dependsOnArr = obj.optJSONArray("dependsOnStepIds")
                if (dependsOnArr != null) {
                    for (d in 0 until dependsOnArr.length()) {
                        dependsOnList.add(dependsOnArr.getString(d))
                    }
                }
                val executedAt = if (obj.has("executedAt")) obj.getLong("executedAt") else null

                var toolResult: ToolResult? = null
                val rObj = obj.optJSONObject("result")
                if (rObj != null) {
                    toolResult = ToolResult(
                        toolCallId = rObj.getString("toolCallId"),
                        toolName = rObj.getString("toolName"),
                        resultJson = rObj.getString("resultJson"),
                        isSuccess = rObj.getBoolean("isSuccess"),
                        errorMessage = if (rObj.has("errorMessage") && !rObj.isNull("errorMessage")) rObj.getString("errorMessage") else null
                    )
                }

                list.add(
                    WorkflowStep(
                        id = id,
                        stepIndex = stepIdx,
                        title = title,
                        toolName = toolName,
                        arguments = args,
                        dynamicBindings = bindingsList,
                        isReadOnly = isReadOnly,
                        riskLevel = risk,
                        requiredPermission = perm,
                        status = status,
                        verificationStatus = verStatus,
                        result = toolResult,
                        verificationDetail = verDetail,
                        errorMessage = errMsg,
                        retriesAttempted = retriesAttempted,
                        maxRetries = maxRetries,
                        dependsOnStepIds = dependsOnList,
                        idempotencyKey = idempotencyKey,
                        executedAt = executedAt
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("TaviDatabase", "Failed parsing workflow steps: $jsonStr", e)
        }

        return list
    }
}
