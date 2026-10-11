# Roadmap: v1 (Pi 4) -> v2 (works on a real Android phone) -> v3 (a real app)

Written 2026-10-06. v1 is working in the lab (DHU). Nothing here has been tried in a real car yet.

## Principle that makes v2 and v3 possible

Almost everything we built is plain Android code that does not care what hardware it runs on:
mirror app (CarAppService + VirtualDisplay) and the Android Auto protocol handling that Google's app already does for us.
The Pi-specific parts are small and isolated:

| Piece | Pi 4 (v1) | Phone (v2/v3) |
|---|---|---|
| USB phone mode + power | Y cable, gadget mode on the Pi's USB-C | native, the phone already is a phone |
| H.264 encoder for Android Auto | broken hardware encoder disabled via vendor XML, software encoder | phone's hardware encoder just works, no patch |
| AV1/VP9 video decode | disabled via vendor XML so YouTube uses H.264 | hardware decoders for everything, no patch |
| Privileged helper (launch apps on our display + inject touch) | init-started shell-uid daemon (`device/autopenn-helper.rc`) | Shizuku / wireless debugging / root (see v2) |
| Google services, Widevine | flashed zips + "uncertified" registration | stock, certified |
| Boot behaviour | PIN unlock, SD card, power cuts | phone sleeps/wakes normally |

So the plan is: put a **helper-backend interface** in the app, keep the Pi backend, add phone backends, then make the
app friendly enough for anyone to install.

## v1.5 (finish the Pi build before the car)

Goal: usable in the Corvette. Acceptance: you park, plug in, open Auto Penn Mirror on the car screen, pick YouTube, it plays with sound, touch works.
- [ ] In-car launcher screen in the mirror app (list of installed video apps) instead of the adb broadcast hook. Remove the exported `LAUNCH` receiver.
- [ ] Helper starts apps by explicit component (Netflix needs `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`); resolve with `cmd package resolve-activity --brief`.
- [ ] Crunchyroll (and any non-resizable app) lands on display 0: try `settings put global force_resizable_activities 1` (+ reboot) and `enable_non_resizable_multi_window`.
- [ ] Pre-grant runtime permissions that pop dialogs on display 0 (`pm grant ... POST_NOTIFICATIONS`).
- [ ] Better touch: map `onScroll`/`onFling` to proper drags, add long-press via repeated clicks, tune coordinates against the real car resolution (check what the Corvette requests; our DHU size is arbitrary).
- [ ] Power-cut safety: SD cards corrupt on abrupt power loss. Options: make `/data` journaling-safe (fsck on boot), or a small supercap/UPS HAT, or accept and keep a spare card image.
- [ ] Decide the screen lock (needs unlocking after each boot, see cleanup-instructions.md 4.5).
- [ ] Sign in to Netflix/Crunchyroll yourself, test Widevine L3 playback (expect SD/540p-720p).
- [ ] Do the hardening list in cleanup-instructions.md section 4 (sshd off, adb tcp off, stock ramdisk, remove Magisk, close the receiver).
- [ ] Real Corvette test: Y cable + 12 V charger, car resolution, USB data/power behaviour, boot time.

## v2: same code on a real Android phone

Goal: install the mirror app on a normal Android phone (Android 11+), plug into the car, same experience, no ROM patching.

### Why a phone is easier
- Hardware H.264 encoder and hardware decoders for H.264/HEVC/VP9/AV1 work out of the box (no codec XML patches, no CPU ceiling).
- Android Auto, Play services and Widevine are stock. Battery rides through car power cycles. No SD card.

### What must change in our code
1. **Helper backends** (the main work). The phone has no init-started shell daemon. Backend interface `Helper { launch(display, component); tap(...); swipe(...) }` with implementations:
   - `PiShellHelper` (current socket client).
   - `ShizukuHelper`: Shizuku runs a shell-privileged service started once per boot from the phone's own "Wireless debugging" (no root, no computer after the first pairing). Needs user to re-activate after each reboot on Android 11+.
   - `RootHelper`: Magisk `su` for rooted phones (starts nothing at boot, permanent).
   - `AdbSelfHelper` (optional): the app embeds an adb client (libadb-android) and connects to the phone's own wireless-debugging port; same effect as Shizuku without a dependency.
2. Run the same `Helper.kt` logic (launch via `am start --display`, inject MotionEvents) inside whichever shell-privileged process the backend provides.
3. Android Auto must be told to show non-Play apps: AA developer settings -> "Unknown sources". Document this step in setup.
4. Orientation/resolution: read the real surface size from `SurfaceCallback` and size the VirtualDisplay to match (already done); add a user "render scale" (e.g. 75%) to save battery/heat.
5. Remove everything Pi-specific from the default path: vendor XML patches, gadget mode, Magisk.

### Known risks to test early on a phone (do these first, before building anything)
- **DRM / secure content.** On phones with Widevine **L1**, Netflix/Crunchyroll HD streams are rendered on a *secure* surface and show black on a normal VirtualDisplay (this is the same reason screen recording blacks them out). The Pi's L3 does not have this problem. Mitigations: Netflix/Crunchyroll may fall back to L3 SD if L1 playback fails to start (unreliable); or a privileged `FLAG_SECURE` virtual display (needs `CAPTURE_SECURE_VIDEO_OUTPUT`, which only the AA role holder / system apps have, so probably not possible unrooted). YouTube is fine either way. **Test Netflix on the candidate phone before buying/committing.**
- **Android Auto limits.** AA may refuse to show a "navigation" app that is not driving to a destination, or hide it while moving on some cars. We saw it work in the DHU only. Verify on the real Corvette head unit early.
- **Shizuku friction.** Without root, someone must start Shizuku after every phone reboot (wireless debugging toggle). Acceptable for a daily driver phone, annoying for an appliance.
- **Thermals/battery.** The phone encodes + decodes while charging in a hot car; throttling is likely on cheap phones.
- **Multiple displays / Android versions.** Behaviour of `am start --display` and virtual display focus changed across versions; define a minimum (Android 13+ suggested) and test on two phones.

### v2 milestones
1. Pick one test phone (Pixel 6a or newer, or any Android 13+ with hardware AV1/VP9 decode and unlocked bootloader for optional root).
2. Install the existing APK, enable AA developer mode, run in the DHU. Expect: surface + virtual display OK, launching apps fails until a helper backend exists.
3. Implement `ShizukuHelper` + `RootHelper` behind the interface; keep `PiShellHelper`. Unit-test the protocol against a fake server.
4. Make Netflix/Crunchyroll/YouTube launch + touch work on the phone in the DHU; measure battery drain and temperature over a 30-minute video.
5. Real car test with the phone.

## v3: a real app anyone can install ("sick")

Goal: one APK on any recent Android phone that turns Android Auto into a parked-video experience, with a friendly setup.

Proposed shape (single APK, no Pi required):
- **Setup wizard** that walks through: enable Android Auto developer mode + unknown sources, grant Shizuku or pair wireless debugging (or detect root), pick helper backend, test with a built-in tone/video, show a green checklist. This is the hardest UX problem and the thing that makes it feel like a product.
- **In-car UI**: app grid/search of installed media apps, recent apps, big touch targets, on-screen remote (back/home/seek), per-app render scale and "use H.264 only" hints.
- **Safety**: only render video when the car reports parked (use Car App Library car hardware APIs / `CarInfo` where available, otherwise require a user "I am parked" confirmation that re-locks on motion). Video while driving is illegal in most places; this must be a hard default, not an option.
- **Input**: better touch mapping, optional touchpad mode, support for the Corvette's rotary controller as focus navigation if exposed by AA.
- **Audio**: already free (AA captures device audio); add per-app volume if needed.
- **Open source** (GitHub/F-Droid). Google Play policy will almost certainly reject a navigation-category app that mirrors video and uses shell privileges, so plan for sideload/F-Droid distribution and clear safety/legal wording. No closed-source or anonymous components (the original reason for dropping Screen2Auto).
- **Architecture cleanup**: modules `core` (VirtualDisplay, input mapping), `backend-*` (Pi/Shizuku/Root/AdbSelf), `app` (wizard + car UI), `docs`. CI builds, instrumented tests on an emulator with the Desktop Head Unit.
- **Compatibility matrix** in the README: Android versions, phones, cars, known-bad apps (DRM L1).

### v3 milestones
1. Extract backends + core into modules; keep the Pi build as one backend.
2. Wizard MVP (Shizuku path) on two phones.
3. Parked-only safety gate and tests.
4. Public repo with docs (reuse `docs/android-auto-architecture.md`), release a first APK, collect reports from other cars.

## v2 validation result (2026-10-10, razr+ 2024)
- Works on a stock phone with no ROM patching: hardware encoder, ~29 fps with YouTube (see STATUS.md session 4b).
- SELinux enforcing blocks the Pi's unix-socket helper link; TCP loopback + token fallback was added.
- **Widevine L1 test done: Netflix is BLACK (audio, subtitles and controls work, picture does not).** YouTube is fine. See STATUS.md. Phones are great for YouTube and not for Netflix/Crunchyroll unless L3 is forced.
- Netflix also moves itself to display 0; the helper must keep moving its task (`am display move-stack`).
- Still open: (old item) Widevine L1 test on Netflix/Crunchyroll, Shizuku backend (helper currently started from the Mac), thermals, real car.

## Decision log
- 2026-10-06: Stayed on Pi 4 for v1 (option 1) instead of Pi 5/RK3588; long-term target is a phone, then an app.
- Screen2Auto / AAAD stay banned (closed source, anonymous developer); our own app replaces them.
