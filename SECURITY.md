# Security Policy

## Reporting Security Vulnerabilities

We take the security and privacy of Tavi very seriously. Because Tavi integrates directly with sensitive Android system APIs (Calendar, Contacts, SMS, Notifications, Accessibility, Storage) and runs an instruction-tuned LLM on-device, our security model is designed with defense-in-depth principles.

If you believe you have found a security vulnerability (such as prompt injection escaping into unauthorized tool execution, unauthorized memory extraction, bypass of consequential confirmation dialogs, or sensitive data leakage), please report it responsibly.

**Please do NOT file public GitHub issues for security vulnerabilities.**

Instead, report your findings via email to:
- **Email:** `mosetireagan78@gmail.com`
- **Subject:** `[SECURITY] Tavi Vulnerability Report`

Include:
- Detailed steps to reproduce the vulnerability.
- Proof of concept input prompt or adversarial payload.
- Affected device model, Android OS version, and app commit hash.
- Impact assessment.

We will acknowledge receipt within 48 hours and work with you on a coordinated fix and release timeline.

---

## Security Architecture & Defense Boundaries

### 1. Human-in-the-Loop Consequential Gating
Actions that modify external state or permanently alter user data (`messages_send_sms`, `calendar_delete_event`, `phone_call`, `memory_delete`, `note_delete`) are marked with `isConsequential = true`.
- Consequential tools **cannot be invoked autonomously** by model completions or upstream tools.
- The execution pipeline intercepts consequential invocations and requires explicit user confirmation via an Android Material 3 confirmation dialog.
- Caller-supplied flags like `__user_confirmed` are strictly purged during argument parsing at the host execution boundary.

### 2. Prompt Injection Defense
Content from untrusted external sources (SMS messages, notification bodies, web pages, clipboard text) is sanitized via `SecurityGuard.sanitizeExternalContent()`.
- Common jailbreak patterns (`"Ignore previous instructions"`, `"System prompt override"`, `"<start_of_turn>model"`) are stripped or neutralized before prompt assembly.

### 3. Screen Inspection Boundaries
When Tavi Accessibility Service inspects the on-screen view hierarchy:
- All `isPassword` nodes are automatically masked with `[PASSWORD FIELD REDACTED]`.
- Credit card numbers, CVVs, and one-time authentication codes (OTPs) are screened out by heuristic regex filters before reaching model context.

### 4. Sandboxed Storage & Backup Protection
- Android backup is disabled (`android:allowBackup="false"` in `AndroidManifest.xml`) to prevent extraction of the local SQLite database via ADB backup or device clone tools.
- Model weights, chat history, and persistent memory records reside strictly in app-private storage (`/data/data/com.tavi.assistant/files/`).
