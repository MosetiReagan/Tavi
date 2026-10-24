# Tavi — Phase 4: Persistent Memory & Local Knowledge System Design

**Document Date:** October 26, 2026  
**Auditor:** Systems Architecture & Core Engineering  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35), 4 GB RAM  
**Target Model:** Gemma 3 1B IT FlatBuffer (`gemma-3-1b-it.task`)  
**Repository Working Directory:** `/Users/reagan/Downloads/Tavi`  
**Document Path:** `/Users/reagan/Downloads/Tavi/TAVI_PERSISTENT_MEMORY_DESIGN.md`  

---

## 1. Architectural Architecture Overview

The Persistent Personal Memory system provides Tavi with continuous, cross-session personal recall operating **100% locally on-device**. It bridges the gap between ephemeral conversation context and long-term personal knowledge without requiring a cloud vector database or heavy secondary embedding model.

```
[User Request / Message]
       │
       ▼
1. Conservative Extraction & Update Gate
       │  ├─► Check Explicit Commands ("Remember that...", "Actually my X is Y", "Forget X")
       │  ├─► Sensitive Pattern Filter (Blocks passwords, PINs, card numbers, OTPs)
       │  └─► Deduplication & Superseding Engine (Marks conflicting old memories SUPERSEDED)
       │
       ▼
2. SQLite Local Store (`tavi_local.db`)
       │  ├─► Table: `persistent_memories` (Facts, Preferences, Entities, Goals)
       │  └─► Table: `episodic_memories` (Past decisions, project rationale, task outcomes)
       │
       ▼
3. Indexed Retrieval & Ranking Engine
       │  ├─► Token-based keyword matching + Category filtering
       │  ├─► Filter: Only ACTIVE, non-expired, user-approved records
       │  └─► Strict Token Budgeting (Max 200 tokens reserved)
       │
       ▼
4. Context Budget Manager Prompt Integration
       │  └─► Injected as DATA block in Turn 1:
       │      [USER PERSISTENT MEMORIES & FACT STORE]
       │      • preference: Dark roast coffee (USER_EXPLICIT)
       │      • past_decision: Selected SQLite for local storage (EPISODIC)
       ▼
[LiteRT-LM Gemma 3 1B Inference]
```

---

## 2. Database Schema Extension (Version 4 Migration)

To preserve existing user data, the schema migration is strictly **additive**:

```sql
-- Table: persistent_memories
CREATE TABLE IF NOT EXISTS persistent_memories (
    id TEXT PRIMARY KEY,
    memory_type TEXT NOT NULL,         -- PREFERENCE, PROFILE_FACT, PROJECT_GOAL, FREQUENT_ENTITY, ROUTINE, SAVED_FACT
    mem_key TEXT NOT NULL,             -- Normalized identifier (e.g., 'editor_preference', 'car_model')
    mem_value TEXT NOT NULL,           -- Concise factual content (e.g., 'Neovim', 'Subaru Outback')
    category TEXT NOT NULL,            -- 'personal', 'work', 'hardware', 'travel', etc.
    provenance TEXT NOT NULL,          -- USER_EXPLICIT, USER_APPROVED_SUGGESTION, SYSTEM_INFERRED
    confidence REAL NOT NULL DEFAULT 1.0,
    verification_status TEXT NOT NULL, -- UNVERIFIED, CONFIRMED, USER_VALIDATED
    consent_status TEXT NOT NULL,      -- EXPLICITLY_GRANTED, PENDING_APPROVAL
    sensitivity TEXT NOT NULL,         -- NORMAL, SENSITIVE, STRICTLY_CONFIDENTIAL
    state TEXT NOT NULL,               -- ACTIVE, SUPERSEDED, DELETED
    superseded_by_id TEXT,             -- ID of memory that superseded this one
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_confirmed_at INTEGER NOT NULL,
    expires_at INTEGER,                -- Optional timestamp for time-limited facts
    source_conversation_id TEXT,
    source_message_id TEXT
);

CREATE INDEX IF NOT EXISTS idx_mem_key_state ON persistent_memories(mem_key, state);
CREATE INDEX IF NOT EXISTS idx_mem_type_state ON persistent_memories(memory_type, state);

-- Table: episodic_memories
CREATE TABLE IF NOT EXISTS episodic_memories (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,               -- Short title (e.g. 'Database Architecture Decision')
    summary TEXT NOT NULL,             -- Bounded factual summary of event/decision
    decision_rationale TEXT,          -- Why the approach was chosen
    outcome TEXT,                      -- Outcome or current state
    task_status TEXT NOT NULL,         -- COMPLETED, IN_PROGRESS, ABANDONED
    source_conversation_id TEXT,
    created_at INTEGER NOT NULL,
    tags_json TEXT NOT NULL DEFAULT '[]'
);

CREATE INDEX IF NOT EXISTS idx_episode_time ON episodic_memories(created_at DESC);
```

---

## 3. Data Models (`PersistentMemoryRecord.kt`)

### 3.1 Enumerations
- **`MemoryType`:** `PREFERENCE`, `PROFILE_FACT`, `PROJECT_GOAL`, `FREQUENT_ENTITY`, `ROUTINE`, `DECISION_RATIONALE`, `SAVED_FACT`, `EPISODIC_SUMMARY`.
- **`MemoryProvenance`:** `USER_EXPLICIT` (user typed "remember that"), `USER_APPROVED_SUGGESTION` (Tavi suggested and user clicked yes), `SYSTEM_INFERRED` (derived from workflow outcome).
- **`SensitivityLevel`:** `NORMAL` (safe for prompt inclusion), `SENSITIVE` (requires matching context query), `STRICTLY_CONFIDENTIAL` (never auto-injected).
- **`MemoryState`:** `ACTIVE` (current valid fact), `SUPERSEDED` (overwritten by newer fact), `DELETED` (soft-deleted).

---

## 4. Conservative Memory Extraction Pipeline

### 4.1 Trigger Rules (Zero Hallucination)
1. **Explicit Memory Save:**
   - Patterns: `(?i)\b(?:please\s+)?remember\s+(?:that\s+)?(.+)`
   - `(?i)\b(?:my|our)\s+([a-zA-Z0-9_\s]{2,20})\s+(?:is|are|was)\s+(.+)`
   - Normalized into `key` and `value`.
2. **Explicit Memory Correction / Superseding:**
   - Patterns: `(?i)\b(?:actually|correction|change\s+that),\s+(?:my|our)?\s*([a-zA-Z0-9_\s]{2,20})\s+(?:is|to)\s+(.+)`
   - Finds existing active record with matching key $\to$ sets state to `SUPERSEDED`, sets `superseded_by_id = newRecord.id` $\to$ inserts new record as `ACTIVE`.
3. **Explicit Memory Delete / Forget:**
   - Patterns: `(?i)\b(?:forget|delete\s+memory|remove\s+fact)\s+(?:about\s+)?(.+)`
   - Marks matching active records as `DELETED`.

### 4.2 Sensitive Keyword Exclusion (The Firewall)
Before any memory record is saved, it passes through `SecurityGuard.isSensitiveData(text)`:
- Passwords (`password`, `passcode`, `secret_key`, `credentials`)
- One-Time Codes (`otp`, `verification code`, `pin number`, `2fa`)
- Financial Accounts (`credit card`, `cvv`, `bank account`, `debit card`)
- If sensitive patterns match: **REJECTED IMMEDIATELY** with error: `"For your security, Tavi does not store credentials, PINs, or financial secrets in persistent memory."`

---

## 5. Token-Budgeted Retrieval & Prompt Integration

### 5.1 Retrieval Strategy
- When user enters query $Q$:
  1. Extract query keywords (excluding stopwords).
  2. Query `persistent_memories` WHERE `state = 'ACTIVE'` AND `(expires_at IS NULL OR expires_at > now)`.
  3. Score records by:
     $$\text{Score} = (\text{KeywordMatch} \times 3.0) + (\text{IsExplicit} \times 2.0) + (\text{RecencyWeight} \times 1.0)$$
  4. Select top $K$ records (max 5) that fit within the **200-token memory budget**.
- Episodic queries (e.g. *"What did we decide about X?"*):
  - Query `episodic_memories` matching topic keywords $\to$ select top 2 most recent relevant episodes.

### 5.2 Prompt Delimitation Format
Injected under the first user turn in `ContextBudgetManager.kt`:
```
<start_of_turn>user
[SYSTEM INSTRUCTIONS]

[PERSISTENT USER MEMORIES & RELEVANT FACTS]
• project_goal: Release candidate on Samsung Galaxy A14 (source: user explicit)
• editor_preference: Neovim (source: user explicit)
• past_decision: Selected SQLite for local storage on 2026-10-26 (source: episodic)

User query<end_of_turn>
<start_of_turn>model
```
**Security Invariant:** Memory items are formatted strictly as bulleted factual statements under `[PERSISTENT USER MEMORIES & RELEVANT FACTS]`. Delimiter tokens (`<start_of_turn>`, `<end_of_turn>`) are sanitized. Memories are never interpreted as instructions.
