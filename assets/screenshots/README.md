# Tavi screenshots

The current README gallery uses unedited PNG captures of the running app. It shows demonstrated UI behavior; screenshots alone do not establish speech accuracy, device performance, or a comparison against other assistants.

## Current gallery — 8 November 2026

- App source: feature revision `04b41ea` (documentation edits do not change the APK).
- App: `com.tavi.assistant`, debug, x86_64.
- APK SHA-256: `21bc642b2f7ed15eaf7ba5d21802f18af322304309d1ca607a793782b38452fb`.
- Device: Android 14 / API 34, redroid cloud Android container.
- Display: 1080 × 2400 pixels, density 440 dpi, default font scale, light theme. The cloud display was configured for portrait documentation capture.
- Capture: `adb exec-out screencap -p`, with UI navigation through ADB. No compositing, AI generation, replacement text, or image cropping was applied.
- Content: authored demonstration conversations and the included [household reserve text](demo-household-reserve.txt). No real contact, personal document, or delivered message appears.
- Installed optional packs: Whisper English, Omnilingual Kiswahili, Piper English/Kiswahili, and multilingual E5 Small. Sources and terms are in [local model packs](../../docs/LOCAL_MODEL_PACKS.md).

| Screenshot | What it shows | How to reproduce |
| :--- | :--- | :--- |
| [08 — Current welcome](08_current_welcome.png) | Action starters, on-device model indicator, and text/voice input. | Open Tavi and start a new chat. |
| [09 — Clarification follow-up](09_clarification_followup.png) | Exact message text collected after a clarification, with the completed draft. | Enter `draft a message to Maya`. Close/reopen the app within ten minutes; answer `I will arrive at 3 pm.` in the same conversation. |
| [10 — Action confirmation](10_action_confirmation.png) | An SMS workflow paused with recipient/body visible. | In a new chat, enter `send a message to +12025550123 saying I will arrive at 3 pm.`. Capture the pending confirmation and select **Cancel**. The number is from the fictional NANP 555-01xx range; no approval or dispatch was performed. |
| [11 — Offline recognition](11_offline_recognition.png) | Installed English and Kiswahili recognition packs. | Open Settings, scroll to Assistant language, and install both recognition packs. |
| [12 — Offline voices](12_offline_voices.png) | Installed English and Kiswahili voice packs and source/removal controls. | Continue scrolling within Assistant language after installing both voices. |
| [13 — Semantic Kiswahili](13_semantic_kiswahili.png) | A Kiswahili query retrieves a cited English source by meaning. | Install E5, import the demo file as `Household safety reserve.txt`, and search `Ninawezaje kulinda familia yangu nikikosa mshahara?`. |

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

Hide the keyboard before capturing, wait for processing to finish, and inspect the image at full size. Preserve the original pixels. Record the source revision, exact APK checksum, device/API, display settings, date, and demo inputs. An installed-pack screen demonstrates installation status, not recognition quality.

Restore cloud display overrides after capture with `adb shell wm size reset` and `adb shell wm density reset`. Check README image links and regenerate hashes when replacing a capture.

## Older captures

Images `01` through `07` predate the new gallery. The previous README described them as Galaxy A14 / Android 15 physical-device captures; the repository does not include the same detailed capture manifest for that set. They remain available as historical assets and are not used to represent the latest features.
