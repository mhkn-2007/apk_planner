package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val isDarkMode = preferencesManager.isDarkMode
    val isNotificationsEnabled = preferencesManager.isNotificationsEnabled

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDarkMode(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val isDarkModeFlow by viewModel.isDarkMode.collectAsState(initial = false)
    val notificationsEnabledFlow by viewModel.isNotificationsEnabled.collectAsState(initial = true)

    // Using local state initialized from Flow to ensure instant visual updates on toggle
    var isDarkMode by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(isDarkModeFlow) {
        isDarkMode = isDarkModeFlow
    }
    LaunchedEffect(notificationsEnabledFlow) {
        notificationsEnabled = notificationsEnabledFlow
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "تنظیمات",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Dark Mode
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("پوسته تاریک", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("حالت شب برای راحتی چشم", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { 
                            isDarkMode = it
                            viewModel.setDarkMode(it) 
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("اعلانات", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("یادآوری کارها و عادت‌ها", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { 
                            notificationsEnabled = it
                            viewModel.setNotificationsEnabled(it) 
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Provider
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Column {
                    Text("هوش مصنوعی", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("در حال استفاده: Mock AI (آزمایشی)", color = AccentTeal, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("برای اتصال به Gemini یا ChatGPT، کلید API خود را وارد کنید", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Version
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("نسخه برنامه", color = TextPrimary)
                    Text("2.0.0", color = AccentBlue)
                }
            }
        }
    }
}
