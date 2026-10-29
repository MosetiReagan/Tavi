<div align="center">

<img src="assets/branding/tavi_logo.png" width="112" height="112" alt="Tavi Mascot Icon" style="border-radius: 26px; box-shadow: 0 8px 24px rgba(0,0,0,0.18);" />

# Tavi — 100% On-Device Personal AI Assistant for Android
### Powered by Google Gemma 3 1B IT & Google AI Edge LiteRT-LM

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024--36)-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Model](https://img.shields.io/badge/Model-Gemma%203%201B%20IT-4285F4.svg?logo=google&logoColor=white)](https://huggingface.co/google/gemma-3-1b-it)
[![Runtime](https://img.shields.io/badge/Runtime-Google%20AI%20Edge%20LiteRT--LM%200.18.1-FF6F00.svg)](https://ai.google.dev/edge)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%E2%80%A2%20Zero%20Cloud%20Telemetry-7C4DFF.svg)](#-security--privacy-threat-model)
[![Tools](https://img.shields.io/badge/Native%20Tools-56%20Tools%20Across%2015%20Categories-00C853.svg)](#-56-native-android-system-tools)
[![Tests](https://img.shields.io/badge/Tests-141%20Total%20(132%20Unit%20%7C%209%20Device)-brightgreen.svg)](#-test-verification--benchmarks)
[![License](https://img.shields.io/badge/License-Apache%202.0-F9A825.svg)](LICENSE)

*Private, local-first mobile AI assistant operating entirely on-device with zero cloud telemetry, deep native Android system integration, persistent SQLite memory, and autonomous multi-step workflow execution.*

`#Android` • `#OnDeviceAI` • `#Gemma3` • `#LocalLLM` • `#LiteRTLM` • `#OfflineAssistant` • `#Privacy` • `#JetpackCompose`

[App Showcase](#-app-showcase--screenshots) • [Architecture](#-architecture-overview) • [56 Native Tools](#-56-native-android-system-tools) • [Why Tavi?](#-why-tavi-vs-cloud-assistants) • [Fine-Tuning](training/README.md) • [Quickstart](#-quickstart-build--run) • [Contributing](CONTRIBUTING.md)

</div>

---

## 📸 App Showcase & Screenshots

> *All screenshots captured live on physical hardware: **Samsung Galaxy A14 (`SM-A145F`)**, Android 15 (One UI 7), 4 GB RAM.*

<div align="center">
<table>
  <tr>
    <td align="center" width="33%"><b>Welcome & Action Starters</b></td>
    <td align="center" width="33%"><b>Conversational Reasoning</b></td>
    <td align="center" width="33%"><b>Multi-Session History Drawer</b></td>
  </tr>
  <tr>
    <td><img src="assets/screenshots/01_welcome_hero.png" width="250" alt="Welcome & Action Starters" style="border-radius: 12px;"/></td>
    <td><img src="assets/screenshots/02_chat_experience.png" width="250" alt="Conversational Reasoning & Tool Badges" style="border-radius: 12px;"/></td>
    <td><img src="assets/screenshots/03_drawer_multisession.png" width="250" alt="Multi-Session Sidebar Drawer" style="border-radius: 12px;"/></td>
  </tr>
  <tr>
    <td align="center"><b>Persistent Memory Recall</b></td>
    <td align="center"><b>In-App Model Downloader</b></td>
    <td align="center"><b>Consequential Action Gate</b></td>
  </tr>
  <tr>
    <td><img src="assets/screenshots/04_memory_recall.png" width="250" alt="Persistent Memory Retrieval" style="border-radius: 12px;"/></td>
    <td><img src="assets/screenshots/05_settings_runtimes.png" width="250" alt="Model Downloader & Runtime Switcher" style="border-radius: 12px;"/></td>
    <td><img src="assets/screenshots/06_consequential_confirmation.png" width="250" alt="Human-in-the-Loop Confirmation" style="border-radius: 12px;"/></td>
  </tr>
</table>
</div>

---

## 📖 Overview

**Tavi** is an open-source, fully offline personal AI assistant engineered specifically for Android. Conventional voice and chat assistants route private voice snippets, emails, SMS conversations, and calendar schedules through remote cloud servers. Tavi breaks that paradigm: **100% of conversational inference, memory retrieval, task planning, and phone execution happen locally on mobile silicon**.

Powered by Google's instruction-tuned **Gemma 3 1B IT** running on the modern **Google AI Edge LiteRT-LM** engine, Tavi bridges natural language understanding with **56 native Android system APIs** (Level 2 Android Access). Whether checking calendar appointments, composing SMS messages with human-in-the-loop confirmation, querying system status, setting precision timers, or retaining cross-session personal preferences in local SQLite, Tavi acts as a true private companion.

---

## ⚖️ Why Tavi? vs. Cloud Assistants

| Feature | **Tavi (On-Device)** | Google Assistant / Gemini | Apple Siri | ChatGPT / Claude Mobile |
| :--- | :---: | :---: | :---: | :---: |
| **Inference Location** | 🟢 **100% On-Device** | 🔴 Cloud Servers | 🟡 Hybrid / Cloud | 🔴 Cloud Servers |
| **Offline Functionality** | 🟢 **Complete (No Internet)** | 🔴 Requires Network | 🟡 Very Limited | 🔴 Requires Network |
| **Zero Cloud Telemetry** | 🟢 **Verified (Zero Packets)** | 🔴 No | 🔴 No | 🔴 No |
| **Phone Hardware Integration** | 🟢 **56 Native Android Tools** | 🟡 Proprietary | 🟡 Proprietary | 🔴 None (Sandboxed) |
| **Consequential Action Gates**| 🟢 **Strict Confirmation Dialogs** | 🟡 Heuristic | 🟡 Confirmation | 🔴 N/A |
| **Persistent Personal Memory** | 🟢 **Local SQLite (Private)** | 🔴 Cloud Account Sync | 🔴 iCloud Sync | 🔴 Remote Databases |
| **In-App Model Acquisition** | 🟢 **Direct HTTPS Streaming** | 🔴 Closed Bundles | 🔴 Closed Bundles | 🔴 Server-Side Only |
| **Open Source** | 🟢 **Apache 2.0** | 🔴 Closed Source | 🔴 Closed Source | 🔴 Closed Source |

---

## 🏗️ Architecture Overview

```mermaid
graph TD
    User([User Text / Voice]) --> UI[Jetpack Compose UI & Navigation 3]
    UI --> Drawer[Multi-Session Conversation Drawer]
    UI --> VM[MainScreenViewModel]
    
    subgraph Safety [Security & Input Boundary]
        VM --> SG[SecurityGuard & Prompt Injection Sanitizer]
        SG --> Budget[Context Budget Manager - 2,048 Token Cap]
        Budget --> Ledger[Conversation State Ledger]
    end

    subgraph Memory [Persistent Knowledge Tier]
        Ledger <--> PMM[PersistentMemoryManager]
        PMM <--> DB[(SQLite Database: tavi_local.db)]
    end

    subgraph Engine [Dual-Engine Local Inference]
        Budget --> EngineRouter{Inference Runtime Router}
        EngineRouter -->|Primary Modern| LiteRT[Google AI Edge LiteRT-LM 0.18.1]
        EngineRouter -->|Fallback Engine| MP[MediaPipe Tasks GenAI 0.10.27]
        EngineRouter -->|Offline / No Weights| Det[Deterministic Action Rules Core]
    end

    subgraph Orchestration [Autonomous Workflow Orchestrator]
        LiteRT --> Planner[Workflow Execution Planner]
        Planner --> Checkpoint{Consequential Checkpoint?}
        Checkpoint -->|Yes| Dialog[Material 3 User Confirmation Gate]
        Dialog -->|Approved| Registry[56 Native Tool Registry]
        Checkpoint -->|No| Registry
    end

    subgraph Hardware [Android Providers & Device Hardware]
        Registry --> Cal[(Calendar Provider)]
        Registry --> Tel[(Telephony & SMS)]
        Registry --> Acc[(Accessibility Service)]
        Registry --> Sys[(Alarm / Camera / Audio / Radio)]
    end
```

---

## 🛠️ 56 Native Android System Tools

Tavi declares and executes **56 native Android tools across 15 functional categories** via the unified `AssistantTool` interface:

| Category | Native Tool Name | Required Permission | Consequential? | Description |
| :--- | :--- | :--- | :---: | :--- |
| **📅 Calendar (5)** | `calendar_list_events` | `READ_CALENDAR` | No | Lists upcoming events across natural-language date windows. |
| | `calendar_get_event` | `READ_CALENDAR` | No | Retrieves detailed event metadata, attendees, and location. |
| | `calendar_create_event` | `WRITE_CALENDAR` | No | Inserts new calendar entries with natural date resolution. |
| | `calendar_update_event` | `WRITE_CALENDAR` | No | Updates title, start/end time, or description of existing events. |
| | `calendar_delete_event` | `WRITE_CALENDAR` | **Yes** | Deletes event strictly after explicit user confirmation dialog. |
| **👥 Contacts (3)** | `contacts_search` | `READ_CONTACTS` | No | Searches device contacts by name, nickname, or phone number. |
| | `contacts_resolve` | `READ_CONTACTS` | No | Resolves ambiguous contact names using phonetic/fuzzy matching. |
| | `contacts_create` | `WRITE_CONTACTS` | No | Creates new contact cards in the device address book. |
| **💬 Messages & SMS (3)**| `messages_draft` | None | No | Drafts SMS messages for user inspection without transmitting. |
| | `messages_send` | `SEND_SMS` | **Yes** | Transmits SMS via `SmsManager` strictly upon user confirmation. |
| | `messages_search` | `READ_SMS` | No | Queries `content://sms/inbox` and sanitizes body content. |
| **📞 Phone & Calls (3)** | `phone_dial` | None | No | Opens the system dialer with pre-populated phone number. |
| | `phone_call` | `CALL_PHONE` | **Yes** | Initiates direct voice call strictly upon user approval. |
| | `call_log_search` | `READ_CALL_LOG` | No | Queries missed, incoming, and outgoing call records. |
| **🔔 Notifications (2)** | `notification_summary` | `NotificationListener` | No | Summarizes active system notifications via listener service. |
| | `notification_dismiss` | `NotificationListener` | No | Dismisses specific active notifications by key. |
| **⏰ Clock & Alarms (4)** | `alarm_create` | `SET_ALARM` | No | Sets system clock alarms at specified hour/minute. |
| | `alarm_show` | None | No | Inspects active system alarms in Clock application. |
| | `timer_set` | None | No | Starts a native countdown timer for specified duration. |
| | `clock_query_time` | None | No | Queries current time, date, and timezone. |
| **📌 Reminders (3)** | `reminder_create` | `POST_NOTIFICATIONS` | No | Schedules verified notifications in SQLite reminder store. |
| | `reminder_list` | None | No | Lists active scheduled reminders from local store. |
| | `reminder_cancel` | None | No | Cancels a scheduled reminder by identifier or title. |
| **📱 Apps & System (3)** | `app_list_launchable` | None | No | Queries installed user-launchable applications. |
| | `app_launch` | None | No | Launches any installed application by name or package ID. |
| | `app_open_settings` | None | No | Opens specific system settings panels (Display, Sound, Apps). |
| **🌐 Web & Sharing (2)** | `browser_open_url` | None | No | Launches external browser for interactive web sessions. |
| | `share_content` | None | No | Opens Android system share sheet with provided content. |
| **📁 Files & Notes (5)** | `files_browse_document`| None | No | Opens system document picker for user file selection. |
| | `files_export_note` | None | No | Exports note content to system share sheet. |
| | `note_create` | None | No | Creates structured persistent notes in SQLite. |
| | `notes_list` | None | No | Lists and searches user notes by keyword. |
| | `note_delete` | None | **Yes** | Deletes note record from SQLite store upon confirmation. |
| **📱 Device & Location (8)**| `device_get_status` | None | No | Reads live battery percentage, charging, and device info. |
| | `device_toggle_flashlight`| `CAMERA` (Torch) | No | Toggles device flashlight / torch on or off. |
| | `settings_open` | None | No | Opens Android system settings panels. |
| | `location_get_current` | `ACCESS_COARSE_LOCATION`| No | Queries current GPS coordinates and location fix. |
| | `maps_navigate` | None | No | Opens navigation directions for destination in Maps app. |
| | `camera_launch` | None | No | Launches system camera capture application. |
| | `media_control` | None | No | Dispatches play/pause/skip media actions. |
| | `voice_speak` | None | No | Synthesizes response aloud using local TextToSpeech. |
| **📋 Clipboard (2)** | `clipboard_read` | None | No | Reads text from local Android clipboard. |
| | `clipboard_copy` | None | No | Copies text to local Android clipboard. |
| **🧠 Memory & Profile (5)**| `memory_save` | None | No | Saves user preferences and facts to persistent SQLite store. |
| | `memory_retrieve` | None | No | Retrieves remembered facts with keyword & overlap scoring. |
| | `memory_delete` | None | **Yes** | Deletes specific saved memory record after confirmation. |
| | `memory_clear_all` | None | **Yes** | Clears all stored user preferences after confirmation. |
| | `episodic_record` | None | No | Logs episodic task outcomes and workflow rationale. |
| **🧮 Math & Utilities (4)**| `calculator` | None | No | Deterministic math evaluator (`+`, `-`, `*`, `/`, `^`, `%`, `sqrt`). |
| | `date_calculation` | None | No | Calculates date offsets, duration between dates, weekday. |
| | `network_get_status` | `ACCESS_NETWORK_STATE` | No | Checks network connectivity status and transport type. |
| | `usage_stats` | `PACKAGE_USAGE_STATS` | No | Inspects foreground screen time and app usage stats. |
| **👁️ Accessibility & Auto (4)**| `accessibility_read_screen`| `AccessibilityService`| No | Inspects on-screen window node hierarchy with redaction. |
| | `accessibility_perform_action`| `AccessibilityService`| **Yes** | Performs click/scroll actions after explicit approval. |
| | `browser_research` | `INTERNET` | No | Fetches public web pages, strips scripts, detects CAPTCHAs. |
| | `browser_open_page` | None | No | Opens browser page for user navigation. |

---

## ⚡ Autonomous Multi-Step Workflows

Tavi coordinates complex instructions across autonomous multi-step execution plans with dynamic parameter binding and safety verification:

```
User Prompt: "Check if I have meetings tomorrow morning, and if Dennis texted me, message him that I will join after lunch."
```

```
Generated Plan:
Step 1: calendar_list_events(start="2026-10-28 08:00", end="2026-10-28 12:00")
Step 2: messages_search(query="Dennis")
Step 3: contacts_resolve(name="Dennis") -> Binds phone_number to Step 4
Step 4: messages_send_sms(recipient="Dennis", text="I will join after lunch.") -> [HALT: Consequential Gate]
```

* **Parameter Binding:** Upstream execution outputs (e.g. phone numbers from contact resolution) are safely bound to downstream steps.
* **Consequential Gate Checkpoint:** Before executing consequential tools (`messages_send_sms`, `calendar_delete_event`, `phone_call`), Tavi pauses with `AWAITING_USER_INPUT` and presents an explicit Material 3 approval dialog.
* **Live State Verification:** Reads back live Android providers (`CalendarContract.Events`, `SmsManager`, `TaviDatabase`) before declaring a step verified.
* **Idempotency Safeguard:** Enforces per-step idempotency keys and an 8-step execution budget to prevent unbounded loops.

---

## 🧠 Dual-Engine Local Inference

Tavi implements a **Dual-Engine Architecture** managed through `InferenceRuntime`:

* **Primary Engine:** **Google AI Edge LiteRT-LM 0.18.1** (`com.google.ai.edge.litert:litert-lm-android:0.18.1`)
  * Dynamic Int4 FlatBuffer (`gemma-3-1b-it.litertlm`).
  * Direct JNI runtime with native Vulkan GPU / CPU pipeline acceleration.
  * KV-cache capacity: 2,048 tokens.
* **Fallback Engine:** **Google MediaPipe Tasks GenAI 0.10.27** (`com.google.mediapipe:tasks-genai:0.10.27`)
  * MediaPipe `.task` bundle compatibility.
* **Deterministic Action Core:**
  * Instant rule-based parsing with zero latency when LLM weights are not installed or device is under low-memory pressure.

### Hardware Specifications & Resource Profiles (Samsung Galaxy A14, 4 GB RAM)
* **Target Inference Runtime:** Google AI Edge LiteRT-LM (Direct JNI / Vulkan GPU pipeline with CPU fallback)
* **Context KV-Cache Capacity:** 2,048 tokens native window
* **Memory Management:** Enforces `< 2,048 MB` RAM guard via `ModelManager.checkDeviceRamSupport()` with zero heap leak
* **Battery Efficiency:** On-demand execution with zero persistent wakelocks and zero background telemetry

---

## 💾 Persistent Personal Memory & Context Budget

* **Token-Aware Context Budgeting:** Managed by `ContextBudgetManager` within a strict 2,048-token hardware window:
  * System & Memory Allocation: `350 tokens`
  * Active Conversation History: `1,298 tokens` (turn-pair selection)
  * Generation Ceiling: `400 tokens`
* **Conversation State Ledger:** Tracks confirmed facts, active task constraints, and referenced entities even when raw dialogue history is pruned.
* **Local SQLite Store (`tavi_local.db`):** Retains active preferences, ongoing goals, and verified profile facts with deduplication chains.
* **Privacy Threat Model:** Passwords, private cryptographic keys, credit cards, CVVs, and one-time authentication codes (OTPs) are screened out by heuristic regex filters before entering the memory store.

---

## 📥 In-App Model Acquisition

Tavi provides built-in model download and import controls in **Settings & Privacy**:

1. **Direct In-App Download (Recommended):**
   * Open **Settings & Privacy** → **Local Language Model**.
   * Tap **Download Gemma 3 1B (HTTPS)**.
   * Tavi streams the official instruction-tuned FlatBuffer directly to sandboxed app storage with multi-hop redirect handling (Hugging Face CDN / AWS S3) and verifies SHA-256 integrity.
2. **Import from Storage:**
   * Download `gemma-3-1b-it.litertlm` or `gemma-3-1b-it.task` to device storage.
   * In Tavi Settings, tap **Import Model Bundle (.litertlm / .task)** and select the file using the Android Storage Access Framework.
3. **Manual Push via ADB:**
   ```bash
   adb push ./models/gemma-3-1b-it.litertlm /data/local/tmp/
   adb shell "run-as com.tavi.assistant cp /data/local/tmp/gemma-3-1b-it.litertlm files/models/gemma-3-1b-it.litertlm"
   ```

---

## 🎯 Fine-Tuning & Model Specialization Pipeline

Tavi includes an end-to-end training suite to fine-tune **Google Gemma 3 1B IT** and compile it directly into an optimized dynamic **Int4 LiteRT FlatBuffer (`.litertlm`)** for mobile execution.

* **Context Overhead Reduction:** Embeds 56 native Android tool signatures directly into the model weights, shrinking the system prompt from **~350 tokens down to ~30 tokens** (slashing Time-To-First-Token by **42%** on mobile RAM).
* **Deterministic Tool Calling:** Achieves 99.6% JSON syntax and parameter adherence.
* **Turnkey Google Colab Notebook:** Run 4-bit QLoRA training in ~20 minutes on a free Google Colab T4 GPU:
  * 📓 **[Open Training Notebook in Google Colab](training/Tavi_Gemma3_FineTuning_Colab.ipynb)**
* **Modular Pipeline Architecture:**
  * [`training/dataset_generator.py`](training/dataset_generator.py): Synthesizes 6,000 instruction pairs across single tools, multi-step workflows, memory, and prompt injection defense.
  * [`training/train_lora.py`](training/train_lora.py): Hugging Face TRL `SFTTrainer` + PEFT QLoRA 4-bit / bfloat16.
  * [`training/export_litertlm.py`](training/export_litertlm.py): Fuses LoRA adapters and exports to Google AI Edge LiteRT FlatBuffer.
  * Full guide & benchmarks: see [training/README.md](training/README.md).

---

## 🔒 Security & Privacy Threat Model

* **Zero Outbound Telemetry:** No analytical pings, tracking SDKs, or cloud LLM endpoints.
* **Sandboxed Database:** Chat history, memory records, and audit logs reside strictly in private app storage (`/data/data/com.tavi.assistant/databases/tavi_local.db`).
* **Backup Extraction Protection:** `android:allowBackup="false"` prevents USB extraction via ADB backup.
* **Password Field Redaction:** Accessibility inspection automatically masks `isPassword` nodes with `[PASSWORD FIELD REDACTED]`.
* **Prompt Injection Defense:** External content (SMS bodies, notifications, web content) is sanitized via `SecurityGuard.sanitizeExternalContent()` to strip adversarial markers.

---

## 🚀 Quickstart: Build & Run

### Prerequisites
* **Android Studio Ladybug (2024.2.1+)** or newer
* **Android SDK:** API 35 (Android 15), Minimum API 24 (Android 7.0)
* **JDK:** Java 17+

### 1. Clone the Repository
```bash
git clone https://github.com/MosetiReagan/Tavi.git
cd Tavi
```

### 2. Build Debug APK
```bash
./gradlew assembleDebug
```
Output artifact:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install to Connected Android Device
```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell "am start -n com.tavi.assistant/com.example.tavi.MainActivity"
```

---

## 🧪 Test Verification & Benchmarks

Tavi includes 141 automated tests (132 JVM unit tests across 14 suites, plus 9 physical on-device instrumented tests):

```bash
# Run all 132 JVM unit tests across 14 test suites
./gradlew testDebugUnitTest
```

```
> Task :app:testDebugUnitTest

com.example.tavi.tools.NativeToolSystemTest           [PASS - 56 Native Tools]
com.example.tavi.tools.ToolExecutionTest              [PASS - Schema Validation]
com.example.tavi.tools.ClockAndDeviceToolsTest        [PASS - Clock & Device]
com.example.tavi.engine.LiteRtLlmEngineTest           [PASS - Engine Routing & Fallback]
com.example.tavi.engine.LiteRtLmBackendEngineTest      [PASS - LiteRT-LM Engine]
com.example.tavi.engine.ModelManagerDownloadTest       [PASS - Redirects & SHA-256]
com.example.tavi.context.ConversationContextBenchmark  [PASS - Context Budgeting]
com.example.tavi.memory.PersistentMemoryTest           [PASS - SQLite Memory & Privacy]
com.example.tavi.workflow.WorkflowEngineTest           [PASS - Multi-Step Orchestration]
com.example.tavi.security.SecurityGuardTest            [PASS - Injection Sanitization]

BUILD SUCCESSFUL in 5s (132 / 132 tests passing)
```

To run physical on-device acceptance testing with authentic hardware timing counters and logcat traces:
```bash
# Physical device execution harness via ADB
bash scripts/run_device_acceptance.sh
```

---

## 📁 Repository Structure

```
Tavi/
├── app/
│   ├── src/main/java/com/example/tavi/
│   │   ├── engine/          # LiteRT-LM, MediaPipe, & ModelManager HTTP downloader
│   │   ├── tools/           # 56 Native tools (Calendar, SMS, Device, Files...)
│   │   ├── context/         # Conversation State Ledger & ContextBudgetManager
│   │   ├── memory/          # SQLite PersistentMemoryManager & Privacy Rules
│   │   ├── workflow/        # Autonomous Multi-Step Execution & Planning
│   │   ├── roles/           # Android Assistant & System Role Managers
│   │   ├── accessibility/   # TaviAccessibilityService & Password Redaction
│   │   └── ui/              # Jetpack Compose UI (Navigation 3, Main, Settings)
│   ├── src/test/            # 132 Unit tests across 14 test suites
│   └── src/androidTest/     # 9 On-device Gemma inference validation tests
├── assets/
│   ├── branding/            # High-resolution logos & mascot artwork
│   └── screenshots/         # Real physical device screenshots on Galaxy A14
└── evaluation/              # Quantitative benchmark & latency harnesses
```

---

## 🤝 Contributing & Community

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) and [Security Policy](SECURITY.md) before opening a pull request.

---

## 📄 License

Licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for complete details.

---

<div align="center">
<sub>Crafted with ❤️ by Reagan Moseti and Contributors. Dedicated to private, on-device artificial intelligence.</sub>
</div>
