package com.example.tavi.memory

import com.example.tavi.context.ContextBudgetManager

/** Local word and synonym retrieval. It never injects unrelated memories just because they are recent. */
object MemoryRelevance {
    private val stopWords = "a an the my me i you your is are was do does did what which who where how to of for in and or it that about now please yangu mimi wewe ni ya na kwa je nini".split(' ').toSet()
    private val groups = listOf(
        "doctor physician medical clinic hospital daktari hospitali afya",
        "work job employer company occupation career kazi mwajiri",
        "home residence live reside city town address location nyumbani mji anwani",
        "meeting meetings appointment calendar schedule agenda mkutano mikutano ratiba",
        "prefer preferred preference favorite favourite like napenda pendekezo",
        "phone telephone mobile number simu namba",
        "project goal objective task mradi lengo",
        "name called named jina")
    private val synonyms = groups.flatMapIndexed { index, group -> group.split(' ').map { it to "concept$index" } }.toMap()
    fun tokens(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+" ).findAll(text.lowercase()).map { it.value }
        .filter { it.length > 1 && it !in stopWords }.flatMap { word -> sequenceOf(word, synonyms[word]).filterNotNull() }.toSet()
    fun score(query: String, text: String): Int {
        val terms = tokens(query)
        if (terms.isEmpty()) return 0
        return terms.intersect(tokens(text)).sumOf { if (it.startsWith("concept")) 2 else 5 }
    }
    fun select(query: String, records: List<PersistentMemoryRecord>, maxTokens: Int, now: Long = System.currentTimeMillis()): List<PersistentMemoryRecord> {
        var used = 0
        return records.filter { it.state == MemoryState.ACTIVE && it.consentStatus == ConsentStatus.EXPLICITLY_GRANTED &&
            (it.expiresAt == null || it.expiresAt > now) }
            .map { it to score(query, "${it.key} ${it.value} ${it.category}") }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<PersistentMemoryRecord, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .map { it.first }.filter { record ->
                val cost = ContextBudgetManager.estimateTokens("• ${record.key}: ${record.value}")
                if (used + cost <= maxTokens) { used += cost; true } else false
            }.take(6)
    }
}
