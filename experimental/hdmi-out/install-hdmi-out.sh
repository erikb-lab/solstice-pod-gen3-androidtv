#!/usr/bin/env bash
# Install the HDMI out fix + resolution picker on a Pod running LineageOS 18.1 (rooted adb needed).
# Usage: install-hdmi-out.sh [adb serial]
# Expects podtv-hdmid.jar and podtv-hdmiout-*.apk in this folder (from the EXPERIMENTAL release,
# or built with daemon/build.sh and app/build.sh). Safe to run again. Undo: see the bottom.
set -uo pipefail
SER="${1:-}"
cd "$(dirname "$0")"
A=(adb); [ -n "$SER" ] && A=(adb -s "$SER")
die() { echo "ERROR: $*" >&2; exit 1; }

JAR=podtv-hdmid.jar; [ -f "$JAR" ] || JAR=daemon/podtv-hdmid.jar
APK=$(ls -1 podtv-hdmiout-*.apk 2>/dev/null | sort -V | tail -1)
[ -n "$APK" ] || { [ -f app/hdmiout.apk ] && APK=app/hdmiout.apk; }
[ -f "$JAR" ] || die "podtv-hdmid.jar missing: download it from the release into this folder, or run daemon/build.sh"
[ -n "$APK" ] || die "HDMI Out APK missing: download it from the release into this folder, or run app/build.sh"

wait_boot() {
  "${A[@]}" wait-for-device
  for _ in $(seq 1 100); do   # ~5 minutes
    [ "$("${A[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && return 0
    sleep 3
  done
  die "the Pod did not finish booting within 5 minutes"
}

echo "1/5 getting root adb"
"${A[@]}" root >/dev/null; sleep 3; "${A[@]}" wait-for-device
[ "$("${A[@]}" shell id -u | tr -d '\r')" = 0 ] || die "adb is not root"

echo "2/5 copying the helper and its init service to /system"
"${A[@]}" push "$JAR" /data/local/tmp/podtv-hdmid.jar >/dev/null || die "push failed"
"${A[@]}" push daemon/podtv-hdmid.rc /data/local/tmp/podtv-hdmid.rc >/dev/null || die "push failed"
# /system goes back to read-only whether or not the copy succeeds
"${A[@]}" shell 'mount -o rw,remount / || exit 1
ok=1
cp /data/local/tmp/podtv-hdmid.jar /system/framework/podtv-hdmid.jar &&
cp /data/local/tmp/podtv-hdmid.rc /system/etc/init/podtv-hdmid.rc &&
chmod 644 /system/framework/podtv-hdmid.jar /system/etc/init/podtv-hdmid.rc &&
chcon u:object_r:system_file:s0 /system/framework/podtv-hdmid.jar /system/etc/init/podtv-hdmid.rc && ok=0
sync; mount -o ro,remount /
rm -f /data/local/tmp/podtv-hdmid.jar /data/local/tmp/podtv-hdmid.rc
exit $ok' || die "writing /system failed (it was put back to read-only)"

echo "3/5 installing the HDMI Out app ($APK)"
# Play Protect blocks self-signed adb installs; turn its adb check off only for this install
OLD=$("${A[@]}" shell settings get global verifier_verify_adb_installs | tr -d '\r')
case "$OLD" in 0|1) ;; *) OLD=1 ;; esac
restore() { "${A[@]}" shell settings put global verifier_verify_adb_installs "$OLD" >/dev/null 2>&1; }
trap restore EXIT INT TERM
"${A[@]}" shell settings put global verifier_verify_adb_installs 0
"${A[@]}" install -r "$APK"; rc=$?
restore; trap - EXIT INT TERM
[ $rc = 0 ] || die "APK install failed (if it says INSTALL_FAILED_UPDATE_INCOMPATIBLE, run: adb uninstall com.podtv.hdmiout, then re-run)"

echo "4/5 rebooting so init starts the helper"
"${A[@]}" reboot; sleep 20
wait_boot
"${A[@]}" root >/dev/null; sleep 3; "${A[@]}" wait-for-device

echo "5/5 verifying"
"${A[@]}" shell 'echo "helper service: $(getprop init.svc.podtv_hdmid)"
echo "port enable (gpio127): $(cat /sys/class/gpio/gpio127/value 2>/dev/null)"
echo "display on HDMI 2.0 port: $(cat /sys/class/switch/hdmi/state 2>/dev/null)"'
echo "Done. Open \"HDMI Out\" from the launcher to pick a resolution and refresh rate."

# UNDO:
#   adb root; adb shell mount -o rw,remount /
#   adb shell rm /system/framework/podtv-hdmid.jar /system/etc/init/podtv-hdmid.rc
#   adb shell mount -o ro,remount /
#   adb shell rm -r /data/misc/podtv-hdmi
#   adb uninstall com.podtv.hdmiout
#   adb reboot
