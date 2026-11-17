#!/usr/bin/env bash
# =============================================================================
# Flash signed TWRP to the Pod's RECOVERY partition (persistent), from a
# currently RAM-booted TWRP session (root adb). Writes ONLY recovery (p15).
# Never touches aboot/boot/system. After writing it reads the partition back
# and sha256-compares to the image, so we KNOW the write is good before reboot.
#
# Pre-req: Pod is in TWRP now (adb shell is root). We dumped stock recovery to
#          recovery-backup-*.img already, so a restore image exists.
# =============================================================================
set -uo pipefail

KIT=$POD_WORKDIR/solstice-pod-kit
IMG="$KIT/hybrid_twrp_signed.img"
REC=/dev/block/bootdevice/by-name/recovery      # mmcblk0p15, 64 MB
ADB=adb

say(){ printf '\n=== %s ===\n' "$*"; }
die(){ printf '\nERROR: %s\n' "$*" >&2; exit 1; }

[ -f "$IMG" ] || die "TWRP image not found: $IMG"

# --- 1. confirm we are in a root TWRP shell ----------------------------------
say "Environment check"
id_out="$($ADB shell id 2>/dev/null | tr -d '\r')"
echo "shell: $id_out"
echo "$id_out" | grep -q 'uid=0' || die "not a root TWRP shell - RAM-boot TWRP first (pod-dump-recovery.sh)"
$ADB shell "[ -e $REC ]" 2>/dev/null || die "recovery path missing: $REC"

# --- 2. size sanity (image must fit the partition) ---------------------------
IMG_SIZE=$(stat -c %s "$IMG")
PART_SIZE=$($ADB shell "blockdev --getsize64 $REC 2>/dev/null || cat /sys/class/block/\$(readlink $REC | xargs basename)/size" 2>/dev/null | tr -d '\r' | head -1)
echo "image size:     $IMG_SIZE bytes"
echo "partition size: $PART_SIZE bytes (recovery / p15)"
case "$PART_SIZE" in
  ''|*[!0-9]*) echo "warn: could not read partition size numerically, continuing (image is 58 MB, partition is 64 MB)";;
  *) [ "$IMG_SIZE" -le "$PART_SIZE" ] || die "image LARGER than partition - aborting";;
esac

# --- 3. extra pre-flash safety: re-dump current recovery ---------------------
TS="$(date +%Y%m%d-%H%M%S)"
PRE="$KIT/recovery-preflash-$TS.img"
say "Snapshot current recovery -> $PRE (belt and suspenders)"
$ADB shell "dd if=$REC of=/tmp/pre.img bs=1048576" 2>&1 | tr -d '\r'
$ADB pull /tmp/pre.img "$PRE" >/dev/null && $ADB shell "rm -f /tmp/pre.img"
echo "saved $(ls -lh "$PRE" | awk '{print $5}')  sha256 $(sha256sum "$PRE" | awk '{print $1}')"

# --- 4. push image + FLASH recovery ------------------------------------------
say "Push TWRP image to device"
$ADB push "$IMG" /tmp/twrp_flash.img >/dev/null || die "push failed"
pushed_sha=$($ADB shell "sha256sum /tmp/twrp_flash.img" 2>/dev/null | tr -d '\r' | awk '{print $1}')
host_sha=$(sha256sum "$IMG" | awk '{print $1}')
echo "host sha:   $host_sha"
echo "pushed sha: $pushed_sha"
[ "$pushed_sha" = "$host_sha" ] || die "pushed image sha mismatch - aborting before write"

say ">>> WRITING recovery (p15) <<<"
$ADB shell "dd if=/tmp/twrp_flash.img of=$REC bs=1048576 conv=fsync" 2>&1 | tr -d '\r'

# --- 5. read back + verify byte-for-byte -------------------------------------
say "Read back + verify"
$ADB shell "dd if=$REC of=/tmp/rb.img bs=$IMG_SIZE count=1" 2>&1 | tr -d '\r'
rb_sha=$($ADB shell "sha256sum /tmp/rb.img" 2>/dev/null | tr -d '\r' | awk '{print $1}')
$ADB shell "rm -f /tmp/rb.img /tmp/twrp_flash.img" 2>/dev/null
echo "flashed sha: $rb_sha"
echo "image sha:   $host_sha"

if [ "$rb_sha" = "$host_sha" ]; then
  say "SUCCESS - recovery now holds the signed TWRP, verified byte-for-byte"
  cat <<EOF
Test it: reboot to recovery and confirm TWRP comes up persistently.
  From here:   adb reboot recovery
  By hardware: (power combo for recovery, if different from fastboot)
Reboot to Android normally with:  adb reboot
EOF
else
  die "VERIFY FAILED - readback != image. Restore stock now:
  adb shell \"dd if=/tmp/pre.img ...\" (already pulled to $PRE)
  or re-push $PRE and dd it back to $REC. Do NOT reboot to recovery until fixed."
fi

# =============================================================================
# UNDO (restore stock recovery). From a root TWRP shell:
#   adb push <recovery-backup-or-preflash>.img /tmp/r.img
#   adb shell "dd if=/tmp/r.img of=/dev/block/bootdevice/by-name/recovery bs=1048576 conv=fsync"
# We only wrote p15 (recovery). aboot/boot/system were never touched, so the
# device still boots Android regardless of recovery state.
# =============================================================================
