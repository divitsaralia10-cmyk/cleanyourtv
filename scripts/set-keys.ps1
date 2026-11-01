<#
  Sends your free API keys to the CleanTV app on the TV over ADB.
  Keys are stored on the TV only. They are never put in the repo or the APK.

  Get the keys (all free):
    -Omdb       omdbapi.com/apikey.aspx   (movie posters, IMDb ratings, plots)
    -Watchmode  watchmode.com             (where to watch, rent/buy prices; optional)
    -Cricket    cricketdata.org           (live cricket scores)

  Usage:
    .\set-keys.ps1 -Omdb YOUR_KEY -Watchmode YOUR_KEY -Cricket YOUR_KEY
  You can pass just one of them; keys you leave out are not changed.
  Open the CleanTV app once before running this, so Android has started it.
#>
param(
    [string]$Omdb,
    [string]$Watchmode,
    [string]$Cricket
)

$ErrorActionPreference = 'Stop'

if (-not (adb devices | Select-String 'device\s*$')) { throw 'No ADB device connected' }
if (-not ($Omdb -or $Watchmode -or $Cricket)) { throw 'Pass at least one of -Omdb, -Watchmode, -Cricket' }

$adbArgs = @('shell', 'am', 'broadcast', '-n', 'com.cleanyourtv.launcher/.KeysReceiver', '-a', 'com.cleanyourtv.SET_KEYS')
if ($Omdb)      { $adbArgs += @('--es', 'omdb', $Omdb) }
if ($Watchmode) { $adbArgs += @('--es', 'watchmode', $Watchmode) }
if ($Cricket)   { $adbArgs += @('--es', 'cricket', $Cricket) }

& adb @adbArgs
Write-Host 'Done. Reopen the Movies or Scores screen on the TV.'
