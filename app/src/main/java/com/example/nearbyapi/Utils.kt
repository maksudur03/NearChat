package com.example.nearbyapi

import android.app.PendingIntent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M

/**
 * @author Munif
 * @since 5/11/24.
 */

object Utils {
    const val FOREGROUND_NOTIFICATION_REQUEST_CODE = 1
    const val BG_NOTIFICATION_CHANNEL_ID = "bg_notification"

    fun isBelowSdk23(): Boolean {
        return SDK_INT < M
    }

    fun getNotificationUpdateCurrentFlags(): Int {
        return if (isBelowSdk23()) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_IMMUTABLE
    }
}