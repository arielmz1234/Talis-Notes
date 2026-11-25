#!/bin/sh
# One CI shard's UI test runs, after build-for-testing. Each argument is one run:
#   "<ipad|ipad11|iphone> <tag> <dark 0/1> <language or -> <Class,Class… or all>"
# Shots land in shots/<tag>-*.png, each run's log in build/<tag>.log. Failing tests are listed but
# don't fail the job: the shots are what it's for.
cd "$(dirname "$0")/.."
mkdir -p shots
ipad=$(sh scripts/sim.sh "iPad Pro 13" iPad)
iphone=$(sh scripts/sim.sh "iPhone 16" iPhone)
# An 11-inch iPad, where the top row has the least room (the owner's iPad is an Air 11).
ipad11=$(sh scripts/sim.sh "iPad Air 11" none)
[ -n "$ipad11" ] || ipad11=$(sh scripts/sim.sh "iPad Pro 11" none)
[ -n "$ipad11" ] || ipad11=$ipad
xcrun simctl list devices | grep -E "$ipad|$iphone|$ipad11"
for run in "$@"; do
  set -- $run
  dev=$ipad; [ "$1" = iphone ] && dev=$iphone; [ "$1" = ipad11 ] && dev=$ipad11
  # Boot fully first: a cold boot inside the first test timed out the app's launch.
  xcrun simctl boot "$dev" 2>/dev/null || true
  xcrun simctl bootstatus "$dev" -b > /dev/null
  only=""
  [ "$5" = all ] || for c in $(echo "$5" | tr ',' ' '); do only="$only -only-testing:TalisNotesUITests/$c"; done
  lang=$4; [ "$lang" = - ] && lang=""
  echo "== $2 ($1, $5)"
  # A test that fails is run again (up to three tries): drags and timings are flaky on CI's slow
  # virtual machines, while a real bug fails every time. CI=1 tells the tests where they run.
  TEST_RUNNER_CI=1 TEST_RUNNER_SHOT_DIR="$PWD/shots" TEST_RUNNER_SHOT_TAG="$2" TEST_RUNNER_SHOT_DARK="$3" TEST_RUNNER_SHOT_LANG="$lang" \
    xcodebuild test-without-building -project TalisNotes.xcodeproj -scheme TalisNotes \
    -destination "id=$dev" -derivedDataPath build/dd -resultBundlePath "build/$2.xcresult" \
    -retry-tests-on-failure -test-iterations 3 $only > "build/$2.log" 2>&1 || true
  # Why a test failed (the element it couldn't find, the line), then the summary.
  grep -E "error:|failed:|Assertion|No matches|not hittable" "build/$2.log" | grep -v "Test Suite" | head -20 || true
  grep -E "Executed .* tests?, with|Test Case .* (passed|failed) on (Retry|Repetition)" "build/$2.log" | tail -8
done
ls shots
test "$(ls shots | wc -l)" -gt 0
