package com.example.tavi.engine

/**
 * Supported on-device inference runtimes for Tavi.
 */
enum class InferenceRuntime(val displayName: String, val description: String) {
    LITERTLM(
        displayName = "LiteRT-LM (Modern Google AI Edge)",
        description = "Google's modern lightweight on-device LLM runtime with optimized CPU/GPU execution."
    ),
    MEDIAPIPE(
        displayName = "MediaPipe Tasks GenAI (Legacy Rollback)",
        description = "Legacy MediaPipe Tasks GenAI runtime retained as a rollback path."
    ),
    DETERMINISTIC_ONLY(
        displayName = "Deterministic Rules Only",
        description = "Zero LLM inference. All tasks handled strictly through native deterministic rule engines."
    );

    companion object {
        fun fromString(value: String?): InferenceRuntime {
            return when (value?.uppercase()?.trim()) {
                "LITERTLM", "LITERT_LM", "LITERT" -> LITERTLM
                "MEDIAPIPE", "MEDIA_PIPE" -> MEDIAPIPE
                "DETERMINISTIC", "DETERMINISTIC_ONLY", "RULES_ONLY" -> DETERMINISTIC_ONLY
                else -> LITERTLM
            }
        }
    }
}
