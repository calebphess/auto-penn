#!/bin/bash
# usage: razr-run.sh <play|idle> <secs> ; one controlled session, prints encoder stats
S=/private/tmp/claude-501/-Users-penn-Development-auto-penn/5b031f40-9ce1-4259-abcb-8b7817e3eb41/scratchpad
r() { adb -s ZY22LBXXFN "$@"; }
r shell am force-stop com.google.android.youtube
bash $S/razr-start.sh default_720p.ini mirror
sleep 25
r shell am broadcast -a com.autopenn.mirror.LAUNCH --es pkg com.google.android.youtube >/dev/null
sleep 15
D=$(r shell "dumpsys display" | grep -E 'DisplayInfo\{"AutoPennMirror"' | grep -oE 'displayId [0-9]+' | tail -1 | grep -oE '[0-9]+$')
[ "$1" = "play" ] && r shell "input -d $D tap 585 494"
sleep $2
pkill -f desktop-head-unit; pkill -f "sleep 7200"; sleep 8
r shell "dumpsys media.metrics 2>/dev/null | grep -E 'avc.encoder'" | python3 -I $S/m.py | tail -1
