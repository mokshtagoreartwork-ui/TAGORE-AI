package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.core.auth.GoogleSignInButton
import com.example.core.auth.signOutUser
import com.example.domain.model.LanguageMode
import com.example.domain.model.ResponseLength
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePersonality
import com.example.domain.model.VoiceTone
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    isAuthenticated: Boolean,
    userEmail: String?,
    onBack: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onVoiceGenderChange: (VoiceGender) -> Unit,
    onPersonalityChange: (VoicePersonality) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToneChange: (VoiceTone) -> Unit,
    onLengthChange: (ResponseLength) -> Unit,
    onLanguageChange: (LanguageMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onAnimSpeedChange: (Float) -> Unit,
    onWakeSensitivityChange: (Float) -> Unit,
    onOfflinePrivacyLockChange: (Boolean) -> Unit,
    onVoiceOutputChange: (Boolean) -> Unit,
    onAutoListenChange: (Boolean) -> Unit,
    onIdentityUpdate: (String, String) -> Unit,
    onTestVoice: () -> Unit,
    onSignedInSuccess: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var nameInput by remember(prefs.preferredName) { mutableStateOf(prefs.preferredName) }
    var honorificInput by remember(prefs.honorific) { mutableStateOf(prefs.honorific) }
    var authStatusMsg by remember { mutableStateOf<String?>(null) }

    val micGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TAGORE Settings", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Ring"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. UI Appearance (Light / Dark Mode + Ring Customization)
            item {
                SettingsSectionCard(
                    title = "Appearance & Holographic Ring",
                    icon = Icons.Default.Brightness6
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Holographic Mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (prefs.darkMode) "Pure Black & Electric Cyan" else "Clean Light Alabaster UI",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.darkMode,
                            onCheckedChange = onDarkModeChange,
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ring Brightness: ${(prefs.ringBrightness * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.ringBrightness,
                        onValueChange = onBrightnessChange,
                        valueRange = 0.25f..1.0f,
                        modifier = Modifier.testTag("ring_brightness_slider")
                    )

                    Text(
                        text = "Ring Animation Speed: ${String.format(Locale.US, "%.2fx", prefs.animationSpeed)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.animationSpeed,
                        onValueChange = onAnimSpeedChange,
                        valueRange = 0.4f..2.0f,
                        modifier = Modifier.testTag("ring_anim_speed_slider")
                    )
                }
            }

            // 2. Voice Personalities, Pitch, Speed, Tone & Accent
            item {
                SettingsSectionCard(
                    title = "Voice Personality & Indian Accent",
                    icon = Icons.Default.GraphicEq
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Output (Offline TTS)", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = prefs.voiceOutputEnabled,
                            onCheckedChange = onVoiceOutputChange,
                            modifier = Modifier.testTag("voice_output_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-On Mic on Launch", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Mic turns ON automatically when TAGORE opens",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.autoListenOnLaunch,
                            onCheckedChange = onAutoListenChange,
                            modifier = Modifier.testTag("auto_listen_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Voice Gender (Male / Female)", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        VoiceGender.entries.forEach { gender ->
                            FilterChip(
                                selected = prefs.voiceGender == gender,
                                onClick = { onVoiceGenderChange(gender) },
                                label = { Text(gender.label) },
                                modifier = Modifier.testTag("voice_gender_${gender.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Voice Personality", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoicePersonality.entries.forEach { personality ->
                            val selected = prefs.voicePersonality == personality
                            Card(
                                onClick = { onPersonalityChange(personality) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_personality_${personality.name.lowercase()}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = personality.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = personality.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Voice Pitch: ${String.format(Locale.US, "%.2f", prefs.voicePitch)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.voicePitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.5f..1.6f,
                        modifier = Modifier.testTag("voice_pitch_slider")
                    )

                    Text(
                        text = "Response Speed: ${String.format(Locale.US, "%.2fx", prefs.voiceSpeed)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.voiceSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.6f..1.75f,
                        modifier = Modifier.testTag("voice_speed_slider")
                    )

                    Text(
                        text = "Wake-Word Sensitivity ('TAGORE'): ${(prefs.wakeWordSensitivity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = prefs.wakeWordSensitivity,
                        onValueChange = onWakeSensitivityChange,
                        valueRange = 0.2f..1.0f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Conversational Tone", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        VoiceTone.entries.forEach { tone ->
                            FilterChip(
                                selected = prefs.voiceTone == tone,
                                onClick = { onToneChange(tone) },
                                label = { Text(tone.label) }
                            )
                        }
                    }

                    Text("Language & Accent", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        LanguageMode.entries.forEach { lang ->
                            FilterChip(
                                selected = prefs.languageMode == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang.label) }
                            )
                        }
                    }

                    Text("Response Length", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        ResponseLength.entries.forEach { len ->
                            FilterChip(
                                selected = prefs.responseLength == len,
                                onClick = { onLengthChange(len) },
                                label = { Text(len.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onTestVoice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_voice_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Indian Voice Calibration")
                    }
                }
            }

            // 3. Identity & Respectful Addressing
            item {
                SettingsSectionCard(
                    title = "Personal Identity",
                    icon = Icons.Default.Person
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Preferred Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = honorificInput,
                        onValueChange = { honorificInput = it },
                        label = { Text("Honorific (e.g. Sir, Boss, Ma'am)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onIdentityUpdate(nameInput, honorificInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Identity")
                    }
                }
            }

            // 4. Privacy, Offline Lock, Permissions & Cloud Sync Account
            item {
                SettingsSectionCard(
                    title = "Privacy, Offline Lock & Ecosystem Sync",
                    icon = Icons.Default.Security
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Privacy Lock", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Force 100% local processing & zero cloud calls",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.offlinePrivacyLock,
                            onCheckedChange = onOfflinePrivacyLockChange,
                            modifier = Modifier.testTag("offline_privacy_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Permission Manager:\n• Microphone: ${if (micGranted) "Granted" else "On-Demand"}\n• Location (SOS): ${if (locationGranted) "Granted" else "On-Demand"}\n• Photos: Zero-Permission Photo Picker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    if (isAuthenticated) {
                        Text(
                            text = "Connected Account: ${userEmail ?: "Google Authenticated"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                signOutUser(credentialManager, scope) {
                                    onSignedOut()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out & Switch to Local Mode")
                        }
                    } else {
                        Text(
                            text = "Currently running in Local Offline Mode. Sign in with Google to enable cross-device Firestore sync & Gemini Cloud AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GoogleSignInButton(
                            onAuthSuccess = {
                                authStatusMsg = null
                                onSignedInSuccess()
                            },
                            onAuthError = { authStatusMsg = it },
                            label = "Connect Google Ecosystem Sync"
                        )
                        if (authStatusMsg != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = authStatusMsg!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TAGORE • Creator: Mr. Moksh Tagore • AES-256-GCM Local Vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
