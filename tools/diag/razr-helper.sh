#!/bin/bash
# (re)install nothing; (re)start helper on Razr with TCP+token
r() { adb -s ZY22LBXXFN "$@"; }
r shell 'kill $(ps -A | grep autopenn_helpe[r] | awk "{print \$2}") 2>/dev/null'
TOKEN=$(openssl rand -hex 16)
r shell "run-as com.penn.auto_penn sh -c 'mkdir -p files; echo $TOKEN > files/helper-token'"
APK=$(r shell pm path com.penn.auto_penn | sed 's/package://' | tr -d '\r')
r shell "nohup app_process -Djava.class.path=$APK /system/bin --nice-name=autopenn_helper com.autopenn.mirror.Helper tcp $TOKEN >/data/local/tmp/helper.log 2>&1 &"
sleep 2; r shell 'cat /data/local/tmp/helper.log'
