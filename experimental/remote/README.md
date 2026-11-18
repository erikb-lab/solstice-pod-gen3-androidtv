# EXPERIMENTAL: phone remote (Google TV app / Nope Remote) on LineageOS 18.1

> **Status: EXPERIMENTAL.** Tested on one Pod. It writes to `/system`. Keep a backup.

This lets the Google TV phone app (or any Android TV Remote v2 client, such as Nope Remote)
pair with the Pod and send keys, volume and text. It survives reboots.

## What you need

| Piece | Where | Hosted here? |
|---|---|---|
| LineageOS 18.1 Pod build + rooted adb | [`../lineageos-18.1.md`](../lineageos-18.1.md) | linked |
| MindTheGapps 11.0.0 arm64 **ATV** (provides the privileged `AtvRemoteService` base and its privapp permissions) | <https://github.com/MindTheGapps> | no, third-party |
| Android TV Remote Service **6.9.906821247** (armeabi-v7a, versionCode 95855536, Google-signed) | [APKMirror release page](https://www.apkmirror.com/apk/google-inc/android-tv-remote-service-android-tv/android-tv-remote-service-6-9-906821247-release/) | no, Google's app. sha256 `d87a4b86af915734789bddb9bf05ba1b5982ee73cf617283b91f09b9abf76052` |
| `com.android.media.tv.remoteprovider.jar` (this folder's source) | EXPERIMENTAL release, or `./build-remoteprovider.sh` | yes (release asset) |

## Why it needs a stand-in jar

The remote service talks to an Android TV framework library, `com.android.media.tv.remoteprovider`,
that phone builds like LineageOS for the OnePlus 3 do not have. This folder's jar replaces it:

- `TvRemoteProvider` forwards key presses to a small root helper (`KeyInjector`) on
  `127.0.0.1:6468`, which injects them through `InputManager`.
- **The part that matters:** remote service 6.x will not finish the connection handshake
  (no `remote_set_active` / `remote_start`, so the phone app connects and then nothing happens)
  until an input device named exactly `virtual-remote` appears. On a real TV the system creates
  it; here `KeyInjector` creates it with `/system/bin/hid` (uhid) when the bridge opens, once per
  remote-service process.
- Each device name gets its own USB product id. With identical ids Android merges
  `virtual-search` and `virtual-remote` into one input device and the "device added" event the
  service waits for never fires.

## Install

```
./install-remote.sh /path/to/android-tv-remote-service-6.9.906821247.apk
```

It checks the APK checksum, copies the jar, the library XML and [`tv-remote.rc`](tv-remote.rc)
into `/system`, reboots so the library is registered, installs the APK and starts the service.
Then pair from the phone; the code appears on the TV.

If your `/system` already defines a `tv_key_injector` service in another `.rc` file, remove that
definition first (init ignores duplicates). Note that init parses **every** file in
`/system/etc/init`, including `*.bak`, so move old files out of the folder instead of renaming them.

## Undo

The bottom of `install-remote.sh` lists the commands. Short version: delete
`/system/etc/init/tv-remote.rc`, restore the jar backup from `/data/local/tmp/remoteprovider.jar.orig`
if one was made, and `pm uninstall-system-updates com.google.android.tv.remote.service`.

## Known limits

- Play-services warnings in logcat ("requires the Google Play Store, but it is missing",
  Phenotype) are noise; they do not block the remote.
- Touchpad/pointer input is not implemented, only keys.
- adb drops root on every reboot on this build; run `adb root` before `/system` work.
