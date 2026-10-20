# Tavi — Level 2 Android System Access & Role Audit

**Audit Date:** October 20, 2026  
**Audited Target Platform:** Android 15 (API Level 35, UpsideDownCake/VanillaIceCream)  
**Reference Physical Device:** Samsung Galaxy A14 (`SM-A145F`, One UI 7 / Android 15, Exynos 850)  
**Security Model:** 100% Local-First On-Device Processing, Zero Remote LLM Telemetry, Explicit User Activation  

---

## 1. Executive Summary & Audit Purpose

This audit establishes the pre-implementation baseline, security requirements, and Android platform constraints for **Level 2 Android System Access** in Tavi. While Level 1 integrations connect Tavi to standard runtime permissions (Calendar, Contacts, SMS/Calls, Camera, Location, Storage Access Framework), Level 2 elevates Tavi into a proactive, system-integrated assistant capable of:
1. **Inspecting on-screen content and performing system navigation** via an Android `AccessibilityService`.
2. **Reading app usage and screen-time breakdowns** via `UsageStatsManager`.
3. **Handling incoming and outgoing communications as default system roles** (Assistant, SMS, Phone/Dialer).
4. **Delivering contextual alerts and overlay assistance** via `NotificationListenerService` and `SYSTEM_ALERT_WINDOW`.
5. **Managing precise background execution** via `AlarmManager` exact alarms and battery optimization exemptions.

This audit evaluates each capability against Android 11–15 security constraints, Google Play Store distribution policies, Samsung One UI OEM restrictions, and strict on-device privacy boundaries.

---

## 2. Pre-Implementation Baseline & Gap Analysis

Prior to this upgrade, Tavi possessed 48 native tools spanning 12 categories, but lacked higher-privilege system integration:

| System Capability | Baseline Status | Identified Architecture Gap | Risk / Platform Challenge |
| :--- | :---: | :--- | :--- |
| **Notification Listener** | Implemented | Basic text reading in buffer; needed full active notification inspection & dismissal. | Low risk; requires explicit settings toggle. |
| **Usage Stats (`PACKAGE_USAGE_STATS`)** | **Missing** | No `UsageStatsManager` queries, no tools for screen time, no daily aggregation. | App-ops protected permission; requires settings intent. |
| **Accessibility Service** | **Missing** | No service declaration, no window tree traversal, no global navigation actions (`back`, `home`, `recents`). | **CRITICAL RISK:** Potential exposure of passwords, OTPs, or arbitrary touch injection if unconstrained. |
| **Default Assistant Role** | **Missing** | No `VoiceInteractionService`, no session service, no `android.intent.action.ASSIST` filter. | Requires complex multi-service manifest structure and `RoleManager` integration. |
| **Default SMS Role** | **Missing** | Missing `SMS_DELIVER` receiver, `WAP_PUSH_DELIVER` receiver, and `RESPOND_VIA_MESSAGE` service required by Android OS for default SMS eligibility. | Android OS will refuse role assignment if any required receiver/service is absent from the APK. |
| **Default Dialer Role** | **Missing** | No `InCallService` declared with `BIND_INCALL_SERVICE`. | Android Telecom framework requires `InCallService` with UI metadata. |
| **Overlay Window (`SYSTEM_ALERT_WINDOW`)** | **Missing** | No permission declaration or overlay manager helper. | Requires `Settings.canDrawOverlays` check and package-specific settings intent. |
| **Broad Storage (`MANAGE_EXTERNAL_STORAGE`)** | **Assessed** | Tavi relied solely on SAF. Broad storage access was not analyzed against distribution policies. | High Play Store policy scrutiny; must preserve SAF as privacy-preserving default. |
| **Exact Alarms & Doze Exemption** | Partial | Exact alarms checked in permission coordinator, but missing unified role manager UI card and Doze exemption flow. | Battery optimization exemption should only be requested on an opt-in basis for essential background tasks. |

---

## 3. Detailed Capability & Security Analysis

### 3.1 Accessibility Service (`AccessibilityService`)
* **Objective:** Enable Tavi to read non-sensitive on-screen content for situational queries (e.g., "What's on my screen?") and execute standard global navigation actions (Back, Home, Recents, Pull Down Notifications, Quick Settings).
* **Strict Security Boundaries:**
  1. **Password Redaction:** The traversal engine MUST inspect `AccessibilityNodeInfo.isPassword`. If `true`, the text must NEVER be retrieved or logged; it is replaced with `"[PASSWORD FIELD REDACTED]"`.
  2. **Pattern Masking:** All extracted text must pass through regex sanitizers:
     - Payment card numbers (`13-19 digits`): Masked to `"[CARD_REDACTED]"`.
     - Verification codes / OTPs: Standalone numeric sequences in security contexts are masked.
  3. **Traversal Bounds:** Safe traversal enforces a recursion depth limit (15) and maximum node count (150) to prevent UI lockups or memory exhaustion on deeply nested web views.
  4. **No Automated Keystroke Logging or Background Exfiltration:** Tavi runs 100% on-device; extracted screen data is evaluated in memory and never written to network sockets or unencrypted logs.
  5. **Explicit Activation:** Cannot be enabled programmatically; must guide the user directly to `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

### 3.2 Usage Access (`UsageStatsManager`)
* **Objective:** Allow users to ask "How much did I use YouTube today?" or "What is my screen time?"
* **Android 11–15 Constraints:**
  - Requires `android.permission.PACKAGE_USAGE_STATS` declared in `AndroidManifest.xml` with `tools:ignore="ProtectedPermissions"`.
  - Permission status verified via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS)`.
  - Android limits query frequency and aggregates history in `INTERVAL_DAILY`.
* **Privacy Boundary:** Aggregated statistics are read-only and used solely to construct local assistant summaries. No per-app keystroke or content data is available through this API.

### 3.3 Default Digital Assistant Role (`RoleManager.ROLE_ASSISTANT`)
* **Objective:** Seamless invocation via system gestures (long-press power button, swipe from bottom corners) and assist intents.
* **Component Architecture:**
  1. `TaviVoiceInteractionService` extending `VoiceInteractionService` with permission `android.permission.BIND_VOICE_INTERACTION`.
  2. `TaviVoiceInteractionSessionService` managing `VoiceInteractionSession`.
  3. `TaviRecognitionService` extending `RecognitionService`.
  4. `MainActivity` intent filter with `android.intent.action.ASSIST` and category `DEFAULT`.
* **Role Verification:** Query `RoleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)`. If available, dispatch `roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)`.

### 3.4 Default SMS App Role (`RoleManager.ROLE_SMS`)
* **Objective:** Allow Tavi to manage cellular messages directly and process incoming texts.
* **Android OS Mandatory Structural Requirements:**
  - BroadcastReceiver with `android.permission.BROADCAST_SMS` handling `android.provider.Telephony.SMS_DELIVER`.
  - BroadcastReceiver with `android.permission.BROADCAST_WAP_PUSH` handling `android.provider.Telephony.WAP_PUSH_DELIVER`.
  - Service with `android.permission.SEND_RESPOND_VIA_MESSAGE` handling `android.intent.action.RESPOND_VIA_MESSAGE`.
  - Activity with `android.intent.action.SENDTO` for `smsto:`, `sms:`, `mms:`, `mmsto:`.
* **Consequential Action Safeguard:** Even if Tavi holds the default SMS role, sending outgoing SMS messages from natural language MUST trigger the native user confirmation dialogue. Model-generated internal flags (`__user_confirmed`) are strictly rejected.

### 3.5 Default Phone / Dialer Role (`RoleManager.ROLE_DIALER`)
* **Objective:** Direct telephony call management.
* **Requirements:**
  - Service extending `android.telecom.InCallService` with `android.permission.BIND_INCALL_SERVICE`.
  - Intent filters for `android.intent.action.DIAL` and `android.intent.action.VIEW` for scheme `tel:`.
* **Safeguards:** Direct call placement (`CALL_PHONE`) remains gated behind explicit user confirmation; `ACTION_DIAL` is used for pre-filling without confirmation.

### 3.6 Storage Access Policy: Storage Access Framework vs `MANAGE_EXTERNAL_STORAGE`
* **Assessment:** Android 11+ introduced `MANAGE_EXTERNAL_STORAGE` (All-Files Access). However, Google Play Store distribution policies strictly restrict this permission to file managers, antivirus tools, and device backup utilities.
* **Architecture Decision:** **Tavi maintains Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`) as the safe, privacy-preserving default.** All-Files access is implemented as an optional special access card for advanced users, with transparent disclosure that SAF is recommended.

### 3.7 Background Scheduling & Battery Optimization Exemption
* **Exact Alarms:** Android 12+ requires `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`. Tavi uses `AlarmManager.canScheduleExactAlarms()` to dynamically detect eligibility.
* **Battery Optimization:** Android Doze mode defers background execution unless exempted via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Tavi will NOT prompt for this permission during onboarding; it is presented solely as a user-directed opt-in for continuous offline routines.

---

## 4. OEM & Android 15 Platform Constraints (Samsung One UI Focus)

Testing on Samsung Galaxy devices (One UI 6/7 on Android 14/15) reveals specific vendor behaviors:
1. **Accessibility Auto-Disable:** Samsung One UI includes an "Unused apps" service watchdog that disables accessibility services if the application is put into deep sleep. Battery optimization exemption prevents premature service teardown.
2. **Default Role Sheets:** On One UI 7, `RoleManager.createRequestRoleIntent()` opens a system dialog with Samsung's customized confirmation modal. Fallback to `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` is required if Samsung knox or work profiles suppress direct role dialogs.
3. **Usage Access Screen:** Samsung settings group usage access under *Security & Privacy > More privacy settings > Usage access*. Launching `Settings.ACTION_USAGE_ACCESS_SETTINGS` with package URI highlights Tavi directly.

---

## 5. Audit Conclusion & Implementation Recommendations

1. Implement all missing system components (`TaviAccessibilityService`, `TaviVoiceInteractionService`, SMS receivers, `TaviInCallService`) with declared permissions and configurations.
2. Enforce strict privacy redactions at the accessibility node traversal layer before any string reaches memory or tool outputs.
3. Upgrade `CapabilitiesScreen.kt` with a live, two-section interface (System Roles & Special Access vs Standard Capabilities) featuring real-time status badges and `LifecycleEventObserver` auto-refresh.
4. Verify with automated unit tests covering accessibility password redaction, card masking, role helper logic, and deterministic routing.
