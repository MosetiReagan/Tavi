package com.example.tavi.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** All extraction runs locally; selected source files are copied temporarily and then removed. */
class LocalDocumentImporter(private val context: Context) {
    suspend fun import(uri: Uri, progress: (String) -> Unit): LocalDocument = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Imported document"
        val mime = context.contentResolver.getType(uri).orEmpty()
        val temp = File.createTempFile("tavi_document_", ".tmp", context.cacheDir)
        try {
            progress("Reading $name")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected document cannot be opened." }
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 20L * 1024 * 1024) { "Import files up to 20 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val pages = when {
                mime.startsWith("text/") || name.endsWith(".txt", true) || name.endsWith(".md", true) -> {
                    require(temp.length() <= 1_000_000) { "Text documents must be under 1 MB." }
                    listOf(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(temp.readBytes())).toString())
                }
                mime == "application/pdf" || name.endsWith(".pdf", true) -> withOcr { ocr ->
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { pdf ->
                            require(pdf.pageCount <= 40) { "Import PDFs with up to 40 pages." }
                            buildList { for (index in 0 until pdf.pageCount) {
                                currentCoroutineContext().ensureActive()
                                progress("Reading page ${index + 1} of ${pdf.pageCount} on device")
                                pdf.openPage(index).use { page ->
                                    val scale = minOf(1600f / page.width, 2000f / page.height)
                                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                                        (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        ocr.setImage(bitmap); add(ocr.utF8Text.orEmpty()); ocr.clear()
                                    } finally { bitmap.recycle() }
                                }
                            } }
                        }
                    }
                }
                mime.startsWith("image/") -> withOcr { ocr ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.path, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported image." }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = 1
                    while (options.outWidth / options.inSampleSize > 1800 || options.outHeight / options.inSampleSize > 2200) options.inSampleSize *= 2
                    val bitmap = requireNotNull(BitmapFactory.decodeFile(temp.path, options)) { "Cannot decode image." }
                    try { progress("Recognizing text on device"); ocr.setImage(bitmap); listOf(ocr.utF8Text.orEmpty()) }
                    finally { bitmap.recycle() }
                }
                else -> error("Choose a UTF-8 text file, PDF, or image.")
            }
            currentCoroutineContext().ensureActive()
            require(pages.sumOf { it.length } <= 1_000_000) { "Extracted text exceeds the 1 MB import limit." }
            DocumentLibrary.getInstance(context).add(name, pages)
        } finally { temp.delete() }
    }
    private suspend fun <T> withOcr(block: suspend (TessBaseAPI) -> T): T {
        val root = File(context.filesDir, "ocr")
        val data = File(root, "tessdata").apply { mkdirs() }
        val language = File(data, "eng.traineddata")
        if (!language.exists()) context.assets.open("tessdata/eng.traineddata").use { input -> language.outputStream().use { input.copyTo(it) } }
        val engine = TessBaseAPI()
        try {
            require(engine.init(root.path, "eng")) { "Offline text recognition could not start." }
            return block(engine)
        } finally { engine.recycle() }
    }
}
