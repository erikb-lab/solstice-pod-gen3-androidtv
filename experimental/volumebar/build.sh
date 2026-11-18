#!/usr/bin/env bash
# Build Volume Bar without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Prerequisites: Android SDK (build-tools 36.0.0, platform android-34; set ANDROID_HOME),
# a JDK 17+ (javac, keytool) and zip.
#
# Signing: a THROWAWAY keystore is created on first run at $PODTV_KEYSTORE
# (default ~/.config/podtv-volumebar/signing.jks), OUTSIDE this repo, so no private
# key is ever committed. It is not a release key.
set -euo pipefail
export TZ=UTC
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-34/android.jar"
PKG_PATH=com/podtv/volumebar
KS="${PODTV_KEYSTORE:-$HOME/.config/podtv-volumebar/signing.jks}"
KS_PASS=podtvbuild

for t in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  [ -e "$t" ] || { echo "missing: $t  (check ANDROID_HOME / build-tools 36.0.0 / platform android-34)"; exit 1; }
done
command -v javac >/dev/null || { echo "javac not found (install a JDK 17+)"; exit 1; }

if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias podtv -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Pod TV Volume Bar, O=podtv, C=US" >/dev/null
  echo "created throwaway signing key at $KS (not part of the repo; do not commit)"
fi

rm -rf build && mkdir -p build/res build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
  --java build/gen build/res/res.zip --min-sdk-version 26 --target-sdk-version 29
if ! javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$JAR" -classpath "$JAR" \
      -d build/classes "build/gen/$PKG_PATH/R.java" src/$PKG_PATH/*.java 2> build/javac.log; then
  cat build/javac.log; echo "javac failed"; exit 1
fi
"$BT/d8" --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/unsigned.apk build/with_dex.apk
(cd build/dex && zip -qX ../with_dex.apk classes.dex)
"$BT/zipalign" -f 4 build/with_dex.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out volumebar.apk build/aligned.apk
rm -f volumebar.apk.idsig
"$BT/apksigner" verify volumebar.apk && echo "built $(pwd)/volumebar.apk ($(stat -c%s volumebar.apk) bytes)"
