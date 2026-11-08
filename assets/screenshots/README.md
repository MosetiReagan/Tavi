# Tavi screenshots

The README uses 25 unedited PNG captures of the running app, covering conversation, local tools, memory, notes, documents, speech, routines, and Android access. It shows demonstrated UI behavior; screenshots alone do not establish speech accuracy, device performance, or a comparison against other assistants.

## Current gallery — 8 November 2026

- App source: feature revision `04b41ea` (documentation edits do not change the APK).
- App: `com.tavi.assistant`, debug, x86_64.
- APK SHA-256: `21bc642b2f7ed15eaf7ba5d21802f18af322304309d1ca607a793782b38452fb`.
- Device: Android 14 / API 34, redroid cloud Android container.
- Display: 1080 × 2400 pixels, density 440 dpi, default font scale, light theme. The cloud display was configured for portrait documentation capture.
- Capture: `adb exec-out screencap -p`, with UI navigation through ADB. No compositing, AI generation, replacement text, or image cropping was applied.
- Content: authored demonstration conversations and the included [household reserve text](demo-household-reserve.txt), [packing-list image](demo-weekend-packing.png), and [two-page budget PDF](demo-weekly-budget.pdf). No real contact, personal document, or delivered message appears.
- Installed optional packs: Whisper English, Omnilingual Kiswahili, Piper English/Kiswahili, and multilingual E5 Small. Sources and terms are in [local model packs](../../docs/LOCAL_MODEL_PACKS.md).

### Conversation and everyday requests

| Screenshot | What it shows | How to reproduce |
| :--- | :--- | :--- |
| [08 — Start a conversation](08_current_welcome.png) | Action starters, local model status, and text or voice input. | Open Tavi and start a new chat. |
| [14 — Ask a local model](14_local_conversation.png) | A local Gemma response to an open-ended question. | Start a new chat and ask `Explain photosynthesis in two short sentences.` with the verified Gemma 3 model installed. |
| [32 — Keep separate conversations](32_conversation_history.png) | Switch between saved chats and start a fresh session. | After creating the demonstration chats, select Chat History to view the saved conversations. |
| [15 — Calculate on the device](15_local_calculator.png) | The request engine evaluates arithmetic through a native tool. | Enter `Calculate 145 * 12` in a new chat. |
| [16 — Use Kiswahili commands](16_kiswahili_clock.png) | A Kiswahili clock request reaches the device clock tool. | Enter `Saa ngapi?` in a new chat. |
| [29 — Check device status](29_device_status.png) | Inspect the current Android device and its available storage. | Enter `Device status` in a new chat. |

### Memory, notes, and documents

| Screenshot | What it shows | How to reproduce |
| :--- | :--- | :--- |
| [17 — Remember and recall](17_memory_recall.png) | Save an explicit preference and retrieve it in conversation. | Enter `Remember that my preferred drink is green tea.` and then `What do you remember about me?`. |
| [18 — Inspect personal memory](18_memory_controls.png) | Review a saved fact, its source, and controls to correct or delete it. | After saving the demonstration preference, open Settings → Personal memory. Leave the record unchanged. |
| [19 — Save private notes](19_local_notes.png) | Create a local note and retrieve its stored content. | Enter `Take a note: Weekend plan - pack a water bottle and notebook.` and then `Show my notes`. |
| [20 — Import a document library](20_document_library.png) | Text, image, and PDF imports with local storage and deletion controls. | Open Local document library and import the included text, image, and PDF using Android’s document picker. The capture uses filenames `Household safety reserve.txt`, `Weekend packing list.png`, and `Weekly budget.pdf`. |
| [21 — Read text from images](21_local_ocr.png) | Search text extracted from an authored image using local OCR. | After importing the packing-list image, search `light jacket` in Local document library. Wait for the image’s extracted text and page citation. |
| [13 — Search across languages](13_semantic_kiswahili.png) | A Kiswahili query retrieves a cited English source by meaning. | Install E5, import the demo text as `Household safety reserve.txt`, and search `Ninawezaje kulinda familia yangu nikikosa mshahara?` in Local document library. |

### Offline speech and model setup

| Screenshot | What it shows | How to reproduce |
| :--- | :--- | :--- |
| [11 — Install offline recognition](11_offline_recognition.png) | Separate verified English and Kiswahili recognition packs. | Open Settings → Assistant language and install both recognition packs. |
| [12 — Install local voices](12_offline_voices.png) | English/Kiswahili voice packs and the optional semantic search pack. | Continue scrolling in Assistant language after installing both voices and the E5 search pack. |
| [25 — Choose a local runtime](25_model_setup.png) | Installed Gemma weights, runtime choices, and model acquisition controls. | Open Settings → Local Language Model after downloading the pinned Gemma 3 bundle; keep the installed runtime selected. |
| [26 — Configure hands-free speech](26_voice_controls.png) | Auto-read, continuous listening, follow-up timing, and interruption controls. | Open Settings → Voice & Speech (TTS). Enable Continuous Voice Loop to reveal its follow-up window. Capture, then restore prior preferences. |

### Clarifications, reminders, and routines

| Screenshot | What it shows | How to reproduce |
| :--- | :--- | :--- |
| [09 — Continue a clarification](09_clarification_followup.png) | The next answer supplies exact message text after an app restart. | Enter `draft a message to Maya`. Close/reopen the app within ten minutes; answer `I will arrive at 3 pm.` in the same conversation. |
| [10 — Review before sending](10_action_confirmation.png) | An SMS workflow pauses with the recipient and body visible. | Enter `send a message to +12025550123 saying I will arrive at 3 pm.` in a new chat. Capture the pending confirmation, then select **Cancel**. The number is fictional (NANP 555-01xx); no dispatch was performed. |
| [22 — Review saved reminders](22_reminders.png) | A demonstration reminder is stored and listed locally. | Enter `Remind me to review my packing list at 18:00` and then `Show my reminders`. Dismiss completed workflow cards to show the conversation, then cancel only this demonstration reminder after capturing. |
| [23 — Reuse personal routines](23_personal_routines.png) | Saved requests can be reviewed, edited, enabled, or removed. | Open Settings → Personal routines and save the Morning and Bedtime presets. Scheduled entries prompt you to review/run the request. |
| [24 — Schedule a routine](24_routine_editor.png) | Compose a reusable request with an optional time and weekday schedule. | In Personal routines, select New routine → Morning, enter `07:30` as the optional reminder time, and capture the filled editor. Disable the demonstration schedule after capture. |
| [30 — Inspect execution records](30_execution_audit.png) | Review local execution records for notes, clock, and calculator tools. | After running the demonstration tools, open Settings → Audit Logs. Capture their local execution records. |

### Android access and screen assistance

| Screenshot | What it shows | How to reproduce |
| :--- | :--- | :--- |
| [27 — Inspect Android permissions](27_permission_manager.png) | Android capability groups show their platform and permission requirements. | Open Security & Capabilities → System Permissions and scroll to Calendar & Schedule and Contacts & People. |
| [28 — Review assistant access](28_special_access.png) | Android roles and special access remain explicit user choices. | In System Permissions, review the Android roles and special-access cards. These are access/status controls, not proof of successful integration on every device. |
| [31 — Control screen assistance](31_screen_agent_controls.png) | Review screen-agent boundaries and enablement controls. | Open Settings → Screen Agent Mode and capture the first-run explanation; select Cancel afterward. |

All sizes and hashes are recorded in [capture-manifest.json](capture-manifest.json). For inference tests and the remaining quality limits, see the [Android validation record](../../docs/validation/2026-11-08-android-cloud.md).

## Capture your own screenshots

Use a device you are authorized to operate, create demonstration conversations, and install the optional packs needed for the screen. Keep private information out of the capture. On a cloud/emulator display, these settings reproduce the current viewport:

```bash
adb shell wm size 1080x2400
adb shell wm density 440
adb shell settings put system font_scale 1.0
adb shell am start -n com.tavi.assistant/com.example.tavi.MainActivity
adb exec-out screencap -p > screenshot.png
```

These captures show actual stored demo results and configuration controls. Speech settings do not measure live microphone quality; the screen-agent capture shows its enablement boundary rather than a completed browser task. Scheduled routines notify the user to review and run them.

Hide the keyboard before capturing, wait for processing to finish, and inspect the image at full size. Preserve the original pixels. Record the source revision, exact APK checksum, device/API, display settings, date, and demo inputs. An installed-pack screen demonstrates installation status, not recognition quality.

Restore cloud display overrides after capture with `adb shell wm size reset` and `adb shell wm density reset`. Check README image links and regenerate hashes when replacing a capture.

## Older captures

Images `01` through `07` predate the new gallery. The previous README described them as Galaxy A14 / Android 15 physical-device captures; the repository does not include the same detailed capture manifest for that set. They remain available as historical assets and are not used to represent the latest features.
