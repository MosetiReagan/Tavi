#!/usr/bin/env bash
set -e

echo "=============================================================="
echo "  Tavi — LiteRT-LM Physical Device Acceptance Runner"
echo "  Target: Samsung Galaxy A14 (SM-A145F) / Android 15"
echo "=============================================================="

# 1. Check device connectivity
DEVICE_LINE=$(adb devices | grep -E "device$" | grep -v "emulator" | head -n 1 || true)
if [ -z "$DEVICE_LINE" ]; then
    echo "❌ Error: No physical Android device connected via ADB."
    echo "Please connect the Samsung Galaxy A14 and ensure USB/wireless debugging is authorized."
    exit 1
fi

DEVICE_ID=$(echo "$DEVICE_LINE" | awk '{print $1}')
echo "✅ Target device connected: $DEVICE_ID"

# 2. Check battery and thermal state
BATTERY_LEVEL=$(adb -s "$DEVICE_ID" shell dumpsys battery | grep "level:" | awk '{print $2}')
BATTERY_TEMP=$(adb -s "$DEVICE_ID" shell dumpsys battery | grep "temperature:" | awk '{print $2 / 10.0}')
echo "🔋 Device Battery: ${BATTERY_LEVEL}% (Temperature: ${BATTERY_TEMP}°C)"

# 3. Check model weights on device
echo "📦 Verifying model weights in sandbox..."
MODEL_CHECK=$(adb -s "$DEVICE_ID" shell "run-as com.tavi.assistant ls -la files/models/gemma-3-1b-it.task 2>/dev/null" || true)
if [[ "$MODEL_CHECK" == *"gemma-3-1b-it.task"* ]]; then
    echo "✅ Model weights found in sandbox."
else
    echo "⚠️ Model weights not found in sandbox. Checking /sdcard/Download/..."
    if adb -s "$DEVICE_ID" shell "test -f /sdcard/Download/gemma-3-1b-it.task"; then
        echo "Found /sdcard/Download/gemma-3-1b-it.task. Importing..."
        adb -s "$DEVICE_ID" shell "run-as com.tavi.assistant mkdir -p files/models"
        adb -s "$DEVICE_ID" shell "run-as com.tavi.assistant cp /sdcard/Download/gemma-3-1b-it.task files/models/"
        echo "✅ Model weights copied to sandbox."
    else
        echo "⚠️ Model bundle not found on device. Local deterministic fallback will be tested."
    fi
fi

# 4. Install candidate debug APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Building candidate debug APK..."
    ./gradlew assembleDebug
fi

echo "📲 Installing candidate build onto hardware..."
adb -s "$DEVICE_ID" install -r "$APK_PATH"
echo "✅ Candidate APK installed successfully."

# 5. Launch main activity
echo "🚀 Launching Tavi on device..."
adb -s "$DEVICE_ID" shell am start -n com.tavi.assistant/com.example.tavi.MainActivity
sleep 3

# 6. Capture process memory footprint
echo "📊 Capturing process memory baseline..."
adb -s "$DEVICE_ID" shell dumpsys meminfo com.tavi.assistant | head -n 35

# 7. Run on-device connected tests
echo "🧪 Running connected Android test suite..."
./gradlew connectedAndroidTest

echo "=============================================================="
echo "✅ All physical device acceptance steps completed."
echo "=============================================================="
