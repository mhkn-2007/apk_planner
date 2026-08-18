package com.example.lifeos.util

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifeos.ui.theme.AccentBlue
import com.example.lifeos.ui.theme.AccentRed
import com.example.lifeos.ui.theme.GradientEnd
import com.example.lifeos.ui.theme.GradientMiddle
import com.example.lifeos.ui.theme.GradientStart
import com.example.lifeos.ui.theme.LifeOSTheme

/**
 * Full-screen ringing alarm, launched by [AlarmReceiver] when a task's
 * reminder has [com.example.lifeos.data.database.entities.TaskEntity.isAlarmRing]
 * (or the equivalent reminder flag) set — prompt sections 10/45: reminders
 * must reliably reach the user. Unlike a plain notification, this:
 * - shows over the lock screen and turns the screen on, so it isn't missed
 *   while the phone is idle
 * - plays the device's default alarm sound on loop and vibrates
 *   continuously until dismissed, instead of a single silent/short buzz
 *   that follows the phone's ringer/DND state
 * - has no swipe-away shortcut — the only way out is the explicit "خاموش
 *   کردن" button, the same contract a phone clock alarm has
 *
 * This intentionally does not touch [TaskDao]/[TaskRepository] — it is a
 * pure presentation layer for "the alarm is ringing right now" and stops
 * itself as soon as the user dismisses it.
 */
class AlarmRingActivity : ComponentActivity() {

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLockedAndTurnScreenOn()

        val title = intent.getStringExtra("ALARM_TITLE") ?: "یادآوری"
        val message = intent.getStringExtra("ALARM_MESSAGE")

        startRinging()

        setContent {
            LifeOSTheme {
                AlarmRingScreen(
                    title = title,
                    message = message,
                    onDismiss = {
                        stopRinging()
                        finish()
                    }
                )
            }
        }
    }

    private fun setShowWhenLockedAndTurnScreenOn() {
        // Show over the lock screen and wake the device — without these,
        // an alarm scheduled while the phone is locked/asleep would only
        // silently start an Activity nobody sees until they unlock the
        // phone themselves, which is exactly the "alarm didn't go off"
        // symptom this activity exists to fix.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRinging() {
        try {
            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    isLooping = true
                }
                play()
            }
        } catch (e: Exception) {
            // If the device has no default alarm sound configured or
            // playback fails for any reason, the vibration below still
            // gets the user's attention — never let a sound failure crash
            // the alarm screen itself (prompt section 52: gracefully
            // handle errors, never let a recoverable one crash).
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            // Same defensive reasoning as above — sound already covers this if vibration is unavailable.
        }
    }

    private fun stopRinging() {
        try { ringtone?.stop() } catch (e: Exception) { /* already stopped/unavailable */ }
        try { vibrator?.cancel() } catch (e: Exception) { /* already stopped/unavailable */ }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

@Composable
private fun AlarmRingScreen(
    title: String,
    message: String?,
    onDismiss: () -> Unit
) {
    val bgGradient = Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))

    Box(
        modifier = Modifier.fillMaxSize().background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                modifier = Modifier.height(56.dp).fillMaxWidth(0.7f)
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("خاموش کردن", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
