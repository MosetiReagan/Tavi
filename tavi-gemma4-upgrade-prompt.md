# TAVI UPGRADE DIRECTIVE — Gemma 4 E2B + Voice Loop + Screen Agent + v1.0 Release

You are working on **Tavi** (https://github.com/MosetiReagan/Tavi), an open-source, 100% on-device Android AI assistant written in Kotlin/Jetpack Compose, currently running Gemma 3 1B via LiteRT-LM with 53 native tools, SQLite memory, workflow engine, and honest execution reporting (confirmation gates, audit log, no fabricated results — this is a hard project value).

Your mission: execute the 6 workstreams below **in order**, keeping the existing test suite green at every step. Do not fabricate benchmark numbers, do not claim capabilities that are not wired end-to-end, and do not regress honest status reporting (e.g., never report "SENT_AND_VERIFIED" without a delivery receipt).

## PROJECT CONTEXT (verified as of commit 0d27c7c)

- Stack: AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01, minSdk 24, targetSdk 36, R8 enabled, CI at `.github/workflows/ci.yml` (lint + testDebugUnitTest + assembleDebug).
- Engine layer: `app/src/main/java/com/example/tavi/engine/` — `InferenceEngine` interface; `LiteRtLlmEngine` (facade, runtime dispatch); `LiteRtLmBackendEngine` (com.google.ai.edge.litertlm SDK: Engine/Session/EngineConfig, GPU→CPU fallback); `MediaPipeGenAiBackendEngine` (tasks-genai `com.google.mediapipe:tasks-genai:0.10.27`, hardcoded in `app/build.gradle.kts` — move to `gradle/libs.versions.toml` while you're here); `DeterministicAssistantEngine` (rule fallback).
- Tools: `tools/ToolRegistry.kt` — singleton via `ToolRegistry.getInstance(context)`; every tool implements `AssistantTool` (JSON schema, `requiredPermissions`, `requiresConfirmation`, risk level). 53+ registered tools across 15 categories.
- Model: `engine/ModelManager.kt` — download (non-resumable), import, SHA-256 verify, `isLowRamDevice` + `ramRequiredMb = 2048` guard. Current model: `gemma-3-1b-it.litertlm` (~1.1 GB).
- Voice: `voice/VoiceManager.kt` — SpeechRecognizer with partial results, TTS with rate + auto-read toggle in Settings.
- Assistant role: `roles/assistant/TaviVoiceInteractionSession.kt` launches `MainActivity` with `EXTRA_FROM_ASSISTANT_INVOCATION` (read in `MainActivity`); `TaviRecognitionService.kt` is an empty stub.
- Workflows: `workflow/WorkflowPlanner.kt` (rule/regex planner), `WorkflowEngine.kt`, `WorkflowStepVerifier.kt`.
- UI: `ui/main/MainScreen.kt` (958 lines) + `ui/main/components/` (ModernMessageBubble, WorkflowReviewCard, HeroEmptyState, ModernInputDock). **Delete the orphaned `ui/main/components/ChatInputBar.kt` (261 lines, never wired) before starting.**
- Data: `data/TaviDatabase.kt` (SQLiteOpenHelper, `ensureAllTables` on DDL paths only). DI: none — singletons only.
- Tests: 141 (`app/src/test/` 132 unit, `app/src/androidTest/` 9 device). No mocking framework.

## KNOWN LITERT-LM CONSTRAINTS (design around these — do not discover them the hard way)

1. **One Conversation per Engine.** Chat, workflow verification, and TTS summarization all compete for the session. You must build an engine/session handoff (close → recreate, or a serialized session queue with async-close awaits). Never `Thread.sleep()` to paper over session release — if close is async, wrap it in a suspendable await with timeout.
2. **Cold init on CPU-only budget phones can take ~45 s.** Show progressive loading UI (phase callbacks from the engine), never a bare indeterminate banner.
3. **Gemma 4 E2B LiteRT weights: ~2.58 GB**, download from Google's official Hugging Face (litert community). CPU decode ~33–47 tok/s on flagships, GPU decode ~52 tok/s. Budget devices (4 GB RAM) cannot hold E2B comfortably — keep Gemma 3 1B as the low-RAM default.
4. **Native tool calling** is exposed via the SDK's ToolSet API (annotation-based `@Tool` methods) and/or structured-output JSON. Tavi's tools are runtime-registered, not compile-time — you must build a bridge (W1).

## NON-NEGOTIABLE RULES

- Every user-visible string goes through `res/values/strings.xml` (currently 60 strings — keep extending it; no hardcoded literals in Compose).
- No `runBlocking`, no `GlobalScope`, no empty catch blocks (project is at 0 — keep it there).
- Touch targets ≥ 48dp. Colors only from the theme. No new `Color(0x…)` literals.
- Every new tool/feature ships with: permission gating, confirmation gate where consequential, audit-log entry, and at least 1 unit test. Target: ≥ 155 tests by the end.
- Update README **only** with numbers you measured. Untested = labeled as such.

---

## W1 — GEMMA 4 E2B MIGRATION (do first, everything depends on it)

**Goal:** E2B becomes the default engine on ≥ 6 GB RAM devices; Gemma 3 1B remains the low-RAM and rollback option; native structured tool calling replaces regex-prompt parsing for the LLM path.

Tasks:
1. Upgrade the LiteRT-LM SDK dependency to the latest release supporting Gemma 4 (check google-ai-edge/litert-lm releases; move the version into `gradle/libs.versions.toml`).
2. Create `engine/gemma4/ToolSetBridge.kt`: adapts `ToolRegistry`'s 53 runtime tools into the SDK's tool-calling interface.
   - If the SDK supports dynamic tool registration with JSON schemas: feed each `AssistantTool`'s schema directly.
   - If it is annotation-only: generate a bridge object exposing all 53 tools as `@Tool` methods with the exact JSON-schema signatures from `AssistantTool`, delegating execution to `ToolRegistry.executeToolCall(...)`. Keep `requiresConfirmation` tools routed through the existing human-confirmation gate — native calling must NEVER bypass confirmation.
3. Create `engine/Gemma4BackendEngine.kt` implementing `InferenceEngine`:
   - Reuse the GPU→CPU fallback pattern from `LiteRtLmBackendEngine`.
   - Support the thinking channel; expose it as a separate stream the UI can render as a collapsible "thinking" section in `ModernMessageBubble` (default collapsed).
   - Implement the session handoff/queue from the constraints section; unit-test it.
4. Update `ModelManager`: model catalog with two entries — `gemma-4-e2b` (2583 MB, ramRequiredMb 6144) and `gemma-3-1b` (existing). Default selection: `isLowRamDevice || totalMem < 6GB → 3-1B else 4-E2B`. Add resumable download (HTTP Range) and free-space check (existing TODO from earlier review if not done).
5. UI: Settings runtime picker gains "Gemma 4 E2B (Recommended)" / "Gemma 3 1B (Low memory)" / "Rules Only (No AI)" — keep the existing human-language copy style. Show expected download size and first-load time warning (~45 s on CPU) in the download card.
6. **Fallback chain must remain:** E2B → 3-1B (if present) → DeterministicAssistantEngine. Never leave the user in a dead app because a 2.58 GB model failed.
7. Tests: ToolSetBridge schema round-trip, session handoff, model selection logic, fallback chain. Real inference tests stay in androidTest behind the existing device-guard pattern.

**Acceptance:** on a 6 GB+ device, E2B loads, streams tokens, executes ≥ 5 representative tools (alarm, SMS draft+send with confirmation, calendar create, reminder create, flashlight) end-to-end; on a 4 GB device the app auto-selects 3-1B and still works.

## W2 — REAL DEVICE BENCHMARK HARNESS (replace scenario specs with measurements)

**Goal:** one command produces honest JSON numbers per device; README links to real results only.

Tasks:
1. Create `app/src/androidTest/benchmark/DeviceBenchmark.kt` (instrumented, `@RequiresDevice`):
   - Measure: cold engine init ms, TTFT ms (first token), decode tok/s over a 200-token generation, peak PSS via `Debug.MemoryInfo`, battery level delta via `BatteryManager`, thermal status before/after.
   - Run the suite for: short reply, tool-call round-trip (alarm_create), 500-token generation. 3 runs each, report median + min/max.
   - Output JSON to external storage; name: `benchmark_<device>_<model>_<date>.json`.
2. Add `scripts/run_benchmark.sh` (pattern-match `scripts/run_device_acceptance.sh`): adb checks (battery > 50%, not thermal-throttled), install, run instrumentation, pull JSON.
3. Update `evaluation/`: keep the scenario-spec files but relabel their headers "SPEC — see measured results in benchmarks/"; add `benchmarks/` dir; commit your real Galaxy-device JSON when you have it. Delete any remaining fabricated numbers anywhere in the repo (`rg "0\.98|342 ms|118 ms|14\.8"` should return zero hits outside specs).
4. README: replace the performance section with a small table driven by the measured JSON, explicitly noting device, model, thermal state, and date. If no device run yet, show "not yet measured" — do not extrapolate.

**Acceptance:** `./scripts/run_benchmark.sh` on a physical device produces committed JSON; README numbers match it; CI stays green.

## W3 — CONTINUOUS VOICE LOOP (speech → act → speak → listen again)

**Goal:** a genuine hands-free mode within the app; assistant-role invocation opens directly into it.

Tasks:
1. Extend `VoiceManager`: add a `VoiceSession` mode — after TTS finishes, auto-reopen the recognizer for a follow-up window (configurable 3–8 s, visualized as a listening pill in `ModernInputDock`); implement barge-in (speech detected → `TextToSpeech.stop()` immediately).
2. Handle the loop cleanly: each turn flows through the same ViewModel path as typed input (tool gates and confirmations still apply — confirmation prompts must be spoken as a question AND shown; voice "yes, send" confirms via the existing `__user_confirmed` path).
3. End conditions: silence timeout, user says "stop/that's all", power button. Add a visible "Hands-free: ON" state so users know the mic is open.
4. Assistant-role: `TaviVoiceInteractionSession.onShow` → launch MainActivity straight into a hands-free session (pass a new intent extra, e.g. `EXTRA_START_HANDSFREE`), read it in `MainActivity`, auto-open mic. Implement or delete the empty `TaviRecognitionService` — an empty default-assistant recognition service is a user-facing bug.
5. Settings: hands-free toggle (default off), follow-up window length, and the existing auto-read + rate controls grouped under "Voice & Speech".
6. Tests: VoiceSession state machine (listen→recognize→execute→speak→listen), barge-in interruption, confirmation-over-voice path (unit test with a fake recognizer).

**Acceptance:** on-device demo: "Set an alarm for 7" → spoken confirmation question → "yes" → spoken "Done, alarm set for 7:00" → listening again; all without touching the screen after the first tap.

## W4 — SCREEN AGENT (Accessibility API, complementary to the 53 tools)

**Goal:** an opt-in "agent mode" that can operate third-party apps by reading the accessibility tree — the capability PokeClaw proved, done with Tavi's safety discipline.

Tasks:
1. New package `agent/`: `ScreenAgentService` (AccessibilityService), `AccessibilityTreeReader` (compact node serialization — id/text/contentDesc/bounds/class, depth-limited), `AgentActionSpace` (tap, long-press, swipe, type, back, open_app, scroll).
2. `AgentExecutor`: per step → read tree → ask the engine to select ONE action (structured output) → execute via `AccessibilityService` → verify via tree diff (`WorkflowStepVerifier` pattern) → loop. Hard limits: max 12 steps/task, per-task budget, and **kill switch** (notification action + floating stop chip).
3. Safety: agent tasks always start from an explicit user prompt; consequential actions inside the flow (send, delete, pay) route through the existing confirmation gate; full audit log per step (tree hash + action + result); agent mode is a Settings toggle, OFF by default, with an honest first-run explanation of what Accessibility access means.
4. Integration: expose as tool `screen_agent_run` (requiresConfirmation = true) so workflows can include agent steps; the workflow card UI (`WorkflowReviewCard`) renders agent steps with the same progress/verification styling.
5. Tests: tree serializer (fixture trees), action parser (valid/invalid/ambiguous LLM output), step-budget enforcement, confirmation interception.

**Acceptance:** on-device: "Open Settings and turn on dark theme" executes via agent steps with visible per-step progress and an audit trail; 12-step cap enforced; kill switch works.

## W5 — RELEASE ENGINEERING v1.0

**Goal:** signed, tagged, reproducible releases; Play/F-Droid-ready hygiene.

Tasks:
1. `versionName 1.0.0` / `versionCode 10`; signing via CI secrets (`KEYSTORE_BASE64` etc.) with a `release.yml` workflow: tag → assembleRelease → sign → GitHub Release with APK + AAB + checksums + changelog from `CHANGELOG.md`.
2. Delete/replace template leftovers: `backup_rules.xml`, `data_extraction_rules.xml` (write real rules — exclude the DB), verify `allowBackup=false` intent, remove `largeHeap` if the RAM guard makes it unnecessary (measure first; if LLM needs it, document why in a comment).
3. Play Data Safety form facts (collects nothing, encrypted nothing-needed, no telemetry) into `PRIVACY.md`; link from Settings > About.
4. Trim dead weight: `ChatInputBar.kt` (W0), any unused strings/resources; run `./gradlew lint` clean with zero errors.
5. F-Droid metadata dir (`metadata/com.example.tavi.yml`) if you want F-Droid distribution later.

**Acceptance:** pushing tag `v1.0.0` produces a signed GitHub Release with passing CI artifacts.

## W6 — POSITIONING & DOCS (last, so it only states truth)

1. README: honest comparison table vs GemOfGemma / PokeClaw / Gemini Nano (columns: model, tools, multimodal, voice loop, agent mode, benchmarks, license) — Tavi wins on tool breadth + safety architecture; say so; concede the rest until measured.
2. Use exactly this defensible claim, or stricter: "the most complete open-source tool and safety architecture of any offline Android assistant — 53 audited, permission-gated, human-confirmed native tools."
3. Update the tool table from `ToolRegistry` (write a small script that generates it so it can never drift again: `scripts/gen_tool_table.py` → README section).
4. Screenshots updated to current UI (E2B badge, catalog, hands-free pill, agent step card).

---

## EXECUTION ORDER & MILESTONES

- M0 (30 min): delete `ChatInputBar.kt`, move hardcoded dependency versions to version catalog, `./gradlew testDebugUnitTest` green.
- M1: W1 complete → tag nothing yet; demo video of E2B + tools.
- M2: W2 complete → real numbers committed.
- M3: W3 + W4 complete → hands-free demo + agent demo.
- M4: W5 → v1.0.0 signed release.
- M5: W6 → docs reflect shipped truth.

## FINAL DEFINITION OF DONE

1. `./gradlew lint testDebugUnitTest assembleRelease` — zero errors, ≥ 155 unit tests passing.
2. On-device: E2B demo, benchmark JSON committed, hands-free loop demo, agent demo with kill switch — all recorded in `benchmarks/` + README.
3. Zero fabricated numbers, zero dead files, zero hardcoded strings/colors introduced.
4. README comparison table only contains claims backed by committed evidence.

Report progress per milestone with: files changed, tests added/removed, and the one riskiest thing you did in that milestone. If a LiteRT-LM SDK capability assumed here turns out unavailable (e.g., dynamic tool registration), stop that subtask, implement the documented fallback (annotation bridge or structured-output JSON path), and note the deviation in the milestone report.
