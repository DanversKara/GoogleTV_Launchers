package com.moomoo.tvlauncherbase

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * All TV Launcher settings in one SharedPreferences file.
 *
 * Sections: anti-takeover, home screen (favorites / hidden / look),
 * cameras (MotionEye streams), floating overlays, and the button
 * remapper (short / long / double press per key).
 */
object BasePrefs {
    private const val PREFS = "tv_launcher_base"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Bumps on every write so the home screen knows when to rebuild. */
    @Volatile var revision = 0L
        private set

    private fun edit(c: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        p(c).edit().apply(block).apply()
        revision++
        Backup.schedule(c)
    }

    // ---------------------------------------------------------------- anti-takeover
    fun guardEnabled(c: Context): Boolean = p(c).getBoolean("guard_enabled", true)
    fun setGuardEnabled(c: Context, v: Boolean) = edit(c) { putBoolean("guard_enabled", v) }

    fun bootOnPower(c: Context): Boolean = p(c).getBoolean("boot_on_power", true)
    fun setBootOnPower(c: Context, v: Boolean) = edit(c) { putBoolean("boot_on_power", v) }

    /** Extra stock-launcher packages the user adds for their TV model. */
    fun extraGuardPackages(c: Context): List<String> {
        val raw = p(c).getString("extra_guard_pkgs", "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    fun setExtraGuardPackages(c: Context, v: String) = edit(c) { putString("extra_guard_pkgs", v) }

    fun hideGoogleLauncher(c: Context): Boolean = p(c).getBoolean("hide_google", false)
    fun setHideGoogleLauncher(c: Context, v: Boolean) = edit(c) { putBoolean("hide_google", v) }

    fun setupDone(c: Context): Boolean = p(c).getBoolean("setup_done", false)
    fun setSetupDone(c: Context) = edit(c) { putBoolean("setup_done", true) }

    // ---------------------------------------------------------------- inputs
    fun inputName(c: Context, inputId: String, fallback: String): String =
        p(c).getString("input_name_$inputId", null) ?: fallback
    fun setInputName(c: Context, inputId: String, name: String) =
        edit(c) { putString("input_name_$inputId", name) }

    fun showInputs(c: Context): Boolean = p(c).getBoolean("show_inputs", true)
    fun setShowInputs(c: Context, v: Boolean) = edit(c) { putBoolean("show_inputs", v) }
    /** Also list app-provided inputs (Xumo Play…) in the TV inputs row; off = HDMI / hardware only. */
    fun showAppInputs(c: Context): Boolean = p(c).getBoolean("show_app_inputs", false)
    fun setShowAppInputs(c: Context, v: Boolean) = edit(c) { putBoolean("show_app_inputs", v) }

    // ---------------------------------------------------------------- home screen
    private fun strList(c: Context, key: String): MutableList<String> {
        val raw = p(c).getString(key, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw); MutableList(a.length()) { a.getString(it) }
        } catch (e: Exception) { mutableListOf() }
    }
    private fun setStrList(c: Context, key: String, v: List<String>) =
        edit(c) { putString(key, JSONArray(v).toString()) }

    fun favorites(c: Context): MutableList<String> = strList(c, "favorites")
    fun setFavorites(c: Context, v: List<String>) = setStrList(c, "favorites", v.distinct())
    fun isFavorite(c: Context, pkg: String) = pkg in favorites(c)
    fun toggleFavorite(c: Context, pkg: String) {
        val f = favorites(c); if (!f.remove(pkg)) f.add(pkg); setFavorites(c, f)
    }
    /** Moves a favorite left (-1) or right (+1). */
    fun moveFavorite(c: Context, pkg: String, delta: Int) {
        val f = favorites(c); val i = f.indexOf(pkg); if (i < 0) return
        val j = (i + delta).coerceIn(0, f.size - 1); if (i == j) return
        f.removeAt(i); f.add(j, pkg); setFavorites(c, f)
    }

    /** Moves a favorite to position [to] (used by drag-to-reorder). */
    fun moveFavoriteTo(c: Context, pkg: String, to: Int) {
        val f = favorites(c); val i = f.indexOf(pkg); if (i < 0) return
        val j = to.coerceIn(0, f.size - 1); if (i == j) return
        f.removeAt(i); f.add(j, pkg); setFavorites(c, f)
    }

    fun hiddenApps(c: Context): MutableList<String> = strList(c, "hidden_apps")
    fun setHidden(c: Context, pkg: String, hidden: Boolean) {
        val h = hiddenApps(c); h.remove(pkg); if (hidden) h.add(pkg); setStrList(c, "hidden_apps", h)
    }

    /** Include phone-style (non-TV) apps in the grid. */
    fun showAllApps(c: Context): Boolean = p(c).getBoolean("show_all_apps", true)
    fun setShowAllApps(c: Context, v: Boolean) = edit(c) { putBoolean("show_all_apps", v) }


    fun clock24h(c: Context): Boolean = p(c).getBoolean("clock_24h", false)
    fun setClock24h(c: Context, v: Boolean) = edit(c) { putBoolean("clock_24h", v) }

    fun appTileBanners(c: Context): Boolean = p(c).getBoolean("app_banners", true)
    fun setAppTileBanners(c: Context, v: Boolean) = edit(c) { putBoolean("app_banners", v) }

    // ---------------------------------------------------------------- theme & design
    fun themeId(c: Context): String = p(c).getString("theme", "gtv") ?: "gtv"
    fun accentHex(c: Context): String? = p(c).getString("accent_hex", null)
    fun setAccentHex(c: Context, v: String?) = edit(c) { if (v == null) remove("accent_hex") else putString("accent_hex", v) }
    fun cornerStyle(c: Context): String? = p(c).getString("corner_style", null)
    fun setCornerStyle(c: Context, v: String) = edit(c) { putString("corner_style", v) }
    fun focusStyleOverride(c: Context): String? = p(c).getString("focus_style", null)
    fun setFocusStyle(c: Context, v: String) = edit(c) { putString("focus_style", v) }
    fun fontOverride(c: Context): String? = p(c).getString("font", null)
    fun setFont(c: Context, v: String) = edit(c) { putString("font", v) }
    fun compactDensity(c: Context): Boolean = p(c).getBoolean("compact", false)
    fun setCompactDensity(c: Context, v: Boolean) = edit(c) { putBoolean("compact", v) }
    /** Wallpaper blur radius (Android 12+): 0, 10, 25. */
    fun wallpaperBlur(c: Context): Int = p(c).getInt("wp_blur", 0)
    fun setWallpaperBlur(c: Context, v: Int) = edit(c) { putInt("wp_blur", v) }

    fun applyThemePreset(c: Context, id: String, wallpaper: String, dim: Int, blur: Int) = edit(c) {
        putString("theme", id)
        remove("accent_hex"); remove("corner_style"); remove("focus_style"); remove("font")
        remove("bg_effect"); remove("glow_mode")
        // A wallpaper YOU picked (Customize → Wallpaper) is kept when you change themes.
        if (!p(c).getBoolean("wp_user_set", false)) { putString("wp_mode", wallpaper); putInt("wp_dim", dim); putInt("wp_blur", blur) }
    }

    private val THEME_KEYS = listOf("theme", "accent_hex", "corner_style", "focus_style", "font", "compact",
        "wp_mode", "wp_url", "wp_color", "wp_dim", "wp_blur", "wp_user_set", "tile_style", "focus_zoom", "clock_style",
        "clock_24h", "clock_seconds", "date_order", "clock_greeting", "bg_effect", "glow_mode", "glow_pulse")

    /** Your saved themes: name -> JSON of the theme-related settings. */
    fun customThemes(c: Context): Map<String, String> = try {
        val o = JSONObject(p(c).getString("custom_themes", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) { emptyMap() }

    fun saveCustomTheme(c: Context, name: String) {
        val all = p(c).all
        val t = JSONObject()
        for (k in THEME_KEYS) all[k]?.let { t.put(k, it) }
        val o = try { JSONObject(p(c).getString("custom_themes", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        o.put(name, t.toString())
        edit(c) { putString("custom_themes", o.toString()) }
    }

    fun applyCustomTheme(c: Context, name: String) {
        val json = customThemes(c)[name] ?: return
        val t = JSONObject(json)
        edit(c) {
            for (k in THEME_KEYS) remove(k)
            for (k in t.keys()) when (val v = t.get(k)) {
                is Boolean -> putBoolean(k, v); is Int -> putInt(k, v); is String -> putString(k, v)
            }
        }
    }

    fun deleteCustomTheme(c: Context, name: String) {
        val o = try { JSONObject(p(c).getString("custom_themes", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        o.remove(name); edit(c) { putString("custom_themes", o.toString()) }
    }

    // ---------------------------------------------------------------- clock
    /** "stacked", "single", "big", "time", "hidden". */
    fun clockStyle(c: Context): String = p(c).getString("clock_style", "stacked") ?: "stacked"
    fun setClockStyle(c: Context, v: String) = edit(c) { putString("clock_style", v) }
    fun clockSeconds(c: Context): Boolean = p(c).getBoolean("clock_seconds", false)
    fun setClockSeconds(c: Context, v: Boolean) = edit(c) { putBoolean("clock_seconds", v) }
    /** "mdy" (November 3), "dmy" (3 November), "num" (11/3). */
    fun dateOrder(c: Context): String = p(c).getString("date_order", "mdy") ?: "mdy"
    fun setDateOrder(c: Context, v: String) = edit(c) { putString("date_order", v) }
    fun clockGreeting(c: Context): Boolean = p(c).getBoolean("clock_greeting", false)
    fun setClockGreeting(c: Context, v: Boolean) = edit(c) { putBoolean("clock_greeting", v) }

    // ---------------------------------------------------------------- weather
    fun weatherEnabled(c: Context): Boolean = p(c).getBoolean("weather_on", true)
    fun setWeatherEnabled(c: Context, v: Boolean) = edit(c) { putBoolean("weather_on", v) }
    fun weatherPlace(c: Context): Weather.Place? {
        val name = p(c).getString("weather_name", null) ?: return null
        val lat = p(c).getString("weather_lat", null)?.toDoubleOrNull() ?: return null
        val lon = p(c).getString("weather_lon", null)?.toDoubleOrNull() ?: return null
        return Weather.Place(name, lat, lon)
    }
    fun setWeatherPlace(c: Context, pl: Weather.Place) = edit(c) {
        putString("weather_name", pl.name); putString("weather_lat", pl.lat.toString()); putString("weather_lon", pl.lon.toString())
    }
    fun weatherFahrenheit(c: Context): Boolean = p(c).getBoolean("weather_f", Weather.defaultFahrenheit())
    fun setWeatherFahrenheit(c: Context, v: Boolean) = edit(c) { putBoolean("weather_f", v) }
    fun weatherAnimated(c: Context): Boolean = p(c).getBoolean("weather_anim", true)
    fun setWeatherAnimated(c: Context, v: Boolean) = edit(c) { putBoolean("weather_anim", v) }
    fun weatherDetails(c: Context): Boolean = p(c).getBoolean("weather_details", true)
    fun setWeatherDetails(c: Context, v: Boolean) = edit(c) { putBoolean("weather_details", v) }
    /** Last forecast JSON (written without bumping the layout revision). */
    fun weatherCache(c: Context): String? = p(c).getString("weather_cache", null)
    fun setWeatherCache(c: Context, v: String?) { p(c).edit().apply { if (v == null) remove("weather_cache") else putString("weather_cache", v) }.apply() }

    /** Header size: 85, 100, 115, 130 (%). The header also shrinks automatically to always fit. */
    fun headerSize(c: Context): Int = p(c).getInt("header_size", 100)
    fun setHeaderSize(c: Context, v: Int) = edit(c) { putInt("header_size", v) }
    /** Text size for the whole app: 90, 100, 115, 130 (%). */
    fun textSize(c: Context): Int = p(c).getInt("text_size", 100)
    fun setTextSize(c: Context, v: Int) = edit(c) { putInt("text_size", v) }

    /** Show the 🔍 Search button in the top bar. */
    fun showSearch(c: Context): Boolean = p(c).getBoolean("show_search", true)
    fun setShowSearch(c: Context, v: Boolean) = edit(c) { putBoolean("show_search", v) }
    /** Show the 📷 Cameras button in the top bar. */
    fun showCamButton(c: Context): Boolean = p(c).getBoolean("show_cam_btn", true)
    fun setShowCamButton(c: Context, v: Boolean) = edit(c) { putBoolean("show_cam_btn", v) }
    /** Show the ⧉ Floating cameras button in the top bar. */
    fun showFloatButton(c: Context): Boolean = p(c).getBoolean("show_float_btn", true)
    fun setShowFloatButton(c: Context, v: Boolean) = edit(c) { putBoolean("show_float_btn", v) }

    // ---------------------------------------------------------------- name
    /** The TV's name shown in the app (default "Kara's TV"). */
    fun displayName(c: Context): String = p(c).getString("display_name", null)?.takeIf { it.isNotBlank() } ?: "Kara's TV"
    fun setDisplayName(c: Context, v: String) = edit(c) { putString("display_name", v.trim()) }
    /** Where the name shows on home: left, center, under, footer, hidden. */
    fun nameSpot(c: Context): String = p(c).getString("name_spot", "left") ?: "left"
    fun setNameSpot(c: Context, v: String) = edit(c) { putString("name_spot", v) }
    fun nameShimmer(c: Context): Boolean = p(c).getBoolean("name_shimmer", true)
    fun setNameShimmer(c: Context, v: Boolean) = edit(c) { putBoolean("name_shimmer", v) }

    // ---------------------------------------------------------------- effects & discover
    /** Animated background: none, aurora, particles, starfield, waves, bokeh, grid. */
    fun bgEffect(c: Context): String? = p(c).getString("bg_effect", null)
    fun setBgEffect(c: Context, v: String) = edit(c) { putString("bg_effect", v) }
    /** Hover glow: "accent", "app" (each app's own color), "off". */
    fun glowMode(c: Context): String? = p(c).getString("glow_mode", null)
    fun setGlowMode(c: Context, v: String) = edit(c) { putString("glow_mode", v) }
    fun glowPulse(c: Context): Boolean = p(c).getBoolean("glow_pulse", true)
    fun setGlowPulse(c: Context, v: Boolean) = edit(c) { putBoolean("glow_pulse", v) }
    /** Discover wallpaper sources. */
    fun discoverSource(c: Context, id: String): Boolean = p(c).getBoolean("disc_$id", true)
    fun setDiscoverSource(c: Context, id: String, v: Boolean) = edit(c) { putBoolean("disc_$id", v) }
    fun tmdbKey(c: Context): String = p(c).getString("tmdb_key", "") ?: ""
    fun setTmdbKey(c: Context, v: String) = edit(c) { putString("tmdb_key", v.trim()) }
    fun discoverCaption(c: Context): Boolean = p(c).getBoolean("disc_caption", true)
    fun setDiscoverCaption(c: Context, v: Boolean) = edit(c) { putBoolean("disc_caption", v) }

    // ---------------------------------------------------------------- backup
    fun autoBackup(c: Context): Boolean = p(c).getBoolean("auto_backup", true)
    fun setAutoBackup(c: Context, v: Boolean) = edit(c) { putBoolean("auto_backup", v) }
    fun lastBackupAt(c: Context): Long = p(c).getLong("last_backup_at", 0L)
    internal fun markBackup(c: Context, t: Long) { p(c).edit().putLong("last_backup_at", t).apply() }

    // ---------------------------------------------------------------- home customization
    /** "gtv" = Google TV style (hero + wallpaper), "classic" = plain rows. */
    fun homeStyle(c: Context): String = p(c).getString("home_style", "gtv") ?: "gtv"
    fun setHomeStyle(c: Context, v: String) = edit(c) { putString("home_style", v) }

    /** "auto" (from what you watch), "gradient", "color", "url", "none". */
    fun wallpaperMode(c: Context): String = p(c).getString("wp_mode", "auto") ?: "auto"
    fun setWallpaperMode(c: Context, v: String) = edit(c) { putString("wp_mode", v); putBoolean("wp_user_set", true) }
    /** True once you've picked a wallpaper yourself — themes then leave it alone. */
    fun wallpaperUserSet(c: Context): Boolean = p(c).getBoolean("wp_user_set", false)
    /** Go back to "each theme brings its own wallpaper". */
    fun useThemeWallpaper(c: Context, mode: String, dim: Int, blur: Int) = edit(c) {
        putBoolean("wp_user_set", false); putString("wp_mode", mode); putInt("wp_dim", dim); putInt("wp_blur", blur)
    }
    fun wallpaperUrl(c: Context): String = p(c).getString("wp_url", "") ?: ""
    fun setWallpaperUrl(c: Context, v: String) = edit(c) { putString("wp_url", v) }
    fun wallpaperColor(c: Context): String = p(c).getString("wp_color", "#1B2735") ?: "#1B2735"
    fun setWallpaperColor(c: Context, v: String) = edit(c) { putString("wp_color", v) }
    /** How dark the scrim over the wallpaper is, 0..90 %. */
    fun wallpaperDim(c: Context): Int = p(c).getInt("wp_dim", 55)
    fun setWallpaperDim(c: Context, v: Int) = edit(c) { putInt("wp_dim", v.coerceIn(0, 90)) }

    /** Default order: cameras on top, the compact watch strip under them, then apps. */
    val ALL_SECTIONS = listOf("cameras", "watchstrip", "favorites", "hero", "watching", "upcoming", "inputs", "apps")
    private val DEFAULT_HIDDEN = listOf("hero", "watching", "upcoming")
    private const val LAYOUT_VERSION = 2

    /** One-time move to the v2 layout (cameras first + compact strip). */
    private fun migrateLayout(c: Context) {
        if (p(c).getInt("layout_v", 1) >= LAYOUT_VERSION) return
        p(c).edit().remove("sections").remove("hidden_sections").putInt("layout_v", LAYOUT_VERSION).apply()
        revision++
    }

    fun sectionLabel(id: String) = when (id) {
        "watchstrip" -> "Watching & upcoming strip (compact)"
        "hero" -> "Featured cards (top show of each: watching · soon · upcoming)"
        "watching" -> "Continue watching"
        "upcoming" -> "Upcoming episodes"
        "favorites" -> "Your apps (favorites)"
        "cameras" -> "Cameras"
        "inputs" -> "TV inputs"
        "apps" -> "All apps"
        else -> id
    }
    /** Section order (all sections, shown or not). */
    fun sectionOrder(c: Context): MutableList<String> {
        migrateLayout(c)
        val saved = strList(c, "sections").filter { it in ALL_SECTIONS }.toMutableList()
        for (s in ALL_SECTIONS) if (s !in saved) saved += s
        return saved
    }
    fun setSectionOrder(c: Context, v: List<String>) = setStrList(c, "sections", v)
    fun hiddenSections(c: Context): MutableList<String> {
        migrateLayout(c)
        return if (p(c).contains("hidden_sections")) strList(c, "hidden_sections") else DEFAULT_HIDDEN.toMutableList()
    }
    fun setSectionShown(c: Context, id: String, shown: Boolean) {
        val h = hiddenSections(c); h.remove(id); if (!shown) h += id; setStrList(c, "hidden_sections", h)
    }
    fun sectionShown(c: Context, id: String) = id !in hiddenSections(c)

    /** App tile style: "banner", "icon", "round". */
    fun tileStyle(c: Context): String = p(c).getString("tile_style", "banner") ?: "banner"
    fun setTileStyle(c: Context, v: String) = edit(c) { putString("tile_style", v) }
    /** App tiles per row in the grid, 4..8. */
    fun appColumns(c: Context): Int = p(c).getInt("app_cols", 6)
    fun setAppColumns(c: Context, v: Int) = edit(c) { putInt("app_cols", v.coerceIn(3, 9)) }
    /** "Your apps" (favorites): "grid" wraps into rows, "row" is one sideways-scrolling line. */
    fun favLayout(c: Context): String = p(c).getString("fav_layout", "grid") ?: "grid"
    fun setFavLayout(c: Context, v: String) = edit(c) { putString("fav_layout", v) }
    /** "Your apps" tiles per row (bigger = smaller tiles), 3..12. */
    fun favColumns(c: Context): Int = p(c).getInt("fav_cols", 8)
    fun setFavColumns(c: Context, v: Int) = edit(c) { putInt("fav_cols", v.coerceIn(3, 12)) }
    /** Max rows of "Your apps" in grid mode (0 = all). */
    fun favMaxRows(c: Context): Int = p(c).getInt("fav_rows", 2)
    fun setFavMaxRows(c: Context, v: Int) = edit(c) { putInt("fav_rows", v.coerceIn(0, 6)) }

    fun showAppLabels(c: Context): Boolean = p(c).getBoolean("app_labels", true)
    fun setShowAppLabels(c: Context, v: Boolean) = edit(c) { putBoolean("app_labels", v) }
    fun focusZoom(c: Context): Boolean = p(c).getBoolean("focus_zoom", true)
    fun setFocusZoom(c: Context, v: Boolean) = edit(c) { putBoolean("focus_zoom", v) }

    // --- compact watch strip
    /** "text" = titles only (smallest), "thumbs" = with small thumbnails. */
    fun stripStyle(c: Context): String = p(c).getString("strip_style", "text") ?: "text"
    fun setStripStyle(c: Context, v: String) = edit(c) { putString("strip_style", v) }
    /** Lines shown per column, 1..8. */
    fun stripRows(c: Context): Int = p(c).getInt("strip_rows", 3)
    fun setStripRows(c: Context, v: Int) = edit(c) { putInt("strip_rows", v.coerceIn(1, 8)) }
    /** Text size of the strip: 0 small, 1 medium, 2 large. */
    fun stripTextSize(c: Context): Int = p(c).getInt("strip_text", 1)
    fun setStripTextSize(c: Context, v: Int) = edit(c) { putInt("strip_text", v.coerceIn(0, 2)) }
    val STRIP_PANELS = listOf("continue", "soon", "later")
    fun stripPanelLabel(id: String) = when (id) {
        "continue" -> "Continue watching"
        "soon" -> "Airing soon"
        else -> "Upcoming episodes"
    }

    /** Episodes airing within this many hours go in "Airing soon"; later ones in "Upcoming". */
    fun soonHours(c: Context): Int = p(c).getInt("soon_hours", 48)
    fun setSoonHours(c: Context, v: Int) = edit(c) { putInt("soon_hours", v.coerceIn(1, 24 * 60)) }
    val SOON_OPTIONS = listOf(24 to "24 hours", 48 to "48 hours", 72 to "3 days", 168 to "1 week", 336 to "2 weeks")
    fun soonLabel(c: Context) = SOON_OPTIONS.firstOrNull { it.first == soonHours(c) }?.second ?: "${soonHours(c)} h"

    /** Featured row (3 cards): which cards show. */
    fun featureShown(c: Context, id: String) = p(c).getBoolean("feat_panel_$id", true)
    fun setFeatureShown(c: Context, id: String, v: Boolean) = edit(c) { putBoolean("feat_panel_$id", v) }
    fun stripPanelShown(c: Context, id: String) = p(c).getBoolean("strip_panel_$id", true)
    fun setStripPanelShown(c: Context, id: String, v: Boolean) = edit(c) { putBoolean("strip_panel_$id", v) }

    /** Size of the big card rows (Continue watching / Upcoming): "s", "m", "l". */
    fun mediaCardSize(c: Context): String = p(c).getString("media_card", "s") ?: "s"
    fun setMediaCardSize(c: Context, v: String) = edit(c) { putString("media_card", v) }
    /** Show artwork on the big card rows (off = text-only cards). */
    fun mediaCardImages(c: Context): Boolean = p(c).getBoolean("media_card_img", true)
    fun setMediaCardImages(c: Context, v: Boolean) = edit(c) { putBoolean("media_card_img", v) }

    /** Home strip / camera wall tiles: "fill" (crop, no bars) or "fit" (whole picture). */
    fun camTileFit(c: Context): String = p(c).getString("cam_tile_fit", "fill") ?: "fill"
    fun setCamTileFit(c: Context, v: String) = edit(c) { putString("cam_tile_fit", v) }

    fun resetHomeLayout(c: Context) = edit(c) {
        for (k in listOf("strip_style", "strip_rows", "strip_text", "strip_panel_continue", "strip_panel_soon",
            "strip_panel_later", "fav_layout", "fav_cols", "fav_rows", "media_card", "media_card_img", "cam_tile_fit", "home_style", "wp_mode", "wp_url", "wp_color", "wp_dim", "sections", "hidden_sections",
            "tile_style", "app_cols", "app_labels", "focus_zoom")) remove(k)
    }

    // ---------------------------------------------------------------- watching / upcoming
    /** Record what's playing (needs notification access) for Continue watching. */
    fun watchTracking(c: Context): Boolean = p(c).getBoolean("watch_tracking", true)
    fun setWatchTracking(c: Context, v: Boolean) = edit(c) { putBoolean("watch_tracking", v) }
    /** Look up next-episode dates + artwork on AniList / TVmaze. */
    fun showInfoOnline(c: Context): Boolean = p(c).getBoolean("show_info_online", true)
    fun setShowInfoOnline(c: Context, v: Boolean) = edit(c) { putBoolean("show_info_online", v) }
    /** Apps whose playback is never recorded (music etc.). */
    fun ignoredWatchApps(c: Context): MutableList<String> {
        val raw = p(c).getString("watch_ignored", null)
        return if (raw == null) mutableListOf(
            "com.spotify.tv.android", "com.spotify.music", "com.google.android.youtube.tvmusic",
            "com.amazon.music.tv", "com.pandora.android.atv", "com.google.android.apps.youtube.music"
        ) else strList(c, "watch_ignored")
    }
    fun setWatchIgnored(c: Context, pkg: String, ignored: Boolean) {
        val l = ignoredWatchApps(c); l.remove(pkg); if (ignored) l += pkg; setStrList(c, "watch_ignored", l)
    }

    // ---------------------------------------------------------------- cameras
    fun cameras(c: Context): MutableList<Camera> {
        val raw = p(c).getString("cameras", null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw)
            MutableList(a.length()) { Camera.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { mutableListOf() }
    }
    fun setCameras(c: Context, list: List<Camera>) =
        edit(c) { putString("cameras", JSONArray(list.map { it.toJson() }).toString()) }

    /** Height of the camera strip on the home screen, in dp. 0 = hidden. */
    fun camStripHeightDp(c: Context): Int = p(c).getInt("cam_strip_h", 220)
    fun setCamStripHeightDp(c: Context, v: Int) = edit(c) { putInt("cam_strip_h", v.coerceIn(0, 720)) }

    /** Camera tiles visible per row on the home strip (1..4). */
    fun camStripColumns(c: Context): Int = p(c).getInt("cam_strip_cols", 2)
    fun setCamStripColumns(c: Context, v: Int) = edit(c) { putInt("cam_strip_cols", v.coerceIn(1, 4)) }

    /** Show the camera name on each tile / floating window. */
    fun camShowLabels(c: Context): Boolean = p(c).getBoolean("cam_labels", true)
    fun setCamShowLabels(c: Context, v: Boolean) = edit(c) { putBoolean("cam_labels", v) }

    // ---------------------------------------------------------------- overlays
    /** Floating window picture fit: "snap" (default), "fill", "stretch", "fit". */
    fun overlayFit(c: Context): String = p(c).getString("ovl_fit", "snap") ?: "snap"
    fun setOverlayFit(c: Context, v: String) = edit(c) { putString("ovl_fit", v) }

    fun overlayOpacity(c: Context): Int = p(c).getInt("ovl_alpha", 100)
    fun setOverlayOpacity(c: Context, v: Int) = edit(c) { putInt("ovl_alpha", v.coerceIn(30, 100)) }

    /** Saved geometry of floating window slot [slot]: x, y, width (px). */
    fun overlayGeom(c: Context, slot: Int): IntArray? {
        val s = p(c).getString("ovl_geom_$slot", null) ?: return null
        val parts = s.split(",").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 3) parts.toIntArray() else null
    }
    fun setOverlayGeom(c: Context, slot: Int, x: Int, y: Int, w: Int) =
        edit(c) { putString("ovl_geom_$slot", "$x,$y,$w") }

    /** Floating windows open last time, as (slot, camera index) — the ⧉ button brings them all back. */
    fun overlaySet(c: Context): List<Pair<Int, Int>> =
        (p(c).getString("ovl_set", null) ?: "").split(",").mapNotNull { e ->
            val a = e.split(":").mapNotNull { it.toIntOrNull() }
            if (a.size == 2) a[0] to a[1] else null
        }
    fun setOverlaySet(c: Context, v: List<Pair<Int, Int>>) =
        edit(c) { putString("ovl_set", v.joinToString(",") { "${it.first}:${it.second}" }) }

    // ---------------------------------------------------------------- remapper
    fun remapEnabled(c: Context): Boolean = p(c).getBoolean("remap_enabled", true)
    fun setRemapEnabled(c: Context, v: Boolean) = edit(c) { putBoolean("remap_enabled", v) }

    fun longPressMs(c: Context): Int = p(c).getInt("long_ms", 550)
    fun setLongPressMs(c: Context, v: Int) = edit(c) { putInt("long_ms", v.coerceIn(300, 1500)) }

    fun doublePressMs(c: Context): Int = p(c).getInt("double_ms", 300)
    fun setDoublePressMs(c: Context, v: Int) = edit(c) { putInt("double_ms", v.coerceIn(150, 700)) }

    /** Keys the user taught the remapper with "Learn a button". */
    fun learnedKeys(c: Context): MutableList<Int> =
        strList(c, "learned_keys").mapNotNull { it.toIntOrNull() }.toMutableList()
    fun addLearnedKey(c: Context, code: Int) {
        val l = learnedKeys(c); if (code !in l) l.add(code)
        setStrList(c, "learned_keys", l.map { it.toString() })
    }
    fun removeLearnedKey(c: Context, code: Int) {
        val l = learnedKeys(c); l.remove(code)
        setStrList(c, "learned_keys", l.map { it.toString() })
        for (t in Press.values()) clearKey(c, code, t)
    }

    enum class Press(val id: String, val label: String) {
        SHORT("short", "Single press"), LONG("long", "Long press"), DOUBLE("double", "Double press")
    }

    /**
     * Action for [code] + [press]. Version-1 mappings ("key_<code>") are
     * read as the single-press mapping so upgrades keep working.
     */
    fun keyAction(c: Context, code: Int, press: Press): String? {
        val v = p(c).getString("key_${code}_${press.id}", null)
        if (v != null || press != Press.SHORT) return v
        return p(c).getString("key_$code", null)
    }
    fun keyArg(c: Context, code: Int, press: Press): String? {
        val v = p(c).getString("key_${code}_${press.id}_arg", null)
        if (v != null || press != Press.SHORT) return v
        return p(c).getString("key_app_$code", null)
    }
    fun setKey(c: Context, code: Int, press: Press, action: String, arg: String?) = edit(c) {
        putString("key_${code}_${press.id}", action)
        if (arg != null) putString("key_${code}_${press.id}_arg", arg)
        else remove("key_${code}_${press.id}_arg")
    }
    fun clearKey(c: Context, code: Int, press: Press) = edit(c) {
        remove("key_${code}_${press.id}"); remove("key_${code}_${press.id}_arg")
        if (press == Press.SHORT) { remove("key_$code"); remove("key_app_$code") }
    }

    /** Wipes every key mapping (learned keys stay listed). */
    fun resetAllKeys(c: Context) = edit(c) {
        for (k in p(c).all.keys) if (k.startsWith("key_")) remove(k)
    }

    // ---------------------------------------------------------------- backup
    fun exportJson(c: Context): String {
        val o = JSONObject()
        for ((k, v) in p(c).all) when (v) {
            is Boolean, is Int, is Long, is Float, is String -> o.put(k, v)
        }
        o.remove("last_backup_at"); o.remove("weather_cache")
        o.put("__watch", WatchStore.exportJson(c))
        o.put("__exported_at", System.currentTimeMillis())
        o.put("__version", 2)
        return o.toString(2)
    }
    fun importJson(c: Context, json: String): Boolean = try {
        val o = JSONObject(json)
        val watch = o.optString("__watch", "")
        Backup.suspended = true
        edit(c) {
            clear()
            for (k in o.keys()) if (!k.startsWith("__")) when (val v = o.get(k)) {
                is Boolean -> putBoolean(k, v)
                is Int -> putInt(k, v)
                is Long -> putLong(k, v)
                is Double -> putFloat(k, v.toFloat())
                is String -> putString(k, v)
            }
        }
        if (watch.isNotEmpty()) WatchStore.importJson(c, watch)
        Backup.suspended = false
        true
    } catch (e: Exception) { Backup.suspended = false; false }
}

/** One camera stream (MotionEye or anything that serves MJPEG / JPEG / video). */
data class Camera(
    val name: String,
    val url: String,
    val type: String = TYPE_MJPEG,
    val user: String = "",
    val pass: String = "",
    val onHome: Boolean = true,
    /** Snapshot mode refresh interval in ms. */
    val refreshMs: Int = 1000,
    /** Rotation in degrees (0/90/180/270). */
    val rotation: Int = 0
) {
    companion object {
        const val TYPE_MJPEG = "mjpeg"
        const val TYPE_SNAPSHOT = "snapshot"
        const val TYPE_VIDEO = "video"
        val TYPES = listOf(TYPE_MJPEG, TYPE_SNAPSHOT, TYPE_VIDEO)
        fun typeLabel(t: String) = when (t) {
            TYPE_SNAPSHOT -> "JPEG snapshot (polled)"
            TYPE_VIDEO -> "Video stream (RTSP / HLS / MP4)"
            else -> "MJPEG stream (MotionEye default)"
        }
        fun fromJson(o: JSONObject) = Camera(
            o.optString("name", "Camera"), o.optString("url", ""),
            o.optString("type", TYPE_MJPEG), o.optString("user", ""), o.optString("pass", ""),
            o.optBoolean("onHome", true), o.optInt("refreshMs", 1000), o.optInt("rotation", 0)
        )
    }
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("url", url).put("type", type).put("user", user)
        .put("pass", pass).put("onHome", onHome).put("refreshMs", refreshMs)
        .put("rotation", rotation)
}
