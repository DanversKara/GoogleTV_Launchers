package com.moomoo.tvlauncherbase

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class LauncherApp(
    val packageName: String,
    val label: String,
    val activityInfo: ActivityInfo,
    /** True when the app ships a TV (leanback) entry point. */
    val isTvApp: Boolean
)

object AppRepository {

    private var cache: List<LauncherApp>? = null
    private var cacheAll = false

    /** Drop the cache (call when packages change). */
    fun invalidate() { cache = null }

    /**
     * All launchable apps, sorted by name. TV (leanback) entry points win
     * over phone ones. [all] = also include phone-only apps.
     * Hidden apps are NOT filtered here — use [visible] for the home grid.
     */
    fun load(context: Context, all: Boolean = true): List<LauncherApp> {
        cache?.let { if (cacheAll == all) return it }
        val pm = context.packageManager
        val cats = if (all) listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                   else listOf(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val seen = LinkedHashMap<String, LauncherApp>()
        for (cat in cats) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val infos = try {
                if (Build.VERSION.SDK_INT >= 33)
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) { emptyList() }
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue
                seen.getOrPut(pkg) {
                    LauncherApp(pkg, ri.loadLabel(pm).toString(), ri.activityInfo,
                        cat == Intent.CATEGORY_LEANBACK_LAUNCHER)
                }
            }
        }
        val out = seen.values.sortedBy { it.label.lowercase() }
        cache = out; cacheAll = all
        return out
    }

    /** Apps for the home grid: respects "show phone apps" and hidden apps. */
    fun visible(c: Context): List<LauncherApp> {
        val hidden = BasePrefs.hiddenApps(c).toSet()
        return load(c, BasePrefs.showAllApps(c)).filter { it.packageName !in hidden }
    }

    fun find(context: Context, packageName: String): LauncherApp? =
        load(context, true).firstOrNull { it.packageName == packageName }
            ?: load(context, BasePrefs.showAllApps(context)).firstOrNull { it.packageName == packageName }

    fun label(c: Context, pkg: String): String = find(c, pkg)?.label ?: try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    /** TV banner (16:9) when the app has one, else null. */
    fun banner(c: Context, app: LauncherApp): Drawable? = try {
        app.activityInfo.loadBanner(c.packageManager)
            ?: app.activityInfo.applicationInfo.loadBanner(c.packageManager)
    } catch (e: Exception) { null }

    fun icon(c: Context, app: LauncherApp): Drawable? = try {
        app.activityInfo.loadIcon(c.packageManager)
    } catch (e: Exception) { null }

    /**
     * Launch an app. Tries the exact activity we listed first (TV apps
     * often only have a LEANBACK activity, and getLaunchIntentForPackage()
     * returns null for those), then the leanback front door, then the
     * normal front door. Returns false only when every attempt failed.
     */
    fun launch(context: Context, app: LauncherApp): Boolean {
        val pm = context.packageManager
        val candidates = listOfNotNull(
            Intent(Intent.ACTION_MAIN)
                .addCategory(if (app.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.activityInfo.name),
            pm.getLeanbackLaunchIntentForPackage(app.packageName),
            pm.getLaunchIntentForPackage(app.packageName)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) { /* next */ }
        }
        return false
    }

    fun launchPackage(context: Context, packageName: String): Boolean {
        find(context, packageName)?.let { return launch(context, it) }
        val pm = context.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }

    fun openAppInfo(c: Context, pkg: String) {
        try {
            c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { Ui.toast(c, "App info isn't available on this TV") }
    }

    fun uninstall(c: Context, pkg: String) {
        try {
            @Suppress("DEPRECATION")
            c.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) { openAppInfo(c, pkg) }
        }
    }
}
