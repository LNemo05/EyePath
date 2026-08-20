package org.walkguard.app.intervention

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.provider.Settings
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.walkguard.app.MainActivity
import org.walkguard.app.R
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.ui.i18n.formatGuardStatusNotificationText

class WalkGuardNotificationManager(
    private val context: Context,
    private val notificationManager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
) : MildInterventionNotifier {
    data class GuardStatus(
        val mode: GuardMode,
        val detectionState: String
    )

    init {
        createChannels()
    }

    fun createChannels() {
        val manager = notificationManager ?: return
        // New channel id: importance/sound are immutable after first create on a device.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GUARD_STATUS,
                context.getString(R.string.notification_channel_guard_status),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_guard_status_desc)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WALK_WARNING,
                context.getString(R.string.notification_channel_walk_warning),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_walk_warning_desc)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                val alertSound = Settings.System.DEFAULT_NOTIFICATION_URI
                setSound(
                    alertSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    fun buildStatusNotification(status: GuardStatus): Notification {
        return NotificationCompat.Builder(context, CHANNEL_GUARD_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_guard_active))
            .setContentText(context.formatGuardStatusNotificationText(status.mode, status.detectionState))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(mainActivityPendingIntent())
            .build()
    }

    override fun sendMildWarning(context: InterventionContext) {
        ensureNotificationsAvailable()
        val notification = NotificationCompat.Builder(this.context, CHANNEL_WALK_WARNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.settings.warningTitle)
            .setContentText(context.settings.warningMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.settings.warningMessage))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent())
            .build()
        notificationManager?.notify(NOTIFICATION_ID_MILD_WARNING, notification)
            ?: throw IllegalStateException("NotificationManager service is unavailable")
    }

    fun updateStatusNotification(notification: Notification) {
        notificationManager?.notify(NOTIFICATION_ID_GUARD_STATUS, notification)
            ?: throw IllegalStateException("NotificationManager service is unavailable")
    }

    private fun ensureNotificationsAvailable() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            throw SecurityException(context.getString(R.string.error_notifications_disabled))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException(context.getString(R.string.error_notification_permission))
        }
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_GUARD_STATUS = "guard_status"
        /** New id so upgrades get IMPORTANCE_HIGH + heads-up defaults (channels are immutable after create). */
        const val CHANNEL_WALK_WARNING = "walk_warning_heads_up"
        const val NOTIFICATION_ID_GUARD_STATUS = 1001
        const val NOTIFICATION_ID_MILD_WARNING = 1002
        private const val REQUEST_CODE_MAIN_ACTIVITY = 2001
    }
}
