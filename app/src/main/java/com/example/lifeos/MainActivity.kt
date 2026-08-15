package com.example.lifeos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel on app start
        NotificationHelper.createNotificationChannel(this)

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
