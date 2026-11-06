package com.moomoo.tvlauncherbase

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Turning on the two system services TV Launcher needs:
 *  - Accessibility: HomeGuardService (Home guard, button remapper, floating cameras)
 *  - Notification access: MediaWatchService (reads "what's playing" for
 *    Continue watching — media titles only, notifications are not read)
 *
 * Android 13+ greys both switches out for sideloaded apps ("Restricted
 * setting"). Two ways around it, both one-time from a computer:
 *  1. adb shell appops set <pkg> ACCESS_RESTRICTED_SETTINGS allow
 *     → then the normal switches work.
 *  2. adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS
 *     → TV Launcher can switch both services on by itself, and turns them
 *       back on after updates or if the system disables them.
 */
object ServiceSetup {
    private const val SECURE_PERM = "android.permission.WRITE_SECURE_SETTINGS"
    private const val NOTIF_KEY = "enabled_notification_listeners"

    fun a11yComponent(c: Context) = ComponentName(c, HomeGuardService::class.java)
    fun mediaComponent(c: Context) = ComponentName(c, MediaWatchService::class.java)

    fun canSelfEnable(c: Context) = c.checkSelfPermission(SECURE_PERM) == PackageManager.PERMISSION_GRANTED

    fun a11yEnabled(c: Context) = HomeGuardService.isEnabled(c)

    fun mediaEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, NOTIF_KEY) ?: return false
        val me = mediaComponent(c)
        return s.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun addTo(c: Context, key: String, comp: ComponentName): Boolean = try {
        val cur = Settings.Secure.getString(c.contentResolver, key) ?: ""
        val list = cur.split(":").filter { it.isNotBlank() }.toMutableList()
        if (list.none { ComponentName.unflattenFromString(it) == comp }) list += comp.flattenToString()
        Settings.Secure.putString(c.contentResolver, key, list.joinToString(":"))
    } catch (e: Exception) { false }

    /** Switches on whatever is off. Needs WRITE_SECURE_SETTINGS (granted via adb). */
    fun selfEnable(c: Context): Boolean {
        if (!canSelfEnable(c)) return false
        var ok = true
        if (!a11yEnabled(c)) {
            ok = addTo(c, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a11yComponent(c)) && ok
            try { Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1) } catch (e: Exception) { ok = false }
        }
        if (BasePrefs.watchTracking(c) && !mediaEnabled(c)) ok = addTo(c, NOTIF_KEY, mediaComponent(c)) && ok
        return ok
    }

    fun adbCommands(c: Context): String {
        val p = c.packageName
        return "adb shell appops set $p ACCESS_RESTRICTED_SETTINGS allow\n" +
            "adb shell pm grant $p android.permission.WRITE_SECURE_SETTINGS"
    }

    fun adbDirect(c: Context): String {
        val p = c.packageName
        return "adb shell settings put secure enabled_accessibility_services $p/${HomeGuardService::class.java.name}\n" +
            "adb shell settings put secure accessibility_enabled 1\n" +
            "adb shell cmd notification allow_listener $p/${MediaWatchService::class.java.name}"
    }

    fun openAccessibility(c: Context) {
        try { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Ui.toast(c, "This TV hides that page — use the adb commands") }
    }

    fun openNotificationAccess(c: Context) {
        try { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Ui.toast(c, "This TV hides that page — use the adb commands") }
    }

    /** One-line status for the accessibility service. */
    fun a11yStatus(c: Context): String = when {
        HomeGuardService.instance != null -> "Running ✓"
        a11yEnabled(c) -> "Switched on but not running — toggle it off and on again"
        canSelfEnable(c) -> "Off — will be switched on automatically"
        else -> "Off"
    }
}
