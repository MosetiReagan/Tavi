package com.example.tavi.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tavi.documents.DocumentLibrary
import com.example.tavi.model.ToolCall
import com.example.tavi.model.ToolResult
import com.example.tavi.tools.ExecutionReceipts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    @Test fun documentSearchKeepsPageReferencesAndDeletionRemovesIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = DocumentLibrary.getInstance(context)
        val word = "regression" + UUID.randomUUID().toString().replace("-", "")
        val document = library.add("Test document", listOf("First page.", "Second page contains $word"))
        try {
            val hit = library.search(word).single()
            assertEquals(document.id, hit.sourceId)
            assertEquals(2, hit.page)
        } finally { library.delete(document.id) }
        assertTrue(library.search(word).isEmpty())
    }
    @Test fun receiptSurvivesDatabaseCloseAndPreventsReplay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = TaviDatabase(context)
        val id = UUID.randomUUID().toString()
        val call = ToolCall(id, "test_write", mapOf("value" to "example"))
        var executions = 0
        ExecutionReceipts(first.executionReceiptStore()).execute(call) { executions++; ToolResult(id, call.toolName, "{}") }
        first.close()
        val reopened = TaviDatabase(context)
        try {
            ExecutionReceipts(reopened.executionReceiptStore()).execute(call) { executions++; ToolResult(id, call.toolName, "{}") }
            assertEquals(1, executions)
        } finally { reopened.writableDatabase.delete("execution_receipts", "id = ?", arrayOf(id)); reopened.close() }
    }
}
