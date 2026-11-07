# Android Device Benchmarks

The top-level directory stores unedited JSON outputs from physical Android devices via `scripts/run_benchmark.sh`. The [cloud/](cloud/) subdirectory contains outputs from cloud Android containers, collected directly from the instrumented `DeviceBenchmark` suite. Cloud measurements do not establish phone latency, battery use, or thermal behavior.

## Benchmark Harness Metrics

The harness uses the installed model and selected runtime, verifies that neural inference is active, and records three runs for short, structured, and long replies. It executes no phone actions.

- **Engine initialization (ms)**: A fresh engine instance within the same process. Filesystem and driver caches may remain warm.
- **First chunk (ms)**: Latency until the first response callback. Callback chunks can contain multiple tokens.
- **Generation duration and characters/second**: Measured response size and elapsed time.
- **Estimated output tokens**: Conservative text estimates, separate from callback counts. Native tokenizer counts and decode tokens/second are unavailable.
- **Sampled peak process PSS (KB)**: Whole-process memory sampled every 50 ms, including app overhead. Brief peaks may be missed.
- **Battery delta (%)**: Coarse Android battery percentage difference; short runs often report zero. This is not an energy measurement.
- **Thermal status**: Android throttling state before and after generation.
- **Selected/effective runtime and errors**: Failed or fallback-only runs cannot count as successful neural measurements.

## Measured Environments
Raw measurements are preserved per run. Initialization or generation failures produce a report and fail the test. Missing weights skip the test; the runner fails when no fresh report appears. Old reports are never counted as results from the current invocation.

With ADB and Python 3 installed, connect a physical device, install a verified model through Tavi Settings, select its compatible runtime, and run:

```bash
bash scripts/run_benchmark.sh
# For multiple connected devices:
ANDROID_SERIAL=device-serial bash scripts/run_benchmark.sh
```

When no physical device run is available in the repository (e.g. CI environments without attached physical hardware), numbers are explicitly labeled **Not yet measured** in accordance with Tavi's honest execution policy.

### Cloud validation on 2026-11-07

The [raw report](cloud/benchmark_redroid14_x86_64_1794062688804.json) records nine successful neural runs on `redroid14_x86_64`, Android 14 / API 34, using the pinned Gemma 3 1B LiteRT-LM artifact (584,417,280 bytes). Median measurements across three runs per scenario:

| Scenario | Engine initialization | First chunk | Generation | Characters/second | Peak process PSS |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Short reply | 388 ms | 740 ms | 773 ms | 9.06 | 1,108,317 KB |
| Structured reply | 413 ms | 841 ms | 2,124 ms | 33.90 | 1,110,318 KB |
| Long reply | 419 ms | 746 ms | 12,323 ms | 130.33 | 1,121,812 KB |

These are measurements of a cloud CPU environment, with warm filesystem/driver caches possible. Response lengths differ between scenarios. Battery and thermal fields from the container do not represent a physical battery or phone cooling system. No comparative ranking against other assistants has been measured.
