package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.CloudGeminiEngine
import com.example.core.ai.CloudStreamEvent
import com.example.core.ai.LocalDeviceAction
import com.example.core.ai.OfflineAssistantEngine
import com.example.core.ai.SearchTarget
import com.example.core.network.NetworkMonitor
import com.example.core.voice.TagoreVoiceEngine
import java.net.URLEncoder
import com.example.data.local.EcosystemDeviceEntity
import com.example.data.local.EncryptedLogEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.ProductivityEntity
import com.example.data.local.SmartDeviceEntity
import com.example.data.remote.TagoreCloudSyncRepository
import com.example.data.repository.TagoreVaultRepository
import com.example.domain.model.ChatAuthor
import com.example.domain.model.ChatTurn
import com.example.domain.model.ConfirmationActionType
import com.example.domain.model.LanguageMode
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.ResponseLength
import com.example.domain.model.RingState
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePersonality
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TagoreMainUiState(
    val ringState: RingState = RingState.IDLE,
    val isOfflineEffective: Boolean = true,
    val hasInternet: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isMicEnabled: Boolean = true,
    val audioLevel: Float = 0.08f,
    val statusCaption: String = "Mic On • Speak naturally to TAGORE",
    val streamingReply: String = "",
    val chatHistory: List<ChatTurn> = emptyList(),
    val pendingConfirmation: PendingConfirmation? = null,
    val pendingDeepTopic: String? = null,
    val showOnboardingDialog: Boolean = false,
    val decryptedLogPreviews: Map<Long, String> = emptyMap(),
    val lastSyncMessage: String = "",
    val selectedVisionImageUri: Uri? = null,
    val visionAnalysisText: String = "",
    val isVisionAnalyzing: Boolean = false
)

class TagoreViewModel(
    private val appContext: Context,
    private val vault: TagoreVaultRepository,
    private val prefsRepo: PreferencesRepository,
    private val networkMonitor: NetworkMonitor,
    private val voiceEngine: TagoreVoiceEngine,
    private val cloudSyncRepo: TagoreCloudSyncRepository?,
    private val isAuthenticated: Boolean
) : ViewModel() {

    private val offlineEngine = OfflineAssistantEngine(vault)
    private val cloudGeminiEngine = CloudGeminiEngine()

    private val _uiState = MutableStateFlow(
        TagoreMainUiState(isAuthenticated = isAuthenticated)
    )
    val uiState: StateFlow<TagoreMainUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UserPreferences()
    )

    val memories: StateFlow<List<MemoryEntity>> = vault.memories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val productivityItems: StateFlow<List<ProductivityEntity>> = vault.productivityItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val smartDevices: StateFlow<List<SmartDeviceEntity>> = vault.smartDevices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val ecosystemDevices: StateFlow<List<EcosystemDeviceEntity>> = vault.ecosystemDevices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val encryptedLogs: StateFlow<List<EncryptedLogEntity>> = vault.encryptedLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val pendingSyncCount: StateFlow<Int> = vault.pendingSyncCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0
    )

    private var torchEnabled = false
    private var micPrefSynced = false

    init {
        viewModelScope.launch {
            vault.ensureInitialized()
            vault.logSecureEvent(
                category = "SYSTEM",
                actionSummary = "TAGORE Core Booted",
                detailedPayload = "Offline-First Engine, Room WAL Vault & AES-256-GCM Initialized. Auth=$isAuthenticated",
                isOffline = !networkMonitor.isOnline.value
            )
        }

        // Restore and stream persisted offline conversation history from Room
        viewModelScope.launch {
            vault.conversationHistory.collectLatest { entities ->
                _uiState.value = _uiState.value.copy(
                    chatHistory = entities.map { it.toChatTurn() }.takeLast(40)
                )
            }
        }

        // Observe connectivity + privacy lock to compute effective offline status and auto-flush queue
        viewModelScope.launch {
            combine(networkMonitor.isOnline, prefsRepo.preferencesFlow) { online, prefs ->
                online to prefs
            }.collectLatest { (online, prefs) ->
                val effectiveOffline = !online || prefs.offlinePrivacyLock
                val initialMicState = if (!micPrefSynced) {
                    micPrefSynced = true
                    prefs.autoListenOnLaunch
                } else {
                    _uiState.value.isMicEnabled
                }
                _uiState.value = _uiState.value.copy(
                    hasInternet = online,
                    isOfflineEffective = effectiveOffline,
                    isMicEnabled = initialMicState,
                    showOnboardingDialog = !prefs.hasCompletedOnboarding
                )
                if (!effectiveOffline && cloudSyncRepo != null) {
                    val flushed = vault.flushPendingSyncQueue(cloudSyncRepo, prefs)
                    if (flushed > 0) {
                        _uiState.value = _uiState.value.copy(
                            lastSyncMessage = "Synced $flushed queued offline changes to cloud."
                        )
                        vault.logSecureEvent(
                            category = "SYNC",
                            actionSummary = "Cloud Recovery Sync",
                            detailedPayload = "Flushed $flushed offline operations to Firestore.",
                            isOffline = false
                        )
                    }
                }
            }
        }

        // Mirror TTS speaking status onto RingState & Waveform
        viewModelScope.launch {
            voiceEngine.isSpeaking.collectLatest { speaking ->
                if (speaking) {
                    _uiState.value = _uiState.value.copy(
                        ringState = RingState.SPEAKING,
                        audioLevel = 0.72f
                    )
                } else if (_uiState.value.ringState == RingState.SPEAKING) {
                    _uiState.value = _uiState.value.copy(
                        ringState = RingState.IDLE,
                        audioLevel = 0.1f
                    )
                }
            }
        }
    }

    fun completeOnboarding(name: String, honorific: String) {
        val cleanName = name.trim().ifBlank { "Moksh" }
        val cleanHonorific = honorific.trim().ifBlank { "Sir" }
        viewModelScope.launch {
            prefsRepo.updatePreferences {
                it.copy(
                    hasCompletedOnboarding = true,
                    preferredName = cleanName,
                    honorific = cleanHonorific
                )
            }
            vault.saveMemory(
                category = "Identity",
                title = "Preferred Address",
                content = "User prefers to be called $cleanName ($cleanHonorific).",
                pinned = true,
                cloudRepo = cloudSyncRepo,
                isOnline = !_uiState.value.isOfflineEffective
            )
            val greeting = "Aapke liye hamesha, $cleanHonorific. Namaste $cleanName, main TAGORE hoon. Aap mujhse offline aur online dono modes mein baat kar sakte hain."
            _uiState.value = _uiState.value.copy(
                showOnboardingDialog = false,
                ringState = RingState.WAKE_UP,
                statusCaption = greeting
            )
            voiceEngine.playConfirmationTone()
            voiceEngine.speak(greeting, preferences.value.copy(preferredName = cleanName, honorific = cleanHonorific))
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        if (!enabled) {
            _uiState.value = _uiState.value.copy(
                isMicEnabled = false,
                ringState = if (_uiState.value.ringState == RingState.LISTENING) RingState.IDLE else _uiState.value.ringState,
                audioLevel = 0.08f,
                statusCaption = "Mic Off • Tap mic to turn on"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isMicEnabled = true,
                statusCaption = "Mic On • Listening..."
            )
        }
    }

    fun toggleMic() {
        setMicEnabled(!_uiState.value.isMicEnabled)
    }

    fun onRingTapped() {
        val current = _uiState.value.ringState
        if (current == RingState.SLEEP) {
            setMicEnabled(true)
            setRingState(RingState.WAKE_UP, "Aapke liye hamesha, ${preferences.value.honorific}.")
            voiceEngine.speak("Yes ${preferences.value.honorific}.", preferences.value)
        } else if (current == RingState.SPEAKING) {
            voiceEngine.stopSpeaking()
            setRingState(RingState.IDLE, if (_uiState.value.isMicEnabled) "Listening..." else "Mic Off")
        } else {
            toggleMic()
        }
    }

    fun onRingLongPressed() {
        val next = if (_uiState.value.ringState == RingState.GESTURE_MODE) {
            RingState.IDLE
        } else {
            RingState.GESTURE_MODE
        }
        setRingState(next, next.statusPhrase)
    }

    fun setListeningState(active: Boolean, rmsDb: Float = 0f) {
        if (active) {
            val normalized = ((rmsDb + 2f) / 12f).coerceIn(0.15f, 1.0f)
            _uiState.value = _uiState.value.copy(
                ringState = RingState.LISTENING,
                audioLevel = normalized,
                statusCaption = "Listening..."
            )
        } else if (_uiState.value.ringState == RingState.LISTENING) {
            _uiState.value = _uiState.value.copy(
                ringState = RingState.IDLE,
                audioLevel = 0.1f,
                statusCaption = if (_uiState.value.isMicEnabled) "Ready & listening..." else "Mic Off • Tap mic to turn on"
            )
        }
    }

    fun setRingState(state: RingState, caption: String = state.statusPhrase) {
        _uiState.value = _uiState.value.copy(
            ringState = state,
            statusCaption = caption
        )
    }

    fun submitCommand(rawInput: String) {
        val text = rawInput.trim()
        if (text.isEmpty()) return

        val startedNano = System.nanoTime()
        val userTurn = ChatTurn(
            id = UUID.randomUUID().toString(),
            author = ChatAuthor.USER,
            text = text,
            isOfflineResponse = _uiState.value.isOfflineEffective
        )
        _uiState.value = _uiState.value.copy(
            chatHistory = (_uiState.value.chatHistory + userTurn).takeLast(40),
            ringState = RingState.THINKING,
            statusCaption = "Processing...",
            streamingReply = ""
        )

        viewModelScope.launch {
            vault.saveConversationTurn(userTurn, RingState.THINKING, latencyMs = 0L)
            val prefs = preferences.value
            val isOffline = _uiState.value.isOfflineEffective
            val activeCloudRepo = if (!isOffline) cloudSyncRepo else null

            vault.broadcastEcosystemCommand("ALL", "Command: ${text.take(80)}", activeCloudRepo, !isOffline)

            // Step 1: Always run deterministic Offline-First Intelligence first!
            val localResult = offlineEngine.processInput(
                rawInput = text,
                prefs = prefs,
                pendingDeepTopic = _uiState.value.pendingDeepTopic,
                cloudRepo = activeCloudRepo,
                isOnline = !isOffline
            )

            if (localResult.handledLocally) {
                val actionStatusOverride = localResult.deviceAction?.let {
                    executeLocalDeviceAction(it, prefs.honorific)
                }
                val finalReplyText = actionStatusOverride ?: localResult.replyText
                val effectivePrefs = if (localResult.updatedVoiceGender != null) {
                    updateVoiceGender(localResult.updatedVoiceGender)
                    val targetGender = localResult.updatedVoiceGender
                    val nextPersonality = if (targetGender == VoiceGender.FEMALE) {
                        VoicePersonality.FEMALE_INDIAN
                    } else if (prefs.voicePersonality == VoicePersonality.FEMALE_INDIAN) {
                        VoicePersonality.MALE_INDIAN
                    } else {
                        prefs.voicePersonality
                    }
                    prefs.copy(
                        voiceGender = targetGender,
                        voicePersonality = nextPersonality,
                        voicePitch = nextPersonality.defaultPitch
                    )
                } else {
                    prefs
                }
                if (localResult.nextRingState == RingState.SUCCESS) {
                    voiceEngine.playConfirmationTone()
                }
                val replyTurn = ChatTurn(
                    id = UUID.randomUUID().toString(),
                    author = ChatAuthor.TAGORE,
                    text = finalReplyText,
                    isOfflineResponse = true,
                    deepKnowledgeTopic = localResult.deepKnowledgeTopic
                )
                val elapsedMs = ((System.nanoTime() - startedNano) / 1_000_000L).coerceAtLeast(1L)
                vault.saveConversationTurn(replyTurn, localResult.nextRingState, latencyMs = elapsedMs)
                _uiState.value = _uiState.value.copy(
                    ringState = localResult.nextRingState,
                    statusCaption = finalReplyText,
                    chatHistory = (_uiState.value.chatHistory + replyTurn).takeLast(40),
                    pendingConfirmation = localResult.pendingConfirmation,
                    pendingDeepTopic = localResult.deepKnowledgeTopic
                )
                voiceEngine.speak(finalReplyText, effectivePrefs)

                if (localResult.nextRingState == RingState.SUCCESS || localResult.nextRingState == RingState.WAKE) {
                    delay(900)
                    if (_uiState.value.ringState == localResult.nextRingState && !voiceEngine.isSpeaking.value) {
                        _uiState.value = _uiState.value.copy(ringState = RingState.IDLE)
                    }
                }
                return@launch
            }

            // Step 2: If online and not in Privacy Lock, stream via fast Gemini with real-time sentence TTS!
            if (!isOffline) {
                _uiState.value = _uiState.value.copy(
                    ringState = RingState.SEARCHING,
                    statusCaption = "Soch raha hoon..."
                )
                val memoriesSnapshot = vault.getAllMemories()
                var spokenCursor = 0
                var hasFlushedTts = false

                cloudGeminiEngine.streamConversation(
                    userPrompt = text,
                    prefs = prefs,
                    recentHistory = _uiState.value.chatHistory,
                    memories = memoriesSnapshot
                ).collect { event ->
                    when (event) {
                        is CloudStreamEvent.Chunk -> {
                            val acc = event.accumulatedText
                            _uiState.value = _uiState.value.copy(
                                ringState = RingState.WORKING,
                                streamingReply = acc,
                                statusCaption = acc
                            )
                            // Speak completed sentences immediately as chunks arrive for near-zero latency
                            val remaining = acc.substring(spokenCursor)
                            val boundaryIdx = findSentenceBoundary(remaining)
                            if (boundaryIdx > 0) {
                                val sentence = remaining.substring(0, boundaryIdx).trim()
                                spokenCursor += boundaryIdx
                                if (sentence.isNotEmpty()) {
                                    voiceEngine.speakQueued(sentence, prefs, flushFirst = !hasFlushedTts)
                                    hasFlushedTts = true
                                }
                            }
                        }
                        is CloudStreamEvent.Completed -> {
                            val aiTurn = ChatTurn(
                                id = UUID.randomUUID().toString(),
                                author = ChatAuthor.TAGORE,
                                text = event.finalText,
                                isOfflineResponse = false
                            )
                            val elapsedMs = ((System.nanoTime() - startedNano) / 1_000_000L).coerceAtLeast(1L)
                            vault.saveConversationTurn(aiTurn, RingState.SPEAKING, latencyMs = elapsedMs)
                            _uiState.value = _uiState.value.copy(
                                ringState = RingState.SPEAKING,
                                streamingReply = "",
                                statusCaption = event.finalText,
                                chatHistory = (_uiState.value.chatHistory + aiTurn).takeLast(40),
                                pendingDeepTopic = null
                            )
                            vault.logSecureEvent("CLOUD_AI", "Cloud AI Query", text, isOffline = false)
                            val tail = if (spokenCursor < event.finalText.length) {
                                event.finalText.substring(spokenCursor).trim()
                            } else ""
                            if (tail.isNotEmpty()) {
                                voiceEngine.speakQueued(tail, prefs, flushFirst = !hasFlushedTts)
                                hasFlushedTts = true
                            }
                            if (!voiceEngine.isSpeaking.value) {
                                delay(400)
                                if (!voiceEngine.isSpeaking.value && _uiState.value.ringState == RingState.SPEAKING) {
                                    _uiState.value = _uiState.value.copy(ringState = RingState.IDLE)
                                }
                            }
                        }
                        is CloudStreamEvent.Failed -> {
                            // Honest error surface + graceful offline fallback
                            val fallbackText = "${event.errorMessage}\n\n${localResult.replyText}"
                            val errTurn = ChatTurn(
                                id = UUID.randomUUID().toString(),
                                author = ChatAuthor.TAGORE,
                                text = fallbackText,
                                isOfflineResponse = true
                            )
                            val elapsedMs = ((System.nanoTime() - startedNano) / 1_000_000L).coerceAtLeast(1L)
                            vault.saveConversationTurn(errTurn, RingState.ERROR, latencyMs = elapsedMs)
                            _uiState.value = _uiState.value.copy(
                                ringState = RingState.ERROR,
                                streamingReply = "",
                                statusCaption = event.errorMessage,
                                chatHistory = (_uiState.value.chatHistory + errTurn).takeLast(40)
                            )
                            vault.logSecureEvent("ERROR", "Cloud AI Error", event.errorMessage, isOffline = false)
                        }
                    }
                }
            } else {
                // Pure Offline Fallback response
                val offlineTurn = ChatTurn(
                    id = UUID.randomUUID().toString(),
                    author = ChatAuthor.TAGORE,
                    text = localResult.replyText,
                    isOfflineResponse = true
                )
                val elapsedMs = ((System.nanoTime() - startedNano) / 1_000_000L).coerceAtLeast(1L)
                vault.saveConversationTurn(offlineTurn, RingState.SPEAKING, latencyMs = elapsedMs)
                _uiState.value = _uiState.value.copy(
                    ringState = RingState.SPEAKING,
                    statusCaption = localResult.replyText,
                    chatHistory = (_uiState.value.chatHistory + offlineTurn).takeLast(40)
                )
                vault.logSecureEvent("OFFLINE_AI", "Local Query", text, isOffline = true)
                voiceEngine.speak(localResult.replyText, prefs)
                if (!voiceEngine.isSpeaking.value) {
                    delay(400)
                    if (!voiceEngine.isSpeaking.value && _uiState.value.ringState == RingState.SPEAKING) {
                        _uiState.value = _uiState.value.copy(ringState = RingState.IDLE)
                    }
                }
            }
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val c = text[i]
            if ((c == '.' || c == '?' || c == '!' || c == '।' || c == '\n') && i >= 12) {
                return i + 1
            }
        }
        return -1
    }

    fun confirmPendingAction(approved: Boolean) {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        val honorific = preferences.value.honorific

        if (!approved) {
            val cancelMsg = "Theek hai $honorific, action cancel kar diya gaya hai."
            setRingState(RingState.IDLE, cancelMsg)
            voiceEngine.speak(cancelMsg, preferences.value)
            return
        }

        viewModelScope.launch {
            when (pending.actionType) {
                ConfirmationActionType.UNLOCK_DOOR -> {
                    val targetId = pending.targetId.toLongOrNull()
                    val device = vault.getAllSmartDevices().firstOrNull { it.id == targetId }
                    if (device != null) {
                        vault.updateSmartDevice(
                            device.copy(isLocked = false, isOn = true),
                            if (!_uiState.value.isOfflineEffective) cloudSyncRepo else null,
                            !_uiState.value.isOfflineEffective
                        )
                        vault.logSecureEvent("SECURITY", "Door Unlocked (Authorized)", device.name, _uiState.value.isOfflineEffective)
                    }
                    voiceEngine.playConfirmationTone()
                    setRingState(RingState.SUCCESS, "Done $honorific. Door unlocked.")
                    voiceEngine.speak("Done $honorific.", preferences.value)
                }
                ConfirmationActionType.POST_SOCIAL -> {
                    vault.logSecureEvent("SOCIAL", "Social draft prepared & confirmed", pending.payload, _uiState.value.isOfflineEffective)
                    voiceEngine.playConfirmationTone()
                    setRingState(RingState.SUCCESS, "Done $honorific. Content queued for sharing.")
                    voiceEngine.speak("Done $honorific.", preferences.value)
                }
                ConfirmationActionType.EMERGENCY_CALL -> {
                    vault.logSecureEvent("EMERGENCY", "Emergency SOS Dialer Opened", "User explicitly confirmed SOS", _uiState.value.isOfflineEffective)
                    executeLocalDeviceAction(LocalDeviceAction.OpenDialer("112"))
                    setRingState(RingState.EMERGENCY, "Emergency dialer opened (112). Confirm call manually.")
                }
                ConfirmationActionType.CLEAR_LOGS -> {
                    vault.clearLogs()
                    voiceEngine.playConfirmationTone()
                    setRingState(RingState.SUCCESS, "Done $honorific. Encrypted logs cleared.")
                }
            }
        }
    }

    fun analyzeVisionImage(uri: Uri, bitmap: Bitmap, question: String) {
        _uiState.value = _uiState.value.copy(
            selectedVisionImageUri = uri,
            isVisionAnalyzing = true,
            ringState = RingState.CAMERA_VISION,
            visionAnalysisText = "Scanning visual input..."
        )
        viewModelScope.launch {
            if (_uiState.value.isOfflineEffective) {
                val msg = "Offline Vision Summary (${preferences.value.honorific}): Image loaded (${bitmap.width}x${bitmap.height}px) inside 360° interactive viewer. Connect to internet for deep Gemini multimodal object & OCR analysis."
                _uiState.value = _uiState.value.copy(
                    isVisionAnalyzing = false,
                    ringState = RingState.SPEAKING,
                    visionAnalysisText = msg
                )
                vault.logSecureEvent("VISION", "Loaded image offline", "${bitmap.width}x${bitmap.height}", true)
                return@launch
            }

            // Downscale bitmap safely so payload stays well under 20MB
            val scaled = scaleBitmapForAi(bitmap)
            val prompt = question.ifBlank {
                "Analyze this image thoroughly: identify key objects, read any visible text, explain diagrams or context, and answer concisely in TAGORE's respectful style."
            }
            cloudGeminiEngine.streamConversation(
                userPrompt = prompt,
                prefs = preferences.value,
                recentHistory = emptyList(),
                memories = vault.getAllMemories(),
                attachedImage = scaled
            ).collect { ev ->
                when (ev) {
                    is CloudStreamEvent.Chunk -> {
                        _uiState.value = _uiState.value.copy(visionAnalysisText = ev.accumulatedText)
                    }
                    is CloudStreamEvent.Completed -> {
                        _uiState.value = _uiState.value.copy(
                            isVisionAnalyzing = false,
                            ringState = RingState.SUCCESS,
                            visionAnalysisText = ev.finalText
                        )
                        vault.logSecureEvent("VISION", "Cloud Multimodal Vision Analysis", prompt, false)
                        voiceEngine.speak(ev.finalText, preferences.value)
                    }
                    is CloudStreamEvent.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            isVisionAnalyzing = false,
                            ringState = RingState.ERROR,
                            visionAnalysisText = "Analysis error: ${ev.errorMessage}"
                        )
                    }
                }
            }
        }
    }

    // Smart Home direct UI controls
    fun toggleSmartDevice(device: SmartDeviceEntity) {
        viewModelScope.launch {
            if (device.category == "LOCK" && device.isLocked) {
                _uiState.value = _uiState.value.copy(
                    pendingConfirmation = PendingConfirmation(
                        id = "unlock_${device.id}",
                        title = "Confirm Door Unlock",
                        promptSpeech = "${preferences.value.honorific}, kya main ${device.name} unlock kar doon?",
                        actionType = ConfirmationActionType.UNLOCK_DOOR,
                        targetId = device.id.toString()
                    )
                )
                return@launch
            }
            val updated = if (device.category == "LOCK") {
                device.copy(isLocked = true, isOn = true)
            } else {
                device.copy(isOn = !device.isOn)
            }
            val isOnline = !_uiState.value.isOfflineEffective
            vault.updateSmartDevice(updated, if (isOnline) cloudSyncRepo else null, isOnline)
            vault.logSecureEvent("SMART_HOME", "Toggled ${device.name}", "isOn=${updated.isOn}", !isOnline)
            voiceEngine.playConfirmationTone()
        }
    }

    fun updateSmartDeviceLevel(device: SmartDeviceEntity, newLevel: Int) {
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            val updated = device.copy(level = newLevel.coerceIn(0, 100), isOn = newLevel > 0)
            vault.updateSmartDevice(updated, if (isOnline) cloudSyncRepo else null, isOnline)
        }
    }

    fun updateSmartDeviceTemperature(device: SmartDeviceEntity, newTemp: Int) {
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            val updated = device.copy(temperature = newTemp.coerceIn(16, 32), isOn = true)
            vault.updateSmartDevice(updated, if (isOnline) cloudSyncRepo else null, isOnline)
        }
    }

    fun handoffToEcosystemDevice(device: EcosystemDeviceEntity) {
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            val summary = _uiState.value.statusCaption.take(120)
            vault.broadcastEcosystemCommand(
                targetFormFactor = device.formFactor,
                commandSummary = "Handoff: $summary",
                cloudRepo = if (isOnline) cloudSyncRepo else null,
                isOnline = isOnline
            )
            vault.logSecureEvent("ECOSYSTEM", "Handoff to ${device.name}", summary, !isOnline)
            voiceEngine.playConfirmationTone()
            setRingState(RingState.SUCCESS, "Done ${preferences.value.honorific}. Synced with ${device.name}.")
        }
    }

    // Memory & Productivity CRUD
    fun addMemory(category: String, title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            vault.saveMemory(
                category = category,
                title = title,
                content = content,
                pinned = false,
                cloudRepo = if (isOnline) cloudSyncRepo else null,
                isOnline = isOnline
            )
            vault.logSecureEvent("MEMORY", "Added memory: $title", content, !isOnline)
            voiceEngine.playConfirmationTone()
        }
    }

    fun deleteMemory(entity: MemoryEntity) {
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            vault.deleteMemory(entity, if (isOnline) cloudSyncRepo else null, isOnline)
            vault.logSecureEvent("MEMORY", "Deleted memory: ${entity.title}", entity.content, !isOnline)
        }
    }

    suspend fun exportMemoriesJson(): String = vault.exportMemoriesJson()

    fun addProductivityItem(kind: String, title: String, details: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            vault.addProductivityItem(
                kind = kind,
                title = title,
                details = details,
                dueAtMillis = System.currentTimeMillis() + 3600_000L,
                cloudRepo = if (isOnline) cloudSyncRepo else null,
                isOnline = isOnline
            )
            vault.logSecureEvent("PRODUCTIVITY", "Added $kind: $title", details, !isOnline)
            voiceEngine.playConfirmationTone()
        }
    }

    fun toggleProductivityDone(item: ProductivityEntity) {
        viewModelScope.launch {
            val isOnline = !_uiState.value.isOfflineEffective
            vault.toggleProductivityCompleted(item, if (isOnline) cloudSyncRepo else null, isOnline)
        }
    }

    fun deleteProductivityItem(id: Long) {
        viewModelScope.launch { vault.deleteProductivityItem(id) }
    }

    fun toggleDecryptLog(log: EncryptedLogEntity) {
        val current = _uiState.value.decryptedLogPreviews.toMutableMap()
        if (current.containsKey(log.id)) {
            current.remove(log.id)
        } else {
            current[log.id] = vault.decryptLog(log)
        }
        _uiState.value = _uiState.value.copy(decryptedLogPreviews = current)
    }

    fun requestClearLogs() {
        _uiState.value = _uiState.value.copy(
            pendingConfirmation = PendingConfirmation(
                id = "clear_logs",
                title = "Clear Encrypted Logs?",
                promptSpeech = "${preferences.value.honorific}, kya aap sabhi encrypted audit logs delete karna chahte hain?",
                actionType = ConfirmationActionType.CLEAR_LOGS
            )
        )
    }

    // Customization & Voice Settings
    fun updateDarkMode(dark: Boolean) = updatePref { it.copy(darkMode = dark) }
    fun updateVoiceGender(gender: VoiceGender) = updatePref { current ->
        val nextPersonality = when (gender) {
            VoiceGender.MALE -> if (current.voicePersonality == VoicePersonality.FEMALE_INDIAN) {
                VoicePersonality.MALE_INDIAN
            } else {
                current.voicePersonality
            }
            VoiceGender.FEMALE -> VoicePersonality.FEMALE_INDIAN
        }
        current.copy(
            voiceGender = gender,
            voicePersonality = nextPersonality,
            voicePitch = nextPersonality.defaultPitch,
            voiceSpeed = nextPersonality.defaultSpeed
        )
    }

    fun updateVoicePersonality(p: VoicePersonality) = updatePref {
        it.copy(
            voiceGender = if (p.preferMale) VoiceGender.MALE else VoiceGender.FEMALE,
            voicePersonality = p,
            voicePitch = p.defaultPitch,
            voiceSpeed = p.defaultSpeed
        )
    }
    fun updateVoicePitch(pitch: Float) = updatePref { it.copy(voicePitch = pitch.coerceIn(0.5f, 1.6f)) }
    fun updateVoiceSpeed(speed: Float) = updatePref { it.copy(voiceSpeed = speed.coerceIn(0.6f, 1.75f)) }
    fun updateVoiceTone(tone: VoiceTone) = updatePref { it.copy(voiceTone = tone) }
    fun updateResponseLength(len: ResponseLength) = updatePref { it.copy(responseLength = len) }
    fun updateLanguageMode(lang: LanguageMode) = updatePref { it.copy(languageMode = lang) }
    fun updateRingBrightness(b: Float) = updatePref { it.copy(ringBrightness = b.coerceIn(0.25f, 1.0f)) }
    fun updateAnimationSpeed(s: Float) = updatePref { it.copy(animationSpeed = s.coerceIn(0.4f, 2.0f)) }
    fun updateWakeSensitivity(w: Float) = updatePref { it.copy(wakeWordSensitivity = w.coerceIn(0.2f, 1.0f)) }
    fun updateOfflinePrivacyLock(lock: Boolean) = updatePref { it.copy(offlinePrivacyLock = lock) }
    fun updateVoiceOutputEnabled(enabled: Boolean) = updatePref { it.copy(voiceOutputEnabled = enabled) }
    fun updateAutoListenOnLaunch(enabled: Boolean) = updatePref { it.copy(autoListenOnLaunch = enabled) }
    fun updateIdentity(name: String, honorific: String) = updatePref {
        it.copy(
            preferredName = name.trim().ifBlank { it.preferredName },
            honorific = honorific.trim().ifBlank { it.honorific }
        )
    }

    fun testVoiceSample() {
        val p = preferences.value
        val sample = when (p.voicePersonality) {
            VoicePersonality.JARVIS -> "Yes ${p.honorific}. TAGORE holographic systems online. All offline and smart home modules are calibrated for you, ${p.preferredName}."
            VoicePersonality.BIGG_BOSS -> "TAGORE chahte hain ki ${p.preferredName} ${p.honorific} ka har kaam bilkul samay aur suraksha ke saath poora ho."
            VoicePersonality.MALE_INDIAN -> "Namaste ${p.preferredName} ${p.honorific}. Main TAGORE hoon, aapki natural male voice ab bilkul ready hai."
            VoicePersonality.FEMALE_INDIAN -> "Namaste ${p.preferredName} ${p.honorific}. Main TAGORE hoon, aapki natural female voice set ho chuki hai."
            VoicePersonality.STANDARD_ASSISTANT -> "Hello ${p.preferredName} ${p.honorific}. TAGORE standard assistant voice calibration is complete."
        }
        voiceEngine.speak(sample, p)
    }

    private fun updatePref(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            prefsRepo.updatePreferences(transform)
            val updated = preferences.value
            if (!_uiState.value.isOfflineEffective && cloudSyncRepo != null) {
                cloudSyncRepo.upsertUserProfile(updated)
            }
        }
    }

    private fun executeLocalDeviceAction(
        action: LocalDeviceAction,
        honorific: String = preferences.value.honorific
    ): String? {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return when (action) {
            is LocalDeviceAction.AdjustVolume -> {
                val dir = if (action.deltaPercent >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                null
            }
            is LocalDeviceAction.SetVolumePercent -> {
                if (am != null) {
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val target = ((maxVol * action.percent.coerceIn(0, 100)) / 100f).toInt().coerceIn(0, maxVol)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                }
                null
            }
            is LocalDeviceAction.SetMute -> {
                if (am != null) {
                    val adj = if (action.mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, AudioManager.FLAG_SHOW_UI)
                    runCatching {
                        am.ringerMode = if (action.mute) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
                    }
                }
                null
            }
            is LocalDeviceAction.MediaControl -> {
                if (am != null) {
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, action.keyCode))
                }
                null
            }
            is LocalDeviceAction.AdjustBrightness -> {
                startNewTaskActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                null
            }
            is LocalDeviceAction.SetFlashlight -> {
                val ok = runCatching {
                    val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    val camId = cm?.cameraIdList?.firstOrNull()
                    if (camId != null) {
                        val nextState = action.enable ?: !torchEnabled
                        torchEnabled = nextState
                        cm.setTorchMode(camId, nextState)
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
                if (!ok) "$honorific, is device par flashlight camera unit available nahi mili." else null
            }
            is LocalDeviceAction.OpenSystemSettings -> {
                val launched = startNewTaskActivity(Intent(action.settingsAction)) ||
                    startNewTaskActivity(Intent(Settings.ACTION_SETTINGS))
                if (!launched) "$honorific, settings panel open nahi ho paya." else null
            }
            is LocalDeviceAction.LaunchApp -> {
                launchInstalledApp(action.appQuery, honorific)
            }
            is LocalDeviceAction.OpenCamera -> {
                val cameraAction = if (action.videoMode) {
                    MediaStore.INTENT_ACTION_VIDEO_CAMERA
                } else {
                    MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                }
                val launched = startNewTaskActivity(Intent(cameraAction)) ||
                    startNewTaskActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                if (!launched) "$honorific, camera app open nahi ho paya." else null
            }
            is LocalDeviceAction.OpenDialer -> {
                val uri = if (action.number.isNotBlank()) Uri.parse("tel:${action.number}") else Uri.parse("tel:")
                startNewTaskActivity(Intent(Intent.ACTION_DIAL, uri))
                null
            }
            is LocalDeviceAction.OpenSmsDraft -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                    if (action.message.isNotBlank()) {
                        putExtra("sms_body", action.message)
                    }
                }
                startNewTaskActivity(intent)
                null
            }
            is LocalDeviceAction.SetSystemAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                val launched = startNewTaskActivity(intent)
                if (!launched) "$honorific, Clock app mein alarm set nahi ho paya." else null
            }
            is LocalDeviceAction.SetSystemTimer -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                val launched = startNewTaskActivity(intent) ||
                    startNewTaskActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    })
                if (!launched) "$honorific, timer app available nahi mila." else null
            }
            is LocalDeviceAction.WebOrMapSearch -> {
                val encoded = runCatching { URLEncoder.encode(action.query, "UTF-8") }.getOrDefault(action.query)
                val uri = when (action.target) {
                    SearchTarget.YOUTUBE -> Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                    SearchTarget.MAPS -> Uri.parse("geo:0,0?q=$encoded")
                    SearchTarget.GOOGLE -> Uri.parse("https://www.google.com/search?q=$encoded")
                }
                val launched = startNewTaskActivity(Intent(Intent.ACTION_VIEW, uri))
                if (!launched) "$honorific, search open karne ke liye browser ya maps app nahi mila." else null
            }
            LocalDeviceAction.SpeakDeviceTelemetry -> {
                readDeviceTelemetrySummary(honorific)
            }
        }
    }

    private fun launchInstalledApp(appQuery: String, honorific: String): String? {
        val clean = appQuery.trim().lowercase(java.util.Locale.ROOT)
        if (clean == "camera") {
            return if (startNewTaskActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) null
            else "$honorific, camera app nahi mila."
        }
        if (clean == "phone" || clean == "dialer" || clean == "contacts") {
            return if (startNewTaskActivity(Intent(Intent.ACTION_DIAL))) null
            else "$honorific, phone dialer nahi mila."
        }

        val knownPackages = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "chrome" to listOf("com.android.chrome"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "gmail" to listOf("com.google.android.gm"),
            "spotify" to listOf("com.spotify.music"),
            "telegram" to listOf("org.telegram.messenger"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
            "alarm" to listOf("com.google.android.deskclock", "com.android.deskclock"),
            "calendar" to listOf("com.google.android.calendar", "com.android.calendar"),
            "play store" to listOf("com.android.vending"),
            "gallery" to listOf("com.google.android.apps.photos", "com.android.gallery3d"),
            "photos" to listOf("com.google.android.apps.photos")
        )

        val pm = appContext.packageManager
        knownPackages.entries.firstOrNull { (key, _) -> clean.contains(key) }?.value?.forEach { pkg ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null && startNewTaskActivity(launchIntent)) {
                return null
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = runCatching {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrElse { emptyList() }

        val matchedResolve = installed.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(java.util.Locale.ROOT)
            label == clean || label.contains(clean) || clean.contains(label)
        }
        if (matchedResolve != null) {
            val pkg = matchedResolve.activityInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null && startNewTaskActivity(launchIntent)) {
                return null
            }
        }
        return "$honorific, '$appQuery' app is phone par install nahi mila."
    }

    private fun readDeviceTelemetrySummary(honorific: String): String {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        val isCharging = bm?.isCharging ?: false

        val freeGbText = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = (stat.availableBlocksLong * stat.blockSizeLong).toDouble() / (1024.0 * 1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.1f GB", freeGb)
        }.getOrNull()

        val batteryPart = when {
            batteryPct != null && isCharging -> "battery $batteryPct percent hai aur phone charge ho raha hai"
            batteryPct != null -> "battery $batteryPct percent hai"
            else -> "battery status read nahi ho paya"
        }
        val storagePart = if (freeGbText != null) " aur $freeGbText internal storage khaali hai" else ""
        return "Ji $honorific, phone ki $batteryPart$storagePart."
    }

    private fun startNewTaskActivity(intent: Intent): Boolean {
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun scaleBitmapForAi(bitmap: Bitmap): Bitmap {
        val maxDim = 1280
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.release()
        voiceEngine.shutdown()
    }

    class Factory(
        private val context: Context,
        private val cloudSyncRepo: TagoreCloudSyncRepository?,
        private val isAuthenticated: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            return TagoreViewModel(
                appContext = appCtx,
                vault = TagoreVaultRepository(appCtx),
                prefsRepo = PreferencesRepository(appCtx),
                networkMonitor = NetworkMonitor(appCtx),
                voiceEngine = TagoreVoiceEngine(appCtx),
                cloudSyncRepo = cloudSyncRepo,
                isAuthenticated = isAuthenticated
            ) as T
        }
    }
}
