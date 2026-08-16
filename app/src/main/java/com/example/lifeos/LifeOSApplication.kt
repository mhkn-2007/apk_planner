package com.example.lifeos

import android.app.Application
import com.example.lifeos.util.PreferencesManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LifeOSApplication : Application() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // One-time migration: move any pre-existing plain-text API key into
        // the new EncryptedSharedPreferences-backed store (prompt section
        // 51). Must run once per process start, before any screen might
        // read PreferencesManager.apiKey, so it belongs here rather than in
        // a specific screen's ViewModel.
        applicationScope.launch {
            preferencesManager.migrateLegacyApiKeyIfNeeded()
        }
    }
}
