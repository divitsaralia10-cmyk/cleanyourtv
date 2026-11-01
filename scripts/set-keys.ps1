<#
  Sends your free API keys to the CleanTV app on the TV over ADB.
  Keys are stored on the TV only. They are never put in the repo or the APK.

  Get the keys (all free):
    -Tmdb     themoviedb.org > Settings > API   (movie lists + where to watch)
    -Omdb     omdbapi.com/apikey.aspx           (IMDb ratings)
    -Cricket  cricketdata.org                   (live cricket scores)

  Usage:
    .\set-keys.ps1 -Tmdb YOUR_KEY -Omdb YOUR_KEY -Cricket YOUR_KEY
  You can pass just one of them; keys you leave out are not changed.
  Open the CleanTV app once before running this, so Android has started it.
#>
param(
    [string]$Tmdb,
    [string]$Omdb,
    [string]$Cricket
)

$ErrorActionPreference = 'Stop'

if (-not (adb devices | Select-String 'device\s*$')) { throw 'No ADB device connected' }
if (-not ($Tmdb -or $Omdb -or $Cricket)) { throw 'Pass at least one of -Tmdb, -Omdb, -Cricket' }

$adbArgs = @('shell', 'am', 'broadcast', '-n', 'com.cleanyourtv.launcher/.KeysReceiver', '-a', 'com.cleanyourtv.SET_KEYS')
if ($Tmdb)    { $adbArgs += @('--es', 'tmdb', $Tmdb) }
if ($Omdb)    { $adbArgs += @('--es', 'omdb', $Omdb) }
if ($Cricket) { $adbArgs += @('--es', 'cricket', $Cricket) }

& adb @adbArgs
Write-Host 'Done. Reopen the Movies or Scores screen on the TV.'
