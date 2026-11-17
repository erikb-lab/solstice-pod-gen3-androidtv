# Pod kernel (3.18.124-pod+ #23, "image 28")

Base: LineageOS `android_kernel_oneplus_msm8996`, branch `lineage-18.1`, commit
`b2a6b3b5`. `kernel/0001-pod-board-and-bringup.patch` is the 21-file diff that built
the booted image (`git apply` on that commit). Files are listed in `kernel/FILES.txt`.
Also here: `kernel/apq8096-v3-sbc-pod.dts` and `kernel/pod_defconfig`.

**Test-only code is still in the patch** (RTC epoch hack in `hctosys.c`/`interface.c`,
`POD_DIAG` in `timerfd.c`/`alarmtimer.c`, fake battery in `test_power.c`, DEBUG defines).
Do not treat it as release quality. The unstaged camera edits (HDMI-in capture) are
deliberately NOT included: they break the LOS camera module.

Build/pack: `scripts/build-pod-kernel.sh`, then `scripts/pack-pod-boot.py` (set
`POD_WORKDIR`, and `VERITY_KEY` to the AOSP public test verity private key).

Binaries are attached to the draft release `pod-kernel-28` (not in git): see
`SHA256SUMS-assets` there. Packed: `boot_pod28_signed.img` / `_unsigned.img`.
Unpacked: `Image.gz`, `dtb.bin`, `ramdisk.gz`, `ramdisk-tree/`. DTBs: Pod builds,
LineageOS, and the 125 stock Mersive DTBs. Flash only with a stock backup in hand.
