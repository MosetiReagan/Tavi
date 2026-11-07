<div align="center">

<img src="assets/branding/tavi_logo.png" width="112" height="112" alt="Tavi Mascot Icon" style="border-radius: 26px; box-shadow: 0 8px 24px rgba(0,0,0,0.18);" />

# Tavi — 100% On-Device Personal AI Assistant for Android
### Powered by Google Gemma 4 E2B, Gemma 3 1B IT & Google AI Edge LiteRT-LM

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024--36)-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Model](https://img.shields.io/badge/Model-Gemma%204%20E2B%20%7C%203%201B-4285F4.svg?logo=google&logoColor=white)](https://huggingface.co/google)
[![Runtime](https://img.shields.io/badge/Runtime-Google%20AI%20Edge%20LiteRT--LM%200.18.1-FF6F00.svg)](https://ai.google.dev/edge)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%E2%80%A2%20Zero%20Cloud%20Telemetry-7C4DFF.svg)](#-security--privacy-threat-model)
[![Tools](https://img.shields.io/badge/Native%20Tools-60%20Tools%20Across%2015%20Categories-00C853.svg)](#-60-native-android-system-tools)
[![Tests](https://img.shields.io/badge/Tests-JVM%20%2B%20Android-brightgreen.svg)](#-test-verification--benchmarks)
[![License](https://img.shields.io/badge/License-Apache%202.0-F9A825.svg)](LICENSE)

*Private, local-first mobile AI assistant operating entirely on-device with zero cloud telemetry, deep native Android system integration, persistent SQLite memory, screen agent mode, and continuous hands-free voice.*

`#Android` • `#OnDeviceAI` • `#Gemma4` • `#Gemma3` • `#LocalLLM` • `#LiteRTLM` • `#OfflineAssistant` • `#Privacy` • `#JetpackCompose`

[App Showcase](#-app-showcase--screenshots) • [Architecture](#-architecture-overview) • [60 Native Tools](#-60-native-android-system-tools) • [Why Tavi?](#-why-tavi) • [Fine-Tuning](training/README.md) • [Quickstart](#-quickstart-build--run) • [Contributing](CONTRIBUTING.md)

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

Powered by Google Gemma 4 E2B (with Gemma 3 1B for low-RAM devices) via Google AI Edge LiteRT-LM, Tavi provides 60 native tools with permission checks and confirmation for consequential actions. Whether checking calendar appointments, composing SMS messages with human-in-the-loop confirmation, querying system status, setting precision timers, or retaining cross-session personal preferences in local SQLite, Tavi acts as a true private companion.

---

## ⚖️ Why Tavi?

Tavi combines local conversation with Android actions and user-controlled personal data. Available capabilities and their limits are:

| Capability | Current behavior | Requirements or limits |
| :--- | :--- | :--- |
| Small request engine | Common commands and clarifications run in a bounded Kotlin router without model weights | Handles time, battery, network, alarms/timers, notes, documents, contacts, settings, and explicit message requests. Shared workflow gates check every action. |
| Local conversation | Gemma 4 E2B or Gemma 3 1B, with a deterministic fallback | Install verified weights and choose a compatible runtime; speed varies by device. |
| Offline voice | On-device recognition, local speech output, follow-up listening | Recognition requires Android 12+ and an installed on-device provider; language packs vary by device. Text remains available. |
| Personal memory | Relevant recall, provenance, corrections, deletion | Stored in private local SQLite; heuristic filters cannot guarantee every sensitive string is detected. |
| Document library | Text/PDF/image import, English/Kiswahili OCR, cited search | Local lexical search; OCR quality depends on the scan. Search covers imported documents and saved notes. |
| Reminders and routines | Recurrence, snooze, reboot restoration, editable routine schedules | Notification permission required; exact timing depends on Android alarm access. Scheduled routines send reminder notifications. |
| Android actions | Schema validation, confirmations, persistent execution receipts | Permissions and providers determine availability. Opening a system app is reported as a handoff. |
| Screen assistance | Local planning, visible node grounding, scoped resume and stop | Requires a working local model and accessibility; currently restricted to Settings, Chrome, and Firefox. |
| System assistant | Invocation panel with transient screen context | User must select Tavi as the default assistant. Default SMS/dialer roles are disabled. |

See [LIMITATIONS.md](LIMITATIONS.md) for runtime boundaries and [CHANGELOG.md](CHANGELOG.md) for the additions in this revision.

---

## 🏗️ Architecture Overview

```mermaid
graph TD
    User([User Text / Voice]) --> UI[Jetpack Compose UI & Navigation 3]
    UI --> Drawer[Multi-Session Conversation Drawer]
    UI --> VM[MainScreenViewModel]
    VM --> LocalRequests[Small Kotlin Request Engine - No Weights]
    LocalRequests -->|Typed commands| Checkpoint
    LocalRequests -->|Needs interpretation| Budget
    
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
        Dialog -->|Approved| Registry[60 Native Tool Registry]
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

## 🛠️ 60 Native Android System Tools

Tavi declares and executes **60 native Android tools across 15 functional categories** via the unified `AssistantTool` interface:

<!-- TOOL_TABLE_START -->

### Complete Native Tool Directory (60 On-Device Tools)

| Category | Tool Identifier | Consequential Gate | Purpose |
| :--- | :--- | :---: | :--- |
| **ACCESSIBILITY** | `accessibility_perform_action` | ⚡ Direct | Performs global system UI navigation actions like pressing back, going home, showing recents, opening notifications, or quick settings. |
| **ACCESSIBILITY** | `accessibility_read_screen` | ⚡ Direct | Inspects the visible text and UI elements currently displayed on the screen with strict password and privacy redactions. |
| **ACCESSIBILITY** | `screen_agent_resume` | 🔒 Required | Resume the exact paused screen action after explicit user confirmation; rejects expired or changed targets. |
| **ACCESSIBILITY** | `screen_agent_run` | 🔒 Required | Use a local model to navigate Settings, Chrome, or Firefox with exact visible nodes and individual confirmations. |
| **APPLICATIONS** | `app_launch` | ⚡ Direct | Launches an installed application by its name or package name. |
| **APPLICATIONS** | `app_list_launchable` | ⚡ Direct | Lists visible launchable applications installed on the device. |
| **APPLICATIONS** | `app_open_settings` | ⚡ Direct | Opens the Android system App Info settings page for Tavi or a specified app. |
| **APPLICATIONS** | `browser_open_url` | ⚡ Direct | Opens a web URL in the device's default web browser. |
| **APPLICATIONS** | `share_content` | ⚡ Direct | Shares text content using the Android system share sheet. |
| **CALENDAR** | `calendar_create_event` | ⚡ Direct | Creates a new calendar event with natural date/time resolution and conflict detection. |
| **CALENDAR** | `calendar_delete_event` | 🔒 Required | Deletes a calendar event by its event ID. This is a consequential action that requires user confirmation. |
| **CALENDAR** | `calendar_get_event` | ⚡ Direct | Inspects details of a single calendar event by eventId. |
| **CALENDAR** | `calendar_list_events` | ⚡ Direct | Lists or searches upcoming calendar events within an optional date range or query. |
| **CALENDAR** | `calendar_update_event` | ⚡ Direct | Updates an existing calendar event title, time, or location. |
| **CLOCK** | `alarm_create` | ⚡ Direct | Sets an alarm in the Android system Clock app. |
| **CLOCK** | `alarm_show` | ⚡ Direct | Opens the device Clock app to display all configured alarms. |
| **CLOCK** | `clock_query_time` | ⚡ Direct | Queries the current device time, timezone, UTC offset, and locale. |
| **CLOCK** | `date_calculation` | ⚡ Direct | Performs deterministic date math (e.g., days until a future date, adding days/weeks, timezone offset). |
| **CLOCK** | `reminder_create` | ⚡ Direct | Schedules an Android system reminder notification at a designated natural time. |
| **CLOCK** | `timer_set` | ⚡ Direct | Starts a countdown timer in the device Clock app. |
| **CONNECTIVITY** | `network_get_status` | ⚡ Direct | Inspects local network connectivity (Wi-Fi, cellular, metered status, offline state). |
| **CONTACTS** | `contacts_create` | ⚡ Direct | Creates a new contact with display name, phone number, and optional email. |
| **CONTACTS** | `contacts_resolve` | ⚡ Direct | Resolves a contact query. If multiple ambiguous contacts match, exposes the ambiguity instead of guessing. |
| **CONTACTS** | `contacts_search` | ⚡ Direct | Searches device contacts by name or phone number to retrieve contact names and phone numbers. |
| **DEVICE** | `device_get_status` | ⚡ Direct | Reads battery level, charging state, available storage, and system platform status. |
| **DEVICE** | `device_toggle_flashlight` | ⚡ Direct | Turns the device flashlight/torch on or off. |
| **DEVICE** | `settings_open` | ⚡ Direct | Opens specific Android system Settings panels (wifi, bluetooth, display, sound, notifications, battery, apps, date, storage). |
| **FILES** | `documents_search` | ⚡ Direct | Search imported local documents and saved notes; returns source and page citations. |
| **FILES** | `files_browse_document` | ⚡ Direct | Launches the Android Storage Access Framework (SAF) document picker. |
| **FILES** | `files_export_note` | ⚡ Direct | Exports a local note via the Android system share sheet. |
| **LOCATION** | `location_get_current` | ⚡ Direct | Reads device's current foreground location coordinates if permission is granted. |
| **LOCATION** | `maps_navigate` | ⚡ Direct | Opens maps or navigation to a destination or search query. |
| **MEDIA** | `camera_launch` | ⚡ Direct | Opens the device Camera application to take a photo. |
| **MEDIA** | `media_control` | ⚡ Direct | Sends media playback control commands (play_pause, next, previous). |
| **MEDIA** | `voice_speak` | ⚡ Direct | Speaks the provided text aloud using Android Text-To-Speech. |
| **NOTES** | `calculator` | ⚡ Direct | Evaluates deterministic mathematical expressions (e.g., '145 * 12', 'sqrt(144) + 2^4'). Safe and local. |
| **NOTES** | `clipboard_copy` | ⚡ Direct | Copies text to the Android system clipboard. |
| **NOTES** | `clipboard_read` | ⚡ Direct | Reads current text content from the Android system clipboard. |
| **NOTES** | `memory_clear_all` | 🔒 Required | Permanently clears all personal memories and notes. This is a consequential action requiring user confirmation. |
| **NOTES** | `memory_delete` | ⚡ Direct | Deletes a specific remembered item from local memory by key or ID. |
| **NOTES** | `memory_record_episode` | ⚡ Direct | Records a completed task, project decision, or workflow rationale as an episodic memory for future recall. |
| **NOTES** | `memory_retrieve` | ⚡ Direct | Retrieves relevant saved personal facts, preferences, or notes from local memory. |
| **NOTES** | `memory_save` | ⚡ Direct | Saves user-approved personal preferences, facts, or notes into persistent local memory. |
| **NOTES** | `note_create` | ⚡ Direct | Creates a persistent local note. |
| **NOTES** | `note_delete` | 🔒 Required | Deletes a saved note by ID. Consequential action requiring confirmation. |
| **NOTES** | `notes_list` | ⚡ Direct | Lists or searches locally saved notes. |
| **NOTES** | `reminder_cancel` | ⚡ Direct | Cancels or deletes a scheduled reminder by title or ID. |
| **NOTES** | `reminder_list` | ⚡ Direct | Lists all upcoming active reminders scheduled on device. |
| **NOTIFICATIONS** | `notifications_dismiss` | ⚡ Direct | Dismisses a notification by its key or clears notifications. |
| **NOTIFICATIONS** | `notifications_read_summary` | ⚡ Direct | Reads authorized recent notifications and summarizes active alerts or messages. |
| **RESEARCH** | `browser_open_page` | ⚡ Direct | Opens an accessible URL in the device browser so the user can interact directly. |
| **RESEARCH** | `browser_research_topic` | ⚡ Direct | Reads accessible web page content, extracts relevant text, and supports multi-step research. Halts if login or CAPTCHA is encountered. |
| **TELEPHONY** | `call_log_search` | ⚡ Direct | Searches recent call history records. |
| **TELEPHONY** | `messages_draft` | ⚡ Direct | Drafts a message reply and presents it for user inspection without sending. |
| **TELEPHONY** | `messages_get_send_status` | ⚡ Direct | Check Android SMS send and delivery callbacks for a previous dispatch ID. |
| **TELEPHONY** | `messages_search` | ⚡ Direct | Searches explicitly permitted local SMS messages by keyword or sender. |
| **TELEPHONY** | `messages_send_sms` | 🔒 Required | Sends an SMS message to a phone number. Requires explicit user confirmation before transmission. |
| **TELEPHONY** | `phone_call` | 🔒 Required | Initiates a direct phone call to a contact or number. Consequential action requiring explicit user confirmation. |
| **TELEPHONY** | `phone_dial` | ⚡ Direct | Opens the device dialer with the requested number prefilled. Does not place the call automatically. |
| **USAGE** | `usage_get_stats` | ⚡ Direct | Retrieves authorized app usage statistics, daily screen time, and recently used apps. |

<!-- TOOL_TABLE_END -->

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

`LocalRequestEngine` handles supported complete commands before model planning or loading. It preserves message/note text, rejects invalid alarm times and overflowing timer durations, and asks for missing recipients or message bodies. It never executes a tool directly. Gemma loads on demand for requests that need interpretation; opening the app or running a supported basic command does not preload neural weights.

Tavi implements a **Dual-Engine Architecture** managed through `InferenceRuntime`:

* **Primary Engine:** **Google AI Edge LiteRT-LM 0.18.1** (`com.google.ai.edge.litert:litert-lm-android:0.18.1`)
  * Dynamic Int4 FlatBuffer (`gemma-3-1b-it.litertlm`).
  * Direct JNI runtime with native Vulkan GPU / CPU pipeline acceleration.
  * KV-cache capacity: 2,048 tokens.
* **Fallback Engine:** **Google MediaPipe Tasks GenAI 0.10.27** (`com.google.mediapipe:tasks-genai:0.10.27`)
  * MediaPipe `.task` bundle compatibility.
* **Deterministic Action Core:**
  * Rule-based parsing when compatible model weights are unavailable or neural inference cannot run.

### Hardware Specifications & Measured Profiles
* **Target Inference Runtime:** Google AI Edge LiteRT-LM (Direct JNI / Vulkan GPU pipeline with CPU fallback)
* **Model Choices:**
  * **Gemma 4 E2B:** ~2.58 GB weights, 6+ GB RAM recommended (GPU Vulkan or ARM Cortex-A CPU)
  * **Gemma 3 1B IT:** ~558 MB pinned LiteRT-LM weights, 2+ GB RAM (lightweight budget profile)
* **Context KV-Cache Capacity:** 2,048 tokens native window
* **Memory Management:** Enforces `< 2,048 MB` RAM guard via `ModelManager.checkDeviceRamSupport()` and releases loaded models under memory pressure
* **Battery Efficiency:** On-demand execution with zero persistent wakelocks and zero background telemetry
* **Measured Benchmark Results:** Real device measurements (engine initialization, first response chunk, characters/second, sampled process PSS, battery percentage delta, thermals) are collected using `scripts/run_benchmark.sh` and stored in [`benchmarks/`](benchmarks/). Unmeasured hardware profiles are explicitly labeled as *"Not yet measured (run scripts/run_benchmark.sh on device)"*. Specification target numbers in `evaluation/` are marked with `# SPEC — see measured results in benchmarks/`.

---

## 💾 Persistent Personal Memory & Context Budget

* **Token-Aware Context Budgeting:** Managed by `ContextBudgetManager` within a strict 2,048-token hardware window:
  * Reserve `400 tokens` for generation; count the complete system instructions, current input, tool definitions, memories, and selected history.
  * Reject requests that exceed the input budget; retain recent complete exchanges and select relevant tools.
* **Conversation State Ledger:** Tracks confirmed facts, active task constraints, and referenced entities even when raw dialogue history is pruned.
* **Local SQLite Store (`tavi_local.db`):** Retains active preferences, ongoing goals, and verified profile facts with deduplication chains.
* **Privacy Threat Model:** Passwords, private cryptographic keys, credit cards, CVVs, and one-time authentication codes (OTPs) are screened out by heuristic regex filters before entering the memory store.

---

## 📥 In-App Model Acquisition

Tavi provides built-in model download and import controls in **Settings & Privacy**:

1. **Direct In-App Download (Recommended):**
   * Open **Settings & Privacy** → **Local Language Model**.
   * Tap **Download Gemma 3 1B (HTTPS)**.
   * Tavi streams a revision-pinned instruction-tuned artifact directly to sandboxed app storage with multi-hop redirect handling (Hugging Face CDN / AWS S3) and verifies SHA-256 integrity.
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

* **Training experiments:** The repository contains QLoRA training and export scripts. Their accuracy, latency improvements, and exported model compatibility require independent validation.
* **Google Colab Notebook:** Explore the training workflow:
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
* **Backup Filtering:** Android cloud backups and device transfers include shared preferences only; private databases and model/document files are omitted.
* **Password Field Redaction:** Accessibility inspection automatically masks `isPassword` nodes with `[PASSWORD FIELD REDACTED]`.
* **Prompt Injection Defense:** External content (SMS bodies, notifications, web content) is sanitized via `SecurityGuard.sanitizeExternalContent()` to strip adversarial markers.

---

## 🚀 Quickstart: Build & Run

### Prerequisites
* **Android Studio** compatible with the configured Android Gradle Plugin
* **Android SDK:** API 36 (Android 16), Minimum API 24 (Android 7.0)
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
For an APK targeting one device architecture, use `./gradlew assembleDebug -PtaviTargetAbi=arm64-v8a` (or another supported ABI). Omitting this option retains the normal architecture set.

Output artifact:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install to Connected Android Device
```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell "am start -n com.tavi.assistant/com.example.tavi.MainActivity"
```

---

## 🧪 Test Verification & Benchmarks

The current JVM suite contains 215 tests across 38 suites. Run validation with:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

CI compiles both app and instrumentation APKs. Lint errors fail builds. JVM tests verify routing, prompt budgets, memory, workflow checkpoints, tool validation, receipts, and safety policies; they do not establish real inference speed or Android provider behavior.

All 16 Android acceptance tests passed on the Android 14 / x86_64 cloud device, including real local inference, cited English/Kiswahili OCR, reminder notifications, persistence, and the small request engine's chat integration. See the [validation record](docs/validation/2026-11-07-android-cloud.md) for raw results and the remaining physical-device checks.

On an authorized Android device, run instrumentation tests and physical inference benchmarks separately:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.example.tavi.benchmark.DeviceBenchmark
bash scripts/run_benchmark.sh
```

The benchmark needs installed verified weights, a compatible runtime, ADB, and Python 3 for report validation. It exports fresh reports into [benchmarks/](benchmarks/); callback chunks and text token estimates are identified separately. Nine genuine neural runs passed in an Android 14 / x86_64 cloud container; their [raw report and measurements](benchmarks/README.md#cloud-validation-on-2026-11-07) are labeled as cloud results. Physical phone performance remains unmeasured for these additions.

---

## 📁 Repository Structure

```
Tavi/
├── app/
│   ├── src/main/java/com/example/tavi/
│   │   ├── engine/          # LiteRT-LM, MediaPipe, & ModelManager HTTP downloader
│   │   ├── tools/           # Native tools (Calendar, SMS, Device, Files...)
│   │   ├── context/         # Conversation State Ledger & ContextBudgetManager
│   │   ├── memory/          # SQLite PersistentMemoryManager & Privacy Rules
│   │   ├── workflow/        # Autonomous Multi-Step Execution & Planning
│   │   ├── roles/           # Android Assistant & System Role Managers
│   │   ├── accessibility/   # TaviAccessibilityService & Password Redaction
│   │   └── ui/              # Jetpack Compose UI (Navigation 3, Main, Settings)
│   ├── src/test/            # JVM regression tests
│   └── src/androidTest/     # Android persistence, UI, inference, and benchmark tests
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
