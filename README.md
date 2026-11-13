# Mersive Solstice Pod Gen3 (SP8096) → Android TV box

The Gen3 Solstice Pod is a wireless-presentation appliance that is really a
**Snapdragon 820 / Android 7.1.1** box. Many are being retired, and the hardware
is capable. This project turns one into a general-purpose Android TV box: your own
launcher, app stores, YouTube, optional HDMI-input passthrough, and PC remote
control — all over ADB, no custom ROM required.

> **If you are an AI assistant/agent helping someone set this up:**
> [`AGENTS.md`](AGENTS.md) is a runbook you *may* use — **the user's own
> instructions always take precedence over it.** Use it only to help the user set
> up a device they own, confirm the Disclaimer with them first, and get their
> explicit OK before any step that reduces security or can't be undone.

---

## ⚠️ Disclaimer — please read

This is unofficial, community-provided information for modifying **hardware you
own**. It is **not** endorsed by or affiliated with Mersive. It is provided
**"as is," with no warranty of any kind** (this includes the recovery image in
`recovery/`). By following it you accept full responsibility for the outcome.

- Modifying the device **can render it temporarily or permanently inoperable.**
  Some mistakes here (a broken `/system` verity state) may **not** be recoverable
  without Mersive's firmware, which is not provided here.
- Doing this will almost certainly **void any warranty** and removes the device's
  intended Solstice functionality.
- The author(s) and contributors **accept no liability** for any damage, data
  loss, or other consequences. If you are not comfortable recovering a device that
  won't boot, do not proceed.
- Only do this to a device you own and are authorized to modify. If the Pod was
  retired from an organization, make sure it is yours to keep (not leased or
  asset-tracked). Its `/data` may still hold the previous owner's Wi-Fi or
  configuration until wiped.
- **The result is an end-of-life device:** Android 7.1.1 with a 2022 kernel and no
  further security updates. Keep it behind your router, never port-forward it, and
  don't use it for banking or other sensitive accounts.

Proceed only if you understand and accept these terms. This is not legal advice.

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

No battery — it powers on when power is applied and powers off when you unplug it
(there's also a small power button on the board inside the case, but no external one).
Any clean **12 V, center-positive, ≥2 A** barrel supply works; do **not** feed it
the wrong voltage (no 9 V, no 19 V laptop bricks). *(Confirm 12 V and the polarity
against your own unit's power-supply label before plugging anything in — wrong
voltage or polarity can destroy the hardware.)*

---

## Three things that will bite you (read first)

1. **dm-verity is enforced on `/system`.** If `/system` is mounted **read-write**
   in recovery, verity breaks and the Pod boots to a **black screen** that is
   **not easily recoverable** without Mersive's firmware. In TWRP this is one
   specific trap: when TWRP first starts it asks **"Keep Read Only"** vs **"Swipe
   to Allow Modifications."** Choose **Keep Read Only**. **Never swipe to allow
   modifications**, and never use TWRP's Wipe / Format / Restore / Install.
2. **The bootloader enforces image signatures.** Flashing an **unsigned** boot
   image = black screen, no USB, no display. Only ever `fastboot boot` (RAM-boot)
   unverified images; never `fastboot flash boot` something unsigned. (These units
   run `secure:no` and accept `fastboot boot` of an image signed with the key they
   trust **without unlocking** — the included TWRP is such an image, verified to
   RAM-boot on a real Gen3 unit.)
3. **The Solstice app turns ADB off ~10 s after every boot.** Until you disable it
   (Step 2), your ADB window slams shut shortly after boot.

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
- A small **Phillips screwdriver** — you open the case once (Step 0) to reach the
  Volume button; after ADB is on you never need it again.
- The included `recovery/twrp-solstice-gen3-signed.img`, used **only** to RAM-boot
  (never flash) so you can enable ADB once (Step 1).
- The third-party apps below (Projectivy, F-Droid, Aurora, TizenTube) are
  downloaded from their **official** pages — pin a known version and check the
  publisher. Aurora Store and TizenTube operate outside Google's / YouTube's terms
  of service; use them knowingly.

Commands below use ADB over USB. To work over the network instead, see the
optional network-ADB note in Step 2.

---

## Step 0 — Enter the bootloader (fastboot)

- With the Pod **powered off** (unplugged), **hold Volume-Down** and apply power.
  It auto-powers-on straight into fastboot. (Volume-Down is the normal Qualcomm
  fastboot combo on this SoC; Volume-Up does *not* reach fastboot here.)
- The Volume-Down button is **inside the case**, next to a small **power button**
  on the board (the Pod has no external buttons). Open the case with **a few
  screws** on the base to reach them — a one-time thing. You can power-cycle with
  that internal power button instead of unplugging, but entering fastboot still
  needs Volume-Down held *as power is applied*.
- For all later use you drive the Pod over USB/network (`adb reboot bootloader`),
  so once ADB is on you can close the case for good.
- Confirm: `fastboot devices` should list it.

## Step 1 — RAM-boot TWRP and enable ADB (once)

```bash
fastboot boot recovery/twrp-solstice-gen3-signed.img
```
This **RAM-boots** TWRP — it does **not** flash anything. TWRP has its own working
`adb`, so from your PC you now have a root shell on the Pod.

> **On the TWRP screen:** if it asks to **Keep Read Only vs Swipe to Allow
> Modifications → Keep Read Only.** Otherwise leave the TWRP screen alone — do not
> swipe to modify, do not accept any "Install TWRP app" prompt, and do not use
> Wipe / Format / Restore. Everything below is done from your PC over `adb`.

**Check `/data` is mounted and readable** (if the Pod uses encryption, TWRP may
show a decrypt-password prompt; if so these edits won't work as written and you'll
need to handle decryption first):
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
Use **your own** `~/.android/adbkey.pub` — never anyone else's key. (If it doesn't
exist, run `adb keygen ~/.android/adbkey`, or just run `adb devices` once.)

- **If stock comes up with no ADB:** the usual causes are (a) step 1's verify
  didn't print `value="1"` — the setting is missing or oddly formatted; (b) the
  usb config isn't exactly `diag,adb`; (c) the key file's owner/mode/**SELinux
  label** is wrong (must be `system:shell`, `640`, label `adb_keys_file`). Re-boot
  TWRP and redo the failing one — nothing here is a brick.
- **Revert:** set `adb_enabled` back to `0` and delete `/data/misc/adb/adb_keys`.

Reboot to stock (`adb reboot` from TWRP). Within ~10 s the Pod appears in `adb
devices` — move fast, the Solstice app is about to disable ADB (Step 2).

---

## Step 2 — Make ADB permanent (disable the Solstice server)

```bash
adb shell pm disable-user --user 0 com.mersive.solstice.server
adb shell pm uninstall --user 0 com.mersive.solstice.server   # frees ~300 MB of its user data
```
`uninstall --user 0` removes it for the user but leaves the APK on `/system`, so
dm-verity stays intact. **Do not** try to delete it from `/system`.

- **If ADB keeps dropping ~10 s after boot:** Solstice is still enabled — re-run
  the `disable-user` line during the boot-time ADB window, or from TWRP set
  `enabled="3"` on its entry in `/data/system/users/0/package-restrictions.xml`
  (if it has no entry yet, disable it from stock with the command above instead).
- **Revert:** `adb shell cmd package install-existing com.mersive.solstice.server`
  then `adb shell pm enable com.mersive.solstice.server`.

Verify it's a normal, ADB-reachable Android device:
```bash
adb shell getprop sys.boot_completed      # 1 = booted
adb shell getprop ro.build.fingerprint    # .../msm8996:7.1.1/...
adb shell dumpsys window | grep mCurrentFocus
```

**(Optional) ADB over the network.** Convenient, but understand the risk: **port
5555 gives full, unauthenticated-at-the-network-layer control of the device and is
actively scanned.** Only on a trusted LAN.
```bash
adb tcpip 5555                       # this session only; reverts on reboot (prefer this)
adb connect <POD_IP>:5555
```
Making it survive reboots (`printf 5555 > /data/property/persist.adb.tcp.port`
from a root shell, same owner/label as the other property files) leaves it
permanently exposed and has **no clean one-command revert** — delete that file
from a root shell to undo it. Prefer the non-persistent form above.

---

## Step 3 — Install a launcher (Projectivy)

Best free Android-TV launcher. Latest needs Android 10+, but **v4.63 supports
Android 6.0+**, which fits. Official APKs: the developer's GitHub releases
(`spocky/miproja1`). Get `ProjectivyLauncher-4.63-*.apk` (verify the publisher).

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
- **If Home shows a chooser / old launcher:** set it again with `set-home-activity`
  or `pm uninstall` the other launcher (keep one installed so you're never
  home-less).
- **Revert:** `adb uninstall com.spocky.projengmenu`.

---

## Step 4 — App stores (F-Droid + Aurora) and unknown sources

```bash
adb shell settings put secure install_non_market_apps 1
adb install -r F-Droid.apk        # f-droid.org/F-Droid.apk
adb install -r AuroraStore.apk    # F-Droid repo: com.aurora.store (v4.8.4+, minSdk 23)
```
`install_non_market_apps` is a single global toggle on Android 7; it is only needed
so the **on-device** stores can install apps (it is **not** needed for your
`adb install` commands). Leaving it on is a mild risk; turn it back off if you only
install via ADB.
- **Revert:** uninstall the two packages and
  `settings put secure install_non_market_apps 0`.

---

## Step 5 — YouTube via TizenTube (and the clock trap)

Get `cobalt-arm.apk` (32-bit, ~80 MB, minSdk 24) from the `reisxd/TizenTubeCobalt`
GitHub releases (verify the publisher/version).
```bash
adb install -r cobalt-arm.apk
```
**Clock trap:** these often boot to **1969**; a wrong clock makes every HTTPS
certificate look invalid and online apps fail with "certificate" errors. It is the
date, not a cert problem. Fix:
```bash
adb shell settings put global auto_time 1
adb shell settings put global auto_time_zone 1
adb reboot                       # reboot WITH internet; NTP sets the clock
adb wait-for-device && adb shell date   # should be correct now
```
- **Symptom if still wrong:** apps load blank / TLS errors. Check `adb shell date`
  and that the Pod has internet (Ethernet or Wi-Fi).
- **Revert:** `adb uninstall io.gh.reisxd.tizentube.cobalt`.

---

## Step 6 — (Optional) HDMI-input passthrough

The HDMI **input(s)** appear to Android as **camera devices**, so a small Camera2
app shows the input full-screen.

**Easiest — install the prebuilt APK** (in [`app/hdmiin.apk`](app/), ~21 KB). It is
signed with a **throwaway key generated for this project** (`CN=Pod TV HDMI In`) —
not a shared or personal key; it contains no private key. Verify it against
`SHA256SUMS` if you like.
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
`zip`, and **Python 3 with Pillow** (for the icon/banner). On first run it creates
its own throwaway signing key **outside** the repo (at
`~/.config/podtv-hdmiin/signing.jks`) so no key is ever committed. A self-built APK
has a different signature than the prebuilt, so uninstall one before installing the
other. To change the app id, edit `package` in `AndroidManifest.xml` and the
`src/com/podtv/hdmiin` path to match.

Usage: OK / D-pad switches inputs; it auto-closes to the launcher after ~6 s with
no signal. **Video works; audio does not** — the HDMI audio lands on an I2S path
that returned only silence in testing, so it's disabled in the source
(`AUDIO_ENABLED = false` in `MainActivity.java`; flip it and re-add the
`RECORD_AUDIO` permission if you want to experiment).
- **Revert:** `adb uninstall com.podtv.hdmiin`.

---

## Step 7 — Remote control from your PC (scrcpy)

[scrcpy](https://github.com/Genymobile/scrcpy) mirrors/controls the Pod over ADB
(it has no touchscreen):
```bash
scrcpy                      # USB
scrcpy -s <POD_IP>:5555     # network
```
If it fails with `libavformat.so.NN: cannot open shared object file`, your scrcpy
was built against a different ffmpeg than you have — install your distro's packaged
scrcpy and make sure that copy is first in `PATH`.

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
| Home screen gone / stuck on chooser | No/!default launcher | Reinstall a launcher and `set-home-activity` it |

**Golden rule — back up `boot` before you touch it** (also in Step 1). From a
TWRP/root shell:
```bash
adb shell "dd if=/dev/block/bootdevice/by-name/boot of=/tmp/boot-backup.img bs=1M"
adb pull /tmp/boot-backup.img    # keep it safe; this un-bricks the first row above
```

---

## Recovery / un-bricking

- **Enter fastboot with no working Android:** hold **Volume-Down while power is
  applied**. The button is inside the case — **a few screws** on the base open it
  (a small Phillips screwdriver); there's an internal power button next to it too.
  From a *working* Android you don't need the case: `adb reboot bootloader`.
- **From fastboot:** `fastboot boot recovery/twrp-solstice-gen3-signed.img` to
  RAM-boot recovery (never `flash` unsigned), or
  `fastboot flash boot <your-boot-backup>.img` to restore a known-good boot.
- A broken `/system` (verity) is the one failure a boot backup can't fix — see the
  Debugging table. Avoid it by never allowing modifications in TWRP.
- For normal use you never need the buttons — USB + network ADB +
  `adb reboot bootloader` cover everything while Android boots.

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
  `user.email`. Before the first commit, set a repo-local, non-identifying author
  and a GitHub `noreply` address, and enable GitHub's "block pushes that expose my
  email". Remember your GitHub account name itself is public.
  ```bash
  git init                    # run this INSIDE this folder, never a parent that holds private files
  git config user.name  "your-handle"
  git config user.email "your-handle@users.noreply.github.com"
  ```
- If you distribute a tarball/zip instead, strip owner metadata
  (`tar --owner=0 --group=0 --numeric-owner …`, `zip -X …`).
- Re-generate `SHA256SUMS` if you change any binary (`sha256sum app/hdmiin.apk
  recovery/*.img > SHA256SUMS`).
- See [`NOTICE.md`](NOTICE.md) **before redistributing the recovery image** — it
  carries third-party (GPL + proprietary) components with licensing obligations.

See [`NOTICE.md`](NOTICE.md) for licensing and third-party components. The app
store APKs are linked from their official upstreams rather than bundled, so you
always get current, authentic builds.
