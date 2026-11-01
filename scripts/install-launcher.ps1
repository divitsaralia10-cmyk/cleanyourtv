<#
  Installs the CleanTV APK and makes it the default home app.
  Usage:  .\install-launcher.ps1 -Apk .\app-debug.apk
  Undo:   .\restore-stock-launcher.ps1
#>
param(
    [string]$Apk = '.\app-debug.apk'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }
if (-not (adb devices | Select-String 'device\s*$')) { throw 'No ADB device connected' }

adb install -r $Apk
adb shell cmd package set-home-activity com.cleanyourtv.launcher/.MainActivity

Write-Host 'Installed. Press Home on the remote to test.'
Write-Host 'If anything looks wrong, run restore-stock-launcher.ps1'
