#!/usr/bin/env bash
# Build podtv-hdmid.jar (javac -> d8 -> jar of classes.dex). Needs ANDROID_HOME with
# build-tools 36.0.0 and platform android-34, and a JDK 17+.
set -euo pipefail
export TZ=UTC
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-34/android.jar"
for t in "$BT/d8" "$JAR"; do [ -e "$t" ] || { echo "missing: $t"; exit 1; }; done
rm -rf build && mkdir -p build/classes build/dex
javac --release 8 -Xlint:-options -cp "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 30 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
rm -f podtv-hdmid.jar
(cd build/dex && touch -d '2000-01-01 00:00:00' classes.dex && zip -qX ../../podtv-hdmid.jar classes.dex)
rm -rf build
echo "built $(pwd)/podtv-hdmid.jar"
