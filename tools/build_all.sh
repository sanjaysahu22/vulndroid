#!/usr/bin/env bash
#
# Build every VulnDroid APK and print the install commands.
#
#   ./tools/build_all.sh              # debug builds of all three modules
#   ./tools/build_all.sh --release    # release builds (still debug-signed)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BUILD_TYPE="Debug"
if [[ "${1:-}" == "--release" ]]; then
  BUILD_TYPE="Release"
fi

VULN_APK="app/build/outputs/apk/vuln/${BUILD_TYPE,,}/app-vuln-${BUILD_TYPE,,}.apk"
SECURE_APK="app/build/outputs/apk/secure/${BUILD_TYPE,,}/app-secure-${BUILD_TYPE,,}.apk"
ATTACKER_APK="attacker/build/outputs/apk/${BUILD_TYPE,,}/attacker-${BUILD_TYPE,,}.apk"

echo "==> Building all modules (${BUILD_TYPE})"
./gradlew \
  ":app:assembleVuln${BUILD_TYPE}" \
  ":app:assembleSecure${BUILD_TYPE}" \
  ":attacker:assemble${BUILD_TYPE}"

echo
echo "==> APKs"
for apk in "$VULN_APK" "$SECURE_APK" "$ATTACKER_APK"; do
  if [[ -f "$apk" ]]; then
    printf '    %-70s %s\n' "$apk" "$(du -h "$apk" | cut -f1)"
  else
    printf '    %-70s MISSING\n' "$apk"
  fi
done

echo
echo "==> Install (with a device or emulator attached)"
echo "    adb install -r $VULN_APK"
echo "    adb install -r $SECURE_APK"
echo "    adb install -r $ATTACKER_APK"

echo
echo "==> Uninstall again"
echo "    adb uninstall com.vulndroid.app"
echo "    adb uninstall com.vulndroid.app.secure"
echo "    adb uninstall com.vulndroid.attacker"
