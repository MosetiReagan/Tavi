# 🛡️ Tavi Privacy Policy & Google Play Data Safety Declaration

**Last Updated:** November 2026
**Application:** Tavi Assistant (`com.tavi.assistant`)  
**License:** Apache 2.0  

Tavi performs inference, memory retrieval, and document processing on your device without uploading them to an AI service. User-requested SMS, sharing, browser, and calling actions use Android providers or other applications and can send the content you approve.

---

## 📊 Google Play Data Safety Declaration

| Data Safety Field | Declared Status | Details |
| :--- | :--- | :--- |
| **Data Collected** | **NONE** | Tavi does not collect, track, or harvest any personal data. |
| **Telemetry Data Shared** | **NONE** | No analytics or advertising data is sent by Tavi. User-requested communication and sharing actions use their respective services. |
| **Network Inference** | **0% (Disabled)** | Neural language model inference occurs exclusively on-device via LiteRT-LM / MediaPipe GenAI runtimes. |
| **Cloud Backup Exclusions** | **Excluded** | `tavi_local.db`, `tavi_documents.db`, and internal files are excluded from Android backups. |
| **Model Weight Acquisition** | **HTTPS + SHA-256** | When downloaded by the user, neural model weights (`.litertlm` / `.task`) are retrieved via HTTPS from configured distribution repositories and verified against cryptographic SHA-256 checksums before loading. |
| **Local Processing and Retention** | **On-Device** | Raw speech audio and assistant screenshots are not saved. Imported document text, conversation/tool evidence, reminders, routines, and execution receipts are retained locally. |

---

## 🔒 On-Device Data Storage & Security

1. **Local SQLite Storage (`tavi_local.db` and `tavi_documents.db`)**:
   - Retained only in private app internal storage (`/data/data/com.tavi.assistant/databases/`).
   - `tavi_local.db` stores conversation turns and tool evidence, personal facts, routine/reminder schedules, workflow checkpoints, execution receipts, and SMS dispatch status. `tavi_documents.db` stores imported document text and citations.
   - Personal memory and screen input use heuristic redaction filters. These filters do not guarantee detection of every sensitive string, and imported documents may contain private text.
   - Users can inspect and delete personal memories, documents, routines, and conversations through their respective screens. Clearing personal memory does not erase every other database table.

2. **Neural Weights Storage**:
   - Stored in the app's internal files directory (`/data/data/com.tavi.assistant/files/models/`).
   - Accessible solely by Tavi's sandboxed UID.

3. **Accessibility & Screen Agent**:
   - `TaviAccessibilityService` and `ScreenAgentService` inspect UI hierarchies on request to perform tasks.
   - Password fields (`node.isPassword == true`) are strictly redacted (`[PASSWORD REDACTED]`).
   - Banking applications, cryptocurrency wallets, password managers, and payment flows are blacklisted from agent manipulation.
   - Consequential actions (such as sending messages, deleting data, or making purchases) always require explicit human confirmation.
   - Screen automation is restricted to Settings, Chrome, and Firefox. Paused action targets are stored locally for scoped resumption.
   - Assistant invocation context stays in memory, expires after two minutes, and is consumed by one conversational turn. A generated response may quote relevant screen text in saved chat history.

4. **Speech & Audio**:
   - Speech recognition uses Android's on-device recognizer on Android 12+ with an installed provider. Recognition is unavailable otherwise; Tavi does not substitute a cloud recognizer.
   - Audio recordings are processed in real-time buffers and never saved to persistent storage.
   - Speech output selects installed voices that do not require a network connection. Missing language packs leave text input/output available.

5. **Imported Documents**:
   - English and Kiswahili OCR data are bundled in the app. OCR runs locally.
   - Imported source files are copied temporarily for decoding and removed when import finishes. Extracted text and page references remain in the document library until deletion.

---

## 🤝 Third-Party Services & Telemetry

* **Analytics:** 0 telemetry SDKs (No Firebase Analytics, No Google Analytics, No Mixpanel, No Sentry).
* **Crash Reporting:** 0 remote crash loggers. All logs are standard Android system Logcat, visible only to the device owner via ADB.
* **Network Permissions:** The `android.permission.INTERNET` permission is utilized solely for:
  1. Optional user-initiated model weight downloads.
  2. The opt-in `browser_research_topic` tool when specifically requested by the user; the visited website receives the request and usual connection metadata.

---

## 📬 Contact & Open Source Auditing

Tavi is open-source software under the Apache License 2.0. The complete source code, reproducible build instructions, and security test harnesses are publicly available for independent audit on GitHub.
