# Can the Solstice Pod Gen3 run newer Android (or mainline Linux)?

Short answer: not out of the box, and not from Mersive. A newer OS is a
**port**, not a download. The good news is that the two things that normally make
an MSM8996 port a research project are already solved on this device, so it is a
tractable multi-week effort rather than a dead end. This page lays out exactly
what is known, what is realistic, and where the work actually is.

Everything below was checked against the Pod's own boot image and against
Mersive's newest firmware, not guessed.

## TL;DR

- The Pod ships **Android 7.1.1** on a **Linux 3.18.31 (Qualcomm CAF) kernel**.
- Mersive's **newest** firmware (6.3.4, Sept 2026) is **still Android 7.1.1 on the
  exact same kernel**. There is no official path up.
- The same SoC in the best-supported phone (OnePlus 3) tops out at **Android 11
  official / 12 unofficial**. That is the realistic ceiling for the Pod too.
- The bootloader trusts the **public AOSP test key** and the device already
  **RAM-boots the stock kernel + its correct device tree**, so bring-up starts
  past the usual hardest steps.
- Biggest single unknown: whether the bootloader lets you **flash** a re-signed
  image to eMMC, not just RAM-boot it. That experiment decides everything.

## The hardware

| | |
|---|---|
| SoC | Qualcomm Snapdragon 820 (MSM8996), Kryo quad-core + **Adreno 530** |
| RAM / storage | 4 GB / 16 GB eMMC |
| OS (stock) | Android 7.1.1, device `msm8996`, codename `stonehenge` |
| Kernel (stock) | `Linux 3.18.31-perf #713, built 2022-10-13, gcc 4.9` (Qualcomm CAF `msm-3.18`) |
| Video out | HDMI (this is a TV/appliance, not a phone with a DSI panel) |
| HDMI in | 2x Toshiba **TC358840** HDMI-to-CSI bridges, exposed to Android as cameras |

## What the newest stock firmware proves

Mersive's current release (`Solstice 6.3.4`, build `39219`, 2026-09-21) was pulled
apart and checked. It is a standard block-based Android OTA. Findings:

1. **Still Android 7.1.1.** Build fingerprint:
   `Android/stonehenge/msm8996:7.1.1/6.3/39219:user/release-keys`. The 6.3.4
   changes are app-level (an AirPlay SDK update, bug fixes), not an OS bump.
   Mersive has never moved the Gen3 off 7.1.1.
2. **Same kernel, unchanged.** The 6.3.4 `boot.img` kernel version string is
   **byte-identical** to the one already on the device: `3.18.31-perf #713,
   2022-10-13`. The newest firmware ships a kernel they have not even recompiled
   in years. Updating gains nothing at the kernel level.
3. **The self-sign path is still open.** The 6.3.4 `boot.img` verity footer still
   carries the **public AOSP test certificate** (`O=Android, CN=Android`,
   valid 2014-2042). That is the same key this project's RAM-boot method relies
   on, so updating to the latest stock does **not** patch out the
   `fastboot boot`-a-signed-image technique. The OTA zip itself is signed with
   Mersive's own key (`O=Mersive`), but that only gates the recovery installer,
   not the bootloader's trust.
4. **It is a useful port donor.** The package provides `boot.img`, the full
   system image, the modem (`NON-HLOS.bin`) and Bluetooth firmware, the **venus
   video-decode firmware**, and the SELinux `file_contexts`, and it writes them by
   partition name (`/dev/block/bootdevice/by-name/{system,boot,modem,bluetooth}`).
   That is a clean source of vendor blobs, the exact partition layout, and an
   un-brick baseline.

So: get the firmware to **mine for blobs and as a safety net**, not as a shortcut
to newer Android.

## The realistic ceiling (from the same SoC elsewhere)

The Pod's MSM8996 is the same SoC as the **OnePlus 3 / 3T** (`oneplus3`), which is
the best-supported MSM8996 device there is. Even it caps out at:

- **Android 11** via official **LineageOS 18.1** (last official build early 2024,
  now marked unmaintained).
- **Android 12** via unofficial **LineageOS 19.1** (community, activity into 2024).
- **Android 13+ is effectively dead** on MSM8996: no credible maintained LOS
  21/22. The blocker is the **3.18 CAF kernel**; newer Android expects newer
  kernel/vendor interfaces, and the community 4.4 forward-port was never finished.

If the best-supported MSM8996 phone caps at 11/12, that is the honest ceiling for
the Pod too.

## Two different paths (do not mix them)

**Path A: mainline Linux (postmarketOS / "Nura").** MSM8996 is genuinely
well-supported in the mainline kernel: CPU, **Adreno 530 via freedreno**, DSI and
**HDMI output (landed kernel 5.2)**, eMMC/UFS, USB, WiFi/BT all work. You get a
GPU-accelerated little Linux box (Phosh/Plasma). What is weak or missing:
**hardware video decode (venus is N/A mainline)**, cameras, cellular. For a TV box
the venus gap matters (software decode of 4K is rough).

**Path B: newer Android (LineageOS family).** These do **not** use mainline. They
forward-port Qualcomm's CAF kernel (3.18, or rebased to 4.4) and keep the vendor
blobs, so hardware video decode, GPU, etc. keep working (the very venus firmware
in the 6.3.4 package is what you would reuse). This is the path that gets you a
usable Android TV box on newer Android, at the 11/12 ceiling above.

Mental model: **Path A = modern Linux, weak media/no HW decode. Path B =
modern-ish Android that keeps the media stack by staying in Qualcomm's blob
world.**

## What is already solved for the Pod, and what is left

Already done, which is why this is not a from-scratch bring-up:

- **A booting kernel + correct device tree already exists** (the stock one). The
  hardest, most device-specific artifact in an MSM8996 port, you have.
- **Signing is open.** `secure:no` plus the trusted public AOSP test key means you
  can sign your own boot/vbmeta with a public key. No unlock, no vendor key.
- **Recovery already works** (this repo ships an OP3-ramdisk TWRP that RAM-boots on
  the Pod kernel), proving how close the platforms are.

What is left, roughly in order:

1. **The make-or-break experiment: can you flash?** RAM-boot working does not prove
   `fastboot flash` (or a recovery `block_image_update`) to eMMC is allowed on this
   appliance bootloader. Flash a trivially modified, re-signed boot image and see
   if it persists and boots. Everything downstream depends on this.
2. **Retarget display to HDMI.** Start from the OP3 tree, but the Pod drives HDMI,
   not a phone DSI panel. Make the display HAL / resolution target HDMI.
3. **Fix the partition map / fstab** to the Pod's real layout (the 6.3.4 package
   gives you the by-name partitions). Wrong fstab = nothing mounts.
4. **Strip the phone-isms** from the OP3 device tree: telephony/RIL, touch, phone
   cameras, DSI panel.
5. **Grind the long tail:** SELinux policy (run permissive first), audio mixer +
   HDMI audio, WiFi/BT firmware pairing, and the Pod's own peripherals.
6. **HDMI input** (optional, see below).

## The HDMI-input question (TC358840)

The two HDMI-in bridges are **TC358840** chips. That driver is **not** in mainline,
but its sibling **TC358743 is** (since kernel 4.2), and downstream TC358840 drivers
exist (NVIDIA Jetson L4T, Antmicro, Facebook Portal), all descended from a
near-mainline 2016 series. So the chip half is a **port-and-clean-up** job, not
new development. The real work is wiring the subdev into Qualcomm's `camss` media
graph (CSIPHY lane/clock config) and, on Android, bridging the V4L2 node into a
camera HAL. Watch out for **HDCP**, which these receivers do not do as repeaters
and can be an outright wall depending on the source, and **EDID/timings** setup.
Verdict: a custom-integration problem, not a solved-driver one, but you are not
starting from zero.

## Prior art and history (important, often confused)

The **original** Mersive Solstice Pod (the small earlier unit) was a rebadged
**Razer Forge TV**: a **Snapdragon 805 (APQ8084)** Android TV microconsole that
Razer abandoned around 2019. The community kept the Forge alive with unofficial
**LineageOS 16 (Android 9)** (codename `pearlyn`) and TWRP, and people documented
converting a Forge-based Solstice Pod **back into a Forge**.

**That lineage is not this device.** The Gen3 (SP8096) is **Snapdragon 820 /
MSM8996**, a completely different and later SoC with **no Forge heritage and no
pre-existing community ROM**. The old small Pod got community love because it was a
Forge; the 820 generation was simply never explored. That is what makes this work
new, and it is the correct answer if anyone says "the Solstice Pod has been
rommed for years" (they mean the SD805 Forge-Pod, not this one).

## Effort verdict

For a capable hobbyist: a **multi-week project**, not a weekend, not a full
research effort. The two usual MSM8996-port killers (reverse-engineering a
kernel/DTB, defeating a locked chain of trust) are both already solved here. The
single most likely blocker is **flash persistence** (step 1 above). If flashing
works, the next hardest piece is HDMI display bring-up under a newer LineageOS
userspace on the stock-era kernel.

## Sources

- OnePlus 3 ceiling: [LineageOS wiki (oneplus3)](https://wiki.lineageos.org/devices/oneplus3/), [device tree](https://github.com/LineageOS/android_device_oneplus_oneplus3), [kernel (3.18.124)](https://github.com/LineageOS/android_kernel_oneplus_msm8996)
- MSM8996 mainline: [Qualcomm mainline status](https://linux-msm.github.io/mainline-status/), [MSM8996 mainlining (pmOS)](https://wiki.postmarketos.org/wiki/MSM8996_Mainlining), [msm8996-mainline/linux](https://gitlab.com/msm8996-mainline/linux/-/tree/msm8996-staging)
- TC358840/743: [mainline tc358743](https://github.com/torvalds/linux/blob/master/drivers/media/i2c/tc358743.c), [TC358840 RFC (LWN)](https://lwn.net/Articles/680945/), [downstream 840 (Jetson)](https://developer.ridgerun.com/wiki/index.php/Toshiba_TC358840_Linux_driver_for_Jetson_TX1_and_TX2), [AOSP external cameras](https://source.android.com/docs/core/camera/external-usb-cameras)
- Verified boot / signing: [AVB README](https://android.googlesource.com/platform/external/avb/+/master/README.md), [dm-verity](https://source.android.com/security/verifiedboot/dm-verity)
- Razer Forge history: [Forge TV specs](https://9to5google.com/guides/razer-forge-tv/), [LineageOS 16 for Forge](https://www.xda-developers.com/lineageos-16-razer-phone-razer-forge-tv/), [Solstice Pod / Forge thread](https://xdaforums.com/t/soltice-pod-razer-forge-tv.4212103/)

<sub>Findings on the stock kernel and the 6.3.4 firmware were taken directly from the device's boot image and Mersive's published firmware package. Compatibility estimates for newer Android are drawn from the same-SoC OnePlus 3 and from MSM8996 mainline status, not from a build on this device; nobody has yet ported newer Android to this Pod.</sub>
