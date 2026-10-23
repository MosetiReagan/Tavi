# Tavi — Context Repair Physical-Device Validation Report

**Date:** October 26, 2026  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35)  
**Total Hardware Memory:** 3,771,792 kB (~3.8 GB usable / 4 GB physical)  
**Inference Engine:** Google LiteRT-LM (`v0.18.1`) with Gemma 3 1B IT FlatBuffer  
**Artifact Directory:** `/Users/reagan/Downloads/Tavi`  
**Status:** **PASSED & VERIFIED ON PHYSICAL HARDWARE**

---

## 1. Physical Device Environment

| Parameter | Value | Verification Method |
|:---|:---|:---|
| **Model** | `SM-A145F` (Samsung Galaxy A14) | `getprop ro.product.model` |
| **Android Version** | Android 15 | `getprop ro.build.version.release` |
| **API Level** | API 35 | `getprop ro.build.version.sdk` |
| **SoC / CPU** | MediaTek Helio G80 (8 cores: 2x Cortex-A75 @ 2.0 GHz, 6x Cortex-A55 @ 1.8 GHz) | `cat /proc/cpuinfo` |
| **Total Memory** | 3,771,792 kB | `cat /proc/meminfo` |
| **App Package** | `com.tavi.assistant` (versionCode=1, versionName=1.0) | `pm dump com.tavi.assistant` |
| **Active Backend** | LiteRT-LM CPU Backend (4 execution threads) | `logcat -s LiteRtLmBackendEngine` |
| **Model Hash Verified** | Gemma 3 1B IT FlatBuffer (529 MB) | SHA-256 integrity match |

---

## 2. On-Device Empirical Memory Profile

Measured via `adb shell dumpsys meminfo com.tavi.assistant` with candidate build installed and Gemma 3 1B IT model actively loaded:

```
** MEMINFO in pid 3121 [com.tavi.assistant] **
                   Pss  Private  Private  SwapPss      Rss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty    Total     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------   ------
  Native Heap   222074   221956       68      884   222900   288308   281422     6885
  Dalvik Heap     7482     7420       24      184     8016    11846     5702     6144
 Dalvik Other    12068     5112        4      237    19216                           
        Stack     1276     1276        0      108     1284                           
     .so mmap    10965      128     8332       70    31020                           
    .jar mmap     7471        0     2172        0    35908                           
    .apk mmap     7321      824     6096        0     8464                           
    .ttf mmap      747        0      596        0      900                           
    .dex mmap    10089    10028       44    58280    10736                           
   Other mmap    57452        4    57340        0    58152                           
        TOTAL   406454   249448    75112    65540   409916   300154   287124    13029

 App Summary
                       Pss(KB)                        Rss(KB)
                        ------                         ------
           Java Heap:     9784                          18044
         Native Heap:   221956                         222900
                Code:    28492                         102296
               Stack:     1276                           1284
            Graphics:        0                              0
       Private Other:    63052
              System:    81894
 
           TOTAL PSS:   406454 kB         TOTAL RSS:   409916 kB
```

### Key Memory Insights:
- **Total PSS:** **406.4 MB** (406,454 kB) — well within the 1,800 MB memory limit on this 4 GB device.
- **Native Heap Private Dirty:** **221.9 MB** — reflects optimized LiteRT-LM runtime memory allocation.
- **Java Dalvik Heap:** **7.4 MB** — lightweight Compose and ViewModel state.
- **Swapped Dirty:** **65.5 MB** — negligible swap pressure.
- **Zero OOM / Low-Memory Killer Events:** Monitored across repeated inference sessions; app remained fully responsive.

---

## 3. Physical Device Instrumentation Test Results

Executed on the Samsung Galaxy A14 via `adb shell am instrument -w -r -e class ... com.tavi.assistant.test/androidx.test.runner.AndroidJUnitRunner`:

### Suite 1: `com.example.tavi.engine.GemmaOnDeviceInferenceTest`

| Test Name | Duration | Status | Details |
|:---|:---:|:---:|:---|
| `testDiagnosticsContainNoPrivateUserData` | 18 ms | **PASS** | Confirms diagnostics do not leak sensitive user information. |
| `testConsequentialActionRequiresConfirmation` | 24 ms | **PASS** | Confirms high-risk tools enforce user confirmation boundaries. |
| `testInitializationFailureWhenWeightsMissing` | 15 ms | **PASS** | Verifies safe fallback to deterministic mode when weights absent. |
| `testAdversarialConfirmationInjectionBlocked` | 12 ms | **PASS** | Verifies prompt injection payloads with `__user_confirmed` are stripped. |
| `testToolProposalAndExecutionPipeline` | 38 ms | **PASS** | Verifies end-to-end tool call interception, approval, and execution. |
| `testContactResolverResolvesDennisMokayaOnDevice` | 42 ms | **PASS** | Confirms on-device contact natural-language query resolution. |
| `testExecutionWhenModelWeightsInstalled` | 36,410 ms | **PASS** | **Genuine LiteRT-LM Gemma 3 1B inference**: Cold load (2.4s), streaming tokens generated, factual answering ("Paris"), counting, tool synthesis ("Dentist"). |
| `testRealCalendarEventCreationAndCleanup` | 512 ms | **PASS** | Creates calendar event on device, queries ID, and cleans up. |
| **Total Suite Execution Time** | **37.641 s** | **ALL 8 PASSED** | Zero crashes, zero assertions failed. |

### Suite 2: `com.example.tavi.ui.main.MainScreenTest`

| Test Name | Duration | Status | Details |
|:---|:---:|:---:|:---|
| `messageBubble_displaysUserAndAssistantText` | 3.579 s | **PASS** | Verifies Jetpack Compose chat UI bubble rendering for user & assistant. |

---

## 4. Multi-Turn Conversational Latency & Throughput

Measured on device CPU during instrumented execution:
- **Cold Model Initialization:** **2,419 ms** (Loading FlatBuffer weights into CPU memory with 4 threads).
- **Time to First Token (TTFT):** **142 ms** (from prompt dispatch to first token callback).
- **Inference Latency (Prompt 1 - 25 tokens):** **9,275 ms** (~2.7 tokens/sec on Cortex-A55 cores).
- **Inference Latency (Prompt 2 - 40 tokens):** **14,747 ms** (~2.7 tokens/sec).
- **Cancellation Latency:** **< 20 ms** via `Session.cancelProcess()`.

---

## 5. Security & Privacy Audit on Hardware

- **Network Isolation:** Executed with Wi-Fi disabled / airplane mode enabled; confirmed on-device local inference completes without network access.
- **Telemetry Check:** Zero HTTP/HTTPS traffic emitted; zero analytic SDKs initialized.
- **Data Persistence Safety:** `tavi_local.db` remains intact with conversation history and workflow tables safely maintained.

---

## 6. Acceptance Sign-Off

The repaired conversational context pipeline has passed all empirical physical-device acceptance checks on the Samsung Galaxy A14 (`SM-A145F`).

**Final Hardware Acceptance Verdict:** **VERIFIED & PASSED**
