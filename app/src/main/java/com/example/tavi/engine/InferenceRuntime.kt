package com.example.tavi.engine

/**
 * Supported on-device inference runtimes for Tavi.
 *
 * Provides honest tiers:
 * - [GEMMA_4_E2B]: Default recommended engine for devices with >= 6 GB RAM.
 * - [GEMMA_3_1B]: Low-memory fallback engine for budget devices (4 GB RAM).
 * - [LITERTLM]: Backwards-compatible alias entry for GEMMA_4_E2B.
 * - [DETERMINISTIC_ONLY]: Pure rules engine with zero LLM memory footprint.
 * - [MEDIAPIPE]: Legacy MediaPipe Tasks GenAI rollback runtime.
 */
enum class InferenceRuntime(val displayName: String, val description: String) {
    GEMMA_4_E2B(
        displayName = "Gemma 4 E2B (Recommended)",
        description = "Latest 2.58 GB Google reasoning model with native structured tool calling and thinking channel."
    ),
    GEMMA_3_1B(
        displayName = "Gemma 3 1B (Low memory)",
        description = "Optimized 1.1 GB model for budget devices (4 GB RAM) and fast execution."
    ),
    LITERTLM(
        displayName = "Gemma 4 E2B (Recommended)",
        description = "Google's modern lightweight on-device LLM runtime with optimized CPU/GPU execution."
    ),
    MEDIAPIPE(
        displayName = "MediaPipe Legacy",
        description = "Legacy MediaPipe Tasks GenAI runtime retained as a rollback path."
    ),
    DETERMINISTIC_ONLY(
        displayName = "Rules Only (No AI)",
        description = "Zero LLM inference. All tasks handled strictly through native deterministic rule engines."
    );

    companion object {
        fun fromString(value: String?): InferenceRuntime {
            return when (value?.uppercase()?.trim()) {
                "GEMMA_4_E2B", "GEMMA4", "E2B" -> GEMMA_4_E2B
                "GEMMA_3_1B", "GEMMA3", "3_1B", "1B" -> GEMMA_3_1B
                "LITERTLM", "LITERT_LM", "LITERT" -> LITERTLM
                "MEDIAPIPE", "MEDIA_PIPE" -> MEDIAPIPE
                "DETERMINISTIC", "DETERMINISTIC_ONLY", "RULES_ONLY" -> DETERMINISTIC_ONLY
                else -> GEMMA_4_E2B
            }
        }
    }
}
