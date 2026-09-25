package com.mkdev.mkboardgames

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Owns the opt-in daily tester reminder.
 *
 * The reminder is a one-shot alarm rather than a repeating alarm. Each time
 * the app comes to the foreground the old alarm is cancelled and a new one is
 * scheduled for 24 hours later, so an active tester is never reminded right
 * after opening the app.
 */
object DailyReminderManager {
    private const val CHANNEL_ID = "daily_game_reminders"
    private const val NOTIFICATION_ID = 2401
    private const val REQUEST_CODE = 2401
    private const val ACTION_SHOW_REMINDER =
        "com.mkdev.mkboardgames.action.SHOW_DAILY_REMINDER"
    private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2402

    fun onAppForeground(context: Context) {
        val appContext = context.applicationContext
        val openedAt = System.currentTimeMillis()
        SettingsManager.recordAppOpened(appContext, openedAt)
        if (SettingsManager.isDailyRemindersEnabled(appContext) && canPostNotifications(appContext)) {
            scheduleNext(appContext, openedAt + DAY_MILLIS)
        } else {
            cancel(appContext)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        SettingsManager.setDailyRemindersEnabled(appContext, enabled)
        if (enabled && canPostNotifications(appContext)) {
            scheduleNext(appContext, System.currentTimeMillis() + DAY_MILLIS)
        } else if (!enabled) {
            cancel(appContext)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(reminderPendingIntent(appContext))
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun scheduleNext(context: Context, triggerAtMillis: Long) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = reminderPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L),
            pendingIntent,
        )
    }

    private fun reminderPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyReminderReceiver::class.java).setAction(ACTION_SHOW_REMINDER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Daily game reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Optional reminders to play MK Board Games."
                },
            )
        }
    }

    private fun showReminder(context: Context) {
        if (!SettingsManager.isDailyRemindersEnabled(context) || !canPostNotifications(context)) {
            cancel(context)
            return
        }

        val now = System.currentTimeMillis()
        val lastOpenedAt = SettingsManager.lastAppOpenedAt(context)
        val nextAllowedReminder = lastOpenedAt + DAY_MILLIS
        if (lastOpenedAt > 0L && now < nextAllowedReminder) {
            scheduleNext(context, nextAllowedReminder)
            return
        }

        ensureChannel(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New day, new game")
            .setContentText("Have some fun with MK Board Games")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Have some fun with MK Board Games"),
            )
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            scheduleNext(context, now + DAY_MILLIS)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
            cancel(context)
        }
    }

    class DailyReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == ACTION_SHOW_REMINDER) {
                showReminder(context.applicationContext)
            }
        }
    }
}