# Build the experimental Pod kernel and boot image yourself

> **EXPERIMENTAL.** Read [`README.md`](README.md) in this folder first. Everything here
> runs on your PC; nothing touches the Pod until the last two sections. Only do this
> to a Pod you own, with an unlocked bootloader and your own stock `boot` backup.

## What you need

- A Linux PC with ~15 GB free, `git`, `make`, `gcc`, `python3`, `cpio`, `xz`, `openssl`,
  and the python `cryptography` module.
- **Toolchains:** AOSP prebuilt `aarch64-linux-android-4.9` and `arm-linux-androideabi-4.9`
  (the gcc 4.9 that LineageOS 18.1 used for this kernel). Put them under `$POD_WORKDIR/toolchains/`.
- **Your own stock boot image** (the Step 1 backup in the main guide). It supplies the boot
  header and the verity footer. Do not use someone else's.
- **The AOSP public test verity key** as a PEM (`verity.pk8` from AOSP
  `build/target/product/security/`, converted with `openssl pkcs8 -inform DER -nocrypt -in
  verity.pk8 -out verity.pem`). This is public AOSP material; the Pod's bootloader trusts it.
- The release assets (`pod-kernel-28`): `dtbs-pod-and-lineage.tar.xz` and
  `unpacked-pod28.tar.xz` (for the ramdisk).

```bash
export POD_WORKDIR=$HOME/pod-build        # everything below lives here
export VERITY_KEY=$HOME/keys/verity.pem
mkdir -p "$POD_WORKDIR" && cd "$POD_WORKDIR"
```

## 1. Get and patch the kernel source

```bash
git clone https://github.com/LineageOS/android_kernel_oneplus_msm8996.git los-kernel-18.1/src
cd los-kernel-18.1/src
git checkout -b pod b2a6b3b5          # the commit the patch was made against
git apply /path/to/this/repo/experimental/kernel/0001-pod-board-and-bringup.patch
cd "$POD_WORKDIR"
```

`git apply` must finish with no errors. The patch adds `apq8096-v3-sbc-pod.dts` and
`pod_defconfig`, plus the bring-up fixes listed in `kernel.md`.

## 2. Extract your stock kernel config

The build seeds from the Pod's own stock config. Pull it from your boot backup:

```bash
mkdir -p podkernel
# kernel.raw = the kernel inside your stock boot image (unpack it with any bootimg tool)
# the stock config is embedded; extract it with the kernel tree's own script:
los-kernel-18.1/src/scripts/extract-ikconfig kernel.raw > podkernel/pod.config
head -3 podkernel/pod.config            # should say Linux/arm64 3.18.x Kernel Configuration
```

## 3. Build

```bash
cp /path/to/this/repo/experimental/scripts/build-pod-kernel.sh .
bash build-pod-kernel.sh all
```

Success ends with `BUILD_RESULT=OK`. The script refuses to finish if the kernel is too
large: the bootloader places the DTB 32 MB above the kernel base, so text offset plus
image size must stay under `0x2000000` or the kernel wipes its own DTB and dies before
any console output. The output is `kbuild-pod/arch/arm64/boot/Image.gz`.

**The DTB is not built by the script.** Use `pod25.dtb` from `dtbs-pod-and-lineage.tar.xz`
(source: `pod25.dts`; it is the stock Pod DT with the changes in `kernel.md`).

## 4. Pack and sign

Use the ramdisk from the release (`unpacked-pod28/ramdisk.gz`), your stock boot image,
and the packer:

```bash
python3 -I experimental/scripts/pack-pod-boot.py \
  kbuild-pod/arch/arm64/boot/Image.gz  pod25.dtb  my-stock-boot.img  ramdisk.gz \
  boot_pod_unsigned.img  boot_pod_signed.img
```

The packer copies the page size, load addresses and command line from your stock image,
appends the DTB to the kernel, and signs with `VERITY_KEY` the same way the TWRP image
is signed. Set `POD_EXTRA_CMDLINE` to add kernel arguments for one build.

**Sanity check that proves your toolchain works:** repack the release's own
`Image.gz`, `dtb.bin` and `ramdisk.gz` with the same command. The unsigned output must
match the release `boot_pod28_unsigned.img` (sha256 in `SHA256SUMS-assets`).

## 5. Test without flashing (RAM boot)

Same as the main guide: the Pod's bootloader will `fastboot boot` an image signed with the
key it trusts, and it is gone after a reboot.

```bash
fastboot boot boot_pod_signed.img
```

Watch for: the Mersive splash, then LineageOS boot animation, then `adb devices` listing
the Pod. If it hangs, power-cycle; nothing was written.

## 6. Flash it (only if the RAM boot worked every time)

You need an unlocked bootloader (see [`docs/unlock-and-twrp.md`](../docs/unlock-and-twrp.md))
and a backup of the current `boot` partition. Verify the backup first.

```bash
fastboot flash boot boot_pod_signed.img
```

Restore: `fastboot flash boot <your-stock-boot-backup>.img`.

## 7. LineageOS system

The kernel needs a LineageOS 18.1 userspace: see [`lineageos-18.1.md`](lineageos-18.1.md)
for the exact download, its checksum, and the on-device edits in
[`android-changes.md`](android-changes.md). How the system partition itself was flashed
is not documented yet.

## Known limits

- HDMI-in capture does not work on this build.
- The patch still contains test-only code (RTC epoch hack, `POD_DIAG`, fake battery).
- The Pod has no RTC backup cell; time comes from NTP after each boot.
- Tested on one unit, on one kernel image.
