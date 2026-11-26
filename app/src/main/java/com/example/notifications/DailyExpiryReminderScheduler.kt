package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PantryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "DailyExpiryReminder"
private const val CHANNEL_ID = "pantrypulse_daily_expiry_alerts"
private const val NOTIFICATION_ID = 4108
private const val PREFS_NAME = "pantrypulse_expiry_reminders"
private const val KEY_ENABLED = "daily_reminder_enabled"
private const val KEY_HOUR = "daily_reminder_hour"
private const val REQUEST_CODE = 41080

data class ExpiryReminderSettings(
    val enabled: Boolean = false,
    val reminderHour: Int = 9
)

/**
 * Play-Policy compliant local morning expiry alert scheduler using battery-friendly
 * `AlarmManager.setInexactRepeating` (no restricted `SCHEDULE_EXACT_ALARM` permission needed).
 */
object DailyExpiryReminderScheduler {

    fun getSettings(context: Context): ExpiryReminderSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ExpiryReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            reminderHour = prefs.getInt(KEY_HOUR, 9)
        )
    }

    /**
     * Re-arms the daily inexact repeating alarm if reminders are enabled (for example after a device reboot
     * or process update when the OS clears previous AlarmManager registrations).
     */
    fun rescheduleIfEnabled(context: Context) {
        val settings = getSettings(context)
        if (settings.enabled) {
            setDailyReminderEnabled(context, enabled = true, hourOfDay = settings.reminderHour)
        }
    }

    fun setDailyReminderEnabled(context: Context, enabled: Boolean, hourOfDay: Int = 9): ExpiryReminderSettings {
        val appContext = context.applicationContext
        val clampedHour = hourOfDay.coerceIn(6, 21)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, clampedHour)
            .apply()

        ensureNotificationChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pendingIntent = buildPendingIntent(appContext)

        if (enabled && alarmManager != null) {
            val triggerAt = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, clampedHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }.timeInMillis

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } else {
            alarmManager?.cancel(pendingIntent)
        }

        return ExpiryReminderSettings(enabled = enabled, reminderHour = clampedHour)
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.expiry_alert_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.expiry_alert_channel_desc)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    suspend fun postImmediateExpirySummaryNotification(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        ensureNotificationChannel(appContext)
        val expiring = PantryDatabase.getInstance(appContext)
            .pantryDao()
            .getActiveItemsSnapshot()
            .filter { it.effectiveDaysUntilExpiry <= 2 }
            .sortedBy { it.effectiveDaysUntilExpiry }

        val title: String
        val body: String
        if (expiring.isEmpty()) {
            title = appContext.getString(R.string.expiry_alert_all_fresh_title)
            body = appContext.getString(R.string.expiry_alert_all_fresh_body)
        } else {
            val topNames = expiring.take(2).joinToString(" & ") { it.name }
            title = appContext.getString(R.string.expiry_alert_urgent_title_fmt, expiring.size)
            body = appContext.getString(R.string.expiry_alert_urgent_body_fmt, topNames)
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE + 1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied", e)
            false
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyExpiryReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class DailyExpiryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyExpiryReminderScheduler.postImmediateExpirySummaryNotification(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post scheduled expiry reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
