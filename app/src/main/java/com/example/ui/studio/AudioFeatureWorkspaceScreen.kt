package com.example.ui.studio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioEditSettings
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.ui.components.MixSyncOffsetControl
import com.example.audio.EqPreset
import com.example.audio.ScrubDirection
import com.example.audio.StudioAudioProcessor
import com.example.audio.VocalIsolationPreset
import com.example.audio.VocalIsolationProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibleAudioWaveform
import com.example.ui.components.AccessibleEffectSlider
import com.example.ui.components.AccessibleTimeReadout
import com.example.ui.components.AccessibleTrimSection
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.VocalKaraokeMakerCard
import com.example.ui.screens.AudioStudioScreen
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioFeatureWorkspaceScreen(
    tool: AudioFeatureTool,
    session: AudioProjectSessionState,
    projectSourceUri: Uri? = null,
    audioProcessor: WavAudioProcessor,
    playerManager: AudioPlayerManager,
    recorderManager: AudioRecorderManager,
    mediaRepository: MediaRepository,
    hapticsEnabled: Boolean,
    isHighContrast: Boolean,
    defaultExportFormat: String,
    followCursor: Boolean,
    onToggleHighContrast: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenBatchProcessing: () -> Unit,
    onSaveAndReturn: (AudioProjectSessionState) -> Unit,
    onCancelBack: () -> Unit,
    visualModeEnabled: Boolean = true,
    showZoomControls: Boolean = true,
    showWaveformDisplay: Boolean = true,
    recordingSettings: StudioSettingsState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workingPcm by remember(session.projectId, tool) { mutableStateOf(session.pcm) }
    var workingTitle by remember(session.projectId, tool) { mutableStateOf(session.title) }
    var workingBaseName by remember(session.projectId, tool) { mutableStateOf(session.baseFileName) }
    var trimStartMs by remember(session.projectId, tool) { mutableLongStateOf(session.trimStartMs) }
    var trimEndMs by remember(session.projectId, tool) { mutableLongStateOf(session.trimEndMs) }
    var editSettings by remember(session.projectId, tool) { mutableStateOf(session.editSettings) }
    var selectedEq by remember(session.projectId, tool) { mutableStateOf(session.selectedEq) }
    var localModified by remember(session.projectId, tool) { mutableStateOf(false) }
    var hasPendingTrimRange by remember(session.projectId, tool) {
        val totalMs = ((session.pcm.size.toDouble() / session.sampleRate.coerceAtLeast(8000)) * 1000.0).toLong()
        mutableStateOf(session.trimStartMs > 0L || (session.trimEndMs in 1L until totalMs))
    }

    val undoHistory = remember(session.projectId, tool) { mutableStateListOf<ShortArray>() }
    var isApplyingTransform by remember(session.projectId, tool) { mutableStateOf(false) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val activeScrubDirection by playerManager.scrubDirection.collectAsState()
    var scrubSpeed by remember(recordingSettings?.audioScrubSpeed) {
        mutableFloatStateOf(
            recordingSettings?.audioScrubSpeed ?: StudioPreferences.loadSettings(context).audioScrubSpeed
        )
    }
    LaunchedEffect(scrubSpeed) {
        playerManager.setScrubSpeed(scrubSpeed)
    }
    val sampleRate = session.sampleRate.coerceAtLeast(8000)

    LaunchedEffect(workingPcm) {
        try {
            playerManager.loadPcmData(workingPcm, sampleRate)
            val dur = playerManager.durationMs.value
            if (trimEndMs <= 0L || trimEndMs > dur) trimEndMs = dur
            if (trimStartMs > trimEndMs) trimStartMs = 0L
        } catch (_: Throwable) {}
    }

    fun pushUndoSnapshotSafely(snapshot: ShortArray) {
        try {
            val maxTotalSamples = 40_000_000L
            while (undoHistory.size >= 6 || (undoHistory.isNotEmpty() && undoHistory.sumOf { it.size.toLong() } + snapshot.size.toLong() > maxTotalSamples)) {
                undoHistory.removeAt(0)
            }
            if (snapshot.size.toLong() <= maxTotalSamples) {
                undoHistory.add(snapshot.clone())
            }
        } catch (_: OutOfMemoryError) {
            undoHistory.clear()
        }
    }

    fun pushUndoAndApply(label: String, preserveSelection: Boolean = false, transform: (ShortArray) -> ShortArray) {
        if (isApplyingTransform) {
            Toast.makeText(context, "Processing in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        val current = workingPcm
        if (current.isEmpty()) {
            Toast.makeText(context, "Audio track is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        isApplyingTransform = true
        scope.launch(Dispatchers.Main) {
            try {
                playerManager.stop()
                val updated = withContext(Dispatchers.Default) { transform(current) }
                pushUndoSnapshotSafely(current)
                workingPcm = updated
                localModified = true
                val newDur = ((updated.size.toDouble() / sampleRate) * 1000.0).toLong()
                if (!preserveSelection) {
                    trimStartMs = 0L
                    trimEndMs = newDur
                } else {
                    trimStartMs = trimStartMs.coerceIn(0L, newDur)
                    trimEndMs = trimEndMs.coerceIn(trimStartMs, newDur)
                }
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(context, label)
                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Toast.makeText(
                    context,
                    "Could not apply effect: ${t.localizedMessage ?: "Audio too long for available memory"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isApplyingTransform = false
            }
        }
    }

    fun commitAndSave() {
        try {
            playerManager.stop()
        } catch (_: Throwable) {}
        val totalDurMs = ((workingPcm.size.toDouble() / sampleRate) * 1000.0).toLong()
        val shouldMaterializeTrim =
            hasPendingTrimRange &&
                workingPcm.isNotEmpty() &&
                trimEndMs > trimStartMs &&
                (trimStartMs > 0L || trimEndMs < totalDurMs)

        var finalPcm: ShortArray = workingPcm
        var finalTrimStart: Long = trimStartMs
        var finalTrimEnd: Long = trimEndMs
        var finalModified: Boolean = localModified

        if (shouldMaterializeTrim) {
            try {
                val cropped = StudioAudioProcessor.cropToSelection(workingPcm, trimStartMs, trimEndMs, sampleRate)
                if (cropped.isNotEmpty()) {
                    finalPcm = cropped
                    finalTrimStart = 0L
                    finalTrimEnd = ((cropped.size.toDouble() / sampleRate) * 1000.0).toLong()
                    finalModified = true
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "Trim could not be finalized (${t.localizedMessage ?: "Memory limit"}). Saving full track.", Toast.LENGTH_SHORT).show()
            }
        }

        onSaveAndReturn(
            session.copy(
                title = workingTitle,
                baseFileName = workingBaseName,
                pcm = finalPcm,
                trimStartMs = finalTrimStart,
                trimEndMs = finalTrimEnd,
                editSettings = editSettings.copy(eqPreset = selectedEq),
                selectedEq = selectedEq,
                hasUnsavedChanges = session.hasUnsavedChanges || finalModified
            )
        )
    }

    BackHandler {
        playerManager.stop()
        onCancelBack()
    }

    if (tool == AudioFeatureTool.FULL_DAW_STUDIO) {
        var dawPendingCommand by remember { mutableStateOf<AudioStudioCommand?>(null) }
        Column(modifier = modifier.fillMaxSize()) {
            WorkspaceHeaderBar(
                title = tool.title,
                subtitle = "Complete Workstation Mode",
                canUndo = false,
                onUndo = {},
                onBack = {
                    playerManager.stop()
                    onCancelBack()
                },
                onSave = { commitAndSave() }
            )
            StudioDawTopBar(
                currentTab = com.example.StudioTab.AUDIO,
                isHighContrast = isHighContrast,
                defaultExportFormat = defaultExportFormat,
                followCursor = followCursor,
                onToggleHighContrast = onToggleHighContrast,
                onOpenAbout = onOpenAbout,
                onOpenSettings = onOpenSettings,
                onOpenHelp = onOpenHelp,
                onOpenWhatsNew = onOpenWhatsNew,
                onSelectTab = {},
                onOpenBatchProcessing = onOpenBatchProcessing,
                onAudioCommand = { dawPendingCommand = it }
            )
            AudioStudioScreen(
                mediaRepository = mediaRepository,
                hapticsEnabled = hapticsEnabled,
                defaultExportFormat = defaultExportFormat,
                followCursor = followCursor,
                onNavigateToExports = {},
                pendingCommand = dawPendingCommand,
                onCommandHandled = { dawPendingCommand = null },
                onOpenHelpRequested = onOpenHelp,
                initialPcm = workingPcm,
                initialTitle = workingTitle,
                initialBaseName = workingBaseName,
                initialSourceUri = if (!localModified) projectSourceUri else null,
                initialTrimStartMs = trimStartMs,
                initialTrimEndMs = trimEndMs,
                onSyncToProjectHub = { syncedTitle, syncedBase, syncedPcm, _ ->
                    workingTitle = syncedTitle
                    workingBaseName = syncedBase
                    workingPcm = syncedPcm
                    localModified = true
                    hasPendingTrimRange = false
                },
                onSyncTrimToProjectHub = { syncedStart, syncedEnd ->
                    val durMs = ((workingPcm.size.toDouble() / sampleRate) * 1000.0).toLong()
                    trimStartMs = syncedStart.coerceIn(0L, durMs)
                    trimEndMs = syncedEnd.coerceIn(trimStartMs, durMs)
                    hasPendingTrimRange = trimStartMs > 0L || (trimEndMs in 1L until durMs)
                    if (hasPendingTrimRange) localModified = true
                },
                visualModeEnabled = visualModeEnabled,
                showZoomControls = showZoomControls,
                showWaveformDisplay = showWaveformDisplay,
                recordingSettings = recordingSettings,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WorkspaceHeaderBar(
            title = tool.title,
            subtitle = workingTitle,
            canUndo = undoHistory.isNotEmpty(),
            onUndo = {
                if (undoHistory.isNotEmpty()) {
                    workingPcm = undoHistory.removeAt(undoHistory.lastIndex)
                    AccessibilityHelper.announceForAccessibility(context, "Undid last change")
                }
            },
            onBack = {
                playerManager.stop()
                onCancelBack()
            },
            onSave = { commitAndSave() }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isApplyingTransform) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("progress_workspace_processing"))
            }

            AccessibleTimeReadout(
                currentMs = 0L,
                durationMs = durationMs,
                label = workingTitle,
                isPlaying = isPlaying,
                positionFlow = playerManager.currentPositionMs
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            playerManager.pause()
                        } else if (workingPcm.isNotEmpty()) {
                            playerManager.play()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_workspace_play_pause")
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPlaying) "Pause" else "Play Preview", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        playerManager.stop()
                        playerManager.seekTo(0L)
                    },
                    modifier = Modifier.height(48.dp).testTag("btn_workspace_stop")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }

            // Rewind / Fast-Forward & Scrub Speed Controls for Audio Editing
            Card(
                modifier = Modifier.fillMaxWidth().testTag("card_workspace_scrub_controls"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = if (isHighContrast) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeSpeedLabel = StudioPreferences.formatScrubSpeedLabel(scrubSpeed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isRewActive = activeScrubDirection == ScrubDirection.REWIND
                        val isFwdActive = activeScrubDirection == ScrubDirection.FORWARD
                        OutlinedButton(
                            onClick = {
                                if (workingPcm.isNotEmpty()) {
                                    playerManager.toggleScrub(ScrubDirection.REWIND)
                                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_workspace_rewind_scrub")
                                .semantics {
                                    contentDescription = if (isRewActive) {
                                        "Rewinding at $activeSpeedLabel speed. Press to resume normal playback."
                                    } else {
                                        "Rewind at $activeSpeedLabel speed"
                                    }
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isRewActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(Icons.Default.FastRewind, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRewActive) "Rew $activeSpeedLabel" else "Rewind $activeSpeedLabel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (workingPcm.isNotEmpty()) {
                                    playerManager.toggleScrub(ScrubDirection.FORWARD)
                                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_workspace_forward_scrub")
                                .semantics {
                                    contentDescription = if (isFwdActive) {
                                        "Fast forwarding at $activeSpeedLabel speed. Press to resume normal playback."
                                    } else {
                                        "Fast forward at $activeSpeedLabel speed"
                                    }
                                },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFwdActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isFwdActive) "FF $activeSpeedLabel" else "Forward $activeSpeedLabel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_workspace_scrub_speed_selector"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "REW/FF Speed:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)
                        )
                        StudioPreferences.SUPPORTED_SCRUB_SPEED_OPTIONS.forEach { spd ->
                            val selected = kotlin.math.abs(scrubSpeed - spd) < 0.1f
                            val label = StudioPreferences.formatScrubSpeedLabel(spd)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val normalized = StudioPreferences.normalizeScrubSpeed(spd)
                                    scrubSpeed = normalized
                                    playerManager.setScrubSpeed(normalized)
                                    StudioPreferences.saveAudioScrubSpeed(context, normalized)
                                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                                    AccessibilityHelper.announceForAccessibility(context, "Rewind and fast forward speed $label")
                                },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("chip_workspace_scrub_speed_$label")
                                    .semantics {
                                        stateDescription = if (selected) "Selected" else "Not selected"
                                        contentDescription = "Set rewind and fast forward speed to $label"
                                    },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }

            when (tool) {
                AudioFeatureTool.TRIM_CUT -> {
                    AccessibleAudioWaveform(
                        currentMs = 0L,
                        durationMs = durationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        samples = workingPcm,
                        visualModeEnabled = visualModeEnabled,
                        showWaveformDisplay = showWaveformDisplay,
                        showZoomControls = showZoomControls,
                        onSeek = { playerManager.seekTo(it) },
                        isPlaying = isPlaying,
                        positionFlow = playerManager.currentPositionMs
                    )
                    AccessibleTrimSection(
                        currentMs = 0L,
                        durationMs = durationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        onSetTrimStart = {
                            trimStartMs = it.coerceIn(0L, trimEndMs)
                            hasPendingTrimRange = true
                            localModified = true
                        },
                        onSetTrimEnd = {
                            trimEndMs = it.coerceIn(trimStartMs, durationMs)
                            hasPendingTrimRange = true
                            localModified = true
                        },
                        onFineTuneStart = {
                            trimStartMs = (trimStartMs + it).coerceIn(0L, trimEndMs)
                            hasPendingTrimRange = true
                            localModified = true
                        },
                        onFineTuneEnd = {
                            trimEndMs = (trimEndMs + it).coerceIn(trimStartMs, durationMs)
                            hasPendingTrimRange = true
                            localModified = true
                        },
                        currentPositionMsProvider = { playerManager.currentPositionMs.value },
                        onPreviewTrim = {
                            if (workingPcm.isNotEmpty() && trimEndMs > trimStartMs) {
                                playerManager.stop()
                                playerManager.seekTo(trimStartMs)
                                playerManager.play(startFromMs = trimStartMs, endLimitMs = trimEndMs)
                            }
                        }
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val s = trimStartMs; val e = trimEndMs
                                if (e > s) {
                                    hasPendingTrimRange = false
                                    pushUndoAndApply("Cropped audio to selection") { StudioAudioProcessor.cropToSelection(it, s, e, sampleRate) }
                                }
                            },
                            modifier = Modifier.height(48.dp).testTag("btn_workspace_crop")
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Crop Selection", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val s = trimStartMs; val e = trimEndMs
                                if (e > s) {
                                    hasPendingTrimRange = false
                                    pushUndoAndApply("Muted selected region", preserveSelection = true) { StudioAudioProcessor.muteSelection(it, s, e, sampleRate) }
                                }
                            },
                            modifier = Modifier.height(48.dp).testTag("btn_workspace_mute_selection")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mute Selection", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val s = trimStartMs; val e = trimEndMs
                                if (e > s) {
                                    hasPendingTrimRange = false
                                    pushUndoAndApply("Deleted selected region") { StudioAudioProcessor.deleteSelection(it, s, e) }
                                }
                            },
                            modifier = Modifier.height(48.dp).testTag("btn_workspace_delete_selection")
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Selection", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                trimStartMs = 0L
                                trimEndMs = durationMs
                                hasPendingTrimRange = false
                            },
                            modifier = Modifier.height(48.dp).testTag("btn_workspace_select_all")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select All", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                AudioFeatureTool.MIX_TRACKS -> {
                    var mixOverlayPcm by remember { mutableStateOf<ShortArray?>(null) }
                    var mixOverlayTitle by remember { mutableStateOf<String?>(null) }
                    var mainVol by remember { mutableFloatStateOf(1.0f) }
                    var overlayVol by remember { mutableFloatStateOf(0.85f) }
                    var startOffsetMs by remember { mutableLongStateOf(0L) }
                    var limiterOn by remember { mutableStateOf(true) }
                    var mixPreviewActive by remember { mutableStateOf(false) }

                    val mixPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        if (uri != null) {
                            scope.launch {
                                try {
                                    val decodedRaw = audioProcessor.decodeUriToPcm(uri)
                                    val decoded = if (decodedRaw.isNotEmpty() && sampleRate != 44100) {
                                        StudioAudioProcessor.resamplePcmLinear(decodedRaw, 44100, sampleRate)
                                    } else {
                                        decodedRaw
                                    }
                                    if (decoded.isNotEmpty()) {
                                        mixOverlayPcm = decoded
                                        mixOverlayTitle = audioProcessor.queryAudioDisplayName(uri) ?: "Overlay Track"
                                    } else {
                                        Toast.makeText(context, "Could not decode selected mix file.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    Toast.makeText(context, "Mix load error: ${t.localizedMessage ?: "Unsupported file"}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Mix Second Audio File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            OutlinedButton(
                                onClick = { mixPicker.launch(arrayOf("audio/*")) },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_mix_browse_file")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(mixOverlayTitle ?: "Browse Audio File to Mix", fontWeight = FontWeight.Bold)
                            }
                            AccessibleEffectSlider(
                                title = "Main Track Volume",
                                value = mainVol,
                                onValueChange = { mainVol = it },
                                valueRange = 0.0f..1.5f,
                                step = 0.05f,
                                displayValue = "${(mainVol * 100).toInt()}%",
                                spokenDescription = "Main track volume ${(mainVol * 100).toInt()} percent",
                                testTag = "slider_mix_main_vol"
                            )
                            AccessibleEffectSlider(
                                title = "Mix Track Volume",
                                value = overlayVol,
                                onValueChange = { overlayVol = it },
                                valueRange = 0.0f..1.5f,
                                step = 0.05f,
                                displayValue = "${(overlayVol * 100).toInt()}%",
                                spokenDescription = "Mix track volume ${(overlayVol * 100).toInt()} percent",
                                testTag = "slider_mix_overlay_vol"
                            )
                            MixSyncOffsetControl(
                                offsetMs = startOffsetMs,
                                onOffsetChange = { startOffsetMs = it },
                                baseStartMs = 0L,
                                minOffsetMs = -60_000L,
                                maxOffsetMs = maxOf(60_000L, durationMs)
                            )
                            val maxOffsetSec = (durationMs / 1000f).coerceAtLeast(10f)
                            AccessibleEffectSlider(
                                title = "Fine Start Position Slider",
                                value = (startOffsetMs / 1000f).coerceIn(-30f, maxOffsetSec),
                                onValueChange = { startOffsetMs = kotlin.math.round(it * 1000.0).toLong() },
                                valueRange = -30f..maxOffsetSec,
                                step = 0.05f,
                                displayValue = "${if (startOffsetMs >= 0) "+" else "-"}${"%.2f".format(kotlin.math.abs(startOffsetMs) / 1000f)}s",
                                spokenDescription = "Mix start offset ${"%.2f".format(startOffsetMs / 1000f)} seconds",
                                testTag = "slider_mix_offset"
                            )
                            OutlinedButton(
                                enabled = mixOverlayPcm != null && mixOverlayPcm!!.isNotEmpty(),
                                onClick = {
                                    val overlay = mixOverlayPcm ?: return@OutlinedButton
                                    if (mixPreviewActive && isPlaying) {
                                        playerManager.stop()
                                        playerManager.loadPcmData(workingPcm, sampleRate)
                                        mixPreviewActive = false
                                    } else {
                                        scope.launch {
                                            playerManager.stop()
                                            val previewPcm = withContext(Dispatchers.Default) {
                                                StudioAudioProcessor.mixAudioTracks(
                                                    main = workingPcm,
                                                    overlay = overlay,
                                                    startMs = startOffsetMs,
                                                    mainVolume = mainVol,
                                                    overlayVolume = overlayVol,
                                                    applyPeakLimiter = limiterOn,
                                                    sampleRate = sampleRate
                                                )
                                            }
                                            mixPreviewActive = true
                                            playerManager.loadPcmData(previewPcm, sampleRate)
                                            playerManager.seekTo(if (startOffsetMs > 0L) (startOffsetMs - 500L).coerceAtLeast(0L) else 0L)
                                            playerManager.play()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_workspace_mix_preview")
                            ) {
                                Icon(
                                    imageVector = if (mixPreviewActive && isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (mixPreviewActive && isPlaying) "Stop Mix Preview" else "Preview Mix Sync",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Anti-Clip Peak Limiter", fontWeight = FontWeight.Bold)
                                Switch(checked = limiterOn, onCheckedChange = { limiterOn = it })
                            }
                            Button(
                                onClick = {
                                    val overlay = mixOverlayPcm
                                    if (overlay == null || overlay.isEmpty()) {
                                        Toast.makeText(context, "Select an audio file to mix first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (mixPreviewActive) {
                                            playerManager.stop()
                                            mixPreviewActive = false
                                        }
                                        pushUndoAndApply("Mixed ${mixOverlayTitle ?: "audio"} into track") { pcm ->
                                            StudioAudioProcessor.mixAudioTracks(
                                                main = pcm,
                                                overlay = overlay,
                                                startMs = startOffsetMs,
                                                mainVolume = mainVol,
                                                overlayVolume = overlayVol,
                                                applyPeakLimiter = limiterOn,
                                                sampleRate = sampleRate
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_workspace_apply_mix")
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Apply Mix to Track", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AudioFeatureTool.EFFECTS_CLEANING -> {
                    var noiseStrength by remember { mutableFloatStateOf(editSettings.noiseReductionIntensity) }
                    var echoMix by remember { mutableFloatStateOf(editSettings.echoMix) }
                    var reverbMix by remember { mutableFloatStateOf(editSettings.reverbMix) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Quick Cleaning & Dynamics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { pushUndoAndApply("Applied Auto Gain leveling") { StudioAudioProcessor.applyAutoGain(it) } },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_fx_auto_gain")
                                        .semantics {
                                            contentDescription = "Auto Gain. Automatically levels quiet and loud parts of the audio to an even volume."
                                        }
                                ) { Text("Auto Gain", fontWeight = FontWeight.Black) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Normalized Peak (-0.1 dB)") { StudioAudioProcessor.normalize(it) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_normalize")
                                ) { Text("Normalize Peak", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Removed 60 Hz hum") { StudioAudioProcessor.removeMainsHum(it, false) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_dehum")
                                ) { Text("De-Hum 60Hz", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied Compressor & Limiter") { StudioAudioProcessor.applyCompressor(it, -18f, 4f, 4f, true) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_compressor")
                                ) { Text("Compressor", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Boosted Gain +3 dB") { StudioAudioProcessor.applyGainDb(it, 3f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_gain_up")
                                ) { Text("Gain +3 dB", fontWeight = FontWeight.Bold) }
                            }

                            var selectedIsolationPreset by remember { mutableStateOf(VocalIsolationPreset.MEDIUM) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_workspace_vocal_isolation"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Noise Reduction & Isolate Spoken Audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                    AccessibleEffectSlider(
                                        title = "Noise Reduction Strength",
                                        value = noiseStrength,
                                        onValueChange = { noiseStrength = it },
                                        valueRange = 0f..1f,
                                        step = 0.05f,
                                        displayValue = "${(noiseStrength * 100).toInt()}%",
                                        spokenDescription = "Noise reduction ${(noiseStrength * 100).toInt()} percent",
                                        testTag = "slider_fx_noise"
                                    )
                                    HorizontalDivider()
                                    Text(
                                        text = "Adaptive Spectral Subtraction (Removes hum & ambient noise)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = selectedIsolationPreset.shortDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            VocalIsolationPreset.LIGHT to "btn_isolate_light",
                                            VocalIsolationPreset.MEDIUM to "btn_isolate_medium",
                                            VocalIsolationPreset.STRONG to "btn_isolate_strong"
                                        ).forEach { (preset, tag) ->
                                            FilterChip(
                                                selected = selectedIsolationPreset == preset,
                                                onClick = { selectedIsolationPreset = preset },
                                                label = { Text(preset.displayName, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier
                                                    .testTag(tag)
                                                    .semantics {
                                                        contentDescription = "${preset.displayName}. ${preset.shortDescription}"
                                                    }
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val chosen = selectedIsolationPreset
                                            pushUndoAndApply("Isolated spoken audio (${chosen.displayName})") { pcm ->
                                                VocalIsolationProcessor.isolateSpokenAudio(pcm, chosen, sampleRate)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_apply_isolate_speech")
                                            .semantics {
                                                contentDescription = "Isolate Spoken Audio using ${selectedIsolationPreset.displayName} adaptive spectral subtraction."
                                            }
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Isolate Speech (${selectedIsolationPreset.displayName})", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                            Text("EQ Presets", fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EqPreset.values().forEach { preset ->
                                    FilterChip(
                                        selected = selectedEq == preset,
                                        onClick = { selectedEq = preset; localModified = true },
                                        label = { Text(preset.displayName) }
                                    )
                                }
                            }
                            AccessibleEffectSlider(
                                title = "Echo Mix",
                                value = echoMix,
                                onValueChange = { echoMix = it },
                                valueRange = 0f..0.9f,
                                step = 0.05f,
                                displayValue = "${(echoMix * 100).toInt()}%",
                                spokenDescription = "Echo mix ${(echoMix * 100).toInt()} percent",
                                testTag = "slider_fx_echo"
                            )
                            AccessibleEffectSlider(
                                title = "Reverb Mix",
                                value = reverbMix,
                                onValueChange = { reverbMix = it },
                                valueRange = 0f..0.9f,
                                step = 0.05f,
                                displayValue = "${(reverbMix * 100).toInt()}%",
                                spokenDescription = "Reverb mix ${(reverbMix * 100).toInt()} percent",
                                testTag = "slider_fx_reverb"
                            )
                            Button(
                                onClick = {
                                    val newSettings = editSettings.copy(
                                        eqPreset = selectedEq,
                                        noiseReductionEnabled = noiseStrength > 0.05f,
                                        noiseReductionIntensity = noiseStrength,
                                        echoEnabled = echoMix > 0.05f,
                                        echoMix = echoMix,
                                        reverbEnabled = reverbMix > 0.05f,
                                        reverbMix = reverbMix
                                    )
                                    editSettings = newSettings
                                    pushUndoAndApply("Applied Studio Effects Chain") { pcm ->
                                        audioProcessor.processPcm(pcm, newSettings, sampleRate)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_workspace_apply_fx")
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Apply Effects to Track", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AudioFeatureTool.RECORD_VOICE -> {
                    val isRecording by recorderManager.isRecording.collectAsState()
                    val recDurMs by recorderManager.recordedDurationMs.collectAsState()
                    val recAmp by recorderManager.amplitude.collectAsState()
                    val resolvedRecConfig by recorderManager.resolvedConfig.collectAsState()
                    var autoGain by remember {
                        mutableStateOf(recordingSettings?.recordingAutoGainEnabled ?: recorderManager.autoGainEnabled)
                    }
                    LaunchedEffect(recordingSettings) {
                        if (recordingSettings != null) {
                            recorderManager.applySettings(recordingSettings)
                            autoGain = recordingSettings.recordingAutoGainEnabled
                        }
                    }

                    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            recorderManager.autoGainEnabled = autoGain
                            recordingSettings?.let { recorderManager.applySettings(it.copy(recordingAutoGainEnabled = autoGain)) }
                            recorderManager.startRecording(onSuccess = {}, onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
                        } else {
                            Toast.makeText(context, "Microphone permission required.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Voice Recording Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(
                                text = "Active Profile: ${resolvedRecConfig.summaryLabel()} • Input Gain ${(recorderManager.micGainVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("txt_workspace_rec_profile")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto-Gain & Anti-Crackle", fontWeight = FontWeight.Bold)
                                Switch(checked = autoGain, onCheckedChange = { autoGain = it; recorderManager.autoGainEnabled = it })
                            }
                            if (isRecording) {
                                LinearProgressIndicator(progress = { recAmp }, modifier = Modifier.fillMaxWidth().height(8.dp))
                                Button(
                                    onClick = {
                                        val recorded = recorderManager.stopRecording()
                                        if (recorded != null && recorded.isNotEmpty()) {
                                            val cleaned = StudioAudioProcessor.gentleCleanup(recorded)
                                            pushUndoSnapshotSafely(workingPcm)
                                            workingPcm = cleaned
                                            workingTitle = "Voice Recording (${AccessibilityHelper.formatTimeVisual(recDurMs)})"
                                            localModified = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_workspace_stop_record"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop Recording (${AccessibilityHelper.formatTimeVisual(recDurMs)})", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            recorderManager.autoGainEnabled = autoGain
                                            recorderManager.startRecording(onSuccess = {}, onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
                                        } else {
                                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_workspace_start_record")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start Recording", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AudioFeatureTool.VOCAL_KARAOKE -> {
                    VocalKaraokeMakerCard(
                        currentTrackTitle = workingTitle,
                        currentTrackPcm = workingPcm,
                        sampleRate = sampleRate,
                        initialStereoSourceUri = if (!localModified) projectSourceUri else null,
                        audioProcessor = audioProcessor,
                        playerManager = playerManager,
                        mediaRepository = mediaRepository,
                        defaultExportFormat = defaultExportFormat,
                        hapticsEnabled = hapticsEnabled,
                        onApplySingleStemToTrack = { label, stemPcm ->
                            pushUndoSnapshotSafely(workingPcm)
                            workingPcm = stemPcm
                            localModified = true
                            val newDur = ((stemPcm.size.toDouble() / sampleRate) * 1000.0).toLong()
                            trimStartMs = 0L
                            trimEndMs = newDur
                            hasPendingTrimRange = false
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                            AccessibilityHelper.announceForAccessibility(context, label)
                            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AudioFeatureTool.FULL_DAW_STUDIO -> Unit
            }
        }
    }
}

@Composable
fun WorkspaceHeaderBar(
    title: String,
    subtitle: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_workspace_back")
                    .semantics { contentDescription = "Back to Project Hub without discarding project" }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .testTag("workspace_title")
                        .semantics { heading() }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canUndo) {
                IconButton(
                    onClick = onUndo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_workspace_undo")
                        .semantics { contentDescription = "Undo last change in workspace" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_workspace_save")
                    .semantics { contentDescription = "Save changes and return to Main Project Screen" },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Black)
            }
        }
    }
}
