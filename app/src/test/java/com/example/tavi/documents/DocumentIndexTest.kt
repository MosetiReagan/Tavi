package com.example.tavi.documents

import org.junit.Assert.*
import org.junit.Test

class DocumentIndexTest {
    @Test fun chunksPreserveTextAcrossBoundaries() {
        val text = (1..2000).joinToString(" ") { "word$it" }
        val chunks = DocumentIndex.chunks(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 1200 })
        assertTrue((1..2000).all { number -> chunks.any { it.contains("word$number") } })
        assertTrue(chunks.first().contains("word1"))
        assertTrue(chunks.last().contains("word2000"))
    }
    @Test fun searchQueryEscapesFtsOperatorsAndPreservesUnicode() {
        assertEquals("\"meeting\"* OR \"budget\"*", DocumentIndex.matchQuery("meeting \" budget * -"))
        assertTrue(DocumentIndex.terms("mikutano Nairobi").contains("mikutano"))
        assertEquals("", DocumentIndex.matchQuery("\" * -"))
        assertTrue(DocumentIndex.chunks(" ").isEmpty())
    }
}
