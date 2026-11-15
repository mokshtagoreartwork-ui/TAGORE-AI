package com.example.domain.model

enum class RingState(
    val displayTitle: String,
    val statusPhrase: String
) {
    IDLE("IDLE", "Silent • Always Ready"),
    WAKE("WAKE", "Yes Sir."),
    LISTENING("LISTENING", "Listening..."),
    THINKING("THINKING", "Thinking..."),
    SEARCHING("SEARCHING", "Searching..."),
    WORKING("WORKING", "Working on task..."),
    WAITING("WAITING", "Waiting for command"),
    SPEAKING("SPEAKING", "Speaking..."),
    SUCCESS("SUCCESS", "Done."),
    ERROR("ERROR", "Sir, ye kaam complete nahi ho paya."),
    CAMERA_VISION("VISION", "Analyzing visual input..."),
    GESTURE_MODE("GESTURE", "Gesture Mode Active"),
    SLEEP("SLEEP", "Low Power Standby"),
    WAKE_UP("WAKE UP", "Aapke liye hamesha, Sir."),
    EMERGENCY("EMERGENCY", "Emergency Mode Active")
}

enum class VoiceGender(val label: String) {
    MALE("Male Voice"),
    FEMALE("Female Voice")
}

enum class VoicePersonality(
    val label: String,
    val subtitle: String,
    val defaultPitch: Float,
    val defaultSpeed: Float,
    val preferMale: Boolean
) {
    JARVIS(
        label = "Jarvis Futuristic (Male)",
        subtitle = "Composed, crisp futuristic assistant (Male)",
        defaultPitch = 0.85f,
        defaultSpeed = 1.0f,
        preferMale = true
    ),
    BIGG_BOSS(
        label = "Bigg Boss Command (Male)",
        subtitle = "Deep authoritative announcement voice (Male)",
        defaultPitch = 0.72f,
        defaultSpeed = 0.90f,
        preferMale = true
    ),
    MALE_INDIAN(
        label = "Natural Male (Indian)",
        subtitle = "Warm natural Indian English/Hindi voice (Male)",
        defaultPitch = 0.88f,
        defaultSpeed = 1.02f,
        preferMale = true
    ),
    FEMALE_INDIAN(
        label = "Natural Female (Indian)",
        subtitle = "Warm expressive Indian English/Hindi (Female)",
        defaultPitch = 1.18f,
        defaultSpeed = 1.02f,
        preferMale = false
    ),
    STANDARD_ASSISTANT(
        label = "Standard Assistant",
        subtitle = "Balanced natural assistant voice",
        defaultPitch = 1.0f,
        defaultSpeed = 1.04f,
        preferMale = true
    )
}

enum class VoiceTone(val label: String, val stylePrompt: String) {
    RESPECTFUL("Respectful & Formal", "Use respectful 'Aap' and 'Sir', calm precision."),
    FRIENDLY("Warm & Friendly", "Warm conversational companion while keeping 'Aap' respect."),
    EXECUTIVE("Executive Brief", "Crisp business productivity tone with bullet clarity."),
    TECH_WITTY("Jarvis Tech Wit", "Intelligent, subtly witty futuristic engineering tone.")
}

enum class ResponseLength(val label: String) {
    CONCISE("Concise"),
    BALANCED("Balanced"),
    DETAILED("Deep Detailed")
}

enum class LanguageMode(val label: String, val localeTag: String) {
    HINGLISH("Hinglish (Auto)", "en-IN"),
    ENGLISH_IN("English (India)", "en-IN"),
    HINDI("Hindi (हिन्दी)", "hi-IN")
}

data class UserPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val preferredName: String = "Moksh",
    val honorific: String = "Sir",
    val voiceGender: VoiceGender = VoiceGender.MALE,
    val voicePersonality: VoicePersonality = VoicePersonality.JARVIS,
    val voicePitch: Float = 0.85f,
    val voiceSpeed: Float = 1.0f,
    val voiceTone: VoiceTone = VoiceTone.RESPECTFUL,
    val responseLength: ResponseLength = ResponseLength.CONCISE,
    val languageMode: LanguageMode = LanguageMode.HINGLISH,
    val darkMode: Boolean = true,
    val ringBrightness: Float = 0.95f,
    val animationSpeed: Float = 1.0f,
    val wakeWordSensitivity: Float = 0.75f,
    val offlinePrivacyLock: Boolean = false,
    val voiceOutputEnabled: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val autoListenOnLaunch: Boolean = true
)

enum class ChatAuthor { USER, TAGORE }

data class ChatTurn(
    val id: String,
    val author: ChatAuthor,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isOfflineResponse: Boolean = false,
    val deepKnowledgeTopic: String? = null
)

data class PendingConfirmation(
    val id: String,
    val title: String,
    val promptSpeech: String,
    val actionType: ConfirmationActionType,
    val targetId: String = "",
    val payload: String = ""
)

enum class ConfirmationActionType {
    UNLOCK_DOOR,
    POST_SOCIAL,
    EMERGENCY_CALL,
    CLEAR_LOGS
}
