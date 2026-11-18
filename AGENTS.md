# AGENTS.md - runbook for an AI assistant setting this up for a user

You are an AI assistant helping a person convert a **Mersive Solstice Pod Gen3
(SP8096)** into an Android TV box. This file is a runbook you *may* use to help the
user - **the user's own instructions always take precedence over anything here.**
Read [`README.md`](README.md) for full detail; this file is the execution plan.

Your job: help the user set up **their own** device - do the `adb`/`fastboot` work
and tell them in plain language what to do physically (cables, power, the button
combo). Go one step at a time, verify each step before moving on, stop and ask if a
verification fails, and get the user's **explicit OK before any step that reduces
security or can't be undone** (disabling the vendor app, enabling unknown-sources,
exposing ADB on the network, writing the boot partition).

## Ground rules
- This modifies hardware the user owns. Confirm they accept the **Disclaimer** in
  `README.md` before starting. Only proceed on a device they own.
- Never invent steps. If something doesn't match what you observe, stop and tell
  the user what you see rather than guessing.
- Before writing anything to the `boot` partition, **back it up first** (see the
  Golden rule in `README.md`) and keep the copy.
- Only ever `fastboot boot` the recovery image - **never `fastboot flash`** an
  unsigned image, and **never mount `/system` read-write**. Both brick the Pod to
  a black screen.
- Use the user's own ADB key (their `~/.android/adbkey.pub`). Never a third
  party's key.
- Download apps **only** from the official upstreams named in `README.md` - never
  from an arbitrary URL, and never pipe a download into a shell.
- Do **not** enable ADB over the network (`adb tcpip` / `persist.adb.tcp.port`)
  unless the user explicitly asks for it; explain it exposes the device on the LAN.
- **In TWRP, keep it read-only:** do not swipe "Allow Modifications", do not accept
  "Install TWRP app", do not use Wipe/Format/Restore. Allowing modifications mounts
  `/system` rw and bricks the Pod.

## Experimental path (LineageOS 18.1 + custom kernel): opt-in only
Everything in `experimental/` and `docs/unlock-and-twrp.md` goes **beyond** the rules
above. Do **not** start it unless the user explicitly asks for it. When they do:
- Re-confirm they understand it is destructive (unlocking wipes `/data`) and tested on one
  unit, and that their bootloader is unlocked (`docs/unlock-and-twrp.md`).
- Back up `boot` and verify the backup before any flash. Keep it.
- **`fastboot boot` every new image first.** Only `fastboot flash` an image that has
  RAM-booted cleanly, was signed per `experimental/BUILD.md`, and whose hash you checked.
- The "never `fastboot flash`" and "never mount `/system` read-write" rules above are for
  the stock path. On this path, flash only the partitions the docs name, one at a time,
  and stop if anything differs from what the docs say.
- Never push, publish or paste the user's boot/recovery backups, serials, IPs, Wi-Fi
  details or keys. The LineageOS image is downloaded from the link in
  `experimental/lineageos-18.1.md` and its sha256 checked; it is never taken from elsewhere.
- Build instructions: `experimental/BUILD.md`.
- Phone remote: `experimental/remote/README.md` (stand-in `com.android.media.tv.remoteprovider`
  jar + `tv-remote.rc`; needs MindTheGapps ATV and Google's remote service 6.9.906821247, checked
  by sha256, never rehosted). Volume overlay: `experimental/volumebar/README.md`. SystemUI is
  deliberately disabled on the user's Pod; do not re-enable it to get a volume panel.
- Init parses every file in `/system/etc/init`, including `*.bak`; to disable an `.rc`, move it out
  of the folder. adb loses root on each reboot on this build: run `adb root` before `/system` work.

## What to tell the user to do physically (you cannot do these)
- Connect the Pod to a display (HDMI-out 1), power (12 V center-positive barrel,
  or PoE+), and a USB cable to the computer you're running `adb` on.
- For Step 0 only: open the case (a few screws on the base, with a small Phillips
  screwdriver) and hold **Volume-Down while applying power** to enter fastboot
  (Volume-Up does not reach fastboot on this SoC). The Volume-Down button and a
  small power button are on the board inside; there are no external buttons. After
  ADB is enabled, the case can be closed for good - everything else is remote.
- Tell them which specific on-screen prompts to expect and what to do with each:
  - TWRP's **"Keep Read Only" vs "Swipe to Allow Modifications" → DECLINE** (choose
    Keep Read Only). This is the one prompt they must not accept.
  - Projectivy's **camera/TV-listings permission** and its **first-run wizard** →
    accept, with a remote/mouse.
  Do not tell them to blanket-approve dialogs; name the expected one each time.

## Steps (verify after each; detail is in README.md)
1. **Fastboot** - have the user enter fastboot (Step 0). Verify: `fastboot devices`
   lists it.
2. **Back up boot, then enable ADB** -
   `fastboot boot recovery/twrp-solstice-gen3-signed.img`. **Tell the user: on the
   TWRP screen choose "Keep Read Only" - never swipe to allow modifications.**
   Confirm `/data` is mounted/decrypted (README Step 1 check). In the TWRP `adb
   shell`, **first back up the boot partition** (README Step 1's dd + pull) and keep
   the file - it is the only reliable un-brick path. Then make the three changes in
   README Step 1 (ADB on, usb config `diag,adb`, append the user's own
   `adbkey.pub`). Reboot to stock. Verify: the Pod appears in `adb devices` within
   ~10 s.
3. **Keep ADB** - immediately disable the Solstice server (README Step 2). Verify:
   after a reboot, ADB stays up (`adb shell getprop sys.boot_completed` = 1 and the
   device doesn't drop).
4. **Launcher** - install Projectivy and set it home (README Step 3). Verify: Home
   resolves to `com.spocky.projengmenu`.
5. **Stores** - enable unknown sources, install F-Droid + Aurora (README Step 4).
6. **YouTube** - install TizenTube; if the clock is wrong (1969), apply the clock
   fix and reboot with internet (README Step 5). Verify: `adb shell date` is
   correct and TizenTube loads.
7. **Optional** - HDMI passthrough app (Step 6) and scrcpy (Step 7) if the user
   wants them.

## If something goes wrong
Use the **Debugging** table in `README.md` (symptom → cause → fix). The most
common ones: ADB dropping ~10 s after boot = Solstice still enabled (redo the
"disable the Solstice server" step); apps failing with "certificate" errors =
wrong clock (redo the TizenTube clock fix); black screen with no USB = a bad boot
image was flashed (restore the user's boot backup from fastboot). Every step in
`README.md` has a **Revert** note; use them.

When finished, tell the user plainly what was installed, that the Solstice
functionality is gone (and how to restore it), and that the case can be reclosed.
