#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
serial="${ANDROID_SERIAL:-emulator-5554}"
adb_bin="$sdk_dir/platform-tools/adb"
validation_dir="$project_dir/output/android-entry-v4/validation"

"$adb_bin" -s "$serial" get-state
cd "$project_dir/native/android"
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$adb_bin" -s "$serial" shell am instrument -w -r -e class com.zoomzhao.racingclub.EmulatorFlowTest com.zoomzhao.racingclub.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-flow-80.log
rg -q 'OK \(2 tests\)' emulator-flow-80.log
"$adb_bin" -s "$serial" shell am instrument -w -r -e class com.zoomzhao.racingclub.DirectionalVisualTest com.zoomzhao.racingclub.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-directions-80.log
rg -q 'OK \(1 test\)' emulator-directions-80.log
"$adb_bin" -s "$serial" shell am instrument -w -r -e class com.zoomzhao.racingclub.CatalogIntegrationTest com.zoomzhao.racingclub.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-catalog-80.log
rg -q 'OK \(3 tests\)' emulator-catalog-80.log
"$adb_bin" -s "$serial" shell am instrument -w -r -e class com.zoomzhao.racingclub.EntryAndGoalsTest com.zoomzhao.racingclub.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-entry-goals-v4.log
rg -q 'OK \(2 tests\)' emulator-entry-goals-v4.log
mkdir -p "$validation_dir"
cp emulator-flow-80.log emulator-directions-80.log emulator-catalog-80.log emulator-entry-goals-v4.log "$validation_dir/"
"$adb_bin" -s "$serial" pull /sdcard/Android/data/com.zoomzhao.racingclub/files/validation/. "$validation_dir/"
cp app/build/outputs/apk/debug/app-debug.apk "$project_dir/output/RacingClub-Android-debug.apk"
