# LAST RESORT: disables the stock Google TV launcher for user 0 so only CleanTV answers Home.
# Reversible with restore-stock-launcher.ps1. Requires -Yes to confirm.
param([switch]$Yes)
if (-not $Yes) { Write-Host "Add -Yes to confirm."; exit 1 }
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell cmd package set-home-activity "com.cleanyourtv.launcher/.MainActivity"
Write-Host "Done. Press Home on the remote."
