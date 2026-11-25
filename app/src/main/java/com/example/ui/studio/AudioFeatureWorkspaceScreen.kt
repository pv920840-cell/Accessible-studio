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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.audio.AdaptiveSpectralDenoiser
import com.example.audio.AudioEditSettings
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.ui.components.AdaptiveNoiseReductionPanel
import com.example.ui.components.ChorusEffectCard
import com.example.ui.components.CompressorEffectCard
import com.example.ui.components.DistortionEffectCard
import com.example.ui.components.EchoParams
import com.example.ui.components.EffectPanelPresetsCatalog
import com.example.ui.components.EffectPresetDropdown
import com.example.ui.components.EffectPresetResolver
import com.example.ui.components.Eq7BandEffectCard
import com.example.ui.components.FlangerEffectCard
import com.example.ui.components.MixSyncOffsetControl
import com.example.ui.components.PeakLimiterEffectCard
import com.example.ui.components.PitchPolarityEffectCard
import com.example.ui.components.ReverbParams
import com.example.audio.EqPreset
import com.example.audio.MusicRebalanceProcessor
import com.example.audio.ScrubDirection
import com.example.audio.SpectralDenoiseParams
import com.example.audio.SpectralNoiseProfile
import com.example.audio.StemSeparationMode
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibleAudioPickerDialog
import com.example.ui.components.AccessibleAudioWaveform
import com.example.ui.components.AccessibleEffectSlider
import com.example.ui.components.AccessiblePreviewPlaybackSlider
import com.example.ui.components.AccessibleTimeReadout
import com.example.ui.components.AccessibleTrimSection
import com.example.ui.components.ActiveEffectChipData
import com.example.ui.components.ActiveEffectsLongPressBar
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.MusicRebalancePanel
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.StudioFeatureGuideDialog
import com.example.ui.screens.AudioStudioScreen
import com.example.storage.StorageExportManager
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var showFeatureGuide by remember { mutableStateOf(false) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
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
            onSave = { commitAndSave() },
            onHelp = { showFeatureGuide = true }
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

            AccessiblePreviewPlaybackSlider(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onSeekTo = { playerManager.seekTo(it) },
                isPlaying = isPlaying,
                onTogglePlayPause = {
                    if (isPlaying) {
                        playerManager.pause()
                    } else if (workingPcm.isNotEmpty()) {
                        playerManager.play()
                    }
                },
                onScrubPreviewPulse = { playerManager.scrubPreviewAt(it) },
                testTag = "slider_audio_workspace_preview"
            )

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
                            val selected = kotlin.math.abs(scrubSpeed - spd) < 0.04f
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

                    var showMixAudioPickerDialog by remember { mutableStateOf(false) }
                    val workspaceExportedFiles by mediaRepository.exportedFiles.collectAsState()

                    fun loadMixAudioFromUri(uri: Uri, explicitTitle: String? = null) {
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
                                    mixOverlayTitle = explicitTitle?.takeIf { it.isNotBlank() }
                                        ?: audioProcessor.queryAudioDisplayName(uri)
                                        ?: "Overlay Track"
                                } else {
                                    Toast.makeText(context, "Could not decode selected mix file.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                Toast.makeText(context, "Mix load error: ${t.localizedMessage ?: "Unsupported file"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    if (showMixAudioPickerDialog) {
                        AccessibleAudioPickerDialog(
                            exportedAudios = workspaceExportedFiles,
                            onSelectAudio = { uri, title ->
                                showMixAudioPickerDialog = false
                                loadMixAudioFromUri(uri, title)
                            },
                            onDismiss = { showMixAudioPickerDialog = false }
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Mix Second Audio File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            OutlinedButton(
                                onClick = { showMixAudioPickerDialog = true },
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
                    var denoiseParams by remember(session.projectId, tool) {
                        mutableStateOf(editSettings.effectiveDenoiseParams())
                    }
                    var learnedNoiseProfile by remember(session.projectId, tool) {
                        mutableStateOf<SpectralNoiseProfile?>(null)
                    }
                    var liveDenoiseArmed by remember(session.projectId, tool) { mutableStateOf(true) }
                    var echoEnabled by remember(session.projectId, tool) { mutableStateOf(editSettings.echoEnabled) }
                    var echoDelayMs by remember(session.projectId, tool) { mutableLongStateOf(editSettings.echoDelayMs) }
                    var echoFeedback by remember(session.projectId, tool) { mutableFloatStateOf(editSettings.echoFeedback) }
                    var echoMix by remember(session.projectId, tool) { mutableFloatStateOf(editSettings.echoMix) }
                    var reverbEnabled by remember(session.projectId, tool) { mutableStateOf(editSettings.reverbEnabled) }
                    var reverbRoomSize by remember(session.projectId, tool) { mutableFloatStateOf(editSettings.reverbRoomSize) }
                    var reverbDamping by remember(session.projectId, tool) { mutableFloatStateOf(editSettings.reverbDamping) }
                    var reverbMix by remember(session.projectId, tool) { mutableFloatStateOf(editSettings.reverbMix) }
                    var studioFxSettings by remember(session.projectId, tool) { mutableStateOf(editSettings.studioFx) }

                    LaunchedEffect(
                        echoEnabled, echoDelayMs, echoFeedback, echoMix,
                        reverbEnabled, reverbRoomSize, reverbDamping, reverbMix,
                        studioFxSettings, selectedEq
                    ) {
                        editSettings = editSettings.copy(
                            eqPreset = selectedEq,
                            echoEnabled = echoEnabled,
                            echoDelayMs = echoDelayMs,
                            echoFeedback = echoFeedback,
                            echoMix = echoMix,
                            reverbEnabled = reverbEnabled,
                            reverbRoomSize = reverbRoomSize,
                            reverbDamping = reverbDamping,
                            reverbMix = reverbMix,
                            studioFx = studioFxSettings
                        )
                    }

                    LaunchedEffect(denoiseParams, learnedNoiseProfile, liveDenoiseArmed) {
                        playerManager.setRealtimeDenoiseParams(denoiseParams)
                        playerManager.setRealtimeNoiseProfile(learnedNoiseProfile)
                        playerManager.setRealtimeDenoiseEnabled(liveDenoiseArmed)
                    }
                    DisposableEffect(tool) {
                        onDispose {
                            playerManager.setRealtimeDenoiseEnabled(false)
                        }
                    }

                    var focusedEffectPanel by remember(session.projectId, tool) {
                        mutableStateOf<String?>(null)
                    }

                    // 1. Individual Quick Effects & Cleaning Buttons
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Quick Effects & Cleaning Buttons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
                                    onClick = { pushUndoAndApply("Applied True Peak Limiter (-1 dB)") { StudioAudioProcessor.applyTruePeakLimiter(it, -1.0f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_limiter")
                                ) { Text("Peak Limiter", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Boosted Gain +3 dB") { StudioAudioProcessor.applyGainDb(it, 3f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_gain_up")
                                ) { Text("Gain +3 dB", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Lowered Gain -3 dB") { StudioAudioProcessor.applyGainDb(it, -3f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_gain_down")
                                ) { Text("Gain -3 dB", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied Flanger effect") { StudioAudioProcessor.applyFlanger(it) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_flanger")
                                ) { Text("Flanger", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied Chorus effect") { StudioAudioProcessor.applyChorus(it) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_chorus")
                                ) { Text("Chorus", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied De-Click / De-Crackle") { StudioAudioProcessor.deClickDeCrackle(it) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_declick")
                                ) { Text("De-Click", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Removed DC Offset") { StudioAudioProcessor.removeDcOffset(it) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_dc_offset")
                                ) { Text("DC Offset", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Reversed Audio") { StudioAudioProcessor.reverseAudio(it) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_reverse")
                                ) { Text("Reverse", fontWeight = FontWeight.Bold) }
                            }

                            HorizontalDivider()

                            Text("Effects & Studio Pro Rack Buttons", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    null to "All Effects",
                                    "denoise" to "Adaptive De-Noise",
                                    "eq" to "EQ Presets",
                                    "echo" to "Echo (Delay)",
                                    "reverb" to "Reverb (Acoustic Space)",
                                    "compressor" to "Dynamics Compressor",
                                    "peak_limiter" to "Peak Limiter (Anti-Clip)",
                                    "flanger" to "Flanger",
                                    "chorus" to "Chorus (Multi-Voice)",
                                    "distortion" to "Overdrive & Distortion",
                                    "eq7band" to "7-Band Graphic EQ",
                                    "pitch_polarity" to "Pitch & Polarity"
                                ).forEach { (key, title) ->
                                    val selected = focusedEffectPanel == key
                                    val tag = "btn_fx_tab_${key ?: "all"}"
                                    if (selected) {
                                        Button(
                                            onClick = { focusedEffectPanel = null },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
                                        ) { Text(title, fontWeight = FontWeight.Bold) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { focusedEffectPanel = key },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
                                        ) { Text(title, fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Adaptive De-Noise Card
                    if (focusedEffectPanel == null || focusedEffectPanel == "denoise") {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_fx_denoise"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                AdaptiveNoiseReductionPanel(
                                    params = denoiseParams,
                                    onParamsChange = { next ->
                                        denoiseParams = next
                                        liveDenoiseArmed = true
                                        playerManager.setRealtimeDenoiseParams(next)
                                        playerManager.setRealtimeDenoiseEnabled(true)
                                    },
                                    learnedProfile = learnedNoiseProfile,
                                    isPlayingLivePreview = isPlaying && liveDenoiseArmed,
                                    onToggleLivePreview = {
                                        if (isPlaying) {
                                            playerManager.pause()
                                        } else {
                                            liveDenoiseArmed = true
                                            playerManager.setRealtimeDenoiseParams(denoiseParams)
                                            playerManager.setRealtimeNoiseProfile(learnedNoiseProfile)
                                            playerManager.setRealtimeDenoiseEnabled(true)
                                            playerManager.play(startFromMs = playerManager.currentPositionMs.value, endLimitMs = null)
                                        }
                                    },
                                    onLearnNoiseProfile = {
                                        val s = trimStartMs
                                        val e = trimEndMs
                                        if (e - s < 120L) {
                                            Toast.makeText(context, "Mark a 0.2s–4s fan or noise-only gap using Trim Start/End first.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val snippet = StudioAudioProcessor.cropToSelection(workingPcm, s, e, sampleRate)
                                            val profile = AdaptiveSpectralDenoiser.captureNoiseProfile(snippet, sampleRate, denoiseParams.fftSize)
                                            if (profile != null) {
                                                learnedNoiseProfile = profile
                                                liveDenoiseArmed = true
                                                playerManager.setRealtimeNoiseProfile(profile)
                                                playerManager.setRealtimeDenoiseEnabled(true)
                                                val msg = "Learned noise profile (${"%.1f".format(profile.durationMs / 1000f)}s): ${profile.bandSummary}"
                                                AccessibilityHelper.announceForAccessibility(context, msg)
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onClearNoiseProfile = {
                                        learnedNoiseProfile = null
                                        liveDenoiseArmed = true
                                        playerManager.setRealtimeNoiseProfile(null)
                                        playerManager.setRealtimeDenoiseEnabled(true)
                                        AccessibilityHelper.announceForAccessibility(context, "Switched to Auto Adaptive live noise tracking")
                                    },
                                    onApplyOfflineClean = {
                                        val applied = denoiseParams.copy(bypass = false).clamped()
                                        val profile = learnedNoiseProfile
                                        denoiseParams = applied
                                        liveDenoiseArmed = false
                                        playerManager.setRealtimeDenoiseEnabled(false)
                                        editSettings = editSettings.copy(
                                            noiseReductionEnabled = true,
                                            noiseThresholdDb = applied.thresholdDb,
                                            noiseReductionDb = applied.reductionDb,
                                            noiseSmoothing = applied.smoothing,
                                            noiseVoiceFocus = applied.voiceDialogueFocus,
                                            noiseBypass = false,
                                            noiseFftSize = applied.fftSize
                                        )
                                        pushUndoAndApply("Applied Studio Adaptive De-Noise") { pcm ->
                                            AdaptiveSpectralDenoiser.processOffline(
                                                samples = pcm,
                                                sampleRate = sampleRate,
                                                channels = 1,
                                                params = applied,
                                                noiseProfile = profile
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 3. EQ Presets Separate Card
                    if (focusedEffectPanel == null || focusedEffectPanel == "eq") {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_fx_eq"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("EQ Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EqPreset.values().forEach { preset ->
                                        val isEqSelected = selectedEq == preset
                                        val tag = "btn_fx_eq_${preset.name.lowercase()}"
                                        if (isEqSelected) {
                                            Button(
                                                onClick = { selectedEq = preset; localModified = true },
                                                modifier = Modifier.height(48.dp).testTag(tag)
                                            ) { Text(preset.displayName, fontWeight = FontWeight.Bold) }
                                        } else {
                                            OutlinedButton(
                                                onClick = { selectedEq = preset; localModified = true },
                                                modifier = Modifier.height(48.dp).testTag(tag)
                                            ) { Text(preset.displayName, fontWeight = FontWeight.SemiBold) }
                                        }
                                    }
                                }
                                Button(
                                    onClick = {
                                        val eqOnlySettings = AudioEditSettings(eqPreset = selectedEq)
                                        editSettings = editSettings.copy(eqPreset = selectedEq)
                                        pushUndoAndApply("Applied EQ: ${selectedEq.displayName}") { pcm ->
                                            audioProcessor.processPcm(pcm, eqOnlySettings, sampleRate)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_eq_only")
                                ) {
                                    Text("Apply Selected EQ (${selectedEq.displayName})", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 4. Echo (Delay) Separate Card
                    if (focusedEffectPanel == null || focusedEffectPanel == "echo") {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_fx_echo"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Echo (Delay)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                EffectPresetDropdown(
                                    effectId = "echo",
                                    effectTitle = "Echo",
                                    currentValues = EchoParams(echoDelayMs, echoFeedback, echoMix),
                                    presets = EffectPanelPresetsCatalog.echoPresets,
                                    defaultOption = EffectPanelPresetsCatalog.echoDefault,
                                    valuesEqual = { a, b ->
                                        a.delayMs == b.delayMs &&
                                            EffectPresetResolver.approxEqual(a.feedback, b.feedback) &&
                                            EffectPresetResolver.approxEqual(a.mix, b.mix)
                                    },
                                    onApplyPreset = { preset ->
                                        echoEnabled = true
                                        echoDelayMs = preset.delayMs
                                        echoFeedback = preset.feedback
                                        echoMix = preset.mix
                                    }
                                )
                                AccessibleEffectSlider(
                                    title = "Echo Delay Time",
                                    value = echoDelayMs.toFloat(),
                                    onValueChange = { echoEnabled = true; echoDelayMs = it.toLong() },
                                    valueRange = 50f..800f,
                                    step = 25f,
                                    displayValue = "$echoDelayMs ms",
                                    spokenDescription = "$echoDelayMs milliseconds echo delay",
                                    testTag = "slider_fx_echo_delay"
                                )
                                AccessibleEffectSlider(
                                    title = "Echo Feedback Repeat",
                                    value = echoFeedback,
                                    onValueChange = { echoEnabled = true; echoFeedback = it },
                                    valueRange = 0.1f..0.85f,
                                    step = 0.05f,
                                    displayValue = "${(echoFeedback * 100).toInt()}%",
                                    spokenDescription = "${(echoFeedback * 100).toInt()} percent feedback",
                                    testTag = "slider_fx_echo_feedback"
                                )
                                AccessibleEffectSlider(
                                    title = "Echo Mix",
                                    value = echoMix,
                                    onValueChange = { echoEnabled = it > 0.05f; echoMix = it },
                                    valueRange = 0f..0.9f,
                                    step = 0.05f,
                                    displayValue = "${(echoMix * 100).toInt()}%",
                                    spokenDescription = "Echo mix ${(echoMix * 100).toInt()} percent",
                                    testTag = "slider_fx_echo"
                                )
                                Button(
                                    onClick = {
                                        val echoAmt = echoMix.coerceAtLeast(0.15f)
                                        echoMix = echoAmt
                                        echoEnabled = true
                                        editSettings = editSettings.copy(
                                            echoEnabled = true,
                                            echoDelayMs = echoDelayMs,
                                            echoFeedback = echoFeedback,
                                            echoMix = echoAmt
                                        )
                                        pushUndoAndApply("Applied Echo (${(echoAmt * 100).toInt()}%)") { pcm ->
                                            audioProcessor.processPcm(
                                                pcm,
                                                AudioEditSettings(
                                                    echoEnabled = true,
                                                    echoDelayMs = echoDelayMs,
                                                    echoFeedback = echoFeedback,
                                                    echoMix = echoAmt
                                                ),
                                                sampleRate
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_echo_only")
                                ) {
                                    Text("Apply Echo (${(echoMix * 100).toInt()}%)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 5. Reverb (Acoustic Space) Separate Card
                    if (focusedEffectPanel == null || focusedEffectPanel == "reverb") {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_fx_reverb"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Reverb (Acoustic Space)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                EffectPresetDropdown(
                                    effectId = "reverb",
                                    effectTitle = "Reverb",
                                    currentValues = ReverbParams(reverbRoomSize, reverbDamping, reverbMix),
                                    presets = EffectPanelPresetsCatalog.reverbPresets,
                                    defaultOption = EffectPanelPresetsCatalog.reverbDefault,
                                    valuesEqual = { a, b ->
                                        EffectPresetResolver.approxEqual(a.roomSize, b.roomSize) &&
                                            EffectPresetResolver.approxEqual(a.damping, b.damping) &&
                                            EffectPresetResolver.approxEqual(a.mix, b.mix)
                                    },
                                    onApplyPreset = { preset ->
                                        reverbEnabled = true
                                        reverbRoomSize = preset.roomSize
                                        reverbDamping = preset.damping
                                        reverbMix = preset.mix
                                    }
                                )
                                AccessibleEffectSlider(
                                    title = "Room Size / Space",
                                    value = reverbRoomSize,
                                    onValueChange = { reverbEnabled = true; reverbRoomSize = it },
                                    valueRange = 0.1f..0.95f,
                                    step = 0.05f,
                                    displayValue = "${(reverbRoomSize * 100).toInt()}%",
                                    spokenDescription = "${(reverbRoomSize * 100).toInt()} percent room size",
                                    testTag = "slider_fx_reverb_room"
                                )
                                AccessibleEffectSlider(
                                    title = "Reverb Damping",
                                    value = reverbDamping,
                                    onValueChange = { reverbEnabled = true; reverbDamping = it },
                                    valueRange = 0.1f..0.8f,
                                    step = 0.05f,
                                    displayValue = "${(reverbDamping * 100).toInt()}%",
                                    spokenDescription = "${(reverbDamping * 100).toInt()} percent damping",
                                    testTag = "slider_fx_reverb_damping"
                                )
                                AccessibleEffectSlider(
                                    title = "Reverb Mix",
                                    value = reverbMix,
                                    onValueChange = { reverbEnabled = it > 0.05f; reverbMix = it },
                                    valueRange = 0f..0.9f,
                                    step = 0.05f,
                                    displayValue = "${(reverbMix * 100).toInt()}%",
                                    spokenDescription = "Reverb mix ${(reverbMix * 100).toInt()} percent",
                                    testTag = "slider_fx_reverb"
                                )
                                Button(
                                    onClick = {
                                        val revAmt = reverbMix.coerceAtLeast(0.15f)
                                        reverbMix = revAmt
                                        reverbEnabled = true
                                        editSettings = editSettings.copy(
                                            reverbEnabled = true,
                                            reverbRoomSize = reverbRoomSize,
                                            reverbDamping = reverbDamping,
                                            reverbMix = revAmt
                                        )
                                        pushUndoAndApply("Applied Reverb (${(revAmt * 100).toInt()}%)") { pcm ->
                                            audioProcessor.processPcm(
                                                pcm,
                                                AudioEditSettings(
                                                    reverbEnabled = true,
                                                    reverbRoomSize = reverbRoomSize,
                                                    reverbDamping = reverbDamping,
                                                    reverbMix = revAmt
                                                ),
                                                sampleRate
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_reverb_only")
                                ) {
                                    Text("Apply Reverb (${(reverbMix * 100).toInt()}%)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 6. Individual Studio Pro Rack Cards (Focused by individual buttons)
                    when (focusedEffectPanel) {
                        "compressor" -> CompressorEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            alwaysExpanded = true,
                            onApplySingleEffect = {
                                val nextFx = studioFxSettings.copy(compressorEnabled = true)
                                studioFxSettings = nextFx
                                editSettings = editSettings.copy(studioFx = nextFx)
                                val isolated = StudioFxSettings(
                                    compressorEnabled = true,
                                    compressorThresholdDb = nextFx.compressorThresholdDb,
                                    compressorRatio = nextFx.compressorRatio,
                                    compressorMakeupGainDb = nextFx.compressorMakeupGainDb
                                )
                                pushUndoAndApply("Applied Dynamics Compressor") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                        "peak_limiter" -> PeakLimiterEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            alwaysExpanded = true,
                            onApplySingleEffect = {
                                val nextFx = studioFxSettings.copy(peakLimiterEnabled = true)
                                studioFxSettings = nextFx
                                editSettings = editSettings.copy(studioFx = nextFx)
                                val isolated = StudioFxSettings(
                                    peakLimiterEnabled = true,
                                    peakLimiterCeilingDb = nextFx.peakLimiterCeilingDb
                                )
                                pushUndoAndApply("Applied Peak Limiter (${"%.1f".format(nextFx.peakLimiterCeilingDb)} dBFS)") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                        "flanger" -> FlangerEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            alwaysExpanded = true,
                            onApplySingleEffect = {
                                val nextFx = studioFxSettings.copy(flangerEnabled = true)
                                studioFxSettings = nextFx
                                editSettings = editSettings.copy(studioFx = nextFx)
                                val isolated = StudioFxSettings(
                                    flangerEnabled = true,
                                    flangerRateHz = nextFx.flangerRateHz,
                                    flangerDepthMs = nextFx.flangerDepthMs,
                                    flangerFeedback = nextFx.flangerFeedback,
                                    flangerMix = nextFx.flangerMix
                                )
                                pushUndoAndApply("Applied Flanger") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                        "chorus" -> ChorusEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            alwaysExpanded = true,
                            onApplySingleEffect = {
                                val nextFx = studioFxSettings.copy(chorusEnabled = true)
                                studioFxSettings = nextFx
                                editSettings = editSettings.copy(studioFx = nextFx)
                                val isolated = StudioFxSettings(
                                    chorusEnabled = true,
                                    chorusRateHz = nextFx.chorusRateHz,
                                    chorusDepthMs = nextFx.chorusDepthMs,
                                    chorusMix = nextFx.chorusMix
                                )
                                pushUndoAndApply("Applied Chorus (Multi-Voice)") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                        "distortion" -> DistortionEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            alwaysExpanded = true,
                            onApplySingleEffect = {
                                val nextFx = studioFxSettings.copy(distortionEnabled = true)
                                studioFxSettings = nextFx
                                editSettings = editSettings.copy(studioFx = nextFx)
                                val isolated = StudioFxSettings(
                                    distortionEnabled = true,
                                    distortionDrive = nextFx.distortionDrive,
                                    distortionTone = nextFx.distortionTone
                                )
                                pushUndoAndApply("Applied Overdrive & Distortion") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                        "eq7band" -> Eq7BandEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            alwaysExpanded = true,
                            onApplySingleEffect = {
                                val nextFx = studioFxSettings.copy(eq7BandEnabled = true)
                                studioFxSettings = nextFx
                                editSettings = editSettings.copy(studioFx = nextFx)
                                val isolated = StudioFxSettings(
                                    eq7BandEnabled = true,
                                    eqBand60Hz = nextFx.eqBand60Hz,
                                    eqBand150Hz = nextFx.eqBand150Hz,
                                    eqBand400Hz = nextFx.eqBand400Hz,
                                    eqBand1kHz = nextFx.eqBand1kHz,
                                    eqBand2_5kHz = nextFx.eqBand2_5kHz,
                                    eqBand6kHz = nextFx.eqBand6kHz,
                                    eqBand15kHz = nextFx.eqBand15kHz
                                )
                                pushUndoAndApply("Applied 7-Band Graphic EQ") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                        "pitch_polarity" -> PitchPolarityEffectCard(
                            settings = studioFxSettings,
                            onSettingsChanged = { studioFxSettings = it; localModified = true },
                            onApplySingleEffect = {
                                editSettings = editSettings.copy(studioFx = studioFxSettings)
                                val isolated = StudioFxSettings(
                                    pitchShiftSemitones = studioFxSettings.pitchShiftSemitones,
                                    invertPhase = studioFxSettings.invertPhase
                                )
                                pushUndoAndApply("Applied Pitch Shifter & Polarity") { pcm ->
                                    StudioAudioProcessor.applyStudioRack(pcm, isolated)
                                }
                            }
                        )
                    }

                    val activeAudioEffects = buildList {
                        if (selectedEq != EqPreset.NORMAL) {
                            add(ActiveEffectChipData("eq", "EQ: ${selectedEq.name}"))
                        }
                        if (denoiseParams.reductionDb >= 0.5f && liveDenoiseArmed) {
                            add(ActiveEffectChipData("denoise", "De-Noise", "${denoiseParams.reductionDb.toInt()} dB"))
                        }
                        if (echoEnabled || echoMix > 0.05f) {
                            add(ActiveEffectChipData("echo", "Echo", "${echoDelayMs}ms"))
                        }
                        if (reverbEnabled || reverbMix > 0.05f) {
                            add(ActiveEffectChipData("reverb", "Reverb", "${(reverbMix * 100).toInt()}%"))
                        }
                        if (studioFxSettings.compressorEnabled) {
                            add(ActiveEffectChipData("compressor", "Compressor"))
                        }
                        if (studioFxSettings.peakLimiterEnabled) {
                            add(ActiveEffectChipData("peak_limiter", "Peak Limiter"))
                        }
                        if (studioFxSettings.flangerEnabled) {
                            add(ActiveEffectChipData("flanger", "Flanger"))
                        }
                        if (studioFxSettings.chorusEnabled) {
                            add(ActiveEffectChipData("chorus", "Chorus"))
                        }
                        if (studioFxSettings.distortionEnabled) {
                            add(ActiveEffectChipData("distortion", "Overdrive"))
                        }
                        if (studioFxSettings.eq7BandEnabled) {
                            add(ActiveEffectChipData("eq7band", "7-Band EQ"))
                        }
                        if (studioFxSettings.pitchShiftSemitones != 0 || studioFxSettings.invertPhase) {
                            add(ActiveEffectChipData("pitch_polarity", "Pitch/Polarity"))
                        }
                    }

                    ActiveEffectsLongPressBar(
                        effects = activeAudioEffects,
                        onRemoveEffect = { chip ->
                            when (chip.id) {
                                "eq" -> selectedEq = EqPreset.NORMAL
                                "denoise" -> {
                                    liveDenoiseArmed = false
                                    playerManager.setRealtimeDenoiseEnabled(false)
                                    denoiseParams = denoiseParams.copy(reductionDb = 0f, bypass = true)
                                }
                                "echo" -> {
                                    echoEnabled = false
                                    echoMix = 0f
                                }
                                "reverb" -> {
                                    reverbEnabled = false
                                    reverbMix = 0f
                                }
                                "compressor" -> studioFxSettings = studioFxSettings.copy(compressorEnabled = false)
                                "peak_limiter" -> studioFxSettings = studioFxSettings.copy(peakLimiterEnabled = false)
                                "flanger" -> studioFxSettings = studioFxSettings.copy(flangerEnabled = false)
                                "chorus" -> studioFxSettings = studioFxSettings.copy(chorusEnabled = false)
                                "distortion" -> studioFxSettings = studioFxSettings.copy(distortionEnabled = false)
                                "eq7band" -> studioFxSettings = studioFxSettings.copy(eq7BandEnabled = false)
                                "pitch_polarity" -> studioFxSettings = studioFxSettings.copy(pitchShiftSemitones = 0, invertPhase = false)
                            }
                            localModified = true
                            AccessibilityHelper.announceForAccessibility(context, "Removed ${chip.label}")
                        }
                    )

                    // Audition Preview (non-destructive audition using current settings)
                    OutlinedButton(
                        onClick = {
                            if (workingPcm.isEmpty()) {
                                Toast.makeText(context, "Track is empty.", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            liveDenoiseArmed = false
                            playerManager.setRealtimeDenoiseEnabled(false)
                            val previewSettings = editSettings.copy(
                                eqPreset = selectedEq,
                                echoEnabled = echoEnabled || echoMix > 0.05f,
                                echoDelayMs = echoDelayMs,
                                echoFeedback = echoFeedback,
                                echoMix = echoMix,
                                reverbEnabled = reverbEnabled || reverbMix > 0.05f,
                                reverbRoomSize = reverbRoomSize,
                                reverbDamping = reverbDamping,
                                reverbMix = reverbMix,
                                studioFx = studioFxSettings
                            )
                            scope.launch {
                                try {
                                    playerManager.stop()
                                    val preview = withContext(Dispatchers.Default) {
                                        audioProcessor.processPcm(workingPcm, previewSettings, sampleRate)
                                    }
                                    playerManager.loadPcmData(preview, sampleRate)
                                    playerManager.play()
                                    AccessibilityHelper.announceForAccessibility(context, "Playing effects audition preview.")
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_workspace_audition_fx")
                    ) {
                        Icon(Icons.Default.Hearing, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Audition Effects Preview", fontWeight = FontWeight.Bold)
                    }

                    if (focusedEffectPanel == null) {
                        Button(
                            onClick = {
                                val activeDenoise = denoiseParams.copy(bypass = false).clamped()
                                val profile = learnedNoiseProfile
                                liveDenoiseArmed = false
                                playerManager.setRealtimeDenoiseEnabled(false)
                                val newSettings = editSettings.copy(
                                    eqPreset = selectedEq,
                                    noiseReductionEnabled = activeDenoise.reductionDb >= 0.5f,
                                    noiseThresholdDb = activeDenoise.thresholdDb,
                                    noiseReductionDb = activeDenoise.reductionDb,
                                    noiseSmoothing = activeDenoise.smoothing,
                                    noiseVoiceFocus = activeDenoise.voiceDialogueFocus,
                                    noiseBypass = false,
                                    noiseFftSize = activeDenoise.fftSize,
                                    echoEnabled = echoEnabled || echoMix > 0.05f,
                                    echoDelayMs = echoDelayMs,
                                    echoFeedback = echoFeedback,
                                    echoMix = echoMix,
                                    reverbEnabled = reverbEnabled || reverbMix > 0.05f,
                                    reverbRoomSize = reverbRoomSize,
                                    reverbDamping = reverbDamping,
                                    reverbMix = reverbMix,
                                    studioFx = studioFxSettings
                                )
                                editSettings = newSettings
                                pushUndoAndApply("Applied Studio Effects Chain") { pcm ->
                                    val nrDone = if (newSettings.noiseReductionEnabled) {
                                        AdaptiveSpectralDenoiser.processOffline(
                                            samples = pcm,
                                            sampleRate = sampleRate,
                                            channels = 1,
                                            params = activeDenoise,
                                            noiseProfile = profile
                                        )
                                    } else {
                                        pcm
                                    }
                                    audioProcessor.processPcm(nrDone, newSettings.copy(noiseReductionEnabled = false), sampleRate)
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

                AudioFeatureTool.MUSIC_REBALANCE -> {
                    var selectedStemMode by remember(session.projectId) { mutableStateOf(StemSeparationMode.FOUR_STEMS) }
                    var stemFiles by remember(session.projectId) { mutableStateOf<Map<String, File>>(emptyMap()) }
                    var stemProgress by remember { mutableFloatStateOf(0f) }

                    MusicRebalancePanel(
                        hasAudioLoaded = workingPcm.isNotEmpty(),
                        isProcessing = isApplyingTransform,
                        progress = stemProgress,
                        selectedStemMode = selectedStemMode,
                        onSelectStemMode = { selectedStemMode = it },
                        separatedStemNames = stemFiles.keys.toList(),
                        onPreviewRebalance = { vGain, bGain, pGain, oGain ->
                            if (workingPcm.isNotEmpty() && !isApplyingTransform) {
                                isApplyingTransform = true
                                stemProgress = 0.05f
                                scope.launch {
                                    try {
                                        playerManager.stop()
                                        val preview = withContext(Dispatchers.Default) {
                                            MusicRebalanceProcessor.rebalanceMix(
                                                samples = workingPcm,
                                                sampleRate = sampleRate,
                                                vocalsGain = vGain,
                                                bassGain = bGain,
                                                percussionGain = pGain,
                                                otherGain = oGain,
                                                isCancelled = { !isActive }
                                            ) { stemProgress = it }
                                        }
                                        playerManager.loadPcmData(preview, sampleRate)
                                        playerManager.play()
                                    } catch (t: Throwable) {
                                        if (t is CancellationException) throw t
                                        Toast.makeText(context, "Rebalance preview failed: ${t.localizedMessage ?: "Error"}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isApplyingTransform = false
                                    }
                                }
                            }
                        },
                        onApplyRebalance = { vGain, bGain, pGain, oGain ->
                            pushUndoAndApply("Applied Music Rebalance mix") { pcm ->
                                MusicRebalanceProcessor.rebalanceMix(pcm, sampleRate, vGain, bGain, pGain, oGain)
                            }
                        },
                        onSeparateStems = { mode ->
                            if (workingPcm.isNotEmpty() && !isApplyingTransform) {
                                isApplyingTransform = true
                                stemProgress = 0.02f
                                scope.launch {
                                    try {
                                        val stemsDir = File(context.cacheDir, "stems/${session.projectId}")
                                        val base = workingBaseName?.takeIf { it.isNotBlank() } ?: "Track"
                                        val stems = withContext(Dispatchers.Default) {
                                            stemsDir.listFiles()?.forEach { try { it.delete() } catch (_: Throwable) {} }
                                            MusicRebalanceProcessor.separateStemsStreamingToFiles(
                                                samples = workingPcm,
                                                sampleRate = sampleRate,
                                                mode = mode,
                                                outputDir = stemsDir,
                                                baseName = base,
                                                isCancelled = { !isActive }
                                            ) { stemProgress = it }
                                        }
                                        stemFiles = stems
                                        AccessibilityHelper.announceForAccessibility(context, "Separated ${stems.size} stems")
                                        Toast.makeText(context, "Separated ${stems.size} stems ready to play or save", Toast.LENGTH_SHORT).show()
                                    } catch (t: Throwable) {
                                        if (t is CancellationException) throw t
                                        Toast.makeText(context, "Stem separation failed: ${t.localizedMessage ?: "Error"}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isApplyingTransform = false
                                    }
                                }
                            }
                        },
                        onAuditionStem = { stemName ->
                            val stemFile = stemFiles[stemName]
                            if (stemFile != null && stemFile.exists()) {
                                playerManager.stop()
                                playerManager.loadUri(Uri.fromFile(stemFile))
                                playerManager.play()
                                AccessibilityHelper.announceForAccessibility(context, "Playing $stemName stem")
                            }
                        },
                        onExportStems = { mode ->
                            if (stemFiles.isNotEmpty() && !isApplyingTransform) {
                                isApplyingTransform = true
                                stemProgress = 0.05f
                                scope.launch {
                                    try {
                                        val base = workingBaseName?.takeIf { it.isNotBlank() } ?: "Stems"
                                        withContext(Dispatchers.IO) {
                                            var idx = 0
                                            stemFiles.forEach { (stemName, stemFile) ->
                                                idx++
                                                val safeStem = stemName.replace(" ", "_")
                                                val targetName = "${base}_${safeStem}_${mode.stemCount}stems.wav"
                                                StorageExportManager.saveAudioFile(
                                                    context = context,
                                                    sourceFile = stemFile,
                                                    targetFileName = targetName,
                                                    mimeType = "audio/wav"
                                                )
                                                stemProgress = idx.toFloat() / stemFiles.size.coerceAtLeast(1)
                                            }
                                        }
                                        mediaRepository.refreshExports()
                                        Toast.makeText(context, "Saved ${stemFiles.size} separated stem files", Toast.LENGTH_LONG).show()
                                    } catch (t: Throwable) {
                                        if (t is CancellationException) throw t
                                        Toast.makeText(context, "Stem export error: ${t.localizedMessage ?: "Failed"}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isApplyingTransform = false
                                    }
                                }
                            }
                        },
                        onOpenGuide = { showFeatureGuide = true }
                    )
                }

                AudioFeatureTool.FULL_DAW_STUDIO -> Unit
            }
        }
    }

    if (showFeatureGuide) {
        StudioFeatureGuideDialog(onDismiss = { showFeatureGuide = false })
    }
}

@Composable
fun WorkspaceHeaderBar(
    title: String,
    subtitle: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onHelp: (() -> Unit)? = null
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

            if (onHelp != null) {
                IconButton(
                    onClick = onHelp,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_workspace_guide")
                        .semantics { contentDescription = "Open Feature Guide and Studio Quality Help" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                }
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
