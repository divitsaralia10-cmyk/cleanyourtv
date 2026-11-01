<#
  Puts the stock Google TV launcher back as the default home app.
  CleanTV stays installed; uninstall it with:  adb uninstall com.cleanyourtv.launcher
#>
$ErrorActionPreference = 'Stop'

if (-not (adb devices | Select-String 'device\s*$')) { throw 'No ADB device connected' }

adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity
Write-Host 'Stock launcher restored as default. Press Home to check.'
