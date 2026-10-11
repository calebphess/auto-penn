# First boot checklist (Phase 2)

Hardware for the bench setup at home:
- Pi 4 with the flashed microSD inserted.
- HDMI monitor or TV on micro-HDMI port 0 (the one closest to the USB-C power port).
- USB keyboard and mouse in the USB-A ports.
- Official-style 5 V / 3 A USB-C power supply into the USB-C port. (Do not use a laptop port, it is too weak.)
- Home Wi-Fi name and password handy.

Steps:
1. Power on. First boot takes 2 to 5 minutes and may show a black screen for a while. If you only
   ever see a rainbow square, the display mode is wrong: tell me, and I will edit resolution.txt on the card.
2. Go through the LineageOS setup wizard. Skip anything optional. Connect to home Wi-Fi.
3. Enable developer options: Settings > About tablet > tap "Build number" 7 times.
4. Settings > System > Developer options: turn on "Rooted debugging" and "Wireless debugging"
   (or "ADB over network"). Note the IP address shown under Settings > About > IP address.
5. Settings > System > Raspberry Pi settings: turn on the SSH server if offered (backup access path).
6. Tell me the IP. I connect with adb over Wi-Fi from the Mac and take it from there:
   TWRP flash of MindTheGapps, Magisk, Widevine, then factory reset, then Google sign-in.

Keyboard tips on this build: F5 = power button, F12 = recovery menu options are in the Android
power menu once "Advanced restart" is enabled in Developer options.
