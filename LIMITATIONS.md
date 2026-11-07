# ⚠️ Tavi System Limitations & Operational Boundaries

This document records the physical hardware, operating system, and runtime boundaries of Tavi v1.0.0.

---

## 1. Physical Device Requirements for Benchmark Metrics

* **Host JVM vs. Physical Device:**
  * Host-side test suites (`./gradlew testDebugUnitTest`) validate schemas, tool signatures, state machine transitions, safety guards, and fallback routing.
  * Measured inference speed, first response chunk latency, thermal throttling, and process PSS **cannot be measured by host JVM unit tests**. Callback chunks are not native tokenizer counts.
  * Physical measurements require executing `scripts/run_benchmark.sh` on an attached physical Android device via ADB. Real measurements are stored in [`benchmarks/`](benchmarks/). Target reference numbers in `evaluation/` are marked as architectural specifications (`# SPEC`).

---

## 2. RAM Sizing & Model Sizing Matrix

* **Gemma 4 E2B (`gemma-4-e2b.litertlm`):**
  * Weight file size: **~2.58 GB**
  * Recommended hardware: **≥ 6 GB physical RAM**, Vulkan GPU or 8-core ARM CPU.
  * Initialization latency and memory consumption require physical device measurement; no measured profile for these additions is available yet.
* **Gemma 3 1B IT (`gemma-3-1b-it.litertlm`):**
  * Weight file size: **~558 MB** for the pinned LiteRT-LM artifact; other bundles vary
  * Recommended hardware: **2 GB – 4 GB physical RAM**.
  * Optimized for low-memory devices (such as Samsung Galaxy A14 `SM-A145F`).
* **Deterministic Fallback Core:**
  * When compatible neural weights are unavailable or memory checks reject loading, Tavi uses the rule-based `DeterministicAssistantEngine`. Memory-pressure callbacks release loaded neural engines; the fallback still has normal app memory and execution costs.

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
  * The current agent is restricted to Settings, Chrome, and Firefox and requires a working local model. It verifies completion only when supplied visible success text can be checked.

---

## 4. Host JVM vs. Android On-Device Bytecode (Java 21 / D8)

* The upstream `litert-lm-android:0.18.1` library is compiled with Java 21 bytecode (class version 65).
* When running host JVM unit tests on development machines with JDK 17, directly instantiating the JNI LiteRT-LM runtime classes throws `java.lang.UnsupportedClassVersionError`.
* **In the Android build pipeline**, D8/R8 converts dependency bytecode into Android DEX. Core library desugaring supplies supported newer Java APIs on older Android versions. Successful packaging alone does not establish native runtime compatibility on every API level or ABI.
* JVM unit tests test tool bridges, OpenAPI schemas, handoff queues, and fallback chains without loading class 65 binaries. LiteRT-LM JNI inference has passed instrumentation on Android 14 / API 34 in an x86_64 cloud container; physical ARM devices and older Android versions still require validation.

---

## 5. Network & Offline Boundaries

* **Inference is 100% Offline:** Tavi does not upload audio, screen trees, conversation history, or personal memories to an AI service. User-requested communication, sharing, and browser tools can send approved content through their respective services.
* **Network Access Usage:**
  * Internet access is used when the user requests an in-app model download via HTTPS with SHA-256 verification. The pinned Gemma 3 artifact has a known digest; hosted Gemma 4 acquisition needs a publisher digest/authentication or a trusted local import.
  * Opt-in web research tool (`browser_research_topic`) fetches accessible public web pages when explicitly commanded.

## 6. Speech, Documents, and Schedules

* On-device recognition requires Android 12+ and an installed compatible speech provider/language pack. Older Android versions support text input. Offline speech output also requires a local voice for the selected language.
* Document retrieval uses local lexical indexing. OCR supports bundled English and Kiswahili data, with quality dependent on the source image; it does not guarantee semantic answers to every document question.
* Scheduled routines post a notification inviting the user to review and run their routine. They do not execute arbitrary phone actions unattended.
* Reminder precision depends on Android exact-alarm access. Notifications require permission, and app force-stop can prevent delivery until the app is reopened.
* Tavi's default SMS and dialer roles are disabled until complete role implementations are available. SMS/call tools remain subject to Android permissions and provider availability.
