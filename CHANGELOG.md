# Changelog

All notable changes to **Tavi** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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
