#!/usr/bin/env bash
# Build with the Gradle + JDK + Android SDK that ship inside Unity's Android Build Support.
# Usage: ./build.sh            -> app/build/outputs/apk/debug/app-debug.apk
#        ./build.sh install    -> build and install debug build on the connected phone (USB debugging)
#        ./build.sh release    -> app/build/outputs/apk/release/app-release.apk (needs keystore.properties)
set -euo pipefail
cd "$(dirname "$0")"
UNITY_ANDROID="${UNITY_ANDROID:-$(ls -d /Applications/Unity/Hub/Editor/*/PlaybackEngines/AndroidPlayer | sort | tail -1)}"
export JAVA_HOME="$UNITY_ANDROID/OpenJDK"
GRADLE_HOME="$UNITY_ANDROID/Tools/gradle"
TASK=assembleDebug
case "${1:-}" in
  install) TASK=installDebug ;;
  release) TASK=assembleRelease ;;
esac
exec "$JAVA_HOME/bin/java" -Xmx64m -Dorg.gradle.appname=gradle \
  -classpath "$(ls "$GRADLE_HOME"/lib/gradle-gradle-cli-main-*.jar)" \
  org.gradle.launcher.GradleMain "$TASK" --console=plain
