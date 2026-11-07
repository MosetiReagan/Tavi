#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

DEVICE_ID="${ANDROID_SERIAL:-$(adb devices | awk '$2 == "device" && $1 !~ /^emulator-/ {print $1; exit}')}"
if [ -z "$DEVICE_ID" ] || [ "$(adb -s "$DEVICE_ID" get-state 2>/dev/null || true)" != "device" ]; then
    echo "No authorized Android device connected. Connect one or set ANDROID_SERIAL."
    exit 1
fi
if [ "$(adb -s "$DEVICE_ID" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ]; then
    echo "Performance measurements require a physical Android device."
    exit 1
fi

echo "Building benchmark APKs for $DEVICE_ID"
./gradlew assembleDebug assembleDebugAndroidTest
adb -s "$DEVICE_ID" install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEVICE_ID" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p benchmarks

remote_reports() {
    adb -s "$DEVICE_ID" shell "run-as com.tavi.assistant sh -c 'ls /sdcard/Android/data/com.tavi.assistant/files/benchmark_*.json files/benchmark_*.json 2>/dev/null'" | tr -d '\r' || true
}
files_before=$(remote_reports)
test_output=$(mktemp)
trap 'rm -f "$test_output"' EXIT
status=0
adb -s "$DEVICE_ID" shell am instrument -w -r \
    -e class com.example.tavi.benchmark.DeviceBenchmark \
    com.tavi.assistant.test/androidx.test.runner.AndroidJUnitRunner | tee "$test_output" || status=1
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$test_output"; then
    status=1
fi

files_after=$(remote_reports)
new_reports=0
while IFS= read -r report; do
    [ -n "$report" ] || continue
    if printf '%s\n' "$files_before" | grep -Fxq "$report"; then continue; fi
    adb -s "$DEVICE_ID" exec-out run-as com.tavi.assistant cat "$report" > "benchmarks/$(basename "$report")"
    new_reports=$((new_reports + 1))
done <<< "$files_after"
if [ "$new_reports" -eq 0 ]; then
    echo "No fresh report was generated. Install and select a verified model, then rerun."
    exit 1
fi
echo "Collected $new_reports fresh report(s) in benchmarks/. Inspect per-run errors and runtime before comparing results."
exit "$status"
