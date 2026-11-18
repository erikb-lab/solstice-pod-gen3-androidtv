#!/usr/bin/env bash
# Install the phone-remote fix on a Pod running LineageOS 18.1 (rooted adb needed).
# Usage: install-remote.sh <AtvRemoteService-6.9.906821247.apk> [adb serial]
# Undo: see the comment at the bottom.
set -uo pipefail
APK="${1:?path to the Android TV Remote Service 6.9.906821247 APK}"
SER="${2:-}"
cd "$(dirname "$0")"
A=(adb); [ -n "$SER" ] && A=(adb -s "$SER")
die() { echo "ERROR: $*" >&2; exit 1; }

echo "1/6 checking APK checksum"
echo "d87a4b86af915734789bddb9bf05ba1b5982ee73cf617283b91f09b9abf76052  $APK" | sha256sum -c - || die "APK checksum mismatch"
[ -f com.android.media.tv.remoteprovider.jar ] || die "jar missing: download it from the EXPERIMENTAL release into this folder, or run ./build-remoteprovider.sh"

echo "2/6 getting root adb"
"${A[@]}" root >/dev/null; sleep 3; "${A[@]}" wait-for-device
[ "$("${A[@]}" shell id -u | tr -d '\r')" = 0 ] || die "adb is not root"

echo "3/6 copying files to /system"
"${A[@]}" push com.android.media.tv.remoteprovider.jar com.android.media.tv.remoteprovider.xml tv-remote.rc /data/local/tmp/ >/dev/null || die "push failed"
"${A[@]}" shell 'set -e
mount -o rw,remount /
J=/system/framework/com.android.media.tv.remoteprovider.jar
[ -f $J ] && [ ! -f /data/local/tmp/remoteprovider.jar.orig ] && cp $J /data/local/tmp/remoteprovider.jar.orig
cp /data/local/tmp/com.android.media.tv.remoteprovider.jar $J
cp /data/local/tmp/com.android.media.tv.remoteprovider.xml /system/etc/permissions/
cp /data/local/tmp/tv-remote.rc /system/etc/init/tv-remote.rc
chmod 644 $J /system/etc/permissions/com.android.media.tv.remoteprovider.xml /system/etc/init/tv-remote.rc
chcon u:object_r:system_file:s0 $J /system/etc/permissions/com.android.media.tv.remoteprovider.xml /system/etc/init/tv-remote.rc
sync; mount -o ro,remount /' || die "writing /system failed"

echo "4/6 rebooting so the shared library is registered"
"${A[@]}" reboot; sleep 20; "${A[@]}" wait-for-device
until [ "$("${A[@]}" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done

echo "5/6 installing the remote service APK"
"${A[@]}" install -r "$APK" || die "APK install failed"
"${A[@]}" shell am startservice -n com.google.android.tv.remote.service/.RemoteService >/dev/null

echo "6/6 verifying"
sleep 5
"${A[@]}" shell 'pidof com.google.android.tv.remote.service >/dev/null && echo "remote service: running" || echo "remote service: NOT running"; getprop init.svc.tv_key_injector'
echo "Done. Pair from the Google TV app or Nope Remote; the pairing code shows on the TV."

# UNDO:
#   adb root; adb shell mount -o rw,remount /
#   adb shell rm /system/etc/init/tv-remote.rc
#   adb shell cp /data/local/tmp/remoteprovider.jar.orig /system/framework/com.android.media.tv.remoteprovider.jar   (if a backup was made)
#   adb shell pm uninstall-system-updates com.google.android.tv.remote.service   (or pm uninstall if it was not a system app)
#   adb reboot
