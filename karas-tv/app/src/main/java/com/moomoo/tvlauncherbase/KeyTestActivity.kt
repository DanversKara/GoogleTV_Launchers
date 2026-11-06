package com.moomoo.tvlauncherbase

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BUTTON TESTER. Press buttons and see, live:
 *  - whether the remapper service receives them (source "service"),
 *  - or only this screen does (source "app" — the service is off),
 *  - or nothing shows at all (the TV handles that button before any app).
 * Nothing runs while testing — it only reports what each press is mapped to.
 * Hold Back for 2 seconds (or press it twice) to leave.
 */
class KeyTestActivity : Activity() {

    companion object {
        @Volatile var active = false
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var log: TextView
    private var seen = -1L
    private var lastBack = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (KeyLog.version != seen) render()
            h.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherTheme.load(this)
        val p = Ui.dp(this, 40)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LauncherTheme.background)
            setPadding(p, p / 2, p, p / 2)
        }
        col.addView(Ui.text(this, "Button tester", LauncherTheme.TITLE_SP, bold = true))
        status = Ui.text(this, "", 16f, LauncherTheme.dimText).apply { setPadding(0, Ui.dp(this@KeyTestActivity, 6), 0, Ui.dp(this@KeyTestActivity, 12)) }
        col.addView(status)
        log = Ui.text(this, "", 18f).apply { typeface = Typeface.MONOSPACE }
        col.addView(ScrollView(this).apply { addView(log) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        active = true
        KeyLog.clear()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        active = false
        h.removeCallbacks(tick)
    }

    private fun render() {
        seen = KeyLog.version
        val svc = HomeGuardService.instance != null
        status.text = (if (svc) "Remapper service: RUNNING — buttons marked [service] can be remapped in every app."
            else "Remapper service: NOT RUNNING (${ServiceSetup.a11yStatus(this)}). Only the home screen can use mappings until it's on.") +
            "\nPress buttons on the remote. Nothing runs while testing. Press Back twice to leave." +
            "\nA button that never shows up here is handled by the TV itself and can't be remapped without root."
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val entries = KeyLog.list()
        log.text = if (entries.isEmpty()) "Waiting for a button…" else entries.joinToString("\n") {
            "${fmt.format(Date(it.at))}  [${it.source}]  ${RemoteKeys.keyName(it.code)}  →  ${it.what}"
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        // Keys the service already logged arrive here too; only log the ones it didn't see.
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            if (HomeGuardService.instance == null) {
                val mapped = RemoteKeys.isMapped(this, e.keyCode)
                KeyLog.add(e.keyCode, "app", if (mapped) RemoteKeys.summary(this, e.keyCode) else "not mapped")
            }
            if (e.keyCode == KeyEvent.KEYCODE_BACK) {
                val now = System.currentTimeMillis()
                if (now - lastBack < 1500) { finish(); return true }
                lastBack = now
                return true
            }
        }
        if (e.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(e)
    }
}
