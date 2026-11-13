# NOTICE — licensing and third-party components

This project is unofficial and **not affiliated with, endorsed by, or supported
by Mersive, Qualcomm, or any app vendor named here**. All trademarks belong to
their respective owners. Everything is provided **as-is, with no warranty**; see
the Disclaimer in `README.md`.

## `recovery/twrp-solstice-gen3-signed.img`

Exactly what this image is, so nothing is hidden:
- **Kernel:** the Pod's **stock Linux kernel (GPLv2)** plus its appended device
  trees — byte-identical to the kernel in the Pod's factory boot image.
- **Ramdisk:** the **unmodified official TWRP 3.1.1-0 recovery ramdisk built for
  the OnePlus 3** (`omni_oneplus3`, 2017). It therefore also contains OnePlus 3 /
  Qualcomm proprietary blobs (e.g. `keystore.msm8996.so`, `libQSEEComAPI.so`,
  `qseecomd`) and the official **TWRP helper app** (`sbin/me.twrp.twrpapp.apk`),
  which declares INTERNET / location / phone-state permissions. That app is
  **inert** unless you accept TWRP's on-screen "Install TWRP app" prompt (don't).
- **Signature:** a 1320-byte trailer using the **public AOSP test verity
  certificate** (public material only — **no private key is included**). "Signed"
  here means it satisfies this bootloader's `fastboot boot` check; it is not a
  Mersive vendor signature.
- Compare it yourself: see `SHA256SUMS`, diff the ramdisk against upstream
  `twrp-3.1.1-0-oneplus3.img`, and read the init scripts (they start only
  ueventd/healthd/adbd/recovery — no network services).

**Licensing & redistribution:** the GPLv2 kernel obligates whoever distributes
this image to also provide the corresponding kernel source (or a written offer).
The source derives from the Qualcomm `msm-3.18` tree (CodeAurora/CAF); Mersive's
exact tree is not publicly posted here. It also carries OnePlus/Qualcomm
proprietary blobs with no redistribution licence. **If you mirror this repo,
either host this image as a separate download with a matching kernel-source
link/offer, or replace it with a build-it-yourself recipe.** It is not affiliated
with or endorsed by Mersive, OnePlus, or Qualcomm, and must not be used on hardware
you do not own.

**Written offer for source (GPLv2 §3(b)):** For at least three (3) years from the
date this image was published in this repository, the maintainers will, on
request, provide the complete corresponding machine-readable source code for the
GPL-licensed Linux kernel contained in `recovery/twrp-solstice-gen3-signed.img`,
for no more than the cost of physically performing distribution. **To request it,
open an issue on this repository.** The kernel corresponds to the Qualcomm
`msm-3.18` CodeAurora Forum (CAF) tree as shipped in the Pod's factory boot
image; where the exact vendor tree cannot be reproduced byte-for-byte, the
closest corresponding CAF source and the kernel configuration will be supplied.
This offer covers only the GPL kernel — the proprietary OnePlus/Qualcomm blobs in
the TWRP ramdisk are not GPL and carry no redistribution licence, so anyone
redistributing should strip them or host the image separately.

- Use it **only** with `fastboot boot` (RAM-boot). Never `fastboot flash` it.

## `app/` (HDMI-input passthrough)
- Original source and a prebuilt build of this project's own app, under the
  **MIT License** (see `LICENSE`); provided with no warranty. The prebuilt
  `app/hdmiin.apk` is signed with a **throwaway key generated just for this
  project** (`CN=Pod TV HDMI In, O=podtv`) — it is not a shared or personal key,
  contains no personal data, and the private key is not in the APK or this repo.
  `build.sh` generates its own throwaway key outside the repo, so a self-built APK
  will have a different signature than the prebuilt one.

## App packages referenced in the guide
These are **not bundled** — install them from their official upstreams so you get
current, authentic, correctly-licensed builds:
- **Projectivy Launcher** — `com.spocky.projengmenu` (developer's GitHub releases)
- **F-Droid** — `org.fdroid.fdroid` (f-droid.org)
- **Aurora Store** — `com.aurora.store` (F-Droid repo)
- **TizenTube Cobalt** — `io.gh.reisxd.tizentube.cobalt` (reisxd/TizenTubeCobalt GitHub)
- **scrcpy** — Genymobile/scrcpy

## Privacy
This bundle contains **no personal data**: no Wi-Fi networks or passwords, no IP
or MAC addresses, no device serials, no accounts, and no personal signing/ADB
keys. Each person uses their own ADB key and their own device.
