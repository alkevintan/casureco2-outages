package com.casureco2.outages.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.casureco2.outages.R

object NotificationHelper {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Config.NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_description)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showRunning(context: Context) {
        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Scraping in progress")
            .setContentText("Fetching CASURECO 2 advisories...")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    fun showSuccess(context: Context, outages: Int, barangays: Int) {
        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sync complete")
            .setContentText("$outages outages updated across $barangays barangays")
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(2, notification)
        manager.cancel(1)
    }

    fun showFailed(context: Context, workerName: String, error: String) {
        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sync failed")
            .setContentText("$workerName: $error")
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(3, notification)
        manager.cancel(1)
    }

    fun showSessionExpired(context: Context) {
        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Action needed")
            .setContentText("Tap to re-login to Facebook")
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(4, notification)
        manager.cancel(1)
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }
}
