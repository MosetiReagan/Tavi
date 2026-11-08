package com.example.tavi.documents

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tavi.data.TaviDatabase
import com.example.tavi.models.LocalModelPacks
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SemanticDocumentSearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun isolated(): Pair<DocumentLibrary, File> {
        val dir = File(context.cacheDir, "semantic-test-${UUID.randomUUID()}").apply { mkdirs() }
        return libraryAt(dir) to dir
    }
    private fun libraryAt(dir: File): DocumentLibrary {
        // Keep the application-wide notes helper attached to its real private database.
        TaviDatabase.getInstance(context)
        val wrapped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(dir, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, handler)
        }
        return DocumentLibrary(wrapped)
    }
    private class FakeEmbedder : DocumentEmbedder {
        override var fingerprint = "fake-v1"
        var calls = 0
        var onEmbed: ((String) -> Unit)? = null
        override fun available() = true
        override suspend fun embed(text: String): FloatArray {
            calls++; onEmbed?.invoke(text)
            return FloatArray(384).apply { this[if (text.contains("fund") || text.contains("expense")) 0 else 1] = 1f }
        }
    }
    @Test fun vectorsPersistReindexOnModelChangeAndDisappearWithDocuments() = runBlocking {
        val (initial, dir) = isolated(); var library = initial; val model = FakeEmbedder()
        try {
            val relevant = library.add("Emergency fund", listOf("Set cash aside for difficult times."))
            library.add("Garden", listOf("Water the flowers every morning."))
            library.indexSemantics(128, model); val count = model.calls
            library.close(); library = libraryAt(dir)
            library.indexSemantics(128, model); assertEquals(count, model.calls)
            val result = library.searchHybrid("unexpected expenses", embedder = model)
            assertTrue(result.semantic); assertEquals(relevant.id, result.hits.first().sourceId)
            model.fingerprint = "fake-v2"; library.indexSemantics(128, model); assertTrue(model.calls > count + 1)
            library.delete(relevant.id)
            assertFalse(library.searchHybrid("unexpected expenses", embedder = model).hits.any { it.sourceId == relevant.id })
            library.readableDatabase.rawQuery("SELECT count(*) FROM semantic_vectors WHERE source_id=?", arrayOf(relevant.id)).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        } finally { library.close(); dir.deleteRecursively() }
    }
    @Test fun aDocumentDeletedDuringInferenceCannotBeResurrected() = runBlocking {
        val (library, dir) = isolated()
        try {
            val document = library.add("delete-during-decode", listOf("An emergency fund protects savings."))
            val model = FakeEmbedder().apply { onEmbed = { if (it.contains("delete-during-decode")) library.delete(document.id) } }
            library.indexSemantics(128, model)
            assertFalse(library.searchHybrid("fund", embedder = model).hits.any { it.sourceId == document.id })
            library.readableDatabase.rawQuery("SELECT count(*) FROM semantic_vectors WHERE source_id=?", arrayOf(document.id)).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        } finally { library.close(); dir.deleteRecursively() }
    }
    @Test fun deletedNotesAreRemovedFromTheSemanticIndex() = runBlocking {
        val library = DocumentLibrary.getInstance(context); val notes = TaviDatabase.getInstance(context)
        val id = notes.saveNote("semantic-note-${UUID.randomUUID()}", "Emergency fund for family expenses.")
        val model = FakeEmbedder()
        try {
            assertTrue(library.searchHybrid("fund", 20, model).hits.first { it.sourceId == id }.isNote)
            assertTrue(notes.deleteNote(id))
            assertFalse(library.searchHybrid("fund", 20, model).hits.any { it.sourceId == id })
            library.readableDatabase.rawQuery("SELECT count(*) FROM semantic_vectors WHERE source_type=1 AND source_id=?", arrayOf(id)).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        } finally { notes.deleteNote(id); library.clearSemanticIndex() }
    }
    @Test fun legacyDocumentDatabaseUpgradesWithoutLosingText() {
        val (library, dir) = isolated()
        try {
            SQLiteDatabase.openOrCreateDatabase(File(dir, "tavi_documents.db"), null).use { db ->
                db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, title TEXT NOT NULL, pages INTEGER NOT NULL, imported_at INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE chunks USING fts4(doc_id,title,page,content,tokenize=unicode61)")
                db.execSQL("INSERT INTO documents VALUES ('legacy','Existing document',1,1)")
                db.execSQL("INSERT INTO chunks VALUES ('legacy','Existing document',1,'Preserved text')"); db.version = 1
            }
            assertEquals("legacy", library.list().single().id)
            assertEquals("Preserved text", library.search("Preserved").first().excerpt)
            assertEquals(2, library.readableDatabase.version)
        } finally { library.close(); dir.deleteRecursively() }
    }
    @Test fun actualMultilingualTokenizerMatchesPythonReferenceIds() = runBlocking {
        val packs = LocalModelPacks.getInstance(context); assumeTrue(packs.isInstalled("semantic-search"))
        val dir = packs.requireVerified("semantic-search")
        val tokenizer = E5Tokenizer.fromReader(File(dir, "tokenizer.json").reader())
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val cases = testContext.assets.open("e5-tokenizer-reference.json").reader().use { JsonParser.parseReader(it).asJsonArray }
        for (case in cases) {
            val obj = case.asJsonObject; val max = obj["maxLength"]?.asInt ?: 256
            assertArrayEquals(obj["text"].asString, obj["ids"].asJsonArray.map { it.asLong }.toLongArray(), tokenizer.encode(obj["text"].asString, max))
        }
    }
    @Test fun realE5RetrievesParaphrasesInEnglishAndKiswahiliWithPageCitations() = runBlocking {
        val runtime = LocalEmbeddingRuntime.getInstance(context); assumeTrue(runtime.available())
        val (library, dir) = isolated()
        try {
            val target = library.add("Family safety reserve", listOf("A cash cushion covering three months of living costs helps the household survive losing a job or paying hospital fees."))
            library.add("Bicycle maintenance", listOf("Lubricate the chain and check tyre pressure before a ride."))
            library.indexSemantics(128, runtime)
            for (query in listOf("How can I prepare for unexpected medical expenses?", "Ninawezaje kulinda familia yangu nikikosa mshahara?")) {
                val start = System.currentTimeMillis(); val result = library.searchHybrid(query, embedder = runtime)
                assertTrue(result.message.orEmpty(), result.semantic)
                Log.i("TaviSemanticTest", "$query -> ${result.hits.map { it.reference }} in ${System.currentTimeMillis() - start} ms")
                assertEquals(target.id, result.hits.first().sourceId)
                assertEquals("Family safety reserve, page 1", result.hits.first().reference)
            }
        } finally { runtime.release(); library.close(); dir.deleteRecursively() }
    }
}
