# CleanTV

A clean, ad-free Android TV launcher (Kotlin + Jetpack Compose) with a glass-style home screen,
plus ADB scripts to debloat a Sony BRAVIA Google TV.

Target device: Sony BRAVIA 4K AE2, Android 14 (SDK 34).

## What the launcher does (v0.2)

- Glass home screen: greeting, big clock, date, live weather (Open-Meteo, no API key)
- Procedural sky behind everything that follows the time of day: sunrise, day, dusk, night with
  stars, a moving sun or moon, and drifting mountains
- Ambient mode: after 90 seconds without a key press, a full-screen clock appears; any key returns
- Arcade: Snake and 2048, played with the remote's arrow keys, best scores saved on the TV
- Every launchable app in one focus-animated dock; refreshes when apps are installed or removed
- No ads, no sponsored rows, no recommendations

Personal settings (name, city, idle time) live in `Config.kt`. Hide apps via `HIDDEN_PACKAGES` in `Apps.kt`.

## Build

Every push to `main` runs the `Build APK` workflow. The APK is published at:

```
https://github.com/<owner>/<repo>/releases/download/latest/app-debug.apk
```

## Install (Windows PowerShell, TV paired over wireless debugging)

```powershell
adb devices
adb install -r app-debug.apk
adb shell cmd package set-home-activity com.cleanyourtv.launcher/.MainActivity
```

Undo at any time: `.\scripts\restore-stock-launcher.ps1`

## Debloat

```powershell
.\scripts\debloat.ps1               # dry run, tier 1
.\scripts\debloat.ps1 -Tier 1 -Apply
```

Removals are per-user (`pm uninstall -k --user 0`) and reversible with
`adb shell cmd package install-existing <package>`. A package-list backup is saved first.
The stock Google TV launcher is never removed or disabled.

## Roadmap

- Phase 2: "For you" posters from TMDB, left/right swipe to like or dislike
- Phase 3: Jarvis-style voice assistant
