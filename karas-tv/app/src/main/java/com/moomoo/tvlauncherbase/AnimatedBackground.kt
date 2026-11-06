package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live animated background layer behind the home screen (above the
 * wallpaper, below the content): aurora, floating particles, starfield,
 * rolling waves, bokeh lights, or a synthwave grid. Drawn in code at
 * ~30 fps, only while the home screen is visible.
 */
class AnimatedBackground(c: Context) : View(c) {

    var effect = "none"
        set(v) { field = v; seeded = false; invalidate() }
    var running = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val start = SystemClock.uptimeMillis()
    private var seeded = false
    private val rnd = Random(7)

    // per-element state (x, y in 0..1, size, speed, phase)
    private var xs = FloatArray(0); private var ys = FloatArray(0)
    private var ss = FloatArray(0); private var vs = FloatArray(0); private var ph = FloatArray(0)
    private var blobShaders = arrayOf<Shader>()
    private var blobColors = intArrayOf()

    private fun seed(n: Int) {
        xs = FloatArray(n) { rnd.nextFloat() }; ys = FloatArray(n) { rnd.nextFloat() }
        ss = FloatArray(n) { rnd.nextFloat() }; vs = FloatArray(n) { 0.3f + rnd.nextFloat() * 0.7f }
        ph = FloatArray(n) { rnd.nextFloat() * 6.28f }
    }

    private fun palette(): IntArray {
        val g = LauncherTheme.gradient
        val a = LauncherTheme.accent
        return intArrayOf(a, g.getOrElse(0) { a }, if (g.size > 1) g[g.size / 2] else Color.CYAN, Color.parseColor("#7B61FF"))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { seeded = false }

    override fun onDraw(canvas: Canvas) {
        if (effect == "none" || width == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        if (!seeded) {
            seeded = true
            when (effect) {
                "particles" -> seed(46)
                "starfield" -> seed(150)
                "bokeh" -> seed(16)
                else -> seed(8)
            }
            blobColors = palette()
            val r = maxOf(w, h) * 0.45f
            blobShaders = Array(4) { i ->
                val col = blobColors[i % blobColors.size]
                RadialGradient(0f, 0f, r, (col and 0xFFFFFF) or 0x66000000, col and 0xFFFFFF, Shader.TileMode.CLAMP)
            }
        }
        when (effect) {
            "aurora" -> aurora(canvas, w, h, t)
            "particles" -> particles(canvas, w, h, t)
            "starfield" -> starfield(canvas, w, h, t)
            "waves" -> waves(canvas, w, h, t)
            "bokeh" -> bokeh(canvas, w, h, t)
            "grid" -> grid(canvas, w, h, t)
        }
        if (running && isShown) postInvalidateDelayed(33)
    }

    private fun aurora(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in blobShaders.indices) {
            val x = w * (0.5f + 0.42f * sin(t * 0.07f * (i + 1) + i * 1.7f))
            val y = h * (0.35f + 0.3f * cos(t * 0.05f * (i + 2) + i))
            p.shader = blobShaders[i]
            c.save(); c.translate(x, y)
            c.scale(1.6f, 0.75f + 0.15f * sin(t * 0.3f + i))
            c.drawCircle(0f, 0f, maxOf(w, h) * 0.45f, p)
            c.restore()
        }
        p.shader = null
        // light curtain ribbons
        p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.05f
        for (i in 0 until 3) {
            path.reset()
            val base = h * (0.18f + i * 0.08f)
            path.moveTo(0f, base)
            var x = 0f
            while (x <= w) {
                path.lineTo(x, base + sin(x / w * 6f + t * 0.4f + i) * h * 0.04f)
                x += w / 40f
            }
            p.color = (blobColors[(i + 1) % blobColors.size] and 0xFFFFFF) or 0x1E000000
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun particles(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in xs.indices) {
            val depth = 0.3f + ss[i] * 0.7f
            val y = ((ys[i] - t * 0.02f * vs[i] * depth) % 1f + 1f) % 1f
            val x = xs[i] + sin(t * 0.5f * vs[i] + ph[i]) * 0.015f
            val col = if (i % 3 == 0) LauncherTheme.accent else Color.WHITE
            p.color = (col and 0xFFFFFF) or ((40 + 150 * depth).toInt() shl 24)
            c.drawCircle(x * w, y * h, (1.5f + 3.5f * depth) * resources.displayMetrics.density, p)
        }
    }

    private fun starfield(c: Canvas, w: Float, h: Float, t: Float) {
        val d = resources.displayMetrics.density
        for (i in xs.indices) {
            val depth = ss[i]
            val x = ((xs[i] - t * 0.004f * (0.2f + depth)) % 1f + 1f) % 1f
            val tw = 0.55f + 0.45f * sin(t * (1.5f + vs[i] * 2f) + ph[i])
            p.color = Color.argb((60 + 195 * tw * (0.4f + depth * 0.6f)).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle(x * w, ys[i] * h, (0.6f + 1.8f * depth) * d, p)
        }
        // a shooting star every ~9 s
        val k = t % 9f
        if (k < 0.9f) {
            val sx = w * (0.15f + 0.1f * ((t / 9f).toInt() % 5)) + k * w * 0.5f
            val sy = h * 0.12f + k * h * 0.25f
            p.shader = LinearGradient(sx, sy, sx - w * 0.12f, sy - h * 0.06f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            p.strokeWidth = 2.5f * d; p.style = Paint.Style.STROKE
            c.drawLine(sx, sy, sx - w * 0.12f, sy - h * 0.06f, p)
            p.shader = null; p.style = Paint.Style.FILL
        }
    }

    private fun waves(c: Canvas, w: Float, h: Float, t: Float) {
        val cols = palette()
        for (i in 0 until 3) {
            path.reset()
            val base = h * (0.72f + i * 0.08f)
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 1) {
                val y = base + sin(x / w * (3f + i) * PI.toFloat() + t * (0.5f + i * 0.2f)) * h * (0.035f - i * 0.006f)
                path.lineTo(x, y)
                x += w / 60f
            }
            path.lineTo(w, h); path.close()
            p.color = (cols[i % cols.size] and 0xFFFFFF) or ((0x30 + i * 0x14) shl 24)
            c.drawPath(path, p)
        }
    }

    private fun bokeh(c: Canvas, w: Float, h: Float, t: Float) {
        val cols = palette()
        for (i in xs.indices) {
            val x = ((xs[i] + t * 0.006f * vs[i]) % 1.2f) - 0.1f
            val y = ys[i] + sin(t * 0.2f * vs[i] + ph[i]) * 0.04f
            val r = (0.03f + ss[i] * 0.07f) * maxOf(w, h)
            val col = cols[i % cols.size]
            val a = (0.18f + 0.12f * sin(t * 0.8f + ph[i])).coerceIn(0.05f, 0.4f)
            p.shader = RadialGradient(x * w, y * h, r, (col and 0xFFFFFF) or ((a * 255).toInt() shl 24),
                col and 0xFFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(x * w, y * h, r, p)
        }
        p.shader = null
    }

    private fun grid(c: Canvas, w: Float, h: Float, t: Float) {
        val horizon = h * 0.58f
        val a = LauncherTheme.accent
        // sun with stripes
        val sunR = h * 0.2f
        p.shader = LinearGradient(0f, horizon - sunR * 2, 0f, horizon, Color.parseColor("#FFD86B"), a, Shader.TileMode.CLAMP)
        c.save()
        c.clipRect(0f, 0f, w, horizon)
        c.drawCircle(w * 0.5f, horizon - sunR * 0.35f, sunR, p)
        p.shader = null
        p.color = LauncherTheme.background
        for (k in 0 until 5) {
            val y = horizon - sunR * 0.35f + sunR * (0.05f + k * 0.17f)
            c.drawRect(0f, y, w, y + sunR * (0.025f + k * 0.012f), p)
        }
        c.restore()
        // floor
        p.color = (LauncherTheme.background and 0xFFFFFF) or 0xCC000000.toInt()
        c.drawRect(0f, horizon, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * resources.displayMetrics.density
        p.color = (a and 0xFFFFFF) or 0x99000000.toInt()
        val cx = w / 2f
        for (k in -12..12) c.drawLine(cx + k * w * 0.02f, horizon, cx + k * w * 0.16f, h, p)
        val move = (t * 0.35f) % 1f
        for (k in 0 until 10) {
            val f = ((k + move) / 10f)
            val y = horizon + (h - horizon) * f * f
            p.alpha = (60 + 160 * f).toInt().coerceIn(0, 255)
            c.drawLine(0f, y, w, y, p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }
}
