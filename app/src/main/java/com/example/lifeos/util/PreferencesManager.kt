package com.example.lifeos.util

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lifeos_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
        private const val ENCRYPTED_PREFS_NAME = "lifeos_secure_prefs"
        private const val API_KEY_PREF = "gemini_api_key"
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE_KEY] ?: false
    }

    val isNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_KEY] ?: true
    }

    // The Gemini API key is a credential, not an ordinary setting (prompt
    // section 51: "secure secret management"), so it's kept out of the
    // plain-text DataStore entirely and stored via EncryptedSharedPreferences
    // (AES256-GCM, key material in the Android Keystore) instead. This is a
    // regular synchronous SharedPreferences API under the hood, so we back
    // the public apiKey Flow with a StateFlow that's seeded from the
    // encrypted store on first access and updated on every write, keeping
    // the same reactive Flow<String> contract callers already depend on.
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _apiKey by lazy { MutableStateFlow(encryptedPrefs.getString(API_KEY_PREF, "") ?: "") }
    val apiKey: Flow<String> by lazy { _apiKey.asStateFlow() }

    /**
     * One-time migration for anyone who saved an API key before this
     * encrypted store existed: it used to live in plain DataStore under the
     * same logical key. Without this, upgrading would silently "lose" a
     * key the user already entered — they'd see Settings as unconfigured
     * even though they'd set it up before. Safe to call every launch: it's
     * a no-op once the legacy key has been migrated (or never existed).
     */
    suspend fun migrateLegacyApiKeyIfNeeded() {
        if (encryptedPrefs.contains(API_KEY_PREF)) return // already on encrypted storage
        val legacyKeyPref = stringPreferencesKey("gemini_api_key")
        val legacyValue = context.dataStore.data.map { it[legacyKeyPref] }.firstOrNull()
        if (!legacyValue.isNullOrBlank()) {
            setApiKey(legacyValue)
            context.dataStore.edit { prefs -> prefs.remove(legacyKeyPref) }
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_KEY] = enabled
        }
    }

    suspend fun setApiKey(key: String) {
        encryptedPrefs.edit().putString(API_KEY_PREF, key).apply()
        _apiKey.value = key
    }
}
