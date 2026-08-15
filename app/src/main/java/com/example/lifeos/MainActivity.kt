package com.example.lifeos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.navigation.compose.rememberNavController
import com.example.lifeos.ui.navigation.LifeOSBottomNav
import com.example.lifeos.ui.navigation.LifeOSNavHost
import com.example.lifeos.ui.theme.LifeOSTheme
import com.example.lifeos.util.NotificationHelper
import com.example.lifeos.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel on app start
        NotificationHelper.createNotificationChannel(this)

        // Ask for notification permission on Android 13+ so reminders actually show.
        requestNotificationPermissionIfNeeded()

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
