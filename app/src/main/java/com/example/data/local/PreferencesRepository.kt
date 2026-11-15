package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.LanguageMode
import com.example.domain.model.ResponseLength
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePersonality
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tagorePrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tagore_user_preferences"
)

/**
 * Dual-backed fast preferences repository:
 * Primary structured persistence in Room (`UserPreferencesDao`) with DataStore warm mirror
 * for instant UI startup and sub-millisecond reads.
 */
class PreferencesRepository(
    private val context: Context,
    private val prefsDao: UserPreferencesDao = TagoreDatabase.getInstance(context).userPreferencesDao()
) {

    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val PREFERRED_NAME = stringPreferencesKey("preferred_name")
        val HONORIFIC = stringPreferencesKey("honorific")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val VOICE_PERSONALITY = stringPreferencesKey("voice_personality")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_TONE = stringPreferencesKey("voice_tone")
        val RESPONSE_LENGTH = stringPreferencesKey("response_length")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val RING_BRIGHTNESS = floatPreferencesKey("ring_brightness")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        val OFFLINE_PRIVACY_LOCK = booleanPreferencesKey("offline_privacy_lock")
        val VOICE_OUTPUT_ENABLED = booleanPreferencesKey("voice_output_enabled")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val AUTO_LISTEN_ON_LAUNCH = booleanPreferencesKey("auto_listen_on_launch")
    }

    private val dataStoreFlow: Flow<UserPreferences> = context.tagorePrefsDataStore.data.map { prefs ->
        decodeDataStore(prefs)
    }

    val preferencesFlow: Flow<UserPreferences> = combine(
        prefsDao.observePreferences(),
        dataStoreFlow
    ) { roomEntity, dsPrefs ->
        roomEntity?.toDomain() ?: dsPrefs
    }

    suspend fun getCurrentPreferences(): UserPreferences {
        return prefsDao.getPreferences()?.toDomain() ?: dataStoreFlow.first()
    }

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        val current = getCurrentPreferences()
        val updated = transform(current)
        val now = System.currentTimeMillis()
        prefsDao.upsertPreferences(UserPreferencesEntity.fromDomain(updated, now))
        context.tagorePrefsDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = updated.hasCompletedOnboarding
            prefs[Keys.PREFERRED_NAME] = updated.preferredName
            prefs[Keys.HONORIFIC] = updated.honorific
            prefs[Keys.VOICE_GENDER] = updated.voiceGender.name
            prefs[Keys.VOICE_PERSONALITY] = updated.voicePersonality.name
            prefs[Keys.VOICE_PITCH] = updated.voicePitch
            prefs[Keys.VOICE_SPEED] = updated.voiceSpeed
            prefs[Keys.VOICE_TONE] = updated.voiceTone.name
            prefs[Keys.RESPONSE_LENGTH] = updated.responseLength.name
            prefs[Keys.LANGUAGE_MODE] = updated.languageMode.name
            prefs[Keys.DARK_MODE] = updated.darkMode
            prefs[Keys.RING_BRIGHTNESS] = updated.ringBrightness
            prefs[Keys.ANIMATION_SPEED] = updated.animationSpeed
            prefs[Keys.WAKE_SENSITIVITY] = updated.wakeWordSensitivity
            prefs[Keys.OFFLINE_PRIVACY_LOCK] = updated.offlinePrivacyLock
            prefs[Keys.VOICE_OUTPUT_ENABLED] = updated.voiceOutputEnabled
            prefs[Keys.BIOMETRIC_LOCK] = updated.biometricLockEnabled
            prefs[Keys.AUTO_LISTEN_ON_LAUNCH] = updated.autoListenOnLaunch
        }
    }

    private fun decodeDataStore(prefs: Preferences): UserPreferences {
        return UserPreferences(
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            preferredName = prefs[Keys.PREFERRED_NAME] ?: "Moksh",
            honorific = prefs[Keys.HONORIFIC] ?: "Sir",
            voiceGender = prefs[Keys.VOICE_GENDER]?.let {
                runCatching { VoiceGender.valueOf(it) }.getOrNull()
            } ?: VoiceGender.MALE,
            voicePersonality = prefs[Keys.VOICE_PERSONALITY]?.let {
                runCatching { VoicePersonality.valueOf(it) }.getOrNull()
            } ?: VoicePersonality.JARVIS,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 0.85f,
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            voiceTone = prefs[Keys.VOICE_TONE]?.let {
                runCatching { VoiceTone.valueOf(it) }.getOrNull()
            } ?: VoiceTone.RESPECTFUL,
            responseLength = prefs[Keys.RESPONSE_LENGTH]?.let {
                runCatching { ResponseLength.valueOf(it) }.getOrNull()
            } ?: ResponseLength.CONCISE,
            languageMode = prefs[Keys.LANGUAGE_MODE]?.let {
                runCatching { LanguageMode.valueOf(it) }.getOrNull()
            } ?: LanguageMode.HINGLISH,
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            ringBrightness = prefs[Keys.RING_BRIGHTNESS] ?: 0.95f,
            animationSpeed = prefs[Keys.ANIMATION_SPEED] ?: 1.0f,
            wakeWordSensitivity = prefs[Keys.WAKE_SENSITIVITY] ?: 0.75f,
            offlinePrivacyLock = prefs[Keys.OFFLINE_PRIVACY_LOCK] ?: false,
            voiceOutputEnabled = prefs[Keys.VOICE_OUTPUT_ENABLED] ?: true,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            autoListenOnLaunch = prefs[Keys.AUTO_LISTEN_ON_LAUNCH] ?: true
        )
    }
}
