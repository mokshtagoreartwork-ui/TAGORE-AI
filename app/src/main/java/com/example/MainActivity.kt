package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.auth.attemptAutoSignIn
import com.example.core.auth.authStateFlow
import com.example.core.configureAppCheck
import com.example.data.local.PreferencesRepository
import com.example.data.remote.TagoreCloudSyncRepository
import com.example.domain.model.UserPreferences
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.RingHomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TagoreRoutes
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.TagoreTheme
import com.example.ui.viewmodel.TagoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            TagoreRootApp()
        }
    }
}

@Composable
fun TagoreRootApp() {
    val context = LocalContext.current
    val prefsRepo = remember(context) { PreferencesRepository(context.applicationContext) }
    val rootPrefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())

    val isFirebaseReady = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    val authFlow = remember(isFirebaseReady) {
        if (isFirebaseReady) Firebase.auth.authStateFlow() else flowOf<FirebaseUser?>(null)
    }
    val initialFirebaseUser = remember(isFirebaseReady) {
        if (isFirebaseReady) Firebase.auth.currentUser else null
    }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = initialFirebaseUser)

    var offlineLocalMode by rememberSaveable { mutableStateOf(!isFirebaseReady) }

    LaunchedEffect(isFirebaseReady) {
        if (isFirebaseReady && Firebase.auth.currentUser == null) {
            val cm = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = cm,
                onAuthSuccess = { offlineLocalMode = false },
                onUnauthenticated = {},
                scope = this
            )
        }
    }

    TagoreTheme(darkTheme = rootPrefs.darkMode) {
        val user = currentUser
        if (user == null && !offlineLocalMode) {
            AuthScreen(
                isDarkMode = rootPrefs.darkMode,
                onAuthSuccess = { offlineLocalMode = false },
                onContinueOffline = { offlineLocalMode = true }
            )
        } else {
            val sessionKey = user?.uid ?: "offline_local_vault"
            key(sessionKey) {
                val cloudSyncRepo = remember(user?.uid) {
                    if (user != null) TagoreCloudSyncRepository(context.applicationContext) else null
                }
                val viewModel: TagoreViewModel = viewModel(
                    key = sessionKey,
                    factory = TagoreViewModel.Factory(
                        context = context.applicationContext,
                        cloudSyncRepo = cloudSyncRepo,
                        isAuthenticated = user != null
                    )
                )
                TagoreNavGraph(
                    viewModel = viewModel,
                    isAuthenticated = user != null,
                    userEmail = user?.email,
                    onSignedIn = { offlineLocalMode = false },
                    onSignedOut = { offlineLocalMode = false }
                )
            }
        }
    }
}

@Composable
private fun TagoreNavGraph(
    viewModel: TagoreViewModel,
    isAuthenticated: Boolean,
    userEmail: String?,
    onSignedIn: () -> Unit,
    onSignedOut: () -> Unit
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val smartDevices by viewModel.smartDevices.collectAsStateWithLifecycle()
    val ecosystemDevices by viewModel.ecosystemDevices.collectAsStateWithLifecycle()
    val productivityItems by viewModel.productivityItems.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val encryptedLogs by viewModel.encryptedLogs.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = TagoreRoutes.RING_HOME
    ) {
        composable(TagoreRoutes.RING_HOME) {
            RingHomeScreen(
                uiState = uiState,
                prefs = prefs,
                pendingSyncCount = pendingSyncCount,
                onRingTap = viewModel::onRingTapped,
                onRingLongPress = viewModel::onRingLongPressed,
                onSetMicEnabled = viewModel::setMicEnabled,
                onListeningStateChange = viewModel::setListeningState,
                onSubmitCommand = viewModel::submitCommand,
                onConfirmPendingAction = viewModel::confirmPendingAction,
                onCompleteOnboarding = viewModel::completeOnboarding,
                onOpenWorkspace = { navController.navigate(TagoreRoutes.WORKSPACE) },
                onOpenSettings = { navController.navigate(TagoreRoutes.SETTINGS) },
                onTriggerEmergency = { viewModel.submitCommand("emergency sos") }
            )
        }

        composable(TagoreRoutes.WORKSPACE) {
            WorkspaceScreen(
                uiState = uiState,
                isDarkMode = prefs.darkMode,
                smartDevices = smartDevices,
                ecosystemDevices = ecosystemDevices,
                productivityItems = productivityItems,
                memories = memories,
                encryptedLogs = encryptedLogs,
                onBack = { navController.popBackStack() },
                onToggleSmartDevice = viewModel::toggleSmartDevice,
                onUpdateSmartLevel = viewModel::updateSmartDeviceLevel,
                onUpdateSmartTemp = viewModel::updateSmartDeviceTemperature,
                onRunRoutine = { cmd -> viewModel.submitCommand(cmd) },
                onEcosystemHandoff = viewModel::handoffToEcosystemDevice,
                onAddTask = viewModel::addProductivityItem,
                onToggleTask = viewModel::toggleProductivityDone,
                onDeleteTask = viewModel::deleteProductivityItem,
                onAddMemory = viewModel::addMemory,
                onDeleteMemory = viewModel::deleteMemory,
                onExportMemories = viewModel::exportMemoriesJson,
                onAnalyzeImage = viewModel::analyzeVisionImage,
                onToggleDecryptLog = viewModel::toggleDecryptLog,
                onClearLogs = viewModel::requestClearLogs,
                onPreviewRingState = { state -> viewModel.setRingState(state) }
            )
        }

        composable(TagoreRoutes.SETTINGS) {
            SettingsScreen(
                prefs = prefs,
                isAuthenticated = isAuthenticated,
                userEmail = userEmail,
                onBack = { navController.popBackStack() },
                onDarkModeChange = viewModel::updateDarkMode,
                onVoiceGenderChange = viewModel::updateVoiceGender,
                onPersonalityChange = viewModel::updateVoicePersonality,
                onPitchChange = viewModel::updateVoicePitch,
                onSpeedChange = viewModel::updateVoiceSpeed,
                onToneChange = viewModel::updateVoiceTone,
                onLengthChange = viewModel::updateResponseLength,
                onLanguageChange = viewModel::updateLanguageMode,
                onBrightnessChange = viewModel::updateRingBrightness,
                onAnimSpeedChange = viewModel::updateAnimationSpeed,
                onWakeSensitivityChange = viewModel::updateWakeSensitivity,
                onOfflinePrivacyLockChange = viewModel::updateOfflinePrivacyLock,
                onVoiceOutputChange = viewModel::updateVoiceOutputEnabled,
                onAutoListenChange = viewModel::updateAutoListenOnLaunch,
                onIdentityUpdate = viewModel::updateIdentity,
                onTestVoice = viewModel::testVoiceSample,
                onSignedInSuccess = onSignedIn,
                onSignedOut = onSignedOut
            )
        }
    }
}
