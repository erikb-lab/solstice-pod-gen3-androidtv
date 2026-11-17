# EXPERIMENTAL: LineageOS 18.1 + custom kernel on the Pod

> **Status: EXPERIMENTAL / test-grade.** Everything in this folder is new, tested on a
> single unit, and may brick it. The main guide and the signed TWRP in `recovery/` are
> separate and are not experimental. Flash only with a stock backup in hand.

- [`kernel.md`](kernel.md): kernel patch, DTS, defconfig, boot image build
- [`lineageos-18.1.md`](lineageos-18.1.md): the LineageOS image (linked, not hosted)
- [`android-changes.md`](android-changes.md): vendor-side edits
- Binaries (packed/unpacked boot images, DTBs): draft release `pod-kernel-28`

**Privacy:** the build host, user name and source paths that kernel builds normally embed were
replaced with neutral same-length strings (`pod@host`, `/usr/src/linux-3.18.124-pod-lineage-18.`).
No personal files, serials, IPs or credentials are included.
