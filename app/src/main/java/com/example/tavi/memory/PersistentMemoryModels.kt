package com.example.tavi.memory

import java.util.UUID

enum class MemoryType {
    PREFERENCE,
    PROFILE_FACT,
    PROJECT_GOAL,
    FREQUENT_ENTITY,
    ROUTINE,
    DECISION_RATIONALE,
    SAVED_FACT,
    EPISODIC_SUMMARY
}

enum class MemoryProvenance {
    USER_EXPLICIT,
    USER_APPROVED_SUGGESTION,
    SYSTEM_INFERRED
}

enum class SensitivityLevel {
    NORMAL,
    SENSITIVE,
    STRICTLY_CONFIDENTIAL
}

enum class MemoryState {
    ACTIVE,
    SUPERSEDED,
    DELETED
}

enum class VerificationStatus {
    UNVERIFIED,
    CONFIRMED,
    USER_VALIDATED
}

enum class ConsentStatus {
    EXPLICITLY_GRANTED,
    PENDING_APPROVAL
}

data class PersistentMemoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val memoryType: MemoryType = MemoryType.SAVED_FACT,
    val key: String,
    val value: String,
    val category: String = "general",
    val provenance: MemoryProvenance = MemoryProvenance.USER_EXPLICIT,
    val confidence: Float = 1.0f,
    val verificationStatus: VerificationStatus = VerificationStatus.CONFIRMED,
    val consentStatus: ConsentStatus = ConsentStatus.EXPLICITLY_GRANTED,
    val sensitivity: SensitivityLevel = SensitivityLevel.NORMAL,
    val state: MemoryState = MemoryState.ACTIVE,
    val supersededById: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastConfirmedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null
)

data class EpisodicMemoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val summary: String,
    val decisionRationale: String? = null,
    val outcome: String? = null,
    val taskStatus: String = "COMPLETED",
    val sourceConversationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
)
