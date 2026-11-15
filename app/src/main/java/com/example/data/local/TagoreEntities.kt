package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ChatAuthor
import com.example.domain.model.ChatTurn
import com.example.domain.model.LanguageMode
import com.example.domain.model.ResponseLength
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePersonality
import com.example.domain.model.VoiceTone

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["pinned", "updatedAtMillis"]),
        Index(value = ["title"])
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = "",
    val category: String,
    val title: String,
    val content: String,
    val tags: String = "",
    val pinned: Boolean = false,
    val accessCount: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "conversation_history",
    indices = [
        Index(value = ["sessionId", "timestampMillis"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["author"]),
        Index(value = ["isOfflineResponse"])
    ]
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val messageUid: String,
    val sessionId: String = "default",
    val author: String, // USER, TAGORE
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isOfflineResponse: Boolean = true,
    val ringState: String = "IDLE",
    val deepKnowledgeTopic: String? = null,
    val latencyMs: Long = 0L
) {
    fun toChatTurn(): ChatTurn = ChatTurn(
        id = messageUid,
        author = runCatching { ChatAuthor.valueOf(author) }.getOrDefault(ChatAuthor.TAGORE),
        text = content,
        timestampMillis = timestampMillis,
        isOfflineResponse = isOfflineResponse,
        deepKnowledgeTopic = deepKnowledgeTopic
    )
}

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val hasCompletedOnboarding: Boolean = false,
    val preferredName: String = "Moksh",
    val honorific: String = "Sir",
    val voiceGender: String = VoiceGender.MALE.name,
    val voicePersonality: String = VoicePersonality.JARVIS.name,
    val voicePitch: Float = 0.85f,
    val voiceSpeed: Float = 1.0f,
    val voiceTone: String = VoiceTone.RESPECTFUL.name,
    val responseLength: String = ResponseLength.CONCISE.name,
    val languageMode: String = LanguageMode.HINGLISH.name,
    val darkMode: Boolean = true,
    val ringBrightness: Float = 0.95f,
    val animationSpeed: Float = 1.0f,
    val wakeWordSensitivity: Float = 0.75f,
    val offlinePrivacyLock: Boolean = false,
    val voiceOutputEnabled: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val autoListenOnLaunch: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): UserPreferences = UserPreferences(
        hasCompletedOnboarding = hasCompletedOnboarding,
        preferredName = preferredName,
        honorific = honorific,
        voiceGender = runCatching { VoiceGender.valueOf(voiceGender) }
            .getOrDefault(VoiceGender.MALE),
        voicePersonality = runCatching { VoicePersonality.valueOf(voicePersonality) }
            .getOrDefault(VoicePersonality.JARVIS),
        voicePitch = voicePitch,
        voiceSpeed = voiceSpeed,
        voiceTone = runCatching { VoiceTone.valueOf(voiceTone) }
            .getOrDefault(VoiceTone.RESPECTFUL),
        responseLength = runCatching { ResponseLength.valueOf(responseLength) }
            .getOrDefault(ResponseLength.CONCISE),
        languageMode = runCatching { LanguageMode.valueOf(languageMode) }
            .getOrDefault(LanguageMode.HINGLISH),
        darkMode = darkMode,
        ringBrightness = ringBrightness,
        animationSpeed = animationSpeed,
        wakeWordSensitivity = wakeWordSensitivity,
        offlinePrivacyLock = offlinePrivacyLock,
        voiceOutputEnabled = voiceOutputEnabled,
        biometricLockEnabled = biometricLockEnabled,
        autoListenOnLaunch = autoListenOnLaunch
    )

    companion object {
        const val SINGLETON_ID = 1

        fun fromDomain(prefs: UserPreferences, updatedAtMillis: Long = System.currentTimeMillis()): UserPreferencesEntity =
            UserPreferencesEntity(
                id = SINGLETON_ID,
                hasCompletedOnboarding = prefs.hasCompletedOnboarding,
                preferredName = prefs.preferredName,
                honorific = prefs.honorific,
                voiceGender = prefs.voiceGender.name,
                voicePersonality = prefs.voicePersonality.name,
                voicePitch = prefs.voicePitch,
                voiceSpeed = prefs.voiceSpeed,
                voiceTone = prefs.voiceTone.name,
                responseLength = prefs.responseLength.name,
                languageMode = prefs.languageMode.name,
                darkMode = prefs.darkMode,
                ringBrightness = prefs.ringBrightness,
                animationSpeed = prefs.animationSpeed,
                wakeWordSensitivity = prefs.wakeWordSensitivity,
                offlinePrivacyLock = prefs.offlinePrivacyLock,
                voiceOutputEnabled = prefs.voiceOutputEnabled,
                biometricLockEnabled = prefs.biometricLockEnabled,
                autoListenOnLaunch = prefs.autoListenOnLaunch,
                updatedAtMillis = updatedAtMillis
            )
    }
}

@Entity(
    tableName = "productivity_items",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["completed", "updatedAtMillis"])
    ]
)
data class ProductivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = "",
    val kind: String, // NOTE, TODO, REMINDER, ALARM
    val title: String,
    val details: String = "",
    val completed: Boolean = false,
    val dueAtMillis: Long = 0L,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "smart_devices",
    indices = [
        Index(value = ["room", "name"]),
        Index(value = ["category"])
    ]
)
data class SmartDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = "",
    val name: String,
    val room: String,
    val category: String, // LIGHT, AC, FAN, LOCK, TV, SCENE
    val isOn: Boolean = false,
    val level: Int = 75,
    val temperature: Int = 24,
    val isLocked: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)

@Entity(tableName = "ecosystem_devices")
data class EcosystemDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = "",
    val name: String,
    val formFactor: String, // PHONE, LAPTOP, WATCH, TABLET
    val status: String, // ONLINE, STANDBY, OFFLINE
    val batteryPercent: Int = 90,
    val lastCommand: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "encrypted_logs",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["category"])
    ]
)
data class EncryptedLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val category: String,
    val actionSummary: String,
    val encryptedPayloadBase64: String,
    val ivBase64: String,
    val isOfflineEvent: Boolean = true
)

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["createdAtMillis"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val collectionName: String,
    val docId: String,
    val operationType: String, // UPSERT, DELETE
    val payloadJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
