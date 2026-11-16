# Experimental: unlock the bootloader and flash TWRP to recovery

> **Status: EXPERIMENTAL.** This goes beyond the main guide. The main guide
> deliberately never unlocks the bootloader and never flashes a partition, so it
> stays low-risk. This page does both. It is here because it worked on one Gen3
> unit (SP8096 / MSM8996), start to finish, but it is **higher risk, destructive,
> and less tested**. Read the whole page before you touch anything.

## What this gets you, and what it does not

**Gets you:** a genuinely unlocked bootloader and **persistent `fastboot flash`**.
On a stock, locked Pod you can only ever `fastboot boot` the signed recovery image
(RAM-boot, gone on reboot). After this you can write recovery, boot, or a custom
ROM to flash so they survive a reboot. Persistent TWRP on the recovery partition
means you no longer need the case open and the button combo every time you want a
root shell.

**Does not get you:** a newer Android. This is still a Snapdragon 820 on Android
7.1.1 with a 2022-era 3.18 kernel. Unlocking changes the lock state, not the OS.
See [`newer-android.md`](newer-android.md) for why a version bump is a separate,
much larger project.

## Before you start: the costs, stated plainly

- **Unlocking WIPES `/data`.** Userdata and the FRP partition are erased. You lose
  the entire Android-TV setup from the main guide (launcher, TizenTube, F-Droid,
  apps, settings) and rebuild it afterward. Back up anything you want to keep
  first. This is not reversible without re-doing the setup.
- **Brick risk moves, it does not disappear.** The unlock command itself is low
  risk (it is a state change plus a wipe). The danger is what you flash *after*
  unlocking: a bad `boot` or `system` image, or mounting `/system` read-write, can
  still leave you at a black screen. Mersive publishes no factory firmware, so a
  bad flash of a critical partition may be unrecoverable.
- **End-of-life device, unchanged.** Same no-security-updates caveat as the main
  [Disclaimer](../README.md). Only do this to a
  Pod you own.
- **Tested on one unit.** Mersive's bootloader behavior is not guaranteed to be
  identical across production runs. Yours may differ. If any command returns
  something other than what is shown here, stop and re-read rather than forcing it.

## Prerequisites

1. The main guide works end to end on your Pod: you can reach fastboot (Step 0) and
   you have a signed recovery image (`recovery/twrp-solstice-gen3-signed.img`).
2. The **"OEM unlocking" toggle is ON** in Android's Developer Options. On a booted
   Pod you can confirm the Android-side permission with:
   ```bash
   adb shell getprop sys.oem_unlock_allowed      # want: 1
   ```
   `1` means the toggle is on and the bootloader is allowed to accept an unlock. It
   does **not** mean the bootloader is already unlocked (that is a separate fastboot
   state, checked below).
3. A backup of anything on the Pod you care about, because the next step wipes it.

## Step A: Unlock the bootloader

Enter fastboot (Step 0: hold internal **Volume-Down while applying power**, or from
a working Android `adb reboot bootloader`). Check the current state first, read
only:

```bash
fastboot oem device-info
# A locked device reports:  Device unlocked: false
```

Now unlock. On this device the two "standard" commands are **rejected** with a
message telling you which command to use instead:

```bash
fastboot flashing unlock
#  -> FAILED (remote: 'Need wipe userdata. Do 'fastboot oem unlock-go'')
fastboot oem unlock
#  -> FAILED (remote: 'Need wipe userdata. Do 'fastboot oem unlock-go'')
```

The command this bootloader actually honors is:

```bash
fastboot oem unlock-go
#  -> OKAY
```

That performs the unlock and the userdata wipe, then the Pod reboots itself into
the stock setup wizard. (If your unit accepts `flashing unlock` or `oem unlock`
directly, let it; the end state is the same. The `-go` variant is what this
Qualcomm LK build required.)

### Confirm the unlock

Once it boots back to Android (you will need to re-enable ADB per the main guide's
Step 1, since `/data` was wiped):

```bash
adb shell getprop ro.boot.verifiedbootstate     # want: orange   (was: green)
```

Or from fastboot:

```bash
fastboot oem device-info                        # want: Device unlocked: true
```

`orange` / `Device unlocked: true` means you are unlocked. `green` means it did not
take. Nothing else changed: it is the same build, now just unlocked.

## Step B: Flash TWRP to the recovery partition (persistent)

This writes **only the recovery partition**. It never touches `aboot`, `boot`, or
`system`. On this hardware the partition layout (by name) is:

| partition | block      | size  | this step writes it? |
|-----------|------------|-------|----------------------|
| aboot     | mmcblk0p12 |       | no, never            |
| boot      | mmcblk0p14 |       | no                   |
| recovery  | mmcblk0p15 | 64 MB | **yes**              |

Work from a **RAM-booted** TWRP so you have a root shell with the recovery
partition unmounted. From fastboot:

```bash
fastboot boot recovery/twrp-solstice-gen3-signed.img
```

Wait for TWRP's own ADB to come up (on the TWRP screen choose **Keep Read Only**;
never swipe to allow modifications, that still bricks `/system`). Confirm you have a
root shell:

```bash
adb shell id                                    # want: uid=0
```

**Dump the stock recovery first.** This is your restore image if the flash goes
wrong, so do not skip it:

```bash
adb shell "dd if=/dev/block/bootdevice/by-name/recovery of=/tmp/stock-recovery.img bs=1M"
adb pull /tmp/stock-recovery.img ./stock-recovery.img     # keep this file safe
```

Push the signed TWRP image and verify it arrived intact before writing anything:

```bash
adb push recovery/twrp-solstice-gen3-signed.img /tmp/twrp.img
adb shell "sha256sum /tmp/twrp.img"
sha256sum recovery/twrp-solstice-gen3-signed.img
# the two hashes MUST match before you continue
```

Write it to recovery, then read it back and compare, so you know the write is good
**before** you reboot into it:

```bash
# write (only p15 / recovery):
adb shell "dd if=/tmp/twrp.img of=/dev/block/bootdevice/by-name/recovery bs=1M conv=fsync"

# read back exactly as many bytes as the image and hash it:
IMG_SIZE=$(stat -c %s recovery/twrp-solstice-gen3-signed.img)
adb shell "dd if=/dev/block/bootdevice/by-name/recovery of=/tmp/readback.img bs=$IMG_SIZE count=1"
adb shell "sha256sum /tmp/readback.img"
# this hash MUST equal the image hash above. If it does not, DO NOT reboot to
# recovery: re-push stock-recovery.img and dd it back to the recovery partition.
adb shell "rm -f /tmp/twrp.img /tmp/readback.img"
```

### Confirm persistent TWRP

Reboot into recovery and check it comes up on its own (not RAM-booted):

```bash
adb reboot recovery
adb wait-for-recovery
adb shell getprop ro.twrp.version               # e.g. 3.1.1-0
```

If TWRP comes up and reports its version, recovery now holds it persistently. Reboot
back to Android with `adb reboot`.

> TWRP may report `ro.build.product=oneplus3`. That is expected: the recovery
> ramdisk is the OnePlus 3 TWRP build (same MSM8996 SoC). It is cosmetic. See
> [`NOTICE.md`](../NOTICE.md) for exactly what the image contains.

## Undo / re-lock

- **Restore stock recovery** (from a root TWRP shell), using the dump you kept:
  ```bash
  adb push ./stock-recovery.img /tmp/r.img
  adb shell "dd if=/tmp/r.img of=/dev/block/bootdevice/by-name/recovery bs=1M conv=fsync"
  ```
- **Re-lock the bootloader** (from fastboot). This also wipes `/data`:
  ```bash
  fastboot flashing lock          # or, if rejected:  fastboot oem lock
  ```

## Things that still bite (even unlocked)

- **Never mount `/system` read-write.** Unlocking does not change this. Allowing
  modifications in TWRP breaks dm-verity and the stock image boots to a black
  screen. Keep TWRP read-only.
- **Be careful flashing `boot`.** The signatures-checked behavior documented in the
  main guide is relaxed once unlocked, but it is not something this page verified
  exhaustively. Keep a `boot` backup and test with `fastboot boot` before you
  `fastboot flash`.
- **There is no published factory firmware.** The 820 has an EDL (9008) mode as a
  last resort, but Mersive ships no firehose loader or factory image, so EDL
  recovery would be its own project. Keep your stock `boot` and `recovery` backups.
  Do not flash `aboot`, `xbl`, `tz`, or `modem`.
