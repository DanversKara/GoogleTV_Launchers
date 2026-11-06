package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.moomoo.tvlauncherbase.Weather.Kind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated weather icon, drawn in code: spinning sun, glowing moon with
 * twinkling stars, drifting clouds, falling rain / drizzle / snow, lightning
 * flashes, sliding fog, and wind streaks when it's windy. ~30 fps while on
 * screen, nothing when hidden.
 */
class WeatherIcon(c: Context) : View(c) {

    var kind: Kind = Kind.SUN
        set(v) { field = v; invalidate() }
    var windy = false
    var animate = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val start = SystemClock.uptimeMillis()
    private var lastFrame = 0L

    private val sunColor = Color.parseColor("#FFC93C")
    private val sunGlow = Color.parseColor("#55FFC93C")
    private val moonColor = Color.parseColor("#F4F1DE")
    private val rainColor = Color.parseColor("#6FB7FF")
    private val boltColor = Color.parseColor("#FFE066")
    private val cloudColor get() = if (LauncherTheme.light) Color.parseColor("#9AA6B8") else Color.parseColor("#E8EEF7")
    private val darkCloud get() = if (LauncherTheme.light) Color.parseColor("#6B7686") else Color.parseColor("#9AA3B2")

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0) return
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        when (kind) {
            Kind.SUN -> sun(canvas, s * 0.5f, s * 0.5f, s * 0.22f, t)
            Kind.MOON -> moon(canvas, s, t)
            Kind.PARTLY_DAY -> { sun(canvas, s * 0.36f, s * 0.36f, s * 0.16f, t); cloud(canvas, s, t, s * 0.12f, cloudColor) }
            Kind.PARTLY_NIGHT -> { moonAt(canvas, s * 0.36f, s * 0.34f, s * 0.15f); cloud(canvas, s, t, s * 0.12f, cloudColor) }
            Kind.CLOUDS -> { cloud(canvas, s * 0.8f, t * 0.7f, -s * 0.12f, darkCloud, dx = s * 0.08f); cloud(canvas, s, t, s * 0.05f, cloudColor) }
            Kind.FOG -> fog(canvas, s, t)
            Kind.DRIZZLE -> { drops(canvas, s, t, 3, 0.6f, rainColor, snow = false); cloud(canvas, s, t, -s * 0.06f, cloudColor) }
            Kind.RAIN -> { drops(canvas, s, t, 5, 1f, rainColor, snow = false); cloud(canvas, s, t, -s * 0.06f, darkCloud) }
            Kind.SNOW -> { drops(canvas, s, t, 5, 0.45f, Color.WHITE, snow = true); cloud(canvas, s, t, -s * 0.06f, cloudColor) }
            Kind.STORM -> { drops(canvas, s, t, 4, 1.1f, rainColor, snow = false); bolt(canvas, s, t); cloud(canvas, s, t, -s * 0.06f, darkCloud) }
        }
        if (windy) wind(canvas, s, t)
        canvas.restore()
        if (animate && isShown) {
            val now = SystemClock.uptimeMillis()
            val wait = (33 - (now - lastFrame)).coerceAtLeast(0)
            lastFrame = now
            postInvalidateDelayed(wait)
        }
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        p.style = Paint.Style.FILL
        p.color = sunGlow
        c.drawCircle(cx, cy, r * (1.35f + 0.08f * sin(t * 2f)), p)
        p.color = sunColor
        p.strokeWidth = r * 0.22f
        p.strokeCap = Paint.Cap.ROUND
        val rot = t * 30f * PI.toFloat() / 180f
        for (i in 0 until 8) {
            val a = rot + i * PI.toFloat() / 4f
            c.drawLine(cx + cos(a) * r * 1.45f, cy + sin(a) * r * 1.45f, cx + cos(a) * r * 1.9f, cy + sin(a) * r * 1.9f, p)
        }
        c.drawCircle(cx, cy, r, p)
    }

    private fun moonAt(c: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW) }
        path.op(cut, Path.Op.DIFFERENCE)
        p.style = Paint.Style.FILL
        p.color = moonColor
        c.drawPath(path, p)
    }

    private fun moon(c: Canvas, s: Float, t: Float) {
        p.color = Color.argb(60, 244, 241, 222)
        c.drawCircle(s * 0.46f, s * 0.52f, s * 0.3f, p)
        moonAt(c, s * 0.46f, s * 0.52f, s * 0.24f)
        // twinkling stars
        val stars = listOf(0.78f to 0.22f, 0.86f to 0.48f, 0.68f to 0.12f)
        stars.forEachIndexed { i, (x, y) ->
            val a = (0.5f + 0.5f * sin(t * 2.2f + i * 2f)).coerceIn(0f, 1f)
            p.color = Color.argb((80 + 175 * a).toInt(), 255, 255, 255)
            c.drawCircle(s * x, s * y, s * (0.018f + 0.012f * a), p)
        }
    }

    /** Fluffy cloud; drifts gently left/right. [dy] shifts it up/down. */
    private fun cloud(c: Canvas, s: Float, t: Float, dy: Float, color: Int, dx: Float = 0f) {
        val drift = sin(t * 0.8f) * s * 0.035f + dx
        val cx = s * 0.55f + drift
        val cy = s * 0.6f + dy
        path.reset()
        path.addCircle(cx - s * 0.17f, cy, s * 0.13f, Path.Direction.CW)
        path.addCircle(cx, cy - s * 0.08f, s * 0.18f, Path.Direction.CW)
        path.addCircle(cx + s * 0.17f, cy, s * 0.12f, Path.Direction.CW)
        path.addRoundRect(RectF(cx - s * 0.3f, cy - s * 0.02f, cx + s * 0.29f, cy + s * 0.13f), s * 0.07f, s * 0.07f, Path.Direction.CW)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }

    private fun drops(c: Canvas, s: Float, t: Float, n: Int, speed: Float, color: Int, snow: Boolean) {
        p.color = color
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.035f
        val top = s * 0.62f; val bottom = s * 0.98f
        for (i in 0 until n) {
            val phase = ((t * speed + i * 0.37f) % 1f)
            val x = s * (0.32f + i * (0.42f / maxOf(1, n - 1))) + if (snow) sin(t * 3f + i) * s * 0.03f else -phase * s * 0.05f
            val y = top + (bottom - top) * phase
            p.alpha = ((1f - phase) * 255).toInt().coerceIn(0, 255)
            if (snow) { p.style = Paint.Style.FILL; c.drawCircle(x, y, s * 0.03f, p) }
            else { p.style = Paint.Style.STROKE; c.drawLine(x, y, x - s * 0.025f, y + s * 0.08f, p) }
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    private fun bolt(c: Canvas, s: Float, t: Float) {
        val cycle = t % 2.6f
        if (cycle > 0.35f) return
        val flicker = if (cycle < 0.12f || cycle in 0.2f..0.3f) 255 else 0
        if (flicker == 0) return
        path.reset()
        path.moveTo(s * 0.56f, s * 0.58f)
        path.lineTo(s * 0.44f, s * 0.78f)
        path.lineTo(s * 0.53f, s * 0.78f)
        path.lineTo(s * 0.46f, s * 0.97f)
        path.lineTo(s * 0.64f, s * 0.72f)
        path.lineTo(s * 0.55f, s * 0.72f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = boltColor
        c.drawPath(path, p)
    }

    private fun fog(c: Canvas, s: Float, t: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.06f
        p.color = cloudColor
        for (i in 0 until 4) {
            val y = s * (0.3f + i * 0.14f)
            val off = sin(t * 0.9f + i * 1.3f) * s * 0.08f
            p.alpha = 200 - i * 30
            c.drawLine(s * 0.18f + off, y, s * 0.82f + off - (i % 2) * s * 0.12f, y, p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    private fun wind(c: Canvas, s: Float, t: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.03f
        p.color = if (LauncherTheme.light) Color.parseColor("#7A8597") else Color.parseColor("#CFE3FF")
        for (i in 0 until 2) {
            val phase = ((t * 0.9f + i * 0.5f) % 1f)
            val x = -s * 0.3f + phase * s * 1.4f
            val y = s * (0.25f + i * 0.5f)
            p.alpha = (sin(phase * PI).toFloat() * 220).toInt().coerceIn(0, 255)
            c.drawLine(x, y, x + s * 0.32f, y, p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }
}
