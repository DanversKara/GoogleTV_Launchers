package com.moomoo.tvlauncherbase

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opened by the watchdog notification when another launcher took over.
 * One tap restores TV Launcher Base as the default home app.
 */
class HomeGuardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (HomeHelper.isDefaultHome(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LauncherTheme.background)
            setPadding(64, 64, 64, 64)
        }
        col.addView(TextView(this).apply {
            text = getString(R.string.guard_title)
            setTextColor(LauncherTheme.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = LauncherTheme.TITLE_SP
            setPadding(0, 0, 0, 24)
        })
        col.addView(TextView(this).apply {
            text = getString(R.string.taken_over) + "\n\n" +
                "For a permanent fix that survives updates and reboots, " +
                "use the device-owner step under \"Permanent fix\"."
            setTextColor(LauncherTheme.text)
            textSize = LauncherTheme.BODY_SP
            setPadding(0, 0, 0, 32)
        })
        col.addView(Button(this).apply {
            text = getString(R.string.make_default)
            textSize = LauncherTheme.BODY_SP
            setOnClickListener { HomeHelper.requestHomeRole(this@HomeGuardActivity) }
        })
        col.addView(Button(this).apply {
            text = getString(R.string.lockdown_title)
            textSize = LauncherTheme.BODY_SP
            setOnClickListener {
                startActivity(Intent(this@HomeGuardActivity, LockdownActivity::class.java))
            }
        })
        setContentView(col)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HomeHelper.REQ_HOME_ROLE && HomeHelper.isDefaultHome(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
