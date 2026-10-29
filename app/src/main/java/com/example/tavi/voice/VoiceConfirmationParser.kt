package com.example.tavi.voice

enum class VoiceConfirmationDecision {
    AFFIRMATIVE,
    NEGATIVE,
    AMBIGUOUS
}

/**
 * Robust intent parser for hands-free confirmation of consequential tool executions.
 */
object VoiceConfirmationParser {

    private val AFFIRMATIVE_TERMS = setOf(
        "yes", "yeah", "yep", "sure", "do it", "confirm", "confirmed",
        "go ahead", "proceed", "okay", "ok", "affirmative", "absolutely",
        "please do", "yup", "definitely", "send it", "run it"
    )

    private val NEGATIVE_TERMS = setOf(
        "no", "nope", "stop", "cancel", "don't", "dont", "never mind",
        "nevermind", "abort", "halt", "dismiss", "do not", "negative",
        "don't do it", "dont do it"
    )

    fun parse(input: String?): VoiceConfirmationDecision {
        if (input.isNullOrBlank()) return VoiceConfirmationDecision.AMBIGUOUS

        val normalized = input.trim().lowercase()
            .replace(Regex("""[^\w\s']"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // 1. Direct or substring checks
        for (term in AFFIRMATIVE_TERMS) {
            if (normalized == term ||
                normalized.startsWith("$term ") ||
                normalized.endsWith(" $term") ||
                normalized.contains(" $term ")
            ) {
                return VoiceConfirmationDecision.AFFIRMATIVE
            }
        }

        for (term in NEGATIVE_TERMS) {
            if (normalized == term ||
                normalized.startsWith("$term ") ||
                normalized.endsWith(" $term") ||
                normalized.contains(" $term ")
            ) {
                return VoiceConfirmationDecision.NEGATIVE
            }
        }

        return VoiceConfirmationDecision.AMBIGUOUS
    }
}
