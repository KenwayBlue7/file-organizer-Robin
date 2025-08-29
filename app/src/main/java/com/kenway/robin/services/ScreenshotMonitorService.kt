package com.kenway.robin.services

import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kenway.robin.MainActivity
import com.kenway.robin.R
import com.kenway.robin.receiver.NotificationActionReceiver
import com.kenway.robin.util.NotificationHelper

private const val TAG = "ScreenshotMonitor"
private const val NOTIFICATION_ID = 1

class ScreenshotMonitorService : Service() {

    private lateinit var screenshotObserver: ContentObserver
    private var lastProcessedUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created.")
        screenshotObserver = ScreenshotObserver(Handler(Looper.getMainLooper()))
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting command.")

        // Create the notification channel for Android O+
        NotificationHelper.createNotificationChannel(this)

        // Create the persistent notification for the foreground service
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Organizer is running")
            .setContentText("Monitoring for new screenshots")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your actual notification icon
            .setOngoing(true) // Makes the notification persistent
            .build()

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification)

        // If the service is killed, it will be automatically restarted
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed.")
        contentResolver.unregisterContentObserver(screenshotObserver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    private fun showScreenshotNotification(uri: Uri) {
        Log.d(TAG, "New screenshot detected, preparing notification for: $uri")
        val notificationId = System.currentTimeMillis().toInt()

        // Intent for the main tap action (open the app)
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the "Delete" action button
        val deleteIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DELETE
            putExtra(NotificationActionReceiver.EXTRA_URI, uri.toString())
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deletePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId, // Use a unique request code
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a proper icon
            .setContentTitle("Screenshot Saved")
            .setContentText("Tap to organize or swipe to dismiss.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true) // Notification dismisses when tapped
            .addAction(R.drawable.ic_launcher_foreground, "Delete", deletePendingIntent) // Replace with a proper delete icon
            .build()

        // Show the notification
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notification)
        }
    }

    private inner class ScreenshotObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            queryLatestImage()
        }

        private fun queryLatestImage() {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    if (path.contains("Screenshots", ignoreCase = true)) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val screenshotUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        // Avoid processing the same screenshot multiple times
                        if (screenshotUri != lastProcessedUri) {
                            lastProcessedUri = screenshotUri
                            showScreenshotNotification(screenshotUri)
                        }
                    }
                }
            }
        }
    }
}
