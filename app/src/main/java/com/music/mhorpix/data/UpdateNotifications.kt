package com.music.mhorpix.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.music.mhorpix.MainActivity
import com.music.mhorpix.R

/**
 * System-tray notification for a new Mhorpix release.
 *
 * Posted once per new version: the first launch that detects a release the
 * user has never seen before fires this, then remembers the version so it
 * does not re-post for the same release on subsequent launches.
 *
 * Tapping the notification opens the app and triggers the in-app update
 * dialog directly via [UpdateDeepLink] -- the round trip stays inside
 * Mhorpix rather than bouncing through a browser.
 */
object UpdateNotifications {

    private const val CHANNEL_ID = "mhorpix_updates"
    private const val NOTIFICATION_ID = 0x4D55 // 'MU' -- Mhorpix Update

    /**
     * Posts (or replaces) the update notification in the system shade.
     *
     * Every call is caught: a missing permission or a closed NotificationManager
     * must not interrupt the rest of the update-check path.
     */
    fun post(context: Context, version: String) {
        runCatching {
            val manager =
                context.getSystemService(NotificationManager::class.java) ?: return

            // createNotificationChannel is idempotent after the first call.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.update_channel_description)
                },
            )

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(UpdateDeepLink.EXTRA_OPEN_UPDATE, true)
                // Bring the existing task to the front rather than stacking
                // a new Activity on top of whatever is already running.
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(
                    context.getString(R.string.update_notification_body, version),
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.update_notification_body, version)),
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** Clears the notification from the shade if it is still visible. */
    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(NOTIFICATION_ID)
        }
    }
}
