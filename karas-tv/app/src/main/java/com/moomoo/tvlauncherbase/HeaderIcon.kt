package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crisp line icons for the header, drawn in code in the style of Google's
 * Material Symbols (2 px rounded strokes): search, apps, camera,
 * picture-in-picture, edit and settings. They take the theme's text color,
 * so they look right on every theme.
 */
class HeaderIcon(c: Context, val kind: Kind) : View(c) {

    enum class Kind { SEARCH, APPS, CAMERA, PIP, EDIT, SETTINGS }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val r = RectF()

    init { isFocusable = false; isDuplicateParentStateEnabled = true }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        p.color = LauncherTheme.text
        p.strokeWidth = s * 0.085f
        p.style = Paint.Style.STROKE
        when (kind) {
            Kind.SEARCH -> {
                canvas.drawCircle(s * 0.42f, s * 0.42f, s * 0.25f, p)
                canvas.drawLine(s * 0.61f, s * 0.61f, s * 0.84f, s * 0.84f, p)
            }
            Kind.APPS -> {
                p.style = Paint.Style.FILL
                for (row in 0..2) for (col in 0..2) {
                    val cx = s * (0.22f + col * 0.28f); val cy = s * (0.22f + row * 0.28f)
                    canvas.drawCircle(cx, cy, s * 0.075f, p)
                }
            }
            Kind.CAMERA -> {
                r.set(s * 0.1f, s * 0.3f, s * 0.9f, s * 0.82f)
                canvas.drawRoundRect(r, s * 0.1f, s * 0.1f, p)
                path.reset()
                path.moveTo(s * 0.32f, s * 0.3f); path.lineTo(s * 0.38f, s * 0.18f)
                path.lineTo(s * 0.62f, s * 0.18f); path.lineTo(s * 0.68f, s * 0.3f)
                canvas.drawPath(path, p)
                canvas.drawCircle(s * 0.5f, s * 0.56f, s * 0.15f, p)
            }
            Kind.PIP -> {
                r.set(s * 0.1f, s * 0.2f, s * 0.9f, s * 0.8f)
                canvas.drawRoundRect(r, s * 0.08f, s * 0.08f, p)
                p.style = Paint.Style.FILL
                r.set(s * 0.5f, s * 0.48f, s * 0.78f, s * 0.68f)
                canvas.drawRoundRect(r, s * 0.04f, s * 0.04f, p)
            }
            Kind.EDIT -> {
                path.reset()
                path.moveTo(s * 0.2f, s * 0.8f); path.lineTo(s * 0.24f, s * 0.62f)
                path.lineTo(s * 0.66f, s * 0.2f); path.lineTo(s * 0.8f, s * 0.34f)
                path.lineTo(s * 0.38f, s * 0.76f); path.close()
                canvas.drawPath(path, p)
                canvas.drawLine(s * 0.58f, s * 0.28f, s * 0.72f, s * 0.42f, p)
            }
            Kind.SETTINGS -> {
                val cx = s / 2f; val cy = s / 2f
                path.reset()
                val teeth = 8
                val inner = s * 0.3f; val outer = s * 0.41f
                val half = Math.PI / teeth
                var first = true
                for (i in 0 until teeth) {
                    val a = 2 * Math.PI * i / teeth
                    // each tooth: inner → outer → outer (flat top) → inner
                    for ((ang, rad) in listOf(a - half * 0.95 to inner, a - half * 0.45 to outer,
                            a + half * 0.45 to outer, a + half * 0.95 to inner)) {
                        val x = cx + (cos(ang) * rad).toFloat(); val y = cy + (sin(ang) * rad).toFloat()
                        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    }
                }
                path.close()
                canvas.drawPath(path, p)
                canvas.drawCircle(cx, cy, s * 0.13f, p)
            }
        }
        canvas.restore()
    }
}
