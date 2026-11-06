package com.moomoo.tvlauncherbase

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView

/**
 * Full-screen camera wall. Grid of every camera; OK on one goes to that
 * camera full screen, Left/Right switch cameras, Back returns to the grid.
 * Menu = pop the focused camera out as a floating window.
 * Started with extra "cam" = index to open one camera directly.
 */
class CameraWallActivity : Activity() {

    private val views = mutableListOf<CameraView>()
    private var single = -1
    private lateinit var root: FrameLayout
    private var cams = listOf<Camera>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherTheme.load(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        cams = BasePrefs.cameras(this)
        single = intent.getIntExtra("cam", -1).takeIf { it in cams.indices } ?: -1
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onPause() {
        super.onPause()
        views.forEach { it.stop() }
    }

    private fun build() {
        views.forEach { it.stop() }
        views.clear()
        root.removeAllViews()
        if (cams.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No cameras yet.\nAdd one in Settings → Cameras."
                setTextColor(Color.WHITE); textSize = 22f; gravity = android.view.Gravity.CENTER
            })
            return
        }
        if (single >= 0) {
            val v = CameraView(this, cams[single])
            root.addView(v, FrameLayout.LayoutParams(-1, -1))
            views += v; v.start()
            return
        }
        val n = cams.size
        val cols = when { n <= 1 -> 1; n <= 4 -> 2; n <= 9 -> 3; else -> 4 }
        val rows = (n + cols - 1) / cols
        val grid = GridLayout(this).apply { columnCount = cols; rowCount = rows }
        val m = Ui.dp(this, 3)
        cams.forEachIndexed { i, cam ->
            val v = CameraView(this, cam, compact = n > 4).apply {
                scale = if (BasePrefs.camTileFit(this@CameraWallActivity) == "fit") CameraView.Scale.FIT else CameraView.Scale.FILL
                setCornerRadius(Ui.dpf(this@CameraWallActivity, 6f))
                isFocusable = true
                foreground = Ui.focusBg(this@CameraWallActivity, Color.TRANSPARENT, 4)
                setOnClickListener { single = i; build() }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(i / cols, 1f), GridLayout.spec(i % cols, 1f)
            ).apply { width = 0; height = 0; setMargins(m, m, m, m) }
            grid.addView(v, lp)
            views += v
        }
        root.addView(grid, FrameLayout.LayoutParams(-1, -1))
        views.forEach { it.start() }
        views.firstOrNull()?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (single >= 0 && cams.isNotEmpty()) when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                single = (single + 1) % cams.size; build(); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                single = (single - 1 + cams.size) % cams.size; build(); return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (intent.hasExtra("cam")) return super.onKeyDown(keyCode, event)
                single = -1; build(); return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val idx = if (single >= 0) single else views.indexOfFirst { it.hasFocus() || it.isFocused }
            if (idx >= 0) {
                OverlayManager.show(this, idx)
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
