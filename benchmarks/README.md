# Real Physical Device Benchmarks

This directory stores real, unedited JSON benchmark outputs captured directly from physical Android devices via `scripts/run_benchmark.sh` and the instrumented `DeviceBenchmark` test suite.

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

Connect a physical device, install a verified model through Tavi Settings, select its compatible runtime, and run:

```bash
bash scripts/run_benchmark.sh
# For multiple connected devices:
ANDROID_SERIAL=device-serial bash scripts/run_benchmark.sh
```

When no physical device run is available in the repository (e.g. CI environments without attached physical hardware), numbers are explicitly labeled **Not yet measured** in accordance with Tavi's honest execution policy.
