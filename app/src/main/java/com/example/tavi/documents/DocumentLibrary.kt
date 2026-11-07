package com.example.tavi.documents

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.tavi.data.TaviDatabase
import java.util.UUID

class DocumentLibrary private constructor(private val context: Context) : SQLiteOpenHelper(context, "tavi_documents.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, title TEXT NOT NULL, pages INTEGER NOT NULL, imported_at INTEGER NOT NULL)")
        db.execSQL("CREATE VIRTUAL TABLE chunks USING fts4(doc_id, title, page, content, tokenize=unicode61)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun add(title: String, pages: List<String>): LocalDocument {
        require(pages.any { it.isNotBlank() }) { "No readable text was found. Try a clearer image or PDF." }
        val document = LocalDocument(UUID.randomUUID().toString(), title.take(200), pages.size, System.currentTimeMillis())
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("documents", null, ContentValues().apply {
                put("id", document.id); put("title", document.title); put("pages", pages.size); put("imported_at", document.importedAt)
            })
            pages.forEachIndexed { index, text -> DocumentIndex.chunks(text).forEach { chunk ->
                db.insertOrThrow("chunks", null, ContentValues().apply {
                    put("doc_id", document.id); put("title", document.title); put("page", index + 1); put("content", chunk)
                })
            } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return document
    }
    fun list(): List<LocalDocument> = readableDatabase.rawQuery("SELECT * FROM documents ORDER BY imported_at DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(LocalDocument(c.getString(0), c.getString(1), c.getInt(2), c.getLong(3))) }
    }
    fun delete(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chunks", "doc_id = ?", arrayOf(id))
            db.delete("documents", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun search(query: String, limit: Int = 8): List<DocumentHit> {
        val terms = DocumentIndex.terms(query)
        if (terms.isEmpty()) return emptyList()
        val hits = readableDatabase.rawQuery("SELECT doc_id,title,page,content FROM chunks WHERE chunks MATCH ? LIMIT 100",
            arrayOf(DocumentIndex.matchQuery(query))).use { c -> buildList {
                while (c.moveToNext()) add(DocumentHit(c.getString(0), c.getString(1), c.getInt(2), c.getString(3)))
            } }
        val notes = TaviDatabase.getInstance(context).getAllNotes().mapNotNull { note ->
            val text = note["content"].toString()
            val title = note["title"].toString()
            if (terms.none { (title + " " + text).lowercase().contains(it) }) null else
                DocumentHit(note["id"].toString(), title, 1, text, true)
        }
        return (hits + notes).sortedByDescending { hit -> terms.count { (hit.title + " " + hit.excerpt).lowercase().contains(it) } }
            .distinctBy { Triple(it.sourceId, it.page, it.excerpt) }.take(limit.coerceIn(1, 20)).map { hit ->
                val position = terms.map { hit.excerpt.lowercase().indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
                val start = (position - 120).coerceAtLeast(0)
                hit.copy(excerpt = (if (start > 0) "…" else "") + hit.excerpt.drop(start).take(700))
            }
    }
    companion object {
        @Volatile private var instance: DocumentLibrary? = null
        fun getInstance(context: Context): DocumentLibrary = instance ?: synchronized(this) {
            instance ?: DocumentLibrary(context.applicationContext).also { instance = it }
        }
    }
}
