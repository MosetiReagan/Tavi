# Android validation — 2026-11-08

The request engine now persists conversation-scoped clarification slots, independent speech packs support English/Kiswahili input and output, and a verified multilingual embedding pack adds semantic document and note search. These results establish tested behavior; no comparative ranking against other assistant apps was performed.

| Check | Result |
| :--- | :--- |
| JVM tests | 234 passed across 42 suites; zero failures, errors, or skips ([suite counts](2026-11-08-jvm-tests.txt)) |
| Debug lint | Zero errors, 192 warnings, 2 hints |
| Android feature tests | 12 passed; zero failures/skips ([runner](2026-11-08-cloud-features.txt)) |
| Existing Android acceptance | 16 passed in a separate fresh-process run; zero failures/skips ([runner](2026-11-08-cloud-baseline.txt)) |
| Tokenizer reference | All 114 Python-reference cases passed on Android, including Unicode and truncation |
| Native inference | Actual Gemma, Whisper English, Omnilingual Kiswahili, Piper voices, and multilingual E5 CPU inference |
| ARM64 debug/release | Both built successfully; debug signature and release ZIP alignment verified |

## What was exercised

Clarification tests cover database persistence, conversation isolation, completion/cancellation/expiry, and collection of time, duration, recipient, and exact message text. Completing an SMS draft retains explicit confirmation before dispatch.

Speech tests verify pinned asset integrity, English and Kiswahili PCM generation, English recognition, cancellable playback and restart, and human Kiswahili recognition. Sherpa's JNI callback uses an explicitly typed Java method retained by R8. Native handles are serialized with inference and pack removal, and unload after 45 seconds idle or memory pressure.

Semantic tests reopen the database to verify persisted vectors, upgrade a schema-1 database without losing text, invalidate vectors after model changes, and delete document/note vectors. Deletion during inference cannot save or return the removed excerpt. Real E5 inference retrieves the relevant English source for both English and Kiswahili paraphrases with page citations. Search limits new embeddings per request and reports incomplete coverage; explicit indexing is cancellable. Scores are ranking signals, not calibrated answer confidence.

Existing acceptance covers JNI neural responses, tool-result synthesis, cited English/Kiswahili OCR, durable execution receipts, reminder notifications and recurrence/snooze, request-engine tool routing/confirmation, and Compose rendering. The [earlier inference log](2026-11-08-initial-inference.txt) also records the initial Tiny Kiswahili failure that prompted replacement; it describes the earlier pack selection.

## Kiswahili accuracy evidence

Passing voice generation or nonempty decoding does not establish recognition accuracy. Before selecting the Kiswahili recognizer, three distinct human Kenyan Kiswahili FLEURS test transcripts were selected before inference. Word errors use lowercase Unicode words, ignore punctuation, and count substitutions, insertions, and deletions by edit distance.

| Candidate | Word errors / reference words | WER on these three clips |
| :--- | :--- | :--- |
| Whisper Tiny int8 | 83 / 83 | 100% |
| Whisper Small int8 | 60 / 83 | 72.3% |
| Omnilingual 300M CTC int8 | 21 / 83 | 25.3% |

Raw [Whisper comparison](2026-11-08-kiswahili-whisper-reference.txt) and [Omnilingual comparison](2026-11-08-kiswahili-omnilingual-reference.txt) include reference/decoded text, file identifiers, and timings. The reference uses Sherpa Python 1.12.26 on the cloud CPU; the app uses pinned Sherpa Android 1.12.20. The actual Android test separately passes a maximum 45% WER threshold on the licensed second human clip (17 reference words). Its PCM16 fixture and CC BY 4.0 attribution are in the Android test assets.

This three-clip comparison is too small to estimate population accuracy. Short synthetic Kiswahili remained poor: “Saa ngapi sasa?” decoded as “zaa ndapisasa” with Omnilingual in the reference test. Mixed speech, accents, noise, and physical microphones remain unmeasured. The approximately 365 MB Kiswahili pack is optional and used only for selected Kiswahili input; the English pack is not silently substituted for it. Review transcripts before consequential actions. The Piper Kiswahili voice is Congolese `sw_CD`, with no Kenyan accent claim.

Dataset: Google FLEURS, revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, `data/sw_ke/audio/test.tar.gz`, CC BY 4.0. Selected files: `5952193592402663472.wav`, `12530876620590353761.wav`, `2128471129536729579.wav`.

## Environment and reproduction

Android 14 / API 34, redroid x86_64 cloud container, approximately 8 GB RAM. Debug instrumentation exercised native CPU runtimes. The verified Gemma 3 1B artifact remained installed (584,417,280 bytes; SHA-256 `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`). Model binaries and device credentials are excluded from Git. Pack revisions, hashes, terms, and runtime provenance are in [local model packs](../LOCAL_MODEL_PACKS.md).

Initial cloud service failures caused install broken pipes and a runner teardown abort. Restarting the Android container restored its services while preserving data/packs. Bluetooth was disabled because its native real-time scheduling crashes in this container. Both fresh-process suites then completed with normal final runner summaries; case statuses from aborted runs were not counted as completed acceptance.

```bash
./gradlew --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest \
  -PtaviTargetAbi=x86_64 -PtaviSpeechAar=/absolute/path/to/verified-sherpa.aar --max-workers=2
./gradlew --offline assembleDebug assembleRelease \
  -PtaviTargetAbi=arm64-v8a -PtaviSpeechAar=/absolute/path/to/verified-sherpa.aar --max-workers=2
```

Install debug app/test APKs with `adb install --no-streaming -r -t`, preserve installed weights, and run:

```bash
adb shell am force-stop com.tavi.assistant
adb shell am instrument -w -r \
  -e class com.example.tavi.voice.LocalSpeechRuntimeTest,com.example.tavi.documents.SemanticDocumentSearchTest,com.example.tavi.engine.LocalClarificationPersistenceTest \
  com.tavi.assistant.test/androidx.test.runner.AndroidJUnitRunner
```

The other 16 cases are selected by excluding those three feature classes and `com.example.tavi.benchmark.DeviceBenchmark`. Tests requiring optional packs use assumptions when absent; the recorded runs had the packs installed and no skips.

## Final artifacts

ARM64 debug: 101,550,352 bytes, signed with the Android development certificate; `apksigner verify` passed. ARM64 release: 86,991,400 bytes, R8 minified and unsigned; `zipalign -c -P 16 4` passed. Both contain only ARM64 native libraries and all five optional pack definitions. The final R8 mapping retains `SpeechGenerationCallback` and `ai.onnxruntime` JNI classes/members. Model weights are downloaded separately.

`app/build/outputs/apk/debug/app-debug.apk`

```text
SHA-256 db91b1d7e23f4ca55a2aaa554c6a531f871975e2b5ffdebeb8882e064079411c
```

`app/build/outputs/apk/release/app-release-unsigned.apk`

```text
SHA-256 2582b59564600f3f4b1e2a7f2aacedca3e7e471bdcd58a27c076ad4d84dd884b
```

[Full APK checksum manifest](2026-11-08-apk-checksums.json) also identifies the exact x86_64 app and instrumentation APKs used for the final feature run. Build artifacts are ignored by Git.

## Remaining limits

Physical ARM phone microphone behavior, latency, battery, thermals, memory pressure, SIM SMS delivery/calls, and full screen automation need validation. Release APK packaging and JNI retention checks do not establish release runtime behavior on a physical phone. Upstream LiteRT-LM Kotlin metadata warnings remain despite successful builds. Production signing is not configured; the unsigned release APK requires a release keystore before distribution.
