#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

DEVICE_ID="${ANDROID_SERIAL:-$(adb devices | awk '$2 == "device" && $1 !~ /^emulator-/ {print $1; exit}')}"
if [ -z "$DEVICE_ID" ] || [ "$(adb -s "$DEVICE_ID" get-state 2>/dev/null || true)" != "device" ]; then
    echo "No authorized Android device connected. Connect one or set ANDROID_SERIAL."
    exit 1
fi
if [ "$(adb -s "$DEVICE_ID" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ]; then
    echo "This acceptance runner expects a physical Android device."
    exit 1
fi

echo "Running Android acceptance tests on $DEVICE_ID"
echo "Install and select verified weights in Tavi Settings to include neural inference; missing weights skip that test."
ANDROID_SERIAL="$DEVICE_ID" ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.notClass=com.example.tavi.benchmark.DeviceBenchmark

echo "Acceptance completed. See app/build/reports/androidTests/connected/debug/ for results and skipped tests."
echo "Run scripts/run_benchmark.sh separately for measured inference reports."
