#!/usr/bin/env bash
# 프로젝트 최상위 폴더에서 실행: bash scripts/build-local.sh
# Android SDK(ANDROID_HOME 또는 local.properties의 sdk.dir)와 JDK 17이 필요합니다.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f ./gradlew ]; then
  GRADLE="bash ./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo 'Gradle을 찾을 수 없습니다. Android Studio 또는 GitHub Actions로 빌드하세요.' >&2
  exit 1
fi
$GRADLE --no-daemon assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/TERO_Factory_OS_Tablet_v1.0.0-debug.apk
sha256sum dist/TERO_Factory_OS_Tablet_v1.0.0-debug.apk > dist/SHA256SUMS
echo 'APK: dist/TERO_Factory_OS_Tablet_v1.0.0-debug.apk'
