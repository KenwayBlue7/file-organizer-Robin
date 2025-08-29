package com.kenway.robin.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "NotificationReceiver"

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DELETE = "com.kenway.robin.ACTION_DELETE"
        const val EXTRA_URI = "com.kenway.robin.EXTRA_URI"
        const val EXTRA_NOTIFICATION_ID = "com.kenway.robin.EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        if (action == ACTION_DELETE) {
            val uriString = intent.getStringExtra(EXTRA_URI)
            if (uriString != null) {
                try {
                    val uri = Uri.parse(uriString)
                    val rowsDeleted = context.contentResolver.delete(uri, null, null)
                    if (rowsDeleted > 0) {
                        Log.d(TAG, "Successfully deleted screenshot: $uri")
                    } else {
                        Log.w(TAG, "Failed to delete screenshot, file not found: $uri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting screenshot from notification action", e)
                }
            } else {
                Log.w(TAG, "Delete action received but no URI was provided.")
            }
        }

        // Dismiss the notification after the action is handled
        if (notificationId != 0) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "Dismissed notification with ID: $notificationId")
        }
    }
}
