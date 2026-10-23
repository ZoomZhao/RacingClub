#!/bin/sh
# Builds and runs the headless Swift tests with the macOS SDK from full Xcode (no simulator).
# SwiftCoreTests: 80-level rule replay. SwiftSessionTests: shared session scenarios + iOS session.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
unset SDKROOT
sdk=$(/usr/bin/xcrun --sdk macosx --show-sdk-path)
case "$sdk" in *MacOSX*) ;; *) echo "Expected a MacOSX SDK, received: $sdk" >&2; exit 1;; esac
src="$root/native/ios/RacingClub"
out="$root/output/swift-tests"
mkdir -p "$out"
swiftc() { /usr/bin/xcrun --sdk macosx swiftc -sdk "$sdk" -parse-as-library -O "$@"; }
swiftc "$src/GameCore.swift" "$root/tests/SwiftCoreTests.swift" -o "$out/core-tests"
swiftc "$src/GameCore.swift" "$src/GameSession.swift" "$src/DesignTokens.swift" "$src/ArtLibrary.swift" "$root/tests/SwiftSessionTests.swift" -o "$out/session-tests"
"$out/core-tests" "$root/shared/game-data.json"
"$out/session-tests" "$root/shared/game-data.json" "$root/shared/session-scenarios.json" "$src/Resources"
