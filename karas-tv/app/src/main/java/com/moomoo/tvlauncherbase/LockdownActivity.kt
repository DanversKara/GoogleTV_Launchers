package com.moomoo.tvlauncherbase

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * PERMANENT FIX (device owner). As device owner, TV Launcher Base pins
 * itself as the persistent HOME handler and can hide the stock launcher
 * packages, re-asserted at every boot.
 *
 * Honest note, shown on screen too: dpm set-device-owner usually fails
 * once a Google account is on the device, so this is best done before
 * setup completes (or after a factory reset, before signing in).
 */
class LockdownActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LauncherTheme.background)
            setPadding(64, 64, 64, 64)
        }
        val isOwner = DeviceOwnerHelper.isDeviceOwner(this)
        col.addView(TextView(this).apply {
            text = getString(R.string.lockdown_title)
            setTextColor(LauncherTheme.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = LauncherTheme.TITLE_SP
            setPadding(0, 0, 0, 24)
        })
        col.addView(TextView(this).apply {
            text = getString(R.string.lockdown_desc) + "\n\n" +
                "Status: " + if (isOwner) "DEVICE OWNER — active" else "not a device owner" + "\n\n" +
                "ADB command (run from a computer with the TV connected):\n" +
                DeviceOwnerHelper.deviceOwnerCommand(this@LockdownActivity) + "\n\n" +
                "Undo: turn off the Home guard service, re-enable the stock " +
                "launcher package (see README), or use \"Release device owner\" below."
            setTextColor(LauncherTheme.text)
            textSize = LauncherTheme.BODY_SP
            setPadding(0, 0, 0, 32)
        })
        if (isOwner) {
            col.addView(Button(this).apply {
                text = "Re-assert persistent HOME now"
                textSize = LauncherTheme.BODY_SP
                setOnClickListener {
                    val ok = DeviceOwnerHelper.assertHomeOwnership(this@LockdownActivity)
                    Toast.makeText(
                        this@LockdownActivity,
                        if (ok) "Persistent HOME pinned" else "Failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            col.addView(Button(this).apply {
                text = if (BasePrefs.hideGoogleLauncher(this@LockdownActivity))
                    "Stop hiding the stock launcher" else "Hide the stock launcher"
                textSize = LauncherTheme.BODY_SP
                setOnClickListener {
                    val next = !BasePrefs.hideGoogleLauncher(this@LockdownActivity)
                    BasePrefs.setHideGoogleLauncher(this@LockdownActivity, next)
                    DeviceOwnerHelper.setGoogleLauncherHidden(this@LockdownActivity, next)
                    recreate()
                }
            })
            col.addView(Button(this).apply {
                text = "Release device owner"
                textSize = LauncherTheme.BODY_SP
                setOnClickListener {
                    AlertDialog.Builder(this@LockdownActivity)
                        .setTitle("Release device owner?")
                        .setMessage("This unhides the stock launcher and gives up the persistent HOME pin. The normal default-home choice still applies.")
                        .setPositiveButton(getString(R.string.ok)) { _, _ ->
                            DeviceOwnerHelper.release(this@LockdownActivity)
                            recreate()
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            })
        } else {
            col.addView(TextView(this).apply {
                text = "Not a device owner, so the buttons above are unavailable. " +
                    "If the ADB command fails with \"already provisioned\", the TV " +
                    "already has an account on it — that is the expected failure, " +
                    "and the Home guard + watchdog layers are the fallback."
                setTextColor(LauncherTheme.dimText)
                textSize = LauncherTheme.BODY_SP
            })
        }
        setContentView(col)
    }
}
