package com.example.lifeos.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor() {

    fun scheduleAlarm(context: Context, taskId: String, title: String, triggerAtMillis: Long) {
        scheduleAlarmInternal(context, requestKey = taskId, taskId = taskId, title = title, message = null, triggerAtMillis = triggerAtMillis)
    }

    fun cancelAlarm(context: Context, taskId: String) {
        cancelAlarmInternal(context, requestKey = taskId)
    }

    /**
     * Schedules an alarm for a single reminder that belongs to a task. Unlike
     * [scheduleAlarm], the PendingIntent is keyed by the reminder's own id so
     * multiple reminders on the same task (required by prompt section 10)
     * don't overwrite each other's alarm.
     */
    fun scheduleReminderAlarm(
        context: Context,
        reminderId: String,
        taskId: String,
        title: String,
        message: String?,
        triggerAtMillis: Long
    ) {
        scheduleAlarmInternal(
            context,
            requestKey = reminderId,
            taskId = taskId,
            reminderId = reminderId,
            title = title,
            message = message,
            triggerAtMillis = triggerAtMillis
        )
    }

    fun cancelReminderAlarm(context: Context, reminderId: String) {
        cancelAlarmInternal(context, requestKey = reminderId)
    }

    private fun scheduleAlarmInternal(
        context: Context,
        requestKey: String,
        taskId: String,
        title: String,
        message: String?,
        triggerAtMillis: Long,
        reminderId: String? = null
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("TASK_TITLE", title)
            if (message != null) putExtra("REMINDER_MESSAGE", message)
            if (reminderId != null) putExtra("REMINDER_ID", reminderId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // On Android 12+ (S), scheduling an *exact* alarm requires the
        // SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM permission to actually be granted
        // (declaring it in the manifest alone is not enough on some OEMs/versions).
        // If it's not available, fall back to an inexact-but-still-timely alarm
        // instead of letting setExactAndAllowWhileIdle throw a SecurityException.
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Extremely defensive fallback: some OEMs revoke the permission after grant.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarmInternal(context: Context, requestKey: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("TASK_TITLE") ?: "یادآوری"
        val message = intent.getStringExtra("REMINDER_MESSAGE")
        val reminderId = intent.getStringExtra("REMINDER_ID")
        val taskId = intent.getStringExtra("TASK_ID") ?: return

        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.showNotification(
            context,
            notificationId = (reminderId ?: taskId).hashCode(),
            title = "یادآوری LifeOS",
            content = message ?: title
        )
    }
}
