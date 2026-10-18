#!/usr/bin/env bash
# Release gate: tests + lint + signed AAB, then verifies the signature.
# Usage: scripts/build-release.sh [versionCode]
#   With a versionCode, app/build.gradle.kts is updated before building.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

GRADLE_FILE="app/build.gradle.kts"
AAB="app/build/outputs/bundle/release/app-release.aab"

if [[ $# -ge 1 ]]; then
  [[ "$1" =~ ^[0-9]+$ ]] || { echo "versionCode must be an integer" >&2; exit 1; }
  sed -i '' -E "s/versionCode = [0-9]+/versionCode = $1/" "$GRADLE_FILE"
fi

echo "Building $(grep -E 'versionName|versionCode' "$GRADLE_FILE" | tr -s ' ' | tr '\n' ' ')"

./gradlew test lint bundleRelease

# Without the COMPASS_* signing properties the build still succeeds, unsigned.
if "$JAVA_HOME/bin/jarsigner" -verify "$AAB" | grep -q "jar verified"; then
  echo "Signed AAB: $AAB"
else
  echo "AAB is NOT signed — check the COMPASS_* signing properties" >&2
  exit 1
fi
