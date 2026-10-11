# STATUS (last updated 2026-10-05, end of session 1)

## Where we are
Phase 1 (flash the SD card) is blocked on a macOS permission. Nothing is written to the card yet.

- Route A chosen by Penn: real Android (KonstaKANG LineageOS 23.2) on the Pi 4. See PLAN.md.
- Penn approved erasing the 128 GB microSD. It shows up as /dev/disk2 (NTFS, "TF Mass Storage" reader). Re-check with `diskutil list` before writing; the number can change.
- All files downloaded and SHA256-verified, now in `downloads/`:
  - lineage-23.2-20260520-UNOFFICIAL-KonstaKANG-rpi4.zip (contains a 15.36 GB .img)
  - MindTheGapps-16.0.0-arm64-20260915_222150.zip
  - KonstaKANG-rpi-magisk-v30.7.zip, Magisk-v30.7.apk
  - KonstaKANG-rpi-widevine-16.zip
  - KonstaKANG-rpi-resize.zip
- `scripts/flash-sd.sh` streams the image to the card. `scripts/filen_dl.py` pulls files off KonstaKANG's filen.io links from the CLI.
- adb is installed (Homebrew cask). scrcpy was compiling from source via Homebrew when the session ended; check `which scrcpy`, rerun `brew install scrcpy` if missing.

## Why it stopped
dd to /dev/rdisk2 returned "Operation not permitted" even as root (via osascript admin prompt).
Cause: the session runs inside iTerm2, which lacks Full Disk Access. Penn was granting iTerm2
Full Disk Access in System Settings > Privacy & Security, which required restarting iTerm2.

## Update (session 2)
- scrcpy is now installed. Card is still unflashed (disk2, NTFS) after 3 more failed attempts.
- Penn did grant the permission, but the old session still failed. Likely cause: the session's process tree
  ran under a stale `iTermServer` that survived the iTerm2 restart, so it never got the new Full Disk Access.
  (Unconfirmed.) Penn is starting a fresh session from a new iTerm2 window/process.
- If a fresh session still gets "Operation not permitted", check which app holds Full Disk Access.
- Note: /private/tmp/flash.log is root-owned; the script overwrites it fine, just don't `rm` it.

## Root cause (session 3, verified)
Full Disk Access was NEVER the problem: TCC.db shows iTerm2, Terminal and claude-code all allowed, and the
session ran under a fresh iTermServer. The real cause: `osascript ... with administrator privileges` runs the
command via Security.framework/authtrampoline, so the root process is NOT a descendant of iTerm2 and does not
inherit its FDA. Even a 512-byte READ of /dev/rdisk2 fails that way. Do NOT use osascript to flash again.
Fix: run `sudo scripts/flash-sd.sh ...` from a shell that is a descendant of iTerm2/Terminal (Penn types it).

## Kick off next session with
1. Confirm the card: `diskutil list` (expect the 127.9 GB external disk, probably disk2).
2. Flash (needs admin password; a GUI prompt via osascript works):
   osascript -e 'do shell script "/Users/penn/Development/auto-penn/scripts/flash-sd.sh /Users/penn/Development/auto-penn/downloads/lineage-23.2-20260520-UNOFFICIAL-KonstaKANG-rpi4.zip 7244f927796a40d8d460992f574b507ac93543a88f0dce1715f63aa64ca2c675 disk2 > /private/tmp/flash.log 2>&1" with administrator privileges'
   Then watch /private/tmp/flash.log for "FLASH COMPLETE". Roughly 10 to 15 minutes.
   If it still says "Operation not permitted", fall back to Penn running the same script with sudo in Terminal.app.
3. Then Phase 2: hand Penn docs/first-boot.md, get the Pi's IP, `adb connect <ip>:5555`.
4. Phase 2 flashing plan via adb: `adb push` the four add-on zips to /sdcard, `adb reboot recovery`,
   then in TWRP `adb shell twrp install /sdcard/<zip>` for MindTheGapps, Magisk, Widevine, resize,
   then `adb shell twrp wipe data` (factory reset), reboot.

## Still to buy
- USB-C OTG Y / splitter cable (data to the car's USB-C, separate power leg).
- 12 V socket USB charger, 3 A at 5 V.

## Update (session 3, end): PHASE 1 DONE
Flashed the LineageOS image to disk2 successfully (FLASH COMPLETE, 15.36 GB, ~4 min). Card now has boot/system/etc partitions.
Method that worked: `SUDO_ASKPASS=<script showing osascript password dialog> sudo -A scripts/flash-sd.sh ...` run from the
session shell (stays a descendant of iTerm2, so it inherits Full Disk Access). Log to a NEW file, not the root-owned /private/tmp/flash.log.
Next: Phase 2. Eject card, put in Pi, boot, get IP, `adb connect <ip>:5555`, then add-on zips via TWRP (see steps above).

## Update (session 3): Pi reachable, adb pending authorization
- Pi at 192.168.86.24 (Ethernet, MAC 88:a2:9e:61:b7:73). Port 5555 open after Penn enabled ADB in Settings; SSH (22) also open
  but root/dev/shell/pi + Penn's PIN were refused (default SSH credentials for this build still unknown).
- ramdisk.img on the card was patched (force_debuggable + adb_debug.prop); it did NOT open adb on its own. Original in backups/ramdisk.img.orig.

## HARDEN BEFORE CAR USE (do at the very end, once everything works)
Penn enabled lots of debug/root options just to get in. When the setup is in a good state, undo them:
- Settings > Developer options: turn OFF Rooted debugging, ADB over network / Wireless debugging / "old ADB", USB debugging; revoke USB debugging authorizations; turn Developer options off.
- Turn OFF the SSH server and serial console in Raspberry Pi settings.
- Restore the original boot ramdisk (cp backups/ramdisk.img.orig to the card's boot partition ramdisk.img) to remove force_debuggable / adb_debug.prop (ro.adb.secure=0, ro.secure=0).
- Keep root only where Screen2Auto needs it (Magisk): set Magisk to prompt/deny for everything else.
- Change/remove any default SSH or dev passwords.

## Update (session 3, later): Phase 2 done, Android Auto installed
- Phase 2 complete: GApps + Magisk 30.7 + Widevine (apex + HAL registered) + resize flashed in TWRP (recovery = jumper pin 39<->40, GPIO21 low, at boot); /data is 112 GB.
- Factory reset must be done in TWRP (Wipe > Factory Reset); Settings > Erase all data fails (no /misc partition).
- Google signed in; GSF id 3ae72d8bb995f885 registered at google.com/android/uncertified.
- Play Store says Android Auto "not compatible" (phone-only filter). Sideloaded APKMirror bundle 17.9.664004 (arm64) via adb install-multiple over the Google-signed stub: Success.
- Pi: 192.168.86.24, adb 5555 (re-authorize popup after each wipe), adb root works. Magisk su via /debug_ramdisk/su.
- Next: confirm AA launches, install Screen2Auto + Netflix/YouTube/Crunchyroll, check Widevine L3, then Phase 5-8.

## Update (session 3): Screen2Auto REMOVED
- Installed Screen2Auto 3.7 via AAAD (sksa.aa.customapps, still installed), then uninstalled it on Penn's request: closed source, anonymous Russian-language developer
  (inceptive.ru, Telegram/VK/4PDA), accessibility + notification listener + overlay grants, downloads Frida binaries from the dev's server, uploads logs.
  Magisk root was never granted. Stale notification-listener setting cleared. ~4 min packet capture showed no contact with inceptive.ru (app was idle).
- Open question: need another way to show video apps on the car screen (Screen2Auto was the plan). Alternatives to research: open-source options only.
- Remember: this Pi still has debug/root enabled; see HARDEN section.

## Update (session 3, END): custom mirror app spike, resume here
State of the Pi (192.168.86.24, Ethernet; adb 5555, re-authorize popup after wipes; `adb root` works; debug/root still ON, see HARDEN):
- Installed user apps: Magisk, com.autopenn.mirror (our test app). AAAD and Screen2Auto were removed on purpose (closed source, anonymous dev).
- Android Auto 17.9.664004 installed (sideloaded APKMirror bundle over the Google stub). AA developer mode ON, "Start head unit server" ON (port 5277).

Mac toolchain (all direct downloads, Homebrew gradle was abandoned: it compiles half the world on macOS 13):
- Android SDK: ~/Library/Android/sdk (platform 36, build-tools 36.0.0, cmdline-tools, extras/google/auto = Desktop Head Unit x86_64). ANDROID_HOME must be exported.
- Gradle 8.14.3 in tools/gradle-8.14.3 (sha256 verified); project uses ./gradlew. AGP 8.13.2, Kotlin 2.3.21, androidx.car.app 1.7.0.
- Project: mirror-app/ (CarAppService, category NAVIGATION, SurfaceCallback painting colour bars + moving box + last touch).
  Build: `cd mirror-app && ANDROID_HOME=~/Library/Android/sdk ./gradlew --no-daemon assembleDebug` (~2 min)
  Install: `adb -s 192.168.86.24:5555 install -r mirror-app/app/build/outputs/apk/debug/app-debug.apk`

DHU test (in progress):
1. `adb -s 192.168.86.24:5555 forward tcp:5277 tcp:5277`
2. Run DHU WITH STDIN HELD OPEN, it has an interactive ">" prompt and exits on EOF (that is why my nohup run died right after connecting,
   Pi log: PROTOCOL_IO_ERROR / READER_CLOSE, then "charge only", PROJECTION_NOT_STARTED; this is the DHU quitting, not necessarily a Pi fault):
   `cd ~/Library/Android/sdk/extras/google/auto && tail -f /dev/null | ./desktop-head-unit -c config/default_1080p.ini`
   Or just run it in a normal iTerm tab by hand.
3. Look for "Auto Penn Mirror" in the DHU launcher; open it; expect colour bars + moving box. If it shows, the surface path works.
- Penn was restarting the terminal to grant Screen Recording permission so my `screencapture` can see the DHU window.
  (First screenshot showed only wallpaper = no window visible / permission missing.)
- Pi side screenshots work: `adb exec-out screencap -p > pi.png`.

Then, in order: VirtualDisplay output into the car surface -> launch YouTube/Netflix/Crunchyroll onto it (needs root/priv perms; Widevine L3 caps quality, DRM may need
CAPTURE_SECURE_VIDEO_OUTPUT via priv-app install) -> touch injection from SurfaceCallback gestures -> real Corvette test (Y cable still to buy).
Install of the streaming apps from Play may need sideloading (Play shows AA "not compatible" because of the phone-only filter).

## Update (session 4): AA projection session now LIVE, video encoder is the blocker
Got the DHU <-> Pi link fully working up to video. Key lessons:
- DHU needs the Pi's "Start head unit server" toggled (Android Auto > overflow menu) EVERY time AA is force-stopped; run DHU with stdin held open (`tail -f /dev/null | ./desktop-head-unit -c config/default_1080p.ini`).
- AA first-run setup finished (it pulled Google Maps from Play). Welcome/permissions dialog on the Pi ignores `input tap` but Tab-to-Continue + Enter works. All runtime perms were already granted by role/default.
- AA's car-side preflight waits on notification-listener access. Grant with (note the escaped `$`, an unescaped one silently truncates the component name on the Pi shell):
  `adb shell "cmd notification allow_listener 'com.google.android.projection.gearhead/com.google.android.gearhead.notifications.SharedNotificationListenerManager\$ListenerService'"`
  A bogus `...PreflightNotificationListenerService` entry (class does not exist) was also added to enabled_notification_listeners by mistake; harmless, can be removed.
- Once past preflight, the session starts (facet bar, dashboard windows, Maps ghost activity) but the DHU stays "Waiting for phone...".
- ROOT CAUSE of black video: AA picks `c2.v4l2.avc.encoder` (Pi 4 hardware H.264). It fails on the virtual-display input:
  `C2AllocatorGralloc: failed transaction: lock(RGBA_8888)` -> `V4L2EncodeComponent: Failed to convert input block` -> MediaCodec UNKNOWN_ERROR -> "Frame stuck or lost in encoder!".
- Tried `stop vendor.media.c2.v4l2` at runtime: AA STILL chose the v4l2 encoder (codec list cached by mediaserver / AA). Service restarted afterwards, Pi is back to normal.
- Next ideas: (a) restart `media` + `media.swcodec` after stopping the v4l2 service so the codec list refreshes; (b) remove/rename the v4l2 c2 service permanently via a Magisk module (but keep HW decode for video apps if possible); (c) try default_720p.ini in case it is resolution/format specific; (d) check dmesg/gralloc for why RGBA_8888 lock fails on the v4l2 path.
- Do NOT take full-screen screencaptures on the Mac (Penn's Messages was visible once). Capture just the DHU window: `screencapture -x -l<windowid>` (id via Quartz CGWindowList in a python3 -I script).
- DHU window and scrcpy are not running right now; Pi debug/root still ON (see HARDEN).

## Update (session 4, later): software-encoder attempt did not work
- Tried: `stop vendor.media.c2.v4l2; stop media; stop media.swcodec; start media.swcodec; start media`, then rerun DHU. AA never reached "Choosing codec" this time: mediaswcodec's gralloc request failed
  (`minigbm_gbm_mesa: Failed to convert descriptor. Unsupported format 0x3c`) and the session stalled (CAR.USBMON.EVENTTIMEOUT: timed out waiting for Bluetooth profile proxy; Bluetooth is OFF on the Pi).
- Codec services were restored afterwards (media, swcodec, v4l2 all running). A Pi reboot also resets everything.
- Web search found nothing specific for this combo (KonstaKANG LineageOS 23.2 advertises H.264 HW encode, but AA's virtual-display RGBA input path fails in V4L2EncodeComponent).
- Untried: turn Bluetooth ON in the Pi first (AA wants the BT profile proxy); retry v4l2 path at default_720p.ini; ask KonstaKANG (XDA thread / GitHub) about gralloc lock(RGBA_8888) failing in c2 v4l2 encoder; check for newer KonstaKANG build; or fall back to a non-AA approach (own app streaming MediaProjection to the car is NOT possible without AA, so AA video must work).
- Bluetooth enabled via `cmd bluetooth_manager enable` (works from shell, no UI). Retried at default_720p.ini with BT on: SAME failure
  (`V4L2EncodeComponent: Failed to convert input block` -> "Frame stuck or lost in encoder!"). So it is the v4l2 encoder itself, not resolution or Bluetooth. Pi left with Bluetooth ON, AA force-stopped, DHU stopped.
- Remaining options: newer KonstaKANG build / report the bug to KonstaKANG (XDA thread, GitHub); or patch/replace the encoder path (e.g. a Magisk module that hides c2.v4l2.avc.encoder while keeping the v4l2 decoder, plus fixing the swcodec gralloc 0x3c format failure).

## Update (session 4, END): VIDEO PATH WORKS. Colour bars + moving box appear in the DHU.
FIX (persistent, on the SD card's /vendor): removed the `<Encoders>` block (c2.v4l2.avc.encoder) from /vendor/etc/media_codecs_v4l2_c2_video.xml, keeping the v4l2 H.264 DECODER.
AA now picks `c2.android.avc.encoder` (software). Original saved at backups/vendor-etc/media_codecs_v4l2_c2_video.xml.orig (sha256 4420bda6...688d).
Steps used: `adb root; adb shell mount -o remount,rw /vendor`, push edited xml (chmod 644, context vendor_configs_file), restart `media`, `media.swcodec`, `vendor.media.c2.v4l2`, then /vendor back to ro.
To undo: remount rw, push the .orig back, restart those services (or reboot).
Test result (default_720p.ini, Bluetooth ON): "Auto Penn Mirror" launches from the AA launcher, renders 1190x700 colour bars, moving white box, "last touch" text. The earlier gralloc 0x3c errors are harmless.
Not yet checked: CPU load / smoothness of software encode at 1080p (the real Corvette screen), and whether the patch survives a full Pi reboot (should: it is on the card).
Next (original plan): VirtualDisplay into the car surface -> launch YouTube/Netflix/Crunchyroll onto it -> touch injection -> Corvette test (Y cable still to buy).
Pi state now: Bluetooth ON, DHU stopped, AA force-stopped, debug/root still ON (HARDEN section).

## Update (session 4, step 2): VirtualDisplay created, launching other apps onto it is blocked by permissions
Code: mirror-app now has VirtualScreen.kt (creates VirtualDisplay "AutoPennMirror" into the car surface; flags PUBLIC|OWN_CONTENT_ONLY|SUPPORTS_TOUCH) and MirrorScreen registers a receiver:
`adb shell am broadcast -a com.autopenn.mirror.LAUNCH --es pkg <package>` launches that package onto the virtual display.
Result in DHU (720p): display id=37 created fine, car shows black (nothing on the display yet).
BLOCKER: `startActivity` with setLaunchDisplayId(37) from our app -> SecurityException "Permission Denial: starting Intent ... with launchDisplayId=37" (display is not TRUSTED, Settings is not allowEmbedded).
Facts learned:
- ROM is userdebug / dev-keys, platform cert is KonstaKANG's private key (SHA-256 5693e9ba...3751), so we cannot platform-sign our app (and should not want to).
- `su` is NOT on PATH for app processes (Magisk root here is only reachable from adb root), so an app calling `su` will not work as-is.
- Shell uid (adb shell) CAN do it: `am start --display 37 -n pkg/activity` and `input -d 37 tap x y` (not yet tested).
Options to finish (need Penn's decision, they affect the HARDEN plan):
 A) Priv-app: install mirror app into /system/priv-app + privapp-permissions XML granting ADD_TRUSTED_DISPLAY (trusted display => any app can be launched on it). Touch still needs INJECT_EVENTS (signature-only), so touch needs B or another trick.
 B) Small init-started helper running as `shell` user, listening on a unix socket owned by our app's uid only; app asks it to `am start --display N` and `input -d N tap/swipe`. One mechanism covers launch + touch. Must be removed/locked down when hardening.

## Update (session 4, END): APPS RUN ON THE CAR SCREEN, TOUCH WORKS (tested in DHU)
Verified in the DHU at 720p: `am broadcast -a com.autopenn.mirror.LAUNCH --es pkg com.android.settings` puts Android Settings on the car screen, and a DHU `tap` (DHU prompt command) opened a Settings page and scrolled the list. So launch + touch work end to end.
Architecture now:
- mirror-app (CarAppService, NAVIGATION template) -> VirtualScreen.kt creates a VirtualDisplay straight into the car surface -> HelperClient.kt sends commands over abstract socket "autopenn.helper".
- Helper.kt runs as uid `shell` via init (`device/autopenn-helper.rc` -> /vendor/etc/init/autopenn-helper.rc, `disabled` + started on sys.boot_completed). It only accepts connections from the mirror app's uid (SO_PEERCRED) and supports START <display> <pkg> (am start --display), TAP, SWIPE (InputManagerGlobal.injectInputEvent with displayId).
- The APK is installed as a priv-app at /system/priv-app/AutoPennMirror/AutoPennMirror.apk (stable path for the helper's app_process classpath). To update: adb root, `mount -o remount,rw /`, `mount -o remount,rw /vendor` if needed, push APK, reboot (the helper loads the APK at boot).
- Dead ends (do not retry): untrusted VirtualDisplay can't host other apps from a normal/priv app (ACTIVITY_EMBEDDING not enough); ADD_TRUSTED_DISPLAY is role-only and the SYSTEM_AUTOMOTIVE_PROJECTION role is static (only AA can hold it); platform key is KonstaKANG's private key; `su` is not on PATH for apps.
- Reboot gotcha: after a reboot the Pi sits at its PIN lock screen with user storage locked: AA's settings activity "does not exist", AA stays stopped, uiautomator/sdcard fail. Unlock it (PIN) first. Penn is deciding whether to remove the screen lock (needed for unattended car boots).
- After unlock: open AA settings with `am start -a com.google.android.projection.gearhead.SETTINGS` (the .companion... class name no longer resolves), menu (1875,90) -> "Start head unit server" (1755,320), forward 5277, run DHU.
- DHU can take commands (tap/swipe) if started from a FIFO instead of `tail -f /dev/null`.
- persist.adb.tcp.port=5555 is set so network adb comes back after reboot (it needs a moment after boot, retry connect).
NEXT:
1. Install YouTube / Netflix / Crunchyroll (none installed yet; Play showed AA "not compatible" earlier, try Play for the streaming apps, else APKMirror bundles). Check Widevine L3 playback on the virtual display and software-encode CPU load at 1080p.
2. Replace the broadcast hook (RECEIVER_EXPORTED, any app can trigger it) with a proper in-car app launcher UI and real touch (onScroll/onFling mapping is crude).
3. Decide on the screen lock (see above), then Y cable + real Corvette test.
HARDEN additions: remove /vendor/etc/init/autopenn-helper.rc (shell helper), remove the exported LAUNCH receiver, adb tcp persist prop, Bluetooth, permissive SELinux ROM, vendor codec patch is fine to keep.

## Update (session 4, final): audio verified, architecture documented
- AUDIO PASSTHROUGH WORKS: with the DHU connected, a tone played in the Music app (org.lineageos.twelve) made AA take audio focus, enable the MEDIA stream, capture ~263 PCM frames via remote submix and send ~1.5 Mbit/s that the HU acked (~20 ms). DHU logged "Audio stream close" at the end. So any app on the Pi (including ones on our VirtualDisplay) is heard in the car with no extra work. Not yet checked: actual sound quality on a real car, and audio while a Widevine app plays.
- Architecture write-up: docs/android-auto-architecture.md (protocol, AA internals, Car App Library, our VirtualDisplay + shell helper design, Pi gotchas, debug recipe).

## Update (session 4, final 2): streaming apps, load test, security audit
- Installed from Play (all three offered Install, no compatibility block): YouTube (signed in with Penn's Google account, shows Premium), Netflix 9.86.0, Crunchyroll. Netflix/Crunchyroll are NOT signed in (we never type credentials).
- Apps on the car screen via the helper path WORK for YouTube (home feed + autoplay preview video rendered in the DHU, 1816x1056 at "1080p"). Gotchas:
  * Launch with `am start --display <id> -a MAIN -c LAUNCHER -p <pkg>`; the display id changes on every car-surface recreation, read it from `dumpsys display` (DisplayInfo "AutoPennMirror").
  * A deep-link VIEW intent (youtube.com/watch URL) pulls the YouTube task onto display 0 instead; drive playback with touch (`input -d <id> tap x y` as root, or the helper's TAP) instead.
  * Netflix: `-p com.netflix.mediaclient` does not resolve a launcher intent ("unable to resolve Intent"). Its launcher is `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`; the helper must start explicit components (use `cmd package resolve-activity --brief` then `am start -n`). Not yet tested.
  * Crunchyroll's onboarding activity landed on display 0 (likely not resizable / not allowed on the untrusted display). Not solved.
  * Permission dialogs (e.g. YouTube POST_NOTIFICATIONS) appear on display 0; pre-grant with `pm grant`.
- LOAD (Pi 4, software H.264 encode for Android Auto, YouTube autoplay/fullscreen video):
  * 1080p session: all four cores ~85-100% busy (400% total: 235-271% user + 85-109% sys), load average ~10, SoC 67-68 C at full 1.8 GHz (no throttling seen), DHU logged "Ping timeout" and "we have 6 frames backlog", encoder logged a few "Frame stuck or lost".
  * 720p session: ~74% busy, 0 "Frame stuck", 0 ping timeouts/backlog in the log, SoC 69 C.
  * Biggest single cost is one mediaswcodec thread at 80-93% of a core: YouTube decoding AV1 in software (first via c2.ffmpeg.av1.decoder, after my XML edit via c2.android.av1). The vendor XML edit (removing AV1/VP9 from media_codecs_ffmpeg_c2.xml and media_codecs_google_c2_video.xml) removed the ffmpeg one but the mediaswcodec APEX still offers c2.android.av1, so YouTube still gets AV1. Next idea: make AV1/VP9 unavailable inside the swcodec APEX or force H.264 (hardware v4l2 decoder), e.g. via a Magisk-free overlay of the APEX list or a YouTube setting/flag; H.264-only apps (Netflix/Crunchyroll L3 SD) should be much lighter.
  * Conclusion so far: 720p is OK, 1080p + AV1 software decode is too much for a Pi 4. Real Corvette head-unit resolution decides how bad it is; check what the car requests (the DHU config size is just ours).
- DRM: Widevine L3 behaviour on Netflix/Crunchyroll NOT verified (needs a login). `dumpsys media.drm` is unavailable on this build.
- Security audit done, results and the itemised to-do list are in cleanup-instructions.md (no remnants of Screen2Auto/AAAD/Frida found; open items: sshd running, adb tcp persistent, debug ramdisk, Magisk app, exported LAUNCH receiver, account/PIN decisions).

## Update (session 4, final 3): OPTION 1 DONE. 1080p YouTube plays smoothly on the car screen on the Pi 4
Fix: in the VENDOR codec XMLs (not the swcodec APEX, which is read-only), disable the AV1 and VP9 software decoders so YouTube falls back to H.264:
- /vendor/etc/media_codecs_ffmpeg_c2.xml: `enabled="false"` added on c2.ffmpeg.av1.decoder and c2.ffmpeg.vp9.decoder (backup *.orig in backups/vendor-etc/).
- /vendor/etc/media_codecs_google_c2_video.xml: av1/vp9 entries replaced by `<MediaCodec name=... update="true" enabled="false">` overrides for c2.android.av1-dav1d.decoder, c2.android.av1.decoder, c2.android.vp9.decoder (the `update="true"` form is what overrides entries declared in /apex/com.android.media.swcodec/etc/media_codecs.xml; plain `enabled="false"` on a vendor copy did not hide them).
- Apply: remount /vendor rw, push, restart `media`, `media.swcodec`, `vendor.media.c2.v4l2`, `vendor.media.c2.ffmpeg`. Check with `dumpsys media.player | grep 'Decoder "'` (only avc + hevc decoders should remain).
Result at default_1080p.ini (Android Auto surface 1816x1056), YouTube video playing: CPU 63% busy (was 85-100%), SoC 62 C, 0 "Frame stuck", 0 DHU ping-timeout/backlog warnings. The one hot thread (~90% of a core) is now just Android Auto's software H.264 ENCODER (c2.android.avc.encoder), the remaining ceiling. Before: AV1 software decode + encoder pegged all four cores.
Caveats: YouTube is capped at H.264 (<=1080p, fine for a car screen). Any app that needs VP9/AV1 will fall back or fail. Netflix / Crunchyroll DRM untested. Roadmap for phone support and a native app: docs/roadmap-v2-v3.md.

## CORRECTION (session 4, after Penn watched the DHU): "smooth" was WRONG, video is a slideshow
Penn saw the 1080p YouTube video play with audio but at slideshow frame rate. My earlier "smooth" claim only checked CPU %, error logs and DHU warnings, never the delivered frame rate.
Real measure: `dumpsys media.metrics | grep c2.android.avc.encoder` gives per-session encoder stats (vencode.frames, vencode.durationUs, width/height). Recorded sessions (both run while YouTube/AV1 was also loading the CPU, so these are worst-case, not clean numbers):
- 1280x720: 106.6 ms per encoded frame => max ~9.4 fps
- 1920x1080: 184.7 ms per encoded frame => max ~5.4 fps
So Android Auto's own software H.264 encode of its virtual display is the bottleneck (the thing that is "free" on a phone because it uses a hardware encoder). It is single-threaded and ~90% of one core, so removing AV1 decode helped other things but did not fix the encoder.
Not yet measured: a clean (no competing load) encoder ms/frame at 480p/720p/1080p. Ideas, in order:
1. Cap Android Auto's video: AA developer settings has "Video resolution" (480p/720p/1080p) and FPS options; try 480p/720p at 30 fps. Cheapest; real cars often only ask for 720p or less anyway.
2. Make the Pi's hardware H.264 encoder work (`c2.v4l2.avc.encoder`): it failed on `lock(RGBA_8888)` / "Failed to convert input block" because AA feeds it an RGBA GPU surface. Try `debug.stagefright.c2inputsurface`, other ccodec props, or a ROM fix/report to KonstaKANG. Highest payoff (real 30 fps) but uncertain.
3. Pi 5 (about 2-3x faster CPU) or a phone (hardware encoder), see docs/roadmap-v2-v3.md.

## Update (session 4, final 4): encoder investigation result (option 1 + time-boxed option 2)
OPTION 1 (cap Android Auto video): AA developer settings > Developer settings > (scroll) "Video Resolution" has: negotiate / only 800x480 / up to 1280x720 / up to 1920x1080 / ... It is now set to "Allow only 800x480". The head unit must OFFER that size (DHU config default.ini = 800x480; default_720p.ini only offers 720p, so the setting had no effect there).
Measured software encoder cost (dumpsys media.metrics, c2.android.avc.encoder, YouTube playing, AV1 removed so little competing load):
| size | ms per frame | max fps |
| 1920x1080 | ~185 | ~5 |   (older run, with AV1 load)
| 1280x720  | ~93 | ~11 |
| 800x480 (surface 800x400) | ~54 | ~18 |
So even the smallest size tops out around 18 fps: watchable but not smooth. Android Auto wants 30.
OPTION 2 (hardware H.264 encoder c2.v4l2.avc.encoder), time-boxed: ROOT CAUSE FOUND, NOT FIXABLE WITHOUT REBUILDING THE ROM.
 Log: `android.hardware.media.c2-service-v4l2: Attempted to lock() a buffer that was not allocated with a BufferUsage::CPU_* usage.` -> `Gralloc5: lock(...) failed: 3` -> `C2AllocatorGralloc: failed transaction: lock(RGBA_8888)` -> `V4L2EncodeComponent: Failed to convert input block` -> MediaCodec UNKNOWN_ERROR.
 Meaning: Android Auto feeds the encoder through a Surface (GPU-rendered RGBA buffers, no CPU access). The v4l2 encode component converts RGBA to NV12 on the CPU, so it must lock the buffer for CPU reads, but its input usage tuning does not request CPU_READ, so the gralloc mapper refuses. The fix is in KonstaKANG's v4l2 C2 component (declare CPU_READ usage on the encoder input, or do the RGBA->NV12 conversion on the GPU), i.e. a source change + rebuild of android.hardware.media.c2-service-v4l2. There is no runtime knob (props like debug.stagefright.c2inputsurface do not change buffer usage).
 Worth reporting upstream to KonstaKANG (XDA / GitHub) with those log lines; if fixed, remove the Encoders block patch and AA would encode in hardware at 30 fps.
 State now: v4l2 encoder entry removed again (software encoder in use), v4l2 decoder kept, AV1/VP9 disabled, AA video resolution capped at 800x480. Original XMLs in backups/vendor-etc.
Bottom line: on the Pi 4 the achievable result is about 18 fps at 800x480 (or ~11 fps at 720p). For real 30 fps use a Pi 5 (software, ~2-3x faster, borderline), a rebuilt ROM with the encoder fix, or a phone (v2 in docs/roadmap-v2-v3.md).

## Update (session 4, final 5): binary-patch attempt on the v4l2 encoder (time-boxed), result: NOT a simple patch
Facts found:
- The v4l2 codec service is an APEX: /vendor/apex/com.android.hardware.media.c2.v4l2.apex (mounted at /apex/com.android.hardware.media.c2.v4l2), the component code is lib64/libv4l2_codec2_components.so (337 KB, original backed up in backups/v4l2-apex/, sha256 dffe4b83...). Tools: python venv in the session scratchpad with capstone + pyelftools (pip), Apple nm/objdump cannot read ELF.
- Runtime log of the ORIGINAL encoder: CCodec reports `int64_t android._C2MemoryUsage = 0` for its input, i.e. the component declares NO input buffer usage, so surface buffers get no CPU_READ flag, which is exactly why the CPU lock fails.
- I guessed the usage constant (`mov x8,#0x400000000` in V4L2EncodeInterface::Initialize at 0x3cd70, a HW_CODEC_READ-like value) and patched it to 1 (CPU_READ) in a copy, bind-mounted over the APEX lib (temporary, `mount --bind`, unmounted afterwards). Result: the v4l2 ENCODER DISAPPEARED from the codec list (interface init failed), so that constant was something else (a field range), and the real fix is not a constant: the component has no usage parameter at all, a new `C2StreamUsageTuning::input = CPU_READ` parameter must be ADDED. That is a source change (a couple of lines in external/v4l2_codec2 V4L2EncodeInterface.cpp) plus rebuilding that library.
- Cleaned up: bind mount removed, lib restored to the original, temp files in /data/local/tmp deleted, v4l2 encoder block removed again from the vendor XML (software encoder in use).
Remaining routes to a hardware encoder: (a) build libv4l2_codec2_components.so from the LineageOS 23.2 / KonstaKANG tree on a Linux box (or cloud VM) with the usage parameter added, then bind-mount or repack the APEX; (b) upstream bug report to KonstaKANG; (c) phone / Pi 5 (see docs/roadmap-v2-v3.md).
Gotcha for next time: never `pkill -f <name>` inside `adb shell "..."` when the name appears in the command line itself (it kills the shell).

## Update (session 4, final 6): hardware encoder FIXED in source (AWS build), but frame rate only ~14 fps
- Source: Raspberry Vanilla fork `raspberry-vanilla/android_external_v4l2_codec2` (branch android-16.0). In V4L2EncodeInterface.cpp the `C2_PARAMKEY_INPUT_STREAM_USAGE` parameter is COMMENTED OUT (TODO b/167640667). Enabling it with `C2MemoryUsage::CPU_READ` is the fix: docs/v4l2-encoder-cpu-read.patch.
- Built on AWS (c7i.8xlarge, Ubuntu 22.04, AOSP android-16.0.0_r4 + RV local manifest android-16.0, `lunch aosp_rpi4-bp4a-userdebug; m com.android.hardware.media.c2.v4l2`): sync ~20 min (Google rate-limits at -j16, use -j4), build 6 min. Total AWS cost about $1.2 (48 min). Everything terminated and deleted (instance, volume, key pair, security group; verified 0 left).
- Artifacts: backups/v4l2-apex/libv4l2_codec2_components.so.cpu_read_fix (rebuilt lib, 337632 bytes), com.android.hardware.media.c2.v4l2.apex.rebuilt (whole APEX, signed with the build's test key, not tried), and the .orig originals.
- Test on the Pi (temporary `mount --bind` of the rebuilt lib over /apex/com.android.hardware.media.c2.v4l2/lib64/libv4l2_codec2_components.so, stop/start vendor.media.c2.v4l2 + media): c2.v4l2.avc.encoder now WORKS (no lock error), AA picks it, encoder service ~40% of one core (software encoder was ~90%).
- Delivered frame rate (matched PLAY vs IDLE sessions, frames from `dumpsys media.metrics`, ~62 s of YouTube video): 720p: (957-64) frames => ~14 fps; 800x480: (889-69) => ~13 fps. Same at both sizes, so resolution is NOT the limit now. Software encoder at 720p was ~10 fps max. Net gain ~35%, still far from 30.
- Likely next bottleneck: CPU readback + RGBA->NV12 conversion of GPU-rendered buffers (uncached reads), and two GPU compositions per frame (our VirtualDisplay + AA's virtual display) on the Pi 4's V3D. SurfaceFlinger ~27% CPU. Ideas: profile with simpleperf/perfetto, avoid the double composition, try a CPU-cached gralloc usage, or give up on 30 fps on a Pi 4.
- SAFE STATE LEFT ON THE PI: bind mount removed, original lib in place, v4l2 ENCODER entry removed from the vendor XML again (software encoder). Do NOT re-enable the encoder entry without also making the fixed lib permanent (a reboot would load the original broken lib and Android Auto would lose video).
- To make the fix permanent if wanted: boot-time `mount --bind` of the fixed lib (init rc) or replace /vendor/apex/com.android.hardware.media.c2.v4l2.apex with the rebuilt APEX (apexd signature check may reject it). Needs Penn to run, boot persistence is blocked for me.
- Bug report draft for KonstaKANG: docs/konstakang-bug-report.md.

## Update (session 4, final 7): where the frame rate goes (pipeline isolation, Pi 4, 720p, hardware-encoder lib bind-mounted for the tests)
New diagnostics: mirror-app `AnimActivity` (full-rate animation) and `settings put global autopenn_mode pattern|mirror` (pattern = paint straight into the car surface, no VirtualDisplay). Scripts: tools/diag/pipe.sh (runs a DHU session and prints encoder frames/lifetime from `dumpsys media.metrics`) and tools/diag/hw.sh on|off (temporary hardware-encoder setup / safe state). fps = encoded frames / active seconds (idle UI is only ~0.5 fps).
| Producer path | Encoder | delivered fps |
|---|---|---|
| pattern painted directly into AA surface | software | ~7 |
| pattern painted directly into AA surface | HARDWARE (fixed lib) | ~27-28 (target 30) |
| our VirtualDisplay + AnimActivity (24 s of animation) | hardware | ~24 |
| our VirtualDisplay + YouTube video (feed autoplay / video page) | hardware | ~13-20 (very noisy, content dependent) |
| same, 800x480 vs 720p | hardware | same (~13-14), so NOT pixel-bound |
CPU accounting (top -H, 4 cores = 400%): pattern direct: v4l2 service 57%, AA car 30%, SurfaceFlinger 25%, idle ~197%. Mirror+animation: v4l2 71%, SurfaceFlinger 61%, mirror app 36%, AA car 33%, idle ~48%. Mirror+YouTube: SurfaceFlinger ~80% (several threads), YouTube ~50%, v4l2 20-40%, idle ~90%.
Conclusions:
1. With the hardware encoder fixed, Android Auto's own encode+transport path can do ~27 fps at 720p. The encoder was the original big bottleneck (software: ~7-10 fps).
2. Our VirtualDisplay hop costs ~3 fps and doubles SurfaceFlinger CPU (two GPU compositions per frame: ours, then AA's).
3. Video playback adds decode/copy work (v4l2 decoder output is column-tiled NC12, copied/converted) plus SurfaceFlinger texture work; with everything the 4 cores are ~75-90% busy and the rate falls to ~13-20 fps and varies with the video.
4. Resolution does not matter (800x480 = 720p), so the limit is per-frame CPU work/latency, not pixel throughput.
5. RenderEngine backend experiment (`debug.renderengine.backend=skiavk` + restart surfaceflinger) CRASHED the framework (system_server DEAD_OBJECT) and needed a reboot; do not restart surfaceflinger live. Not retried via boot-time config.
6. Display-off test (`cmd display power-off 0`, stands in for a headless car install): inconclusive. Runs varied 12-20 fps and one DHU crashed (segfault). YouTube content changes between runs; use a fixed video for A/B tests (VIEW intents move YouTube to display 0, so a fixed local H.264 file played by a simple player is the better test source).
Options to get closer to 30 fps on the Pi 4: (a) make the hardware-encoder lib permanent (+~40% vs software), (b) overclock CPU/GPU (arm_freq=2000/over_voltage=6, gpu_freq=600, needs cooling; ~+10%), (c) fewer compositions / cheaper video path (needs deeper SurfaceFlinger/HWC work), (d) Pi 5 or a phone (roadmap v2).
Safe state left on the Pi: bind mount removed, encoder entry removed from the vendor XML (software encoder), app updated (AnimActivity + pattern mode, installed as an update over the priv-app, codePath /data/app), display awake, unlocked, Bluetooth on.

## Session 4b (2026-10-10): v2 validation on a Motorola razr+ 2024 (stock, locked bootloader, Android 15)

Result: **the same app works on a stock phone; smooth video, no ROM patching.**

- Phone: razr plus 2024 (Snapdragon 8s Gen 3), Android 15, verified boot green, no root. Two displays (inner display id 4630947043778501763). adb over USB.
- Setup that was needed: Google account on the phone, Play update of Android Auto (shipped as a stub), AA developer mode (tap Version 10x) -> Developer settings -> Unknown sources, overflow -> Start head unit server, DHU 720p. YouTube had to be updated through Play (old version showed an "update required" screen that was mirrored).
- **Encoder**: `c2.qti.avc.encoder` (hardware), AA picks 1280x720. No patch. Measured with `dumpsys media.metrics` (tools/diag/enc-metrics.py), matched sessions of 100 s: idle YouTube home = 149 frames, YouTube video playing = 1895 frames, so about (1895-149)/60 = **29 fps** while playing (Pi 4 with the fixed hardware encoder: 13-20 fps, software encoder: 5 fps).
- YouTube launches on the mirror display and plays; AA also shows its media card with transport controls (audio session recognised).
- **New finding, needs code change (done):** stock phones run SELinux **enforcing** and deny `untrusted_app -> shell` unix-socket connect (`avc: denied { connectto } ... tcontext=u:r:shell:s0`). The abstract-socket helper from the Pi build therefore fails. Fix: helper also listens on `127.0.0.1:5279` (started with `tcp <token>`), client falls back to TCP and sends `AUTH <token>`; token lives in the app's files dir. App needed the INTERNET permission for that. Pi keeps working over the unix socket (SO_PEERCRED).
- Helper start on the phone (from the Mac, once per phone reboot): tools/diag/razr-helper.sh (kills old helper, writes a fresh token via `run-as`, starts `app_process ... Helper tcp <token>` as the adb shell user). Real v2 should do the same from Shizuku / on-device wireless debugging instead of a Mac.
- Permission dialogs appear on the mirror display and dim it (GrantPermissionsActivity): pre-grant `POST_NOTIFICATIONS` with `pm grant`.
- Netflix: needs the explicit component (`cmd package resolve-activity --brief` gives `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`), launches on the mirror display, but is not signed in (SignupNativeActivity). **Widevine L1 black-screen test still to do: needs a Netflix login (Penn).** Crunchyroll not tested yet (not signed in).
- The splash screens of YouTube/Netflix look cropped on the right while loading; they fix themselves once the real UI is up (not yet investigated).
- Open: DRM test, Crunchyroll, touch via TAP from the car, Shizuku backend, thermal over 30 min, real car.

### Razr: Netflix result (2026-10-10, same session)
- **Widevine L1 black screen CONFIRMED.** Netflix (signed in) plays, AA shows its media card (title, pause button, progress) and the subtitles render on the car screen ("[men shouting]"), audio is routed, but the **video plane is black** on the non-secure virtual display. Same effect screen recording has. YouTube is unaffected.
- Netflix escapes the virtual display: its splash (`UIWebViewActivity`) starts a new root task (`o.mIP`) on display 0 and it keeps re-launching onto display 0 after the profile screen. `force_resizable_activities` / `enable_non_resizable_multi_window` (set + reboot) did NOT help. Working fix: `am display move-stack <taskId> <mirrorDisplayId>` run repeatedly (tools/diag/pin-task.py polls `dumpsys activity activities` and moves the package's tasks); this must become part of the helper (watch + move) in the app. Touch via `input -d <id> tap` worked on the moved task.
- Open: how to get Netflix to use L3/SD on an L1 phone (Netflix picks L1 whenever the device is provisioned for it; options: a rooted phone with an L3-forcing module, or a phone/ROM that only has L3, i.e. the Pi route). Crunchyroll untested (needs login).

### Full screen / visible area (2026-10-10)
- Finding: Android Auto gives us the whole content area as the surface (DHU 720p: 1190x700) but paints its own dashboard panel (media card) over the right side. `onVisibleAreaChanged` reports what is really visible: Rect(24,88-746,688) then (24,24-746,688), i.e. only ~61% of the width; 800x480 DHU layout: surface 800x400, visible (22,34-464,376). Android Auto settings and the developer "Application Mode" have no switch to remove the panel; it is the head unit layout, not ours to control. Whether the Corvette's screen shows the same panel is still unknown (needs the car).
- Before the fix our mirror used the whole surface, so ~40% of the app (right side) was hidden under the panel (Netflix profile row and YouTube's top bar were cut off).
- Fix (mirror-app): `FitRenderer.kt` (EGL/GLES: VirtualDisplay -> SurfaceTexture -> quad drawn only inside the visible rect of the car surface), `VirtualScreen.attachFit/updateVisible` sizes the VirtualDisplay to the visible rect and resizes it when the rect changes; `MirrorScreen` offsets touch coordinates by the rect origin. Result on the Razr + DHU: YouTube UI fully visible (search icon, bottom bar), touch hits the right item and plays. `adb shell settings put global autopenn_fit 0` keeps the old direct path (cheaper on the Pi, no extra GPU copy).
- Not yet measured: fps with the extra GL pass on the Razr (expected fine), and the Pi (leave `autopenn_fit 0` there).

### CORRECTION: real full screen exists (Penn was right), 2026-10-10
- I wrote above that the dashboard panel cannot be removed. Wrong. Android Auto has a **full-screen toggle for navigation apps**: with our app already open in the split layout, tap **our own icon in the left rail a second time** (the Maps icon does the same for Maps). The panel disappears, the rail shrinks to its icon column, and the bottom-left icon becomes the "split layout" glyph that goes back.
- Verified in the DHU on the Razr: visible area changes from Rect(24,24-746,688) to **Rect(24,24-1166,688)** (full width) and the FitRenderer follows it automatically (VirtualDisplay resized to the new rect); YouTube then fills the whole content area (search icon, chip row and bottom bar all visible).
- No API found to trigger it from the app: the user taps the rail icon (or the split icon to leave). In the Corvette it should behave the same if its AA layout has the toggle (unverified).
- Keep the FitRenderer: it makes both layouts correct (split and full) without cropping.
- Verified (Razr + DHU 720p): in AA full-screen mode, YouTube's own fullscreen button makes the video fill nearly the whole content area to the right of the rail (small black margins at the sides and top; the "Exit" button overlays the top right). Order that works: open app, tap its rail icon again (full screen), launch YouTube, play, tap the player's fullscreen icon (tap the video first to show controls). AA remembers the full-screen state across reconnects (visible area was already 1142 wide at the next connect).

### Helper keeps apps on the mirror + app icon (2026-10-10)
- `Helper.kt`: `START` now resolves the launcher component (`cmd package resolve-activity --brief`) and starts it explicitly with `am start --display N -n pkg/.Activity` (fixes Netflix). The started package is "pinned": a 1.5 s watcher parses `dumpsys activity activities` (stops at "ActivityTaskSupervisor state") and runs `am display move-stack <task> <mirror>` for any pinned task found on another display. New command `DISPLAY <id>` (sent by the app when the VirtualDisplay is created / released with -1) tells the helper which display is the mirror; -1 clears the pins, so after Android Auto disconnects nothing is yanked off the phone's own screen.
- Verified on the Razr (DHU, full-screen layout): broadcast LAUNCH Netflix -> splash, relaunch onto display 0, auto-moved back (helper log "moving task ... from display 0 to 64"), profile screen and Home shown full-width; touch works. The earlier pin-task.py is no longer needed.
- App icon: artwork "Auto Penn_ Neon Road Monogram.png" (kept as docs/icon-source.png) is the adaptive launcher icon (background #04132B, foreground res/drawable-nodpi/ic_launcher_foreground.png, round variant too). App label is now "Auto Penn" (was "Auto Penn Mirror"). It shows in the Android Auto rail.
- Pi: needs the new APK in /system/priv-app + helper restart to get this (not done yet); keep `autopenn_fit 0` there.

### Pre-car-test state (2026-10-10): Razr
- Phone-side launcher added to the app (MainActivity): when the phone is connected to the car (AA session open) it shows buttons YouTube / Netflix / Crunchyroll / Chrome that start the app on the car screen through the helper (tested on the DHU: YouTube button -> car screen). Needs `<queries>` for those packages (added) and a NoActionBar theme.
- The helper on the Razr is a shell process started from the Mac (`tools/diag/razr-helper.sh`) and dies when the phone reboots; no Mac in the car, so it must already be running when the phone leaves the desk. The app's token file is in its own files dir (written via run-as).
- Not tested yet: real Corvette head unit (layout, whether the nav icon appears, full-screen toggle, video while parked), whether the nohup helper survives unplugging USB, touch accuracy on the real screen.

## Session 5 (2026-10-10): app missing from car launcher -> Play internal testing
- Symptom in the Corvette: icon shown once, then gone; absent from AA "Customize launcher".
- Cause: `adb logcat -s CAR.VALIDATOR` shows `Package DENIED; failed all other checks [com.autopenn.mirror]` + `isPackageInstalledByPlayCheck service not connected`. Unknown sources ON and installer spoof (`adb install -i com.android.vending`) did NOT fix it outside a DHU session.
- Decision: Penn chose Play internal testing. Built `release/auto-penn-0.2.aab` (versionCode 2), signed with upload key ~/.android/autopenn-upload.jks (props in ~/.android/autopenn-signing.properties; BACK THESE UP). New icon from "Fountain Nib Speedway Icon.png" (docs/icon-source.png; old one docs/icon-source-old.png), Play 512 icon docs/play-icon-512.png.
- Next: Penn creates the Play Console app + internal testing track, uploads the .aab, installs via the tester link on the Razr, then re-check CAR.VALIDATOR / Customize launcher. Caveat: NAVIGATION category apps may need Play review for production; internal testing should not.
- Package renamed to com.penn.auto_penn (Play requirement; Kotlin namespace stays com.autopenn.mirror). Bundle: release/auto-penn-0.2-com.penn.auto_penn.aab (versionCode 3). Helper.APP_PACKAGE + tools/diag scripts updated; Pi priv-app still has the old package and needs the new APK before its helper works.

## Session 5b (2026-10-11): helper without a Mac
- Play-installed (release) build: run-as fails (not debuggable) and pm path returns split APKs, so tools/diag/razr-helper.sh cannot start the helper for it. That is why the in-car YouTube button did nothing (helper not running, HelperClient only logs).
- v0.3 (versionCode 4): app starts its own helper. AdbManager.kt (libadb-android over Wireless debugging, key in filesDir), HelperSetup.kt (pairing via NSD + notification RemoteInput code, startHelper = connect + 'setsid nohup app_process ... Helper tcp <token>' using ApplicationInfo.sourceDir), PairingService, MainActivity buttons + status, helper auto-restart on car session. Wi-Fi + Wireless debugging needed only at (re)start; helper should survive until reboot (UNTESTED: confirm it survives Wireless debugging off). Bundle: release/auto-penn-0.3-com.penn.auto_penn.aab.
- Shizuku not needed.
- RESULT (2026-10-11, Razr, Play build 0.3.3 / versionCode 7): in-app pairing + helper start WORKS end to end, no Mac. Verified: pair via notification code, Start helper -> shell-uid helper listening on 127.0.0.1:5279, helper survives Wireless debugging being turned off, DHU session: YouTube button -> task on the AutoPennMirror display.
- Gotchas found: (1) NsdManager callbacks must not use an executor we shut down (crash). (2) adbd kills a freshly forked child when a RAW shell stream closes immediately (adb exec-out / libadb); hold the stream open ~4 s (`& sleep 4`). (3) Android prompts "Allow wireless debugging on this network?" on first enable; human must tap Allow. (4) tools: scratchpad ui.py/pair.py automated the Settings UI over USB adb (uiautomator dump + input tap; notification RemoteInput works via adb input text).
- Play publishing flow (internal testing) via Playwright documented in memory play-publishing.md.
- TODO: confirm in the real Corvette; helper needs re-start after each phone reboot (Wi-Fi + Wireless debugging on, tap Start helper, or just connect the car: service auto-restarts it if paired).

## Session 6 (v1 branch): auto-start + car home screen
- Repo: github.com/calebphess/auto-penn (public). v0 tagged on main, work on branch v1.
- 0.4.0: app turns Wireless debugging on itself (needs one-time `adb shell pm grant com.penn.auto_penn android.permission.WRITE_SECURE_SETTINGS`). Verified on the Razr: helper killed + wireless debugging off -> Start helper re-enabled it and relaunched the helper.
- 0.4.1: car session retries starting the helper for 2 min. 0.4.2: HomeScreen (ListTemplate, clock in title, app list), mirror's button is now "Apps" (pops back). 0.4.3: Maps and Spotify added.
- Wi-Fi gotcha: wireless debugging only works on a Wi-Fi network already approved on the phone; helper survives network changes once running.
- Untested: HomeScreen on a real head unit/DHU, auto restart in the car.
