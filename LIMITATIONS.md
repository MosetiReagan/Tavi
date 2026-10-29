# ⚠️ Tavi System Limitations & Operational Boundaries

This document records the physical hardware, operating system, and runtime boundaries of Tavi v1.0.0.

---

## 1. Physical Device Requirements for Benchmark Metrics

* **Host JVM vs. Physical Device:**
  * Host-side test suites (`./gradlew testDebugUnitTest`) validate schemas, tool signatures, state machine transitions, safety guards, and fallback routing.
  * Measured inference throughput (decode tokens/sec), time-to-first-token (TTFT), thermal throttling, and memory PSS allocations **cannot be measured on host machines**.
  * Physical measurements require executing `scripts/run_benchmark.sh` on an attached physical Android device via ADB. Real measurements are stored in [`benchmarks/`](benchmarks/). Target reference numbers in `evaluation/` are marked as architectural specifications (`# SPEC`).

---

## 2. RAM Sizing & Model Sizing Matrix

* **Gemma 4 E2B (`gemma-4-e2b.litertlm`):**
  * Weight file size: **~2.58 GB**
  * Recommended hardware: **≥ 6 GB physical RAM**, Vulkan GPU or 8-core ARM CPU.
  * Cold initialization on budget CPU devices (e.g., Cortex-A55) takes **~45 seconds** to map weights into memory.
* **Gemma 3 1B IT (`gemma-3-1b-it.litertlm`):**
  * Weight file size: **~1.10 GB**
  * Recommended hardware: **2 GB – 4 GB physical RAM**.
  * Optimized for low-memory devices (such as Samsung Galaxy A14 `SM-A145F`).
* **Deterministic Fallback Core:**
  * When neural model weights are not downloaded or device memory is under pressure (< 2,048 MB available), Tavi falls back instantaneously to the rule-based `DeterministicAssistantEngine` with zero RAM overhead.

---

## 3. Android Accessibility Permissions for Screen Agent Mode

* **Special Access Required:**
  * The Screen Agent (`screen_agent_run`) and screen inspection tools require `TaviAccessibilityService` to be enabled by the user in:
    * `Settings` → `Accessibility` → `Installed Apps` → `Tavi Accessibility Assistant` → `On`.
* **Android 13+ Restricted Settings:**
  * When sideloading or installing development APKs on Android 13+, Android may display a *"Restricted setting"* dialog.
  * Resolution: Open device `Settings` → `Apps` → `Tavi` → Three dots menu (top right) → `Allow restricted settings`.
* **Hard Safety Boundaries:**
  * Screen Agent has a hard-coded budget of **12 steps max** per task.
  * Banking applications, password managers, and payment flows are blacklisted from automated interaction.
  * Consequential actions (Buy, Send, Delete, Transfer, Pay) always pause execution and require explicit user tap confirmation.

---

## 4. Host JVM vs. Android On-Device Bytecode (Java 21 / D8)

* The upstream `litert-lm-android:0.18.1` library is compiled with Java 21 bytecode (class version 65).
* When running host JVM unit tests on development machines with JDK 17, directly instantiating the JNI LiteRT-LM runtime classes throws `java.lang.UnsupportedClassVersionError`.
* **In the Android build pipeline**, Android's D8/R8 dexer desugars all bytecode during APK packaging, allowing APKs to run cleanly on Android API 24+ without class version issues.
* Consequently, JVM unit tests test tool bridges, OpenAPI schemas, handoff queues, and fallback chains without loading class 65 binaries, while physical JNI runtime execution is validated via `androidTest`.

---

## 5. Network & Offline Boundaries

* **Inference is 100% Offline:** Tavi never transmits audio, screen trees, conversation history, or personal memories across the network.
* **Network Access Usage:**
  * Internet access is used strictly when the user requests an in-app neural model download via HTTPS (from official HuggingFace / CDN mirrors) with SHA-256 verification.
  * Opt-in web research tool (`browser_research_topic`) fetches accessible public web pages when explicitly commanded.
