package com.example.lifeos

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.lifeos.ui.navigation.LifeOSBottomNav
import com.example.lifeos.ui.navigation.LifeOSNavHost
import com.example.lifeos.ui.theme.LifeOSTheme
import com.example.lifeos.util.NotificationHelper
import com.example.lifeos.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    // Launcher for requesting POST_NOTIFICATIONS at runtime (required on Android 13+).
    // Without this, notifications/reminders silently never appear on modern devices.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No-op: if denied, reminders simply won't show. User can grant later from system settings. */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Root cause of "آلارمی زده نمی‌شه" on Android 12+ (API 31+): unlike
     * POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM can NOT be
     * granted through a normal runtime permission dialog
     * (ActivityResultContracts.RequestPermission does nothing for it). The
     * OS only lets the user grant it from a dedicated system settings
     * screen (ACTION_REQUEST_SCHEDULE_EXACT_ALARM), and on a sideloaded /
     * non-Play-Store build (exactly how this APK is distributed via GitHub
     * Actions) it starts OFF by default. Without this, AlarmScheduler's own
     * canScheduleExactAlarms() check silently falls back to an inexact
     * alarm on every single reminder/task alarm/focus session alarm in the
     * app, which most OEM battery optimizers delay by many minutes to hours
     * or drop outright -- from the user's perspective indistinguishable
     * from "the alarm never fired at all".
     *
     * This is only shown once per install (tracked via
     * hasRequestedExactAlarmPermission in PreferencesManager) so LifeOS
     * doesn't nag on every launch once the user has made a choice either way.
     */
    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return

        lifecycleScope.launch {
            val alreadyAsked = preferencesManager.hasRequestedExactAlarmPermission.first()
            if (alreadyAsked) return@launch

            preferencesManager.setHasRequestedExactAlarmPermission(true)
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Some OEM ROMs strip this action; the app must not crash
                // over it (prompt section 52). AlarmScheduler's own
                // inexact-alarm fallback still applies, and the user can
                // still find "Alarms & reminders" manually in system
                // Settings for this app.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel on app start
        NotificationHelper.createNotificationChannel(this)

        // Ask for notification permission on Android 13+ so reminders actually show.
        requestNotificationPermissionIfNeeded()

        // Ask for exact-alarm permission on Android 12+ so scheduled
        // reminders/task alarms/focus alarms actually fire on time instead
        // of being silently delayed or dropped by the OS.
        requestExactAlarmPermissionIfNeeded()

        setContent {
            val isDarkMode by preferencesManager.isDarkMode.collectAsState(initial = false)
            
            LifeOSTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { LifeOSBottomNav(navController = navController) }
                ) { innerPadding ->
                    LifeOSNavHost(
                        navController = navController,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
