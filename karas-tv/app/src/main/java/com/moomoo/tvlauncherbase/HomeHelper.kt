package com.moomoo.tvlauncherbase

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object HomeHelper {

    const val REQ_HOME_ROLE = 9001

    /** True when this app currently holds the HOME role / default launcher. */
    fun isDefaultHome(c: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            c.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val ri = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ri?.activityInfo?.packageName == c.packageName
        }
    }

    /** Fires the system "set default home app" prompt. Result lands in
        onActivityResult with REQ_HOME_ROLE; callers re-check isDefaultHome. */
    fun requestHomeRole(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    activity.startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE
                    )
                    return
                } catch (e: Exception) { /* fall through */ }
            } else if (rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                Ui.toast(activity, "Already the default home app")
                return
            }
        }
        openHomePicker(activity)
    }

    /** System "Home app" settings page, or the HOME chooser as a last resort. */
    fun openHomePicker(c: Context) {
        try {
            c.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {}
        try {
            c.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Choose home app"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Ui.toast(c, "Press Home on the remote and pick Kara's TV")
        }
    }

    /** Bring TV Launcher to the foreground (used by the guard + boot). */
    fun bringHome(c: Context) {
        val intent = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(intent)
        } catch (e: Exception) {
            // System may refuse a background start on some builds; the
            // watchdog notification is the fallback path.
        }
    }
}
