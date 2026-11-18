# EXPERIMENTAL: Volume Bar (on-screen volume without SystemUI)

> **Status: EXPERIMENTAL.** Tested on one Pod.

On this build SystemUI is disabled because it caused problems, so nothing draws a volume panel
when the volume changes (from the phone remote, a keyboard, or HDMI-CEC). Volume Bar is a tiny
app that does only that: a slim pill on the right edge with the level, shown for 2.5 s after each
change. It listens for `android.media.VOLUME_CHANGED_ACTION` and draws a
`TYPE_APPLICATION_OVERLAY` window; it has no network access and no UI of its own.

## Install

Get `podtv-volumebar-1.2.apk` from the EXPERIMENTAL release (or build it with `./build.sh`), then:

```
adb install podtv-volumebar-1.2.apk
adb shell appops set com.podtv.volumebar SYSTEM_ALERT_WINDOW allow
adb shell dumpsys deviceidle whitelist +com.podtv.volumebar
adb shell am start-foreground-service -n com.podtv.volumebar/.VolumeService
```

It starts itself at boot (BOOT_COMPLETED receiver; [`../remote/tv-remote.rc`](../remote/tv-remote.rc)
also starts it as a backup). If `adb install` fails with `INSTALL_FAILED_VERIFICATION_FAILURE`,
Play Protect rejected the self-signed APK: set
`adb shell settings put global verifier_verify_adb_installs 0`, install, then set it back to `1`.

Remove with `adb uninstall com.podtv.volumebar`.
