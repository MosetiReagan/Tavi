package com.example.tavi.memory

import org.junit.Assert.*
import org.junit.Test

class MemoryRelevanceTest {
    @Test fun findsSynonymsAndSwahiliWithoutInjectingUnrelatedRecentFacts() {
        val doctor = PersistentMemoryRecord(key = "doctor", value = "Dr. Mwangi", category = "medical")
        val editor = PersistentMemoryRecord(key = "editor", value = "Neovim")
        assertEquals(listOf(doctor), MemoryRelevance.select("Who is my physician?", listOf(editor, doctor), 180))
        assertEquals(listOf(doctor), MemoryRelevance.select("Daktari wangu ni nani?", listOf(editor, doctor), 180))
        assertTrue(MemoryRelevance.select("How is the weather?", listOf(doctor, editor), 180).isEmpty())
    }
    @Test fun excludesSupersededUnapprovedExpiredAndOversizedMemories() {
        val base = PersistentMemoryRecord(key = "doctor", value = "New doctor")
        val records = listOf(base, base.copy(state = MemoryState.SUPERSEDED), base.copy(consentStatus = ConsentStatus.PENDING_APPROVAL),
            base.copy(expiresAt = 1), base.copy(value = "doctor ".repeat(1000)))
        assertEquals(listOf(base), MemoryRelevance.select("doctor", records, 180, 100))
    }
}
