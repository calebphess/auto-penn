#!/bin/bash
# start AA head unit server on Razr + DHU with a FIFO at $S/dhu.fifo
S=/private/tmp/claude-501/-Users-penn-Development-auto-penn/5b031f40-9ce1-4259-abcb-8b7817e3eb41/scratchpad
cfg=${1:-default_720p.ini}
pkill -f desktop-head-unit; pkill -f "sleep 7200"; rm -f $S/dhu.fifo; mkfifo $S/dhu.fifo
adb -s ZY22LBXXFN shell "settings put global autopenn_mode ${2:-mirror}; am force-stop com.google.android.projection.gearhead; sleep 1; am start -a com.google.android.projection.gearhead.SETTINGS >/dev/null; sleep 3; input tap 1020 192; sleep 1; input tap 810 590"
sleep 2; adb -s ZY22LBXXFN forward tcp:5277 tcp:5277
(sleep 7200 > $S/dhu.fifo &)
(cd ~/Library/Android/sdk/extras/google/auto && nohup ./desktop-head-unit -c config/$cfg < $S/dhu.fifo > $S/dhu.log 2>&1 &)
