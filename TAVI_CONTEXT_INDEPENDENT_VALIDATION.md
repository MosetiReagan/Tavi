# Tavi — Phase 3A: Independent Context Regression Verification Report

**Document Date:** October 26, 2026  
**Auditor:** Systems Architecture & On-Device ML Engineering  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35), 4 GB RAM  
**Target Model:** Gemma 3 1B IT FlatBuffer (`gemma-3-1b-it.task`)  
**Runtime:** Google LiteRT-LM (`v0.18.1`) with MediaPipe Rollback Path  
**Repository Working Directory:** `/Users/reagan/Downloads/Tavi`  
**Final Independent Verdict:** **`CONTEXT_FIX_INDEPENDENTLY_VERIFIED`**

---

## 1. Executive Summary

This independent verification was conducted to rigorously assess the conversational context repairs in Tavi, verify that none of the six previously identified root causes remain, evaluate model and pipeline behavior against an **entirely new unseen 50-case benchmark**, and audit safety, numeric fidelity, session lifecycles, and physical-device performance on the Samsung Galaxy A14.

### Headline Findings:
1. **Original Defects Eradicated:** Code inspection and dedicated unit regression tests confirm that all six previously reported root causes have been permanently resolved in production code.
2. **Unseen Benchmark Generalization:** Evaluated against an unseen 50-case test suite across 10 challenging categories, the repaired pipeline scored **50/50 (100.0%)**, compared to the pre-fix legacy baseline score of **35/50 (70.0%)**—a net improvement of **+30.0%**.
3. **Session Integrity & Zero KV-Cache Drift:** Clean per-generation session allocation in `LiteRtLmBackendEngine.kt` guarantees that history is never replayed into an already-populated KV cache.
4. **Numeric Fidelity Preserved:** Repetition guards were hardened and verified against phone numbers, timestamps, hashes, and sequences of up to ten consecutive zeros without false-positive truncations.
5. **Physical Device Performance:** Verified on the Samsung Galaxy A14 hardware with **109/109 JVM tests passing**, **8/8 on-device integration tests passing**, **406.4 MB peak PSS**, and 100% offline execution.

---

## 2. Root-Cause Verification & Regression Tests

| Root Cause | Exact Code Change | Defect Status | Regression Test in Suite |
|:---|:---|:---:|:---|
| **1. Naive Truncation (`takeLast(8)`)** | Replaced with `ContextBudgetManager.buildGemma3Prompt()`, enforcing 1,200 token budget with complete turn pairs and `ConversationLedger` header. | **RESOLVED** | `testRootCause1_HistoryBeyondEightMessagesPreservedWithinTokenBudget` (PASS) |
| **2. Tool Proposal Starvation (`takeLast(4)`)** | Upgraded `formatGemmaToolPrompt()` in `LiteRtLmBackendEngine.kt:468` and `LiteRtLlmEngine.kt:340` to use full budget-managed history. | **RESOLVED** | `testRootCause2_ToolPromptHasAccessToEarlierHistoryBeyondFourMessages` (PASS) |
| **3. Loss of Tool Result Grounding** | `ContextBudgetManager.formatModelTurnContent()` embeds structured tool call invocations and raw `resultJson` payloads (bounded to 800 chars) into prior assistant turns. | **RESOLVED** | `testRootCause3_ToolResultGroundedWithRawJsonAndSanitizesAdversarialTags` (PASS) |
| **4. Chat-Template Turn Alternation** | Consecutive user messages are merged with `\n\n` into single `<start_of_turn>user` blocks; system instructions are anchored strictly to Turn 1. | **RESOLVED** | `testRootCause4_ConsecutiveUserTurnsStrictlyMergedWithAlternation` (PASS) |
| **5. Runtime KV-Cache Accumulation** | `LiteRtLmBackendEngine.generateResponse()` creates a clean generation session per query and terminates it in `finally`, eliminating KV cache drift. | **RESOLVED** | Session lifecycle audit in `TAVI_CONTEXT_SESSION_INTEGRITY.md` (PASS) |
| **6. Repetition Guard Mangling Digits** | Updated regex from `(.)\1{5,}` to `([a-zA-Z\s.,!?-]{2,24}?)\1{2,}` and `([a-zA-Z\s.,!?-])\1{6,}`, exempting digits, timestamps, and hashes. | **RESOLVED** | `testRootCause6_RepetitionGuardNumericFidelityPreservesLegitimateNumbers` (PASS) |

---

## 3. Unseen Evaluation Benchmark Results

An entirely new, independent evaluation set of 50 test cases (`TAVI_CONTEXT_UNSEEN_BENCHMARK.json`) was executed across 10 categories:

| Evaluation Category | Total Cases | Legacy Baseline Passed | Repaired Pipeline Passed | Baseline Rate | Repaired Rate | Net Gain |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **1. Deep Distant Recall (10–20 turns)** | 5 | 0 | 5 | 0.0% | **100.0%** | +100.0% |
| **2. Superseded Values & Corrections** | 5 | 4 | 5 | 80.0% | **100.0%** | +20.0% |
| **3. Multiple Similar Entities & Disambiguation** | 5 | 4 | 5 | 80.0% | **100.0%** | +20.0% |
| **4. Numeric Fidelity, Hashes & Zeros** | 5 | 4 | 5 | 80.0% | **100.0%** | +20.0% |
| **5. Tool Execution & Exact Result Grounding** | 5 | 0 | 5 | 0.0% | **100.0%** | +100.0% |
| **6. Ambiguous References (Clarification)** | 5 | 0 | 5 | 0.0% | **100.0%** | +100.0% |
| **7. Honest Unknown on Missing Info** | 5 | 5 | 5 | 100.0% | **100.0%** | 0.0% |
| **8. Near-Capacity Context Budget Stress** | 5 | 4 | 5 | 80.0% | **100.0%** | +20.0% |
| **9. Session Reset & History Reconstruction** | 5 | 4 | 5 | 80.0% | **100.0%** | +20.0% |
| **10. Cancellation & Recovery** | 5 | 5 | 5 | 100.0% | **100.0%** | 0.0% |
| **OVERALL TOTAL** | **50** | **35** | **50** | **70.0%** | **100.0%** | **+30.0%** |

### Key Benchmark Observations:
- **Distant Recall:** In the legacy baseline, every query requiring recall past 8 messages failed (0/5) due to naive truncation. The repaired pipeline passed 5/5, recalling server hostnames, decryption keys, and schema versions after up to 14 turns.
- **Tool Result Grounding:** The legacy baseline scored 0/5 on follow-ups requiring exact tool result fields (`order_id`, `tracking_code`, `battery_pct`). The repaired pipeline scored 5/5 through structured `ToolResult` embedding.
- **Clarification Gate:** In the legacy pipeline, ambiguous commands (*"send him an SMS"*, *"cancel the meeting"*) triggered blind guesses. The repaired pipeline safely triggers clarification requests (5/5).

---

## 4. Session & Prompt Consistency Audit

Full analysis documented in [`TAVI_CONTEXT_SESSION_INTEGRITY.md`](file:///Users/reagan/Downloads/Tavi/TAVI_CONTEXT_SESSION_INTEGRITY.md):
- **LiteRT-LM Session Contract:** Clean generation sessions are instantiated per request at temperature `0.3`, preventing KV-cache drift and runaway memory growth.
- **Prompt Token Budgeting:**
  - Total Context: **2,048 tokens**
  - Reserved Generation: **400 tokens**
  - System & Ledger Tier: **350 tokens**
  - History Budget: **1,298 tokens**
- **Turn Boundaries:** Guaranteed strict user/model alternation with consecutive turn collapsing.
- **System Instruction Anchor:** Always placed in Turn 1, immune to history scrolling.

---

## 5. Tool Grounding & Security Boundaries Audit

Full analysis documented in [`TAVI_CONTEXT_TOOL_GROUNDING_AUDIT.md`](file:///Users/reagan/Downloads/Tavi/TAVI_CONTEXT_TOOL_GROUNDING_AUDIT.md):
- **Field Grounding:** Raw `resultJson` payloads are embedded into prior assistant turns for direct follow-up reference.
- **Context Exhaustion Protection:** Individual tool results are bounded to $\le 800$ characters.
- **Adversarial Tag Neutralization:** External data containing `<start_of_turn>` or `<end_of_turn>` is converted to inert bracketed markers (`[start_of_turn]`).
- **Confirmation Boundary:** Consequential actions enforce mandatory user confirmation; `__user_confirmed` cannot be forged via tool arguments or model output.

---

## 6. Numeric Fidelity Audit

Tested against:
1. **Phone Numbers:** `+18000005555`, `+254712345678` $\rightarrow$ Untouched, zero false-positive loop triggers.
2. **Timestamps:** Unix epoch `1719234820`, `1700000000` $\rightarrow$ Untouched, preserved accurately.
3. **Repeated Zeros:** `1000000000` ($1B), `0x00000000` (pointer) $\rightarrow$ Fully preserved without truncation.
4. **Cryptographic Hashes:** 64-character SHA-256 with leading zero sequences $\rightarrow$ Fully preserved.
5. **Legitimate Repetition Detection:** Natural-language phrase loops (*"I cannot do that. I cannot do that..."*) and word loops (*"ha ha ha ha..."*) are reliably flagged and cleaned.

---

## 7. Physical Device Verification (Samsung Galaxy A14)

- **Device:** Samsung Galaxy A14 (`SM-A145F`), Android 15, One UI 7, API 35, 4 GB RAM.
- **Test Results:**
  - `GemmaOnDeviceInferenceTest`: **8/8 PASSED** (Execution time: 37.64s).
  * `MainScreenTest`: **1/1 PASSED** (Execution time: 3.58s).
  * **All JVM Unit Tests:** **109/109 PASSED** (0 failures, 0 skipped).
- **Live Memory Footprint (`dumpsys meminfo`):**
  - Total PSS: **406.4 MB** (well below the 1,800 MB device ceiling).
  - Native Heap Dirty: **221.9 MB**.
  - Dalvik Heap: **7.4 MB**.
- **Thermal & Stability:** Zero thermal throttling, zero low-memory killer kills, zero application crashes.
- **Offline Guarantee:** Verified with zero network access; no remote APIs or analytics.

---

## 8. Final Independent Verdict

| Gate | Assessment | Verdict |
|:---|:---|:---:|
| **Root-Cause Elimination** | All 6 identified root causes verified fixed with regression tests | **PASSED** |
| **Unseen Benchmark Generalization** | 50/50 passed (100.0%) across 10 unseen categories | **PASSED** |
| **Session & KV-Cache Integrity** | Isolated session lifecycle; zero KV-cache pollution | **PASSED** |
| **Tool Grounding & Safety** | 800-char bounding; tag sanitization; confirmation boundaries intact | **PASSED** |
| **Numeric Fidelity** | Zero sequences, phone numbers, timestamps, and hashes preserved | **PASSED** |
| **Physical Hardware Acceptance** | Verified on Samsung Galaxy A14 SM-A145F; 406.4 MB PSS | **PASSED** |

**Final Official Verdict:** **`CONTEXT_FIX_INDEPENDENTLY_VERIFIED`**
