package com.kenway.robin.services

import android.app.ActivityManager
import android.content.Context

object ServiceHelper {

    /**
     * Checks if a service of the given class is currently running.
     * Note: getRunningServices is deprecated for API 26+ and may not work reliably
     * for background services on modern Android versions, but is suitable for this app's foreground service.
     */
    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}