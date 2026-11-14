# Mersive Solstice Pod Gen3 (SP8096) → Android TV box

<!-- Add a photo of your own Pod here once you have one, e.g.:
     ![Mersive Solstice Pod Gen3 running Android TV](assets/pod.jpg) -->

The Gen3 Solstice Pod is a wireless-presentation appliance that's really a
**Snapdragon 820 / Android 7.1.1** box. Lots are being retired, and the hardware
holds up. This turns one into a general-purpose Android TV box — your own launcher,
app stores, YouTube, optional HDMI-input passthrough, and PC remote control —
entirely over ADB, no custom ROM.

**Difficulty:** moderate — you're comfortable with `adb` / `fastboot`. **Time:** about an hour. **Risk:** low *if* you heed the one TWRP warning below (a wrong move there can brick the Pod). Every step has a revert.

**Prebuilt downloads:** the signed RAM-boot TWRP image and the HDMI-in APK are attached to the [latest release](../../releases/latest) (verify against [`SHA256SUMS`](SHA256SUMS)). They're also in [`recovery/`](recovery/) and [`app/`](app/) in the tree.

> **AI assistant/agent helping with this?** [`AGENTS.md`](AGENTS.md) is a runbook
> you *may* use. The user's own instructions always take precedence. Only help set
> up a device the user owns, confirm the Disclaimer with them first, and get their
> explicit OK before any step that reduces security or can't be undone.

---

## ⚠️ Disclaimer — please read

Unofficial, community info for modifying **hardware you own**. Not endorsed by or
affiliated with Mersive. Provided **"as is," with no warranty** (including the image
in `recovery/`). You accept full responsibility for the outcome.

- This **can render the device temporarily or permanently inoperable.** One mistake
  here — a broken `/system` verity state — may be **unrecoverable** without Mersive's
  firmware, which isn't provided here.
- It **voids any warranty** and removes the device's Solstice functionality.
- The author(s) **accept no liability** for any damage, data loss, or other
  consequence. If you aren't comfortable recovering a device that won't boot, stop.
- Only modify a device you own and are authorized to. If the Pod was retired from an
  organization, make sure it's yours to keep (not leased or asset-tracked); its
  `/data` may still hold the previous owner's Wi-Fi or config until wiped.
- **The result is an end-of-life device:** Android 7.1.1, a 2022 kernel, no further
  security updates. Keep it behind your router, never port-forward it, and don't use
  it for banking or other sensitive accounts.

Not legal advice. Proceed only if you understand and accept this.

---

## Contents

- [The hardware (Gen3 / SP8096)](#the-hardware-gen3--sp8096)
- [Is this your Pod?](#is-this-your-pod)
- [Three things that will bite you (read first)](#three-things-that-will-bite-you-read-first)
- [What's in this repo](#whats-in-this-repo)
- [Prerequisites](#prerequisites)
- [Step 0 — Enter the bootloader (fastboot)](#step-0--enter-the-bootloader-fastboot)
- [Step 1 — RAM-boot TWRP and enable ADB (once)](#step-1--ram-boot-twrp-and-enable-adb-once)
- [Step 2 — Make ADB permanent (disable the Solstice server)](#step-2--make-adb-permanent-disable-the-solstice-server)
- [Step 3 — Install a launcher (Projectivy)](#step-3--install-a-launcher-projectivy)
- [Step 4 — App stores (F-Droid + Aurora) and unknown sources](#step-4--app-stores-f-droid--aurora-and-unknown-sources)
- [Step 5 — YouTube via TizenTube (and the clock trap)](#step-5--youtube-via-tizentube-and-the-clock-trap)
- [Step 6 — (Optional) HDMI-input passthrough](#step-6--optional-hdmi-input-passthrough)
- [Step 7 — Remote control from your PC (scrcpy)](#step-7--remote-control-from-your-pc-scrcpy)
- [Debugging — symptoms, causes, fixes](#debugging--symptoms-causes-fixes)
- [Recovery / un-bricking](#recovery--un-bricking)
- [Reverting everything](#reverting-everything)
- [Publishing this (if you fork/mirror it)](#publishing-this-if-you-forkmirror-it)

---

## The hardware (Gen3 / SP8096)

| | |
|---|---|
| SoC | Qualcomm Snapdragon 820 (MSM8996), quad-core Kryo + Adreno 530 |
| RAM / storage | 4 GB / 16 GB eMMC |
| OS | Android 7.1.1 (codename `stonehenge`), kernel 3.18 |
| Video out | 2× HDMI (HDMI1 = HDMI 2.0, up to 4K@30; HDMI2 = HDMI 1.4, 1080p) |
| Video in | HDMI input(s) exposed to Android as **camera devices** |
| Network | Gigabit Ethernet + PoE+ (802.3at Type 2), Wi-Fi 802.11ac 2×2 |
| Ports | 2× USB 3.0-A, 1× USB-C |
| Power | 12 VDC @ 2 A barrel (5.5 mm OD / 2.1 mm ID, **center-positive**) or PoE+ |
| Draw | ~6–10 W average (the "2 A" is headroom, not continuous draw) |

No battery — it powers on when power is applied and off when you unplug it (there's
a small power button on the board inside, but none outside). Use any clean **12 V,
center-positive, ≥2 A** barrel supply. **Confirm the voltage and polarity against
your own unit's label before plugging anything in — the wrong voltage or polarity
can destroy the hardware** (no 9 V, no 19 V laptop bricks).

---

## Is this your Pod?

Check the label on the base — it should read **SP8096** / **Pod Gen3**. Physically it's the small black slab (Snapdragon 820) with **two HDMI-out ports, one HDMI-in, a USB-C, two USB-A, and Gigabit Ethernet**, powered from a 12 V barrel jack or PoE+.

If the label shows a **different model or generation** (Gen2 / Gen2.5, or anything other than SP8096), **stop** — the signed boot and recovery images here are built for the Gen3, won't match your device, and flashing a mismatched boot image is a brick risk.

---

## Three things that will bite you (read first)

1. **dm-verity is enforced on `/system`.** Mount `/system` **read-write** in recovery
   and verity breaks, leaving a **black screen that's not easily recoverable** without
   Mersive's firmware. The trap: when TWRP first starts it offers **"Keep Read Only"**
   vs **"Swipe to Allow Modifications."** Always choose **Keep Read Only** — never
   swipe, and never use TWRP's Wipe / Format / Restore / Install.
2. **The bootloader enforces image signatures.** Flashing an **unsigned** boot image
   = black screen, no USB, no display. Only ever `fastboot boot` (RAM-boot) unverified
   images; never `fastboot flash boot` something unsigned. (These units run `secure:no`
   and will `fastboot boot` an image signed with the key they trust **without
   unlocking** — the included TWRP is one such image, verified on a real Gen3.)
3. **The Solstice app turns ADB off ~10 s after every boot.** Until you disable it
   (Step 2), your ADB window slams shut right after boot.

---

## What's in this repo

```
README.md          this guide
AGENTS.md          runbook for an AI agent to set it up for a user
NOTICE.md          licensing / third-party components (read before redistributing)
LICENSE            MIT, for this repo's own code (not the recovery image)
SHA256SUMS         checksums for the binaries, so you can verify them
app/               source + prebuilt APK for the optional HDMI-input app
recovery/
  twrp-solstice-gen3-signed.img   recovery for this board (RAM-boot only; see NOTICE.md)
```

Verify the binaries before use: `sha256sum -c SHA256SUMS`.

## Prerequisites

- A PC with `adb` and `fastboot` (Android platform-tools).
- A small **Phillips screwdriver** — the case comes off once (Step 0) to reach the
  Volume button; after ADB is on you never need it again.
- The included `recovery/twrp-solstice-gen3-signed.img`, **only** ever RAM-booted
  (never flashed) to enable ADB once (Step 1).
- The third-party apps (Projectivy, F-Droid, Aurora, TizenTube) from their
  **official** pages — pin a known version and check the publisher. Aurora Store and
  TizenTube operate outside Google's / YouTube's terms of service; use them knowingly.

Commands below use ADB over USB. For network ADB instead, see Step 2.

---

## Step 0 — Enter the bootloader (fastboot)

- With the Pod **unplugged**, **hold Volume-Down** and apply power — it boots straight
  into fastboot. (Volume-Down is the normal Qualcomm fastboot combo on this SoC;
  Volume-Up does *not* reach fastboot here.)
- Volume-Down and a small **power button** are both **inside the case** (the Pod has
  no external buttons). Open the base — **a few screws** — to reach them, a one-time
  thing. The internal power button can power-cycle instead of unplugging, but entering
  fastboot still needs Volume-Down held *as power is applied*.
- For all later use you drive the Pod over USB/network (`adb reboot bootloader`), so
  once ADB is on you can close the case for good.
- Confirm: `fastboot devices` lists it.

## Step 1 — RAM-boot TWRP and enable ADB (once)

```bash
fastboot boot recovery/twrp-solstice-gen3-signed.img
```
This **RAM-boots** TWRP — it flashes nothing. TWRP brings its own working `adb`, so
from your PC you now have a root shell on the Pod.

> **On the TWRP screen:** if asked **Keep Read Only vs Swipe to Allow Modifications →
> Keep Read Only.** Otherwise leave the screen alone — don't swipe to modify, don't
> accept any "Install TWRP app" prompt, don't Wipe / Format / Restore. Everything
> below runs from your PC over `adb`.

**Check `/data` is mounted and readable** (if the Pod is encrypted, TWRP shows a
decrypt-password prompt; if so these edits won't work as written and you must handle
decryption first):
```bash
adb shell 'mount | grep -q " /data " && head -c 40 /data/system/users/0/settings_global.xml && echo OK'
# expect: the start of an XML file, then OK
```

**Back up the boot partition** — your get-out-of-jail card:
```bash
adb shell "dd if=/dev/block/bootdevice/by-name/boot of=/tmp/boot-backup.img bs=1M"
adb pull /tmp/boot-backup.img ./boot-backup.img    # /tmp is lost on reboot; the pull is what matters
```

Now make three changes so **stock** boots with ADB enabled:
```bash
# 1. turn ADB on in settings (flip it to 1 if present, add it if absent, then verify):
adb shell 'F=/data/system/users/0/settings_global.xml; \
  if grep -q "name=\"adb_enabled\"" "$F"; then \
    sed -i "s#\(name=\"adb_enabled\" value=\"\)[0-9]#\11#" "$F"; \
  else \
    sed -i "s#</settings>#  <setting id=\"9990\" name=\"adb_enabled\" value=\"1\" package=\"android\" />\n</settings>#" "$F"; \
  fi; \
  grep -o "name=\"adb_enabled\" value=\"[0-9]\"" "$F"'   # must print value="1"

# 2. usb config that exposes adb (this file normally already exists, so its
#    SELinux label is preserved; the chcon is a safety net if it was created new):
adb shell 'printf diag,adb > /data/property/persist.sys.usb.config; \
  chown 0:0 /data/property/persist.sys.usb.config; chmod 600 /data/property/persist.sys.usb.config; \
  chcon u:object_r:property_data_file:s0 /data/property/persist.sys.usb.config 2>/dev/null'

# 3. authorize YOUR computer: push your PC's public key, fix owner/mode/label:
adb push ~/.android/adbkey.pub /tmp/mykey.pub
adb shell 'mkdir -p /data/misc/adb; cat /tmp/mykey.pub >> /data/misc/adb/adb_keys; \
  chown 1000:2000 /data/misc/adb/adb_keys; chmod 640 /data/misc/adb/adb_keys; \
  (restorecon /data/misc/adb/adb_keys 2>/dev/null || \
   chcon u:object_r:adb_keys_file:s0 /data/misc/adb/adb_keys 2>/dev/null); rm /tmp/mykey.pub'
```
Use **your own** `~/.android/adbkey.pub`, never anyone else's. (Missing? Run
`adb keygen ~/.android/adbkey`, or just run `adb devices` once.)

- **Stock comes up with no ADB?** Usual causes: (a) step 1's verify didn't print
  `value="1"` — the setting is missing or oddly formatted; (b) the usb config isn't
  exactly `diag,adb`; (c) the key file's owner/mode/**SELinux label** is wrong (want
  `system:shell`, `640`, label `adb_keys_file`). Re-boot TWRP and redo the failing
  one — nothing here is a brick.
- **Revert:** set `adb_enabled` back to `0` and delete `/data/misc/adb/adb_keys`.

Reboot to stock (`adb reboot` from TWRP). Within ~10 s the Pod shows in `adb devices`
— move fast, the Solstice app is about to disable ADB (Step 2).

---

## Step 2 — Make ADB permanent (disable the Solstice server)

```bash
adb shell pm disable-user --user 0 com.mersive.solstice.server
adb shell pm uninstall --user 0 com.mersive.solstice.server   # frees ~300 MB of its user data
```
`uninstall --user 0` removes it for the user but leaves the APK on `/system`, so
dm-verity stays intact. **Don't** try to delete it from `/system`.

- **ADB still drops ~10 s after boot?** Solstice is still enabled — re-run the
  `disable-user` line during the boot-time ADB window, or from TWRP set `enabled="3"`
  on its entry in `/data/system/users/0/package-restrictions.xml` (no entry yet?
  disable it from stock with the command above instead).
- **Revert:** `adb shell cmd package install-existing com.mersive.solstice.server`
  then `adb shell pm enable com.mersive.solstice.server`.

Confirm it's a normal, ADB-reachable Android device:
```bash
adb shell getprop sys.boot_completed      # 1 = booted
adb shell getprop ro.build.fingerprint    # .../msm8996:7.1.1/...
adb shell dumpsys window | grep mCurrentFocus
```

**(Optional) ADB over the network.** Convenient, but **port 5555 gives full control
of the device with no network-layer auth, and is actively scanned** — trusted LAN
only.
```bash
adb tcpip 5555                       # this session only; reverts on reboot (prefer this)
adb connect <POD_IP>:5555
```
Making it survive reboots (`printf 5555 > /data/property/persist.adb.tcp.port` from a
root shell, same owner/label as the other property files) leaves it permanently
exposed and has **no clean one-command revert** — delete that file from a root shell
to undo it. Prefer the non-persistent form above.

---

## Step 3 — Install a launcher (Projectivy)

Best free Android-TV launcher. The latest needs Android 10+, but **v4.63 supports
Android 6.0+**, which fits. Official APKs: the developer's GitHub releases
(`spocky/miproja1`) — get `ProjectivyLauncher-4.63-*.apk` and verify the publisher.

```bash
adb install -r ProjectivyLauncher-4.63-c76-xda-release.apk
adb shell cmd package set-home-activity \
  com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity
```
Press Home; finish Projectivy's first-run wizard with a remote/mouse. Confirm:
```bash
adb shell 'am start -a android.intent.action.MAIN -c android.intent.category.HOME; \
  sleep 2; dumpsys window | grep mCurrentFocus'   # -> com.spocky.projengmenu/...
```
- **Home shows a chooser / old launcher?** Re-run `set-home-activity`, or
  `pm uninstall` the other launcher (keep one installed so you're never home-less).
- **Revert:** `adb uninstall com.spocky.projengmenu`.

---

## Step 4 — App stores (F-Droid + Aurora) and unknown sources

```bash
adb shell settings put secure install_non_market_apps 1
adb install -r F-Droid.apk        # f-droid.org/F-Droid.apk
adb install -r AuroraStore.apk    # F-Droid repo: com.aurora.store (v4.8.4+, minSdk 23)
```
`install_non_market_apps` is a single global toggle on Android 7, needed only so the
**on-device** stores can install apps (not for your own `adb install` commands).
Leaving it on is a mild risk; turn it back off if you only install via ADB.
- **Revert:** uninstall the two packages and
  `settings put secure install_non_market_apps 0`.

---

## Step 5 — YouTube via TizenTube (and the clock trap)

Get `cobalt-arm.apk` (32-bit, ~80 MB, minSdk 24) from the `reisxd/TizenTubeCobalt`
GitHub releases (verify the publisher/version).
```bash
adb install -r cobalt-arm.apk
```
**Clock trap:** these often boot to **1969**, and a wrong clock makes every HTTPS
certificate look invalid, so online apps fail with "certificate" errors. It's the
date, not a cert problem. Fix:
```bash
adb shell settings put global auto_time 1
adb shell settings put global auto_time_zone 1
adb reboot                       # reboot WITH internet; NTP sets the clock
adb wait-for-device && adb shell date   # should be correct now
```
- **Still wrong?** Apps load blank / TLS errors. Check `adb shell date` and that the
  Pod has internet (Ethernet or Wi-Fi).
- **Revert:** `adb uninstall io.gh.reisxd.tizentube.cobalt`.

---

## Step 6 — (Optional) HDMI-input passthrough

The HDMI **input(s)** appear to Android as **camera devices**, so a small Camera2 app
shows the input full-screen.

**Easiest — the prebuilt APK** ([`app/hdmiin.apk`](app/), ~21 KB). It's signed with a
**throwaway key made for this project** (`CN=Pod TV HDMI In`) — not a shared or
personal key, and it contains no private key. Verify against `SHA256SUMS` if you like.
```bash
adb install -r app/hdmiin.apk
adb shell pm grant com.podtv.hdmiin android.permission.CAMERA
adb shell am start -n com.podtv.hdmiin/.MainActivity
```

**Or build it yourself** from the source in [`app/`](app/) — Gradle-free:
```bash
export ANDROID_HOME=~/Android/Sdk    # your SDK path
cd app && ./build.sh                 # produces app/hdmiin.apk
```
`build.sh` needs **build-tools 36.0.0**, **platform android-34**, a **JDK 17+**,
`zip`, and **Python 3 with Pillow** (for the icon/banner). On first run it creates its
own throwaway signing key **outside** the repo (`~/.config/podtv-hdmiin/signing.jks`),
so no key is ever committed. A self-built APK signs differently from the prebuilt, so
uninstall one before installing the other. To change the app id, edit `package` in
`AndroidManifest.xml` and the `src/com/podtv/hdmiin` path to match.

Usage: OK / D-pad switches inputs; it auto-closes to the launcher after ~6 s with no
signal. **Video works; audio does not** — the HDMI audio lands on an I2S path that
returned only silence in testing, so it's disabled in the source (`AUDIO_ENABLED =
false` in `MainActivity.java`; flip it and re-add the `RECORD_AUDIO` permission to
experiment).
- **Revert:** `adb uninstall com.podtv.hdmiin`.

---

## Step 7 — Remote control from your PC (scrcpy)

[scrcpy](https://github.com/Genymobile/scrcpy) mirrors and controls the Pod over ADB
(it has no touchscreen):
```bash
scrcpy                      # USB
scrcpy -s <POD_IP>:5555     # network
```
Fails with `libavformat.so.NN: cannot open shared object file`? Your scrcpy was built
against a different ffmpeg than you have — install your distro's packaged scrcpy and
make sure that copy is first in `PATH`.

---

## Debugging — symptoms, causes, fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| Black screen, **no USB**, no display, for minutes | Unsigned/bad **boot** image flashed | Enter fastboot and `fastboot flash boot <your known-good boot backup>` |
| Black screen that a boot restore does **not** fix | `/system` verity broke (TWRP mounted it rw) | **Often unrecoverable without Mersive firmware** — re-apply Mersive's official firmware update if you can obtain it. This is why you never swipe "Allow Modifications". |
| Black screen but Pod is **pingable / on the network** | Display/HDMI-out issue; Android is actually up | Try the other HDMI-out port; `scrcpy` to see the UI |
| ADB disappears ~10 s after each boot | Solstice server re-enabled | Step 2 (`disable-user`) |
| Apps load blank / **cert / TLS errors** | Clock wrong (1969) | Step 5 clock fix (auto_time + reboot with internet) |
| Stock shows a **"No command"** screen | You're in stock recovery | Power-cycle (unplug/replug) while holding **Vol-Down** to re-enter fastboot |
| Home screen gone / stuck on chooser | No default launcher set | Reinstall a launcher and `set-home-activity` it |

**Golden rule — back up `boot` before you touch it** (also in Step 1). From a
TWRP/root shell:
```bash
adb shell "dd if=/dev/block/bootdevice/by-name/boot of=/tmp/boot-backup.img bs=1M"
adb pull /tmp/boot-backup.img    # keep it safe; this un-bricks the first row above
```

---

## Recovery / un-bricking

- **No working Android, need fastboot:** hold **Volume-Down while applying power**.
  The button is inside the case — **a few screws** (Phillips) on the base — with an
  internal power button beside it. From a *working* Android, skip the case:
  `adb reboot bootloader`.
- **From fastboot:** `fastboot boot recovery/twrp-solstice-gen3-signed.img` to
  RAM-boot recovery (never `flash` unsigned), or `fastboot flash boot
  <your-boot-backup>.img` to restore a known-good boot.
- A broken `/system` (verity) is the one failure a boot backup can't fix (see the
  Debugging table). Avoid it by never allowing modifications in TWRP.
- For normal use you never need the buttons — USB + network ADB + `adb reboot
  bootloader` cover everything while Android boots.

---

## Reverting everything

```bash
adb shell cmd package install-existing com.mersive.solstice.server
adb shell pm enable com.mersive.solstice.server
adb uninstall com.spocky.projengmenu
adb uninstall io.gh.reisxd.tizentube.cobalt
adb uninstall com.podtv.hdmiin
adb uninstall org.fdroid.fdroid
adb uninstall com.aurora.store
adb shell settings put secure install_non_market_apps 0
# undo the ADB-enable changes too, if you want the Pod back to stock behavior
#   (from a root/TWRP shell): set adb_enabled back to 0, delete /data/misc/adb/adb_keys,
#   and delete /data/property/persist.adb.tcp.port if you created it
adb reboot
```
To fully return to factory, re-apply Mersive's official firmware update.

---

## Publishing this (if you fork/mirror it)

- **Scrub your own identity from git.** A commit records your git `user.name` and
  `user.email`. Before the first commit, set a repo-local, non-identifying author and
  a GitHub `noreply` address, and enable GitHub's "block pushes that expose my email".
  Your GitHub account name itself is public.
  ```bash
  git init                    # run this INSIDE this folder, never a parent that holds private files
  git config user.name  "your-handle"
  git config user.email "your-handle@users.noreply.github.com"
  ```
- Distributing a tarball/zip instead? Strip owner metadata
  (`tar --owner=0 --group=0 --numeric-owner …`, `zip -X …`).
- Re-generate `SHA256SUMS` if you change any binary
  (`sha256sum app/hdmiin.apk recovery/*.img > SHA256SUMS`).
- Read [`NOTICE.md`](NOTICE.md) **before redistributing the recovery image** — it
  carries third-party (GPL + proprietary) components with licensing obligations. The
  app-store APKs are linked from their official upstreams rather than bundled, so you
  always get current, authentic builds.


---

<sub>Built with AI assistance ("vibe-coded") and tested on a single unit — read each command before you run it.</sub>
