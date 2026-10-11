#!/bin/bash
# usage: pipe.sh <mode: pattern|mirror> <dhu cfg> <seconds-after-surface> [play]   -> prints encoder session stats
L=$(mktemp -d)  # scratch dir for dhu fifo/log
mode=$1; cfg=$2; secs=$3; play=$4
adb -s $S shell "settings put global autopenn_mode $mode"
pkill -f desktop-head-unit; pkill -f "sleep 7200"; rm -f "$L/dhu.fifo"; mkfifo "$L/dhu.fifo"
adb -s $S shell "am force-stop com.google.android.youtube; am force-stop com.google.android.projection.gearhead; sleep 1; am start -a com.google.android.projection.gearhead.SETTINGS >/dev/null; sleep 3; input tap 1875 90; sleep 1; input tap 1755 320"
sleep 2; adb -s $S forward tcp:5277 tcp:5277
(sleep 7200 > "$L/dhu.fifo" &)
(cd ~/Library/Android/sdk/extras/google/auto && nohup ./desktop-head-unit -c config/$cfg < "$L/dhu.fifo" > "$L/dhu.log" 2>&1 &)
sleep 40
[ "$DISPOFF" = "1" ] && adb -s $S shell "cmd display power-off 0"
if [ "$play" = "play" ]; then
  D=$(adb -s $S shell "dumpsys display" | grep -E 'DisplayInfo\{"AutoPennMirror"' | grep -oE 'displayId [0-9]+' | tail -1 | grep -oE '[0-9]+$')
  adb -s $S shell "am start --display $D -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.youtube >/dev/null 2>&1; sleep 14; input -d $D tap 205 217"
  sleep $((secs-14))
elif [ "$play" = "anim" ]; then
  D=$(adb -s $S shell "dumpsys display" | grep -E 'DisplayInfo\{"AutoPennMirror"' | grep -oE 'displayId [0-9]+' | tail -1 | grep -oE '[0-9]+$')
  adb -s $S shell "am start --display $D -n com.penn.auto_penn/com.autopenn.mirror.AnimActivity >/dev/null 2>&1"
  sleep $secs
else sleep $secs; fi
[ "$DISPOFF" = "1" ] && adb -s $S shell "cmd display power-on 0"
pkill -f desktop-head-unit; pkill -f "sleep 7200"; sleep 8
adb -s $S shell "dumpsys media.metrics 2>/dev/null | grep -E 'c2\.(v4l2|android)\.avc\.encoder'" | python3 -I -c '
import sys,re
rows=[]
for line in sys.stdin:
    ts=re.search(r"\((10-[0-9]+ [0-9:.]+)\)",line).group(1)
    g=lambda k:(re.search(r"android\.media\.mediacodec\."+re.escape(k)+r"=([^,)]*)",line) or [None,None])[1]
    rows.append((ts,g("codec"),g("width"),g("height"),int(g("vencode.frames") or 0),int(g("lifetimeMs") or 0)/1000))
r=rows[-1]; print(r, "fps over lifetime=%.1f"%(r[4]/max(r[5],1)))'
