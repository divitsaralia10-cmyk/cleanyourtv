# CleanTV

A clean, ad-free Android TV launcher (Kotlin + Jetpack Compose) with an Apple TV-style home screen,
plus ADB scripts to debloat a Sony BRAVIA Google TV.

Target device: Sony BRAVIA 4K AE2, Android 14 (SDK 34).

## What the launcher does (v0.1)

- Shows every launchable app in one focus-animated row (D-pad friendly, scale + border on focus)
- Background glow shifts colour with the focused app and drifts slowly
- Clock and date, no ads, no sponsored rows, no recommendations
- Refreshes automatically when apps are installed or removed
- Hide apps by adding package names to `HIDDEN_PACKAGES` in `Apps.kt`

## Build

Push to GitHub. The `Build APK` workflow produces `cleantv-debug-apk` under
Actions > the latest run > Artifacts. Or open the project in Android Studio and run `assembleDebug`.

## Install (Windows PowerShell, TV paired over wireless debugging)

```powershell
adb devices                                  # TV must show as "device"
.\scripts\install-launcher.ps1 -Apk .\app-debug.apk
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
