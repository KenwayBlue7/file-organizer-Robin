package com.kenway.robin.services

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kenway.robin.MainActivity
import com.kenway.robin.R
import com.kenway.robin.data.AppDatabase
import com.kenway.robin.data.ImageRepository
import com.kenway.robin.receiver.NotificationActionReceiver
import com.kenway.robin.util.NotificationHelper
import java.io.File

class ScreenshotMonitorService : Service() {
    private var fileObserver: FileObserver? = null
    private val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    companion object {
        private const val TAG = "ScreenshotMonitorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screenshot_monitor_channel"
        var isRunning = false
            private set

        fun startService(context: Context) {
            val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)  // Add this line
            }
        }

        fun stopService(context: Context) {
            val serviceIntent = Intent(context, ScreenshotMonitorService::class.java)
            context.stopService(serviceIntent)
        }
    }

    private lateinit var screenshotObserver: ContentObserver
    private var lastProcessedUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created.")
        createNotificationChannel()
        screenshotObserver = ScreenshotObserver(Handler(Looper.getMainLooper()))
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
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

            startMonitoring()
            isRunning = true
            Log.d(TAG, "Screenshot monitoring started")
        }
        // If the service is killed, it will be automatically restarted
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed.")
        stopMonitoring()
        isRunning = false
        Log.d(TAG, "Screenshot monitoring stopped")
        contentResolver.unregisterContentObserver(screenshotObserver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    private fun startMonitoring() {
        fileObserver = object : FileObserver(screenshotDir, FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && isScreenshotFile(path)) {
                    Log.d(TAG, "New screenshot detected: $path")
                    handleNewScreenshot(File(screenshotDir, path))
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun stopMonitoring() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    private fun isScreenshotFile(filename: String): Boolean {
        val lowercaseName = filename.lowercase()
        return (lowercaseName.contains("screenshot") || lowercaseName.contains("screen")) &&
                (lowercaseName.endsWith(".png") || lowercaseName.endsWith(".jpg") || lowercaseName.endsWith(".jpeg"))
    }

    private fun handleNewScreenshot(file: File) {
        // Fix: Use applicationContext instead of application
        // val repository = ImageRepository(applicationContext as Application, AppDatabase.getInstance(applicationContext).tagDao())
        val intent = Intent("com.kenway.robin.SCREENSHOT_DETECTED")
        intent.putExtra("screenshot_uri", file.toString())
        sendBroadcast(intent)
        // You could add it to a database or trigger a notification
        // For now, just log it
        Log.d(TAG, "Processing new screenshot: ${file.absolutePath}")
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
            .setSmallIcon(R.drawable.ic_notification) // Replace with a proper icon
            .setContentTitle("Screenshot Saved")
            .setContentText("Tap to organize or swipe to dismiss.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true) // Notification dismisses when tapped
            .addAction(R.drawable.ic_notification, "Delete", deletePendingIntent) // Replace with a proper delete icon
            .build()

        // Show the notification
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notification)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot Monitor")
            .setContentText("Monitoring for new screenshots")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screenshot Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for new screenshots"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
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
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            
            // Add selection clause to filter for Screenshots folder
            val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf("Screenshots")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,        // Filter by Screenshots folder
                selectionArgs,    // Arguments for the filter
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                    
                    Log.d(TAG, "Screenshot detected in bucket: $bucketName, path: $path")
                    
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
