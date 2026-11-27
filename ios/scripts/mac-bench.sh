#!/bin/bash
# How well does this Mac handle the iPad app? Run from anywhere:  bash ios/scripts/mac-bench.sh
# Checks the hardware and versions, then times: core tests, a clean build, a one-file rebuild,
# booting an iPad simulator, installing and launching the app, CPU and memory with it running,
# and the iPad screenshot tests (what CI runs). Report: ios/build/bench-report.txt
# SKIP_SHOTS=1 leaves out the screenshot tests (the longest part).
cd "$(dirname "$0")/.." || exit 1
mkdir -p build
R=build/bench-report.txt
: > "$R"
say() { echo "$*" | tee -a "$R"; }
t0=0; start() { t0=$(perl -MTime::HiRes=time -e 'printf "%.1f", time'); }
took() { perl -MTime::HiRes=time -e "printf '%.1f', time - $t0"; }
step() { say ""; say "== $*"; }

step "Machine"
say "Model:      $(sysctl -n hw.model)"
say "CPU:        $(sysctl -n machdep.cpu.brand_string)  ($(sysctl -n hw.physicalcpu) cores / $(sysctl -n hw.logicalcpu) threads)"
say "Memory:     $(( $(sysctl -n hw.memsize) / 1073741824 )) GB"
say "macOS:      $(sw_vers -productVersion) ($(sw_vers -buildVersion))"
say "Disk free:  $(df -h / | awk 'NR==2{print $4}')"
say "Disk type:  $(diskutil info / | awk -F: '/Solid State/{gsub(/ /,"",$2); print ($2=="Yes"?"SSD":"not SSD")}')"
say "Thermal:    $(pmset -g therm 2>/dev/null | grep -i 'CPU_Speed_Limit' | xargs || echo 'no limit reported')"
say "Power:      $(pmset -g batt | head -1 | sed "s/.*'\(.*\)'.*/\1/")"

step "Tools"
if ! xcodebuild -version >/dev/null 2>&1; then
  say "Xcode is not installed (or not selected: sudo xcode-select -s /Applications/Xcode.app)."
  say "The app needs Xcode 26 or newer (it uses the iOS 26 SDK: Liquid Glass)."
  exit 1
fi
say "$(xcodebuild -version | tr '\n' ' ')"
say "Swift:      $(swift --version 2>&1 | head -1)"
major=$(xcodebuild -version | awk 'NR==1{split($2,v,"."); print v[1]}')
[ "$major" -lt 26 ] && say "WARNING: Xcode $major is too old for this app (needs 26+, CI uses the newest)."
command -v xcodegen >/dev/null || { say "Installing xcodegen…"; brew install xcodegen >/dev/null || { say "No Homebrew: install it from brew.sh first."; exit 1; }; }
command -v jq >/dev/null || brew install jq >/dev/null
runtimes=$(xcrun simctl list runtimes available | grep -c iOS)
say "iOS simulator runtimes: $runtimes"
[ "$runtimes" -eq 0 ] && { say "No iOS simulator: Xcode › Settings › Components › iOS. Then run this again."; exit 1; }

step "Core tests (swift test, TaliCore)"
start; swift test --package-path Packages/TaliCore > build/bench-core.log 2>&1; ok=$?
say "$( [ $ok = 0 ] && echo passed || echo FAILED ) in $(took) s   ($(grep -oE 'Executed [0-9]+ tests' build/bench-core.log | tail -1))"

step "Clean build of the app and UI tests"
xcodegen generate >/dev/null
rm -rf build/dd
start; sh scripts/app-build.sh > build/bench-build.txt 2>&1; ok=$?
clean=$(took)
say "$( [ $ok = 0 ] && echo built || echo FAILED ) in $clean s"
[ $ok = 0 ] || { cat build/bench-build.txt | tee -a "$R"; exit 1; }

step "Rebuild after touching one file (what you'd feel while working)"
f=$(ls App/Sources/*/*.swift | head -1); touch "$f"
start; sh scripts/app-build.sh >/dev/null 2>&1; say "rebuilt in $(took) s  (touched $f)"

step "iPad simulator"
dev=$(sh scripts/sim.sh "iPad Pro 13" iPad)
[ -n "$dev" ] || { say "No iPad simulator found."; exit 1; }
say "Device: $(xcrun simctl list devices | grep "$dev" | sed 's/(.*//' | xargs)"
xcrun simctl shutdown all >/dev/null 2>&1
start; xcrun simctl boot "$dev"; xcrun simctl bootstatus "$dev" -b >/dev/null
say "Cold boot: $(took) s"
open -a Simulator --args -CurrentDeviceUDID "$dev"
app=$(find build/dd/Build/Products -maxdepth 2 -name 'TalisNotes.app' -path '*iphonesimulator*' | head -1)
start; xcrun simctl install "$dev" "$app"; say "Install:   $(took) s"
start; xcrun simctl launch "$dev" com.tali.notes >/dev/null; say "Launch:    $(took) s (until the process starts)"
sleep 15
step "Load with the app open (15 s after launch, averaged over 10 s)"
sim_cpu=$(for i in 1 2 3 4 5; do ps -A -o %cpu=,comm= | awk '/Simulator|launchd_sim|TalisNotes|backboardd|SpringBoard/ {s+=$1} END{print s+0}'; sleep 2; done | awk '{s+=$1} END{printf "%.0f", s/NR}')
say "Simulator processes: ~${sim_cpu}% CPU (100% = one core of $(sysctl -n hw.logicalcpu))"
say "$(top -l 2 -n 0 | grep -E '^CPU usage' | tail -1)"
say "$(top -l 1 -n 0 | grep -E '^PhysMem')"
say "Memory pressure: $(memory_pressure 2>/dev/null | grep -i 'free percentage' | xargs)"
say "Swap used: $(sysctl -n vm.swapusage | awk '{print $6}')"
xcrun simctl io "$dev" screenshot build/bench-ipad.png >/dev/null 2>&1 && say "Screenshot: ios/build/bench-ipad.png"

if [ "${SKIP_SHOTS:-0}" != 1 ]; then
  step "iPad screenshot tests (CI's quick job)"
  start; NO_BUILD=1 ONLY=ScreenshotTests sh scripts/local-shots.sh "iPad Pro 13" bench 0 "" > build/bench-shots.txt 2>&1
  say "done in $(took) s, $(ls shots/bench-*.png 2>/dev/null | wc -l | xargs) screenshots in ios/shots/"
  say "$(grep -E 'Executed [0-9]+ test' build/bench.log | tail -1 | xargs)"
fi

step "Verdict"
c=${clean%.*}
if   [ "$c" -lt 180 ]; then say "Clean build ${clean}s: fine for daily work."
elif [ "$c" -lt 420 ]; then say "Clean build ${clean}s: slow but workable (rebuilds matter more)."
else                       say "Clean build ${clean}s: painful. Lean on CI and use the Mac for running/testing."
fi
say ""
say "Full report: ios/$R"
