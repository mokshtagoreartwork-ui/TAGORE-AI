package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val preferredName: String = "Moksh",
    val honorific: String = "Sir",
    val voicePersonality: String = "JARVIS",
    val voicePitch: Double = 0.88,
    val voiceSpeed: Double = 1.0,
    val voiceTone: String = "RESPECTFUL",
    val responseLength: String = "CONCISE",
    val languageMode: String = "HINGLISH",
    val darkMode: Boolean = true,
    val ringBrightness: Double = 0.95,
    val animationSpeed: Double = 1.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MemoryDoc(
    @DocumentId val id: String = "",
    val category: String = "General",
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProductivityItemDoc(
    @DocumentId val id: String = "",
    val kind: String = "NOTE",
    val title: String = "",
    val details: String = "",
    val completed: Boolean = false,
    val dueAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SmartDeviceDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val room: String = "Living Room",
    val category: String = "LIGHT",
    val isOn: Boolean = false,
    val level: Long = 75L,
    val temperature: Long = 24L,
    val isLocked: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EcosystemDeviceDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val formFactor: String = "PHONE",
    val status: String = "ONLINE",
    val batteryPercent: Long = 90L,
    val lastCommand: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
