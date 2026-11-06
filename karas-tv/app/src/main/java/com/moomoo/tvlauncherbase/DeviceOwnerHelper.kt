package com.moomoo.tvlauncherbase

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Device-owner powers. All of these are no-ops unless the app was
 * provisioned as device owner via ADB (one-time, ideally before a Google
 * account is added during setup — after setup, dpm set-device-owner
 * fails on most devices):
 *
 *   adb shell dpm set-device-owner com.moomoo.tvlauncherbase/.TvDeviceAdmin
 */
object DeviceOwnerHelper {

    /** Stock Google TV / Android TV launcher packages. */
    val GOOGLE_LAUNCHERS = HomeGuardService.STOCK_LAUNCHERS

    private fun dpm(c: Context): DevicePolicyManager? =
        c.getSystemService(DevicePolicyManager::class.java)

    fun isDeviceOwner(c: Context): Boolean =
        dpm(c)?.isDeviceOwnerApp(c.packageName) == true

    fun adminComponent(c: Context): ComponentName =
        ComponentName(c, TvDeviceAdmin::class.java)

    /**
     * Pins this launcher as the *persistent* handler of HOME. Unlike the
     * normal "default app" choice (which updates and user taps can clear),
     * a persistent preferred activity survives reboots and can't be
     * overridden by the stock launcher. Re-asserted at every boot.
     */
    fun assertHomeOwnership(c: Context): Boolean {
        if (!isDeviceOwner(c)) return false
        return try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm(c)?.addPersistentPreferredActivity(
                adminComponent(c), filter, ComponentName(c, MainActivity::class.java)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Hides (or unhides) the stock Google launcher(s) completely. */
    fun setGoogleLauncherHidden(c: Context, hidden: Boolean) {
        if (!isDeviceOwner(c)) return
        for (pkg in GOOGLE_LAUNCHERS) {
            try {
                dpm(c)?.setApplicationHidden(adminComponent(c), pkg, hidden)
            } catch (e: Exception) {
                // package not present on this device; ignore
            }
        }
    }

    /** Gives up device ownership and restores the stock launcher. */
    fun release(c: Context): Boolean {
        return try {
            setGoogleLauncherHidden(c, false)
            dpm(c)?.clearDeviceOwnerApp(c.packageName)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deviceOwnerCommand(c: Context): String =
        "adb shell dpm set-device-owner ${c.packageName}/.TvDeviceAdmin"
}
