package com.example.tavi.context

import android.util.Log
import com.example.tavi.memory.EpisodicMemoryRecord
import com.example.tavi.memory.PersistentMemoryManager
import com.example.tavi.memory.PersistentMemoryRecord
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole

/**
 * Token-Aware Context & Chat Template Manager for Gemma 3 1B.
 *
 * Implements:
 * 1. Dynamic token budgeting within the 2,048-token hardware limit.
 * 2. Complete-exchange retention (never drops a single turn without its partner).
 * 3. Strict Gemma 3 user/model alternating turn formatting (no duplicate or consecutive roles).
 * 4. Grounded tool execution result reinsertion into the model context.
 * 5. Integration with [ConversationLedger] to ensure zero fact loss across turns.
 */
object ContextBudgetManager {

    private const val TAG = "ContextBudgetManager"
    const val MAX_CONTEXT_TOKENS = 2048
    const val RESERVED_GENERATION_TOKENS = 400
    const val MAX_SYSTEM_AND_LEDGER_TOKENS = 350
    const val AVAILABLE_HISTORY_TOKENS = MAX_CONTEXT_TOKENS - RESERVED_GENERATION_TOKENS - MAX_SYSTEM_AND_LEDGER_TOKENS // ~1,298 tokens

    /**
     * Fast character-to-token estimator calibrated for Gemma 3's vocabulary.
     * English natural text averages ~3.8 characters per token.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 3.8).toInt().coerceAtLeast(1)
    }

    /**
     * Builds a token-budgeted, strictly alternating Gemma 3 prompt from conversation history.
     */
    fun buildGemma3Prompt(
        history: List<Message>,
        currentPrompt: String,
        baseSystemInstruction: String,
        ledger: ConversationLedger? = null,
        persistentMemories: List<PersistentMemoryRecord>? = null,
        episodicMemories: List<EpisodicMemoryRecord>? = null
    ): String {
        // 1. Build authoritative ledger from full history
        val activeLedger = ledger ?: ConversationLedger().apply { buildFromHistory(history) }
        activeLedger.updateFromMessage(Message(role = MessageRole.USER, content = currentPrompt))

        // 2. Prepare system block with active ledger facts and persistent memories
        val ledgerHeader = activeLedger.formatLedgerHeader()
        val memoryHeader = if (!persistentMemories.isNullOrEmpty() || !episodicMemories.isNullOrEmpty()) {
            PersistentMemoryManager.formatMemoryContextBlock(
                persistentMemories ?: emptyList(),
                episodicMemories ?: emptyList()
            )
        } else {
            ""
        }
        val effectiveSystem = buildString {
            append(baseSystemInstruction.trim())
            if (memoryHeader.isNotBlank()) {
                append("\n").append(memoryHeader)
            }
            if (ledgerHeader.isNotBlank()) {
                append("\n").append(ledgerHeader)
            }
        }

        // 3. Filter history to remove any duplicate of current prompt at the tail
        val cleanedHistory = if (history.isNotEmpty() &&
            history.last().role == MessageRole.USER &&
            history.last().content.trim() == currentPrompt.trim()
        ) {
            history.dropLast(1)
        } else {
            history
        }

        // 4. Token-budgeted history selection (Complete Turn Pairs)
        val budgetedHistory = selectBudgetedTurns(cleanedHistory, currentPrompt, AVAILABLE_HISTORY_TOKENS)

        // 5. Serialize into strictly alternating Gemma 3 chat template
        val sb = StringBuilder()
        var systemPending = effectiveSystem.isNotBlank()

        // Helper to format model turn with tool grounding
        fun formatModelTurnContent(msg: Message): String {
            val toolCalls = msg.toolCalls ?: emptyList()
            val toolResults = msg.toolResults ?: emptyList()

            if (toolCalls.isEmpty() && toolResults.isEmpty()) {
                return msg.content.trim()
            }

            val partBuilder = StringBuilder()
            for (i in toolCalls.indices) {
                val call = toolCalls[i]
                val res = toolResults.getOrNull(i)
                partBuilder.append("[Tool Call: ").append(call.toolName).append("(")
                partBuilder.append(call.arguments.entries.joinToString(", ") { "${it.key}=${it.value}" })
                partBuilder.append(")]\n")
                if (res != null && res.isSuccess && res.resultJson.isNotBlank()) {
                    val rawJson = res.resultJson.trim()
                    // Bound individual tool result to prevent context exhaustion and sanitize adversarial turn tokens
                    val boundedJson = if (rawJson.length > 800) {
                        rawJson.take(800) + "... [truncated]"
                    } else {
                        rawJson
                    }
                    val sanitized = boundedJson
                        .replace("<start_of_turn>", "[start_of_turn]")
                        .replace("<end_of_turn>", "[end_of_turn]")
                    partBuilder.append("[Tool Result: ").append(sanitized).append("]\n")
                }
            }
            if (msg.content.isNotBlank()) {
                partBuilder.append(msg.content.trim())
            }
            return partBuilder.toString().trim()
        }

        // Ensure user/model alternation by grouping consecutive messages of identical roles
        val normalizedTurns = mutableListOf<Pair<MessageRole, String>>()
        for (msg in budgetedHistory) {
            if (msg.content.isBlank() && msg.toolCalls.isNullOrEmpty()) continue

            val turnContent = if (msg.role == MessageRole.ASSISTANT) {
                formatModelTurnContent(msg)
            } else {
                msg.content.trim()
            }

            if (normalizedTurns.isNotEmpty() && normalizedTurns.last().first == msg.role) {
                // Merge consecutive turns of identical role
                val (prevRole, prevContent) = normalizedTurns.removeAt(normalizedTurns.lastIndex)
                normalizedTurns.add(prevRole to "$prevContent\n\n$turnContent")
            } else {
                normalizedTurns.add(msg.role to turnContent)
            }
        }

        // Emit turns into Gemma template
        for ((role, content) in normalizedTurns) {
            when (role) {
                MessageRole.USER -> {
                    sb.append("<start_of_turn>user\n")
                    if (systemPending) {
                        sb.append(effectiveSystem).append("\n\n")
                        systemPending = false
                    }
                    sb.append(content).append("<end_of_turn>\n")
                }
                MessageRole.ASSISTANT -> {
                    if (sb.isNotEmpty()) { // Model turn cannot appear before first user turn
                        sb.append("<start_of_turn>model\n").append(content).append("<end_of_turn>\n")
                    }
                }
                else -> Unit
            }
        }

        // Append current user prompt turn
        sb.append("<start_of_turn>user\n")
        if (systemPending) {
            sb.append(effectiveSystem).append("\n\n")
            systemPending = false
        }
        sb.append(currentPrompt.trim()).append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")

        return sb.toString()
    }

    /**
     * Selects recent turns from history that fit within the token budget.
     * Preserves complete conversational pairs and ensures older turns are pruned cleanly.
     */
    private fun selectBudgetedTurns(
        history: List<Message>,
        currentPrompt: String,
        maxTokens: Int
    ): List<Message> {
        if (history.isEmpty()) return emptyList()

        var currentTokens = estimateTokens(currentPrompt)
        val selected = mutableListOf<Message>()

        // Scan backwards from newest to oldest
        for (i in history.indices.reversed()) {
            val msg = history[i]
            val msgTokens = estimateTokens(msg.content) + (msg.toolResults?.sumOf { estimateTokens(it.resultJson) } ?: 0)
            if (currentTokens + msgTokens > maxTokens && selected.isNotEmpty()) {
                Log.d(TAG, "Context budget reached ($currentTokens tokens). Pruned ${i + 1} older messages.")
                break
            }
            selected.add(0, msg)
            currentTokens += msgTokens
        }

        // If the first message in selected history is an ASSISTANT without preceding USER, drop it
        if (selected.isNotEmpty() && selected.first().role == MessageRole.ASSISTANT) {
            selected.removeAt(0)
        }

        return selected
    }
}
