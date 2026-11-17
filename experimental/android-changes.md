# On-device changes (LineageOS 18.1 on the Pod)

Summary of vendor-side edits (each has a `.pod-bak`/`.bak` on the device):
mixer_paths_tasha.xml routed to SEC_MI2S_RX; stock Pod ACDB calibration; vintf manifest
with Bluetooth HAL, livedisplay removed; livedisplay/fingerprint/dashd init disabled;
`ro.frp.pst=frp`, `persist.sys.disable_rescue=true`; USB ADB via `ro.adb.secure=0`.
Stock Mersive/Qualcomm files (ACDB, camera libraries) are proprietary and are NOT in this repo.
