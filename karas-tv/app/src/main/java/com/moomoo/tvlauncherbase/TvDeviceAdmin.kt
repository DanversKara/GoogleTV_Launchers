package com.moomoo.tvlauncherbase

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Device-admin receiver for the optional device-owner lockdown. */
class TvDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        DeviceOwnerHelper.assertHomeOwnership(context)
    }
}
