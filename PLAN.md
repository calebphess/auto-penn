# auto-penn: Raspberry Pi 4 as a video "phone" for the 2024 Corvette

Goal: a Pi 4 that plugs into the Corvette's console USB port, shows up to the car as an
Android Auto phone, and streams Netflix / YouTube / Crunchyroll to the dash screen while parked.

Written 2026-10-05.

## What the research says

- Android Auto's wired mode is "phone is a USB peripheral, car is the USB host". The Pi 4's
  USB-C port is the only port that can be a peripheral (dwc2 gadget mode). So the car cable
  goes into the Pi's USB-C port, and power has to come from somewhere else (Y cable or GPIO).
- No turnkey project exists for "Pi pretends to be an Android phone and shows video". Two
  realistic routes:

### Route A (recommended): run real Android on the Pi
- KonstaKANG LineageOS 23.2 (Android 16) for Pi 4, build 20260520. Actively maintained.
- Its kernel has the Android Open Accessory gadget (CONFIG_USB_CONFIGFS_F_ACC=y), which is
  exactly what a car head unit asks the phone to switch into. Verified in the lineage-rpi
  kernel defconfig.
- Add-ons from the same site: MindTheGapps 16 (Google services), Magisk 30.7 (root),
  Widevine L3 zip (needed for Netflix/Crunchyroll DRM; SD/720p quality).
- Then: register the device as "uncertified" with Google using its GSF ID so Play Services
  behaves, install Android Auto, install Screen2Auto (root screen-mirroring into Android
  Auto, the standard trick on rooted phones) plus the Netflix/YouTube/Crunchyroll apps.
- Prior art: one XDA user in 2023 got a Pi 4 on an older Konsta build to the point where the
  car started the Android Auto authorization screen, but he was using microG instead of
  real Google services and never finished the setup wizard. Nobody has published a finished
  build. So this is plausible, not proven. Expect debugging in the car.

### Route B (fallback): Linux on the Pi + implement the phone side of the protocol
- Raspberry Pi OS + the AACS project (tomasz-grobelny/AACS). It implements the phone half
  of Android Auto over USB gadget and streams a Linux desktop via GStreamer, with touch
  forwarded back. Built for Odroid N2, dormant since Feb 2024. A 2021 Pi 4 attempt got
  through auth and service discovery, then hit an unimplemented message.
- Video source would be Chromium with Widevine (officially packaged for Pi OS).
- More engineering (protocol work by me), more control, no Google account games.

### Route C (no build): a commercial "AI box" (Carlinkit TBox etc.) does this out of the box.
  Listed only so it's a known option.

## Hardware to have on hand (Route A)
- Pi 4 Model B (have) + 128 GB microSD (have, currently NTFS, /dev/disk2 on the Mac).
- USB-C OTG Y / splitter cable: one leg USB-C data to the car's USB-C port, one leg power in.
  (The 2023 XDA attempt only worked after switching from a plain cable to one of these.)
- 12 V socket USB charger rated 3 A at 5 V to feed the power leg.
- For first boot at home: HDMI monitor/TV + micro-HDMI cable, USB keyboard + mouse.
- Optional: micro-HDMI dummy plug if the build needs a display attached to render.

## Phases

1. Flash the SD card (Mac): write the LineageOS image with dd, verify SHA256 first.
2. First boot at home on a monitor: Wi-Fi, enable ADB, boot to TWRP, flash MindTheGapps,
   Magisk, Widevine, factory reset, reboot.
3. Set up remote access so I can drive it: adb over Wi-Fi + scrcpy from the Mac (install via
   Homebrew). Pi and Mac on the same network (home Wi-Fi now, phone hotspot in the car).
4. Google: sign in, grab GSF ID, register at google.com/android/uncertified, confirm Play
   works. Install Android Auto (sideloaded, Play hides it), Netflix, YouTube, Crunchyroll.
   CHANGED 2026-10-06: Screen2Auto/AAAD dropped (closed source, anonymous dev). We are writing our own
   open car app (mirror-app/) that mirrors a VirtualDisplay onto the car surface. See STATUS.md.
   Confirm Widevine L3 shows in a DRM-info app.
5. Bench test the USB side: with the Pi's USB-C plugged into the Mac, confirm ADB shows
   up (proves gadget mode). Optionally run an Android Auto head-unit emulator (Google's
   Desktop Head Unit, or OpenAuto on the Mac) to test the handshake before going to the car.
6. In the car: Y cable, phone hotspot, Mac on the hotspot, scrcpy to the Pi, plug into the
   console USB-C, walk through the Android Auto pairing wizard, then launch Screen2Auto.
7. Polish: auto-start, headless display config, resolution tuned to the Corvette screen,
   boot time, case + mounting in the console.
8. Harden (last): turn off Rooted debugging, ADB over network/old ADB, Developer options, SSH
   server and serial console; revoke adb authorizations; restore backups/ramdisk.img.orig
   (removes force_debuggable / ro.adb.secure=0); limit Magisk root to Screen2Auto; no default
   passwords. Details in STATUS.md "HARDEN BEFORE CAR USE".

## Open questions / risks
- Google may block Android Auto on an uncertified device even after registration.
- Android Auto wizard needs a touch-capable "phone" screen; the 2023 attempt saw
  "no touchable window" errors with HDMI + mouse. scrcpy may or may not satisfy that.
- Power: the Pi browns out below ~2.5 A. Separate power leg is not optional.
- If Route A dead-ends at the pairing wizard, switch to Route B with the same hardware.
