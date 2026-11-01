# CleanTV

A clean, ad-free Android TV launcher (Kotlin + Jetpack Compose) with a glass-style home screen,
plus ADB scripts to debloat a Sony BRAVIA Google TV.

Target device: Sony BRAVIA 4K AE2, Android 14 (SDK 34).

## What the launcher does (v0.3)

- Glass home screen: greeting, big clock, date, live weather (Open-Meteo, no key)
- Procedural sky that follows the time of day, with a moving sun or moon and drifting mountains
- Ambient mode: after 90 seconds of no key presses, a full-screen clock; any key returns
- **Favorite apps** pinned in the dock (edit `FAVORITE_PACKAGES` in `Config.kt`), plus an **All apps** screen
- **Live scores**: home card plus a Scores screen with Cricket and Football tabs.
  Football comes from ESPN's public feeds (no key). Cricket needs a free CricketData key.
  Teams in `FOLLOW_TEAMS` (default India) are listed first.
- **Movies**: browse by category (Trending, Bollywood, Action, Comedy, ...) with posters, ratings,
  and which apps stream, rent or sell the title in India. Needs a free TMDB key; IMDb ratings need a free OMDb key.
  Rent and buy prices and time limits are not available from these services, so the app shows which apps offer it.
- **Settings** button that opens the TV's system settings
- **Arcade**: Snake and 2048, with best scores saved on the TV
- No ads, no sponsored rows, no recommendations

Personal settings (name, city, favorites, leagues) live in `Config.kt`.

## Build

Every push to `main` runs the `Build APK` workflow. The APK is published at:

```
https://github.com/<owner>/<repo>/releases/download/latest/app-debug.apk
```

Each CI build is signed with a fresh debug key, so updating an installed copy needs
`adb uninstall com.cleanyourtv.launcher` first (this also clears the saved keys; run `set-keys.ps1` again).

## Install (Windows PowerShell, TV paired over wireless debugging)

```powershell
adb devices
adb install -r app-debug.apk
adb shell am start -n com.cleanyourtv.launcher/.MainActivity
```

## API keys (all free, stored only on the TV)

```powershell
.\scripts\set-keys.ps1 -Tmdb YOUR_KEY -Omdb YOUR_KEY -Cricket YOUR_KEY
```

## Debloat

```powershell
.\scripts\debloat.ps1               # dry run, tier 1
.\scripts\debloat.ps1 -Tier 1 -Apply
```

Removals are per-user (`pm uninstall -k --user 0`) and reversible with
`adb shell cmd package install-existing <package>`. A package-list backup is saved first.
The stock Google TV launcher is never removed or disabled.

## Roadmap

- Swipe left/right to like or dislike movies, with personalised picks
- Jarvis-style voice assistant
