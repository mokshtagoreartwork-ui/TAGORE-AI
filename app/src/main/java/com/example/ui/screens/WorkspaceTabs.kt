package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.local.EcosystemDeviceEntity
import com.example.data.local.EncryptedLogEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.ProductivityEntity
import com.example.data.local.SmartDeviceEntity
import com.example.domain.model.RingState
import com.example.ui.components.HolographicRing
import com.example.ui.theme.TagoreOfflineAmber
import com.example.ui.theme.TagoreSuccessGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartHomeTab(
    devices: List<SmartDeviceEntity>,
    onToggleDevice: (SmartDeviceEntity) -> Unit,
    onUpdateLevel: (SmartDeviceEntity, Int) -> Unit,
    onUpdateTemperature: (SmartDeviceEntity, Int) -> Unit,
    onRunRoutine: (String) -> Unit
) {
    var selectedRoom by remember { mutableStateOf("All") }
    val rooms = remember(devices) { listOf("All") + devices.map { it.room }.distinct() }
    val filtered = remember(devices, selectedRoom) {
        if (selectedRoom == "All") devices else devices.filter { it.room == selectedRoom }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Quick Automation Routines", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRunRoutine("Focus mode activate karo") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Work Focus")
                }
                OutlinedButton(
                    onClick = { onRunRoutine("Night mode activate karo") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Night Security")
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    FilterChip(
                        selected = selectedRoom == room,
                        onClick = { selectedRoom = room },
                        label = { Text(room) }
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { dev ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (dev.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = when (dev.category) {
                                    "LOCK" -> if (dev.isLocked) Icons.Default.Lock else Icons.Default.LockOpen
                                    else -> Icons.Default.Lightbulb
                                },
                                contentDescription = null,
                                tint = if (dev.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(dev.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${dev.room} • ${dev.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (dev.category == "LOCK") {
                            OutlinedButton(onClick = { onToggleDevice(dev) }) {
                                Text(if (dev.isLocked) "Locked" else "Unlocked")
                            }
                        } else {
                            Switch(
                                checked = dev.isOn,
                                onCheckedChange = { onToggleDevice(dev) }
                            )
                        }
                    }

                    if (dev.category == "LIGHT" || dev.category == "FAN") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Intensity / Speed: ${dev.level}%", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = dev.level.toFloat(),
                            onValueChange = { onUpdateLevel(dev, it.toInt()) },
                            valueRange = 0f..100f
                        )
                    } else if (dev.category == "AC") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Target Temp: ${dev.temperature}°C", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = dev.temperature.toFloat(),
                            onValueChange = { onUpdateTemperature(dev, it.toInt()) },
                            valueRange = 16f..30f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EcosystemTab(
    devices: List<EcosystemDeviceEntity>,
    currentRingState: RingState,
    isOffline: Boolean,
    isDarkMode: Boolean,
    onHandoff: (EcosystemDeviceEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Lightweight Smartwatch Companion Mirror Preview
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HolographicRing(
                        ringState = currentRingState,
                        isOfflineMode = isOffline,
                        audioLevel = 0.25f,
                        brightness = 0.95f,
                        animationSpeed = 1.0f,
                        isDarkMode = isDarkMode,
                        onTap = {},
                        onLongPress = {},
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Watch & Cross-Device Mirror", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "TAGORE Watch mirrors ${currentRingState.displayTitle} state across Phone, Laptop, Watch & Tablet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(devices, key = { it.id }) { dev ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (dev.formFactor) {
                            "LAPTOP" -> Icons.Default.LaptopMac
                            "WATCH" -> Icons.Default.Watch
                            "TABLET" -> Icons.Default.TabletMac
                            else -> Icons.Default.PhoneAndroid
                        },
                        contentDescription = dev.formFactor,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dev.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${dev.status} • ${dev.batteryPercent}% • ${dev.lastCommand}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { onHandoff(dev) }) {
                        Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductivityMemoryTab(
    items: List<ProductivityEntity>,
    memories: List<MemoryEntity>,
    onAddTask: (String, String, String) -> Unit,
    onToggleTask: (ProductivityEntity) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onAddMemory: (String, String, String) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    onExportMemories: suspend () -> String
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var newTitle by remember { mutableStateOf("") }
    var newDetails by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf("NOTE") }
    var exportFeedback by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline Productivity & Memory Vault", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NOTE", "TODO", "REMINDER", "MEMORY").forEach { kind ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = { selectedKind = kind },
                                label = { Text(kind) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title / Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDetails,
                        onValueChange = { newDetails = it },
                        label = { Text("Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank()) {
                                    if (selectedKind == "MEMORY") {
                                        onAddMemory("Personal", newTitle, newDetails.ifBlank { newTitle })
                                    } else {
                                        onAddTask(selectedKind, newTitle, newDetails)
                                    }
                                    newTitle = ""
                                    newDetails = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Offline")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = onExportMemories()
                                    clipboard.setText(AnnotatedString(json))
                                    exportFeedback = "Exported ${memories.size} memories JSON to clipboard."
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Export JSON", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                    if (exportFeedback.isNotBlank()) {
                        Text(exportFeedback, style = MaterialTheme.typography.labelSmall, color = TagoreSuccessGreen)
                    }
                }
            }
        }

        item {
            Text("Tasks, Notes & Reminders (${items.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(items, key = { "task_${it.id}" }) { item ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = item.completed, onCheckedChange = { onToggleTask(item) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.kind}: ${item.title}", style = MaterialTheme.typography.titleMedium)
                        if (item.details.isNotBlank()) {
                            Text(
                                item.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onDeleteTask(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete task")
                    }
                }
            }
        }

        item {
            Text("Personal Memories (${memories.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(memories, key = { "mem_${it.id}" }) { mem ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${mem.category} • ${mem.title}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            mem.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDeleteMemory(mem) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete memory")
                    }
                }
            }
        }
    }
}

@Composable
fun Vision360Tab(
    analysisText: String,
    isAnalyzing: Boolean,
    onAnalyzeImage: (Uri, Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadedUri by remember { mutableStateOf<Uri?>(null) }
    var question by remember { mutableStateOf("") }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            if (bmp != null) {
                loadedBitmap = bmp
                loadedUri = uri
                scale = 1f
                rotation = 0f
                offsetX = 0f
                offsetY = 0f
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Multimodal Vision & 360° Interactive Viewer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pinch to zoom, drag to pan, twist to rotate 360°, and ask TAGORE to analyze diagrams, artwork, or documents.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_vision_image_button")
                    ) {
                        Icon(Icons.Default.ImageSearch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Image (Photo Picker)")
                    }

                    if (loadedBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, rot ->
                                        scale = (scale * zoom).coerceIn(0.6f, 4.0f)
                                        rotation = (rotation + rot) % 360f
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = loadedBitmap!!.asImageBitmap(),
                                contentDescription = "Interactive 360 image preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        rotationZ = rotation
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { rotation = (rotation + 45f) % 360f },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Rotate +45°")
                            }
                            OutlinedButton(
                                onClick = {
                                    scale = 1f; rotation = 0f; offsetX = 0f; offsetY = 0f
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            label = { Text("Ask TAGORE about this image...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (loadedUri != null && loadedBitmap != null) {
                                    onAnalyzeImage(loadedUri!!, loadedBitmap!!, question)
                                }
                            },
                            enabled = !isAnalyzing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing...")
                            } else {
                                Text("Analyze with TAGORE Vision")
                            }
                        }
                    }

                    if (analysisText.isNotBlank()) {
                        Text(
                            text = analysisText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncryptedLogsDiagnosticsTab(
    logs: List<EncryptedLogEntity>,
    decryptedMap: Map<Long, String>,
    currentRingState: RingState,
    onToggleDecrypt: (EncryptedLogEntity) -> Unit,
    onClearLogs: () -> Unit,
    onPreviewRingState: (RingState) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("15 Ring States Interactive Showcase", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RingState.entries.forEach { st ->
                            FilterChip(
                                selected = currentRingState == st,
                                onClick = { onPreviewRingState(st) },
                                label = { Text(st.displayTitle, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AES-256-GCM Audit Logs (${logs.size})", style = MaterialTheme.typography.titleMedium)
                }
                if (logs.isNotEmpty()) {
                    OutlinedButton(onClick = onClearLogs) {
                        Text("Clear")
                    }
                }
            }
        }

        items(logs, key = { it.id }) { log ->
            val decrypted = decryptedMap[log.id]
            Card(
                onClick = { onToggleDecrypt(log) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${log.category} • ${log.actionSummary}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (log.isOfflineEvent) "OFFLINE" else "CLOUD",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (log.isOfflineEvent) TagoreOfflineAmber else TagoreSuccessGreen
                        )
                    }
                    Text(
                        text = dateFmt.format(Date(log.timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (decrypted != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TagoreSuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = decrypted,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "AES-GCM Cipher: ${log.encryptedPayloadBase64.take(36)}... (Tap to decrypt)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
