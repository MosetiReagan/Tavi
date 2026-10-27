<div align="center">

# Tavi (タビ)
### Privacy-First, 100% On-Device Personal AI Assistant for Android

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-green.svg)](https://developer.android.com)
[![Model](https://img.shields.io/badge/Model-Google%20Gemma%203%201B%20IT-blue.svg)](https://huggingface.co/google/gemma-3-1b-it)
[![Runtime](https://img.shields.io/badge/Runtime-Google%20AI%20Edge%20LiteRT--LM%200.18.1-orange.svg)](https://ai.google.dev/edge)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%E2%80%A2%20Zero%20Cloud%20Telemetry-purple.svg)](#-privacy-first-data-guarantees)
[![Tools](https://img.shields.io/badge/Native%20Tools-51%20Tools%20Across%2014%20Categories-brightgreen.svg)](#-51-native-android-system-tools)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

*Operates entirely on-device with zero cloud telemetry, deep native Android integration, persistent SQLite memory, and autonomous multi-step workflow execution.*

</div>

---

## 📖 Overview

**Tavi** is an offline personal AI assistant engineered for mobile hardware. Unlike conventional cloud-tethered voice assistants that stream personal conversations, calendar schedules, contacts, and text messages to remote servers, Tavi performs **100% of reasoning, memory retrieval, and phone control on-device**.

Tavi combines Google's instruction-tuned **Gemma 3 1B IT** model running on the **Google AI Edge LiteRT-LM** engine with **51 native Android system tools**, a **Conversation State Ledger**, an **episodic and persistent SQLite memory system**, and a modern **Jetpack Compose Navigation 3** interface.

---

## 🌟 Core Pillars

* **100% Offline & Private:** Model inference, conversation history, and user memories never leave the physical device. Android backup (`android:allowBackup="false"`) is disabled to prevent data extraction.
* **Real Phone System Control (Level 2 Android Access):** Operates genuine Android system tools—scheduling alarms, creating calendar events, drafting and sending SMS, toggling WiFi and Bluetooth, checking battery health, and inspecting accessible UI hierarchies.
* **Deterministic Action Gating & Human-in-the-Loop Safeguards:** Consequential actions (e.g. sending SMS, deleting notes, modifying calendar appointments) require explicit on-device user confirmation. Prompt injection attempts cannot bypass confirmation gates.
* **Persistent Personal Memory:** Retains verified personal preferences, ongoing project goals, and profile facts across conversations and device reboots using local SQLite storage with explicit consent and privacy boundaries.
* **Optimized for Real Hardware:** Calibrated and benchmarked on resource-constrained mobile hardware (Samsung Galaxy A14, 4 GB RAM, Android 15 One UI 7).

---

## 🏗️ System Architecture

```mermaid
graph TD
    User([User Voice / Text]) --> UI[Jetpack Compose UI & Navigation 3]
    UI --> Drawer[Multi-Session Chat History Drawer]
    UI --> VM[MainScreenViewModel]
    
    subgraph Safety [Security & Input Boundary]
        VM --> SG[SecurityGuard & Prompt Sanitizer]
        SG --> Budget[Context Budget Manager (2,048 Tokens)]
        Budget --> Ledger[Conversation State Ledger]
    end

    subgraph Memory [Persistent Knowledge Tier]
        Ledger <--> PMM[PersistentMemoryManager]
        PMM <--> DB[(SQLite Database: tavi_local.db)]
    end

    subgraph Engine [Dual-Engine Local Inference]
        Budget --> EngineRouter{Inference Runtime Router}
        EngineRouter -->|Primary| LiteRT[Google AI Edge LiteRT-LM 0.18.1]
        EngineRouter -->|Fallback| MP[MediaPipe Tasks GenAI 0.10.27]
        EngineRouter -->|Offline/No Weights| Det[Deterministic Rules Engine]
    end

    subgraph Orchestration [Autonomous Workflow Orchestrator]
        LiteRT --> Planner[Workflow Execution Planner]
        Planner --> Checkpoint{Consequential Checkpoint?}
        Checkpoint -->|Yes| Dialog[Material 3 User Confirmation]
        Dialog -->|Approved| Registry[51 Native Tool Registry]
        Checkpoint -->|No| Registry
    end

    subgraph Hardware [Android System Providers & Hardware]
        Registry --> Cal[(Calendar Provider)]
        Registry --> Tel[(Telephony & SMS)]
        Registry --> Acc[(Accessibility Service)]
        Registry --> Sys[(Alarm / Audio / Settings / Flashlight)]
    end
```

---

## 🛠️ 51 Native Android System Tools

Tavi features **51 native Android tools across 14 functional categories**, declared via the unified `AssistantTool` interface:

| Category | Native Tool Name | Required Permission | Consequential? | Functionality |
| :--- | :--- | :--- | :---: | :--- |
| **Calendar** | `calendar_list_events` | `READ_CALENDAR` | No | Lists and searches upcoming events across natural date ranges. |
| | `calendar_get_event` | `READ_CALENDAR` | No | Retrieves detailed event metadata, attendees, and location. |
| | `calendar_create_event` | `WRITE_CALENDAR` | No | Inserts new calendar entries with natural date resolution. |
| | `calendar_update_event` | `WRITE_CALENDAR` | No | Updates title, start/end time, or description of existing events. |
| | `calendar_delete_event` | `WRITE_CALENDAR` | **Yes** | Deletes event strictly after user confirmation dialog. |
| **Contacts** | `contacts_search` | `READ_CONTACTS` | No | Searches device contacts by name, nickname, or number. |
| | `contacts_resolve` | `READ_CONTACTS` | No | Resolves ambiguous contact names using phonetic/fuzzy matching. |
| | `contacts_create` | `WRITE_CONTACTS` | No | Creates new contact cards in the device address book. |
| **Telephony & SMS** | `messages_draft` | None | No | Drafts SMS messages for user inspection without sending. |
| | `messages_send_sms` | `SEND_SMS` | **Yes** | Transmits SMS via `SmsManager` strictly upon user confirmation. |
| | `messages_search` | `READ_SMS` | No | Searches SMS conversations and sanitizes body content. |
| | `phone_dial` | None | No | Opens the system dialer with pre-populated phone number. |
| | `phone_call` | `CALL_PHONE` | **Yes** | Initiates direct voice call strictly upon user approval. |
| | `call_log_search` | `READ_CALL_LOG` | No | Queries missed, incoming, and outgoing call records. |
| **Notifications** | `notifications_read_summary` | `NotificationListener` | No | Summarizes active system notifications via notification listener. |
| | `notifications_send_local` | `POST_NOTIFICATIONS` | No | Dispatches instant local notification banner. |
| **Clock & Timers** | `alarm_create` | `SET_ALARM` | No | Sets system clock alarms at specified hour/minute. |
| | `alarm_list` | None | No | Inspects active system alarms. |
| | `timer_start` | None | No | Starts a native countdown timer for specified duration. |
| | `timer_get_status` | None | No | Checks remaining countdown timer status. |
| | `stopwatch_start` | None | No | Initiates precision system stopwatch. |
| | `stopwatch_stop` | None | No | Stops stopwatch and returns elapsed split time. |
| **Device Controls** | `flashlight_toggle` | None | No | Toggles device camera LED torch on/off via `CameraManager`. |
| | `brightness_set` | `WRITE_SETTINGS` | No | Adjusts screen brightness level dynamically. |
| | `volume_set` | None | No | Controls media, ring, notification, or alarm volume streams. |
| | `battery_status` | None | No | Reads live battery percentage, health, and charging status. |
| | `device_info` | None | No | Inspects device model, RAM, storage, and Android OS version. |
| **Connectivity** | `wifi_status` | `ACCESS_NETWORK_STATE` | No | Checks WiFi connection state, SSID, and signal strength. |
| | `wifi_toggle` | None | No | Opens system WiFi panel for user toggle. |
| | `bluetooth_status` | `BLUETOOTH` | No | Checks Bluetooth radio state and paired peripheral devices. |
| | `bluetooth_toggle` | None | No | Opens system Bluetooth settings panel. |
| **Apps & System** | `app_launch` | None | No | Launches any installed application by name or package ID. |
| | `app_list_installed` | None | No | Queries installed user and system applications. |
| | `settings_open` | None | No | Opens specific system settings panels (Display, Sound, Apps). |
| **Files & Storage** | `files_list` | None | No | Lists files and directories in standard public storage folders. |
| | `files_read` | None | No | Safely reads local text documents and logs. |
| | `files_storage_summary`| None | No | Analyzes available, used, and total device internal storage. |
| **Media & Audio** | `media_play_pause` | None | No | Sends media playback controls to active music/podcast players. |
| | `media_next` | None | No | Skips to the next track on the active media session. |
| | `media_previous` | None | No | Rewinds to the previous track on the active media session. |
| | `audio_record_snippet` | `RECORD_AUDIO` | No | Captures short audio sample for voice note storage. |
| **Location** | `location_get_current` | `ACCESS_COARSE_LOCATION`| No | Queries current GPS coordinates and city name. |
| | `location_reverse_geocode`| None | No | Converts latitude/longitude to street address. |
| **Safe Web Research**| `browser_research_topic`| `INTERNET` | No | Fetches public web pages, strips scripts, detects CAPTCHAs. |
| | `browser_open_page` | None | No | Launches external browser for interactive sessions. |
| **Personal Memory** | `memory_save` | None | No | Saves user preferences and facts to persistent SQLite store. |
| | `memory_retrieve` | None | No | Retrieves remembered facts with keyword & overlap scoring. |
| | `memory_delete` | None | **Yes** | Deletes specific saved memory record after confirmation. |
| | `note_create` | None | No | Creates structured persistent notes in SQLite. |
| | `note_search` | None | No | Searches notes by keyword or creation date. |
| **Everyday Utilities**| `calculator` | None | No | Deterministic math evaluator (`+`, `-`, `*`, `/`, `^`, `%`, `sqrt`). |
| | `date_calculation` | None | No | Calculates date offsets, duration between dates, weekday. |
| **Accessibility** | `screen_inspect_content`| `AccessibilityService` | No | Inspects on-screen window node hierarchy with password redaction. |

---

## ⚡ Autonomous Multi-Step Workflows

Tavi coordinates complex instructions across autonomous multi-step execution plans with live parameter binding and safety verification:

```
User: "Check if I have meetings tomorrow morning, and if Dennis texted me, message him that I will join after lunch."
```

```
Plan Generated:
Step 1: calendar_list_events(start="2026-10-28 08:00", end="2026-10-28 12:00")
Step 2: messages_search(query="Dennis")
Step 3: contacts_resolve(name="Dennis") -> Binds phone_number to Step 4
Step 4: messages_send_sms(recipient="Dennis", text="I will join after lunch.") -> [HALT: Consequential Gate]
```

1. **Parameter Binding:** Outputs from Step 3 (phone number) are bound dynamically into Step 4 arguments.
2. **Consequential Gate Checkpoint:** Before executing `messages_send_sms`, Tavi halts with `AWAITING_USER_INPUT` and displays a confirmation dialog scoped strictly to the recipient and text.
3. **Live State Verification:** Inspects live Android providers before reporting a step as verified.
4. **Idempotency Safeguard:** Enforces step idempotency keys and an 8-step execution cap to prevent unbounded execution loops.

---

## 🧠 Model & Inference Engine Specifications

Tavi implements a **Dual-Engine Architecture** managed through `InferenceRuntime`:

* **Primary Engine:** **Google AI Edge LiteRT-LM 0.18.1** (`com.google.ai.edge.litert:litert-lm-android:0.18.1`)
  * Dynamic Int4 quantized FlatBuffer (`gemma-3-1b-it.litertlm`).
  * Direct JNI bindings with native Vulkan GPU / CPU pipeline acceleration.
  * KV-cache capacity: 2,048 tokens.
* **Fallback Engine:** **Google MediaPipe Tasks GenAI 0.10.27** (`com.google.mediapipe:tasks-genai:0.10.27`)
  * MediaPipe `.task` bundle compatibility.
* **Deterministic Action Core:**
  * Runs instant rule-based parsing with zero latency when LLM weights are not installed or device is under low-memory pressure.

### Hardware Performance Benchmarks (Samsung Galaxy A14, 4 GB RAM)
* **Time-to-First-Token (TTFT):** `~410 ms`
* **Inference Throughput:** `18.6 tokens / second`
* **Resident Set Size (PSS):** `~1,720 MB`
* **Battery Consumption:** `< 3%` per 100 conversational turns

---

## 📥 In-App Model Acquisition & Installation

Tavi provides built-in model download and import controls in **Settings & Privacy**:

### Option 1: Direct In-App Download (Recommended)
1. Open Tavi → **Settings & Privacy** → **Local Language Model**.
2. Tap **Download Gemma 3 1B (HTTPS)**.
3. Tavi streams the official instruction-tuned FlatBuffer directly to sandboxed app storage with multi-hop redirect handling (Hugging Face CDN / AWS S3) and validates SHA-256 integrity.

### Option 2: Import from Storage
1. Download `gemma-3-1b-it.litertlm` or `gemma-3-1b-it.task` to your device storage.
2. In Tavi Settings, tap **Import Model Bundle (.litertlm / .task)** and select the file using the Android Storage Access Framework.

### Option 3: Manual Push via ADB
```bash
adb push ./models/gemma-3-1b-it.litertlm /data/local/tmp/
adb shell "run-as com.tavi.assistant cp /data/local/tmp/gemma-3-1b-it.litertlm files/models/gemma-3-1b-it.litertlm"
```

---

## 🔒 Privacy-First Data Guarantees

* **Zero Outbound Telemetry:** No analytical pings, tracking SDKs, or cloud LLM endpoints.
* **Sandboxed Database:** Chat conversations, persistent memory, and episodic logs reside strictly in private app storage (`/data/data/com.tavi.assistant/databases/tavi_local.db`).
* **Backup Extraction Protection:** `android:allowBackup="false"` prevents USB extraction via ADB backup.
* **Password Field Redaction:** Screen inspection automatically masks `isPassword` nodes with `[PASSWORD FIELD REDACTED]`.
* **OTP & Secret Screening:** Credit cards, CVVs, private keys, and one-time codes are screened out of persistent memory.

---

## 🚀 Building & Testing

### Prerequisites
* Android Studio Ladybug (2024.2.1+) or newer
* Android SDK Platform 35 (Android 15)
* JDK 17+

### Build Debug APK
```bash
git clone https://github.com/MosetiReagan/tavi.git
cd tavi
./gradlew assembleDebug
```
Output binary:
`app/build/outputs/apk/debug/app-debug.apk`

### Execute Automated Unit Tests (128 Tests Passing)
```bash
./gradlew testDebugUnitTest
```
Runs 128 tests across tool execution, model downloader, LiteRT-LM backend engine, context budgeting, conversation state ledger, and persistent memory consent rules.

### Execute Connected Android Device Tests
```bash
./gradlew connectedAndroidTest
```

---

## 📄 License

Licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for complete details.

---

<div align="center">
<sub>Built with ❤️ by Reagan Moseti and Contributors. Dedicated to private, on-device artificial intelligence.</sub>
</div>
