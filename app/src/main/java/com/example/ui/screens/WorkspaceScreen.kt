package com.example.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.EcosystemDeviceEntity
import com.example.data.local.EncryptedLogEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.ProductivityEntity
import com.example.data.local.SmartDeviceEntity
import com.example.domain.model.RingState
import com.example.ui.viewmodel.TagoreMainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    uiState: TagoreMainUiState,
    isDarkMode: Boolean,
    smartDevices: List<SmartDeviceEntity>,
    ecosystemDevices: List<EcosystemDeviceEntity>,
    productivityItems: List<ProductivityEntity>,
    memories: List<MemoryEntity>,
    encryptedLogs: List<EncryptedLogEntity>,
    onBack: () -> Unit,
    onToggleSmartDevice: (SmartDeviceEntity) -> Unit,
    onUpdateSmartLevel: (SmartDeviceEntity, Int) -> Unit,
    onUpdateSmartTemp: (SmartDeviceEntity, Int) -> Unit,
    onRunRoutine: (String) -> Unit,
    onEcosystemHandoff: (EcosystemDeviceEntity) -> Unit,
    onAddTask: (String, String, String) -> Unit,
    onToggleTask: (ProductivityEntity) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onAddMemory: (String, String, String) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    onExportMemories: suspend () -> String,
    onAnalyzeImage: (android.net.Uri, android.graphics.Bitmap, String) -> Unit,
    onToggleDecryptLog: (EncryptedLogEntity) -> Unit,
    onClearLogs: () -> Unit,
    onPreviewRingState: (RingState) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Smart Home" to Icons.Default.Home,
        "Ecosystem" to Icons.Default.Devices,
        "Vault" to Icons.Default.NoteAlt,
        "Vision 360" to Icons.Default.ImageSearch,
        "Logs" to Icons.Default.EnhancedEncryption
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TAGORE Workspace", style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("workspace_back_button")
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
            bottomBar = {
                if (!isExpandedWidth) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedWidth) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEachIndexed { index, (label, icon) ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> SmartHomeTab(
                            devices = smartDevices,
                            onToggleDevice = onToggleSmartDevice,
                            onUpdateLevel = onUpdateSmartLevel,
                            onUpdateTemperature = onUpdateSmartTemp,
                            onRunRoutine = onRunRoutine
                        )
                        1 -> EcosystemTab(
                            devices = ecosystemDevices,
                            currentRingState = uiState.ringState,
                            isOffline = uiState.isOfflineEffective,
                            isDarkMode = isDarkMode,
                            onHandoff = onEcosystemHandoff
                        )
                        2 -> ProductivityMemoryTab(
                            items = productivityItems,
                            memories = memories,
                            onAddTask = onAddTask,
                            onToggleTask = onToggleTask,
                            onDeleteTask = onDeleteTask,
                            onAddMemory = onAddMemory,
                            onDeleteMemory = onDeleteMemory,
                            onExportMemories = onExportMemories
                        )
                        3 -> Vision360Tab(
                            analysisText = uiState.visionAnalysisText,
                            isAnalyzing = uiState.isVisionAnalyzing,
                            onAnalyzeImage = onAnalyzeImage
                        )
                        4 -> EncryptedLogsDiagnosticsTab(
                            logs = encryptedLogs,
                            decryptedMap = uiState.decryptedLogPreviews,
                            currentRingState = uiState.ringState,
                            onToggleDecrypt = onToggleDecryptLog,
                            onClearLogs = onClearLogs,
                            onPreviewRingState = onPreviewRingState
                        )
                    }
                }
            }
        }
    }
}
