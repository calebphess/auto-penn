# Cleanup instructions (security audit of the Pi, 2026-10-06)

Scope: you asked whether the old software (Screen2Auto and its installer "AAAD", the anonymous Russian-language
developer's apps) could have taken passwords or left anything behind on the Pi. This is the result of a read-only
sweep run as root over adb, plus the list of things to undo before the Pi goes in the car.

## 1. Verdict

- **No remnants of Screen2Auto, AAAD, Frida or inceptive.ru were found** (details in section 3).
- **No unexplained apps, services, certificates, VPN/proxy, device admins, accessibility services, Magisk modules or
  Magisk root grants.** Only one adb key is authorised (your Mac).
- **The only changes to the read-only system partitions are ours** (3 intentional patches, section 4).
- **Exposure that cannot be checked from the device:** while Screen2Auto was installed it held an accessibility service,
  a notification listener and a draw-over-apps grant. That combination can read on-screen text and notification
  contents (including 2FA codes and password-reset notices) and it was reported to upload logs. Nothing on the device
  can tell us what it actually sent. Treat what was visible on the Pi's screen during that window as potentially seen
  (section 2).

## 2. What the old apps could have seen, and what to do

Window: from when it was installed in session 3 until you asked me to remove it (the packet capture at about 00:39-00:43
on 2026-10-06 was taken after removal, saw nothing going to inceptive.ru, and the app was idle).
Magisk root was never granted to it, so it could not read other apps' private data or the Google account database.

| Item | Could it have been exposed? | Action |
|---|---|---|
| Google account password | Only if typed on the Pi's screen while the app ran. Accessibility services normally do not get the contents of masked password fields, but they do see usernames, page text and key events. | If you typed it on the Pi in that window: change the password. Either way, review https://myaccount.google.com/device-activity and https://myaccount.google.com/security (sign out unknown sessions, check "Your devices", "Third-party access", "Recent security activity"). |
| Google account session | Not directly (no root, tokens are in the account manager, not readable by apps). | Optional: sign the Pi out and back in, or better, use a separate throwaway Google account for the Pi (see section 5). |
| 2FA codes / password-reset codes shown as notifications | Yes, if any arrived while it was installed (notification listener). | Check the Google security activity page for resets you did not do. |
| The Pi's screen PIN | Lock-screen typing is not visible to accessibility services. You gave me the dev PIN in chat, so it also exists in this session's transcript. | Change the PIN when you are done testing (or remove it, section 5). |
| Wi-Fi passwords | No Wi-Fi networks are saved (Pi is on Ethernet). | None. |
| Streaming-service logins | Not installed until today (YouTube/Netflix/Crunchyroll were added after the old apps were removed). YouTube is signed in with your Google account, Netflix/Crunchyroll are not signed in. | None. |
| Files on the device | Nothing sensitive was stored (no documents, photos or keys; only flashing zips, see 4.6). | None. |

## 3. What I checked, and the result

- Installed third-party apps: only Magisk, Google Maps, Google Contact Keys (plus today's YouTube, Netflix, Crunchyroll).
  Our mirror app is a system priv-app. Screen2Auto (`sksa.aa.customapps`), AAAD: **not installed**.
- Filesystem search for `sksa`, `screen2auto`, `inceptive`, `frida`, `aaad`, `customapps` across /data, /sdcard, /cache,
  /metadata: **no hits**. Orphan app-data directories (data left by uninstalled apps): **none**.
- Executables or `.so` files newer than 2026-10-05 in app/data areas: only my own `/data/local/tmp/mon/uidwatch.sh`.
- Accessibility services enabled: **none** (`accessibility_enabled=0`). Notification listeners: **only Android Auto**
  (the bogus `PreflightNotificationListenerService` entry I added by mistake is gone).
- Device admins: none. Overlay (draw-over-apps) grants: only the Google app (system). Install-unknown-apps grants: only
  Magisk. Manage-all-files grants: none.
- Input methods: Google keyboard + Google speech only (no rogue keyboard / keylogger).
- User-installed CA certificates: none. VPN: none. HTTP proxy: none.
- Magisk: modules directory empty, `service.d` and `post-fs-data.d` empty, **no apps have root** (policy table empty).
- Authorised adb keys: 1 (`penn@calebs-mbp.lan`).
- Added Google accounts: 1 (yours).
- Read-only partitions modified since the build date: exactly three files, all ours (section 4.1).
- Listening ports: 22 (sshd, see 4.2), 5555 (adb), 8872 (Bluetooth, loopback), 53601 (Google Play services, uid 10067,
  normal), plus normal mDNS/DHCP UDP. Nothing unknown.

## 4. Cleanup to-do list, itemised (do these before the Pi goes in the car)

### 4.1 Things we added on purpose (decide keep vs remove)
- [ ] `/vendor/etc/init/autopenn-helper.rc` + the shell-uid helper (`Helper.kt`): needed for the mirror app to launch
      apps / inject touch. Keep for the car, but it is a privileged boot service. Only the mirror app's uid can talk to it
      (SO_PEERCRED), and it runs as `shell`, not root.
- [ ] The mirror app is installed as a system priv-app at `/system/priv-app/AutoPennMirror/`. Its manifest has an
      **exported broadcast receiver** (`com.autopenn.mirror.LAUNCH`) that lets any app on the Pi trigger a launch. Remove
      or protect it before real use (it was only a dev hook).
- [ ] Vendor codec patches (all backed up in `backups/vendor-etc/*.orig`):
      `media_codecs_v4l2_c2_video.xml` (hardware H.264 encoder removed, needed for Android Auto video),
      `media_codecs_ffmpeg_c2.xml` and `media_codecs_google_c2_video.xml` (AV1/VP9 software decoders disabled so YouTube uses
      H.264; this works, see STATUS.md). Safe to keep; revert the last two if you want stock behaviour.

### 4.2 Debug and remote-access settings (turn off at the very end)
- [ ] **sshd is running** (`persist.sshd.enabled=1`) and its credentials are unknown to us. Turn it off in Raspberry Pi
      settings, or `setprop persist.sshd.enabled 0` and reboot.
- [ ] **ADB over network** is on and made permanent (`persist.adb.tcp.port=5555`). Remove with
      `setprop persist.adb.tcp.port ""` and turn off USB/network debugging in Developer options. Revoke USB debugging
      authorisations.
- [ ] Developer options on (`development_settings_enabled=1`), `ro.debuggable=1` (userdebug ROM), `adb root` is available
      to anyone holding the authorised Mac key.
- [ ] Turn off Bluetooth if you do not need it (Android Auto wanted it on during testing).
- [ ] SELinux is **permissive** on this ROM (`getenforce`). That is how KonstaKANG ships it; it cannot be changed from
      the settings. Just be aware nothing is sandboxed against root-level mistakes.

### 4.3 Boot ramdisk still contains my earlier debug patch
- [ ] `/boot/ramdisk.img` currently contains Magisk (`.backup/`, `overlay.d/`) **plus** `adb_debug.prop` and
      `force_debuggable` (my session-3 patch). The stock ramdisk we saved is `backups/ramdisk.img.orig` (verified: it
      contains none of those files; sha256 `9bd2702f...f3c`). To restore: mount the card's boot partition on the Mac (or from
      the Pi remount `/boot` rw) and copy `backups/ramdisk.img.orig` over `/boot/ramdisk.img`. Note this also removes Magisk.
      The Pi's `/boot/ramdisk-orig.img` is another unpatched ramdisk (hash differs from our backup by a few bytes, probably
      a different repack); either should boot, test before relying on it.
- [ ] Nothing the project needs still depends on Magisk or root: you can uninstall the Magisk app too
      (`com.topjohnwu.magisk`, the only app with install-unknown-apps permission). Do this together with the ramdisk restore.

### 4.4 My own temporary files
- [ ] `/data/local/tmp/mon/` (world-writable): `cap.pcap0` (packet capture of Screen2Auto removal), `uidwatch.sh`, logs.
      Safe to delete: `adb shell rm -rf /data/local/tmp/mon`.
- [ ] `/sdcard/Download/`: the flashing zips (about 520 MB: MindTheGapps, Magisk zip + apk, Widevine, resize). Delete when the
      build is final.
- [ ] Mac side: `downloads/`, `backups/`, and `docs/` stay in the project folder; nothing secret in them. The dev PIN is
      not written in any file.

### 4.5 Accounts and credentials
- [ ] The Pi is signed in as your main Google account (YouTube shows Premium). Strongly consider replacing it with a
      separate Google account used only for this device (then Google Play, YouTube and the registered GSF ID are isolated
      from your main account if the Pi is ever stolen).
- [ ] Change or remove the Pi lock PIN (the dev PIN appeared in this chat). Removing it makes the Pi usable after a
      power cut in the car without typing; the trade-off is that anyone with the box can open it. Remove with
      `adb shell locksettings clear --old <PIN>` (type it yourself so I never see it).
- [ ] Google "uncertified device" registration for this GSF ID (`3ae72d8bb995f885`) at google.com/android/uncertified: can be
      revoked from that page when you retire the box.

### 4.6 Not needed to clean
- Port 53601: Google Play services, normal.
- `.Spotlight-V100` folder on the boot partition: created by macOS when the card was mounted, harmless.

## 5. Things I could not verify
- What Screen2Auto actually uploaded (the logs it sent live on its developer's servers).
- Whether any 2FA or reset notification arrived during the window (only your Google security activity page can show that).
- Contents of other apps' private data (I did not read them and the old app had no root to read them either).
