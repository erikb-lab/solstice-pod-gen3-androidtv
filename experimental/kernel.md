# Pod kernel (3.18.124-pod+ #23, "image 28")

Base: LineageOS `android_kernel_oneplus_msm8996`, branch `lineage-18.1`, commit
`b2a6b3b5`. `experimental/kernel/0001-pod-board-and-bringup.patch` is the 21-file diff that built
the booted image (`git apply` on that commit). Files are listed in `experimental/kernel/FILES.txt`.
Also here: `experimental/kernel/apq8096-v3-sbc-pod.dts` and `experimental/kernel/pod_defconfig`.

**Test-only code is still in the patch** (RTC epoch hack in `hctosys.c`/`interface.c`,
`POD_DIAG` in `timerfd.c`/`alarmtimer.c`, fake battery in `test_power.c`, DEBUG defines).
Do not treat it as release quality. The unstaged camera edits (HDMI-in capture) are
deliberately NOT included: they break the LOS camera module.

Build/pack (paths are relative to this folder): `experimental/scripts/build-pod-kernel.sh`, then `experimental/scripts/pack-pod-boot.py` (set
`POD_WORKDIR`, and `VERITY_KEY` to the AOSP public test verity private key).

Binaries are attached to the draft release `pod-kernel-28` (not in git): see
`SHA256SUMS-assets` there. Packed: `boot_pod28_signed.img` / `_unsigned.img`.
Unpacked: `Image.gz`, `dtb.bin`, `ramdisk.gz`, `ramdisk-tree/`. DTBs: Pod builds,
LineageOS, and the 125 stock Mersive DTBs. Flash only with a stock backup in hand.

## Sanitized binaries
The released `boot_pod28_*` images were built from the same source as the image running on
the author's unit. Embedded build user/host and `__FILE__` paths were replaced with
same-length neutral strings, then repacked with `scripts/pack-pod-boot.py`. Repacking the
unmodified pieces reproduces the author's flashed image byte for byte, so the pack step is
verified; the sanitized image itself has **not yet been boot-tested**. Its hash therefore
differs from the image on the author's unit.
