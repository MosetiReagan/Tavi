# Android validation — 2026-11-07

The small request engine in commit `54f8d6f` and the preceding feature additions have passed JVM checks, APK packaging, and Android cloud instrumentation. These results establish the tested behavior, not a comparative ranking against other assistants.

| Check | Result |
| :--- | :--- |
| JVM tests | 215 passed across 38 suites; no failures or skips |
| Debug lint | 0 errors, 183 warnings, 2 hints |
| Debug app and instrumentation APKs | Built for x86_64 and installed on the cloud device |
| ARM64 debug APK | Built; installable with the development signing key |
| ARM64 release APK | Built with R8 and release lint gates; unsigned until a production keystore is configured |
| Cloud Android acceptance | 16 passed; no failures or skips ([runner output](2026-11-07-cloud-acceptance.txt)) |
| Cloud neural benchmark | 9 successful runs across 3 scenarios ([runner output](2026-11-07-cloud-benchmark.txt), [raw report](../../benchmarks/cloud/benchmark_redroid14_x86_64_1794062688804.json)) |
| Main activity launch | `Status: ok`; application process remained running after launch |

## Environment and model

Android target: `redroid14_x86_64`, Android 14 / API 34, approximately 8 GB RAM. It is a cloud Android container. The Android acceptance suite uses the debug APK.

The selected runtime was Gemma 3 1B through LiteRT-LM. The installed artifact is 584,417,280 bytes; its SHA-256 was checked on the host and in Android app storage:

```text
1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be
```

The model is the revision-pinned artifact configured by `ModelManager.DEFAULT_DOWNLOAD_URL_3_1B`. Model binaries and device access credentials are excluded from these records.

## Behavior covered

Acceptance includes genuine JNI model loading, streamed responses to distinct prompts, grounded tool-result synthesis, and unloading. It also verifies document persistence/search/deletion, cited English/Kiswahili image OCR, durable write receipt replay prevention, reminder notification delivery/recurrence/snooze, and Compose message rendering.

The small request engine is checked against actual registered tool schemas. Android tests verify that its clock request reaches the native tool, unconfirmed SMS cannot dispatch, and the chat screen persists missing-time clarification and successful clock evidence. The engine has no weights or additional inference dependency and routes supported commands before neural model planning/loading.

## Reproduction

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest \
  -PtaviTargetAbi=x86_64 --max-workers=2
./gradlew assembleDebug assembleRelease -PtaviTargetAbi=arm64-v8a --max-workers=2
```

After installing app/test APKs and selecting verified weights on an authorized Android device:

```bash
adb shell am instrument -w -r \
  -e notClass com.example.tavi.benchmark.DeviceBenchmark \
  com.tavi.assistant.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r \
  -e class com.example.tavi.benchmark.DeviceBenchmark \
  com.tavi.assistant.test/androidx.test.runner.AndroidJUnitRunner
```

`scripts/run_benchmark.sh` collects fresh, valid JSON reports from physical devices. Its external/private storage paths, stale reports, failed runs, unreadable JSON, disconnected devices, and emulator rejection were also checked with an isolated mocked ADB harness.

## Limits of this evidence

Physical ARM phone latency, memory pressure, battery use, thermals, microphone/speech-provider behavior, reboot alarm restoration, and full screen automation still need device validation. Calendar tests permit a permission-denied result; passing them alone does not establish a Calendar Provider write. No real SIM SMS delivery or call was performed.

The build tools emit upstream LiteRT-LM Kotlin metadata compatibility warnings. Both APK variants package successfully, and JNI inference passes on the cloud debug build; the minified release APK has not been exercised on a physical phone. No comparative benchmark against other assistant apps has been collected.
