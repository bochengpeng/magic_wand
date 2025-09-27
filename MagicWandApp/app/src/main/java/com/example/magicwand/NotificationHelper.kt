package com.example.magicwand

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {

    const val CHANNEL_ID = "shake_channel"
    private const val CHANNEL_NAME = "Shake Events"
    private const val CHANNEL_DESC = "Notification for phone shake events"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = CHANNEL_DESC
                    }
                )
            }
        }
    }

}