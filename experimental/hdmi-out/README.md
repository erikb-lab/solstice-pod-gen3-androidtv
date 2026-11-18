# EXPERIMENTAL: HDMI 2.0 output + resolution / refresh-rate picker

> **Status: EXPERIMENTAL.** Tested on one Pod with one 1080p monitor. Writes to `/system`.

The Pod has two HDMI outputs. On LineageOS 18.1, one of them works out of the box: the
ADV7533 bridge, HDMI 1.4, fixed at 1080p60. The other is the SoC's native HDMI 2.0 port, the
"HDMI1 / 4K" output in Mersive's spec. It stayed black there: the screen gave no hotplug and
no EDID.

**Why it was black:** that port has an enable line on TLMM GPIO 127 ("MER6641 HDMI OUT1 enable"
in the stock firmware's `init.qcom.post_boot.sh`). Stock drives it high at boot; LineageOS
never does, so the port gets no HPD and no DDC. Setting GPIO 127 high brings the port up, with
hotplug and the screen's EDID read normally.

## What this adds

- **`podtv_hdmid`**: a small root helper started by init
  ([`daemon/podtv-hdmid.rc`](daemon/podtv-hdmid.rc)). It:
  - drives GPIO 127 high;
  - watches hotplug and re-applies your saved mode every time a screen is plugged in;
  - talks to the display HAL (`display.qservice` on vndbinder) to list and switch modes.

  It listens on `127.0.0.1:6469` and only answers root and the uid that owns the
  `com.podtv.hdmiout` package, checked against the caller's exact socket. A caller can only list
  and switch display modes. If the HDMI Out app is not installed, another app installed under
  that package name would get the same access, so install the app along with the helper.
- **HDMI Out app** (`com.podtv.hdmiout`, leanback launcher tile): lists every resolution and
  refresh rate the screen offers, grouped by resolution, and marks which mode is active and
  which is saved.
  - Picking a mode switches immediately and asks "Keep?". If you don't confirm within 15 s
    (or the screen went blank), it switches back.
  - **Auto** uses the best mode the screen reports.
- **All modes (screens with no EDID)**: cheap adapters, splitters, capture cards and some
  KVMs send no EDID or a broken one. The kernel then offers only 640×480.
  - When the helper sees a missing or invalid EDID, it loads a built-in EDID instead. That EDID
    lists every mode the msm HDMI driver supports up to 300 MHz, and the helper defaults to
    1080p60 with it.
  - This happens on its own, with no setting. The **All modes** row in the app forces the same
    list for screens that do send an EDID but leave modes out.
  - It switches back to the screen's own EDID when you turn the row off or unplug the cable.

> [!NOTE]
> **Untested on hardware:** the helper's caller check (exact-socket match, bounded requests,
> root-only settings files) was added after a security audit and has only been tested
> off-device. Everything else here was tested on a Pod at 1080p.

## Resolutions and refresh rates

What you can pick depends on the screen's EDID; the app shows exactly what the display HAL
offers.

| | Modes |
|---|---|
| **Video (CEA) modes, when the screen lists them; all of them with the all-modes EDID** | 3840×2160 @ 24/25/30 Hz (4096×2160 too, if the screen lists it); 1920×1080 @ 60/50/30/25/24 Hz; 1920×1080i @ 60/50; 1280×720 @ 60/50; 720×576 @ 50; 720×480 @ 60; 640×480 @ 60 |
| **PC modes (same rule)** | 800×600, 1024×768, 1280×1024, 1280×960, 1360×768, 1366×768, 1280×800, 1440×900, 1600×900, 1400×1050, 1680×1050, 1600×1200, 1920×1200, 2560×1600 (all 60 Hz) |
| **4K60** | 3840×2160 @ 50/60 Hz needs a screen whose EDID advertises it (HDMI 2.0, 594 MHz). The all-modes EDID stops at 4K30, to stay safe on HDMI 1.4 adapters. |

**Limits:**
- **Refresh tops out at 60 Hz.** The kernel's mode table has no 1080p120 / 1440p144 entries.
- **Only some PC timings are decoded.** The kernel's EDID parser ignores 75/85 Hz PC timings.
- Both limits need a kernel change, not an app change.
- **4K and all-modes are untested.** Only 1080p and below were tried on real hardware.

The ADV7533 port (HDMI 1.4) is not affected and stays at 1080p60.

## Install

Rooted adb is required (`adb root`). Put `podtv-hdmid.jar` and `podtv-hdmiout-*.apk` from the
EXPERIMENTAL [`remote-volumebar-1` pre-release](../../../../releases/tag/remote-volumebar-1) into this folder, or build them with `daemon/build.sh` and `app/build.sh`.
Then:

```
./install-hdmi-out.sh
```

It copies the helper and its init rc to `/system`, installs the app (with the Play Protect
toggle the self-signed APK needs), reboots, and prints a check. Undo steps are at the bottom
of the script.

## Command protocol (for scripts)

One line per connection to `127.0.0.1:6469`, as root, for example
`echo L | toybox nc 127.0.0.1 6469`:

| Send | Does |
|---|---|
| `L` | Prints connection state, monitor name, saved mode, all-modes flag, EDID source (`real` / `none` / `synthetic`), the active config, then one `MODE <idx> <w> <h> <milliHz>` per mode |
| `S <w> <h> <milliHz>` | Switch now, without saving |
| `P <w> <h> <milliHz>` / `P auto` | Save, and re-apply after every hotplug and boot |
| `F 1` / `F 0` | Force the all-modes EDID on / off |

Settings live in `/data/misc/podtv-hdmi/`.
