package com.moomoo.tvlauncherbase

/**
 * CUSTOMIZE HOME: look, wallpaper, which rows show and in what order,
 * and how app tiles look. Changes apply as soon as you go back home.
 */
class CustomizeActivity : RowListActivity() {

    override val screenTitle = "Customize home"
    override val screenDesc = "Press Back to see your changes. Tip: Menu on the home screen opens this too."

    private val wallpaperModes = listOf(
        "discover" to "Discover — new movies, shows & anime (rotating)",
        "auto" to "From what you're watching (Google TV style)",
        "slideshow" to "Slideshow of your shows' artwork",
        "animated" to "Animated gradient (theme colors)",
        "gradient" to "Theme gradient",
        "color" to "Solid color",
        "url" to "Your own image (web link)",
        "none" to "Plain dark"
    )
    private val colors = listOf(
        "Midnight" to "#1B2735", "Charcoal" to "#202124", "Deep purple" to "#2A1B3D", "Forest" to "#14281D",
        "Wine" to "#3A1420", "Ocean" to "#0B2A3C", "Black" to "#000000"
    )
    private val tileStyles = listOf(
        "banner" to "TV banners (like Google TV)", "icon" to "Icon cards", "round" to "Round icons"
    )

    override fun rows(): List<Row> {
        val c = this
        val out = mutableListOf<Row>()
        out += Row("Name", header = true)
        out += Row("Name", BasePrefs.displayName(c)) {
            Ui.prompt(c, "What should the TV be called?", BasePrefs.displayName(c), "Kara's TV") {
                if (it.isNotBlank()) { BasePrefs.setDisplayName(c, it); refresh() }
            }
        }
        val spots = listOf("left" to "Header — far left", "center" to "Header — between Float and the weather",
            "under" to "Under the top bar (between Search and Cameras)", "footer" to "Footer — bottom of home", "hidden" to "Hidden")
        out += Row("Where the name shows", spots.first { it.first == BasePrefs.nameSpot(c) }.second) {
            Ui.choose(c, "Show the name…", spots.map { it.second }) { BasePrefs.setNameSpot(c, spots[it].first); refresh() }
        }
        out += Row("Name shimmer", if (BasePrefs.nameShimmer(c)) "On — glowing, animated" else "Off — plain glow") {
            BasePrefs.setNameShimmer(c, !BasePrefs.nameShimmer(c)); refresh()
        }

        out += Row("Theme", header = true)
        val pr = LauncherTheme.presetById(BasePrefs.themeId(c))
        out += Row("Theme preset", "${pr.name} — ${pr.blurb}") {
            val ps = LauncherTheme.PRESETS
            Ui.choose(c, "Choose a theme", ps.map { "${it.name}  —  ${it.blurb}" }) {
                LauncherTheme.applyPreset(c, ps[it].id)
                if (BasePrefs.wallpaperUserSet(c)) Ui.toast(c, "Kept your wallpaper choice")
                recreate()
            }
        }
        val customs = BasePrefs.customThemes(c)
        out += Row("My themes", if (customs.isEmpty()) "Save your current look to reuse it later" else customs.keys.joinToString(", ")) {
            val items = customs.keys.map { "Apply “$it”" } + "+ Save current look as a new theme" + customs.keys.map { "Delete “$it”" }
            Ui.choose(c, "My themes", items) { w ->
                val names = customs.keys.toList()
                when {
                    w < names.size -> { BasePrefs.applyCustomTheme(c, names[w]); recreate() }
                    w == names.size -> Ui.prompt(c, "Name this theme", "My theme ${names.size + 1}") {
                        if (it.isNotBlank()) { BasePrefs.saveCustomTheme(c, it); Ui.toast(c, "Saved “$it”"); refresh() }
                    }
                    else -> { BasePrefs.deleteCustomTheme(c, names[w - names.size - 1]); refresh() }
                }
            }
        }

        out += Row("Design (overrides the preset)", header = true)
        out += Row("Accent color", LauncherTheme.ACCENTS.firstOrNull { it.second.equals(BasePrefs.accentHex(c), true) }?.first
            ?: "From theme") {
            Ui.choose(c, "Accent color", listOf("From theme") + LauncherTheme.ACCENTS.map { it.first }) {
                BasePrefs.setAccentHex(c, if (it == 0) null else LauncherTheme.ACCENTS[it - 1].second); recreate()
            }
        }
        fun pick(title: String, opts: List<Pair<String, String>>, cur: String, set: (String) -> Unit) =
            Row(title, opts.firstOrNull { it.first == cur }?.second ?: cur) {
                Ui.choose(c, title, opts.map { it.second }) { set(opts[it].first); recreate() }
            }
        out += pick("Focus effect", LauncherTheme.FOCUS_STYLES, LauncherTheme.focusStyle) { BasePrefs.setFocusStyle(c, it) }
        out += pick("Corners", LauncherTheme.CORNERS, LauncherTheme.corner) { BasePrefs.setCornerStyle(c, it) }
        out += pick("Font", LauncherTheme.FONTS, LauncherTheme.font) { BasePrefs.setFont(c, it) }
        out += pick("Animated background", LauncherTheme.EFFECTS, LauncherTheme.effect) { BasePrefs.setBgEffect(c, it) }
        out += pick("Hover glow", LauncherTheme.GLOWS, LauncherTheme.glowMode) { BasePrefs.setGlowMode(c, it) }
        out += Row("Glow pulse", if (BasePrefs.glowPulse(c)) "On — gently breathes" else "Off — steady") {
            BasePrefs.setGlowPulse(c, !BasePrefs.glowPulse(c)); refresh()
        }
        out += Row("Text size", "${BasePrefs.textSize(c)}% — makes all text bigger or smaller") {
            val o = listOf(90 to "Small (90%)", 100 to "Normal (100%)", 115 to "Large (115%)", 130 to "Extra large (130%)")
            Ui.choose(c, "Text size", o.map { it.second }) { BasePrefs.setTextSize(c, o[it].first); recreate() }
        }
        out += Row("Header size", "${BasePrefs.headerSize(c)}% — the top bar always shrinks to fit, so nothing is ever hidden") {
            val o = listOf(85 to "Small (85%)", 100 to "Normal (100%)", 115 to "Large (115%)", 130 to "Extra large (130%)")
            Ui.choose(c, "Header size", o.map { it.second }) { BasePrefs.setHeaderSize(c, o[it].first); refresh() }
        }
        out += Row("Density", if (BasePrefs.compactDensity(c)) "Compact — everything ~15% smaller" else "Comfortable") {
            BasePrefs.setCompactDensity(c, !BasePrefs.compactDensity(c)); refresh()
        }

        out += Row("Wallpaper", header = true)
        out += Row("Wallpaper", wallpaperModes.firstOrNull { it.first == BasePrefs.wallpaperMode(c) }?.second ?: "") {
            Ui.choose(c, "Wallpaper", wallpaperModes.map { it.second }) { i ->
                val mode = wallpaperModes[i].first
                BasePrefs.setWallpaperMode(c, mode)
                when (mode) {
                    "url" -> Ui.prompt(c, "Image link (jpg/png)", BasePrefs.wallpaperUrl(c).ifEmpty { "https://" }) {
                        BasePrefs.setWallpaperUrl(c, it); refresh()
                    }
                    "color" -> Ui.choose(c, "Color", colors.map { it.first }) { j ->
                        BasePrefs.setWallpaperColor(c, colors[j].second); refresh()
                    }
                }
                refresh()
            }
        }
        out += Row("Wallpaper source", wallpaperStatus()) {
            Ui.choose(c, "Wallpaper", listOf("Test connections", "Use each theme's own wallpaper again")) {
                if (it == 0) ConnTest.run(c) else {
                    val p = LauncherTheme.presetById(BasePrefs.themeId(c))
                    BasePrefs.useThemeWallpaper(c, p.wallpaper, p.dim2, p.blur); refresh()
                }
            }
        }
        out += Row("Wallpaper darkness", "${BasePrefs.wallpaperDim(c)}% — higher = easier to read") {
            val o = listOf(10, 20, 35, 45, 55, 65, 75, 85)
            Ui.choose(c, "Darkness", o.map { "$it%" }) { BasePrefs.setWallpaperDim(c, o[it]); refresh() }
        }
        out += Row("Wallpaper blur", when (BasePrefs.wallpaperBlur(c)) { 0 -> "Off"; 10 -> "Light"; else -> "Strong" } +
            if (android.os.Build.VERSION.SDK_INT < 31) " (needs Android 12+)" else "") {
            val o = listOf(0 to "Off", 10 to "Light", 25 to "Strong")
            Ui.choose(c, "Wallpaper blur", o.map { it.second }) { BasePrefs.setWallpaperBlur(c, o[it].first); refresh() }
        }

        out += Row("Discover wallpaper (new movies & shows)", header = true)
        for ((id, label) in Discover.SOURCES) {
            val on = BasePrefs.discoverSource(c, id)
            val note = if (id == "theaters" && BasePrefs.tmdbKey(c).isEmpty()) " — add a key below" else ""
            out += Row((if (on) "✓  " else "✗  ") + label, (if (on) "Included" else "Off") + note) {
                BasePrefs.setDiscoverSource(c, id, !on); Discover.refresh(c, force = true); refresh()
            }
        }
        out += Row("TMDB key (optional, free)", if (BasePrefs.tmdbKey(c).isEmpty()) "Not set — adds “Now in theaters” & “Coming soon”" else "Set ✓") {
            Ui.dialog(c).setTitle("Free TMDB key")
                .setMessage("1. Make a free account at themoviedb.org\n2. Settings → API → request a key (choose Personal)\n" +
                    "3. Type the “API Key” (32 letters/numbers) here.\n\nNo key? Discover still shows anime, TV and new movie releases.")
                .setPositiveButton("Enter key") { _, _ ->
                    Ui.prompt(c, "TMDB API key", BasePrefs.tmdbKey(c), "32 characters") {
                        BasePrefs.setTmdbKey(c, it); Discover.refresh(c, force = true); refresh()
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
        out += Row("Title caption on the wallpaper", if (BasePrefs.discoverCaption(c)) "On — e.g. “Dune: Part Three · Now in theaters”" else "Off") {
            BasePrefs.setDiscoverCaption(c, !BasePrefs.discoverCaption(c)); refresh()
        }
        out += Row("Refresh Discover now", "Loads the latest movies & shows") {
            Discover.refresh(c, force = true) { Ui.toast(c, "Discover updated") }; Ui.toast(c, "Loading…")
        }

        out += Row("Weather", header = true)
        out += Row("Weather in the header", if (BasePrefs.weatherEnabled(c)) "On" else "Off") {
            BasePrefs.setWeatherEnabled(c, !BasePrefs.weatherEnabled(c)); refresh()
        }
        out += Row("Location", BasePrefs.weatherPlace(c)?.name ?: "Not set") { pickLocation(c) { refresh() } }
        out += Row("Units", if (BasePrefs.weatherFahrenheit(c)) "°F and mph" else "°C and km/h") {
            BasePrefs.setWeatherFahrenheit(c, !BasePrefs.weatherFahrenheit(c)); Weather.reset(c); Weather.refresh(c, true); refresh()
        }
        out += Row("Animated weather icon", if (BasePrefs.weatherAnimated(c)) "On" else "Off") {
            BasePrefs.setWeatherAnimated(c, !BasePrefs.weatherAnimated(c)); refresh()
        }
        out += Row("Details (high/low, wind, rain chance)", if (BasePrefs.weatherDetails(c)) "Shown" else "Hidden") {
            BasePrefs.setWeatherDetails(c, !BasePrefs.weatherDetails(c)); refresh()
        }

        out += Row("Clock", header = true)
        val clockStyles = listOf("stacked" to "Stacked — time, then day & date", "single" to "Single line — Tue, Nov 3 · 9:41 PM",
            "big" to "Big clock at the top of home", "time" to "Time only", "hidden" to "Hidden")
        out += Row("Clock style", clockStyles.first { it.first == BasePrefs.clockStyle(c) }.second) {
            Ui.choose(c, "Clock style", clockStyles.map { it.second }) { BasePrefs.setClockStyle(c, clockStyles[it].first); refresh() }
        }
        out += Row("Time format", (if (BasePrefs.clock24h(c)) "24-hour" else "12-hour") + if (BasePrefs.clockSeconds(c)) ", with seconds" else "") {
            Ui.choose(c, "Time format", listOf("12-hour", "24-hour", "12-hour with seconds", "24-hour with seconds")) {
                BasePrefs.setClock24h(c, it % 2 == 1); BasePrefs.setClockSeconds(c, it >= 2); refresh()
            }
        }
        val orders = listOf("mdy" to "Tuesday · November 3", "dmy" to "Tuesday · 3 November", "num" to "Tuesday · 11/3")
        out += Row("Date format", orders.first { it.first == BasePrefs.dateOrder(c) }.second) {
            Ui.choose(c, "Date format", orders.map { it.second }) { BasePrefs.setDateOrder(c, orders[it].first); refresh() }
        }
        out += Row("Greeting above the clock", if (BasePrefs.clockGreeting(c)) "On — “Good evening”" else "Off") {
            BasePrefs.setClockGreeting(c, !BasePrefs.clockGreeting(c)); refresh()
        }

        out += Row("Rows on the home screen (select to show/hide or move)", header = true)
        val order = BasePrefs.sectionOrder(c)
        order.forEachIndexed { i, id ->
            val shown = BasePrefs.sectionShown(c, id)
            val note = ""
            out += Row((if (shown) "✓  " else "✗  ") + BasePrefs.sectionLabel(id),
                (if (shown) "Shown" else "Hidden") + " · position ${i + 1}$note") { sectionMenu(id) }
        }

        out += Row("Watching & upcoming strip (compact)", header = true)
        out += Row("Strip look", if (BasePrefs.stripStyle(c) == "text") "Text only — smallest" else "Text with small thumbnails") {
            Ui.choose(c, "Strip look", listOf("Text only (smallest)", "Text with small thumbnails")) {
                BasePrefs.setStripStyle(c, if (it == 0) "text" else "thumbs"); refresh()
            }
        }
        out += Row("Lines per column", "${BasePrefs.stripRows(c)} — more lines = taller strip") {
            val o = (1..8).toList()
            Ui.choose(c, "Lines per column", o.map { it.toString() }) { BasePrefs.setStripRows(c, o[it]); refresh() }
        }
        out += Row("Strip text size", listOf("Small", "Medium", "Large")[BasePrefs.stripTextSize(c)]) {
            Ui.choose(c, "Strip text size", listOf("Small", "Medium", "Large")) { BasePrefs.setStripTextSize(c, it); refresh() }
        }
        out += Row("“Airing soon” window", "${BasePrefs.soonLabel(c)} — Upcoming episodes lists every followed show") {
            val o = BasePrefs.SOON_OPTIONS
            Ui.choose(c, "Airing soon = the next…", o.map { it.second }) { BasePrefs.setSoonHours(c, o[it].first); refresh() }
        }
        for (id in BasePrefs.STRIP_PANELS) {
            val on = BasePrefs.stripPanelShown(c, id)
            out += Row((if (on) "✓  " else "✗  ") + BasePrefs.stripPanelLabel(id), if (on) "Shown in the strip" else "Hidden") {
                BasePrefs.setStripPanelShown(c, id, !on); refresh()
            }
        }

        out += Row("Featured cards (3 side by side)", header = true)
        for (id in BasePrefs.STRIP_PANELS) {
            val on = BasePrefs.featureShown(c, id)
            out += Row((if (on) "✓  " else "✗  ") + BasePrefs.stripPanelLabel(id) + " card",
                if (on) "Shown in the featured row" else "Hidden") {
                BasePrefs.setFeatureShown(c, id, !on); refresh()
            }
        }

        out += Row("Big card rows (Continue watching / Upcoming episodes)", header = true)
        out += Row("Card size", mapOf("s" to "Small", "m" to "Medium", "l" to "Large")[BasePrefs.mediaCardSize(c)] ?: "Medium") {
            val o = listOf("s" to "Small", "m" to "Medium", "l" to "Large")
            Ui.choose(c, "Card size", o.map { it.second }) { BasePrefs.setMediaCardSize(c, o[it].first); refresh() }
        }
        out += Row("Card artwork", if (BasePrefs.mediaCardImages(c)) "Pictures" else "Text only (compact cards)") {
            BasePrefs.setMediaCardImages(c, !BasePrefs.mediaCardImages(c)); refresh()
        }

        out += Row("Cameras", header = true)
        out += Row("Camera picture", if (BasePrefs.camTileFit(c) == "fill") "Fill the tile (no black bars)" else "Show whole picture (may have bars)") {
            BasePrefs.setCamTileFit(c, if (BasePrefs.camTileFit(c) == "fill") "fit" else "fill"); refresh()
        }
        out += Row("Camera strip size", CamerasActivity.stripLabel(c)) { CamerasActivity.pickStripSize(c) { refresh() } }

        out += Row("Your apps (favorites)", header = true)
        out += Row("Layout", if (BasePrefs.favLayout(c) == "grid") "Grid — stacked rows, no scrolling sideways" else "One row (scrolls sideways)") {
            BasePrefs.setFavLayout(c, if (BasePrefs.favLayout(c) == "grid") "row" else "grid"); refresh()
        }
        out += Row("Size", "${BasePrefs.favColumns(c)} per row — bigger number = smaller tiles") {
            val o = (3..12).toList()
            Ui.choose(c, "Your apps per row", o.map { "$it per row" + if (it >= 9) " (small)" else if (it <= 4) " (large)" else "" }) {
                BasePrefs.setFavColumns(c, o[it]); refresh()
            }
        }
        out += Row("Rows shown", if (BasePrefs.favMaxRows(c) == 0) "All" else "Up to ${BasePrefs.favMaxRows(c)} (grid layout)") {
            val o = listOf(1, 2, 3, 4, 0)
            Ui.choose(c, "Rows of Your apps", o.map { if (it == 0) "All" else "$it" }) { BasePrefs.setFavMaxRows(c, o[it]); refresh() }
        }

        out += Row("App tiles (style for every app tile)", header = true)
        out += Row("Tile style", tileStyles.first { it.first == BasePrefs.tileStyle(c) }.second) {
            Ui.choose(c, "Tile style", tileStyles.map { it.second }) { BasePrefs.setTileStyle(c, tileStyles[it].first); refresh() }
        }
        out += Row("All apps: per row", "${BasePrefs.appColumns(c)} — bigger number = smaller tiles") {
            val o = (3..9).toList()
            Ui.choose(c, "Apps per row (bigger number = smaller tiles)", o.map { it.toString() }) {
                BasePrefs.setAppColumns(c, o[it]); refresh()
            }
        }
        out += Row("App names", if (BasePrefs.showAppLabels(c)) "Shown" else "Hidden") {
            BasePrefs.setShowAppLabels(c, !BasePrefs.showAppLabels(c)); refresh()
        }
        out += Row("Zoom on focus", if (BasePrefs.focusZoom(c)) "On" else "Off — border only") {
            BasePrefs.setFocusZoom(c, !BasePrefs.focusZoom(c)); refresh()
        }
        out += Row("Show phone-only apps", if (BasePrefs.showAllApps(c)) "On" else "Off") {
            BasePrefs.setShowAllApps(c, !BasePrefs.showAllApps(c)); AppRepository.invalidate(); refresh()
        }
        val hidden = BasePrefs.hiddenApps(c)
        out += Row("Hidden apps", if (hidden.isEmpty()) "None (Menu on an app → Hide)" else "${hidden.size} — select to unhide") {
            if (hidden.isNotEmpty()) Ui.choose(c, "Unhide", hidden.map { AppRepository.label(c, it) }) {
                BasePrefs.setHidden(c, hidden[it], false); refresh()
            }
        }
        out += Row("Reset", header = true)
        out += Row("Reset home layout", "Style, wallpaper, rows and tiles back to default") {
            Ui.confirm(c, "Reset home layout?", "Favorites, cameras and button mappings are kept.") {
                BasePrefs.resetHomeLayout(c); refresh()
            }
        }
        return out
    }

    companion object {
        /** City / ZIP search, or detect from the internet connection. */
        fun pickLocation(c: android.content.Context, done: () -> Unit) {
            Ui.choose(c, "Weather location", listOf("Search city or ZIP code", "Detect automatically (from your internet connection)")) { w ->
                if (w == 1) {
                    Ui.toast(c, "Detecting…")
                    Weather.detectPlace { pl ->
                        if (pl == null) Ui.toast(c, "Couldn't detect — search instead")
                        else { BasePrefs.setWeatherPlace(c, pl); Weather.reset(c); Weather.refresh(c, true); Ui.toast(c, "Weather for ${pl.name}"); done() }
                    }
                } else Ui.prompt(c, "City or ZIP code", "", "e.g. Phoenix or 85001") { q ->
                    if (q.isBlank()) return@prompt
                    Weather.searchPlaces(q) { places, err ->
                        if (places.isEmpty()) { Ui.toast(c, err ?: "No place found for “$q”"); return@searchPlaces }
                        Ui.choose(c, "Which one?", places.map { it.name }) { i ->
                            BasePrefs.setWeatherPlace(c, places[i]); Weather.reset(c); Weather.refresh(c, true)
                            Ui.toast(c, "Weather for ${places[i].name}"); done()
                        }
                    }
                }
            }
        }

        /** Forecast popup from the header (OK on the weather). */
        fun weatherDetails(c: android.content.Context, changed: () -> Unit) {
            val place = BasePrefs.weatherPlace(c)
            if (place == null) { pickLocation(c, changed); return }
            val now = Weather.current
            val sb = StringBuilder()
            if (now != null) {
                sb.append("${Weather.deg(c, now.temp)}${Weather.unitLetter(c)} · ${Weather.describe(now.code)}\n")
                sb.append("Feels like ${Weather.deg(c, now.feels)} · Humidity ${now.humidity}% · Wind ${Weather.windText(c, now.wind)}\n\n")
                val fmtIn = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val fmtOut = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
                now.days.forEachIndexed { i, d ->
                    val name = if (i == 0) "Today" else try { fmtOut.format(fmtIn.parse(d.date)!!) } catch (e: Exception) { d.date }
                    sb.append("$name — ${Weather.describe(d.code)}, ${Weather.deg(c, d.high)} / ${Weather.deg(c, d.low)}" +
                        (if (d.rainChance >= 10) ", rain ${d.rainChance}%" else "") + "\n")
                }
            } else sb.append("Loading…")
            Ui.dialog(c).setTitle("Weather · ${place.name}").setMessage(sb.toString())
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton("Change location") { _, _ -> pickLocation(c, changed) }
                .setNegativeButton("Refresh") { _, _ -> Weather.refresh(c, true) { changed() } }
                .show()
        }
    }

    /** Plain description of what the wallpaper is actually showing right now. */
    private fun wallpaperStatus(): String {
        val keep = if (BasePrefs.wallpaperUserSet(this)) "Your choice — kept when you change themes." else "Set by the theme."
        val now = when (BasePrefs.wallpaperMode(this)) {
            "discover" -> {
                val n = Discover.list(this).size
                if (n > 0) "Discover: $n movie/show images rotating."
                else "Discover hasn't loaded yet${Discover.lastError?.let { " ($it)" } ?: ""} — showing your shows' artwork as a backup."
            }
            "slideshow" -> "Slideshow of your shows' artwork."
            "auto" -> "Follows the show you highlight."
            "url" -> "Your image link."
            "color" -> "Solid color."
            "animated" -> "Animated gradient."
            "none" -> "Plain."
            else -> "Theme gradient."
        }
        return "$now $keep"
    }

    private fun sectionMenu(id: String) {
        val shown = BasePrefs.sectionShown(this, id)
        Ui.choose(this, BasePrefs.sectionLabel(id), listOf(if (shown) "Hide" else "Show", "Move up", "Move down", "Move to top")) { w ->
            val order = BasePrefs.sectionOrder(this)
            val i = order.indexOf(id)
            when (w) {
                0 -> BasePrefs.setSectionShown(this, id, !shown)
                1 -> if (i > 0) { order.add(i - 1, order.removeAt(i)); BasePrefs.setSectionOrder(this, order) }
                2 -> if (i < order.size - 1) { order.add(i + 1, order.removeAt(i)); BasePrefs.setSectionOrder(this, order) }
                3 -> { order.remove(id); order.add(0, id); BasePrefs.setSectionOrder(this, order) }
            }
            refresh()
        }
    }
}
