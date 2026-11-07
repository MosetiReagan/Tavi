package com.example.tavi.documents

object DocumentIndex {
    fun chunks(text: String, size: Int = 1200): List<String> {
        require(size >= 200)
        val clean = text.replace('\u0000', ' ').trim()
        if (clean.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = (start + size).coerceAtMost(clean.length)
            if (end < clean.length) {
                val boundary = clean.lastIndexOf(' ', end)
                if (boundary > start + size / 2) end = boundary
            }
            result.add(clean.substring(start, end).trim())
            if (end == clean.length) break
            start = (end - 120).coerceAtLeast(start + 1)
        }
        return result
    }
    fun terms(query: String): List<String> = Regex("[\\p{L}\\p{N}]{2,}").findAll(query.lowercase())
        .map { it.value }.distinct().take(12).toList()
    fun matchQuery(query: String): String = terms(query).joinToString(" OR ") { "\"$it\"*" }
}

data class LocalDocument(val id: String, val title: String, val pages: Int, val importedAt: Long)
data class DocumentHit(val sourceId: String, val title: String, val page: Int, val excerpt: String, val isNote: Boolean = false) {
    val reference: String get() = if (isNote) "Note: $title" else "$title, page $page"
}
