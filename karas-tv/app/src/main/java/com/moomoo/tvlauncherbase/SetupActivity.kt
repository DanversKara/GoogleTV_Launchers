package com.moomoo.tvlauncherbase

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * First-run setup:
 * 1. Make TV Launcher the default home app.
 * 2. Turn on the Home guard service (keeps Home, powers the button
 *    remapper and the floating camera windows). Android never lets an
 *    app switch this on itself, so this opens the system page.
 * 3. Optional: add a MotionEye camera.
 */
class SetupActivity : Activity() {

    private var offered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherTheme.load(this)
        // Fresh start (e.g. after Clear storage): offer to restore the last backup.
        if (Backup.needsPermission(this)) Backup.requestPermission(this) else offerRestore()
    }

    private fun offerRestore() {
        if (offered) return
        offered = true
        Thread {
            val json = Backup.find(this)
            runOnUiThread {
                if (json != null && !isFinishing) Backup.confirmRestore(this, json) { finishSetup() }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Backup.REQ_PERM) offerRestore()
    }

    private fun finishSetup() {
        BasePrefs.setSetupDone(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val p = Ui.dp(this, 56)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LauncherTheme.background)
            setPadding(p, p / 2, p, p / 2)
        }
        fun step(n: Int, done: Boolean, title: String, body: String, btn: String, onClick: () -> Unit) {
            col.addView(Ui.text(this, (if (done) "✓ " else "$n. ") + title, 22f,
                if (done) LauncherTheme.accent else LauncherTheme.text, true).apply {
                setPadding(0, Ui.dp(this@SetupActivity, 18), 0, Ui.dp(this@SetupActivity, 4))
            })
            col.addView(Ui.text(this, body, 16f, LauncherTheme.dimText).apply {
                setPadding(0, 0, 0, Ui.dp(this@SetupActivity, 10))
            })
            col.addView(Ui.button(this, btn, onClick).apply {
                layoutParams = LinearLayout.LayoutParams(-2, -2)
            })
        }
        col.addView(Ui.text(this, "Welcome to ${Brand.name}", LauncherTheme.TITLE_SP + 6, bold = true))
        col.addView(Ui.text(this, "An ad-free home screen with live cameras and a full button remapper.", 17f, LauncherTheme.dimText))
        col.addView(Ui.button(this, "↺  Restore from a backup") { Backup.restoreFlow(this) { finishSetup() } }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(this@SetupActivity, 12) }
        })

        val isHome = HomeHelper.isDefaultHome(this)
        step(1, isHome, "Make it your home screen",
            "So the remote's Home button opens ${Brand.name} instead of Google TV's ad-filled home.",
            if (isHome) "Done" else getString(R.string.make_default)) { HomeHelper.requestHomeRole(this) }

        ServiceSetup.selfEnable(this)
        val guard = HomeGuardService.isEnabled(this)
        step(2, guard, "Turn on the Home guard service (recommended)",
            "Settings → Accessibility → Kara's TV → On. This keeps Google from taking Home back, " +
                "powers the button remapper, and lets cameras float over movies.",
            if (guard) "Done" else getString(R.string.open_accessibility_settings)) {
            if (!ServiceSetup.selfEnable(this)) KeyMapActivity.showServiceFix(this)
        }

        val cams = BasePrefs.cameras(this).size
        step(3, cams > 0, "Add your cameras (optional)",
            "Paste a MotionEye streaming URL, e.g. http://192.168.1.20:8081/",
            if (cams > 0) "$cams added — add another" else "Add a camera") {
            CamerasActivity.editCamera(this, null) { build() }
        }

        val finish = Ui.button(this, "Start using ${Brand.name}  →") {
            BasePrefs.setSetupDone(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(this@SetupActivity, 28) }
            gravity = Gravity.CENTER
        }
        col.addView(finish)
        setContentView(ScrollView(this).apply { setBackgroundColor(LauncherTheme.background); addView(col) })
        (if (!isHome) col.getChildAt(5) else finish).requestFocus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Backup.REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { Backup.readUri(this, it) }?.let { Backup.confirmRestore(this, it) { finishSetup() } }
                ?: Ui.toast(this, "Couldn't open that file")
            return
        }
        build()
    }
}
