#!/bin/bash
# Flash the KonstaKANG LineageOS image to the microSD card (streams the zip straight to the card).
# Usage: sudo scripts/flash-sd.sh <image.zip> <sha256> <disk device, e.g. disk2>
set -euo pipefail
ZIP="$1"; SHA="$2"; DISK="$3"
[ "$(id -u)" = 0 ] || { echo "run as root"; exit 1; }
diskutil info "$DISK" | grep -q "Removable Media:.*Removable" || { echo "$DISK is not removable media, refusing"; exit 1; }
echo "$(date) verifying $ZIP"
echo "$SHA  $ZIP" | shasum -a 256 -c -
IMG_NAME="$(unzip -Z1 "$ZIP" | grep -E '\.img$' | head -1)"
echo "$(date) image inside zip: $IMG_NAME"
diskutil unmountDisk force "$DISK"
echo "$(date) writing to /dev/r$DISK"
unzip -p "$ZIP" "$IMG_NAME" | dd of="/dev/r$DISK" bs=4m
sync
echo "$(date) FLASH COMPLETE"
diskutil list "$DISK"
