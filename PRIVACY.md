# 🛡️ Tavi Privacy Policy & Google Play Data Safety Declaration

**Last Updated:** October 2026  
**Application:** Tavi Assistant (`com.tavi.assistant`)  
**License:** Apache 2.0  

Tavi is built from the ground up as a **100% private, on-device AI assistant**. Your personal data, conversations, device state, and personal knowledge graph never leave your device.

---

## 📊 Google Play Data Safety Declaration

| Data Safety Field | Declared Status | Details |
| :--- | :--- | :--- |
| **Data Collected** | **NONE** | Tavi does not collect, track, or harvest any personal data. |
| **Data Shared** | **NONE** | Zero data is shared with third parties, advertising networks, or analytics providers. |
| **Network Inference** | **0% (Disabled)** | Neural language model inference occurs exclusively on-device via LiteRT-LM / MediaPipe GenAI runtimes. |
| **Cloud Backup Exclusions** | **Excluded** | `tavi.db` (containing chat history, ledger state, and personal memory) is explicitly excluded from Android Cloud Backups (`android:fullBackupContent` and `android:dataExtractionRules`). |
| **Model Weight Acquisition** | **HTTPS + SHA-256** | When downloaded by the user, neural model weights (`.litertlm` / `.task`) are retrieved via HTTPS directly from official distribution repositories and verified against cryptographic SHA-256 checksums before loading. |
| **Ephemeral Processing** | **On-Device Only** | Camera frames, screen trees, voice audio, and clipboard contents are processed in memory and never persisted or transmitted. |

---

## 🔒 On-Device Data Storage & Security

1. **Local SQLite Storage (`tavi.db` / `tavi_local.db`)**:
   - Retained only in private app internal storage (`/data/data/com.tavi.assistant/databases/`).
   - Stores user preferences, conversation turns, and verified personal facts.
   - Screened by `SecurityGuard` heuristic sanitizers before ingestion: passwords, private keys, credit cards (13–19 digit sequences), and one-time passcodes (OTPs) are stripped before storage.
   - Users can inspect, delete individual facts, or wipe the entire database at any time via **Settings & Privacy** → **Persistent Personal Memory**.

2. **Neural Weights Storage**:
   - Stored in the app's internal files directory (`/data/data/com.tavi.assistant/files/models/`).
   - Accessible solely by Tavi's sandboxed UID.

3. **Accessibility & Screen Agent**:
   - `TaviAccessibilityService` and `ScreenAgentService` inspect UI hierarchies on request to perform tasks.
   - Password fields (`node.isPassword == true`) are strictly redacted (`[PASSWORD REDACTED]`).
   - Banking applications, cryptocurrency wallets, password managers, and payment flows are blacklisted from agent manipulation.
   - Consequential actions (such as sending messages, deleting data, or making purchases) always require explicit human confirmation.

4. **Speech & Audio**:
   - Speech recognition utilizes the on-device system recognizer when available.
   - Audio recordings are processed in real-time buffers and never saved to persistent storage.

---

## 🤝 Third-Party Services & Telemetry

* **Analytics:** 0 telemetry SDKs (No Firebase Analytics, No Google Analytics, No Mixpanel, No Sentry).
* **Crash Reporting:** 0 remote crash loggers. All logs are standard Android system Logcat, visible only to the device owner via ADB.
* **Network Permissions:** The `android.permission.INTERNET` permission is utilized solely for:
  1. Optional user-initiated model weight downloads.
  2. The opt-in `browser_research` web search tool when specifically requested by the user.

---

## 📬 Contact & Open Source Auditing

Tavi is open-source software under the Apache License 2.0. The complete source code, reproducible build instructions, and security test harnesses are publicly available for independent audit on GitHub.
