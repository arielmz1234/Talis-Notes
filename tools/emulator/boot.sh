#!/bin/sh
# Waits for the emulator to boot, installs the given APK (default: the debug build) and launches the app.
# Start the emulator first, headless:
#   "$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd medium_tablet -no-window -no-audio -no-boot-anim -no-snapshot-save &
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
for i in $(seq 1 80); do [ "$("$A" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 5; done
"$A" shell input keyevent KEYCODE_WAKEUP; "$A" shell wm dismiss-keyguard; "$A" shell svc power stayon true
"$A" install -r "$APK" && "$A" shell am start -n com.tali.notes/.MainActivity
