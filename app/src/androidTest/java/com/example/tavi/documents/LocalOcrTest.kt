package com.example.tavi.documents

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.tavi.language.AssistantLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class LocalOcrTest {
    @Test fun englishImageImportProducesCitedSearchResults() = verifyImage("en", "Tavi offline library", "offline")

    @Test fun kiswahiliImageImportLoadsBundledLanguagePacks() = verifyImage("sw", "Jambo rafiki", "rafiki")

    private fun verifyImage(language: String, text: String, query: String) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previousLanguage = AssistantLanguage.selected(context)
        val source = File.createTempFile("ocr_fixture_", ".png", context.cacheDir)
        val library = DocumentLibrary.getInstance(context)
        var imported: LocalDocument? = null
        try {
            AssistantLanguage.set(context, language)
            val image = Bitmap.createBitmap(1000, 240, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(Color.WHITE)
                Canvas(image).drawText(text, 40f, 145f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 64f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                })
                source.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { image.recycle() }

            // A framework resolver supplies a controlled content URI without exporting a test provider.
            val provider = ImageSource(source).apply {
                attachInfo(context, ProviderInfo().apply {
                    authority = "tavi.ocr.fixture"
                    applicationInfo = context.applicationInfo
                    exported = false
                })
            }
            val resolver = ContentResolver.wrap(provider)
            val importContext = object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = resolver
            }
            val document = LocalDocumentImporter(importContext).import(Uri.parse("content://tavi.ocr.fixture/image")) {}
            imported = document
            val hits = library.search(query).filter { it.sourceId == document.id }
            assertTrue("OCR text should be searchable with source citations", hits.isNotEmpty())
            assertEquals(1, hits.first().page)
            assertTrue(hits.first().excerpt.contains(query, ignoreCase = true))
            if (language == "sw") assertTrue(File(context.filesDir, "ocr/tessdata/swa.traineddata").isFile)
        } finally {
            imported?.let { library.delete(it.id) }
            source.delete()
            AssistantLanguage.set(context, previousLanguage)
        }
    }

    private class ImageSource(private val image: File) : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(image.name)) }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    }
}
