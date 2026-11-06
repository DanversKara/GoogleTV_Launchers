@echo off
REM Disable the stock Google TV / Android TV launcher so it can't take over Home.
REM Set TV Launcher as the DEFAULT launcher first, or you'll get a black home screen.
REM Re-enable: adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tvlauncher
echo Stock launcher disabled. Re-enable with:
echo   adb shell pm enable com.google.android.apps.tv.launcherx
echo   adb shell pm enable com.google.android.tvlauncher
