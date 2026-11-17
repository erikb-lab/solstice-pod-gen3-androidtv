#!/usr/bin/env bash
# Build the experimental Pod kernel (LineageOS OnePlus 3 lineage-18.1 tree, branch "pod")
# with the Pod's own stock kernel config as the base. Host-side only; nothing touches the Pod.
#   stage "config": seed pod_defconfig from the stock config + olddefconfig
#   stage "build" : Image.gz + the Pod DTB (apq8096-v3-sbc-pod.dtb)
# usage: build-pod-kernel.sh [config|build|all]   (default all)
# Undo: rm -rf $POD_WORKDIR/kbuild-pod $POD_WORKDIR/toolchains/shim
set -uo pipefail
T=$POD_WORKDIR
SRC=$T/los-kernel-18.1/src
TC=$T/toolchains
OUT=$T/kbuild-pod
SHIM=$TC/shim
STAGE=${1:-all}
JOBS=$(nproc)

# --- shim: the AOSP gcc wrapper is a python2 script; call the real binaries directly
mkdir -p "$SHIM" || exit 1
mkshim() {  # prefix realbindir realprefix
  local pfx=$1 bd=$2 rp=$3 t
  for t in ar as ld ld.bfd ld.gold nm objcopy objdump ranlib readelf size strings strip addr2line elfedit; do
    [ -x "$bd/$rp-$t" ] && ln -sf "$bd/$rp-$t" "$SHIM/$pfx-$t"
  done
  for t in gcc cpp; do
    printf '#!/bin/sh\nexec %s/real-%s-%s "$@"\n' "$bd" "$rp" "$t" > "$SHIM/$pfx-$t"
    chmod +x "$SHIM/$pfx-$t"
  done
  # cpp is a driver symlink in the real tree; fall back to gcc -E
  if [ ! -x "$bd/real-$rp-cpp" ]; then
    printf '#!/bin/sh\nexec %s/real-%s-gcc -E "$@"\n' "$bd" "$rp" > "$SHIM/$pfx-cpp"; chmod +x "$SHIM/$pfx-cpp"
  fi
}
mkshim aarch64-linux-android "$TC/aarch64-4.9/bin" aarch64-linux-android
mkshim arm-linux-androideabi "$TC/arm-4.9/bin" arm-linux-androideabi
export PATH="$SHIM:$PATH"
"$SHIM/aarch64-linux-android-gcc" --version 2>&1 | head -1 || { echo "BUILD_RESULT=FAIL (cross gcc)"; exit 1; }

# host tools: modern gcc 15 vs a 2018 kernel's scripts/
HOSTFLAGS="-fcommon -std=gnu11 -Wno-error -Wno-implicit-function-declaration -Wno-incompatible-pointer-types -Wno-int-conversion"
M=(make -C "$SRC" O="$OUT" ARCH=arm64 SUBARCH=arm64 CROSS_COMPILE=aarch64-linux-android- CROSS_COMPILE_ARM32=arm-linux-androideabi- \
   HOSTCC=gcc "HOSTCFLAGS=$HOSTFLAGS" "KCFLAGS=-Wno-error" -j"$JOBS")

mkdir -p "$OUT" "$OUT/arch/arm64/boot/dts/qcom" || exit 1

if [ "$STAGE" = config ] || [ "$STAGE" = all ]; then
  echo "== config"
  [ -f "$T/podkernel/pod.config" ] || { echo "missing stock config"; exit 1; }
  cp "$T/podkernel/pod.config" "$SRC/arch/arm64/configs/pod_defconfig" || exit 1
  "${M[@]}" pod_defconfig 2>&1 | tail -5 || exit 1
  # board-specific adjustments on top of the stock config
  S="$SRC/scripts/config"
  "$S" --file "$OUT/.config" -e MSM_DBA -e MSM_DBA_ADV7533 -e PSTORE -e PSTORE_RAM -e PSTORE_CONSOLE -e TEST_POWER \
       --set-str LOCALVERSION "-pod" -d LOCALVERSION_AUTO \
       -d QPNP_SMBCHARGER -d QPNP_FG -d ONEPLUS_FASTCHG   # phone-only power/charger drivers (Pod has no battery); OP3 tree links them against OnePlus-only symbols
  "$S" --file "$OUT/.config" -e NETPOLL -e NETCONSOLE -e NETPOLL_TRAP
  # WiFi (QCA6174 on PCIe0, built-in qcacld-2.0 like the OP3) and Bluetooth power/rfkill
  "$S" --file "$OUT/.config" -e CNSS_PCI -e QCA_CLD_WLAN -e MSM_BT_POWER
  # Size budget: the bootloader puts the DTB 32 MB above the kernel base, so text_offset + image_size
  # (Image plus bss) must stay under 0x2000000 or the kernel wipes its own DTB and dies before console.
  # Drop phone-only / unused code to make room for the built-in WiFi driver.
  "$S" --file "$OUT/.config" -d KALLSYMS_ALL -d CORESIGHT -d SCSI_UFS_QCOM -d SCSI_UFSHCD -d NFC_NQ \
       -d TOUCHSCREEN_ATMEL_MXT -d TOUCHSCREEN_ATMEL_MAXTOUCH_TS -d WIL6210 \
       -d RNDIS_IPA -d ECM_IPA -d RMNET_IPA -d IPA -d MSM_RMNET_MHI -d MSM_MHI
  "${M[@]}" olddefconfig 2>&1 | tail -5
  grep -E "^CONFIG_(MSM_DBA|MSM_DBA_ADV7533|FB_MSM_MDSS|FB_MSM_MDSS_HDMI_PANEL|MMC_SDHCI_MSM|PSTORE_RAM|SCSI_UFS_QCOM)=" "$OUT/.config"
  echo "CONFIG_RESULT=OK"
fi

if [ "$STAGE" = build ] || [ "$STAGE" = all ]; then
  echo "== build (log: $OUT/build.log)"
  "${M[@]}" Image.gz > "$OUT/build.log" 2>&1
  rc=$?
  tail -8 "$OUT/build.log"
  if [ $rc -ne 0 ]; then echo "BUILD_RESULT=FAIL rc=$rc"; exit 1; fi
  ls -la "$OUT/arch/arm64/boot/Image.gz" 2>&1
  END=$(python3 -I -c "import gzip,struct,sys;d=gzip.open(sys.argv[1]).read(64);print(struct.unpack_from('<Q',d,8)[0]+struct.unpack_from('<Q',d,16)[0])" "$OUT/arch/arm64/boot/Image.gz")
  printf 'in-memory kernel end: 0x%x (limit 0x2000000)\n' "$END"
  if [ "$END" -ge $((0x2000000)) ]; then echo "BUILD_RESULT=FAIL (kernel too large, would overwrite the DTB at boot)"; exit 1; fi
  echo "BUILD_RESULT=OK"
fi
# Undo: remove $OUT and $SHIM (see header). The source tree edits live on git branch "pod".
