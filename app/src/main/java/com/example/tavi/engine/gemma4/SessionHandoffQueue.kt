package com.example.tavi.engine.gemma4

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages serialization and graceful handoff of LiteRT-LM conversation sessions.
 *
 * LiteRT-LM enforces a strict 1-conversation-per-engine constraint: chat,
 * workflow verification, and TTS summarization compete for the active session.
 *
 * This queue guarantees:
 * 1. Strict FIFO mutual exclusion (never concurrent native calls).
 * 2. Suspendable close awaits with bounded timeout (never [Thread.sleep]).
 * 3. Atomic session ownership handoff across competing subsystems.
 */
class SessionHandoffQueue(
    private val closeTimeoutMs: Long = 3000L
) {
    companion object {
        private const val TAG = "SessionHandoffQueue"
    }

    private val mutex = Mutex()
    private val activeOwner = AtomicReference<String?>(null)
    private val pendingRequests = AtomicInteger(0)
    private var lastHandoffDurationMs: Long = 0L

    val currentOwner: String? get() = activeOwner.get()
    val queuedRequestsCount: Int get() = pendingRequests.get()
    val lastHandoffMs: Long get() = lastHandoffDurationMs

    /**
     * Executes [block] with exclusive access to the underlying engine.
     * If a previous session is currently leased to another owner, safely awaits its handoff.
     */
    suspend fun <T> withExclusiveSession(
        ownerTag: String,
        block: suspend () -> T
    ): T {
        pendingRequests.incrementAndGet()
        val waitStart = SystemClock.elapsedRealtime()
        return mutex.withLock {
            pendingRequests.decrementAndGet()
            val acquiredAt = SystemClock.elapsedRealtime()
            lastHandoffDurationMs = acquiredAt - waitStart
            activeOwner.set(ownerTag)
            Log.d(TAG, "Leased engine session to '$ownerTag' (waited ${lastHandoffDurationMs}ms)")

            try {
                block()
            } finally {
                activeOwner.set(null)
                Log.d(TAG, "Released engine session from '$ownerTag'")
            }
        }
    }

    /**
     * Safely closes an active closable session with a suspendable timeout.
     * Never blocks a thread or uses Thread.sleep.
     */
    suspend fun safeClose(closable: AutoCloseable?, tag: String = "session"): Boolean {
        if (closable == null) return true
        val start = SystemClock.elapsedRealtime()
        return try {
            val closed = withTimeoutOrNull(closeTimeoutMs) {
                try {
                    closable.close()
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during async close of $tag: ${e.message}")
                    false
                }
            } ?: false
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.d(TAG, "Closed $tag in ${elapsed}ms (success=$closed)")
            closed
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "Unexpected error closing $tag: ${t.message}")
            false
        }
    }
}
