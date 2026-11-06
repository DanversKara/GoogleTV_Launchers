package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.SystemClock
import android.widget.TextView

/** The name shown inside the app (Settings → Name). Loaded with the theme. */
object Brand {
    /** Android's own screens (Accessibility, Home picker) always show this built-in label. */
    const val LABEL = "Kara's TV"
    @Volatile var name = LABEL
}

/**
 * The TV's name ("Kara's TV" by default — Settings → Name) as a glowing
 * title with a slow shimmer sweeping across it in the theme's colors.
 */
class NameBadge(c: Context, sizeSp: Float) : TextView(c) {

    var shimmer = true
    private val m = Matrix()
    private var shader: LinearGradient? = null
    private val start = SystemClock.uptimeMillis()

    init {
        text = BasePrefs.displayName(c)
        textSize = sizeSp
        typeface = LauncherTheme.typeface(true)
        letterSpacing = 0.04f
        setTextColor(LauncherTheme.text)
        setShadowLayer(18f, 0f, 0f, (LauncherTheme.accent and 0xFFFFFF) or 0xAA000000.toInt())
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val base = LauncherTheme.text
        val a = LauncherTheme.accent
        val g2 = LauncherTheme.gradient.getOrElse(0) { a }
        shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(base, base, a, Color.WHITE, g2, base, base),
            floatArrayOf(0f, 0.3f, 0.42f, 0.5f, 0.58f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val s = shader
        if (shimmer && s != null && width > 0) {
            val t = ((SystemClock.uptimeMillis() - start) % 5200L) / 5200f
            m.setTranslate((t * 2.4f - 1.2f) * width, 0f)
            s.setLocalMatrix(m)
            paint.shader = s
            super.onDraw(canvas)
            paint.shader = null
            if (isShown) postInvalidateDelayed(40)
        } else super.onDraw(canvas)
    }
}
