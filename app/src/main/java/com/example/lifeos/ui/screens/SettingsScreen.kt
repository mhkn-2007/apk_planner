package com.example.lifeos.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

            // Exact Alarm Permission Status — this is the #1 real-world
            // cause of "آلارمی زده نمی‌شه" reports: on Android 12+,
            // SCHEDULE_EXACT_ALARM can't be granted through a normal
            // in-app permission dialog, only from this dedicated system
            // screen, and it defaults to OFF for a sideloaded APK like
            // this one. MainActivity already redirects here once
            // automatically on first launch, but this card lets the user
            // fix it manually afterward too (e.g. if they dismissed it, or
            // an OEM later revoked it).
            val context = LocalContext.current
            var canScheduleExactAlarms by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                }
            }
            if (!canScheduleExactAlarms) {
                Box(
                    modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("زمان‌بندی دقیق آلارم غیرفعال است", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "بدون این مجوز، یادآوری‌ها و آلارم‌ها ممکن است دیر یا اصلاً نمایش داده نشوند.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // OEM stripped this action; nothing more LifeOS can do from here.
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("فعال‌سازی در تنظیمات سیستم")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

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


