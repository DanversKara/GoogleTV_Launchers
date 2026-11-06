package com.moomoo.tvlauncherbase

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * The boot guard. On every boot:
 *  - device-owner mode: re-assert the persistent HOME binding and re-hide
 *    the stock launcher (DevicePolicyManager calls, not shell, so they
 *    work without root);
 *  - "Boot on Power" on: open TV Launcher Base as the TV powers on;
 *  - always: arm the watchdog alarm that checks every 30 minutes whether
 *    another launcher took over, and notifies you if so.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "com.moomoo.tvlauncherbase.CHECK_HOME"
        private const val CHANNEL = "base_guard"

        fun scheduleWatchdog(c: Context) {
            val am = c.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                c, 0,
                Intent(c, BootReceiver::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 15 * 60 * 1000L,
                30 * 60 * 1000L,
                pi
            )
        }
    }

    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                ServiceSetup.selfEnable(c)
                if (DeviceOwnerHelper.isDeviceOwner(c)) {
                    DeviceOwnerHelper.assertHomeOwnership(c)
                    DeviceOwnerHelper.setGoogleLauncherHidden(
                        c, BasePrefs.hideGoogleLauncher(c)
                    )
                }
                scheduleWatchdog(c)
                if (BasePrefs.bootOnPower(c)) {
                    HomeHelper.bringHome(c)
                }
            }
            ACTION_CHECK -> {
                ServiceSetup.selfEnable(c)
                if (DeviceOwnerHelper.isDeviceOwner(c)) {
                    DeviceOwnerHelper.assertHomeOwnership(c)
                    return
                }
                if (!HomeHelper.isDefaultHome(c)) {
                    showTakeoverNotification(c)
                }
            }
        }
    }

    private fun showTakeoverNotification(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Launcher guard", NotificationManager.IMPORTANCE_HIGH)
        )
        val tap = PendingIntent.getActivity(
            c, 0,
            Intent(c, HomeGuardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(c.getString(R.string.taken_over_title))
            .setContentText(c.getString(R.string.taken_over_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(42, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; nothing we can do silently.
        }
    }
}
