# Real Physical Device Benchmarks

This directory stores real, unedited JSON benchmark outputs captured directly from physical Android devices via `scripts/run_benchmark.sh` and the instrumented `DeviceBenchmark` test suite.

## Benchmark Harness Metrics
Every run measures:
- **Cold Init (ms)**: Engine initialization and memory mapping latency.
- **TTFT (Time To First Token) (ms)**: Latency until the first streamed response token.
- **Decode Throughput (tokens/s)**: Token generation speed over short (30 tok), tool proposal (50 tok), and long (200 tok) generation.
- **Peak PSS (MB)**: Total proportional memory set during peak generation.
- **Battery Drain (% delta)**: Difference in reported battery percentage before and after generation.
- **Thermal Status**: Hardware thermal throttling state before and after each run.

## Measured Environments
Runs are executed in 3 iterations per scenario, reporting median, min, and max.

When no physical device run is available in the repository (e.g. CI environments without attached physical hardware), numbers are explicitly labeled **Not yet measured** in accordance with Tavi's honest execution policy.
