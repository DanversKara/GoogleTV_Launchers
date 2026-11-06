package com.moomoo.tvlauncherbase

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.moomoo.tvlauncherbase.BasePrefs.Press

/**
 * The button remapper: what each remote button does on a single, long
 * and double press. Mappings are enforced system-wide by the Home guard
 * accessibility service (they override the stock Google TV behaviour for
 * every key that reaches accessibility — which on most TVs includes Home,
 * Back, Menu, Guide, Settings, Input, colour keys, media keys and often
 * the Assistant / app buttons; use "Learn a button" to find out).
 *
 * Adding an action: one constant, add it to [GROUPS], one branch in [run].
 */
object RemoteKeys {
    // -- pass-through / block
    const val NONE = "none"           // default behaviour
    const val BLOCK = "block"         // swallow the key, do nothing
    // -- launcher
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val KEYMAP = "keymap"
    const val CAMERAS = "cameras"
    const val GUARD = "guard"
    const val LOCKDOWN = "lockdown"
    // -- apps & inputs
    const val APP = "app"
    const val LAST_APP = "last_app"
    const val INPUT = "input"
    const val URL = "url"
    // -- cameras
    const val CAM_TOGGLE = "cam_toggle"
    const val CAM_ADD = "cam_add"
    const val CAM_ALL = "cam_all"
    const val CAM_NEXT = "cam_next"
    const val CAM_CONTROL = "cam_control"
    const val CAM_CLOSE = "cam_close"
    const val CAM_WALL = "cam_wall"
    const val CAM_FULL = "cam_full"
    // -- system pages
    const val SYS_SETTINGS = "sys_settings"
    const val WIFI_SETTINGS = "wifi_settings"
    const val DISPLAY_SETTINGS = "display_settings"
    const val SOUND_SETTINGS = "sound_settings"
    const val APPS_SETTINGS = "apps_settings"
    const val ACCESS_SETTINGS = "access_settings"
    const val BT_SETTINGS = "bt_settings"
    const val INPUT_PICKER = "input_picker"
    // -- global actions (need the accessibility service)
    const val BACK = "back"
    const val SYS_HOME = "sys_home"
    const val RECENTS = "recents"
    const val NOTIFICATIONS = "notifications"
    const val QUICK_SETTINGS = "quick_settings"
    const val POWER = "power"
    const val SLEEP = "sleep"
    const val SLEEP_TIMER = "sleep_timer"
    const val SCREENSHOT = "screenshot"
    // -- media & volume
    const val PLAY_PAUSE = "play_pause"
    const val MEDIA_NEXT = "media_next"
    const val MEDIA_PREV = "media_prev"
    const val MEDIA_FF = "media_ff"
    const val MEDIA_RW = "media_rw"
    const val MEDIA_STOP = "media_stop"
    const val VOL_UP = "vol_up"
    const val VOL_DOWN = "vol_down"
    const val MUTE = "mute"

    val GROUPS: List<Pair<String, List<String>>> = listOf(
        "Basic" to listOf(NONE, BLOCK),
        "Apps & inputs" to listOf(APP, LAST_APP, INPUT, URL),
        "Cameras" to listOf(CAM_TOGGLE, CAM_ADD, CAM_ALL, CAM_NEXT, CAM_CONTROL, CAM_CLOSE, CAM_WALL, CAM_FULL),
        "Launcher" to listOf(HOME, SETTINGS, KEYMAP, CAMERAS, GUARD, LOCKDOWN),
        "Media & volume" to listOf(PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREV, MEDIA_FF, MEDIA_RW, MEDIA_STOP, VOL_UP, VOL_DOWN, MUTE),
        "System" to listOf(BACK, SYS_HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER, SLEEP, SLEEP_TIMER, SCREENSHOT),
        "Settings pages" to listOf(SYS_SETTINGS, WIFI_SETTINGS, BT_SETTINGS, DISPLAY_SETTINGS, SOUND_SETTINGS,
            APPS_SETTINGS, ACCESS_SETTINGS, INPUT_PICKER)
    )

    val NEEDS_ARG = setOf(APP, INPUT, URL, CAM_FULL)

    fun label(action: String?): String = when (action) {
        null, NONE -> "Default (unchanged)"
        BLOCK -> "Disable button (do nothing)"
        HOME -> "Open this launcher"
        SETTINGS -> "Launcher settings"
        KEYMAP -> "Remote buttons screen"
        CAMERAS -> "Cameras screen"
        GUARD -> "Home guard screen"
        LOCKDOWN -> "Permanent fix screen"
        APP -> "Open an app…"
        LAST_APP -> "Switch to previous app"
        INPUT -> "Switch to a TV input / HDMI…"
        URL -> "Open a web link / deep link…"
        CAM_TOGGLE -> "Floating cameras: show / hide"
        CAM_ADD -> "Floating cameras: add another"
        CAM_ALL -> "Floating cameras: show all"
        CAM_NEXT -> "Floating cameras: next camera"
        CAM_CONTROL -> "Floating cameras: move / resize"
        CAM_CLOSE -> "Floating cameras: close all"
        CAM_WALL -> "Camera wall (full screen)"
        CAM_FULL -> "One camera full screen…"
        SYS_SETTINGS -> "System settings"
        WIFI_SETTINGS -> "Wi-Fi settings"
        BT_SETTINGS -> "Bluetooth / remotes settings"
        DISPLAY_SETTINGS -> "Display settings"
        SOUND_SETTINGS -> "Sound settings"
        APPS_SETTINGS -> "Apps settings"
        ACCESS_SETTINGS -> "Accessibility settings"
        INPUT_PICKER -> "Inputs list"
        BACK -> "Back"
        SYS_HOME -> "System Home (stock behaviour)"
        RECENTS -> "Recent apps"
        NOTIFICATIONS -> "Notifications"
        QUICK_SETTINGS -> "Quick settings panel"
        POWER -> "Power menu"
        SLEEP -> "Sleep / screen off"
        SLEEP_TIMER -> "Sleep timer (30/60/90/120 min)"
        SCREENSHOT -> "Screenshot"
        PLAY_PAUSE -> "Play / pause"
        MEDIA_NEXT -> "Next track"
        MEDIA_PREV -> "Previous track"
        MEDIA_FF -> "Fast forward"
        MEDIA_RW -> "Rewind"
        MEDIA_STOP -> "Stop"
        VOL_UP -> "Volume up"
        VOL_DOWN -> "Volume down"
        MUTE -> "Mute / unmute"
        else -> action
    }

    /** "Open Netflix", "HDMI 2", ... — action label with its argument resolved. */
    fun describe(c: Context, action: String?, arg: String?): String = when (action) {
        APP -> arg?.let { "Open " + AppRepository.label(c, it) } ?: "Open an app (none chosen)"
        INPUT -> arg?.let { id -> "Input: " + (TvInputs.list(c, all = true).firstOrNull { it.id == id }?.displayName(c) ?: id.substringAfterLast('/')) }
            ?: "Input (none chosen)"
        URL -> "Open $arg"
        CAM_FULL -> arg?.toIntOrNull()?.let { BasePrefs.cameras(c).getOrNull(it)?.name }?.let { "Full screen: $it" }
            ?: "Camera (none chosen)"
        else -> label(action)
    }

    // ------------------------------------------------------------ key catalogue
    data class MappedKey(val code: Int, val name: String, val warn: String? = null)

    val KEYS = listOf(
        MappedKey(KeyEvent.KEYCODE_HOME, "Home", "Remapping Home replaces the Home button everywhere."),
        MappedKey(KeyEvent.KEYCODE_BACK, "Back", "Map long or double press only, so Back keeps working."),
        MappedKey(KeyEvent.KEYCODE_SEARCH, "Search / Mic"),
        MappedKey(KeyEvent.KEYCODE_ASSIST, "Assistant"),
        MappedKey(KeyEvent.KEYCODE_VOICE_ASSIST, "Voice assistant"),
        MappedKey(KeyEvent.KEYCODE_SETTINGS, "Settings (gear)"),
        MappedKey(KeyEvent.KEYCODE_TV_INPUT, "Input / Source"),
        MappedKey(KeyEvent.KEYCODE_GUIDE, "Guide / Live TV"),
        MappedKey(KeyEvent.KEYCODE_TV, "TV"),
        MappedKey(KeyEvent.KEYCODE_MENU, "Menu / Options"),
        MappedKey(KeyEvent.KEYCODE_INFO, "Info"),
        MappedKey(KeyEvent.KEYCODE_BOOKMARK, "Bookmark / Star"),
        MappedKey(KeyEvent.KEYCODE_ALL_APPS, "All apps"),
        MappedKey(KeyEvent.KEYCODE_PROFILE_SWITCH, "Profile"),
        MappedKey(KeyEvent.KEYCODE_CAPTIONS, "Captions / Subtitles"),
        MappedKey(KeyEvent.KEYCODE_VOLUME_MUTE, "Mute"),
        MappedKey(KeyEvent.KEYCODE_VOLUME_UP, "Volume up", "Map long or double press only, so volume keeps working."),
        MappedKey(KeyEvent.KEYCODE_VOLUME_DOWN, "Volume down", "Map long or double press only, so volume keeps working."),
        MappedKey(KeyEvent.KEYCODE_CHANNEL_UP, "Channel up"),
        MappedKey(KeyEvent.KEYCODE_CHANNEL_DOWN, "Channel down"),
        MappedKey(KeyEvent.KEYCODE_LAST_CHANNEL, "Last channel"),
        MappedKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play / Pause"),
        MappedKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Fast forward"),
        MappedKey(KeyEvent.KEYCODE_MEDIA_REWIND, "Rewind"),
        MappedKey(KeyEvent.KEYCODE_PROG_RED, "Red"),
        MappedKey(KeyEvent.KEYCODE_PROG_GREEN, "Green"),
        MappedKey(KeyEvent.KEYCODE_PROG_YELLOW, "Yellow"),
        MappedKey(KeyEvent.KEYCODE_PROG_BLUE, "Blue")
    )

    fun keyName(code: Int): String = KEYS.firstOrNull { it.code == code }?.name
        ?: KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
            .lowercase().replaceFirstChar { it.uppercase() } + " ($code)"

    /** Keys shown in the editor: catalogue + learned keys. */
    fun allKeys(c: Context): List<MappedKey> {
        val learned = BasePrefs.learnedKeys(c).filter { code -> KEYS.none { it.code == code } }
        return KEYS + learned.map { MappedKey(it, keyName(it)) }
    }

    fun isMapped(c: Context, code: Int): Boolean = Press.values().any {
        val a = BasePrefs.keyAction(c, code, it); a != null && a != NONE
    }

    /** Short summary for a key row: "Single: Open Netflix · Long: Camera wall". */
    fun summary(c: Context, code: Int): String {
        val parts = Press.values().mapNotNull { p ->
            val a = BasePrefs.keyAction(c, code, p)
            if (a == null || a == NONE) null
            else "${p.label.substringBefore(' ')}: ${describe(c, a, BasePrefs.keyArg(c, code, p))}"
        }
        return if (parts.isEmpty()) "Default (unchanged)" else parts.joinToString("  ·  ")
    }

    // ------------------------------------------------------------ running actions
    /** Runs [action]. Returns false when it couldn't run here. */
    fun run(c: Context, action: String, arg: String?): Boolean {
        val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
        fun open(i: Intent): Boolean = try { c.startActivity(i.addFlags(newTask)); true } catch (e: Exception) { false }
        fun screen(cls: Class<*>) = open(Intent(c, cls))
        val svc = c as? AccessibilityService
        fun global(a: Int): Boolean = svc?.performGlobalAction(a) == true
        return when (action) {
            NONE -> false
            BLOCK -> true
            HOME -> { HomeHelper.bringHome(c); true }
            SETTINGS -> screen(SettingsActivity::class.java)
            KEYMAP -> screen(KeyMapActivity::class.java)
            CAMERAS -> screen(CamerasActivity::class.java)
            GUARD -> screen(HomeGuardActivity::class.java)
            LOCKDOWN -> screen(LockdownActivity::class.java)
            APP -> arg != null && AppRepository.launchPackage(c, arg)
            LAST_APP -> {
                val pkg = HomeGuardService.previousApp()
                if (pkg == null) { Ui.toast(c, "No previous app yet"); true }
                else AppRepository.launchPackage(c, pkg)
            }
            INPUT -> arg != null && TvInputs.open(c, arg)
            URL -> arg != null && open(Intent(Intent.ACTION_VIEW, Uri.parse(arg)))
            CAM_TOGGLE -> { OverlayManager.toggle(c); true }
            CAM_ADD -> { OverlayManager.addNext(c); true }
            CAM_ALL -> { OverlayManager.showAll(c); true }
            CAM_NEXT -> { OverlayManager.cycleCamera(c); true }
            CAM_CONTROL -> { OverlayManager.enterControl(c); true }
            CAM_CLOSE -> { OverlayManager.hideAll(); true }
            CAM_WALL -> screen(CameraWallActivity::class.java)
            CAM_FULL -> open(Intent(c, CameraWallActivity::class.java).putExtra("cam", arg?.toIntOrNull() ?: 0))
            SYS_SETTINGS -> open(Intent(Settings.ACTION_SETTINGS))
            WIFI_SETTINGS -> open(Intent(Settings.ACTION_WIFI_SETTINGS))
            BT_SETTINGS -> open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            DISPLAY_SETTINGS -> open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            SOUND_SETTINGS -> open(Intent(Settings.ACTION_SOUND_SETTINGS))
            APPS_SETTINGS -> open(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            ACCESS_SETTINGS -> open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            INPUT_PICKER -> open(Intent("com.android.tv.action.VIEW_INPUTS")) || open(Intent(Settings.ACTION_SETTINGS))
            BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            SYS_HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            POWER -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            SLEEP -> Build.VERSION.SDK_INT >= 28 && global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            SCREENSHOT -> Build.VERSION.SDK_INT >= 28 && global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            SLEEP_TIMER -> { HomeGuardService.cycleSleepTimer(c); true }
            PLAY_PAUSE -> media(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            MEDIA_NEXT -> media(c, KeyEvent.KEYCODE_MEDIA_NEXT)
            MEDIA_PREV -> media(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MEDIA_FF -> media(c, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            MEDIA_RW -> media(c, KeyEvent.KEYCODE_MEDIA_REWIND)
            MEDIA_STOP -> media(c, KeyEvent.KEYCODE_MEDIA_STOP)
            VOL_UP -> volume(c, AudioManager.ADJUST_RAISE)
            VOL_DOWN -> volume(c, AudioManager.ADJUST_LOWER)
            MUTE -> volume(c, AudioManager.ADJUST_TOGGLE_MUTE)
            else -> false
        }
    }

    fun media(c: Context, code: Int): Boolean {
        val am = c.getSystemService(AudioManager::class.java) ?: return false
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    fun volume(c: Context, dir: Int): Boolean {
        val am = c.getSystemService(AudioManager::class.java) ?: return false
        am.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        return true
    }

    /**
     * Re-creates a key's normal behaviour after the remapper swallowed it
     * (needed when only long/double press is mapped). Works for Back, Home,
     * D-pad (Android 13+), media and volume keys. Other keys can't be
     * re-injected without root, so their single press is lost while a
     * long/double mapping exists — the editor warns about this.
     */
    fun replay(c: Context, code: Int): Boolean {
        val svc = c as? AccessibilityService
        fun g(a: Int) = svc?.performGlobalAction(a) == true
        return when (code) {
            KeyEvent.KEYCODE_BACK -> g(AccessibilityService.GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_HOME -> g(AccessibilityService.GLOBAL_ACTION_HOME)
            KeyEvent.KEYCODE_DPAD_UP -> Build.VERSION.SDK_INT >= 33 && g(AccessibilityService.GLOBAL_ACTION_DPAD_UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> Build.VERSION.SDK_INT >= 33 && g(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> Build.VERSION.SDK_INT >= 33 && g(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> Build.VERSION.SDK_INT >= 33 && g(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
            KeyEvent.KEYCODE_DPAD_CENTER -> Build.VERSION.SDK_INT >= 33 && g(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
            KeyEvent.KEYCODE_VOLUME_UP -> volume(c, AudioManager.ADJUST_RAISE)
            KeyEvent.KEYCODE_VOLUME_DOWN -> volume(c, AudioManager.ADJUST_LOWER)
            KeyEvent.KEYCODE_VOLUME_MUTE -> volume(c, AudioManager.ADJUST_TOGGLE_MUTE)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STOP -> media(c, code)
            else -> false
        }
    }

    fun canReplay(code: Int): Boolean = code in setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STOP
    ) || (Build.VERSION.SDK_INT >= 33 && code in setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER))
}
