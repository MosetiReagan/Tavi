# Changelog

All notable changes to **Tavi** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Small model-free request engine for common bilingual commands, payload preservation, bounded input, and missing-detail clarification; neural weights load only when needed.
- Offline document library with text, image, and PDF import, bundled English/Kiswahili OCR, local search, page citations, and deletion.
- Editable personal routines with recurring reminder schedules; reminders restore after reboot and support snoozing.
- Explicit English/Kiswahili language selection, deterministic command routing, and installed offline speech pack checks.
- Durable workflow checkpoints scoped to their conversation and durable native write receipts to prevent duplicate execution.
- SMS send/delivery tracking and a status tool that distinguishes dispatch from confirmed Android callbacks.
- Local screen planning restricted to supported apps, visible targets, bounded steps, scoped confirmations, and a stop control.
- System assistant invocation panel with transient, redacted screen context.
- On-device regression tests for document deletion/search, English/Kiswahili image OCR, write receipt persistence, and reminder notification delivery/recurrence/snooze.

### Changed
- Pin model downloads to verified artifacts and select compatible models/runtimes for the device.
- Preserve tool evidence across conversation reloads; share relevant personal memory across model backends.
- Budget complete prompts and select relevant tool definitions within the local context limit.
- Show memory provenance, retrieve relevant facts, and apply corrections atomically.
- Enforce on-device speech recognition and offline speech output; complete hands-free follow-up and cancellation handling.
- Require successful streaming neural inference for benchmarks; distinguish callback chunks, token estimates, and sampled process memory.
- Compile instrumentation tests in CI and make lint errors fail builds.
- Offer an optional `taviTargetAbi` build property for producing an APK for one device architecture.
- Bundle only the 35 extended vector icons used by Tavi, replacing the complete icon library while preserving existing visuals and attribution.

### Fixed
- Reject invalid tool arguments and ambiguous calendar dates or action times.
- Handle negative voice confirmations without treating them as approval.
- Retain runtime fallback decisions and release model resources under memory pressure.
- Disable incomplete default SMS/dialer roles.
- Handle unavailable language preferences during host tests and repair the moved message bubble test import.
- Support date/time APIs on Android 7 through core library desugaring; guard newer SMS/screen APIs and notification permission.
- Use valid settings-only cloud backup/device transfer rules that omit private databases and files.
- Make real inference tests skip absent weights, select the installed runtime, and clean up resources; remove dependency on a specific saved contact.

## [1.0.0] - 2026-10-29

### Added
- **Gemma 4 E2B Migration:**
  - Upgraded Google AI Edge LiteRT-LM runtime to 0.18.1.
  - Implemented `ToolSetBridge` adapting 57 runtime tools into native OpenAPI structured tool definitions.
  - Added serialized `SessionHandoffQueue` and `Gemma4BackendEngine` supporting streaming and thinking reasoning channel.
  - Implemented collapsible thought process UI in `ModernMessageBubble`.
  - Added dual-model catalog in `ModelManager`: `gemma-4-e2b` (~2.58 GB, recommended for ≥ 6 GB RAM) and `gemma-3-1b-it` (~1.10 GB, default for low-RAM devices), with resumable HTTP range downloads, free space checks, and SHA-256 verification.
  - Resilient 3-tier fallback chain: Gemma 4 E2B → Gemma 3 1B → DeterministicAssistantEngine.

- **Continuous Voice Loop:**
  - Added `VoiceSessionMode` state machine (`IDLE`, `LISTENING`, `PROCESSING`, `SPEAKING`, `AWAITING_FOLLOWUP`).
  - Added barge-in speech interruption (`TextToSpeech.stop()` triggers on speech detection).
  - Added configurable follow-up window (3–8s) with pulsing listening dock pill.
  - Added `VoiceConfirmationParser` for hands-free affirmative / negative confirmation parsing.
  - Integrated system assistant invocation with `EXTRA_START_HANDSFREE` and proxied speech recognition in `TaviRecognitionService`.

- **Screen Agent Mode (Accessibility):**
  - Added `agent/` package: `ScreenAgentService` (gesture execution), `AccessibilityTreeReader` (depth-limited serialization with PII/password/OTP redaction), `AgentActionSpace`, `AgentSafetyGuard`, and `AgentExecutor`.
  - Added hard 12-step budget cap per task and persistent kill switch.
  - Added in-app Settings toggle with first-run explanation dialog outlining accessibility boundaries and safety protections.
  - Exposed `screen_agent_run` as a native consequential tool with human confirmation gate.

- **Real Device Benchmark Harness:**
  - Added `DeviceBenchmark.kt` instrumented test suite (`@RequiresDevice`) measuring cold init, TTFT, decode tok/s, peak PSS, and thermal status.
  - Added `scripts/run_benchmark.sh` adb automation harness and `benchmarks/` repository.
  - Relabeled evaluation scripts with `# SPEC — see measured results in benchmarks/`.

- **Release Engineering & Compliance:**
  - Bumped `versionCode = 10` and `versionName = "1.0.0"`.
  - Created `.github/workflows/release.yml` with automated release drafting, signing, and checksum generation.
  - Replaced template backup rules with production `backup_rules.xml` and `data_extraction_rules.xml` excluding database and neural model weights.
  - Created `PRIVACY.md` declaring Google Play Data Safety facts (zero telemetry, 100% on-device).
  - Added F-Droid metadata recipe `metadata/com.example.tavi.yml`.
  - Added `scripts/gen_tool_table.py` for automated native tool directory documentation.

### Changed
- Centralized external dependencies in `gradle/libs.versions.toml`.
- Replaced orphaned `ChatInputBar.kt` with `ModernInputDock.kt`.
- Updated README with honest competitor comparison matrix and audited 57 native tool table.

### Fixed
- Fixed unhandled empty catch blocks in `ModelManager.kt` and `AgentExecutor.kt` with descriptive warning logs.
- Fixed `TaviRecognitionService` stub throwing `ERROR_CLIENT`.
