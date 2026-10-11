# How Android Auto works, and how auto-penn plugs into it

Written 2026-10-06 at the end of session 4. "Verified" means we saw it in logs or on screen on the Pi. "Background"
means it comes from general knowledge of Android Auto / the Car App Library and was not independently checked here.

## 1. The big picture

Android Auto (AA) is **not** the phone mirroring its screen. The phone renders a UI on a **virtual display**, encodes it
as H.264, and streams it to the head unit (HU, the car's screen). The HU sends touch, buttons and sensor data back.
The HU is a "dumb" video player and input device; the phone runs all the apps.

```
 Car head unit (HU)                         Phone (our Pi 4 running LineageOS)
 ------------------                         -------------------------------------------
 USB host                  <== USB ==>      USB accessory (AOA) / or TCP in dev mode
 video decoder  <---- H.264 video ------    AA app (Gearhead + GMS car module)
 speakers       <---- PCM audio -------       - renders AA UI + Car App Library apps to virtual displays
 touchscreen    ----- touch/keys ---->        - MediaCodec H.264 encoder (software on our Pi, see section 6)
 GPS/sensors    ----- sensor data --->        - captures device audio (remote submix)
                                              - hosts third-party "car apps" (Car App Library)
```

## 2. The wire protocol (background, partly verified)

- Transport: wired = USB Android Open Accessory (AOA). The phone is the USB *peripheral*, the car is the host, which is
  why our Pi must use its USB-C port (dwc2 gadget) and be powered by a separate leg. The kernel needs
  `CONFIG_USB_CONFIGFS_F_ACC` (KonstaKANG's kernel has it).
  Dev/test transport (verified): AA developer settings -> "Start head unit server" opens TCP **5277** on the phone; the
  Desktop Head Unit (DHU) connects to it (via `adb forward tcp:5277 tcp:5277`).
- Protocol name (internal): GAL / "Android Auto Protocol". Handshake order (verified in DHU log and Pi logs):
  1. Version exchange ("Phone reported protocol version 1.7").
  2. TLS handshake (TLS 1.2, ECDHE-RSA-CHACHA20-POLY1305, BoringSSL on both sides).
  3. **Service discovery**: the HU lists what it can do; the phone opens one **channel** per service.
  4. Steady state: independent channels multiplexed on the one link.
- Channels we saw: **video** (H.264, flow control with "max unacked" frames = 2), **audio** (separate streams MEDIA, TTS,
  SYSTEM; PCM 48 kHz stereo for media; acks from the HU with ~20 ms latency), **input** (touch), **sensor**, **bluetooth**,
  audio-focus negotiation messages (AUDIO_FOCUS_GAIN / LOSS exchanged with the HU, the HU is the arbiter).
- If the HU quits (DHU exits on stdin EOF!), the phone logs `PROTOCOL_IO_ERROR / READER_CLOSE` and later "charge only" /
  `PROJECTION_NOT_STARTED`. That is the HU leaving, not necessarily a phone fault.

## 3. Phone-side pieces (verified by logs)

- `com.google.android.projection.gearhead` = the AA app. Several processes: `:projection`, `:car`, `:shared`.
  - `CAR.*` log tags = the car service (protocol, audio, video, window manager `CAR.WM`).
  - `GH.*` = Gearhead UI/logic (preflight, FRX = first-run experience, connection logger).
- **Preflight / FRX**: before projecting, AA needs: first-run setup (it pulls Google Maps, TTS, Google App from Play),
  the welcome/permissions dialog, **notification-listener access**, location/Bluetooth, etc. A missing requirement shows up
  as the HU sitting on "Waiting for phone..." while the phone shows a preflight screen or polls ("Permission polling timed out").
- **Window manager (`CAR.WM`)**: AA creates a virtual display ("LayoutDisplay") and composes windows onto it: facet bar,
  dashboard, content windows, tooltip. That composed surface feeds the encoder.
- **Encoder**: `CAR.VIDEO: Choosing codec ...` picks a MediaCodec H.264 encoder by name from the system codec list.
- **Audio**: AA captures the device's own playback through the **remote submix** audio HAL (`r_submix` in logs) and
  streams it on the MEDIA channel. So ANY app playing media on the phone is heard in the car with no app changes
  (verified 2026-10-06 with a tone in the Music app: `enabling stream: MEDIA`, ~1.5 Mbit/s PCM, acked by the HU).
  Audio focus is bridged: Android focus changes become AUDIO_FOCUS_* requests to the HU.
- Developer mode (tap the AA version 10x) adds the overflow menu: "Start head unit server", "Developer settings".
  The toggle must be redone after every force-stop of AA.

## 4. The Car App Library (what "coding for Android Auto" means)

Third-party apps do not draw arbitrary UI in AA. They use **androidx.car.app** (Car App Library). Background + what our
app does:

- Declare a `CarAppService` (manifest intent filter `androidx.car.app.CarAppService` + a category) and metadata
  `com.google.android.gms.car.application` pointing to `res/xml/automotive_app_desc.xml` (ours: `<uses name="template"/>`).
- Categories decide *where* the app appears and which templates it may use: NAVIGATION, POI, IOT, PARKING, CHARGING, etc.
  Only **NAVIGATION** apps may request a raw **Surface** (needs permission `androidx.car.app.ACCESS_SURFACE`) and use
  `NavigationTemplate`. That is the loophole auto-penn uses: it is a "navigation app" that draws whatever it wants.
- `CarAppService.onCreateSession()` -> `Session` -> `Screen` (our `MirrorScreen`). A Screen returns a **Template**
  (`NavigationTemplate` with an Exit action strip) and can register a `SurfaceCallback` through `AppManager.setSurfaceCallback`.
- `SurfaceCallback` gives: `onSurfaceAvailable(SurfaceContainer: surface, width, height, dpi)`, `onVisibleAreaChanged`,
  `onSurfaceDestroyed`, and *gesture-level* input only: `onClick(x,y)`, `onScroll(dx,dy)`, `onFling(vx,vy)`, `onScale`.
  There is **no raw down/move/up stream**, so touch fidelity is limited (drag-scrubbing, multi-touch, long-press are
  approximated or impossible).
- The surface is created by AA at the HU's size (720p DHU test: 1190x700 dpi 160; surface is recreated when the car UI
  toggles, e.g. entering/leaving the app, so display ids change, see below).
- `HostValidator`: the app decides which hosts may bind to it. Ours uses ALLOW_ALL_HOSTS (development only; tighten to
  Google's AA signatures before release).
- Car-side policy (background): AA restricts template depth, driver-distraction rules apply (video is normally blocked
  while moving on real cars; the Corvette use case is while parked).
- The app appears in the AA launcher as "Auto Penn Mirror"; in the DHU it auto-opens when it is the last used app.

## 5. What auto-penn adds on top (verified)

```
AA car service ----surface----> MirrorScreen (CarAppService)
                                   |  VirtualScreen.attach(): DisplayManager.createVirtualDisplay(surface, flags PUBLIC|OWN_CONTENT_ONLY|SUPPORTS_TOUCH)
                                   |  => anything shown on that virtual display is drawn straight into the car's video
                                   v
                          HelperClient --abstract socket "autopenn.helper"--> Helper (uid shell, started by init)
                                   START <display> <pkg>  -> am start --display N -a MAIN -c LAUNCHER -p pkg
                                   TAP / SWIPE            -> InputManagerGlobal.injectInputEvent(MotionEvent with displayId)
```

Why the helper exists (all verified dead ends before it):
- An app cannot launch other apps onto its own *untrusted* VirtualDisplay: SecurityException "Permission Denial:
  starting Intent ... with launchDisplayId=N". `ACTIVITY_EMBEDDING` (privileged) does not help.
- A *trusted* display needs `ADD_TRUSTED_DISPLAY`, which is `signature|role`; the only role that grants it,
  `SYSTEM_AUTOMOTIVE_PROJECTION`, is **static** (only the default holder, AA, qualifies).
- Platform-signing our APK is impossible (the ROM's platform key is KonstaKANG's private key).
- `su` is not on PATH for app processes on this Magisk setup.
- The shell uid has START_ANY_ACTIVITY and INJECT_EVENTS, so a small shell-uid daemon does both jobs. Access control is
  SO_PEERCRED: only the mirror app's uid may connect. SELinux on this ROM is permissive, so no policy work was needed.
- Touch test (verified): a DHU `tap` on the Settings list opened "Display"; the list scrolled via the swipe path.
- Display ids are not stable (id 9, 11, 15 seen). Always read `VirtualScreen.displayId` at the moment of use.

## 6. The Pi 4 specific gotchas (all fixed, verified)

- Hardware encoder `c2.v4l2.avc.encoder` fails on AA's virtual-display input: `C2AllocatorGralloc: lock(RGBA_8888)`
  failed, `V4L2EncodeComponent: Failed to convert input block`, MediaCodec UNKNOWN_ERROR, AA logs "Frame stuck or lost
  in encoder!" and the HU stays on "Waiting for phone...". Fix: remove the `<Encoders>` block from
  `/vendor/etc/media_codecs_v4l2_c2_video.xml` (decoder kept). AA then picks `c2.android.avc.encoder` (software).
  Original file: `backups/vendor-etc/media_codecs_v4l2_c2_video.xml.orig`. Software encode CPU cost at 1080p is NOT measured yet.
- After a Pi reboot the PIN lock screen must be unlocked before AA and app data work (credential-encrypted storage).
- Welcome dialog ignores `input tap`; Tab/Enter works. Notification listener must be granted with an escaped `$` in the
  component name. Bluetooth should be on (`cmd bluetooth_manager enable`).
- Network adb needs `persist.adb.tcp.port=5555` and a few seconds after boot.

## 7. Debug recipe (copy/paste order)

1. Unlock the Pi (PIN). `adb connect 192.168.86.24:5555`.
2. `adb shell am force-stop com.google.android.projection.gearhead`
3. `adb shell am start -a com.google.android.projection.gearhead.SETTINGS`; overflow menu -> "Start head unit server".
4. `adb forward tcp:5277 tcp:5277`
5. Run the DHU with stdin held open: `tail -f /dev/null | ./desktop-head-unit -c config/default_720p.ini`
   (or feed a FIFO to send `tap x y` commands). Capture ONLY the DHU window: `screencapture -x -l<windowid>`.
6. `adb shell am broadcast -a com.autopenn.mirror.LAUNCH --es pkg <package>` to put an app on the car screen.
7. Useful logcat filters: `CAR.VIDEO|CAR.MEDIA.VIDEO|Frame stuck` (video), `CAR.AUDIO` (audio), `GH.Preflight` (setup),
   `VirtualScreen|MirrorScreen|HelperClient` (our app).

## 8. Open questions / next

- Software H.264 encode load at 1080p on a Pi 4 (smoothness for video playback). Fallback: lower resolution / fps.
- Widevine L3 playback of Netflix/Crunchyroll on a non-secure virtual display (L3 should be OK; HD is not).
- Streaming apps are not installed yet.
- Proper launcher UI and better touch mapping (AA only gives click/scroll/fling).
- Replace the exported `LAUNCH` broadcast with an authenticated path before real use.
