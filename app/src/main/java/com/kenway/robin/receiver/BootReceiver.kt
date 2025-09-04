package com.kenway.robin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.kenway.robin.services.ScreenshotMonitorService

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "robin_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed event received")
            
            // Check if screenshot monitoring was enabled before reboot
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wasServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
            
            if (wasServiceEnabled) {
                Log.d(TAG, "Screenshot monitoring was enabled before reboot, restarting service")
                try {
                    ScreenshotMonitorService.startService(context)
                    Log.d(TAG, "Screenshot monitoring service restarted successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart screenshot monitoring service after boot", e)
                }
            } else {
                Log.d(TAG, "Screenshot monitoring was disabled, not restarting service")
            }
        }
    }
}
