#!/usr/bin/env bash
# Build HDMI In without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
#
# Prerequisites:
#   - Android SDK with build-tools 36.0.0 and platform android-34
#     (set ANDROID_HOME, e.g. export ANDROID_HOME=~/Android/Sdk)
#   - a JDK 17+ (javac, keytool)
#   - zip, and Python 3 with Pillow (pip install pillow) for the icon/banner
#
# Signing: this uses a THROWAWAY keystore created on first run at
#   $PODTV_KEYSTORE (default ~/.config/podtv-hdmiin/signing.jks).
# It is kept OUTSIDE this repo on purpose so no private key is ever committed or
# shipped. It is not a release key; if you publish updates, make your own.
set -euo pipefail
export TZ=UTC                      # stable, non-identifying timestamps in the APK
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-34/android.jar"
PKG_PATH=com/podtv/hdmiin
KS="${PODTV_KEYSTORE:-$HOME/.config/podtv-hdmiin/signing.jks}"
KS_PASS=podtvbuild

for t in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  [ -e "$t" ] || { echo "missing: $t  (check ANDROID_HOME / build-tools 36.0.0 / platform android-34)"; exit 1; }
done
command -v javac >/dev/null || { echo "javac not found (install a JDK 17+)"; exit 1; }

# throwaway signing key, outside the repo, created once
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -v -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias podtv -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Pod TV HDMI In, O=podtv, C=US" >/dev/null
  echo "created throwaway signing key at $KS (not part of the repo; do not commit)"
fi

rm -rf build res && mkdir -p build/res build/gen build/classes build/dex res/drawable res/mipmap

python3 - <<'PY'
from PIL import Image, ImageDraw, ImageFont
def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"]:
        try: return ImageFont.truetype(p, sz)
        except Exception: pass
    return ImageFont.load_default()
b = Image.new("RGB", (320, 180), (18, 32, 64)); d = ImageDraw.Draw(b)
d.rounded_rectangle((24, 50, 88, 130), 10, outline=(120, 200, 255), width=6)
d.rectangle((40, 66, 72, 114), fill=(120, 200, 255))
d.text((108, 62), "HDMI In", fill="white", font=font(42))
b.save("res/drawable/banner.png")
i = Image.new("RGB", (192, 192), (18, 32, 64)); d = ImageDraw.Draw(i)
d.rounded_rectangle((40, 50, 152, 142), 14, outline=(120, 200, 255), width=10)
d.rectangle((70, 78, 122, 114), fill=(120, 200, 255))
i.save("res/mipmap/ic_launcher.png")
PY

"$BT/aapt2" compile --dir res -o build/res/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
  --java build/gen build/res/res.zip --min-sdk-version 21 --target-sdk-version 25
if ! javac -source 8 -target 8 -nowarn -bootclasspath "$JAR" -classpath "$JAR" -d build/classes \
      "build/gen/$PKG_PATH/R.java" src/$PKG_PATH/*.java 2> build/javac.log; then
  cat build/javac.log; echo "javac failed"; exit 1
fi
grep -v "bootstrap class path" build/javac.log || true
"$BT/d8" --min-api 21 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/unsigned.apk build/with_dex.apk
(cd build/dex && zip -qX ../with_dex.apk classes.dex)
"$BT/zipalign" -f 4 build/with_dex.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out hdmiin.apk build/aligned.apk
rm -f hdmiin.apk.idsig
"$BT/apksigner" verify hdmiin.apk && echo "built $(pwd)/hdmiin.apk ($(stat -c%s hdmiin.apk) bytes)"
