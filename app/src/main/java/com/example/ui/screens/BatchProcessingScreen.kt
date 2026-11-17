package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioPlayerManager
import com.example.audio.BatchAudioProcessor
import com.example.audio.BatchChainConfig
import com.example.audio.BatchItem
import com.example.audio.BatchItemStatus
import com.example.audio.DemoAudioType
import com.example.data.MediaRepository
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchProcessingDialog(
    mediaRepository: MediaRepository,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onNavigateToExports: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighContrast = LocalHighContrast.current

    val batchProcessor = remember { BatchAudioProcessor(context) }
    val playerManager = remember { AudioPlayerManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }

    val selectedItems = remember { mutableStateListOf<BatchItem>() }
    var config by remember { mutableStateOf(BatchChainConfig()) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var processingStatusMessage by remember { mutableStateOf("") }
    var processingJob by remember { mutableStateOf<Job?>(null) }
    var hasCompleted by remember { mutableStateOf(false) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    var currentPlayingPath by remember { mutableStateOf<String?>(null) }

    // Multi-file picker contract
    val multipleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val item = batchProcessor.createItemFromUri(uri)
                selectedItems.add(item)
            }
            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
            AccessibilityHelper.announceForAccessibility(
                context,
                "Added ${uris.size} audio files to batch. Total files: ${selectedItems.size}"
            )
        }
    }

    val listState = rememberLazyListState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dialog_batch_processing"),
        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Batch Processing Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Apply normalization, trimming, and effects to multiple files",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        processingJob?.cancel()
                        playerManager.stop()
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_close_batch_studio")
                        .semantics { contentDescription = "Close Batch Processing Studio" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: File Selection Toolbar & List
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_batch_file_selection"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (isHighContrast) 2.dp else 1.dp,
                            if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Selected Files (${selectedItems.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (selectedItems.isNotEmpty() && !isProcessing) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedItems.clear()
                                            hasCompleted = false
                                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                            AccessibilityHelper.announceForAccessibility(context, "Cleared all files from batch list")
                                        },
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Text("Clear All", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Add files buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                        multipleFilePicker.launch("audio/*")
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_batch_add_storage_files")
                                        .semantics {
                                            contentDescription = "Select audio files from device storage for batch processing"
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("+ Add Files", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                        // Add 4 demo tracks for instant testing
                                        selectedItems.add(batchProcessor.createDemoItem(DemoAudioType.PIANO_MELODY))
                                        selectedItems.add(batchProcessor.createDemoItem(DemoAudioType.VOICE_TONE))
                                        selectedItems.add(batchProcessor.createDemoItem(DemoAudioType.AMBIENT_PAD))
                                        selectedItems.add(batchProcessor.createDemoItem(DemoAudioType.PERCUSSION_BEAT))
                                        AccessibilityHelper.announceForAccessibility(
                                            context,
                                            "Added 4 demo tracks. Ready for batch processing."
                                        )
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_batch_add_demo_tracks")
                                        .semantics {
                                            contentDescription = "Add 4 demo audio tracks for instant offline batch test"
                                        }
                                ) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("+ Demo Tracks", fontSize = 13.sp)
                                }
                            }

                            if (selectedItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No files selected yet.\nTap '+ Add Files' to pick audio from storage or '+ Demo Tracks' to test instantly.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected File Items
                items(selectedItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_batch_${item.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighContrast) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            when (item.status) {
                                BatchItemStatus.COMPLETED -> Color(0xFF4CAF50)
                                BatchItemStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                                BatchItemStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (item.status) {
                                        BatchItemStatus.COMPLETED -> Icons.Default.CheckCircle
                                        BatchItemStatus.ERROR -> Icons.Default.Error
                                        BatchItemStatus.PROCESSING -> Icons.Default.Refresh
                                        else -> Icons.Default.Audiotrack
                                    },
                                    contentDescription = null,
                                    tint = when (item.status) {
                                        BatchItemStatus.COMPLETED -> Color(0xFF4CAF50)
                                        BatchItemStatus.ERROR -> MaterialTheme.colorScheme.error
                                        BatchItemStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )

                                Column {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    val statusText = when (item.status) {
                                        BatchItemStatus.COMPLETED -> "Ready: ${item.resultFile?.name ?: "Processed"}"
                                        BatchItemStatus.PROCESSING -> "Processing..."
                                        BatchItemStatus.ERROR -> "Error: ${item.errorMessage ?: "Failed"}"
                                        BatchItemStatus.PENDING -> if (item.durationMs > 0) "${item.durationMs / 1000}s" else "Pending"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.status == BatchItemStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Quick Play button if completed
                            if (item.status == BatchItemStatus.COMPLETED && item.resultFile != null) {
                                val isThisPlaying = isPlaying && currentPlayingPath == item.resultFile.absolutePath
                                IconButton(
                                    onClick = {
                                        if (isThisPlaying) {
                                            playerManager.pause()
                                        } else {
                                            currentPlayingPath = item.resultFile.absolutePath
                                            playerManager.loadUri(Uri.fromFile(item.resultFile))
                                            playerManager.play()
                                        }
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_play_batch_item_${item.id}")
                                        .semantics {
                                            contentDescription = if (isThisPlaying) "Pause preview for ${item.name}" else "Play preview of processed ${item.name}"
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Remove Item button (if not currently processing)
                            if (!isProcessing) {
                                IconButton(
                                    onClick = {
                                        selectedItems.remove(item)
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                        AccessibilityHelper.announceForAccessibility(context, "Removed ${item.name}")
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_remove_batch_item_${item.id}")
                                        .semantics { contentDescription = "Remove ${item.name} from batch" }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Consistent Effects Chain Configuration
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_batch_effects_chain"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (isHighContrast) 2.dp else 1.dp,
                            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = "Consistent Effects Chain",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "These effects will be applied sequentially to all files in one operation:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 1. Peak Normalization Toggle
                            BatchToggleRow(
                                title = "Peak Normalization (-0.1 dB)",
                                subtitle = "Maximizes volume across all files to consistent professional level",
                                isChecked = config.normalize,
                                isHighContrast = isHighContrast,
                                onCheckedChange = { config = config.copy(normalize = it) },
                                testTag = "toggle_batch_normalize"
                            )

                            // 2. Auto-Trim Silence Toggle
                            BatchToggleRow(
                                title = "Auto-Trim Silence",
                                subtitle = "Removes quiet lead-in and trailing dead silence below -40 dB",
                                isChecked = config.trimSilence,
                                isHighContrast = isHighContrast,
                                onCheckedChange = { config = config.copy(trimSilence = it) },
                                testTag = "toggle_batch_trim_silence"
                            )

                            // 3. Trim Start & Duration options
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Trim Range / Duration Limits:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val startOptions = listOf(0L to "Start 0s", 500L to "Cut 0.5s", 1000L to "Cut 1s")
                                    startOptions.forEach { (ms, label) ->
                                        FilterChip(
                                            selected = config.trimStartMs == ms,
                                            onClick = { config = config.copy(trimStartMs = ms) },
                                            label = { Text(label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }

                                    val maxOptions = listOf(0L to "Full Track", 10000L to "Max 10s", 30000L to "Max 30s", 60000L to "Max 60s")
                                    maxOptions.forEach { (ms, label) ->
                                        FilterChip(
                                            selected = config.maxDurationMs == ms,
                                            onClick = { config = config.copy(maxDurationMs = ms) },
                                            label = { Text(label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    }
                                }
                            }

                            // 4. De-Click & De-Crackle Filter
                            BatchToggleRow(
                                title = "De-Click & De-Crackle",
                                subtitle = "Removes pops and audio crackles automatically",
                                isChecked = config.deClick,
                                isHighContrast = isHighContrast,
                                onCheckedChange = { config = config.copy(deClick = it) },
                                testTag = "toggle_batch_declick"
                            )

                            // 5. Gentle Noise Reduction
                            BatchToggleRow(
                                title = "Gentle Noise Reduction",
                                subtitle = "Soft multi-band cleanup without robotic timbre",
                                isChecked = config.noiseReduction,
                                isHighContrast = isHighContrast,
                                onCheckedChange = { config = config.copy(noiseReduction = it) },
                                testTag = "toggle_batch_noise_reduction"
                            )

                            // 6. Smooth Lead/Tail Fades
                            BatchToggleRow(
                                title = "Smooth Fades (100ms In / 200ms Out)",
                                subtitle = "Prevents clicks at start and end of every file",
                                isChecked = config.fadeInMs > 0,
                                isHighContrast = isHighContrast,
                                onCheckedChange = {
                                    config = config.copy(
                                        fadeInMs = if (it) 100L else 0L,
                                        fadeOutMs = if (it) 200L else 0L
                                    )
                                },
                                testTag = "toggle_batch_fades"
                            )

                            // 7. Output Format Selector
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Output Audio Format:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val formats = listOf("WAV", "MP3", "FLAC", "M4A")
                                    formats.forEach { fmt ->
                                        FilterChip(
                                            selected = config.outputFormat.equals(fmt, ignoreCase = true),
                                            onClick = { config = config.copy(outputFormat = fmt) },
                                            label = { Text(fmt) },
                                            modifier = Modifier.semantics {
                                                contentDescription = "Export batch files as $fmt format"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = Color.Transparent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Progress Indicator during processing
                    AnimatedVisibility(visible = isProcessing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { processingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = processingStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Main Action Button
                    if (!isProcessing && !hasCompleted) {
                        Button(
                            onClick = {
                                if (selectedItems.isEmpty()) {
                                    AccessibilityHelper.announceForAccessibility(
                                        context,
                                        "Please select or add at least one audio file first."
                                    )
                                    return@Button
                                }

                                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                isProcessing = true
                                processingProgress = 0f
                                processingStatusMessage = "Starting batch processing on ${selectedItems.size} files..."

                                processingJob = scope.launch {
                                    val processed = batchProcessor.processBatch(
                                        items = selectedItems.toList(),
                                        config = config,
                                        onProgress = { index, total, itemProg, msg ->
                                            val overall = ((index - 1).toFloat() + itemProg) / total.toFloat()
                                            processingProgress = overall.coerceIn(0f, 1f)
                                            processingStatusMessage = "[$index/$total] $msg"
                                        }
                                    )

                                    selectedItems.clear()
                                    selectedItems.addAll(processed)
                                    isProcessing = false
                                    hasCompleted = true
                                    mediaRepository.refreshExports()

                                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                                    AccessibilityHelper.announceForAccessibility(
                                        context,
                                        "Batch processing finished! ${processed.count { it.status == BatchItemStatus.COMPLETED }} files processed successfully."
                                    )
                                }
                            },
                            enabled = selectedItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_start_batch_processing")
                                .semantics {
                                    contentDescription = "Start batch processing for ${selectedItems.size} selected audio files"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHighContrast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Start Batch Processing (${selectedItems.size} Files)",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                    } else if (isProcessing) {
                        OutlinedButton(
                            onClick = {
                                processingJob?.cancel()
                                isProcessing = false
                                processingStatusMessage = "Batch processing cancelled"
                                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                AccessibilityHelper.announceForAccessibility(context, "Batch processing cancelled")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_cancel_batch_processing")
                        ) {
                            Text("Cancel Processing", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (hasCompleted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    playerManager.stop()
                                    onDismiss()
                                    onNavigateToExports()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_view_batch_exports")
                                    .semantics { contentDescription = "View processed batch files in Exported Files" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("View in Exports", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    hasCompleted = false
                                    selectedItems.clear()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("New Batch")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    isHighContrast: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        )
    }
}
