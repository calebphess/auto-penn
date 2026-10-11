#!/bin/bash
# usage: hw.sh on|off   (temporary hardware-encoder test setup on the Pi; "off" restores the safe software-encoder state)
S=192.168.86.24:5555; B=/Users/penn/Development/auto-penn/backups; APEXLIB=/apex/com.android.hardware.media.c2.v4l2/lib64/libv4l2_codec2_components.so
if [ "$1" = on ]; then
 adb -s $S push "$B/v4l2-apex/libv4l2_codec2_components.so.cpu_read_fix" /data/local/tmp/libv4l2_codec2_components.so >/dev/null
 adb -s $S push "$B/vendor-etc/media_codecs_v4l2_c2_video.xml.orig" /data/local/tmp/v4l2x.xml >/dev/null
 adb -s $S shell "chmod 644 /data/local/tmp/libv4l2_codec2_components.so; stop media; stop media.swcodec; stop vendor.media.c2.v4l2; sleep 3; mount | grep -q libv4l2_codec2_components || mount --bind /data/local/tmp/libv4l2_codec2_components.so $APEXLIB; mount -o remount,rw /vendor; cp /data/local/tmp/v4l2x.xml /vendor/etc/media_codecs_v4l2_c2_video.xml; chcon u:object_r:vendor_configs_file:s0 /vendor/etc/media_codecs_v4l2_c2_video.xml; sync; mount -o remount,ro /vendor; start vendor.media.c2.v4l2; start media.swcodec; start media; sleep 10; dumpsys media.player 2>&1 | grep -E 'Encoder \"c2\.[a-z0-9]+\.avc' | sed 's/ supports//'"
else
 python3 -I - <<PY
import re
s=open("$B/vendor-etc/media_codecs_v4l2_c2_video.xml.orig").read()
open("/tmp/v4l2_sw.xml","w").write(re.sub(r'\s*<Encoders>.*?</Encoders>','',s,flags=re.S))
PY
 adb -s $S push /tmp/v4l2_sw.xml /data/local/tmp/v4l2x.xml >/dev/null
 adb -s $S shell "stop media; stop media.swcodec; stop vendor.media.c2.v4l2; sleep 3; umount $APEXLIB 2>/dev/null; mount -o remount,rw /vendor; cp /data/local/tmp/v4l2x.xml /vendor/etc/media_codecs_v4l2_c2_video.xml; chcon u:object_r:vendor_configs_file:s0 /vendor/etc/media_codecs_v4l2_c2_video.xml; sync; mount -o remount,ro /vendor; rm -f /data/local/tmp/v4l2x.xml /data/local/tmp/libv4l2_codec2_components.so; start vendor.media.c2.v4l2; start media.swcodec; start media; sleep 10; dumpsys media.player 2>&1 | grep -E 'Encoder \"c2\.[a-z0-9]+\.avc' | sed 's/ supports//'"
 rm -f /tmp/v4l2_sw.xml
fi
