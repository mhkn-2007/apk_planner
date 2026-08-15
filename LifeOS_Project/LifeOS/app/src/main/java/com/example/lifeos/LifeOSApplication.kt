package com.example.lifeos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LifeOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization code (e.g. WorkManager custom configuration if needed)
    }
}
