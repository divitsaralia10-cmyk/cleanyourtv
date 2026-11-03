# Makes CleanTV the Home screen. Run with the TV connected via adb (adb connect IP:port first).
$ErrorActionPreference = "Continue"
$pkg = "com.cleanyourtv.launcher"
Write-Host "Setting CleanTV as the home app..."
adb shell cmd package set-home-activity "$pkg/.MainActivity"
adb shell cmd role add-role-holder android.app.role.HOME $pkg
Write-Host "Resolved Home app is now:"
adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
Write-Host "Press Home on the remote to test. If Google TV still opens, run scripts\disable-stock-launcher.ps1 -Yes"
