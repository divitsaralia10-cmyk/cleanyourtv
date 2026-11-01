<#
  CleanTV debloat helper for a Sony BRAVIA 4K AE2 (Android 14, Google TV).

  - Dry run by default: it only prints what it WOULD remove.
  - Pass -Apply to actually remove, only after you have approved the list.
  - Always saves a package-list backup first.
  - Uses "pm uninstall -k --user 0": no root, keeps app data, reversible.
    Restore one package:  adb shell cmd package install-existing <package>

  Usage:
    .\debloat.ps1                 # dry run, tier 1
    .\debloat.ps1 -Tier 2         # dry run, tier 1 + 2
    .\debloat.ps1 -Tier 1 -Apply  # really remove tier 1
#>
param(
    [ValidateSet(1, 2)][int]$Tier = 1,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'

# Tier 1: promos, tracking, retail demo, unused Play Games.
$tier1 = @(
    'tv.samba.ssm',
    'tv.samba.ssm.ui',
    'com.sony.dtv.promos',
    'com.sony.dtv.sonyselect',
    'com.sony.dtv.demosupport',
    'com.sony.dtv.demosystemsupport',
    'com.sony.dtv.multiscreendemo',
    'com.google.android.play.games'
)

# Tier 2: only if you do not use them.
$tier2 = @(
    'com.amazon.amazonvideo.livingroom',
    'com.google.android.youtube.tvmusic',
    'com.sony.dtv.airplayapp',
    'com.sony.dtv.smarthelp',
    'com.sony.dtv.helpguidecontents',
    'com.sony.dtv.customersupport'
)

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw 'adb not found in PATH' }
if (-not (adb devices | Select-String 'device\s*$')) { throw 'No ADB device connected' }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = "package-backup-$stamp.txt"
adb shell pm list packages | Out-File $backup -Encoding ascii
Write-Host "Backup of package list saved: $backup"

$targets = if ($Tier -eq 2) { $tier1 + $tier2 } else { $tier1 }
$installed = (adb shell pm list packages) | ForEach-Object { ($_ -replace '^package:', '').Trim() }

foreach ($pkg in $targets) {
    if ($installed -notcontains $pkg) {
        Write-Host "skip (not installed): $pkg"
        continue
    }
    if ($Apply) {
        $result = adb shell pm uninstall -k --user 0 $pkg
        Write-Host "$pkg -> $result"
    }
    else {
        Write-Host "[dry-run] would remove $pkg"
    }
}

if (-not $Apply) {
    Write-Host 'Dry run only. Re-run with -Apply once you have approved this list.'
}
