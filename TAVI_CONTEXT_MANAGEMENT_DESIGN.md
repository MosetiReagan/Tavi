# Tavi — Context Management & Conversation Continuity Design

**Date:** October 26, 2026  
**Author:** Systems & On-Device ML Architecture  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35), 4 GB RAM  
**Target Model:** Gemma 3 1B IT (LiteRT-LM 0.18.1)  
**Document Path:** `/Users/reagan/Downloads/Tavi/TAVI_CONTEXT_MANAGEMENT_DESIGN.md`  

---

## 1. Architectural Overview & Context Strategy

To overcome the weak conversational context observed on the device, Tavi's context pipeline transitions from an unbudgeted, naive message window (`takeLast(8)`) to a **Structured 3-Tier Context Architecture**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        2,048 Token Context Budget                      │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 1: Core System Directives & Conversation State Ledger (~350 tok)  │
│ - Identity, role, safety boundaries                                    │
│ - Extracted Explicit Facts (User details, project names, tech choices) │
│ - Active Constraints & Requirements (Budgets, platforms, rules)        │
│ - Recent Corrections (Overrides superseding outdated values)           │
│ - Recent Grounded Tool Execution Summaries (IDs, phone numbers, times) │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 2: Recent Alternating Conversational History (~1,200 tok)         │
│ - Complete User / Model turn pairs                                     │
│ - Tool Call invocations & raw structured payloads                      │
│ - Guaranteed strict turn alternation (no consecutive user blocks)      │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 3: Generation & Safety Reserve (~498 tok)                         │
│ - Reserved output token space for model generation (up to 400 tokens)  │
│ - Safety buffer against context overflow clipping                      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Dynamic Token-Budgeted Truncation Policy

### 2.1 Token Estimation Mechanics
On-device tokenization is fast-pathed using character-to-token ratio modeling calibrated for Gemma 3's vocabulary:
- **English / Natural Language:** $1.0\text{ token} \approx 3.8\text{ characters}$.
- **Code / JSON / Tool Schemas:** $1.0\text{ token} \approx 3.2\text{ characters}$.

### 2.2 Complete-Exchange Retention Rule
When context exceeds the 1,200-token conversational history budget:
1. Messages are pruned from the oldest turn forward.
2. Pruning **never separates** a `MessageRole.USER` turn from its corresponding `MessageRole.ASSISTANT` turn or its associated tool calls/results.
3. If an older turn contained an explicit user fact (e.g., *"My project is Atlas"*), that fact is extracted and preserved in the **Conversation State Ledger** before the raw message is pruned.
4. Truncation emits a diagnostic log message (`"Trimmed 2 older turns; preserved 4 active facts in ledger"`) without logging private message text.

---

## 3. Structured Conversation State Ledger

### 3.1 Data Model
```kotlin
data class ConversationLedger(
    val explicitFacts: MutableMap<String, String> = mutableMapOf(),
    val activeConstraints: MutableList<String> = mutableListOf(),
    val corrections: MutableMap<String, String> = mutableMapOf(),
    val recentToolSummaries: MutableList<String> = mutableListOf()
)
```

### 3.2 Deterministic Fact & Correction Extraction
The ledger employs deterministic syntactic patterns to record and update facts without requiring heavy background LLM calls:
1. **Explicit Fact Declarations:**
   - `"My project is (called|named)? (.+)"` $\rightarrow$ `facts["project"] = "$2"`
   - `"I use (.+) as my primary (.+)"` $\rightarrow$ `facts["primary_$2"] = "$1"`
   - `"Target (IP|server|database) is (.+)"` $\rightarrow$ `facts["target_$1"] = "$2"`
   - `"My (cat|dog)'s name is (.+)"` $\rightarrow$ `facts["pet"] = "$2 ($1)"`
2. **Corrections & Overrides:**
   - `"(Actually|Correction|Wait, not .*,|Change that to) (.+)"`
   - When a correction matches an existing fact key (e.g. `meeting_time`, `project_name`, `budget`), the old value is overwritten and marked as `[UPDATED]`.
3. **Tool Result Extraction:**
   - Tool `contacts_search` $\rightarrow$ records contact name, phone number, email.
   - Tool `calendar_create_event` $\rightarrow$ records event title, start time, event ID.
   - Tool `device_get_battery` $\rightarrow$ records percentage, charging state.

### 3.3 Prompt Ingestion
The ledger is formatted into a compact markdown header inside the first user turn:
```
<start_of_turn>user
You are Tavi, an on-device personal AI assistant for Android.

[ACTIVE CONVERSATION CONTEXT & EXPLICIT FACTS]
• project: Artemis (updated from Apollo)
• budget: $1,200 (updated from $500)
• primary_editor: Neovim
• Recent Tool Result: contacts_search -> Dennis Mokaya (+254712345678)

User Message
<end_of_turn>
```

---

## 4. Gemma 3 Chat Template & Strict Turn Alternation

Gemma 3 1B instruction-tuned models require strict alternation:
1. **Never emit consecutive identical roles:**
   If multiple user messages exist consecutively, merge their text into a single turn:
   ```
   <start_of_turn>user
   First instruction.

   Follow-up instruction.
   <end_of_turn>
   ```
2. **Tool Interaction Turn Formatting:**
   When an assistant turn executed a tool, the model turn contains both the tool invocation and the grounded execution data:
   ```
   <start_of_turn>model
   [Tool Call: contacts_search({"name": "Dennis"})]
   [Tool Result: {"name": "Dennis Mokaya", "phone": "+254712345678"}]
   Found Dennis Mokaya in your contacts.
   <end_of_turn>
   ```
   This ensures follow-up turns ("What is his phone number?") have direct semantic access to the tool payload.

---

## 5. LiteRT-LM Runtime Session Management

1. **Session Lifecycle Policy:**
   - The compiled `Engine` remains loaded and warm in memory.
   - To guarantee zero KV-cache token duplication, each inference request creates a dedicated session from the engine:
     ```kotlin
     val session = engine.createSession(SessionConfig(samplerConfig = ...))
     ```
   - When inference completes, `session.close()` releases the session's KV buffer immediately.
2. **Decoding Parameters Calibration:**
   - Factual conversation and tool synthesis: `temperature = 0.3`, `topK = 40`, `topP = 0.95`.
   - Low temperature eliminates hallucinations while preserving natural phrasing.

---

## 6. Safety, Tool Integrity & Privacy Invariants

1. **No External Transmission:** All ledger state and messages remain exclusively in memory and local SQLite (`tavi_local.db`).
2. **Untrusted Data Boundaries:** Tool results and external webpage contents are strictly tagged as data and cannot override system policy.
3. **Consequential Action Gates:** `messages_send_sms`, `telecom_place_call`, and `calendar_delete_event` continue to strictly require user confirmation dialogs.
4. **Stripping Injected Confirmation:** The parser strips any hallucinated or injected `__user_confirmed` parameters.
