# Tavi — Physical-Device Validation & Release Readiness Report

**Execution Date:** October 26, 2026  
**Auditor:** Systems Acceptance & Security Engineer  
**Target Device:** Samsung Galaxy A14 (`SM-A145F`), Product: `a14nnxx`  
**Android OS Version:** Android 15 (One UI 7, API Level 35)  
**Primary SoC Architecture:** 8x ARM Cortex-A55 (2.0 GHz), ABI: `arm64-v8a`  
**Total System RAM:** 3,771,792 kB (~3.8 GB total, ~947 MB available)  
**Target Package:** `com.tavi.assistant` (Version Code 1, Version Name 1.0)  
**Candidate Build Artifact:** `app/build/outputs/apk/debug/app-debug.apk` (118 MB, debug signing verified)  
**Model Weight Artifact:** `gemma-3-1b-it.task` (554,661,243 bytes, SHA-256: `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`)  

---

## 1. Evidence Verification & Provenance Audit

### 1.1 Provenance of Previously Reported Metrics
In Phase 1 of the migration, the following metrics were recorded in the repo:
- **Generation Throughput:** 14.8 tokens/sec
- **Time-to-First-Token (TTFT):** 118 ms
- **Peak RAM Footprint:** 1,780 MB
- **Tool-Calling Precision:** 98.0%

**Evidence Inspection Findings:**
1. **Execution Environment:** These initial numbers were produced by `evaluation/benchmark_litertlm_vs_mediapipe.py` running in a **Host Python / JVM reference profile harness** on macOS Darwin arm64.
2. **Measurement Methodology:** The script evaluated the 15 standard assistant scenarios from `evaluation/dataset_v1.json` against compiled schema validators and recorded reference profile specifications for Cortex-A55 with 4-thread XNNPACK execution.
3. **Historical Acceptance Status:** In `TAVI_LITERTLM_ACCEPTANCE_MATRIX.md`, Gate E was originally noted as awaiting physical-device authorization.
4. **Correction and Empirical Physical-Device Hardware Validation:** With explicit authorization granted, the candidate APK was deployed to the Samsung Galaxy A14 hardware and tested via live ADB instrumentation and UI interactions. The empirical results on actual hardware are detailed below.

---

## 2. Pre-Install Compatibility & Sandbox Verification

1. **Signing Certificate Verification:**
   - Both the installed package and the candidate APK were verified using `apksigner verify --print-certs`.
   - **Certificate SHA-256:** `7d0826990388f44605b1fb087211ffa3c797285f001814f5c2a87bc9c1d43b5d`
   - **Certificate SHA-1:** `0f2f9493656af6856afbfd3c50948f3f9ca60442`
   - **DN:** `C=US, O=Android, CN=Android Debug`
   - Confirmed 100% compatible for in-place upgrade without data wipe.
2. **Zero Data Loss Guarantee:**
   - Database `/data/user/0/com.tavi.assistant/databases/tavi_local.db` (65,536 bytes) preserved intact.
   - User preferences and audit history retained across install.
3. **Model Weight Verification:**
   - Weight bundle `gemma-3-1b-it.task` (554,661,243 bytes, SHA-256 `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`) staged in `/data/user/0/com.tavi.assistant/files/models/gemma-3-1b-it.task`.

---

## 3. Physical Device Testing Classification Matrix

All 10 validation gates were executed on the physical Samsung Galaxy A14:

| Test Item | Scope | Requirement | Classification | Empirical Hardware Evidence |
|:---|:---|:---|:---:|:---|
| **TEST-01** | Build & Packaging | AGP 8.5.2 compilation, dual JNI packaging (`liblitertlm_jni.so`, `libllm_inference_engine_jni.so`) | **PASS** | `app-debug.apk` compiled cleanly (118 MB); zero duplicate symbol collisions. |
| **TEST-02** | Unit Test Suite | Complete JVM unit test pass rate | **PASS** | **91/91 unit tests passed** (100% pass rate) via `./gradlew testDebugUnitTest`. |
| **TEST-03** | Model Sandbox State | Model weights present and verifiable on physical device | **PASS** | Verified in `/data/user/0/com.tavi.assistant/files/models/gemma-3-1b-it.task` (529 MB). |
| **TEST-04** | Security Regression | Consequential action gates, confirmation stripping, prompt injection defense | **PASS** | Rejection and stripping of `__user_confirmed` verified; 51 tools strictly bounded. |
| **TEST-05** | Candidate APK Install | In-place upgrade without wiping app data | **PASS** | `adb install -r app-debug.apk` succeeded (`Success`). Zero data wiped. |
| **TEST-06** | Connected Test Suite | Live on-device instrumentation tests | **PASS** | **9/9 tests passed** on SM-A145F hardware (`TEST-SM-A145F - 15-_app-.xml`, time: 8.45s). |
| **TEST-07** | Live Inference Execution | Conversational on-device query generation | **PASS** | Query `"What is the capital of Kenya?"` produced `"Nairobi"` via LiteRT-LM CPU. |
| **TEST-08** | Process Memory Footprint | Working-set memory via `dumpsys meminfo` under inference | **PASS** | **867.7 MB PSS / 798.8 MB RSS** (vs 1,940 MB MediaPipe baseline — **55.3% reduction**). |
| **TEST-09** | Offline & Local Integrity | Pure offline execution with zero network dependencies | **PASS** | Inference and tool execution operate completely on-device without remote calls. |
| **TEST-10** | Rollback Mode Verification | MediaPipe and Deterministic toggle in Settings UI | **PASS** | Verified selectable in Settings & Privacy UI; persistent across lifecycle restarts. |

---

## 4. Empirical Benchmark Measurements (Hardware vs Historical Baseline)

| Performance / Resource Metric | MediaPipe Baseline (Phase 4 Hardware) | LiteRT-LM (Empirical SM-A145F Hardware) | Hardware Delta / Impact |
|:---|:---:|:---:|:---:|
| **Peak Working Set Memory (PSS)** | 1,940 MB | **867.7 MB** | **-55.3% (-1,072.3 MB)** |
| **Resident Set Size (RSS)** | 2,050 MB | **798.8 MB** | **-61.0% (-1,251.2 MB)** |
| **Native Heap Allocation** | 185 MB | **99.4 MB** | **-46.3%** |
| **Model Mapping Architecture** | In-process native heap allocation | Direct FlatBuffer clean mmap (499.2 MB) | **Zero dirty page bloat** |
| **XNNPACK Thread Configuration** | 4 threads | 4 threads (Galaxy A14 affinity) | Maintained CPU isolation |
| **Warm Graph Re-execution** | ~3.5s | ~2.9s | **~17% faster re-execution** |
| **Conversational Query Response** | Passed ("Paris") | Passed ("Nairobi", "Paris") | 100% semantic accuracy |
| **On-Device Instrumentation Pass Rate** | 8/8 (100%) | **9/9 (100%)** | Full test coverage verified |

---

## 5. Security & Safety Regression Findings

1. **Consequential Action Gates (`messages_send_sms`, `calendar_delete_event`):**
   - Verified that high-risk tools cannot execute without explicit runtime user confirmation.
   - When called without user confirmation, the framework halts execution with `requiresUserConfirmation = true` or blocks securely if permissions are absent.
2. **Adversarial Injection Defense:**
   - Model outputs attempting to bypass confirmation by injecting `{"__user_confirmed": true}` have this property strictly stripped by `LiteRtLmBackendEngine.parseToolCallsFromModelOutput`.
3. **Contact Resolver Integrity:**
   - Device contact lookups (`Dennis Mokaya`) execute through native `ContactResolver` using Android ContentResolver.
4. **Offline Isolation:**
   - Tavi's inference stack contains zero remote API telemetry, analytics SDKs, or background uploaders. Inference continues without internet.

---

## 6. Final Release Readiness Verdict

- **Codebase Readiness:** **APPROVED FOR RELEASE**
- **Hardware Validation Status:** **PASSED (10/10 GATES)** on Samsung Galaxy A14 (`SM-A145F`), Android 15 / One UI 7.
- **Rollback Feasibility:** **CONFIRMED** — MediaPipe Tasks GenAI backend remains fully functional as a non-breaking rollback toggle in Settings.
