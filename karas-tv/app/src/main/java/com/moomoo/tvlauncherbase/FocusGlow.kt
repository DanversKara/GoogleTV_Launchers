package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/**
 * The hover glow: one overlay that draws a soft neon halo around whatever
 * is focused, GLIDES smoothly from tile to tile as you move, and gently
 * pulses. Its color is the theme accent, or each app's own color (red for
 * Netflix, orange for Crunchyroll…) taken from the app's banner/icon.
 */
class FocusGlow(c: Context) : View(c) {

    var mode = "accent"          // accent | app | off
    var pulse = true
    var cornerPx = 24f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cur = RectF()
    private val target = RectF()
    private var curColor = Color.WHITE
    private var hasRect = false
    private val tmp = Rect()
    private val mine = Rect()
    private val start = SystemClock.uptimeMillis()

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun focusedTile(): View? {
        val f = rootView?.findFocus() ?: return null
        return if (f.tag is String) f else null
    }

    private fun colorFor(v: View): Int {
        if (mode == "app") {
            val tag = v.tag as? String ?: ""
            val pkg = when {
                tag.startsWith("app:") || tag.startsWith("fav:") -> tag.substringAfter(':')
                else -> null
            }
            if (pkg != null) AppColor.of(context, pkg)?.let { return it }
        }
        return LauncherTheme.accent
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == "off") return
        val v = focusedTile()
        if (v == null || !v.isShown || !v.getGlobalVisibleRect(tmp)) {
            hasRect = false
            postInvalidateDelayed(120)
            return
        }
        // Both rects are window-relative: shift the tile's into our own coordinates.
        getGlobalVisibleRect(mine)
        target.set((tmp.left - mine.left).toFloat(), (tmp.top - mine.top).toFloat(),
            (tmp.right - mine.left).toFloat(), (tmp.bottom - mine.top).toFloat())
        val tc = colorFor(v)
        if (!hasRect) { cur.set(target); curColor = tc; hasRect = true }
        else {
            val k = 0.32f // glide speed
            cur.left += (target.left - cur.left) * k; cur.top += (target.top - cur.top) * k
            cur.right += (target.right - cur.right) * k; cur.bottom += (target.bottom - cur.bottom) * k
            curColor = blend(curColor, tc, 0.25f)
        }
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        val pulseK = if (pulse) 0.78f + 0.22f * sin(t * 3.2f) else 1f
        val d = resources.displayMetrics.density
        val rgb = curColor and 0xFFFFFF
        // soft outer halo: concentric strokes fading out
        val layers = 9
        for (i in layers downTo 1) {
            val spread = i * 2.2f * d
            val a = (110f * pulseK * (1f - i / (layers + 1f)) * (1f - i / (layers + 1f))).toInt().coerceIn(0, 255)
            p.strokeWidth = 2.6f * d
            p.color = (a shl 24) or rgb
            val r = RectF(cur.left - spread, cur.top - spread, cur.right + spread, cur.bottom + spread)
            canvas.drawRoundRect(r, cornerPx + spread, cornerPx + spread, p)
        }
        // bright rim
        p.strokeWidth = 2.4f * d
        p.color = ((200 * pulseK).toInt().coerceIn(0, 255) shl 24) or rgb
        canvas.drawRoundRect(cur, cornerPx, cornerPx, p)
        postInvalidateDelayed(if (pulse || cur != target) 33 else 120)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }
}

/** Each app's signature color, taken from its TV banner or icon (cached). */
object AppColor {
    private val cache = HashMap<String, Int?>()

    fun of(c: Context, pkg: String): Int? {
        if (cache.containsKey(pkg)) return cache[pkg]
        val app = AppRepository.find(c, pkg)
        val d = app?.let { AppRepository.banner(c, it) ?: AppRepository.icon(c, it) }
        val col = d?.let {
            try {
                val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                it.setBounds(0, 0, 24, 24); it.draw(cv)
                pick(bmp)
            } catch (e: Exception) { null }
        }
        cache[pkg] = col
        return col
    }

    /** Average of the most colorful pixels, brightened so it glows. */
    private fun pick(b: Bitmap): Int? {
        val hsv = FloatArray(3)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val px = b.getPixel(x, y)
            if (Color.alpha(px) < 128) continue
            Color.colorToHSV(px, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
            r += Color.red(px); g += Color.green(px); bl += Color.blue(px); n++
        }
        if (n < 8) return null
        val avg = Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        Color.colorToHSV(avg, hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceAtLeast(0.9f)
        return Color.HSVToColor(hsv)
    }
}
