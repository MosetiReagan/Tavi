# Tavi — Phase 4: Persistent Memory Baseline Audit Report

**Document Date:** October 26, 2026  
**Auditor:** Systems Architecture & Core Engineering  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35), 4 GB RAM  
**Target Model:** Gemma 3 1B IT FlatBuffer (`gemma-3-1b-it.task`)  
**Repository Working Directory:** `/Users/reagan/Downloads/Tavi`  
**Document Path:** `/Users/reagan/Downloads/Tavi/TAVI_MEMORY_BASELINE_AUDIT.md`  

---

## 1. Executive Summary

This audit assesses the state of personal memory, state retention, and cross-session persistence in Tavi prior to the Phase 4 persistent memory implementation.

### Key Audit Findings:
1. **Chat History $\neq$ Persistent Memory:** Tavi previously stored all messages in SQLite table `messages`. While chat history survived app restarts, memory was **only retained within the scope of the active conversation messages**.
2. **Conversation Ledger Was Ephemeral:** The `ConversationLedger` introduced in Phase 3 built its state dynamically by iterating over `currentHistory: List<Message>`. When a user cleared the chat or if messages were trimmed past the token budget without being explicit facts, the ledger did not persist across independent conversations.
3. **Primitive `memory` Table Existed but Was Disconnected:** A simple SQLite table `memory (id, mem_key, mem_value, category, timestamp)` existed alongside tools `memory_save`, `memory_retrieve`, and `memory_delete`. However:
   - Memories were **never automatically retrieved or injected** into the model prompt during normal conversation.
   - Memories were only accessed if the model explicitly hallucinated or proposed a `memory_retrieve` tool call.
   - The table lacked provenance, confidence scores, sensitivity classifications, consent tracking, verification status, expiry dates, and lifecycle states (active, superseded, deleted).
4. **Episodic Recall Was Absent:** There was zero mechanism to track project decisions, historical approaches, task outcomes, or unfinished items across sessions.
5. **Privacy & Exclusion Controls Lacked Enforcement:** The primitive memory tools did not inspect or reject sensitive credentials, auth tokens, financial records, or medical details.

---

## 2. Storage & Component Inspection

### 2.1 Database Schema (`TaviDatabase.kt` - Version 3)
```sql
CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    tool_calls_json TEXT,
    tool_results_json TEXT
);

CREATE TABLE IF NOT EXISTS memory (
    id TEXT PRIMARY KEY,
    mem_key TEXT NOT NULL UNIQUE,
    mem_value TEXT NOT NULL,
    category TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS workflows (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    user_query TEXT NOT NULL,
    state TEXT NOT NULL,
    plan_source TEXT NOT NULL,
    current_step_index INTEGER NOT NULL,
    steps_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    ...
);
```

### 2.2 What Tavi Currently Remembers vs What It Forgets

| Information Type | Storage Mechanism | Survival Across Restarts? | Automatic Retrieval into Prompt? | Status |
|:---|:---|:---:|:---:|:---:|
| **Current Chat Messages** | `messages` table | Yes | Yes (up to 1,200 token budget) | Working |
| **In-Session Facts / Constraints** | `ConversationLedger` | Only if raw messages exist | Yes (via Ledger Header) | Session-scoped only |
| **Explicit `memory_save` Facts** | `memory` table | Yes | **NO (Requires tool call)** | Disconnected |
| **User Preferences across Chats** | None | **NO** | **NO** | Missing |
| **Projects & Ongoing Goals** | None | **NO** | **NO** | Missing |
| **Historical Decisions & Rationale**| None | **NO** | **NO** | Missing |
| **Task Episodes & Outcomes** | `workflows` table (raw JSON) | Yes | **NO** | Disconnected |
| **Sensitive Credential Exclusions**| None | N/A | N/A | Missing |

---

## 3. Baseline Failure Modes Identified

1. **Failure Mode 1: New Conversation Amnesia**
   - When a user starts a new conversation or clears the chat log, Tavi forgets their name, dietary restrictions, preferred programming languages, and project names.
2. **Failure Mode 2: No Fact Superseding / Stale Conflicts**
   - The existing `memory` table enforced `UNIQUE(mem_key)`. If a user updated a preference, it either failed or overwrote without preserving provenance, timestamp history, or superseding state.
3. **Failure Mode 3: Inability to Answer Episodic Questions**
   - When asked *"What did we decide about my project yesterday?"* or *"What was the approach we tried last time?"*, Tavi had no episodic memory index and replied with an honest unknown or hallucination.
4. **Failure Mode 4: Sensitive Data Risk**
   - An adversarial prompt or user slip could store credit card numbers, passwords, or OTPs into the plaintext `memory` table.

---

## 4. Phase 4 Objectives

1. Design and migrate `tavi_local.db` to Version 4, introducing:
   - `persistent_memories`: Structured records with provenance, sensitivity, verification status, and lifecycle states.
   - `episodic_memories`: Bounded summaries of decisions, completed workflows, and task outcomes.
2. Implement `PersistentMemoryManager`:
   - Conservative extraction for explicit commands.
   - Exact and token-indexed retrieval.
   - Deduplication and conflict reconciliation (superseding old records).
   - Sensitive keyword rejection (PINs, passwords, card numbers, OTPs).
3. Integrate with `ContextBudgetManager.kt`:
   - Dedicated memory tier ($\le 200$ tokens) injected as structured data into the user turn.
4. Provide explicit user controls:
   - Querying what is remembered.
   - Forgetting specific memories.
   - Bulk deletion with confirmation.
   - Setting toggle to enable/disable persistent memory.
