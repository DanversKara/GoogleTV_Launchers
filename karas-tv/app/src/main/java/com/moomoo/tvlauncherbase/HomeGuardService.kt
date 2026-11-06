package com.moomoo.tvlauncherbase

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * The Home guard + button remapper + floating-camera host.
 *
 * 1. Window watch: when a stock launcher (Google TV launcherx, the older
 *    Android TV launcher, or extra packages the user lists) comes to the
 *    foreground, TV Launcher is brought straight back.
 * 2. Button remapper: every key that reaches accessibility goes through
 *    [KeyEngine] (single / long / double press). Mappings work in every
 *    app including this launcher's home screen; they pause only while the
 *    Remote buttons editor is open, so a bad mapping can always be fixed.
 * 3. Floating cameras: hosts TYPE_ACCESSIBILITY_OVERLAY windows and feeds
 *    the D-pad to them in control mode.
 * 4. Remembers the previous app ("Switch to previous app") and runs the
 *    sleep timer.
 *
 * The user must enable this service once in system Settings → Accessibility.
 */
class HomeGuardService : AccessibilityService() {

    companion object {
        val STOCK_LAUNCHERS = listOf(
            "com.google.android.apps.tv.launcherx", // Google TV (Onn, Chromecast, ...)
            "com.google.android.tvlauncher",        // older Android TV
            "com.google.android.leanbacklauncher"   // very old Android TV
        )

        /** Packages that are never "the app you were in" (system chrome). */
        private val NOT_APPS = setOf(
            "com.android.systemui", "android", "com.google.android.tvrecommendations",
            "com.google.android.katniss", "com.google.android.apps.tv.dreamx",
            "com.android.tv.settings", "com.google.android.inputmethod.latin",
            "com.google.android.leanback.ime"
        )

        @Volatile var instance: HomeGuardService? = null
            private set

        /** Set by the key editor's "Learn a button"; receives the next key code. */
        @Volatile var learnListener: ((Int) -> Unit)? = null

        /** When the service last connected / last saw a key (for diagnostics). */
        @Volatile var connectedAt = 0L
        @Volatile var lastKeySeenAt = 0L

        private val appHistory = ArrayDeque<String>()

        fun previousApp(): String? = synchronized(appHistory) { appHistory.getOrNull(1) }

        // -------- sleep timer
        private val timerHandler = Handler(Looper.getMainLooper())
        private var sleepAt = 0L
        private val sleepRunnable = Runnable {
            sleepAt = 0L
            val s = instance
            if (s != null && Build.VERSION.SDK_INT >= 28) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Minutes until the sleep timer fires, or 0 when it's off. */
        fun sleepMinutesLeft(): Int = if (sleepAt == 0L) 0
            else (((sleepAt - SystemClock.elapsedRealtime()) / 60_000L) + 1).toInt().coerceAtLeast(0)

        fun cycleSleepTimer(c: Context) {
            val steps = listOf(30, 60, 90, 120, 0)
            val remaining = if (sleepAt == 0L) 0 else ((sleepAt - SystemClock.elapsedRealtime()) / 60_000L).toInt()
            val next = steps.firstOrNull { it > remaining + 1 } ?: 0
            timerHandler.removeCallbacks(sleepRunnable)
            if (next == 0) {
                sleepAt = 0L
                Ui.toast(c, "Sleep timer off")
            } else {
                sleepAt = SystemClock.elapsedRealtime() + next * 60_000L
                timerHandler.postDelayed(sleepRunnable, next * 60_000L)
                Ui.toast(c, "TV will sleep in $next minutes" +
                    if (instance == null) " (needs the Home guard service)" else "")
            }
        }

        fun isEnabled(c: Context): Boolean {
            val enabled = Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = "${c.packageName}/${HomeGuardService::class.java.name}"
            val meShort = "${c.packageName}/.HomeGuardService"
            return enabled.split(":").any { it.equals(me, true) || it.equals(meShort, true) }
        }

        fun isStockLauncher(pkg: String?, c: Context): Boolean {
            if (pkg == null) return false
            if (pkg in STOCK_LAUNCHERS) return true
            return pkg in BasePrefs.extraGuardPackages(c)
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var lastBounceAt = 0L
    private var foreground: String? = null

    // ---------------------------------------------------------------- lifecycle
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectedAt = SystemClock.elapsedRealtime()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanup(); return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup(); super.onDestroy()
    }

    private fun cleanup() {
        if (instance === this) instance = null
        OverlayManager.onHostGone(this)
        engine.reset()
        h.removeCallbacksAndMessages(null)
    }

    override fun onInterrupt() {}

    // ---------------------------------------------------------------- window watch
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore toasts, dialogs from systemui, IMEs, our own overlays.
        if (pkg in NOT_APPS) return
        foreground = pkg
        if (pkg != packageName && !isStockLauncher(pkg, this)) {
            synchronized(appHistory) {
                if (appHistory.firstOrNull() != pkg) {
                    appHistory.remove(pkg); appHistory.addFirst(pkg)
                    while (appHistory.size > 6) appHistory.removeLast()
                }
            }
        }
        if (!BasePrefs.guardEnabled(this)) return
        if (pkg == packageName || !isStockLauncher(pkg, this)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBounceAt < 1500L) return // never loop between launchers
        lastBounceAt = now
        HomeHelper.bringHome(this)
    }

    // ---------------------------------------------------------------- key filter
    private val engine by lazy { KeyEngine(this, "service") }
    private var learnSwallowUp = -1

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && !KeyTestActivity.active)
            lastKeySeenAt = SystemClock.elapsedRealtime()

        // 1) "Learn a button" in the key editor.
        learnListener?.let { cb ->
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                learnListener = null
                learnSwallowUp = code
                h.post { cb(code) }
            }
            return true
        }
        if (code == learnSwallowUp && e.action == KeyEvent.ACTION_UP) { learnSwallowUp = -1; return true }

        // 2) Floating camera control mode.
        if (OverlayManager.handleKey(this, e)) return true

        // 3) Remapper (single / long / double press).
        return engine.onKey(e)
    }
}
