package com.moomoo.tvlauncherbase

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Floating camera windows on top of any app (picture-in-picture for CCTV).
 *
 * Host, best first:
 *  1. The Home guard accessibility service → TYPE_ACCESSIBILITY_OVERLAY.
 *     Needs no extra permission, and the service can also steer the
 *     windows with the remote while a movie keeps playing.
 *  2. "Display over other apps" (SYSTEM_ALERT_WINDOW) → TYPE_APPLICATION_OVERLAY.
 *     On Google TV that settings page is often hidden; grant it with
 *     adb shell appops set com.moomoo.tvlauncherbase SYSTEM_ALERT_WINDOW allow
 *
 * Windows never take focus, so the app underneath keeps the remote. They
 * are fully opaque at 100% (no 80% Android cap, see show()). Up to [MAX] windows. Camera audio is always muted.
 *
 * Control mode (remap a button to "Floating cameras: control", or use
 * Settings → Floating windows): OK cycles Move → Resize → Pick window →
 * Change camera; the D-pad does the current job; Back finishes.
 */
object OverlayManager {
    const val MAX = 4

    private class Win(
        var camIndex: Int,
        val slot: Int,
        val frame: FrameLayout,
        var cam: CameraView,
        val lp: WindowManager.LayoutParams,
        val wm: WindowManager,
        val hostCtx: Context,
        /** Height / width of this window — follows the camera's real shape in "snap" mode. */
        var aspect: Float = 9f / 16f
    )

    // ------------------------------------------------------------ picture fit
    /** "snap" = window takes the camera's shape (no bars), "fill" = crop, "stretch", "fit" = bars. */
    val FIT_MODES = listOf(
        "snap" to "Snap window to the camera's shape (no bars, no distortion)",
        "fill" to "Fill the window (crop the edges)",
        "stretch" to "Stretch to fill (may look squashed)",
        "fit" to "Show whole picture (black bars)"
    )

    fun fitLabel(c: Context) = FIT_MODES.firstOrNull { it.first == BasePrefs.overlayFit(c) }?.second ?: FIT_MODES[0].second

    private fun scaleFor(mode: String) = when (mode) {
        "fill" -> CameraView.Scale.FILL
        "stretch" -> CameraView.Scale.STRETCH
        "fit" -> CameraView.Scale.FIT
        else -> CameraView.Scale.FILL // snap: window matches the picture; FILL hides any 1-px rounding gap
    }

    /** Hooks a camera view up to its window: scale mode + snapping to the picture's shape. */
    private fun wire(c: Context, w: Win) {
        val mode = BasePrefs.overlayFit(c)
        w.cam.scale = scaleFor(mode)
        w.cam.setCornerRadius(Ui.dpf(w.hostCtx, CORNER_DP))
        w.cam.onPictureSize = { pw, ph ->
            if (BasePrefs.overlayFit(c) == "snap") {
                w.aspect = ph.toFloat() / pw
                val dm = w.hostCtx.resources.displayMetrics
                val bottom = w.lp.y + w.lp.height
                w.lp.height = (w.lp.width * w.aspect).toInt()
                // Windows in the lower half keep their bottom edge where you put them.
                if (bottom > dm.heightPixels / 2) w.lp.y = bottom - w.lp.height
                clamp(w, dm.widthPixels, dm.heightPixels); update(w)
            }
        }
    }

    fun setFit(c: Context, mode: String) {
        BasePrefs.setOverlayFit(c, mode)
        for (w in wins) {
            wire(c, w)
            if (mode != "snap") {
                w.aspect = 9f / 16f
                w.lp.height = (w.lp.width * w.aspect).toInt()
                val dm = w.hostCtx.resources.displayMetrics
                clamp(w, dm.widthPixels, dm.heightPixels); update(w)
            }
            // Re-report the size so snap mode re-applies immediately.
            swapCamera(w, w.camIndex, BasePrefs.cameras(c).getOrNull(w.camIndex) ?: continue)
        }
    }

    enum class Mode(val label: String) {
        MOVE("Move: ◀ ▲ ▼ ▶"), RESIZE("Resize: ▲/▶ bigger · ▼/◀ smaller"),
        PICK("Pick window: ◀ ▶"), CAMERA("Change camera: ◀ ▶")
    }

    private val wins = mutableListOf<Win>()
    private val main = Handler(Looper.getMainLooper())
    var controlMode = false
        private set
    private var mode = Mode.MOVE
    private var selected = 0
    private var hint: TextView? = null
    private val autoExit = Runnable { exitControl() }

    val count get() = wins.size

    // ------------------------------------------------------------ hosting
    private fun host(c: Context): Pair<Context, Int>? {
        HomeGuardService.instance?.let { return it to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY }
        if (Settings.canDrawOverlays(c))
            return c.applicationContext to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        return null
    }

    fun canShow(c: Context) = HomeGuardService.instance != null || Settings.canDrawOverlays(c)

    fun statusLine(c: Context): String = when {
        HomeGuardService.instance != null -> "Ready (via Home guard service)" +
            if (wins.isNotEmpty()) " · ${wins.size} open" else ""
        Settings.canDrawOverlays(c) -> "Ready (overlay permission)" +
            if (wins.isNotEmpty()) " · ${wins.size} open" else ""
        else -> "Needs the Home guard service ON (or overlay permission)"
    }

    private fun explainNoHost(c: Context) {
        Ui.toast(c, "Floating windows need the Home guard service — opening setup")
        try {
            c.startActivity(Intent(c, OverlaySettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {}
    }

    // ------------------------------------------------------------ show / hide
    fun show(c: Context, camIndex: Int) = openWin(c, camIndex, null, true)

    /** Opens [camIndex] in [wantSlot] (or the first free slot) at that slot's saved spot and size. */
    private fun openWin(c: Context, camIndex: Int, wantSlot: Int?, persist: Boolean): Boolean {
        val cams = BasePrefs.cameras(c)
        if (camIndex !in cams.indices) { Ui.toast(c, "Add a camera first (Settings → Cameras)"); return false }
        wins.indexOfFirst { it.camIndex == camIndex }.takeIf { it >= 0 }?.let { selected = it; highlight(); return false }
        if (wins.size >= MAX) { Ui.toast(c, "Up to $MAX floating windows"); return false }
        val (ctx, type) = host(c) ?: run { explainNoHost(c); return false }
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return false
        val dm = ctx.resources.displayMetrics
        val slot = wantSlot?.takeIf { s -> s in 0 until MAX && wins.none { it.slot == s } }
            ?: (0 until MAX).first { s -> wins.none { it.slot == s } }
        val geom = BasePrefs.overlayGeom(c, slot)
        // New windows start small, lined up from the bottom-right corner.
        val w = geom?.get(2) ?: minWidth(dm.widthPixels)
        val h = w * 9 / 16
        val margin = Ui.dp(c, EDGE_DP)
        val lp = WindowManager.LayoutParams(
            w, h, type,
            // NOT_FOCUSABLE keeps the remote with the app underneath. Deliberately NOT
            // FLAG_NOT_TOUCHABLE: Android 12+ caps such overlay windows at 80% opacity
            // (they'd look see-through over bright video). TVs have no touchscreen, and
            // NOT_TOUCH_MODAL still lets any touches outside the window pass through.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = geom?.get(0) ?: (dm.widthPixels - margin - (slot + 1) * w - slot * Ui.dp(c, GAP_DP))
            y = geom?.get(1) ?: (dm.heightPixels - h - margin)
            alpha = BasePrefs.overlayOpacity(c) / 100f
            title = "Camera overlay $slot"
        }
        if (lp.height > dm.heightPixels) lp.height = dm.heightPixels
        lp.x = lp.x.coerceIn(0, maxOf(0, dm.widthPixels - lp.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, dm.heightPixels - lp.height))
        val frame = FrameLayout(ctx).apply {
            // Border is a foreground so it sits over the video's edge like a window frame.
            foreground = border(ctx, false)
            background = null
            Ui.roundClip(this, Ui.dpf(ctx, CORNER_DP))
        }
        val cam = CameraView(ctx, cams[camIndex], compact = true)
        frame.addView(cam, FrameLayout.LayoutParams(-1, -1))
        try {
            wm.addView(frame, lp)
        } catch (e: Exception) {
            Ui.toast(c, "Couldn't open a floating window: ${e.message}")
            return false
        }
        val win = Win(camIndex, slot, frame, cam, lp, wm, ctx)
        wire(c, win)
        cam.start()
        wins += win
        selected = wins.size - 1
        if (persist) saveSet(c)
        if (controlMode) highlight()
        return true
    }

    /** Remembers which cameras are floating in which slot (their spot and size are saved per slot). */
    private fun saveSet(c: Context) = BasePrefs.setOverlaySet(c, wins.map { it.slot to it.camIndex })

    /**
     * Open / close. Closing only hides the windows — opening again brings back
     * every window that was open, each with its camera, spot and size. The very
     * first time, every camera (up to [MAX]) opens small in the bottom-right corner.
     */
    fun toggle(c: Context) {
        if (wins.isNotEmpty()) { hideAll(); return }
        restore(c)
    }

    private fun restore(c: Context) {
        val cams = BasePrefs.cameras(c)
        if (cams.isEmpty()) { Ui.toast(c, "Add a camera first (Settings → Cameras)"); return }
        val saved = BasePrefs.overlaySet(c).filter { it.second in cams.indices }.distinctBy { it.second }.take(MAX)
        if (saved.isNotEmpty()) {
            for ((slot, cam) in saved) openWin(c, cam, slot, persist = false)
            if (wins.isNotEmpty()) saveSet(c)
            return
        }
        openAll(c)
    }

    /** Every camera (up to MAX), slot i = camera i; any slot without a saved spot gets the bottom-right row. */
    private fun openAll(c: Context) {
        val n = minOf(BasePrefs.cameras(c).size, MAX)
        val fresh = (0 until n).any { BasePrefs.overlayGeom(c, it) == null }
        for (i in 0 until n) openWin(c, i, i, persist = false)
        if (wins.isEmpty()) return
        saveSet(c)
        if (fresh) arrange(c, Layout.BOTTOM_RIGHT)
    }

    /** Adds the next camera that isn't floating yet. */
    fun addNext(c: Context) {
        val cams = BasePrefs.cameras(c)
        val next = cams.indices.firstOrNull { i -> wins.none { it.camIndex == i } }
        if (next == null) { Ui.toast(c, "All cameras are already open"); return }
        show(c, next)
    }

    /** Opens every camera (up to MAX) where you last put them (new ones: bottom-right row). */
    fun showAll(c: Context) {
        hideAll()
        openAll(c)
    }

    fun hideAll() {
        exitControl()
        for (w in wins.toList()) remove(w)
        wins.clear()
    }

    private fun remove(w: Win) {
        w.cam.stop()
        try { w.wm.removeView(w.frame) } catch (e: Exception) {}
    }

    fun closeSelected(c: Context) {
        val w = wins.getOrNull(selected) ?: return
        remove(w); wins.remove(w)
        if (wins.isNotEmpty()) saveSet(c)
        selected = selected.coerceAtMost(wins.size - 1).coerceAtLeast(0)
        if (wins.isEmpty()) exitControl() else highlight()
    }

    /** Called when the accessibility host goes away (its windows die with it). */
    fun onHostGone(host: Context) {
        val dead = wins.filter { it.hostCtx === host }
        dead.forEach { it.cam.stop() }
        wins.removeAll(dead)
        if (wins.isEmpty()) controlMode = false
    }

    fun setOpacity(c: Context, pct: Int) {
        BasePrefs.setOverlayOpacity(c, pct)
        for (w in wins) { w.lp.alpha = pct / 100f; update(w) }
    }

    enum class Layout(val label: String) {
        BOTTOM_RIGHT("Small, in a row in the bottom-right corner"), RIGHT_COLUMN("Stacked down the right side"), CORNERS("One in each corner"),
        BOTTOM_ROW("Row along the bottom"), TOP_RIGHT("All in the top-right corner")
    }

    fun arrange(c: Context, layout: Layout) {
        if (wins.isEmpty()) return
        val dm = wins[0].hostCtx.resources.displayMetrics
        val sw = dm.widthPixels; val sh = dm.heightPixels
        val m = Ui.dp(c, 24)
        if (layout == Layout.BOTTOM_RIGHT) {
            // Smallest size, side by side from the right edge, bottoms lined up.
            val e = Ui.dp(c, EDGE_DP); val g = Ui.dp(c, GAP_DP)
            val ww = minWidth(sw)
            var right = sw - e
            wins.sortedByDescending { it.slot }.forEach { w ->
                w.lp.width = ww; w.lp.height = (ww * w.aspect).toInt()
                w.lp.x = right - ww; w.lp.y = sh - w.lp.height - e
                right -= ww + g
                clamp(w, sw, sh); update(w); save(w)
            }
            return
        }
        wins.forEachIndexed { i, w ->
            val ww = when (layout) {
                Layout.BOTTOM_RIGHT, Layout.BOTTOM_ROW -> ((sw - m * (wins.size + 1)) / wins.size).coerceAtMost((sw * 0.3f).toInt())
                else -> (sw * if (wins.size > 2) 0.24f else 0.28f).toInt()
            }
            val hh = (ww * w.aspect).toInt()
            w.lp.width = ww; w.lp.height = hh
            when (layout) {
                Layout.RIGHT_COLUMN -> { w.lp.x = sw - ww - m; w.lp.y = m + i * (hh + m / 2) }
                Layout.CORNERS -> {
                    w.lp.x = if (i % 2 == 0) sw - ww - m else m
                    w.lp.y = if (i < 2) m else sh - hh - m
                }
                Layout.BOTTOM_RIGHT, Layout.BOTTOM_ROW -> { w.lp.x = m + i * (ww + m); w.lp.y = sh - hh - m }
                Layout.TOP_RIGHT -> { w.lp.x = sw - ww - m - i * m; w.lp.y = m + i * m }
            }
            clamp(w, sw, sh); update(w); save(w)
        }
    }

    // ------------------------------------------------------------ remote control
    fun enterControl(c: Context) {
        if (wins.isEmpty()) toggle(c)
        if (wins.isEmpty()) return
        if (HomeGuardService.instance == null) {
            // No key filter available: a transparent activity grabs the keys instead.
            try {
                c.startActivity(Intent(c, OverlayControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {}
        }
        controlMode = true
        mode = Mode.MOVE
        selected = selected.coerceIn(0, wins.size - 1)
        highlight()
        bumpTimeout()
    }

    fun exitControl() {
        controlMode = false
        main.removeCallbacks(autoExit)
        highlight()
    }

    private fun bumpTimeout() {
        main.removeCallbacks(autoExit)
        main.postDelayed(autoExit, 20_000L)
    }

    /** Key handling while in control mode. Returns true when consumed. */
    fun handleKey(c: Context, e: KeyEvent): Boolean {
        if (!controlMode || wins.isEmpty()) return false
        val code = e.keyCode
        val handled = code in CONTROL_KEYS
        if (!handled) return false
        if (e.action != KeyEvent.ACTION_DOWN) return true
        bumpTimeout()
        val w = wins.getOrNull(selected) ?: return true
        val dm = w.hostCtx.resources.displayMetrics
        val step = (dm.widthPixels * 0.04f).toInt()
        when (code) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { exitControl(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                mode = Mode.values()[(mode.ordinal + 1) % Mode.values().size]; highlight(); return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DEL -> { closeSelected(c); return true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { resize(w, 1.15f); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND -> { resize(w, 1 / 1.15f); return true }
        }
        val dx = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
        val dy = when (code) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
        when (mode) {
            Mode.MOVE -> {
                w.lp.x += dx * step; w.lp.y += dy * step
                clamp(w, dm.widthPixels, dm.heightPixels); update(w); save(w)
            }
            Mode.RESIZE -> resize(w, if (dx > 0 || dy < 0) 1.12f else 1 / 1.12f)
            Mode.PICK -> {
                val d = if (dx != 0) dx else dy
                selected = (selected + d + wins.size) % wins.size; highlight()
            }
            Mode.CAMERA -> {
                val cams = BasePrefs.cameras(c)
                if (cams.isNotEmpty()) {
                    val d = if (dx != 0) dx else dy
                    val ni = (w.camIndex + d + cams.size) % cams.size
                    swapCamera(w, ni, cams[ni])
                }
            }
        }
        return true
    }

    private val CONTROL_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND
    )

    /** Swap the selected window to the next camera (also a remapper action). */
    fun cycleCamera(c: Context) {
        val w = wins.getOrNull(selected) ?: return toggle(c)
        val cams = BasePrefs.cameras(c)
        if (cams.isEmpty()) return
        val ni = (w.camIndex + 1) % cams.size
        swapCamera(w, ni, cams[ni])
    }

    private fun swapCamera(w: Win, index: Int, cam: Camera) {
        w.cam.stop()
        w.frame.removeView(w.cam)
        val v = CameraView(w.hostCtx, cam, compact = true)
        w.frame.addView(v, 0, FrameLayout.LayoutParams(-1, -1))
        w.cam = v; w.camIndex = index
        wire(w.hostCtx, w)
        saveSet(w.hostCtx)
        v.start()
    }

    private fun resize(w: Win, f: Float) {
        val dm = w.hostCtx.resources.displayMetrics
        val nw = (w.lp.width * f).toInt().coerceIn(minWidth(dm.widthPixels), dm.widthPixels)
        w.lp.width = nw; w.lp.height = (nw * w.aspect).toInt()
        clamp(w, dm.widthPixels, dm.heightPixels); update(w); save(w)
    }

    private fun clamp(w: Win, sw: Int, sh: Int) {
        val lp = w.lp
        if (lp.height > sh) { lp.height = sh; lp.width = (sh / w.aspect).toInt() }
        if (lp.width > sw) { lp.width = sw; lp.height = (sw * w.aspect).toInt() }
        lp.x = lp.x.coerceIn(0, maxOf(0, sw - lp.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, sh - lp.height))
    }

    private fun update(w: Win) {
        try { w.wm.updateViewLayout(w.frame, w.lp) } catch (e: Exception) {}
    }

    private fun save(w: Win) = BasePrefs.setOverlayGeom(w.hostCtx, w.slot, w.lp.x, w.lp.y, w.lp.width)

    private val CORNER_DP = 12f
    /** Gap to the screen edge and between windows in the bottom-right row. */
    private const val EDGE_DP = 8
    private const val GAP_DP = 8
    /** Smallest window: 12% of the screen width (also the Resize minimum). */
    private fun minWidth(screenW: Int) = (screenW * 0.12f).toInt()

    private fun border(c: Context, on: Boolean) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = Ui.dpf(c, CORNER_DP)
        setStroke(Ui.dp(c, if (on) 4 else 1), if (on) LauncherTheme.accent else 0x55FFFFFF)
    }

    private fun highlight() {
        hint?.let { h -> (h.parent as? FrameLayout)?.removeView(h) }
        hint = null
        wins.forEachIndexed { i, w ->
            val on = controlMode && i == selected
            w.frame.foreground = border(w.hostCtx, on)
            if (on) {
                val t = TextView(w.hostCtx).apply {
                    text = "${mode.label}\nOK: next mode · Back: done · Menu: close"
                    setTextColor(Color.WHITE); textSize = 11f
                    setBackgroundColor(0xCC000000.toInt())
                    val p = Ui.dp(w.hostCtx, 6); setPadding(p, p / 2, p, p / 2)
                }
                w.frame.addView(t, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
                hint = t
            }
        }
    }
}
