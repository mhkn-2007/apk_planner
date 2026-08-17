package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    val apiKey = preferencesManager.apiKey

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDarkMode(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { preferencesManager.setApiKey(key) }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val isDarkModeFlow by viewModel.isDarkMode.collectAsState(initial = false)
    val notificationsEnabledFlow by viewModel.isNotificationsEnabled.collectAsState(initial = true)
    val apiKeyFlow by viewModel.apiKey.collectAsState(initial = "")

    var isDarkMode by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isDarkModeFlow) {
        isDarkMode = isDarkModeFlow
    }
    LaunchedEffect(notificationsEnabledFlow) {
        notificationsEnabled = notificationsEnabledFlow
    }
    LaunchedEffect(apiKeyFlow) {
        apiKeyInput = apiKeyFlow
    }

    // Dynamic gradient backgrounds depending on Light/Dark mode
    val isLight = !LocalIsDarkTheme.current
    val bgGradient = if (isLight) {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "تنظیمات",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Dark Mode Toggle
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("پوسته تاریک", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                        Text("حالت شب برای راحتی چشم", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
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

            // Notifications Toggle
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("اعلانات", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                        Text("یادآوری کارها و عادت‌ها روی گوشی", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
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

            // API Key Input
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Column {
                    Text("تنظیم دستیار هوشمند", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { 
                            apiKeyInput = it
                            viewModel.setApiKey(it)
                        },
                        label = { Text("Gemini API Key") },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "نمایش کلید",
                                    tint = AccentBlue
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "با وارد کردن کلید خود، دستیار هوشمند به جای حالت ماک (آفلاین) به شبکه متصل خواهد شد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Version Info
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("نسخه برنامه", color = MaterialTheme.colorScheme.onBackground)
                    Text("2.5.0", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


