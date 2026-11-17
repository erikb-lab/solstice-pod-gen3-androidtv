# Contributing

Thanks for helping make this guide better. This is a small, single-maintainer
project, so anything below is appreciated - none of it is required.

## The most useful thing: report your unit

The guide was developed and tested on **one** Pod. Every confirmation (or
failure) from another unit makes it more trustworthy. If you try it:

- Open a **[Unit report](../../issues/new?template=unit-report.yml)** - "worked
  on mine" is a perfectly good report. Failures are even more valuable.
- Note your Pod's **base-label model** (e.g. SP8096 / Gen3) and the stock
  Solstice firmware version if you know it.

## Found something wrong?

- A broken step, a command that fails, a bad path → open a
  **[Bug report](../../issues/new?template=bug-report.yml)**.
- Not sure, or just want to ask → **[Discussions](../../discussions)**.

## Prebuilt downloads

The signed RAM-boot TWRP image and the HDMI-in APK are attached to the
**[latest release](../../releases/latest)**. Verify them against
[`SHA256SUMS`](SHA256SUMS) before use.

## Building the HDMI-in app yourself

`app/build.sh` builds the APK with a throwaway signing key it generates outside
the repo (so your build will have a different signature than the prebuilt one).
See the comments at the top of the script.

## Experimental LineageOS + kernel

Unit reports for the [`experimental/`](experimental/README.md) path are the most valuable
thing right now: which kernel image you booted (RAM boot or flashed), what worked
(Wi-Fi, Ethernet, Bluetooth, HDMI audio) and what did not. Build steps are in
[`experimental/BUILD.md`](experimental/BUILD.md). Never post boot backups, serials or IPs.

## Pull requests

PRs are welcome for clarity fixes, additional-unit notes, and corrections. Please
keep shell commands byte-exact unless you're fixing an actual error, and explain
*why* in the PR. Don't add personal data (serials, IPs, Wi-Fi, keys) to anything.

## Ground rules

- Only ever `fastboot boot` the TWRP image - never `fastboot flash` it, and never
  let TWRP mount `/system` read-write. Both brick the Pod. (See the README.)
- Only work on a Pod you own.
