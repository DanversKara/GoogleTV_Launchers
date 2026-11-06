package com.moomoo.tvlauncherbase

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

/**
 * The whole look of the launcher. A theme PRESET sets everything at once
 * (colors, panels, corners, focus effect, font, wallpaper); every part can
 * then be overridden on its own in Customize home → Theme & design.
 * Call [load] in onCreate / onResume — every screen reads from here.
 */
object LauncherTheme {

    class Preset(
        val id: String, val name: String, val blurb: String,
        val bg: String, val surface: String, val surfaceHi: String, val panel: String,
        val text: String, val dim: String, val accent: String,
        val corner: String, val focus: String, val font: String,
        val wallpaper: String, val dim2: Int, val blur: Int = 0,
        val gradient: List<String> = listOf(bg, bg), val light: Boolean = false,
        /** Animated background layer: none, aurora, particles, starfield, waves, bokeh, grid. */
        val effect: String = "none",
        /** Hover glow: accent, app (each app's own color), off. */
        val glow: String = "accent"
    )

    val PRESETS = listOf(
        Preset("gtv", "Google TV", "Show-art wallpaper, soft dark panels, blue glow",
            "#0E1014", "#1A1D24", "#252A33", "#B3141820", "#FFFFFF", "#9AA3B2", "#3D8BFF",
            "rounded", "border", "standard", "auto", 55, gradient = listOf("#1B2A44", "#151A24", "#07080B"), glow = "app"),
        Preset("aurora", "Aurora ✦", "Northern-lights colors flowing behind everything, soft glow",
            "#05070F", "#141A2A", "#1E2740", "#99101626", "#F2F7FF", "#A9B6D3", "#6CF0C2",
            "rounded", "glow", "rounded", "gradient", 25, gradient = listOf("#0B1630", "#05070F"), effect = "aurora", glow = "app"),
        Preset("galaxy", "Galaxy ✦", "Drifting starfield with twinkling stars and a nebula haze",
            "#03020A", "#120E24", "#1C1636", "#99100C22", "#F5F0FF", "#B0A6D6", "#B388FF",
            "rounded", "glow", "standard", "gradient", 20, gradient = listOf("#1A0B3A", "#03020A", "#000000"), effect = "starfield", glow = "accent"),
        Preset("synthwave", "Synthwave ✦", "Retro neon sunset with a moving grid",
            "#12051E", "#22103A", "#2E1650", "#9918082C", "#FFE9FF", "#D9A6E8", "#FF4FD8",
            "sharp", "glow", "condensed", "gradient", 15, gradient = listOf("#FF6A3D", "#9B2FAE", "#12051E"), effect = "grid", glow = "accent"),
        Preset("cyber", "Cyberpunk ✦", "Neon yellow and cyan, floating particles, sharp edges",
            "#07080C", "#12141C", "#1C2030", "#B30C0F18", "#F4F7FF", "#9CA6BF", "#FCEE0A",
            "sharp", "glow", "mono", "gradient", 30, gradient = listOf("#002B36", "#07080C", "#2A0030"), effect = "particles", glow = "app"),
        Preset("neon", "Neon ✦", "Dark purple and teal, floating particles, pill tiles",
            "#0B0614", "#160D26", "#22143A", "#B3150A28", "#F2ECFF", "#A79BC4", "#00F0D0",
            "pill", "glow", "condensed", "animated", 45, gradient = listOf("#2A0B4A", "#0B0614", "#003A40"), effect = "particles"),
        Preset("glass", "Glass ✦", "Frosted panels over a blurred wallpaper, floating bokeh lights",
            "#10141C", "#33FFFFFF", "#4DFFFFFF", "#40FFFFFF", "#FFFFFF", "#D0D8E4", "#8FD3FF",
            "rounded", "lift", "rounded", "auto", 35, blur = 25, gradient = listOf("#2B3A55", "#1A2233", "#0E1320"), effect = "bokeh", glow = "app"),
        Preset("ocean", "Ocean ✦", "Animated gradient with rolling waves",
            "#06141C", "#0E2230", "#163246", "#990A1A24", "#EAF7FF", "#9CC3D6", "#4FE3C1",
            "rounded", "border", "standard", "animated", 30, gradient = listOf("#00C6FF", "#0072FF", "#031A33"), effect = "waves"),
        Preset("sunset", "Sunset ✦", "Warm animated gradient with aurora light",
            "#1A0F14", "#2A1820", "#3A2230", "#99200F1A", "#FFF4EC", "#E0B9A8", "#FFB347",
            "rounded", "glow", "rounded", "animated", 30, gradient = listOf("#FF5E62", "#8E2DE2", "#2B1055"), effect = "aurora"),
        Preset("crunchy", "Crunchyroll", "Charcoal with orange accents, poster-forward",
            "#141519", "#1E2026", "#2A2D35", "#CC17181D", "#FFFFFF", "#A0A3AB", "#F47521",
            "rounded", "glow", "standard", "discover", 55, gradient = listOf("#3A1E0E", "#141519", "#0A0A0C"), glow = "accent"),
        Preset("cinema", "Cinema", "Movie & show backdrops as wallpaper, deep black and red",
            "#0A0A0A", "#181818", "#262626", "#D90A0A0A", "#FFFFFF", "#B3B3B3", "#E50914",
            "sharp", "lift", "standard", "discover", 60, gradient = listOf("#2A0507", "#0A0A0A", "#000000"), glow = "app"),
        Preset("amoled", "AMOLED Black", "Pure black, crisp white focus — great for OLED and at night",
            "#000000", "#0D0D0D", "#1A1A1A", "#E6000000", "#FFFFFF", "#8C8C8C", "#FFFFFF",
            "rounded", "border", "standard", "none", 70, gradient = listOf("#000000", "#000000"), glow = "off"),
        Preset("light", "Minimal Light", "Light grey with dark text, for bright rooms",
            "#ECEFF3", "#FFFFFF", "#F5F7FA", "#E6FFFFFF", "#15181D", "#5B6472", "#1A73E8",
            "rounded", "lift", "standard", "gradient", 10, gradient = listOf("#F8FAFC", "#E3E8EF"), light = true, glow = "accent"),
        Preset("retro", "Retro", "Chunky borders, pixel-style font, scrolling starfield",
            "#101820", "#1C2A38", "#28394A", "#E61C2A38", "#F2F2E9", "#9FB3A0", "#7CFC00",
            "sharp", "border", "mono", "gradient", 40, gradient = listOf("#1C2A38", "#101820"), effect = "starfield", glow = "accent")
    )

    val EFFECTS = listOf("none" to "None", "aurora" to "Aurora (flowing northern lights)", "particles" to "Floating particles",
        "starfield" to "Starfield (twinkling, drifting)", "waves" to "Rolling waves", "bokeh" to "Bokeh lights", "grid" to "Synthwave grid")
    val GLOWS = listOf("app" to "Each app's own color", "accent" to "Theme color", "off" to "Off")

    val ACCENTS = listOf(
        "Ocean blue" to "#3D8BFF", "Emerald" to "#21C07A", "Sunset orange" to "#FF8A3D", "Violet" to "#9B6BFF",
        "Crimson" to "#FF4D6D", "Gold" to "#F5C542", "Ice white" to "#E8EEF7", "Crunchy orange" to "#F47521",
        "Neon teal" to "#00F0D0", "Cinema red" to "#E50914", "Lime" to "#7CFC00", "Pink" to "#FF6EC7"
    )
    val CORNERS = listOf("sharp" to "Sharp", "rounded" to "Rounded", "pill" to "Pill")
    val FOCUS_STYLES = listOf("border" to "Border", "glow" to "Glow", "lift" to "Lift & zoom", "underline" to "Underline")
    val FONTS = listOf("standard" to "Standard", "condensed" to "Condensed", "rounded" to "Rounded", "mono" to "Monospace", "serif" to "Serif")

    var preset: Preset = PRESETS[0]; private set
    var background = Color.parseColor("#0E1014"); private set
    var surface = Color.parseColor("#1A1D24"); private set
    var surfaceHi = Color.parseColor("#252A33"); private set
    /** Translucent panel behind strips / featured cards. */
    var panel = 0xB3141820.toInt(); private set
    var text = Color.WHITE; private set
    var dimText = Color.parseColor("#9AA3B2"); private set
    var accent = Color.parseColor("#3D8BFF"); private set
    var gradient = intArrayOf(Color.BLACK, Color.BLACK); private set
    var light = false; private set
    var corner = "rounded"; private set
    var focusStyle = "border"; private set
    var font = "standard"; private set
    var effect = "none"; private set
    var glowMode = "accent"; private set
    /** Whole-app text size (Settings → Text size). */
    var textScale = 1f; private set
    /** 1.0 comfortable, 0.85 compact. */
    var density = 1f; private set
    val danger = Color.parseColor("#FF5A5F")

    /** Text color that reads on top of the accent. */
    val onAccent get() = if (luminance(accent) > 0.55) Color.BLACK else Color.WHITE
    /** Secondary text over artwork / wallpaper. */
    val softText get() = if (light) Color.parseColor("#3A4250") else Color.parseColor("#D0D6E0")

    val focusFill get() = when (focusStyle) {
        "glow" -> (accent and 0x00FFFFFF) or 0x59000000
        "lift" -> if (light) Color.WHITE else surfaceHi
        "underline" -> if (light) 0x14000000 else 0x1FFFFFFF
        else -> (accent and 0x00FFFFFF) or 0x40000000
    }
    val focusBorder get() = accent

    /** Corner radius in dp for cards/tiles. */
    val CORNER_DP: Int get() = when (corner) { "sharp" -> 3; "pill" -> 24; else -> 12 }
    const val FOCUS_STROKE_DP = 3

    fun typeface(bold: Boolean): Typeface {
        val fam = when (font) {
            "condensed" -> "sans-serif-condensed"
            "rounded" -> "sans-serif-medium"
            "mono" -> "monospace"
            "serif" -> "serif"
            else -> "sans-serif"
        }
        return Typeface.create(fam, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun luminance(c: Int): Double =
        (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0

    private fun col(s: String) = try { Color.parseColor(s) } catch (e: Exception) { Color.BLACK }

    fun presetById(id: String) = PRESETS.firstOrNull { it.id == id } ?: PRESETS[0]

    fun load(c: Context) {
        Brand.name = BasePrefs.displayName(c)
        val p = presetById(BasePrefs.themeId(c))
        preset = p
        background = col(p.bg); surface = col(p.surface); surfaceHi = col(p.surfaceHi); panel = col(p.panel)
        text = col(p.text); dimText = col(p.dim); light = p.light
        gradient = p.gradient.map { col(it) }.toIntArray()
        accent = BasePrefs.accentHex(c)?.let { col(it) } ?: col(p.accent)
        corner = BasePrefs.cornerStyle(c) ?: p.corner
        focusStyle = BasePrefs.focusStyleOverride(c) ?: p.focus
        font = BasePrefs.fontOverride(c) ?: p.font
        effect = BasePrefs.bgEffect(c) ?: p.effect
        glowMode = BasePrefs.glowMode(c) ?: p.glow
        density = if (BasePrefs.compactDensity(c)) 0.85f else 1f
        textScale = BasePrefs.textSize(c) / 100f
    }

    /** Applies a preset: resets the per-part overrides and sets its wallpaper defaults. */
    fun applyPreset(c: Context, id: String) {
        val p = presetById(id)
        BasePrefs.applyThemePreset(c, p.id, p.wallpaper, p.dim2, p.blur)
        load(c)
    }

    // Text sizes (sp) and layout constants.
    const val TITLE_SP = 28f
    const val CLOCK_SP = 30f
    const val SECTION_SP = 18f
    const val APP_LABEL_SP = 14f
    const val BODY_SP = 18f
    const val GRID_COLUMNS = 6
    const val SCREEN_PADDING_DP = 40
}
