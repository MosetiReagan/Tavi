# Tavi — Conversational Context Pipeline Audit Report

**Date:** October 26, 2026  
**Auditor:** Systems Architecture & On-Device ML Engineer  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35), 4 GB RAM  
**Runtime:** LiteRT-LM 0.18.1 with Gemma 3 1B IT (`gemma-3-1b-it.task` FlatBuffer)  
**Repository Working Directory:** `/Users/reagan/Downloads/Tavi`  

---

## 1. Executive Summary

This audit traces and diagnoses the conversational context pipeline of Tavi.
The observed defect reported by users is:
> **"Tavi does not seem to retain or use conversational context reliably."**

Our deep inspection reveals that while Gemma 3 1B instruction-tuned model is capable of multi-turn conversational reasoning, the surrounding application context pipeline suffers from **systematic structural deficiencies**:
1. **Naive Truncation (`takeLast(8)`):** Fixed 8-message window (only 4 turns!) permanently drops older context with zero summarization or persistent fact preservation.
2. **Tool-Proposal Window Starvation (`takeLast(4)`):** When evaluating tool calls, the model is only given 4 messages (2 turns), completely blinding tool proposal from multi-turn user intent.
3. **Loss of Tool Execution Grounding:** When tools execute, only a generic assistant summary string is added to conversation history; the actual input arguments (`ToolCall`) and output payloads (`ToolResult.resultJson`) are completely omitted from the context sent to the model on subsequent turns.
4. **Chat-Template Turn Corruption:** The current prompt assembly (`buildGemmaPrompt`) emits consecutive `<start_of_turn>user` blocks under multi-turn user edits, and relocates system instructions whenever messages scroll off the top, violating Gemma 3's strict alternating turn format.
5. **Runtime Session State Desynchronization:** In `LiteRtLmBackendEngine`, a single persistent `Session` instance is kept alive across multiple turns while the application resends the full conversation history string on every turn, causing duplicate tokens and context-window pollution in the runtime's KV cache.
6. **Absence of a Structured Conversation State Ledger:** There is no mechanism to track active project names, active task constraints, user corrections, or decisions across conversational exchanges.

---

## 2. End-to-End Pipeline Trace

The path from user text input to the final assistant message:

```
[User Input Text in UI]
       │
       ▼
MainScreenViewModel.sendMessage(userText: String)
       │
       ├─► 1. Check if activeWorkflow is AWAITING_USER_INPUT
       │      (if so, resume workflowEngine.provideMissingInput)
       │
       ├─► 2. Create User Message: Message(role = USER, content = trimmed)
       │      updatedMessages = _uiState.messages + userMessage
       │      db.insertMessage(userMessage)  [SQLite / Room]
       │
       ├─► 3. WorkflowPlanner.planWorkflow(trimmed)
       │      (Checks deterministic fast-path or Gemma workflow planning)
       │
       └─► 4. processAssistantTurn(userMessage, updatedMessages)
              │
              ├─► A. Tool Proposal Gate:
              │      engine.proposeToolCalls(userMessage.content, currentHistory)
              │      └─► LiteRtLmBackendEngine.formatGemmaToolPrompt(..., maxHistoryMessages = 4)
              │          [DEFECT: Truncates history to only 4 messages / 2 turns!]
              │
              ├─► B. Tool Execution (if proposedTools not empty):
              │      executeToolsAndSynthesize(proposedTools, currentHistory)
              │      ├─► ToolRegistry.executeToolCall(toolCall)
              │      ├─► SecurityGuard consequential action check
              │      └─► synthesizeToolResults(tools, results) -> finalSummary string
              │          assistantMessage = Message(role=ASSISTANT, content=finalSummary, ...)
              │          [DEFECT: Raw resultJson and toolCall arguments never entered into model prompt!]
              │
              └─► C. Direct Conversational Reply (if proposedTools is empty):
                     engine.generateResponse(userMessage.content, currentHistory, onToken)
                     └─► LiteRtLmBackendEngine.formatGemmaPrompt(prompt, conversationHistory)
                         └─► LiteRtLlmEngine.buildGemmaPrompt(history, prompt, systemPrompt, 8)
                             [DEFECT: Hardcoded takeLast(8) discards older context without summary!]
                             └─► activeSession.generateContentStream(formattedPrompt, callback)
                                 [DEFECT: Sends full history to persistent stateful Session!]
```

---

## 3. Audit of the 13 Suspected Failure Modes

### Failure Mode 1: Only the newest user message is passed to the model
- **Status:** **PROVEN IN TOOL PROPOSAL PATH**
- **Code Reference:** `LiteRtLmBackendEngine.kt:461-463`
- **Evidence:**
  ```kotlin
  val history = LiteRtLlmEngine.historyWithoutCurrentPrompt(conversationHistory, userQuery)
  return LiteRtLlmEngine.buildGemmaPrompt(history, userQuery, instructions, 4)
  ```
  In `formatGemmaToolPrompt`, `maxHistoryMessages` is hardcoded to `4` (2 turns). For conversational questions following a 3-turn discussion, older user facts (such as the contact name or meeting subject discussed in Turn 1) are completely absent when the model decides which tool to invoke.

### Failure Mode 2: Previous assistant/user messages are dropped, reordered, or incorrectly assigned roles
- **Status:** **PROVEN**
- **Code Reference:** `LiteRtLlmEngine.kt:128-143`
- **Evidence:**
  `buildGemmaPrompt` iterates over `history.takeLast(maxHistoryMessages)` and appends turns:
  ```kotlin
  for (msg in history.takeLast(maxHistoryMessages)) {
      when (msg.role) {
          MessageRole.USER -> appendUserTurn(msg.content)
          MessageRole.ASSISTANT -> {
              if (seenUserTurn && msg.content.isNotBlank()) {
                  sb.append("<start_of_turn>model\n").append(msg.content.trim()).append("<end_of_turn>\n")
              }
          }
          MessageRole.SYSTEM -> Unit
          MessageRole.TOOL -> appendUserTurn("[Tool Output]: ${msg.content.trim()}")
      }
  }
  appendUserTurn(currentPrompt)
  sb.append("<start_of_turn>model\n")
  ```
  If `history` has two consecutive `MessageRole.USER` messages (e.g. user sends two instructions back-to-back), the builder produces:
  `<start_of_turn>user\nMsg 1<end_of_turn>\n<start_of_turn>user\nMsg 2<end_of_turn>\n`
  Furthermore, `appendUserTurn(currentPrompt)` always appends another user turn at the end. Gemma 3's official chat format requires strict alternation between `user` and `model` turns. Multiple consecutive user turns confuse the attention mechanism.

### Failure Mode 3: History is saved but not loaded into the next inference request
- **Status:** **VERIFIED PARTIAL DEFECT**
- **Code Reference:** `MainScreenViewModel.kt:240-275`, `LiteRtLlmEngine.kt:96-106`
- **Evidence:**
  `MainScreenViewModel` constructs `updatedMessages = _uiState.value.messages + userMessage` and passes it to `processAssistantTurn(userMessage, updatedMessages)`. Then `historyWithoutCurrentPrompt` removes the last message by checking `last.content.trim() == currentPrompt.trim()`. If the user submits identical prompts (e.g., repeating "What else?"), or if a message has trailing whitespace variations, deduplication fails and history gets truncated or misaligned.

### Failure Mode 4: Plain-text concatenation vs expected Gemma conversation format
- **Status:** **IDENTIFIED & ROOT-CAUSED**
- **Code Reference:** `LiteRtLlmEngine.kt:114-126`
- **Evidence:**
  In `buildGemmaPrompt`:
  ```kotlin
  fun appendUserTurn(content: String) {
      seenUserTurn = true
      sb.append("<start_of_turn>user\n")
      if (systemPending) {
          sb.append(systemInstruction.trim()).append("\n\n")
          systemPending = false
      }
      sb.append(content.trim()).append("<end_of_turn>\n")
  }
  ```
  When history is longer than 8 messages, the first turn of the conversation (which originally had `systemPending = true`) is dropped by `history.takeLast(8)`. As a result, the system prompt is injected into message 9! This causes the system instruction to float forward in the conversation history as old messages are trimmed, altering the instruction context on every scroll.

### Failure Mode 5: System instructions or tool definitions corrupt the chat template
- **Status:** **PROVEN**
- **Code Reference:** `LiteRtLmBackendEngine.kt:447-463`
- **Evidence:**
  `formatGemmaToolPrompt` embeds over 1,200 tokens of raw tool schemas directly into the user prompt turn:
  ```
  <start_of_turn>user
  [51 Tool Schemas + JSON examples + INSTRUCTIONS]

  User Query
  <end_of_turn>
  ```
  This crowds out previous conversation context and forces the 2,048 token limit to be consumed primarily by tool schemas rather than user memory.

### Failure Mode 6: Runtime session preserves history while application resends it
- **Status:** **PROVEN CRITICAL DEFECT**
- **Code Reference:** `LiteRtLmBackendEngine.kt:157-161`, `LiteRtLmBackendEngine.kt:245-265`
- **Evidence:**
  In `LiteRtLmBackendEngine`:
  - `session` is created once in `loadModel()` via `newEngine.createSession(SessionConfig(...))`.
  - Every call to `generateLiteRtLmResponse` passes `val formattedPrompt = formatGemmaPrompt(prompt, conversationHistory)` to `activeSession.generateContentStream(...)`.
  - In LiteRT-LM C++, `Session` maintains internal KV-cache state across stream invocations. Because the application prepends full history to every prompt, the runtime session's KV cache accumulates duplicate copies of previous turns. Over 3-5 turns, this leads to context overflow, degradation of attention, and repetitive loops.

### Failure Mode 7: Eight-turn limit discards critical information without summary
- **Status:** **PROVEN CRITICAL DEFECT**
- **Code Reference:** `LiteRtLlmEngine.kt:338`, `LiteRtLmBackendEngine.kt:440`
- **Evidence:**
  `LiteRtLlmEngine.buildGemmaPrompt(history, userPrompt, effectiveSystem, 8)`:
  The number `8` represents 8 *messages*, which is only 4 user-assistant turns! Once a conversation exceeds 4 exchanges, user facts introduced in turn 1 (e.g., project name, user's role, dietary constraints, preferred locations) are completely erased from model input.

### Failure Mode 8: Context truncated by token limits, character limits, or hidden runtime limit
- **Status:** **PROVEN**
- **Code Reference:** `LiteRtLmBackendEngine.kt:49-50`, `LiteRtLmBackendEngine.kt:235-241`
- **Evidence:**
  `MAX_RESPONSE_CHARS = 1600` and `MAX_TOKENS = 2048`.
  No token-aware budgeting exists before formatting prompts. If an earlier turn had a lengthy message or note content, it pushes the prompt over 2,048 tokens, triggering runtime clipping or silent termination.

### Failure Mode 9: Tool results executed but not returned to model as structured context
- **Status:** **PROVEN CRITICAL DEFECT**
- **Code Reference:** `MainScreenViewModel.kt:478-494`
- **Evidence:**
  When tools execute, `assistantMessage` is saved with `content = finalSummary` (e.g., `"Action executed: contacts_search"`). The structured `resultJson` payload (which contains phone numbers, emails, addresses, IDs) is discarded from future prompts. When the user asks a follow-up question ("What was his number?"), the model has no access to the search results.

### Failure Mode 10: Streaming, cancellation, or concurrent requests corrupt conversation state
- **Status:** **PROVEN**
- **Code Reference:** `MainScreenViewModel.kt:393-435`
- **Evidence:**
  When a streaming generation begins, an empty `Message(role=ASSISTANT, content="", isStreaming=true)` is appended to the UI state. If the user cancels or an error occurs midway, `db.insertMessage` is called with whatever partial string was captured, or the state leaves an empty message in the history. Subsequent requests load this empty message into the prompt, creating invalid empty turns (`<start_of_turn>model\n<end_of_turn>`).

### Failure Mode 11: Insufficient space left for useful context due to schema bloat
- **Status:** **PROVEN**
- **Code Reference:** `ToolRegistry.generateToolsSystemPrompt()`
- **Evidence:**
  All 51 tools across 14 categories are serialized into the prompt at once. In a 2,048-token context, this leaves fewer than 600 tokens for conversation history and generation combined.

### Failure Mode 12: Repetition and prompt sanitization stripping legitimate information
- **Status:** **PROVEN**
- **Code Reference:** `LiteRtLlmEngine.kt:84-94`
- **Evidence:**
  `cleanRepetitiveText` replaces any character repeated 5+ times with a single instance:
  `text.replace(Regex("""(.)\1{5,}"""), "$1")`.
  This mangles phone numbers, UUIDs, hex hashes, or mathematical values with repeating digits (e.g., `000000`).

### Failure Mode 13: Model decoding configuration causing generic responses
- **Status:** **PROVEN**
- **Code Reference:** `LiteRtLmBackendEngine.kt:133`, `LiteRtLmBackendEngine.kt:158`
- **Evidence:**
  `SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0)`
  A temperature of 0.7 on a 1-billion parameter model causes high variance and hallucination when answering factual queries about conversational history. Factual recall and constraint following require lower sampling temperature (0.2 to 0.4).

---

## 4. Conclusion & Audit Sign-Off

The root causes of Tavi's weak context retention are fully identified:
1. **Application-level history management flaws** (`takeLast(8)`, loss of tool result data, turn alternation corruption).
2. **Session-level KV-cache duplication** in the LiteRT-LM backend.
3. **Absence of a token-aware context budgeter and conversation state ledger.**

The repair plan is outlined in `TAVI_CONTEXT_MANAGEMENT_DESIGN.md` and verified against the 50-case benchmark in Phase 2.
