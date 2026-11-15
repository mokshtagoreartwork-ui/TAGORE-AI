package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.RingState
import com.example.domain.model.UserPreferences
import com.example.ui.components.HolographicRing
import com.example.ui.components.VoiceWaveform
import com.example.ui.theme.TagoreErrorRed
import com.example.ui.theme.TagoreOfflineAmber
import com.example.ui.viewmodel.TagoreMainUiState
import kotlinx.coroutines.delay

/**
 * Pure minimal Holographic Ring Screen:
 * - Pure black (or clean light) background
 * - TAGORE in bold Orbitron above the ring
 * - Animated 15-state Holographic Ring in the center
 * - Voice Waveform directly below the ring
 * - Default Auto-On Mic on Launch: single click on Mic toggles it OFF/ON
 */
@Composable
fun RingHomeScreen(
    uiState: TagoreMainUiState,
    prefs: UserPreferences,
    pendingSyncCount: Int,
    onRingTap: () -> Unit,
    onRingLongPress: () -> Unit,
    onSetMicEnabled: (Boolean) -> Unit,
    onListeningStateChange: (Boolean, Float) -> Unit,
    onSubmitCommand: (String) -> Unit,
    onConfirmPendingAction: (Boolean) -> Unit,
    onCompleteOnboarding: (String, String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerEmergency: () -> Unit
) {
    val context = LocalContext.current
    var showCommandInput by remember { mutableStateOf(false) }
    var commandDraft by remember { mutableStateOf("") }
    var isVoiceCapturing by remember { mutableStateOf(false) }
    var hasRequestedInitialMicPerm by remember { mutableStateOf(false) }
    var rearmNonce by remember { mutableIntStateOf(0) }

    val latestMicEnabled by rememberUpdatedState(uiState.isMicEnabled)
    val latestOffline by rememberUpdatedState(uiState.isOfflineEffective)

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val stopVoiceCapture = remember(speechRecognizer) {
        {
            runCatching { speechRecognizer?.cancel() }
            isVoiceCapturing = false
            onListeningStateChange(false, 0f)
        }
    }

    val startVoiceCapture = remember(speechRecognizer, prefs.languageMode) {
        {
            if (speechRecognizer != null) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.languageMode.localeTag)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, latestOffline)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                }
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isVoiceCapturing = true
                        onListeningStateChange(true, 2f)
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        if (latestMicEnabled) {
                            onListeningStateChange(true, rmsdB)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isVoiceCapturing = false
                    }

                    override fun onError(error: Int) {
                        isVoiceCapturing = false
                        onListeningStateChange(false, 0f)
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                if (latestMicEnabled) {
                                    rearmNonce++
                                }
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                            SpeechRecognizer.ERROR_AUDIO -> {
                                onSetMicEnabled(false)
                            }
                            else -> {
                                if (latestMicEnabled) {
                                    rearmNonce++
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isVoiceCapturing = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull().orEmpty()
                        if (spoken.isNotBlank()) {
                            onSubmitCommand(spoken)
                        } else {
                            onListeningStateChange(false, 0f)
                            if (latestMicEnabled) rearmNonce++
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                isVoiceCapturing = true
                onListeningStateChange(true, 1f)
                runCatching { speechRecognizer.startListening(intent) }
                    .onFailure {
                        isVoiceCapturing = false
                        onSetMicEnabled(false)
                        showCommandInput = true
                    }
            } else {
                // Device/emulator without SpeechRecognizer service falls back cleanly
                isVoiceCapturing = false
                onSetMicEnabled(false)
                showCommandInput = true
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetMicEnabled(true)
            startVoiceCapture()
        } else {
            onSetMicEnabled(false)
            showCommandInput = true
        }
    }

    // Default Auto-Mic controller:
    // - Starts mic immediately on app launch when enabled
    // - Pauses while TAGORE speaks or processes, then resumes automatically
    // - Stops immediately when user clicks Mic button to turn it OFF
    LaunchedEffect(
        uiState.isMicEnabled,
        uiState.ringState,
        uiState.showOnboardingDialog,
        uiState.pendingConfirmation,
        rearmNonce
    ) {
        if (!uiState.isMicEnabled) {
            if (isVoiceCapturing) {
                stopVoiceCapture()
            }
            return@LaunchedEffect
        }

        if (uiState.showOnboardingDialog || uiState.pendingConfirmation != null) {
            if (isVoiceCapturing) stopVoiceCapture()
            return@LaunchedEffect
        }

        val isBusyWithReply = uiState.ringState == RingState.SPEAKING ||
            uiState.ringState == RingState.THINKING ||
            uiState.ringState == RingState.SEARCHING ||
            uiState.ringState == RingState.WORKING

        if (isBusyWithReply) {
            if (isVoiceCapturing) {
                runCatching { speechRecognizer?.cancel() }
                isVoiceCapturing = false
            }
            return@LaunchedEffect
        }

        if (!isVoiceCapturing) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                delay(240L)
                if (latestMicEnabled && !isVoiceCapturing) {
                    startVoiceCapture()
                }
            } else if (!hasRequestedInitialMicPerm) {
                hasRequestedInitialMicPerm = true
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val onMicButtonClicked = {
        if (uiState.isMicEnabled) {
            // Single click turns Mic OFF cleanly
            stopVoiceCapture()
            onSetMicEnabled(false)
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onSetMicEnabled(true)
                startVoiceCapture()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        val ringDimension = (minOf(maxWidth, maxHeight) * 0.64f).coerceIn(200.dp, 340.dp)

        // Minimal corner controls so the main screen remains pure Ring-focused
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subtle Offline / Online status indicator
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = if (uiState.isOfflineEffective) TagoreOfflineAmber.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("connectivity_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (uiState.isOfflineEffective) Icons.Default.CloudOff else Icons.Default.Wifi,
                    contentDescription = if (uiState.isOfflineEffective) "Offline Local AI" else "Online Ecosystem",
                    tint = if (uiState.isOfflineEffective) TagoreOfflineAmber else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buildString {
                        append(if (uiState.isOfflineEffective) "OFFLINE AI" else "ECOSYSTEM")
                        if (pendingSyncCount > 0) append(" • $pendingSyncCount")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.isOfflineEffective) TagoreOfflineAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTriggerEmergency,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("emergency_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Emergency SOS",
                        tint = TagoreErrorRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onOpenWorkspace,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("workspace_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = "Open Ecosystem & Smart Home Hub",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open TAGORE Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Pure Centerpiece Column: TAGORE + Holographic Ring + Voice Waveform
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TAGORE",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("tagore_brand_title")
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = uiState.ringState.displayTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(18.dp))

            HolographicRing(
                ringState = uiState.ringState,
                isOfflineMode = uiState.isOfflineEffective,
                audioLevel = uiState.audioLevel,
                brightness = prefs.ringBrightness,
                animationSpeed = prefs.animationSpeed,
                isDarkMode = prefs.darkMode,
                onTap = {
                    if (uiState.ringState == RingState.SLEEP || uiState.ringState == RingState.SPEAKING) {
                        onRingTap()
                    } else {
                        onMicButtonClicked()
                    }
                },
                onLongPress = onRingLongPress,
                modifier = Modifier.size(ringDimension)
            )

            Spacer(modifier = Modifier.height(18.dp))

            VoiceWaveform(
                ringState = uiState.ringState,
                audioLevel = uiState.audioLevel,
                isDarkMode = prefs.darkMode,
                modifier = Modifier.widthIn(max = 360.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Single clean caption line showing current TAGORE speech/reply
            Text(
                text = uiState.streamingReply.ifBlank { uiState.statusCaption },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .clickable { showCommandInput = !showCommandInput }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("ring_status_caption")
            )

            // Quick Deep Knowledge acceptance chip when offered
            if (uiState.pendingDeepTopic != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSubmitCommand("Yes, deep knowledge chahiye") },
                    modifier = Modifier.testTag("deep_knowledge_button")
                ) {
                    Text("Yes Sir, Deep Knowledge")
                }
            }
        }

        // Minimal Bottom Controls (Default-On Voice Mic Toggle + Optional Expandable Command Input)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = showCommandInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandDraft,
                        onValueChange = { commandDraft = it },
                        placeholder = {
                            Text(
                                text = "Ask TAGORE (Hindi / English / Hinglish)...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commandDraft.isNotBlank()) {
                                    onSubmitCommand(commandDraft)
                                    commandDraft = ""
                                }
                            }
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_text_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commandDraft.isNotBlank()) {
                                onSubmitCommand(commandDraft)
                                commandDraft = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_keyboard_button")
                ) {
                    IconButton(onClick = { showCommandInput = !showCommandInput }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Type command",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Default ON Mic: Single click toggles Mic OFF / ON cleanly
                Surface(
                    shape = CircleShape,
                    color = if (uiState.isMicEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (uiState.isMicEnabled) null
                    else BorderStroke(1.dp, TagoreErrorRed.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("mic_button")
                ) {
                    IconButton(onClick = { onMicButtonClicked() }) {
                        Icon(
                            imageVector = if (uiState.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (uiState.isMicEnabled) "Mic On - Tap to turn off" else "Mic Off - Tap to turn on",
                            tint = if (uiState.isMicEnabled) MaterialTheme.colorScheme.onPrimary else TagoreErrorRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (uiState.isMicEnabled) "MIC ON • Tap mic to turn off" else "MIC OFF • Tap mic to turn on",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.isMicEnabled) MaterialTheme.colorScheme.primary else TagoreErrorRed.copy(alpha = 0.85f),
                modifier = Modifier.testTag("mic_status_label")
            )
        }
    }

    // First-time Onboarding Dialog: "Main tumhe kis naam se bulaun?"
    if (uiState.showOnboardingDialog) {
        OnboardingNameDialog(
            initialName = prefs.preferredName,
            initialHonorific = prefs.honorific,
            onSave = onCompleteOnboarding
        )
    }

    // Explicit Confirmation Dialog for Sensitive / External / Emergency Actions
    uiState.pendingConfirmation?.let { confirmation ->
        ConfirmationActionDialog(
            confirmation = confirmation,
            onApprove = { onConfirmPendingAction(true) },
            onDecline = { onConfirmPendingAction(false) }
        )
    }
}

@Composable
private fun OnboardingNameDialog(
    initialName: String,
    initialHonorific: String,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var honorific by remember { mutableStateOf(initialHonorific) }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "TAGORE • First Launch",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Main tumhe kis naam se bulaun?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Aapka Naam") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input")
                )
                OutlinedTextField(
                    value = honorific,
                    onValueChange = { honorific = it },
                    label = { Text("Form of Address (e.g. Sir, Ma'am, Boss)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_honorific_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, honorific) },
                modifier = Modifier.testTag("onboarding_confirm_button")
            ) {
                Text("Confirm")
            }
        }
    )
}

@Composable
private fun ConfirmationActionDialog(
    confirmation: PendingConfirmation,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(confirmation.title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(confirmation.promptSpeech, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_action_yes")
            ) {
                Text("Yes, Proceed")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("confirm_action_no")
            ) {
                Text("Cancel")
            }
        }
    )
}
