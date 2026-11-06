package com.moomoo.tvlauncherbase

import android.content.Intent

/** SETTINGS: everything in one list, grouped. */
class SettingsActivity : RowListActivity() {

    override val screenTitle = "Settings"

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Backup.REQ_PICK && resultCode == RESULT_OK) data?.data?.let { uri ->
            val json = Backup.readUri(this, uri)
            if (json == null) Ui.toast(this, "Couldn't open that file") else Backup.confirmRestore(this, json) { recreate() }
        }
    }

    private fun onOff(v: Boolean) = if (v) "On" else "Off"

    override fun rows(): List<Row> {
        val c = this
        val out = mutableListOf<Row>()

        out += Row("Home screen & remote", header = true)
        out += Row("Customize home", "Google TV style, wallpaper, rows, app tile style & size") {
            startActivity(Intent(c, CustomizeActivity::class.java))
        }
        out += Row("Watching & upcoming", "${WatchStore.snapshot(c).size} show(s) · What's playing: " +
            if (MediaWatchService.instance != null) "on" else "off") {
            startActivity(Intent(c, WatchActivity::class.java))
        }
        out += Row("Cameras", "${BasePrefs.cameras(c).size} camera(s) · MotionEye / MJPEG / RTSP") {
            startActivity(Intent(c, CamerasActivity::class.java))
        }
        out += Row("Floating camera windows", OverlayManager.statusLine(c)) {
            startActivity(Intent(c, OverlaySettingsActivity::class.java))
        }
        out += Row(getString(R.string.keymap_title), "Remap any button: single, long and double press · service: " +
            ServiceSetup.a11yStatus(c)) {
            startActivity(Intent(c, KeyMapActivity::class.java))
        }
        out += Row("Button tester", "See live which buttons reach the remapper") {
            startActivity(Intent(c, KeyTestActivity::class.java))
        }

        out += Row("More home options", header = true)
        out += Row("Text size", "${BasePrefs.textSize(c)}% — makes all text bigger or smaller") {
            val o = listOf(90 to "Small (90%)", 100 to "Normal (100%)", 115 to "Large (115%)", 130 to "Extra large (130%)")
            Ui.choose(c, "Text size", o.map { it.second }) { BasePrefs.setTextSize(c, o[it].first); recreate() }
        }
        out += Row("Header size", "${BasePrefs.headerSize(c)}% — the top bar always shrinks to fit, so nothing is ever hidden") {
            val o = listOf(85 to "Small (85%)", 100 to "Normal (100%)", 115 to "Large (115%)", 130 to "Extra large (130%)")
            Ui.choose(c, "Header size", o.map { it.second }) { BasePrefs.setHeaderSize(c, o[it].first); refresh() }
        }
        out += Row("Search button", if (BasePrefs.showSearch(c)) "Shown in the top bar" else "Hidden") {
            BasePrefs.setShowSearch(c, !BasePrefs.showSearch(c)); refresh()
        }
        out += Row("Cameras button (📷)", if (BasePrefs.showCamButton(c)) "Shown in the top bar" else "Hidden") {
            BasePrefs.setShowCamButton(c, !BasePrefs.showCamButton(c)); refresh()
        }
        out += Row("Floating cameras button (⧉)", if (BasePrefs.showFloatButton(c)) "Shown in the top bar"
            else "Hidden — a remapped button still opens floating cameras") {
            BasePrefs.setShowFloatButton(c, !BasePrefs.showFloatButton(c)); refresh()
        }
        out += Row("Theme, clock & design", "${LauncherTheme.presetById(BasePrefs.themeId(c)).name} — 10 themes, fonts, focus effects, clock styles") {
            startActivity(Intent(c, CustomizeActivity::class.java))
        }
        out += Row("Show phone-only apps", onOff(BasePrefs.showAllApps(c)) + " — apps without a TV entry point") {
            BasePrefs.setShowAllApps(c, !BasePrefs.showAllApps(c)); AppRepository.invalidate(); refresh()
        }
        val hidden = BasePrefs.hiddenApps(c)
        out += Row("Hidden apps", if (hidden.isEmpty()) "None" else "${hidden.size} hidden — select to unhide") {
            if (hidden.isEmpty()) { Ui.toast(c, "Hide apps with Menu on an app tile"); return@Row }
            Ui.choose(c, "Unhide which app?", hidden.map { AppRepository.label(c, it) }) {
                BasePrefs.setHidden(c, hidden[it], false); refresh()
            }
        }
        out += Row("Show TV inputs row", onOff(BasePrefs.showInputs(c))) {
            BasePrefs.setShowInputs(c, !BasePrefs.showInputs(c)); refresh()
        }
        out += Row("App inputs (Xumo Play…)", if (BasePrefs.showAppInputs(c)) "Shown in the TV inputs row"
            else "Hidden — the TV inputs row shows HDMI only") {
            BasePrefs.setShowAppInputs(c, !BasePrefs.showAppInputs(c)); refresh()
        }
        out += Row(getString(R.string.rename_input), "Rename an HDMI input (e.g. PS5)") {
            val inputs = TvInputs.list(c)
            if (inputs.isEmpty()) Ui.toast(c, "No TV inputs found on this device")
            else Ui.choose(c, getString(R.string.rename_input), inputs.map { it.displayName(c) }) {
                TvInputs.showRenameDialog(c, inputs[it]) { refresh() }
            }
        }

        out += Row("Keep the Home button", header = true)
        out += Row(
            if (HomeHelper.isDefaultHome(c)) "✓ ${Brand.name} is the default home app" else "⚠ Make ${Brand.name} the default home app",
            "Required for the Home button"
        ) { HomeHelper.requestHomeRole(c) }
        out += Row("Home guard / remapper service", ServiceSetup.a11yStatus(c) +
            if (ServiceSetup.canSelfEnable(c)) " · self-repair enabled" else "") {
            if (HomeGuardService.instance != null) ServiceSetup.openAccessibility(c) else KeyMapActivity.showServiceFix(c)
        }
        out += Row(getString(R.string.guard_on), onOff(BasePrefs.guardEnabled(c)) + " — bounce Google TV's launcher back") {
            BasePrefs.setGuardEnabled(c, !BasePrefs.guardEnabled(c)); refresh()
        }
        out += Row(getString(R.string.boot_on_power), onOff(BasePrefs.bootOnPower(c))) {
            BasePrefs.setBootOnPower(c, !BasePrefs.bootOnPower(c)); refresh()
        }
        out += Row("Extra launcher packages to override", BasePrefs.extraGuardPackages(c).let {
            if (it.isEmpty()) "None — stock Google packages only" else it.joinToString(", ")
        }) {
            Ui.prompt(c, "Extra launcher packages (comma-separated)",
                BasePrefs.extraGuardPackages(c).joinToString(", "), "com.example.oemlauncher") {
                BasePrefs.setExtraGuardPackages(c, it); refresh()
            }
        }
        out += Row(getString(R.string.lockdown_title),
            if (DeviceOwnerHelper.isDeviceOwner(c)) "Device owner: active" else "Not a device owner (optional)") {
            startActivity(Intent(c, LockdownActivity::class.java))
        }

        out += Row("System", header = true)
        out += Row("Android TV settings", "Wi-Fi, display, remotes, apps…") {
            RemoteKeys.run(c, RemoteKeys.SYS_SETTINGS, null)
        }
        out += Row("Backup", header = true)
        val last = BasePrefs.lastBackupAt(c)
        out += Row("Automatic backup", (if (BasePrefs.autoBackup(c)) "On" else "Off") + " · saved to ${Backup.location}" +
            if (last > 0) " · last ${ShowInfo.whenText(last)}" else "") {
            BasePrefs.setAutoBackup(c, !BasePrefs.autoBackup(c)); refresh()
        }
        out += Row("Back up now", "Survives “Clear storage” and uninstalling (shared Downloads folder)") {
            if (Backup.needsPermission(c)) { Backup.requestPermission(c); return@Row }
            Thread {
                val ok = Backup.writeNow(c)
                runOnUiThread { Ui.toast(c, if (ok) "Backed up to ${Backup.location}" else "Backup failed — check storage"); refresh() }
            }.start()
        }
        out += Row("Restore from backup", "Settings, cameras, buttons, theme, favorites and shows") {
            Backup.restoreFlow(c) { recreate() }
        }
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        out += Row("About", "${Brand.name} $ver · ad-free · no tracking") {
            Ui.dialog(c).setTitle("${Brand.name} $ver")
                .setMessage(
                    "Ad-free home screen for Google TV / Android TV.\n\n" +
                    "• No ads, no sponsored rows, no recommendations, no tracking. Network use: your camera " +
                    "URLs, and show-name lookups on AniList / TVmaze for episode dates and artwork (can be " +
                    "switched off in Watching & upcoming).\n" +
                    "• Ads inside other apps (YouTube etc.) come from those apps and are not touched.\n" +
                    "• The remote's power button and some branded app buttons are handled by the TV " +
                    "before any app sees them — use Remote buttons → Learn a button to check yours."
                )
                .setPositiveButton(R.string.ok, null).show()
        }
        return out
    }
}
