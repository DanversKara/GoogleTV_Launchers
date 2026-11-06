package com.moomoo.tvlauncherbase

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.util.Calendar

/**
 * The home screen. Two looks (Customize home → Style):
 *  - Google TV style: full-screen wallpaper taken from what you're watching,
 *    a featured banner that follows the focused show, then rows.
 *  - Classic: the same rows on a plain background.
 *
 * Rows (order / visibility in Customize home): Continue watching, Upcoming
 * episodes, Your apps, Cameras, TV inputs, All apps.
 *
 * Ad-free: no sponsored content, no recommendations, no tracking.
 * Menu (or hold OK) on any tile opens its options; Menu elsewhere opens Settings.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var scrim: View
    private lateinit var animBg: AnimatedBackground
    private lateinit var caption: TextView
    private lateinit var glow: FocusGlow
    /** Background layers below the content: wallpaper, animated effect, scrim, caption. */
    private val LAYERS = 4
    private var scroll: ScrollView? = null
    private val camViews = mutableListOf<CameraView>()
    private var camStripRow: LinearLayout? = null
    private var builtRevision = -1L
    private var builtWatch = -1L
    private var dirty = true
    private var resumed = false
    private var firstFocus: View? = null
    private var tab = "home"
    private var wallpaperSrc: String? = "<unset>"

    /** The show the wallpaper currently follows (last highlighted). */
    private var heroItem: WatchItem? = null
    private val h = Handler(Looper.getMainLooper())
    private var pendingHero: WatchItem? = null
    private val heroRunnable = Runnable { pendingHero?.let { showHero(it) } }

    // drag-to-reorder for Your apps
    private var movingPkg: String? = null
    private var moveHint: TextView? = null
    private var moveLongHandled = false

    private fun enterMove(pkg: String) {
        if (!BasePrefs.isFavorite(this, pkg)) return
        movingPkg = pkg
        moveLongHandled = true // the hold that started this must not also drop it
        moveHint?.let { root.removeView(it) }
        moveHint = Ui.text(this, "MOVING  ◀ ▶ ▲ ▼ move  ·  OK drop  ·  hold OK for options  ·  Back done", 16f,
            LauncherTheme.onAccent, true).apply {
            background = Ui.rounded(this@MainActivity, LauncherTheme.accent, 20)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        root.addView(moveHint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) })
        markMoving()
    }

    private fun markMoving() {
        val v = movingPkg?.let { root.findViewWithTag<View>("fav:$it") } ?: return
        v.requestFocus()
        v.post { v.animate().scaleX(1.15f).scaleY(1.15f).translationZ(30f).setDuration(120).start() }
    }

    private fun exitMove() {
        if (movingPkg == null) return
        movingPkg = null
        moveHint?.let { root.removeView(it) }
        moveHint = null
        builtRevision = -1L
        if (resumed) rebuild()
    }

    private fun moveBy(delta: Int) {
        val pkg = movingPkg ?: return
        val favs = BasePrefs.favorites(this)
        val i = favs.indexOf(pkg)
        if (i < 0) return
        val j = (i + delta).coerceIn(0, favs.size - 1)
        if (j == i) return
        BasePrefs.moveFavoriteTo(this, pkg, j)
        rebuild()
        moveHint?.let { if (it.parent == null) root.addView(it, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) }) }
        markMoving()
    }

    // camera strip resize mode
    private var resizeMode = false
    private var resizeHint: TextView? = null

    /** Remaps on the home screen even when the accessibility service is off. */
    private val engine by lazy { KeyEngine(this, "home") }

    private val watchPoll = object : Runnable {
        override fun run() {
            if (resumed && WatchStore.version != builtWatch && !resizeMode && movingPkg == null) rebuild()
            updateSleepPill()
            if (resumed) Weather.refresh(this@MainActivity) { if (resumed) bindWeather() }
            h.postDelayed(this, 3000)
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            AppRepository.invalidate()
            dirty = true
            if (resumed) rebuild()
        }
    }

    /** dp scaled by the theme's density (Compact shrinks everything ~15 %). */
    private fun dp(v: Int) = Ui.dp(this, (v * LauncherTheme.density).toInt().coerceAtLeast(if (v > 0) 1 else 0))
    private val zoomFactor get() = if (LauncherTheme.focusStyle == "lift") 1.1f else 1.07f
    private val gtv get() = BasePrefs.homeStyle(this) == "gtv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BasePrefs.setupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        root = FrameLayout(this).apply { setBackgroundColor(LauncherTheme.background) }
        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        scrim = View(this)
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        animBg = AnimatedBackground(this)
        root.addView(animBg, FrameLayout.LayoutParams(-1, -1))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        caption = TextView(this).apply {
            textSize = 15f; setTextColor(Color.WHITE); maxLines = 2
            setShadowLayer(8f, 0f, 1f, Color.BLACK)
            visibility = View.GONE
        }
        // The caption is part of the wallpaper: it sits BEHIND the content (index 3),
        // so cameras, rows and apps always draw on top of it.
        root.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 24))
        })
        glow = FocusGlow(this)
        root.addView(glow, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(pkgReceiver, f)
        BootReceiver.scheduleWatchdog(this)
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (!::root.isInitialized) return
        resumed = true
        ServiceSetup.selfEnable(this)
        ShowInfo.refreshStale(this)
        Weather.refresh(this) { if (resumed) bindWeather() }
        if (BasePrefs.wallpaperMode(this) == "discover") Discover.refresh(this) { if (resumed) { wallpaperSrc = "<new>"; applyWallpaper(heroItem) } }
        if (::animBg.isInitialized) { animBg.running = true; animBg.invalidate() }
        if (dirty || builtRevision != BasePrefs.revision || builtWatch != WatchStore.version) rebuild()
        camViews.forEach { it.start() }
        h.postDelayed(watchPoll, 3000)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        stopAnimatedGradient()
        h.removeCallbacks(slideRunnable)
        animBg.running = false
        exitMove()
        exitResize()
        engine.reset()
        h.removeCallbacks(watchPoll)
        camViews.forEach { it.stop() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: back to the top of the Home tab.
        if (tab != "home") { tab = "home"; rebuild() }
        scroll?.smoothScrollTo(0, 0)
        firstFocus?.requestFocus()
    }

    // ================================================================ building
    private fun rebuild() {
        LauncherTheme.load(this)
        val focusedTag = currentFocus?.tag as? String
        val scrollY = scroll?.scrollY ?: 0
        camViews.forEach { it.stop() }
        camViews.clear()
        camStripRow = null
        scroll?.let { root.removeView(it) }
        firstFocus = null

        val pad = dp(LauncherTheme.SCREEN_PADDING_DP)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(20), pad, dp(48))
            clipChildren = false; clipToPadding = false
        }
        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            addView(content)
        }
        scroll = sv
        root.addView(sv, LAYERS, FrameLayout.LayoutParams(-1, -1))
        // theme effects
        animBg.effect = LauncherTheme.effect
        animBg.running = true
        glow.mode = LauncherTheme.glowMode
        glow.pulse = BasePrefs.glowPulse(this)
        glow.cornerPx = Ui.dpf(this, LauncherTheme.CORNER_DP.toFloat())
        glow.invalidate()

        headerParent = content
        val fitKey = "${BasePrefs.revision}|${resources.displayMetrics.widthPixels}|${tab}"
        if (fitKey != headerFitKey) { headerFit = 1f; headerFitKey = fitKey }
        content.addView(topBar())
        if (BasePrefs.nameSpot(this) == "under") nameBadge(34f)?.let { content.addView(it, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(8); topMargin = dp(6); bottomMargin = dp(2) }) }
        if (BasePrefs.clockStyle(this) == "big") clockView()?.let { content.addView(it) }
        val watching = WatchStore.continueWatching(this)
        val upcoming = WatchStore.upcoming(this)

        if (tab == "apps") {
            buildAllApps(content, showHeader = true)
        } else {
            for (id in BasePrefs.sectionOrder(this)) {
                if (!BasePrefs.sectionShown(this, id)) continue
                when (id) {
                    "hero" -> buildFeatured(content, watching, upcoming)
                    "watchstrip" -> buildWatchStrip(content, watching, upcoming)
                    "watching" -> buildWatching(content, watching)
                    "upcoming" -> buildUpcoming(content, upcoming)
                    "favorites" -> buildFavorites(content)
                    "cameras" -> buildCameraSection(content)
                    "inputs" -> buildInputs(content)
                    "apps" -> buildAllApps(content, showHeader = true)
                }
            }
        }
        if (BasePrefs.nameSpot(this) == "footer") nameBadge(22f)?.let { content.addView(it, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(28) }) }

        // Settings changed (theme / wallpaper)? Force the wallpaper to redraw.
        if (builtRevision != BasePrefs.revision) wallpaperSrc = "<rebuilt>"
        heroItem = heroItem?.let { old -> (watching + upcoming).firstOrNull { it.key == old.key } }
            ?: watching.firstOrNull() ?: upcoming.firstOrNull()
        showHero(heroItem)

        builtRevision = BasePrefs.revision
        builtWatch = WatchStore.version
        dirty = false
        root.setBackgroundColor(LauncherTheme.background)
        val target = (movingPkg?.let { root.findViewWithTag<View>("fav:$it") })
            ?: focusedTag?.let { root.findViewWithTag<View>(it) } ?: firstFocus
        sv.post { sv.scrollTo(0, scrollY); target?.requestFocus() }
        if (resumed) camViews.forEach { it.start() }
    }

    // ---------------------------------------------------------------- wallpaper
    private fun applyWallpaper(focused: WatchItem?) {
        val mode = BasePrefs.wallpaperMode(this)
        // Only "From what you're watching" follows the highlighted show; every other
        // mode shows exactly the wallpaper you picked.
        val item = if (mode == "auto") focused else null
        val dim = BasePrefs.wallpaperDim(this)
        val base = LauncherTheme.background
        applyBlur()
        if (mode == "animated") { startAnimatedGradient() } else stopAnimatedGradient()
        val rotating = mode == "slideshow" || mode == "discover"
        if (rotating) { if (item == null) startSlideshow() else h.removeCallbacks(slideRunnable) } else h.removeCallbacks(slideRunnable)
        val disc = if (mode == "discover" && item == null) discoverItem() else null
        showCaption(disc)
        val src: String? = when (mode) {
            "discover" -> disc?.image ?: slideSource(any = true) ?: WatchStore.continueWatching(this).firstOrNull()?.landscape
            "slideshow" -> slideSource() ?: WatchStore.continueWatching(this).firstOrNull()?.landscape
            "auto" -> item?.landscape ?: WatchStore.continueWatching(this).firstOrNull()?.landscape
            "url" -> BasePrefs.wallpaperUrl(this).takeIf { it.startsWith("http") || it.startsWith("/") }
            else -> null
        }
        // Scrim: dark on the left (text side) and the bottom (rows), like Google TV.
        val a = (dim * 255 / 100).coerceIn(0, 255)
        val strong = (minOf(255, a + 90) shl 24) or (base and 0xFFFFFF)
        val weak = (a / 2 shl 24) or (base and 0xFFFFFF)
        scrim.background = android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(strong, weak, (a / 3 shl 24))),
            GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(base, strong and 0xE0FFFFFF.toInt(), 0x00000000))
        ))
        if (src == wallpaperSrc) return
        wallpaperSrc = src
        // Wallpaper only: every image (backdrops and tall posters) just fills the
        // background behind the content — nothing is ever drawn on top of the rows.
        when {
            src != null -> {
                Img.load(wallpaper, src, resources.displayMetrics.widthPixels) { ok ->
                    if (ok) { wallpaper.alpha = 0f; wallpaper.animate().alpha(1f).setDuration(600).start() }
                    else fallbackWallpaper(mode)
                }
            }
            else -> fallbackWallpaper(mode)
        }
    }

    private fun fallbackWallpaper(mode: String) {
        wallpaper.tag = null
        wallpaper.setImageDrawable(when (mode) {
            "none" -> ColorDrawable(LauncherTheme.background)
            "color" -> ColorDrawable(try { Color.parseColor(BasePrefs.wallpaperColor(this)) } catch (e: Exception) { LauncherTheme.background })
            "animated" -> animDrawable
            else -> GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (LauncherTheme.preset.gradient.distinct().size > 1) LauncherTheme.gradient
                else intArrayOf(blend(LauncherTheme.accent, LauncherTheme.background, 0.35f), LauncherTheme.background,
                    blend(Color.BLACK, LauncherTheme.background, 0.5f)))
        })
        wallpaper.alpha = 1f
    }

    // Animated gradient wallpaper: slowly drifts between the theme's gradient colors.
    private val animDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.BLACK, Color.BLACK))
    private var animator: android.animation.ValueAnimator? = null
    private fun startAnimatedGradient() {
        if (animator != null) return
        val cols = LauncherTheme.gradient.takeIf { it.size >= 2 } ?: intArrayOf(LauncherTheme.accent, LauncherTheme.background)
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 14_000; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                val n = cols.size
                animDrawable.colors = IntArray(n) { i -> blend(cols[i], cols[(i + 1) % n], t) }
            }
            start()
        }
    }
    private fun stopAnimatedGradient() { animator?.cancel(); animator = null }

    // Slideshow wallpaper: rotates through your shows' artwork every 25 s.
    private var slideIndex = 0
    private fun discoverItem(): Discover.Item? {
        val l = Discover.list(this)
        return if (l.isEmpty()) null else l[slideIndex % l.size]
    }

    private fun showCaption(d: Discover.Item?) {
        if (d == null || !BasePrefs.discoverCaption(this)) { caption.visibility = View.GONE; return }
        val sp = android.text.SpannableStringBuilder()
        sp.append(d.title, android.text.style.StyleSpan(Typeface.BOLD), 0)
        sp.setSpan(android.text.style.RelativeSizeSpan(1.25f), 0, d.title.length, 0)
        sp.append("\n").append(d.caption)
        caption.text = sp
        caption.visibility = View.VISIBLE
        caption.alpha = 0f
        caption.animate().alpha(1f).setDuration(500).start()
    }

    /** Your shows' artwork in turn ([any] = also when Discover has nothing loaded yet). */
    private fun slideSource(any: Boolean = false): String? {
        if (!any && BasePrefs.wallpaperMode(this) != "slideshow") return null
        val arts = (WatchStore.continueWatching(this) + WatchStore.upcoming(this)).mapNotNull { it.backdropUrl ?: it.landscape }.distinct()
        return if (arts.isEmpty()) null else arts[slideIndex % arts.size]
    }
    private val slideRunnable = Runnable {
        slideIndex++
        wallpaperSrc = "<next>"
        applyWallpaper(null)
    }
    private fun startSlideshow() {
        h.removeCallbacks(slideRunnable)
        h.postDelayed(slideRunnable, if (BasePrefs.wallpaperMode(this) == "discover") 18_000 else 25_000)
    }

    private fun applyBlur() {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val r = BasePrefs.wallpaperBlur(this).toFloat()
        wallpaper.setRenderEffect(if (r > 0) android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP) else null)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * t + ((b shr s) and 255) * (1 - t)).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }

    // ---------------------------------------------------------------- top bar
    // ---------------------------------------------------------------- header
    /** Automatic shrink factor so the whole header always fits on one row. */
    private var headerFit = 1f
    private var headerFitKey = ""
    private var headerView: View? = null
    private var headerParent: LinearLayout? = null

    /** Final header scale: Header size setting × automatic fit. */
    private val hs get() = BasePrefs.headerSize(this) / 100f * headerFit

    private fun hdp(v: Float) = Ui.dp(this, (v * hs * LauncherTheme.density).toInt().coerceAtLeast(1))

    /** Round icon button (Search, Cameras, Float, Customize, Settings). */
    private fun iconButton(kind: HeaderIcon.Kind, tag: String, desc: String, onClick: () -> Unit): View {
        val size = hdp(46f)
        return FrameLayout(this).apply {
            this.tag = tag
            contentDescription = desc
            isFocusable = true; isClickable = true
            background = Ui.focusBg(this@MainActivity, Color.TRANSPARENT, 999)
            addView(HeaderIcon(this@MainActivity, kind), FrameLayout.LayoutParams(hdp(24f), hdp(24f), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = hdp(6f) }
            setOnClickListener { onClick() }
        }
    }

    /** Text tab (Apps). Selected = accent underline, like Google TV's tabs. */
    private fun textTab(label: String, selected: Boolean, tag: String, onClick: () -> Unit): TextView =
        Ui.text(this, label, 17f * hs, if (selected) LauncherTheme.text else LauncherTheme.softText, selected).apply {
            this.tag = tag
            gravity = Gravity.CENTER
            isFocusable = true; isClickable = true
            setPadding(hdp(16f), hdp(9f), hdp(16f), hdp(9f))
            background = if (selected) android.graphics.drawable.LayerDrawable(arrayOf(
                Ui.focusBg(this@MainActivity, Color.TRANSPARENT, 999),
                GradientDrawable().apply { setColor(LauncherTheme.accent); cornerRadius = Ui.dpf(this@MainActivity, 2f) }
            )).apply {
                setLayerGravity(1, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                setLayerSize(1, hdp(18f), hdp(3f)); setLayerInsetBottom(1, hdp(3f))
            } else Ui.focusBg(this@MainActivity, Color.TRANSPARENT, 999)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = hdp(6f) }
            setOnClickListener { onClick() }
        }

    /** Thin vertical divider between header groups. */
    private fun divider(): View = View(this).apply {
        setBackgroundColor((LauncherTheme.text and 0xFFFFFF) or 0x33000000)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 1), hdp(30f)).apply {
            marginStart = hdp(10f); marginEnd = hdp(14f)
        }
    }

    /**
     * The header, designed like Google TV's: one clean row.
     *   [Name]  [🔍] [Apps] [📷] [⧉]  ………  [☀ 61° / Mostly clear] | [1:16 PM / Wed, Nov 4]  [✎] [⚙]
     * Nothing is ever hidden or pushed off: if the row is wider than the screen,
     * every item shrinks together (auto-fit) until it fits.
     */
    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hdp(4f), hdp(4f), 0, hdp(10f))
            clipChildren = false; clipToPadding = false
        }
        if (BasePrefs.nameSpot(this) == "left") nameBadge(24f * hs)?.let {
            bar.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = hdp(20f) })
        }
        val search = iconButton(HeaderIcon.Kind.SEARCH, "top:search", "Search") { openSearch() }
        if (BasePrefs.showSearch(this)) bar.addView(search)
        val apps = textTab(if (tab == "apps") "✕  Apps" else "Apps", tab == "apps", "top:apps") {
            tab = if (tab == "apps") "home" else "apps"; rebuild()
        }
        bar.addView(apps)
        if (BasePrefs.cameras(this).isNotEmpty()) {
            if (BasePrefs.showCamButton(this))
                bar.addView(iconButton(HeaderIcon.Kind.CAMERA, "top:cams", "Cameras") {
                    startActivity(Intent(this, CameraWallActivity::class.java))
                })
            if (BasePrefs.showFloatButton(this))
                bar.addView(iconButton(HeaderIcon.Kind.PIP, "top:float", "Floating cameras") { OverlayManager.toggle(this) })
        }
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (BasePrefs.nameSpot(this) == "center") {
            nameBadge(22f * hs)?.let { bar.addView(it) }
            bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        val wx = weatherView()
        if (wx != null) bar.addView(wx)
        val clock = if (BasePrefs.clockStyle(this) != "big") clockView() else null
        if (wx != null && clock != null) bar.addView(divider())
        clock?.let { bar.addView(it) }
        bar.addView(View(this), LinearLayout.LayoutParams(hdp(14f), 1))
        bar.addView(iconButton(HeaderIcon.Kind.EDIT, "top:customize", "Customize home") {
            startActivity(Intent(this, CustomizeActivity::class.java))
        })
        val settings = iconButton(HeaderIcon.Kind.SETTINGS, "top:settings", "Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        (settings.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        bar.addView(settings)

        // ◀ on the first button and ▶ on Settings stay put.
        val first = if (BasePrefs.showSearch(this)) search else apps
        first.id = View.generateViewId(); settings.id = View.generateViewId()
        first.nextFocusLeftId = first.id
        settings.nextFocusRightId = settings.id

        headerView = bar
        bar.post { fitHeader(bar) }
        return bar
    }

    /** Shrinks the header (and rebuilds it) when its natural width is wider than the screen. */
    private fun fitHeader(bar: LinearLayout) {
        if (bar.width <= 0 || bar !== headerView) return
        var need = bar.paddingLeft + bar.paddingRight
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        for (i in 0 until bar.childCount) {
            val v = bar.getChildAt(i)
            val lp = v.layoutParams as LinearLayout.LayoutParams
            if (lp.weight > 0f) continue
            v.measure(unspec, unspec)
            need += v.measuredWidth + lp.leftMargin + lp.rightMargin
        }
        val avail = bar.width
        val target = avail * 0.97f
        if (need > target && headerFit > 0.6f) {
            headerFit = (headerFit * target / need).coerceAtLeast(0.6f)
            replaceHeader()
        } else if (need < avail * 0.8f && headerFit < 1f) {
            // room again (e.g. a shorter time string) — grow back a little
            headerFit = (headerFit * 1.08f).coerceAtMost(1f)
            replaceHeader()
        }
    }

    private fun replaceHeader() {
        val parent = headerParent ?: return
        val old = headerView ?: return
        val idx = parent.indexOfChild(old)
        if (idx < 0) return
        val hadFocus = old.hasFocus()
        val focusTag = (currentFocus?.tag as? String)?.takeIf { it.startsWith("top:") }
        parent.removeViewAt(idx)
        parent.addView(topBar(), idx)
        if (hadFocus) focusTag?.let { t -> parent.findViewWithTag<View>(t)?.requestFocus() }
    }

    private fun clockFormats(): Pair<String, String> {
        val is24 = BasePrefs.clock24h(this)
        val sec = BasePrefs.clockSeconds(this)
        val time = (if (is24) "HH:mm" else "h:mm") + (if (sec) ":ss" else "") + (if (is24) "" else " a")
        val date = when (BasePrefs.dateOrder(this)) {
            "dmy" -> "EEEE · d MMMM"
            "num" -> "EEEE · M/d"
            else -> "EEEE · MMMM d"
        }
        return time to date
    }

    private var sleepPill: TextView? = null

    // ---------------------------------------------------------------- weather
    private var wxIcon: WeatherIcon? = null
    private var wxTemp: TextView? = null
    private var wxLine: TextView? = null

    /** Weather in the header: animated icon, temperature, and one short line under it. */
    private fun weatherView(): View? {
        if (!BasePrefs.weatherEnabled(this)) return null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            tag = "top:weather"
            contentDescription = "Weather"
            background = Ui.focusBg(this@MainActivity, Color.TRANSPARENT, 999)
            setPadding(hdp(8f), hdp(4f), hdp(14f), hdp(4f))
            setOnClickListener { CustomizeActivity.weatherDetails(this@MainActivity) { rebuild() } }
        }
        val icon = WeatherIcon(this).apply { animate = BasePrefs.weatherAnimated(this@MainActivity) }
        box.addView(icon, LinearLayout.LayoutParams(hdp(38f), hdp(38f)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(hdp(8f), 0, 0, 0) }
        val temp = Ui.text(this, "", 24f * hs, LauncherTheme.text).apply {
            typeface = if (LauncherTheme.font == "mono") Typeface.MONOSPACE else Typeface.create("sans-serif", Typeface.NORMAL)
            if (!LauncherTheme.light) setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            maxLines = 1
        }
        val line = Ui.text(this, "", 12.5f * hs, LauncherTheme.softText).apply {
            maxLines = 1
            if (!LauncherTheme.light) setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
        }
        col.addView(temp); col.addView(line)
        box.addView(col)
        wxIcon = icon; wxTemp = temp; wxLine = line
        bindWeather()
        return box
    }

    private fun bindWeather() {
        val temp = wxTemp ?: return
        val now = Weather.current
        val place = BasePrefs.weatherPlace(this)
        when {
            place == null -> { wxIcon?.kind = Weather.Kind.PARTLY_DAY; temp.text = "--°"; wxLine?.text = "Set your location ›" }
            now == null -> { wxIcon?.kind = Weather.Kind.CLOUDS; temp.text = "--°"; wxLine?.text = "Loading weather…" }
            else -> {
                wxIcon?.kind = Weather.kind(now.code, now.isDay)
                wxIcon?.windy = now.wind >= (if (BasePrefs.weatherFahrenheit(this)) 18.0 else 29.0)
                temp.text = Weather.deg(this, now.temp)
                // One short line, Google style. Wind / rain chance / 4-day forecast: press OK on the weather.
                val parts = mutableListOf(Weather.describe(now.code))
                if (BasePrefs.weatherDetails(this) && !now.high.isNaN())
                    parts += "${Weather.deg(this, now.high)} / ${Weather.deg(this, now.low)}"
                if (BasePrefs.weatherDetails(this) && now.rainChance >= 40) parts += "☂ ${now.rainChance}%"
                wxLine?.text = parts.joinToString(" · ")
            }
        }
    }

    /**
     * The clock: in the header a compact stack (time, then "Wed, Nov 4");
     * "big" puts a large clock at the top of the page instead.
     */
    private fun clockView(): View? {
        val style = BasePrefs.clockStyle(this)
        if (style == "hidden") return null
        val big = style == "big"
        val k = if (big) 1f else hs
        val (tf, df0) = clockFormats()
        val df = if (big) df0 else df0.replace("EEEE · ", "EEE, ").replace("MMMM", "MMM")
        fun clock(fmt: String, sp: Float, color: Int, light: Boolean) = TextClock(this).apply {
            format12Hour = fmt; format24Hour = fmt
            textSize = sp * LauncherTheme.textScale; setTextColor(color)
            typeface = if (LauncherTheme.font == "mono") Typeface.MONOSPACE
                else Typeface.create(if (light) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
            if (!LauncherTheme.light) setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            maxLines = 1
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (big) Gravity.CENTER_HORIZONTAL else Gravity.START or Gravity.CENTER_VERTICAL
        }
        val greet = if (BasePrefs.clockGreeting(this)) greeting() else null
        when (style) {
            "single" -> col.addView(clock(df.replace("EEEE", "EEE") + "  ·  " + tf, 17f * k, LauncherTheme.text, false))
            "time" -> col.addView(clock(tf, 24f * k, LauncherTheme.text, false))
            "big" -> {
                greet?.let { col.addView(Ui.text(this, it, 20f, LauncherTheme.accent, true)) }
                col.addView(clock(tf, 72f, LauncherTheme.text, true))
                col.addView(clock(df, 22f, LauncherTheme.softText, false))
                col.setPadding(0, dp(6), 0, dp(10))
            }
            else -> {
                col.addView(clock(tf, 24f * k, LauncherTheme.text, false))
                val dateLine = clock(df, 12.5f * k, LauncherTheme.softText, false)
                if (greet != null) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(Ui.text(this, "$greet · ", 12.5f * k, LauncherTheme.accent, true).apply { maxLines = 1 })
                    row.addView(dateLine)
                    col.addView(row)
                } else col.addView(dateLine)
            }
        }
        val pill = Ui.text(this, "", 11.5f * k, LauncherTheme.onAccent, true).apply {
            background = Ui.rounded(this@MainActivity, LauncherTheme.accent, 10)
            setPadding(dp(8), dp(1), dp(8), dp(1))
            visibility = View.GONE
        }
        col.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
        sleepPill = pill
        updateSleepPill()
        return col
    }

    private fun updateSleepPill() {
        val m = HomeGuardService.sleepMinutesLeft()
        sleepPill?.apply { visibility = if (m > 0) View.VISIBLE else View.GONE; text = "💤 $m min" }
    }

    private fun nameBadge(sp: Float): View? =
        NameBadge(this, sp).apply { shimmer = BasePrefs.nameShimmer(this@MainActivity) }

    private fun openSearch() {
        val tries = listOf(
            Intent(Intent.ACTION_ASSIST),
            Intent("android.search.action.GLOBAL_SEARCH"),
            Intent(Intent.ACTION_WEB_SEARCH)
        )
        for (i in tries) try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) {}
        Ui.toast(this, "No search app on this TV")
    }

    // ---------------------------------------------------------------- hero
    /** Splits upcoming shows into "Airing soon" (inside the user's window) and later. */
    private fun splitUpcoming(upcoming: List<WatchItem>): Pair<List<WatchItem>, List<WatchItem>> {
        val cut = System.currentTimeMillis() + BasePrefs.soonHours(this) * 3600_000L
        // "date TBA" shows (nextAt = 0) always go in Upcoming, never in Airing soon.
        return upcoming.partition { it.nextAt in 1..cut }
    }

    private fun panelTitle(id: String) =
        if (id == "soon") "Airing soon · ${BasePrefs.soonLabel(this)}" else BasePrefs.stripPanelLabel(id).substringBefore(" (")

    /**
     * Featured row: three compact cards side by side — the top show of
     * Continue watching, Airing soon and Upcoming episodes — each with its
     * own artwork, so nothing depends on what was highlighted last.
     */
    private fun buildFeatured(content: LinearLayout, watching: List<WatchItem>, upcoming: List<WatchItem>) {
        val (soon, _) = splitUpcoming(upcoming)
        // "Upcoming episodes" lists every followed show's next episode, same as the
        // Upcoming episodes row — including ones also listed under Airing soon.
        val data = mapOf("continue" to watching, "soon" to soon, "later" to upcoming)
        val panels = BasePrefs.STRIP_PANELS.filter { BasePrefs.featureShown(this, it) }
        if (panels.isEmpty()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(6))
            clipChildren = false; clipToPadding = false
        }
        val g = dp(16)
        val w = (usableWidth() - g * (panels.size - 1)) / panels.size
        panels.forEachIndexed { i, id ->
            row.addView(featureCard(id, data[id]?.firstOrNull(), w).also { gap(it, i == panels.size - 1) })
        }
        content.addView(row)
    }

    private fun featureCard(panel: String, item: WatchItem?, w: Int): View {
        val h = dp(118)
        val c = card(w, h, bg = LauncherTheme.panel)
        c.tag = "ft:$panel:" + (item?.key ?: "empty")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (item != null) {
            val iw = minOf(h * 16 / 9, w * 45 / 100)
            val img = ImageView(this)
            row.addView(img, LinearLayout.LayoutParams(iw, -1))
            val src = if (panel == "continue") item.landscape ?: item.posterUrl else item.backdropUrl ?: item.posterUrl ?: item.landscape
            loadArt(img, src, iw, item.pkg)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(12), dp(8))
        }
        fun t(txt: String, sp: Float, color: Int, bold: Boolean = false) =
            Ui.text(this, txt, sp, color, bold).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        texts.addView(t(panelTitle(panel).uppercase(), 11f, LauncherTheme.accent, true).apply { letterSpacing = 0.08f })
        if (item == null) {
            texts.addView(t(if (panel == "continue") "Nothing yet" else "Nothing scheduled", 17f, LauncherTheme.text, true))
            texts.addView(t(if (panel == "continue") "Play a show in any app" else "+ Follow a show", 13f, LauncherTheme.dimText))
            c.setOnClickListener {
                if (panel == "continue") startActivity(Intent(this, WatchActivity::class.java))
                else WatchActivity.followFlow(this) { rebuild() }
            }
        } else {
            texts.addView(t(if (item.needsMatch) "Pick the show ›" else item.title, 17f, LauncherTheme.text, true))
            val line2 = when {
                item.needsMatch -> item.show
                panel == "continue" -> item.episode ?: appName(item.pkg) ?: ""
                else -> {
                    val ep = if (item.nextSeason > 0) "S${item.nextSeason} E${item.nextEp}" else if (item.nextEp > 0) "Ep ${item.nextEp}" else "Next episode"
                    "$ep · ${ShowInfo.whenText(item.nextAt)}"
                }
            }
            texts.addView(t(line2, 13f, LauncherTheme.dimText))
            val line3 = if (panel == "continue") listOfNotNull(
                if (item.progress in 0.01f..0.99f) "${(item.progress * 100).toInt()}% watched" else null,
                ago(item.lastWatched), appName(item.pkg)).joinToString(" · ")
            else ShowInfo.countdown(item.nextAt)
            texts.addView(t(line3, 13f, if (panel == "continue") LauncherTheme.dimText else LauncherTheme.accent, panel != "continue"))
            c.setOnClickListener { openItem(item) }
            c.setOnLongClickListener { WatchActivity.itemMenu(this, item) { rebuild() }; true }
        }
        row.addView(texts, LinearLayout.LayoutParams(0, -1, 1f))
        c.addView(row, FrameLayout.LayoutParams(-1, -1))
        val zoom = BasePrefs.focusZoom(this)
        c.setOnFocusChangeListener { v, has ->
            if (zoom) v.animate().scaleX(if (has) 1.04f else 1f).scaleY(if (has) 1.04f else 1f)
                .translationZ(if (has) 14f else 0f).setDuration(140).start()
            if (has && item != null) queueHero(item)
        }
        return c
    }

    /** When a show has no artwork: the app's own TV banner (or icon) instead of an empty box. */
    private fun fallbackArt(img: ImageView, pkg: String?) {
        val app = pkg?.let { AppRepository.find(this, it) }
        val d = app?.let { AppRepository.banner(this, it) }
        if (d != null) { img.scaleType = ImageView.ScaleType.CENTER_CROP; img.setImageDrawable(d); return }
        val icon = app?.let { AppRepository.icon(this, it) }
        img.scaleType = ImageView.ScaleType.CENTER_INSIDE
        img.setImageDrawable(icon)
    }

    private fun loadArt(img: ImageView, src: String?, w: Int, pkg: String?) {
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        Img.load(img, src, w) { ok -> if (!ok) fallbackArt(img, pkg) }
    }

    private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    private fun appName(pkg: String?) = pkg?.let { AppRepository.label(this, it) }

    /** Moves the wallpaper to [item] (or the default) — the featured row has its own content. */
    private fun showHero(item: WatchItem?) {
        heroItem = item
        applyWallpaper(item)
    }

    private fun ago(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60_000
        return when {
            m < 2 -> "just now"
            m < 60 -> "${m}m ago"
            m < 1440 -> "${m / 60}h ago"
            else -> "${m / 1440}d ago"
        }
    }

    private fun queueHero(item: WatchItem) {
        if (BasePrefs.wallpaperMode(this) != "auto") return
        pendingHero = item
        h.removeCallbacks(heroRunnable)
        h.postDelayed(heroRunnable, 280)
    }

    private fun openItem(item: WatchItem) {
        val pkg = item.pkg ?: if (item.isAnime) WatchActivity.CRUNCHYROLL.firstOrNull { AppRepository.find(this, it) != null } else null
        if (pkg != null && AppRepository.launchPackage(this, pkg)) return
        WatchActivity.itemMenu(this, item) { rebuild() }
    }

    // ---------------------------------------------------------------- rows
    private fun section(parent: LinearLayout, title: String) {
        parent.addView(Ui.text(this, title, 20f, LauncherTheme.text, true).apply {
            if (!LauncherTheme.light) setShadowLayer(6f, 0f, 1f, Color.BLACK)
            setPadding(dp(4), dp(18), 0, dp(6))
        })
    }

    private fun hRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            clipToPadding = false
            clipChildren = false
        }
        parent.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            addView(row)
        })
        return row
    }

    private fun card(w: Int, h: Int, radius: Int = LauncherTheme.CORNER_DP, bg: Int = LauncherTheme.surface) = FrameLayout(this).apply {
        isFocusable = true
        isClickable = true
        isLongClickable = true
        background = Ui.rounded(this@MainActivity, bg, radius)
        foreground = Ui.focusBg(this@MainActivity, Color.TRANSPARENT, radius)
        layoutParams = LinearLayout.LayoutParams(w, h)
        clipToOutline = true
        if (firstFocus == null) firstFocus = this
        val zoom = BasePrefs.focusZoom(this@MainActivity)
        setOnFocusChangeListener { v, has ->
            if (zoom) v.animate().scaleX(if (has) zoomFactor else 1f).scaleY(if (has) zoomFactor else 1f)
                .translationZ(if (has) 14f else 0f).setDuration(140).start()
        }
    }

    private fun gap(v: View, last: Boolean) {
        (v.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = if (last) 0 else dp(18)
    }

    // Continue watching -----------------------------------------------------
    private fun buildWatching(content: LinearLayout, items: List<WatchItem>) {
        section(content, "Continue watching")
        val row = hRow(content)
        if (items.isEmpty()) {
            row.addView(hintCard(
                if (MediaWatchService.instance == null && BasePrefs.watchTracking(this))
                    "Turn on “What's playing”\nso shows you watch appear here"
                else "Play something in Crunchyroll,\nNetflix, Prime… it appears here", "watch:setup"
            ) { startActivity(Intent(this, WatchActivity::class.java)) })
            return
        }
        items.forEachIndexed { i, it -> row.addView(mediaCard(it, upcoming = false).also { v -> gap(v, i == items.size - 1) }) }
    }

    // Upcoming episodes -----------------------------------------------------
    private fun buildUpcoming(content: LinearLayout, items: List<WatchItem>) {
        section(content, "Upcoming episodes")
        val row = hRow(content)
        items.forEachIndexed { i, it -> row.addView(mediaCard(it, upcoming = true).also { v -> gap(v, false) }) }
        row.addView(hintCard("+  Follow a show\nto see its next episode", "up:follow") {
            WatchActivity.followFlow(this) { rebuild() }
        })
    }

    private fun hintCard(text: String, tag: String, onClick: () -> Unit) =
        card(mediaCardWidth(), if (BasePrefs.mediaCardImages(this)) mediaCardWidth() * 9 / 16 else dp(86), bg = 0x66000000).apply {
        this.tag = tag
        addView(Ui.text(this@MainActivity, text, 17f, bold = true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        setOnClickListener { onClick() }
    }

    private fun mediaCardWidth() = dp(when (BasePrefs.mediaCardSize(this)) { "s" -> 210; "l" -> 380; else -> 290 })

    private fun mediaCard(item: WatchItem, upcoming: Boolean): View {
        val w = mediaCardWidth()
        val images = BasePrefs.mediaCardImages(this)
        val hh = if (images) w * 9 / 16 else dp(if (BasePrefs.mediaCardSize(this) == "s") 70 else 86)
        val c = card(w, hh, bg = if (images) LauncherTheme.surface else LauncherTheme.panel).apply {
            tag = (if (upcoming) "up:" else "cw:") + item.key
        }
        if (images) {
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            c.addView(img, FrameLayout.LayoutParams(-1, -1))
            val src = if (upcoming) (item.backdropUrl ?: item.posterUrl ?: item.landscape) else item.landscape
            loadArt(img, src, w, item.pkg)
        }
        c.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xEE000000.toInt(), 0x66000000, 0x00000000))
        }, FrameLayout.LayoutParams(-1, hh * 2 / 3, Gravity.BOTTOM))
        val txt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(if (!upcoming && item.progress >= 0) 12 else 10))
        }
        txt.addView(Ui.text(this, if (item.needsMatch) "Pick the show ›" else item.title, 16f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        val line2 = if (upcoming) {
            val ep = if (item.nextSeason > 0) "S${item.nextSeason} E${item.nextEp}" else if (item.nextEp > 0) "Ep ${item.nextEp}" else "Next ep"
            "$ep · ${ShowInfo.whenText(item.nextAt)}"
        } else if (item.needsMatch) item.show else item.episode ?: appName(item.pkg) ?: ""
        txt.addView(Ui.text(this, line2, 13f, LauncherTheme.softText).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        c.addView(txt, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        if (!upcoming && item.progress >= 0) {
            c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = (item.progress * 1000).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(LauncherTheme.accent)
            }, FrameLayout.LayoutParams(-1, dp(6), Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(4)) })
        }
        if (upcoming) {
            c.addView(Ui.text(this, ShowInfo.countdown(item.nextAt), 13f, Color.BLACK, true).apply {
                background = Ui.rounded(this@MainActivity, LauncherTheme.accent, 10)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(10), dp(10), 0) })
        }
        if (images) item.pkg?.let { AppRepository.find(this, it) }?.let { app ->
            c.addView(ImageView(this).apply {
                setImageDrawable(AppRepository.icon(this@MainActivity, app))
                background = Ui.rounded(this@MainActivity, 0xAA000000.toInt(), 8)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(10), 0, 0) })
        }
        c.setOnFocusChangeListener { v, has ->
            if (BasePrefs.focusZoom(this)) v.animate().scaleX(if (has) zoomFactor else 1f).scaleY(if (has) zoomFactor else 1f)
                .translationZ(if (has) 14f else 0f).setDuration(140).start()
            if (has) queueHero(item)
        }
        c.setOnClickListener { openItem(item) }
        c.setOnLongClickListener { WatchActivity.itemMenu(this, item) { rebuild() }; true }
        return c
    }

    // Compact watch strip ----------------------------------------------------
    /**
     * Continue watching, Airing soon and Upcoming side by side as short text
     * lines (optionally with tiny thumbnails) — all three visible at once in
     * a fraction of the height of the card rows.
     */
    private fun buildWatchStrip(content: LinearLayout, watching: List<WatchItem>, upcoming: List<WatchItem>) {
        val (soon, _) = splitUpcoming(upcoming)
        // "Upcoming episodes" lists every followed show's next episode, same as the
        // Upcoming episodes row — including ones also listed under Airing soon.
        val data = mapOf("continue" to watching, "soon" to soon, "later" to upcoming)
        val panels = BasePrefs.STRIP_PANELS.filter { BasePrefs.stripPanelShown(this, it) }
        if (panels.isEmpty()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(4))
            clipChildren = false; clipToPadding = false
            isBaselineAligned = false
        }
        panels.forEachIndexed { i, id ->
            row.addView(stripPanel(id, data[id] ?: emptyList()), LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (i < panels.size - 1) marginEnd = dp(14)
            })
        }
        content.addView(row)
    }

    private fun stripSizes(): Triple<Float, Float, Int> = when (BasePrefs.stripTextSize(this)) {
        0 -> Triple(13f, 11f, 34)
        2 -> Triple(18f, 14f, 50)
        else -> Triple(15.5f, 12.5f, 42)
    }

    private fun stripPanel(id: String, items: List<WatchItem>): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@MainActivity, LauncherTheme.panel, 14)
            setPadding(dp(10), dp(10), dp(10), dp(8))
            clipChildren = false
        }
        val title = panelTitle(id)
        panel.addView(Ui.text(this, title.uppercase() + if (items.isNotEmpty()) "  ·  ${items.size}" else "", 12f,
            LauncherTheme.accent, true).apply { letterSpacing = 0.1f; setPadding(dp(8), 0, 0, dp(4)) })
        val n = BasePrefs.stripRows(this)
        items.take(n).forEach { panel.addView(stripLine(it, id)) }
        when {
            items.size > n -> panel.addView(stripAction("+ ${items.size - n} more…", "ws:$id:more") {
                startActivity(Intent(this, WatchActivity::class.java))
            })
            items.isEmpty() && id == "continue" -> panel.addView(stripAction(
                if (MediaWatchService.instance == null && BasePrefs.watchTracking(this)) "Turn on “What's playing” →"
                else "Play a show in any app — it shows up here", "ws:$id:empty") {
                startActivity(Intent(this, WatchActivity::class.java))
            })
            items.isEmpty() -> panel.addView(stripAction(
                if (id == "soon") "Nothing in the next ${BasePrefs.soonLabel(this)}" else "+ Follow a show", "ws:$id:empty") {
                WatchActivity.followFlow(this) { rebuild() }
            })
        }
        return panel
    }

    private fun stripAction(text: String, tag: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        this.tag = tag
        textSize = stripSizes().second
        setTextColor(LauncherTheme.dimText)
        isFocusable = true; isClickable = true
        background = Ui.focusBg(this@MainActivity, Color.TRANSPARENT, 8)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun stripLine(item: WatchItem, panel: String): View {
        val (big, small, thumbH) = stripSizes()
        val thumbs = BasePrefs.stripStyle(this) == "thumbs"
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true; isLongClickable = true
            background = Ui.focusBg(this@MainActivity, Color.TRANSPARENT, 8)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            tag = "ws:$panel:${item.key}"
            if (firstFocus == null) firstFocus = this
        }
        if (thumbs) {
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.rounded(this@MainActivity, LauncherTheme.surfaceHi, 6)
            }
            Ui.roundClip(img, Ui.dpf(this, 6f))
            line.addView(img, LinearLayout.LayoutParams(dp(thumbH) * 16 / 9, dp(thumbH)).apply { marginEnd = dp(10) })
            loadArt(img, item.landscape ?: item.posterUrl, dp(thumbH) * 2, item.pkg)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.text(this, if (item.needsMatch) "Pick the show ›" else item.title, big, bold = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        val sub = if (item.needsMatch) "${appName(item.pkg) ?: ""}: ${item.show}"
        else if (panel == "continue") listOfNotNull(item.episode, appName(item.pkg)).joinToString(" · ")
        else {
            val ep = if (item.nextSeason > 0) "S${item.nextSeason} E${item.nextEp}" else if (item.nextEp > 0) "Ep ${item.nextEp}" else "Next ep"
            "$ep · ${ShowInfo.whenText(item.nextAt)}"
        }
        if (sub.isNotEmpty()) texts.addView(Ui.text(this, sub, small, LauncherTheme.dimText).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val badge = if (panel == "continue") {
            if (item.progress in 0.01f..0.99f) "${(item.progress * 100).toInt()}%" else ago(item.lastWatched)
        } else ShowInfo.countdown(item.nextAt).removePrefix("in ")
        line.addView(Ui.text(this, badge, small, if (panel == "continue") LauncherTheme.dimText else Color.BLACK, panel != "continue").apply {
            if (panel != "continue") {
                background = Ui.rounded(this@MainActivity, LauncherTheme.accent, 8)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        line.setOnFocusChangeListener { _, has -> if (has) queueHero(item) }
        line.setOnClickListener { openItem(item) }
        line.setOnLongClickListener { WatchActivity.itemMenu(this, item) { rebuild() }; true }
        return line
    }

    // Cameras ----------------------------------------------------------------
    private fun buildCameraSection(content: LinearLayout) {
        val h = BasePrefs.camStripHeightDp(this)
        if (h <= 0) return
        val cams = BasePrefs.cameras(this).withIndex().filter { it.value.onHome }
        if (cams.isEmpty() && BasePrefs.cameras(this).isNotEmpty()) return
        section(content, "Cameras")
        val row = hRow(content)
        if (cams.isEmpty()) {
            row.addView(hintCard("+  Add your MotionEye / CCTV cameras", "cam:add") {
                CamerasActivity.editCamera(this, null) { rebuild() }
            })
            return
        }
        camStripRow = row
        val (w, hh) = camTileSize()
        cams.forEachIndexed { pos, (index, cam) ->
            val tile = card(w, hh).apply {
                tag = "cam:$index"
                val cv = CameraView(this@MainActivity, cam, compact = hh < dp(200)).apply {
                    // Full-bleed, cropped to the tile, clipped to its rounded corners;
                    // the focus border (card foreground) is drawn over the edges.
                    scale = if (BasePrefs.camTileFit(this@MainActivity) == "fit") CameraView.Scale.FIT else CameraView.Scale.FILL
                    setCornerRadius(Ui.dpf(this@MainActivity, LauncherTheme.CORNER_DP.toFloat()))
                }
                camViews += cv
                addView(cv, 0, FrameLayout.LayoutParams(-1, -1))
                setOnClickListener { startActivity(Intent(this@MainActivity, CameraWallActivity::class.java).putExtra("cam", index)) }
                setOnLongClickListener { cameraMenu(index); true }
            }
            gap(tile, pos == cams.size - 1)
            row.addView(tile)
        }
    }

    private fun usableWidth() = resources.displayMetrics.widthPixels - 2 * dp(LauncherTheme.SCREEN_PADDING_DP) - 2 * dp(12)

    private fun camTileSize(): Pair<Int, Int> {
        val cols = BasePrefs.camStripColumns(this)
        val w = (usableWidth() - dp(18) * (cols - 1)) / cols
        return w to dp(BasePrefs.camStripHeightDp(this))
    }

    private fun applyCameraSize() {
        val row = camStripRow ?: return
        val (w, hh) = camTileSize()
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i)
            val lp = v.layoutParams as LinearLayout.LayoutParams
            lp.width = w; lp.height = hh
            v.layoutParams = lp
        }
        resizeHint?.text = resizeText()
    }

    private fun cameraMenu(index: Int) {
        val cam = BasePrefs.cameras(this).getOrNull(index) ?: return
        Ui.choose(this, cam.name, listOf(
            "Watch full screen", "Show as floating window (over other apps)", "Watch all cameras",
            "Resize camera strip", "Edit camera", "Hide from home", "Manage cameras"
        )) { w ->
            when (w) {
                0 -> startActivity(Intent(this, CameraWallActivity::class.java).putExtra("cam", index))
                1 -> OverlayManager.show(this, index)
                2 -> startActivity(Intent(this, CameraWallActivity::class.java))
                3 -> enterResize()
                4 -> CamerasActivity.editCamera(this, index) { rebuild() }
                5 -> {
                    val l = BasePrefs.cameras(this); l[index] = cam.copy(onHome = false)
                    BasePrefs.setCameras(this, l); rebuild()
                }
                6 -> startActivity(Intent(this, CamerasActivity::class.java))
            }
        }
    }

    private fun resizeText() =
        "RESIZE CAMERAS  ▲▼ height (${BasePrefs.camStripHeightDp(this)}dp)   ◀▶ per row (${BasePrefs.camStripColumns(this)})   OK / Back: done"

    private fun enterResize() {
        if (camStripRow == null) { CamerasActivity.pickStripSize(this) { rebuild() }; return }
        resizeMode = true
        resizeHint = Ui.text(this, resizeText(), 16f, Color.BLACK, true).apply {
            background = Ui.rounded(this@MainActivity, LauncherTheme.accent, 20)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        root.addView(resizeHint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(28)
        })
    }

    private fun exitResize() {
        if (!resizeMode) return
        resizeMode = false
        resizeHint?.let { root.removeView(it) }
        resizeHint = null
        builtRevision = BasePrefs.revision
    }

    // Apps -------------------------------------------------------------------
    private fun buildFavorites(content: LinearLayout) {
        var apps = BasePrefs.favorites(this).mapNotNull { AppRepository.find(this, it) }
        val fallback = apps.isEmpty()
        if (fallback) apps = AppRepository.visible(this).sortedByDescending { it.isTvApp }.take(12)
        if (apps.isEmpty()) return
        section(content, if (fallback) "Your apps  ·  Menu on an app → Add to favorites" else "Your apps")
        val cols = BasePrefs.favColumns(this)
        if (BasePrefs.favLayout(this) == "row") {
            val row = hRow(content)
            val w = (usableWidth() - dp(16) * (cols - 1)) / cols
            apps.forEachIndexed { i, app -> row.addView(appTile(app, w, "fav:").also { gap(it, i == apps.size - 1) }) }
        } else {
            val maxRows = BasePrefs.favMaxRows(this)
            val shown = if (maxRows > 0) apps.take(cols * maxRows) else apps
            appGrid(content, shown, cols, "fav:")
        }
    }

    private fun buildInputs(content: LinearLayout) {
        if (!BasePrefs.showInputs(this)) return
        val inputs = TvInputs.list(this)
        if (inputs.isEmpty()) return
        section(content, getString(R.string.inputs_label))
        val row = hRow(content)
        inputs.forEachIndexed { i, input ->
            row.addView(card(dp(180), dp(76)).apply {
                tag = "in:${input.id}"
                addView(Ui.text(this@MainActivity, input.displayName(this@MainActivity), 17f, bold = true).apply {
                    gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                }, FrameLayout.LayoutParams(-1, -1))
                gap(this, i == inputs.size - 1)
                setOnClickListener {
                    if (!TvInputs.open(this@MainActivity, input.id))
                        Ui.toast(this@MainActivity, "Can't open ${input.displayName(this@MainActivity)} on this TV")
                }
                setOnLongClickListener { TvInputs.showRenameDialog(this@MainActivity, input) { rebuild() }; true }
            })
        }
    }

    /**
     * All-apps grid. Tiles share each row by weight, so they always fit the
     * screen exactly — no tile is ever pushed off the side or cut off.
     */
    private fun buildAllApps(content: LinearLayout, showHeader: Boolean) {
        val apps = AppRepository.visible(this)
        if (showHeader) section(content, getString(R.string.apps_label) + "  ·  ${apps.size}")
        if (apps.isEmpty()) {
            content.addView(Ui.text(this, getString(R.string.no_apps), LauncherTheme.BODY_SP, LauncherTheme.dimText))
            return
        }
        appGrid(content, apps, BasePrefs.appColumns(this), "app:")
    }

    /** Tiles share each row by weight, so they always fit the screen exactly. */
    private fun appGrid(content: LinearLayout, apps: List<LauncherApp>, cols: Int, prefix: String) {
        val g = dp(16)
        val w = (usableWidth() - g * (cols - 1)) / cols
        apps.chunked(cols).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                clipChildren = false; clipToPadding = false
                weightSum = cols.toFloat()
            }
            chunk.forEachIndexed { i, app ->
                val tile = appTile(app, w, prefix)
                tile.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < cols - 1) marginEnd = g }
                row.addView(tile)
            }
            content.addView(row)
        }
    }

    /** One app tile in the chosen style. [w] = tile width in px. */
    private fun appTile(app: LauncherApp, w: Int, prefix: String): View {
        val style = BasePrefs.tileStyle(this)
        val labels = BasePrefs.showAppLabels(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(w, -2)
            clipChildren = false
        }
        val banner = if (style == "banner") AppRepository.banner(this, app) else null
        val icon = AppRepository.icon(this, app)
        val c: FrameLayout
        var labelInside = false
        when {
            style == "round" -> {
                val s = (w * 0.72f).toInt()
                c = card(s, s, radius = 999)
                c.addView(ImageView(this).apply {
                    setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(s * 6 / 10, s * 6 / 10, Gravity.CENTER))
            }
            banner != null -> {
                c = card(-1, w * 9 / 16)
                val bw = banner.intrinsicWidth; val bh = banner.intrinsicHeight
                val ratio = if (bw > 0 && bh > 0) bw.toFloat() / bh else 16f / 9f
                c.addView(ImageView(this).apply {
                    setImageDrawable(banner)
                    // Real 16:9 banners fill the tile; odd-shaped ones are shown whole.
                    scaleType = if (ratio in 1.6f..1.9f) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(-1, -1))
            }
            else -> {
                val hh = if (style == "icon") (w * 0.8f).toInt() else w * 9 / 16
                c = card(-1, hh)
                labelInside = style != "icon" || !labels
                val inner = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val isz = (hh * if (labelInside) 0.5f else 0.62f).toInt()
                inner.addView(ImageView(this).apply {
                    setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(isz, isz)
                })
                if (labelInside) inner.addView(Ui.text(this, app.label, LauncherTheme.APP_LABEL_SP, bold = true).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(6), 0, 0)
                })
                c.addView(inner, FrameLayout.LayoutParams(-1, -1))
            }
        }
        c.tag = prefix + app.packageName
        c.setOnClickListener { if (!AppRepository.launch(this, app)) Ui.toast(this, "Can't open ${app.label}") }
        c.setOnLongClickListener {
            // Hold OK on a favorite = pick it up and drag it; elsewhere = options.
            if (prefix == "fav:" && BasePrefs.isFavorite(this, app.packageName)) enterMove(app.packageName) else appMenu(app)
            true
        }
        col.addView(c)
        if (labels && !labelInside) col.addView(Ui.text(this, app.label, 13f, LauncherTheme.softText).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            if (!LauncherTheme.light) setShadowLayer(4f, 0f, 1f, Color.BLACK)
            setPadding(dp(2), dp(8), dp(2), dp(2))
        })
        return col
    }

    private fun appMenu(app: LauncherApp) {
        val fav = BasePrefs.isFavorite(this, app.packageName)
        val items = mutableListOf(if (fav) "Remove from favorites" else "Add to favorites")
        if (fav) { items += "Move left"; items += "Move right" }
        items += listOf("Hide from home", "Map to a remote button…", "Customize home…", "App info", "Uninstall")
        Ui.choose(this, app.label, items) { w ->
            when (items[w]) {
                "Add to favorites", "Remove from favorites" -> BasePrefs.toggleFavorite(this, app.packageName)
                "Move left" -> BasePrefs.moveFavorite(this, app.packageName, -1)
                "Move right" -> BasePrefs.moveFavorite(this, app.packageName, 1)
                "Hide from home" -> { BasePrefs.setHidden(this, app.packageName, true)
                    Ui.toast(this, "Hidden — unhide in Settings → Hidden apps") }
                "Map to a remote button…" -> mapAppToButton(app)
                "Customize home…" -> startActivity(Intent(this, CustomizeActivity::class.java))
                "App info" -> AppRepository.openAppInfo(this, app.packageName)
                "Uninstall" -> AppRepository.uninstall(this, app.packageName)
            }
            if (resumed) rebuild()
        }
    }

    private fun mapAppToButton(app: LauncherApp) {
        val keys = RemoteKeys.allKeys(this)
        Ui.choose(this, "Open ${app.label} with…", keys.map { it.name }) { k ->
            val presses = BasePrefs.Press.values()
            Ui.choose(this, keys[k].name, presses.map { it.label }) { p ->
                BasePrefs.setKey(this, keys[k].code, presses[p], RemoteKeys.APP, app.packageName)
                Ui.toast(this, "${keys[k].name} (${presses[p].label.lowercase()}) → ${app.label}")
            }
        }
    }

    // ================================================================ keys
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (movingPkg != null) {
            val cols = if (BasePrefs.favLayout(this) == "row") 0 else BasePrefs.favColumns(this)
            val code = event.keyCode
            val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    code == KeyEvent.KEYCODE_DPAD_LEFT -> moveBy(-1)
                    code == KeyEvent.KEYCODE_DPAD_RIGHT -> moveBy(1)
                    code == KeyEvent.KEYCODE_DPAD_UP -> if (cols > 0) moveBy(-cols)
                    code == KeyEvent.KEYCODE_DPAD_DOWN -> if (cols > 0) moveBy(cols)
                    ok && event.repeatCount > 0 && !moveLongHandled -> {
                        moveLongHandled = true
                        val pkg = movingPkg
                        exitMove()
                        pkg?.let { AppRepository.find(this, it) }?.let { appMenu(it) }
                    }
                    code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU -> exitMove()
                }
            } else if (event.action == KeyEvent.ACTION_UP && ok) {
                if (moveLongHandled) moveLongHandled = false   // release of the hold that picked it up
                else exitMove()                                // short OK = drop
            }
            return true
        }
        // No wrap-around: ◀ / ▶ at the end of a row stays put instead of jumping
        // to another row or the top bar.
        if (event.action == KeyEvent.ACTION_DOWN && !resizeMode &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val cur = currentFocus
            if (cur != null) {
                val dir = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) View.FOCUS_LEFT else View.FOCUS_RIGHT
                val next = android.view.FocusFinder.getInstance().findNextFocus(root, cur, dir)
                if (next == null || !sameRow(cur, next)) return true
            }
        }
        if (resizeMode) {
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { BasePrefs.setCamStripHeightDp(this, BasePrefs.camStripHeightDp(this) + 20); applyCameraSize() }
                KeyEvent.KEYCODE_DPAD_DOWN -> { BasePrefs.setCamStripHeightDp(this, maxOf(80, BasePrefs.camStripHeightDp(this) - 20)); applyCameraSize() }
                KeyEvent.KEYCODE_DPAD_LEFT -> { BasePrefs.setCamStripColumns(this, BasePrefs.camStripColumns(this) + 1); applyCameraSize() }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { BasePrefs.setCamStripColumns(this, BasePrefs.camStripColumns(this) - 1); applyCameraSize() }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> exitResize()
            }
            return true
        }
        // Accessibility service off → still honour remaps while home is in front.
        if (HomeGuardService.instance == null && engine.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** True when [b] sits on the same visual row as [a] (their vertical extents overlap). */
    private fun sameRow(a: View, b: View): Boolean {
        val ra = android.graphics.Rect(); val rb = android.graphics.Rect()
        a.getGlobalVisibleRect(ra); b.getGlobalVisibleRect(rb)
        if (ra.isEmpty || rb.isEmpty) return true
        val overlap = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top)
        return overlap > minOf(ra.height(), rb.height()) / 3
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                val tag = currentFocus?.tag as? String
                when {
                    tag == null -> startActivity(Intent(this, SettingsActivity::class.java))
                    tag.startsWith("app:") || tag.startsWith("fav:") ->
                        AppRepository.find(this, tag.substringAfter(':'))?.let { appMenu(it) }
                    tag.startsWith("cam:") && tag != "cam:add" ->
                        tag.substringAfter(':').toIntOrNull()?.let { cameraMenu(it) }
                    tag.startsWith("ws:") && tag.count { it == ':' } >= 2 && !tag.endsWith(":more") && !tag.endsWith(":empty") ->
                        WatchStore.get(this, tag.substringAfter(':').substringAfter(':'))?.let { WatchActivity.itemMenu(this, it) { rebuild() } }
                    tag.startsWith("cw:") || tag.startsWith("up:") ->
                        WatchStore.get(this, tag.substringAfter(':'))?.let { WatchActivity.itemMenu(this, it) { rebuild() } }
                    (tag.startsWith("ft:") || tag.startsWith("ws:")) && tag.count { it == ':' } >= 2 ->
                        WatchStore.get(this, tag.substringAfter(':').substringAfter(':'))?.let { WatchActivity.itemMenu(this, it) { rebuild() } }
                            ?: startActivity(Intent(this, CustomizeActivity::class.java))
                    else -> startActivity(Intent(this, CustomizeActivity::class.java))
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (tab != "home") { tab = "home"; rebuild() }
                else { scroll?.smoothScrollTo(0, 0); firstFocus?.requestFocus() }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
