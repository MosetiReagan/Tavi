#!/usr/bin/env bash
set -e

echo "=============================================================="
echo "  Tavi — Real Device Benchmark Runner"
echo "  Measures: Cold Init, TTFT, Decode Tok/s, Peak PSS, Battery, Thermal"
echo "=============================================================="

# 1. Check device connectivity
DEVICE_LINE=$(adb devices | grep -E "device$" | grep -v "emulator" | head -n 1 || true)
if [ -z "$DEVICE_LINE" ]; then
    echo "❌ Error: No physical Android device connected via ADB."
    echo "Please connect an Android device and ensure USB/wireless debugging is authorized."
    exit 1
fi

DEVICE_ID=$(echo "$DEVICE_LINE" | awk '{print $1}')
echo "✅ Target device connected: $DEVICE_ID"

# 2. Check battery level and thermal state
BATTERY_LEVEL=$(adb -s "$DEVICE_ID" shell dumpsys battery | grep "level:" | awk '{print $2}')
if [ -n "$BATTERY_LEVEL" ] && [ "$BATTERY_LEVEL" -lt 50 ]; then
    echo "⚠️ Warning: Device battery is below 50% ($BATTERY_LEVEL%). Connect charger before running full benchmark suite."
fi

echo "🔋 Device Battery: ${BATTERY_LEVEL}%"

# 3. Build & Install Test APKs
echo "📦 Assembling test and debug APKs..."
./gradlew assembleDebug assembleDebugAndroidTest

echo "📲 Installing APKs onto target hardware..."
adb -s "$DEVICE_ID" install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEVICE_ID" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 4. Prepare host output directory
mkdir -p benchmarks/

# 5. Run Device Benchmark
echo "⏱️ Running DeviceBenchmark instrumentation suite..."
adb -s "$DEVICE_ID" shell am instrument -w -r \
    -e class com.example.tavi.benchmark.DeviceBenchmark \
    com.tavi.assistant.test/androidx.test.runner.AndroidJUnitRunner

# 6. Pull results
echo "📥 Pulling benchmark output JSON from device..."
REMOTE_FILES=$(adb -s "$DEVICE_ID" shell "ls /sdcard/Android/data/com.tavi.assistant/files/benchmark_*.json 2>/dev/null" || true)

if [ -n "$REMOTE_FILES" ]; then
    for f in $REMOTE_FILES; do
        f_clean=$(echo "$f" | tr -d '\r')
        echo "Found: $f_clean"
        adb -s "$DEVICE_ID" pull "$f_clean" benchmarks/
    done
    echo "✅ Benchmark output files pulled into benchmarks/ directory."
else
    echo "ℹ️ Checking internal sandbox files..."
    adb -s "$DEVICE_ID" shell "run-as com.tavi.assistant ls files/benchmark_*.json 2>/dev/null" || true
fi

echo "=============================================================="
echo "✅ Benchmark run complete."
echo "=============================================================="
