package com.moomoo.tvlauncherbase

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent

/** FLOATING WINDOWS: permission status, open/close, layout, opacity, remote control. */
class OverlaySettingsActivity : RowListActivity() {

    override val screenTitle = "Floating camera windows"
    override val screenDesc = "Watch up to ${OverlayManager.MAX} cameras on top of a movie or any app. " +
        "Tip: map a remote button to “Floating cameras: show / hide” in Remote buttons."

    override fun rows(): List<Row> {
        val cams = BasePrefs.cameras(this)
        val out = mutableListOf<Row>()
        out += Row("Status", OverlayManager.statusLine(this)) {}
        if (!OverlayManager.canShow(this)) {
            out += Row("Turn on the Home guard service (recommended)",
                "Settings → Accessibility → Kara's TV → On. No other permission needed.") {
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) {}
            }
            out += Row("…or allow “Display over other apps”",
                "If this page is missing on your TV, run from a computer:\nadb shell appops set $packageName SYSTEM_ALERT_WINDOW allow") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) { Ui.toast(this, "This TV hides that page — use the adb command shown") }
            }
        }
        if (cams.isEmpty()) {
            out += Row("Add a camera first", "Settings → Cameras") {
                startActivity(Intent(this, CamerasActivity::class.java))
            }
            return out
        }
        out += Row("Open windows", header = true)
        cams.forEachIndexed { i, cam ->
            out += Row("Float “${cam.name}”", cam.url) { OverlayManager.show(this, i); refresh() }
        }
        out += Row("Float all cameras", "Up to ${OverlayManager.MAX}") { OverlayManager.showAll(this); refresh() }
        out += Row("Close all floating windows", "${OverlayManager.count} open · the ⧉ button reopens them just as you left them") { OverlayManager.hideAll(); refresh() }
        out += Row("Arrange & control", header = true)
        out += Row("Move / resize with the remote", "OK cycles Move → Resize → Pick window → Change camera · Back finishes") {
            OverlayManager.enterControl(this)
            moveTaskToBack(true)
        }
        out += Row("Arrange windows", "Snap open windows into a layout") {
            val ls = OverlayManager.Layout.values()
            Ui.choose(this, "Arrange", ls.map { it.label }) { OverlayManager.arrange(this, ls[it]) }
        }
        out += Row("Picture fit (no black bars)", OverlayManager.fitLabel(this)) {
            val modes = OverlayManager.FIT_MODES
            Ui.choose(this, "Floating window picture", modes.map { it.second }) {
                OverlayManager.setFit(this, modes[it].first); refresh()
            }
        }
        out += Row("Opacity", "${BasePrefs.overlayOpacity(this)}%") {
            val opts = listOf(100, 90, 80, 70, 60, 50, 40)
            Ui.choose(this, "Window opacity", opts.map { "$it%" }) { OverlayManager.setOpacity(this, opts[it]); refresh() }
        }
        return out
    }
}

/**
 * Fallback key catcher for control mode when the accessibility service is
 * off: a transparent activity that forwards keys to [OverlayManager].
 */
class OverlayControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val consumed = OverlayManager.handleKey(this, event)
        if (!OverlayManager.controlMode) { finish(); return true }
        return consumed || super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) { OverlayManager.exitControl(); finish() }
    }
}
