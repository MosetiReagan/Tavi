package com.example.tavi.documents

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.tavi.data.TaviDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class IndexedChunk(val rowId: Long, val hit: DocumentHit) {
    val type: Int get() = if (hit.isNote) 1 else 0
    val fingerprint: String = MessageDigest.getInstance("SHA-256").digest(
        "${hit.sourceId}\n${hit.title}\n${hit.page}\n${hit.excerpt}".toByteArray()).joinToString("") { "%02x".format(it) }
}
data class SemanticCoverage(val indexed: Int, val total: Int) {
    val complete get() = indexed >= total
}
data class DocumentSearchResult(val hits: List<DocumentHit>, val semantic: Boolean, val coverage: SemanticCoverage? = null, val message: String? = null)

/** Persisted vectors contain no source text; every hit is joined to its current source before use. */
internal class SemanticDocumentIndex(private val library: DocumentLibrary, private val notes: TaviDatabase) {
    private val mutex = Mutex()
    private val db get() = library.writableDatabase
    private fun chunks(): List<IndexedChunk> {
        val documents = db.rawQuery("SELECT rowid,doc_id,title,page,content FROM chunks", null).use { c -> buildList {
            while (c.moveToNext()) add(IndexedChunk(c.getLong(0), DocumentHit(c.getString(1), c.getString(2), c.getInt(3), c.getString(4))))
        } }
        val noteChunks = notes.getAllNotes().flatMap { note ->
            DocumentIndex.chunks(note["content"].toString()).mapIndexed { index, text ->
                IndexedChunk(index.toLong(), DocumentHit(note["id"].toString(), note["title"].toString(), 1, text, true))
            }
        }
        return documents + noteChunks
    }
    private fun vector(chunk: IndexedChunk, model: String): FloatArray? = db.rawQuery(
        "SELECT vector FROM semantic_vectors WHERE source_type=? AND source_id=? AND chunk_id=? AND model=? AND text_fingerprint=?",
        arrayOf(chunk.type.toString(), chunk.hit.sourceId, chunk.rowId.toString(), model, chunk.fingerprint)).use { c ->
        if (!c.moveToFirst()) null else decode(c.getBlob(0))
    }
    private fun stillCurrent(chunk: IndexedChunk): Boolean = if (chunk.hit.isNote) {
        val note = notes.getAllNotes().firstOrNull { it["id"] == chunk.hit.sourceId }
        note != null && note["title"] == chunk.hit.title &&
            DocumentIndex.chunks(note["content"].toString()).getOrNull(chunk.rowId.toInt()) == chunk.hit.excerpt
    } else db.rawQuery("SELECT doc_id,title,page,content FROM chunks WHERE rowid=?", arrayOf(chunk.rowId.toString())).use { c ->
        c.moveToFirst() && c.getString(0) == chunk.hit.sourceId && c.getString(1) == chunk.hit.title &&
            c.getInt(2) == chunk.hit.page && c.getString(3) == chunk.hit.excerpt
    }
    private fun store(chunk: IndexedChunk, model: String, embedding: FloatArray) = synchronized(DocumentLibrary.noteIndexLock) {
        val database = db
        database.beginTransaction()
        try {
            if (stillCurrent(chunk)) database.insertWithOnConflict("semantic_vectors", null, ContentValues().apply {
                put("source_type", chunk.type); put("source_id", chunk.hit.sourceId); put("chunk_id", chunk.rowId)
                put("model", model); put("text_fingerprint", chunk.fingerprint); put("vector", encode(embedding))
            }, SQLiteDatabase.CONFLICT_REPLACE)
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }
    private fun prune(current: List<IndexedChunk>, model: String) {
        val keys = current.map { Triple(it.type, it.hit.sourceId, it.rowId) }.toSet()
        db.delete("semantic_vectors", "model<>?", arrayOf(model))
        val stale = db.rawQuery("SELECT source_type,source_id,chunk_id FROM semantic_vectors", null).use { c ->
            buildList { while (c.moveToNext()) {
                val key = Triple(c.getInt(0), c.getString(1), c.getLong(2)); if (key !in keys) add(key)
            } }
        }
        for ((type, id, row) in stale) db.delete("semantic_vectors", "source_type=? AND source_id=? AND chunk_id=?", arrayOf(type.toString(), id, row.toString()))
    }
    private suspend fun indexBatch(current: List<IndexedChunk>, embedder: DocumentEmbedder, batchSize: Int, progress: (Int, Int) -> Unit): SemanticCoverage {
        val model = embedder.fingerprint
        var ready = current.count { vector(it, model) != null }; var generated = 0
        progress(ready, current.size)
        for (chunk in current) {
            currentCoroutineContext().ensureActive()
            if (vector(chunk, model) != null) continue
            if (generated == batchSize) break
            val embedding = embedder.embed("passage: ${chunk.hit.title}\n${chunk.hit.excerpt}")
            currentCoroutineContext().ensureActive()
            store(chunk, model, embedding); generated++
            if (vector(chunk, model) != null) ready++
            progress(ready, current.size)
        }
        return SemanticCoverage(ready, current.size)
    }
    suspend fun index(embedder: DocumentEmbedder, batchSize: Int, progress: (Int, Int) -> Unit): SemanticCoverage = withContext(Dispatchers.IO) { mutex.withLock {
        require(batchSize in 1..128 && embedder.available())
        val current = chunks(); prune(current, embedder.fingerprint)
        indexBatch(current, embedder, batchSize, progress)
    } }
    suspend fun search(query: String, limit: Int, embedder: DocumentEmbedder, progress: (Int, Int) -> Unit): DocumentSearchResult = withContext(Dispatchers.IO) { mutex.withLock {
        val current = chunks(); prune(current, embedder.fingerprint)
        val coverage = indexBatch(current, embedder, 24, progress)
        val queryVector = embedder.embed("query: ${query.trim()}")
        val terms = DocumentIndex.terms(query)
        val ranked = current.mapNotNull { chunk ->
            currentCoroutineContext().ensureActive()
            val embedding = vector(chunk, embedder.fingerprint)
            val text = (chunk.hit.title + " " + chunk.hit.excerpt).lowercase()
            val lexical = if (terms.isEmpty()) 0.0 else terms.count { it in text }.toDouble() / terms.size
            if (embedding == null && lexical == 0.0) null else {
                val similarity = embedding?.let { EmbeddingMath.cosine(queryVector, it) } ?: .70
                if (similarity < .70 && lexical == 0.0) null else chunk to (similarity + .03 * lexical)
            }
        }.sortedByDescending { it.second }
        // Re-read sources after inference: deletion during a slow decode cannot revive an excerpt.
        val hits = ranked.asSequence().filter { stillCurrent(it.first) }.map { it.first.hit }
            .distinctBy { Triple(it.sourceId, it.page, it.excerpt) }.take(limit.coerceIn(1, 20)).map { it.copy(excerpt = it.excerpt.take(700)) }.toList()
        DocumentSearchResult(hits, true, coverage)
    } }
    companion object {
        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS semantic_vectors (source_type INTEGER NOT NULL, source_id TEXT NOT NULL, chunk_id INTEGER NOT NULL, model TEXT NOT NULL, text_fingerprint TEXT NOT NULL, vector BLOB NOT NULL, PRIMARY KEY(source_type,source_id,chunk_id))")
        }
        private fun encode(vector: FloatArray): ByteArray {
            require(vector.size == 384 && vector.all { it.isFinite() })
            return ByteBuffer.allocate(384 * 4).order(ByteOrder.LITTLE_ENDIAN).apply { vector.forEach { putFloat(it) } }.array()
        }
        private fun decode(blob: ByteArray): FloatArray? {
            if (blob.size != 384 * 4) return null
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(384) { buffer.float }.takeIf { it.all(Float::isFinite) }
        }
    }
}
