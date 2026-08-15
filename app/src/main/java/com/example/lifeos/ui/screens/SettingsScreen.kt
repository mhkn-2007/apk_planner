package com.example.lifeos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") }, // Persian: Settings
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "تنظیمات عمومی",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            ListItem(
                headlineContent = { Text("پوسته تاریک (Dark Mode)") },
                trailingContent = { Switch(checked = false, onCheckedChange = {}) }
            )
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text("اعلانات (Notifications)") },
                trailingContent = { Switch(checked = true, onCheckedChange = {}) }
            )
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text("پیکربندی هوش مصنوعی (AI Provider)") },
                supportingContent = { Text("کلید API خود را وارد کنید (اختیاری)") }
            )
            HorizontalDivider()
        }
    }
}
