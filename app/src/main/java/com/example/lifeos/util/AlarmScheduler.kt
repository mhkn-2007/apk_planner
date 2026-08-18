package com.example.lifeos.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor() {

    fun scheduleAlarm(context: Context, taskId: String, title: String, triggerAtMillis: Long, isAlarmRing: Boolean = true) {
        scheduleAlarmInternal(context, requestKey = taskId, taskId = taskId, title = title, message = null, triggerAtMillis = triggerAtMillis, isAlarmRing = isAlarmRing)
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
        triggerAtMillis: Long,
        isAlarmRing: Boolean = true
    ) {
        scheduleAlarmInternal(
            context,
            requestKey = reminderId,
            taskId = taskId,
            reminderId = reminderId,
            title = title,
            message = message,
            triggerAtMillis = triggerAtMillis,
            isAlarmRing = isAlarmRing
        )
    }

    fun cancelReminderAlarm(context: Context, reminderId: String) {
        cancelAlarmInternal(context, requestKey = reminderId)
    }

    /**
     * Schedules the "session finished" alarm for a Focus/Pomodoro session
     * (prompt section 18) so the user is notified even if LifeOS isn't in
     * the foreground when the timer reaches zero. Keyed by [sessionId] so a
     * concurrent reminder/other focus session never overwrites this one.
     */
    fun scheduleFocusSessionAlarm(
        context: Context,
        sessionId: String,
        message: String,
        triggerAtMillis: Long
    ) {
        scheduleAlarmInternal(
            context,
            requestKey = "focus_$sessionId",
            taskId = sessionId,
            title = "پایان جلسه‌ی فوکوس",
            message = message,
            triggerAtMillis = triggerAtMillis
        )
    }

    fun cancelFocusSessionAlarm(context: Context, sessionId: String) {
        cancelAlarmInternal(context, requestKey = "focus_$sessionId")
    }

    private fun scheduleAlarmInternal(
        context: Context,
        requestKey: String,
        taskId: String,
        title: String,
        message: String?,
        triggerAtMillis: Long,
        reminderId: String? = null,
        isAlarmRing: Boolean = true
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("TASK_TITLE", title)
            putExtra("IS_ALARM_RING", isAlarmRing)
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

    /**
     * [AlarmReceiver] is instantiated by the Android framework, not by
     * Hilt, so it can't take an @Inject constructor. This is the standard
     * Hilt pattern for reaching a @Singleton from a framework-constructed
     * class: EntryPoints.get() pulls the exact same app-wide
     * PreferencesManager instance every other injected class uses, instead
     * of `PreferencesManager(context)` constructing a second, separate
     * instance that happens to read the same backing files.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PreferencesManagerEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("TASK_TITLE") ?: "یادآوری"
        val message = intent.getStringExtra("REMINDER_MESSAGE")
        val reminderId = intent.getStringExtra("REMINDER_ID")
        val taskId = intent.getStringExtra("TASK_ID") ?: return
        val isAlarmRing = intent.getBooleanExtra("IS_ALARM_RING", true)

        // Respect the user's notification toggle in Settings (prompt section
        // 45: "Users must be able to control notification preferences").
        // Without this check the toggle only changed a stored flag that
        // nothing ever read — exactly the "fake button" prompt section 62
        // forbids. goAsync() keeps the receiver (and its process) alive long
        // enough for the one-shot suspend read, since onReceive itself can't
        // suspend.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferencesManager = EntryPoints.get(
                    context.applicationContext,
                    PreferencesManagerEntryPoint::class.java
                ).preferencesManager()
                val notificationsEnabled = preferencesManager.isNotificationsEnabled.first()
                if (notificationsEnabled) {
                    if (isAlarmRing) {
                        // Launch the full-screen ringing alarm directly
                        // (prompt sections 10/45: reminders must reliably
                        // reach the user). A plain notification can be
                        // silenced by the phone's ringer/DND state and is
                        // easy to swipe away without reading — this instead
                        // behaves like a phone clock alarm: it rings,
                        // vibrates, and requires an explicit dismissal.
                        // FLAG_ACTIVITY_NEW_TASK is required since we're
                        // starting an Activity from a BroadcastReceiver's
                        // application context, not from an existing Activity.
                        val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("ALARM_TITLE", title)
                            putExtra("ALARM_MESSAGE", message)
                            putExtra("ALARM_ID", (reminderId ?: taskId).hashCode())
                        }
                        context.startActivity(ringIntent)
                    } else {
                        NotificationHelper.createNotificationChannel(context)
                        NotificationHelper.showNotification(
                            context,
                            notificationId = (reminderId ?: taskId).hashCode(),
                            title = "یادآوری LifeOS",
                            content = message ?: title
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
