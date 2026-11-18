# On-device changes (LineageOS 18.1 on the Pod)

Summary of vendor-side edits (each has a `.pod-bak`/`.bak` on the device):
mixer_paths_tasha.xml routed to SEC_MI2S_RX; stock Pod ACDB calibration; vintf manifest
with Bluetooth HAL, livedisplay removed; livedisplay/fingerprint/dashd init disabled;
`ro.frp.pst=frp`, `persist.sys.disable_rescue=true`; USB ADB via `ro.adb.secure=0`.
Stock Mersive/Qualcomm files (ACDB, camera libraries) are proprietary and are NOT in this repo.

Later additions: SystemUI disabled for user 0 (it caused problems; the volume panel is replaced
by [`volumebar/`](volumebar/README.md)); phone remote via a stand-in
`com.android.media.tv.remoteprovider` jar and `tv-remote.rc` ([`remote/`](remote/README.md));
LiveDisplay's `.rc` moved out of `/system/vendor/etc/init` (it crash-looped every 5 s, and init
parses `.rc.bak` files too); `/system/framework/podtv-hdmid.jar` + `/system/etc/init/podtv-hdmid.rc`
for the second HDMI output ([`hdmi-out/`](hdmi-out/README.md)).
