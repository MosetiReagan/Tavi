package com.example.tavi.roles.assistant

import com.example.tavi.security.SecurityGuard

data class AssistSnapshot(val source: String, val text: String, val capturedAt: Long = System.currentTimeMillis())

/** Invocation context stays in memory, expires quickly, and is consumed by one assistant turn. */
object AssistContextStore {
    @Volatile private var snapshot: AssistSnapshot? = null
    fun set(source: String, text: String) {
        snapshot = AssistSnapshot(source, SecurityGuard.sanitizeExternalContent(text.take(1800), "assist_screen"))
    }
    @Synchronized fun consume(now: Long = System.currentTimeMillis()): AssistSnapshot? {
        val current = snapshot
        snapshot = null
        return current?.takeIf { now - it.capturedAt in 0..120_000 }
    }
    fun clear() { snapshot = null }
}
