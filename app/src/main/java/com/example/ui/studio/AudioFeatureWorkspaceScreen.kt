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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.audio.AudioEffectsProEngine
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.CuePoint
import com.example.ui.components.AccessibleProSuiteSection
import com.example.ui.components.AdaptiveNoiseReductionPanel
import com.example.ui.components.AudioControlDeckCard
import com.example.ui.components.AudioVisualMode
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
import com.example.ui.components.FeatureGuideButton
import com.example.ui.components.FeatureGuideCatalog
import com.example.ui.components.FeatureGuideTopic
import com.example.ui.components.MusicRebalancePanel
import com.example.ui.components.SingleFeatureGuideDialog
import com.example.ui.components.StudioAudioStatsDialog
import com.example.ui.components.StudioCuePointsDialog
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.StudioGainDialog
import com.example.ui.components.StudioInsertSilenceDialog
import com.example.ui.components.StudioToneGeneratorDialog
import com.example.ui.components.VoiceOverSourceChoice
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

    var clipboardPcm by remember(session.projectId) { mutableStateOf(session.clipboardPcm) }
    var cuePoints by remember(session.projectId) { mutableStateOf(session.cuePoints) }
    var isLoopSelection by remember(session.projectId, tool) { mutableStateOf(false) }
    var showInsertSilenceDialog by remember { mutableStateOf(false) }
    var showToneGeneratorDialog by remember { mutableStateOf(false) }
    var showCuePointsDialog by remember { mutableStateOf(false) }
    var showGainDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var currentAudioStats by remember { mutableStateOf<StudioAudioProcessor.AudioStats?>(null) }
    var showSpeedDeck by remember(session.projectId, tool) { mutableStateOf(false) }
    var visualMode by remember(session.projectId) { mutableStateOf(AudioVisualMode.WAVEFORM) }

    val undoHistory = remember(session.projectId, tool) { mutableStateListOf<ShortArray>() }
    val redoHistory = remember(session.projectId, tool) { mutableStateListOf<ShortArray>() }
    var isApplyingTransform by remember(session.projectId, tool) { mutableStateOf(false) }
    var activeWorkspaceGuideTopic by remember { mutableStateOf<FeatureGuideTopic?>(null) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
    val activeScrubDirection by playerManager.scrubDirection.collectAsState()
    val livePlaybackRate by playerManager.playbackRate.collectAsState()
    val liveVolumeLevel by playerManager.volumeLevel.collectAsState()
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

    fun pushSnapshotTo(targetList: MutableList<ShortArray>, snapshot: ShortArray) {
        try {
            val maxTotalSamples = 40_000_000L
            while (targetList.size >= 6 || (targetList.isNotEmpty() && targetList.sumOf { it.size.toLong() } + snapshot.size.toLong() > maxTotalSamples)) {
                targetList.removeAt(0)
            }
            if (snapshot.size.toLong() <= maxTotalSamples) {
                targetList.add(snapshot.clone())
            }
        } catch (_: OutOfMemoryError) {
            targetList.clear()
        }
    }

    fun pushUndoSnapshotSafely(snapshot: ShortArray) {
        pushSnapshotTo(undoHistory, snapshot)
        redoHistory.clear()
    }

    fun pushUndoAndApply(
        label: String,
        preserveSelection: Boolean = false,
        allowEmptyInput: Boolean = false,
        transform: (ShortArray) -> ShortArray
    ) {
        if (isApplyingTransform) {
            Toast.makeText(context, "Processing in progress, please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        val current = workingPcm
        if (!allowEmptyInput && current.isEmpty()) {
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

    fun playSelectionRange(loopSegment: Boolean) {
        if (workingPcm.isEmpty()) {
            Toast.makeText(context, "Audio track is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val safeDur = durationMs.coerceAtLeast(1L)
        val start = trimStartMs.coerceIn(0L, safeDur)
        val rawEnd = trimEndMs.coerceIn(start, safeDur)
        val end = if (rawEnd > start) rawEnd else safeDur
        playerManager.stop()
        playerManager.seekTo(start)
        playerManager.play(startFromMs = start, endLimitMs = end, loopSegment = loopSegment)
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
                clipboardPcm = clipboardPcm,
                cuePoints = cuePoints,
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
                    pushSnapshotTo(redoHistory, workingPcm)
                    workingPcm = undoHistory.removeAt(undoHistory.lastIndex)
                    localModified = true
                    AccessibilityHelper.announceForAccessibility(context, "Undid last change")
                }
            },
            canRedo = redoHistory.isNotEmpty(),
            onRedo = {
                if (redoHistory.isNotEmpty()) {
                    pushSnapshotTo(undoHistory, workingPcm)
                    workingPcm = redoHistory.removeAt(redoHistory.lastIndex)
                    localModified = true
                    AccessibilityHelper.announceForAccessibility(context, "Redid change")
                }
            },
            onBack = {
                playerManager.stop()
                onCancelBack()
            },
            onSave = { commitAndSave() },
            onHelp = { activeWorkspaceGuideTopic = FeatureGuideCatalog.forAudioTool(tool) }
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

            val activeSpeedLabel = StudioPreferences.formatScrubSpeedLabel(scrubSpeed)
            val isRewActive = activeScrubDirection == ScrubDirection.REWIND
            val isFwdActive = activeScrubDirection == ScrubDirection.FORWARD

            @Composable
            fun TransportRewindButton(buttonModifier: Modifier) {
                OutlinedButton(
                    onClick = {
                        if (workingPcm.isNotEmpty()) {
                            playerManager.toggleScrub(ScrubDirection.REWIND)
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                        }
                    },
                    modifier = buttonModifier
                        .heightIn(min = 48.dp)
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
                    Text(
                        text = if (isRewActive) "Rew $activeSpeedLabel" else "Rewind $activeSpeedLabel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            @Composable
            fun TransportPlayPauseButton(buttonModifier: Modifier) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            playerManager.pause()
                        } else if (workingPcm.isNotEmpty()) {
                            if (isLoopSelection && trimEndMs > trimStartMs) {
                                playSelectionRange(loopSegment = true)
                            } else {
                                playerManager.play()
                            }
                        }
                    },
                    modifier = buttonModifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_workspace_play_pause")
                        .semantics {
                            contentDescription = if (isPlaying) "Pause audio" else "Play audio"
                        }
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPlaying) "Pause" else "Play", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }

            @Composable
            fun TransportForwardButton(buttonModifier: Modifier) {
                OutlinedButton(
                    onClick = {
                        if (workingPcm.isNotEmpty()) {
                            playerManager.toggleScrub(ScrubDirection.FORWARD)
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                        }
                    },
                    modifier = buttonModifier
                        .heightIn(min = 48.dp)
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
                    Text(
                        text = if (isFwdActive) "FF $activeSpeedLabel" else "Forward $activeSpeedLabel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            @Composable
            fun TransportStopButton(buttonModifier: Modifier) {
                OutlinedButton(
                    onClick = {
                        playerManager.stop()
                        playerManager.seekTo(0L)
                    },
                    modifier = buttonModifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_workspace_stop")
                        .semantics {
                            contentDescription = "Stop audio playback"
                        }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("row_workspace_transport_controls")
            ) {
                if (maxWidth >= 480.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransportRewindButton(Modifier.weight(1f))
                        TransportPlayPauseButton(Modifier.weight(1f))
                        TransportForwardButton(Modifier.weight(1f))
                        TransportStopButton(Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransportRewindButton(Modifier.weight(1f))
                            TransportPlayPauseButton(Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransportForwardButton(Modifier.weight(1f))
                            TransportStopButton(Modifier.weight(1f))
                        }
                    }
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isLoopSelection = false
                        playSelectionRange(loopSegment = false)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_workspace_play_selection")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play Selection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (isLoopSelection) {
                    Button(
                        onClick = {
                            isLoopSelection = false
                            if (isPlaying) playerManager.pause()
                            AccessibilityHelper.announceForAccessibility(context, "Selection loop turned off")
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("btn_workspace_loop_selection")
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Loop On", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            isLoopSelection = true
                            playSelectionRange(loopSegment = true)
                            AccessibilityHelper.announceForAccessibility(context, "Looping selection")
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("btn_workspace_loop_selection")
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Loop Selection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = { playerManager.seekTo(0L) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_workspace_jump_start")
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { playerManager.seekTo(durationMs) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_workspace_jump_end")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("End", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { showSpeedDeck = !showSpeedDeck },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_workspace_toggle_speed_deck")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showSpeedDeck) "Hide Speed & Vol" else "Speed & Volume", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (showSpeedDeck) {
                AudioControlDeckCard(
                    playbackRate = livePlaybackRate,
                    masterVolume = liveVolumeLevel,
                    sampleRateHz = sampleRate,
                    visualMode = visualMode,
                    onPlaybackRateChange = { r ->
                        playerManager.setPlaybackRate(r)
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                    },
                    onMasterVolumeChange = { v -> playerManager.setVolumeLevel(v) }
                )
            }

            // Rewind / Fast-Forward Scrub Speed Selector for Audio Editing
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
                    FeatureGuideButton(
                        featureTitle = "Audio Trim & Cut",
                        onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_TRIM_CUT },
                        testTag = "btn_feature_guide_audio_trim"
                    )
                    AccessibleAudioWaveform(
                        currentMs = 0L,
                        durationMs = durationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        samples = workingPcm,
                        visualMode = visualMode,
                        onToggleVisualMode = {
                            val modes = AudioVisualMode.values()
                            visualMode = modes[(visualMode.ordinal + 1) % modes.size]
                            AccessibilityHelper.announceForAccessibility(context, "Visual display switched to ${visualMode.displayName}")
                        },
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

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("card_trim_clipboard_silence"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val clipSecText = clipboardPcm?.let { clip ->
                                if (clip.isNotEmpty()) "Clipboard Ready (${"%.1f".format(clip.size.toFloat() / sampleRate)}s)" else null
                            } ?: "Clipboard Empty"
                            Text(
                                text = "Cut, Copy, Paste, Silence & Cues • $clipSecText",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val s = trimStartMs; val e = trimEndMs
                                        if (workingPcm.isNotEmpty() && e > s) {
                                            val cutClip = StudioAudioProcessor.cropToSelection(workingPcm, s, e, sampleRate)
                                            if (cutClip.isNotEmpty()) clipboardPcm = cutClip
                                            hasPendingTrimRange = false
                                            pushUndoAndApply("Cut selection to clipboard") {
                                                StudioAudioProcessor.deleteSelection(it, s, e)
                                            }
                                        } else {
                                            Toast.makeText(context, "Select an audio range first.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_cut")
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cut", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val s = trimStartMs; val e = trimEndMs
                                        if (workingPcm.isNotEmpty() && e > s) {
                                            val copied = StudioAudioProcessor.cropToSelection(workingPcm, s, e, sampleRate)
                                            if (copied.isNotEmpty()) {
                                                clipboardPcm = copied
                                                localModified = true
                                                val msg = "Copied ${"%.1f".format(copied.size.toFloat() / sampleRate)}s to clipboard"
                                                AccessibilityHelper.announceForAccessibility(context, msg)
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Select an audio range first.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_copy")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboardPcm
                                        if (clip == null || clip.isEmpty()) {
                                            Toast.makeText(context, "Clipboard is empty. Copy or Cut audio first.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val cursorMs = playerManager.currentPositionMs.value
                                            pushUndoAndApply("Pasted clipboard at cursor", allowEmptyInput = true) { pcm ->
                                                val pos = if (pcm.isEmpty()) 0 else ((cursorMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(0, pcm.size)
                                                ShortArray(pcm.size + clip.size).also { out ->
                                                    System.arraycopy(pcm, 0, out, 0, pos)
                                                    System.arraycopy(clip, 0, out, pos, clip.size)
                                                    System.arraycopy(pcm, pos, out, pos + clip.size, pcm.size - pos)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_paste_cursor")
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Paste Cursor", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboardPcm
                                        if (clip == null || clip.isEmpty()) {
                                            Toast.makeText(context, "Clipboard is empty. Copy or Cut audio first.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pushUndoAndApply("Pasted clipboard at end", allowEmptyInput = true) { pcm ->
                                                ShortArray(pcm.size + clip.size).also { out ->
                                                    System.arraycopy(pcm, 0, out, 0, pcm.size)
                                                    System.arraycopy(clip, 0, out, pcm.size, clip.size)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_paste_end")
                                ) {
                                    Text("Paste End", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboardPcm
                                        if (clip == null || clip.isEmpty()) {
                                            Toast.makeText(context, "Clipboard is empty. Copy or Cut audio first.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val cursorMs = playerManager.currentPositionMs.value
                                            pushUndoAndApply("Mix pasted clipboard at cursor", allowEmptyInput = true) { pcm ->
                                                StudioAudioProcessor.mixAudioTracks(
                                                    main = pcm,
                                                    overlay = clip,
                                                    startMs = cursorMs,
                                                    mainVolume = 1.0f,
                                                    overlayVolume = 0.85f,
                                                    applyPeakLimiter = true,
                                                    sampleRate = sampleRate
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_mix_paste")
                                ) {
                                    Text("Mix Paste", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showInsertSilenceDialog = true },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_insert_silence")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Insert Silence", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showCuePointsDialog = true },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_cue_points")
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cue Points (${cuePoints.size})", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { playerManager.seekTo(trimStartMs) },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_go_sel_start")
                                ) {
                                    Text("Go Sel Start", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { playerManager.seekTo(trimEndMs) },
                                    modifier = Modifier.height(48.dp).testTag("btn_workspace_go_sel_end")
                                ) {
                                    Text("Go Sel End", fontWeight = FontWeight.Bold)
                                }
                            }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Mix Second Audio File",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                FeatureGuideButton(
                                    featureTitle = "Audio Mix & Overdub",
                                    onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_MIX_OVERDUB },
                                    testTag = "btn_feature_guide_audio_mix"
                                )
                            }
                            OutlinedButton(
                                onClick = { showMixAudioPickerDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_mix_browse_file")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(mixOverlayTitle ?: "Browse Audio File to Mix", fontWeight = FontWeight.Bold)
                            }
                            val clipForMix = clipboardPcm
                            if (clipForMix != null && clipForMix.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        mixOverlayPcm = clipForMix
                                        mixOverlayTitle = "Clipboard Audio (${"%.1f".format(clipForMix.size.toFloat() / sampleRate)}s)"
                                        AccessibilityHelper.announceForAccessibility(context, "Loaded clipboard audio ready to mix")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_mix_use_clipboard")
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Use Clipboard Audio (${"%.1f".format(clipForMix.size.toFloat() / sampleRate)}s)", fontWeight = FontWeight.Bold)
                                }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Quick Effects & Cleaning Buttons",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                FeatureGuideButton(
                                    featureTitle = "Quick Effects & Cleaning",
                                    onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_EFFECTS_CLEANING },
                                    testTag = "btn_feature_guide_quick_effects"
                                )
                            }
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
                                OutlinedButton(
                                    onClick = {
                                        val s = trimStartMs; val e = trimEndMs
                                        pushUndoAndApply("Applied Fade In") { StudioAudioProcessor.fadeInSelection(it, s, e) }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_fade_in")
                                ) { Text("Fade In", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = {
                                        val s = trimStartMs; val e = trimEndMs
                                        pushUndoAndApply("Applied Fade Out") { StudioAudioProcessor.fadeOutSelection(it, s, e) }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_fade_out")
                                ) { Text("Fade Out", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { showGainDialog = true },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_custom_gain")
                                ) { Text("Custom Gain ±dB", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Normalized Loudness (-16 LUFS / RMS)") { StudioAudioProcessor.normalizeRms(it, -16f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_normalize_rms")
                                ) { Text("Normalize RMS", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied Dynamic Expander") { StudioAudioProcessor.applyExpander(it, -28f, 2.0f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_expander")
                                ) { Text("Expander", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied 300 Hz High-Pass Rumble Filter") { StudioAudioProcessor.applyHighPassFilter(it, 300f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_highpass")
                                ) { Text("High-Pass 300Hz", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied 3000 Hz Low-Pass Warm Filter") { StudioAudioProcessor.applyLowPassFilter(it, 3000f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_lowpass")
                                ) { Text("Low-Pass 3kHz", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied Radio / Telephone Band-Pass") { StudioAudioProcessor.applyBandPassFilter(it, 1800f, 3000f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_bandpass")
                                ) { Text("Band-Pass Voice", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { pushUndoAndApply("Applied Mechanize Robot Voice") { StudioAudioProcessor.applyMechanize(it, 120f, 0.85f) } },
                                    modifier = Modifier.height(48.dp).testTag("btn_fx_mechanize")
                                ) { Text("Mechanize Robot", fontWeight = FontWeight.Bold) }
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
                                    "pitch_polarity" to "Pitch & Polarity",
                                    "pro_suite" to "Time Stretch, Pitch & Pro DSP"
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "EQ Presets",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FeatureGuideButton(
                                        featureTitle = "EQ Presets",
                                        onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_EQ_PRESETS },
                                        testTag = "btn_feature_guide_eq_presets"
                                    )
                                }
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Echo (Delay)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FeatureGuideButton(
                                        featureTitle = "Echo (Delay)",
                                        onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_EFFECT_ECHO },
                                        testTag = "btn_feature_guide_workspace_echo"
                                    )
                                }
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Reverb (Acoustic Space)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FeatureGuideButton(
                                        featureTitle = "Reverb (Acoustic Space)",
                                        onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_EFFECT_REVERB },
                                        testTag = "btn_feature_guide_workspace_reverb"
                                    )
                                }
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

                    if (focusedEffectPanel == "pro_suite") {
                        var fxProCategoryIndex by remember(session.projectId, tool) { mutableIntStateOf(0) }
                        AccessibleProSuiteSection(
                            selectedCategoryIndex = fxProCategoryIndex,
                            onSelectCategoryIndex = { idx ->
                                fxProCategoryIndex = idx
                                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                            },
                            hasClipboard = clipboardPcm != null && clipboardPcm!!.isNotEmpty(),
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            currentPositionMs = playerManager.currentPositionMs.value,
                            currentSampleRate = sampleRate,
                            currentPlaybackRate = livePlaybackRate,
                            clipboardPcm = clipboardPcm,
                            onApplyPcmTransform = { msg, fn ->
                                pushUndoAndApply(msg, transform = fn)
                            },
                            onSetSampleRate = { sr -> playerManager.setWorkingSampleRate(sr) },
                            onSetPlaybackRate = { rate -> playerManager.setPlaybackRate(rate) },
                            onOpenEchoReverbTab = { focusedEffectPanel = "echo" },
                            onOpenNoiseTab = { focusedEffectPanel = "denoise" },
                            onOpenEqTab = { focusedEffectPanel = "eq" },
                            onOpenGainDialog = { showGainDialog = true },
                            onApplyEcho = {
                                pushUndoAndApply("Applied Echo (${echoDelayMs}ms)") { pcm ->
                                    audioProcessor.applyEcho(pcm, echoDelayMs, echoFeedback, echoMix)
                                }
                            },
                            onApplyReverb = {
                                pushUndoAndApply("Applied Acoustic Reverb") { pcm ->
                                    audioProcessor.applyReverb(pcm, reverbRoomSize, reverbDamping, reverbMix)
                                }
                            },
                            onApplyNoiseGate = {
                                pushUndoAndApply("Applied Noise Gate") { pcm ->
                                    audioProcessor.applyNoiseGate(pcm, 0.08f)
                                }
                            },
                            onApplyNoiseReduction = {
                                val applied = denoiseParams.copy(bypass = false).clamped()
                                val profile = learnedNoiseProfile
                                liveDenoiseArmed = false
                                playerManager.setRealtimeDenoiseEnabled(false)
                                pushUndoAndApply("Applied Studio Adaptive De-Noise") { pcm ->
                                    AdaptiveSpectralDenoiser.processOffline(
                                        samples = pcm,
                                        sampleRate = sampleRate,
                                        channels = 1,
                                        params = applied,
                                        noiseProfile = profile
                                    )
                                }
                            },
                            onApplyEqualizer = {
                                pushUndoAndApply("Applied EQ (${selectedEq.name})") { pcm ->
                                    audioProcessor.processPcm(pcm, editSettings.copy(eqPreset = selectedEq), sampleRate)
                                }
                            }
                        )
                    }

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
                    val isTooLoud by recorderManager.isTooLoudWarning.collectAsState()
                    val resolvedRecConfig by recorderManager.resolvedConfig.collectAsState()
                    var recordMode by remember(session.projectId, tool) { mutableStateOf("replace") }
                    var autoGain by remember {
                        mutableStateOf(recordingSettings?.recordingAutoGainEnabled ?: recorderManager.autoGainEnabled)
                    }
                    var micBufferSize by remember { mutableIntStateOf(recorderManager.bufferSizeOption) }
                    var micGain by remember {
                        mutableFloatStateOf(recordingSettings?.recordingInputGain ?: recorderManager.micGainVolume)
                    }
                    LaunchedEffect(recordingSettings) {
                        if (recordingSettings != null) {
                            recorderManager.applySettings(recordingSettings)
                            autoGain = recordingSettings.recordingAutoGainEnabled
                            micGain = StudioPreferences.normalizeInputGain(recordingSettings.recordingInputGain)
                        }
                    }

                    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            recorderManager.bufferSizeOption = micBufferSize
                            recorderManager.autoGainEnabled = autoGain
                            recorderManager.micGainVolume = micGain
                            recorderManager.startRecording(onSuccess = {}, onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
                        } else {
                            Toast.makeText(context, "Microphone permission required.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    fun startWorkspaceRecording(mode: String) {
                        recordMode = mode
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            recorderManager.bufferSizeOption = micBufferSize
                            recorderManager.autoGainEnabled = autoGain
                            recorderManager.micGainVolume = micGain
                            recorderManager.startRecording(
                                onSuccess = {
                                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.HEAVY_CLICK)
                                    val msg = when (mode) {
                                        "append" -> "Append recording started"
                                        "punch_in" -> "Punch-In recording started over selection"
                                        else -> "Voice recording started"
                                    }
                                    AccessibilityHelper.announceForAccessibility(context, msg)
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Voice Recording Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                FeatureGuideButton(
                                    featureTitle = "Voice Recording Studio",
                                    onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_VOICE_RECORDER },
                                    testTag = "btn_feature_guide_voice_recorder"
                                )
                            }
                            Text(
                                text = "Active Profile: ${resolvedRecConfig.summaryLabel()} • Input Gain ${(micGain * 100).toInt()}% • Buffer $micBufferSize",
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
                                Switch(
                                    checked = autoGain,
                                    onCheckedChange = {
                                        autoGain = it
                                        recorderManager.autoGainEnabled = it
                                    },
                                    modifier = Modifier.testTag("switch_workspace_rec_auto_gain")
                                )
                            }
                            if (isRecording) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isTooLoud) "CLIPPING" else "MIC",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (isTooLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    LinearProgressIndicator(
                                        progress = { recAmp },
                                        modifier = Modifier.weight(1f).height(8.dp),
                                        color = if (isTooLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = {
                                        val recorded = recorderManager.stopRecording()
                                        if (recorded != null && recorded.isNotEmpty()) {
                                            val cleaned = StudioAudioProcessor.gentleCleanup(recorded)
                                            pushUndoSnapshotSafely(workingPcm)
                                            when {
                                                recordMode == "punch_in" && workingPcm.isNotEmpty() && trimEndMs > trimStartMs -> {
                                                    val sIdx = ((trimStartMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(0, workingPcm.size)
                                                    val eIdx = ((trimEndMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(sIdx, workingPcm.size)
                                                    val merged = ShortArray(sIdx + cleaned.size + (workingPcm.size - eIdx))
                                                    System.arraycopy(workingPcm, 0, merged, 0, sIdx)
                                                    System.arraycopy(cleaned, 0, merged, sIdx, cleaned.size)
                                                    System.arraycopy(workingPcm, eIdx, merged, sIdx + cleaned.size, workingPcm.size - eIdx)
                                                    workingPcm = merged
                                                    AccessibilityHelper.announceForAccessibility(context, "Punch-in recording spliced into track.")
                                                }
                                                recordMode == "append" && workingPcm.isNotEmpty() -> {
                                                    val combined = ShortArray(workingPcm.size + cleaned.size)
                                                    System.arraycopy(workingPcm, 0, combined, 0, workingPcm.size)
                                                    System.arraycopy(cleaned, 0, combined, workingPcm.size, cleaned.size)
                                                    workingPcm = combined
                                                    AccessibilityHelper.announceForAccessibility(context, "Appended recording to track end.")
                                                }
                                                else -> {
                                                    workingPcm = cleaned
                                                    workingTitle = "Voice Recording (${AccessibilityHelper.formatTimeVisual(recDurMs)})"
                                                    AccessibilityHelper.announceForAccessibility(context, "Recorded new voice track cleanly.")
                                                }
                                            }
                                            localModified = true
                                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_workspace_stop_record"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when (recordMode) {
                                            "punch_in" -> "Stop Punch-In (${AccessibilityHelper.formatTimeVisual(recDurMs)})"
                                            "append" -> "Stop Append (${AccessibilityHelper.formatTimeVisual(recDurMs)})"
                                            else -> "Stop Recording (${AccessibilityHelper.formatTimeVisual(recDurMs)})"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { startWorkspaceRecording("replace") },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_workspace_start_record")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start Recording (New / Replace)", fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { startWorkspaceRecording("append") },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_workspace_append_record")
                                    ) {
                                        Text("Append to End", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { startWorkspaceRecording("punch_in") },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_workspace_punch_in_record")
                                    ) {
                                        Text("Punch-In Range", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            HorizontalDivider()

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Mic Buffer:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                listOf(1024, 2048, 4096).forEach { size ->
                                    FilterChip(
                                        selected = micBufferSize == size,
                                        onClick = {
                                            micBufferSize = size
                                            recorderManager.bufferSizeOption = size
                                        },
                                        label = { Text(if (size == 2048) "$size (Default)" else "$size", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("chip_workspace_buffer_$size")
                                    )
                                }
                            }

                            AccessibleEffectSlider(
                                title = "Mic Input Gain",
                                value = micGain,
                                onValueChange = {
                                    micGain = it
                                    recorderManager.micGainVolume = it
                                },
                                valueRange = 0.2f..2.0f,
                                step = 0.1f,
                                displayValue = "${(micGain * 100).toInt()}%",
                                spokenDescription = "Microphone input gain ${(micGain * 100).toInt()} percent",
                                testTag = "slider_workspace_mic_gain"
                            )
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
                        onOpenGuide = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_MUSIC_REBALANCE }
                    )
                }

                AudioFeatureTool.VOICE_OVER -> {
                    var selectedPreset by remember(session.projectId, tool) {
                        mutableStateOf(AudioEffectsProEngine.VoiceOverPreset.TUTORIAL)
                    }
                    var sourceChoice by remember(session.projectId, tool) {
                        mutableStateOf(VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC)
                    }
                    var pickedVoPcm by remember(session.projectId, tool) { mutableStateOf<ShortArray?>(null) }
                    var pickedVoTitle by remember(session.projectId, tool) { mutableStateOf<String?>(null) }
                    var autoDuckEnabled by remember(session.projectId, tool) { mutableStateOf(selectedPreset.autoDuckEnabled) }
                    var duckFactor by remember(session.projectId, tool) { mutableFloatStateOf(selectedPreset.duckFactor) }
                    var voiceGain by remember(session.projectId, tool) { mutableFloatStateOf(selectedPreset.voiceGain) }
                    var musicVolume by remember(session.projectId, tool) { mutableFloatStateOf(selectedPreset.musicVolume) }
                    var loopMusic by remember(session.projectId, tool) { mutableStateOf(true) }
                    var syncOffsetMs by remember(session.projectId, tool) { mutableLongStateOf(0L) }
                    var showVoPickerDialog by remember { mutableStateOf(false) }
                    val voExportedFiles by mediaRepository.exportedFiles.collectAsState()

                    if (showVoPickerDialog) {
                        AccessibleAudioPickerDialog(
                            exportedAudios = voExportedFiles,
                            onSelectAudio = { uri, title ->
                                showVoPickerDialog = false
                                scope.launch {
                                    try {
                                        val decodedRaw = audioProcessor.decodeUriToPcm(uri)
                                        val decoded = if (decodedRaw.isNotEmpty() && sampleRate != 44100) {
                                            StudioAudioProcessor.resamplePcmLinear(decodedRaw, 44100, sampleRate)
                                        } else {
                                            decodedRaw
                                        }
                                        if (decoded.isNotEmpty()) {
                                            pickedVoPcm = decoded
                                            pickedVoTitle = title?.takeIf { it.isNotBlank() }
                                                ?: audioProcessor.queryAudioDisplayName(uri)
                                                ?: "Music / Voice Track"
                                        } else {
                                            Toast.makeText(context, "Could not decode selected audio.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (t: Throwable) {
                                        if (t is CancellationException) throw t
                                        Toast.makeText(context, "Voice Over load error: ${t.localizedMessage ?: "Unsupported file"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onDismiss = { showVoPickerDialog = false }
                        )
                    }

                    fun buildVoiceOverResult(basePcm: ShortArray, cursorPosMs: Long): ShortArray {
                        val pickedPcm = pickedVoPcm
                        val clip = clipboardPcm
                        return when {
                            sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC && pickedPcm != null && pickedPcm.isNotEmpty() -> {
                                AudioEffectsProEngine.applySmartMusicBedAutoDuck(
                                    voiceTrack = basePcm,
                                    musicPcm = pickedPcm,
                                    duckLevel = duckFactor,
                                    voiceGain = voiceGain,
                                    musicBaseVolume = musicVolume,
                                    autoDuckEnabled = autoDuckEnabled,
                                    loopMusic = loopMusic,
                                    sampleRate = sampleRate,
                                    startOffsetMs = syncOffsetMs
                                )
                            }
                            sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP && pickedPcm != null && pickedPcm.isNotEmpty() -> {
                                AudioEffectsProEngine.applyVoiceOverDuck(
                                    mainTrack = basePcm,
                                    voiceClip = pickedPcm,
                                    insertAtMs = cursorPosMs + syncOffsetMs,
                                    musicDuckFactor = if (autoDuckEnabled) duckFactor else 1.0f,
                                    voiceGain = voiceGain,
                                    sampleRate = sampleRate
                                )
                            }
                            sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP && clip != null && clip.isNotEmpty() -> {
                                AudioEffectsProEngine.applyVoiceOverDuck(
                                    mainTrack = basePcm,
                                    voiceClip = clip,
                                    insertAtMs = cursorPosMs + syncOffsetMs,
                                    musicDuckFactor = if (autoDuckEnabled) duckFactor else 1.0f,
                                    voiceGain = voiceGain,
                                    sampleRate = sampleRate
                                )
                            }
                            else -> {
                                AudioEffectsProEngine.applyShapeVolume(
                                    samples = basePcm,
                                    startMs = trimStartMs,
                                    endMs = trimEndMs,
                                    shapeType = AudioEffectsProEngine.VolumeShapeType.DUCK_VALLEY,
                                    depth = (1f - duckFactor).coerceIn(0.1f, 0.95f),
                                    sampleRate = sampleRate
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("card_workspace_voice_over"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Voice Over & Auto-Ducking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                FeatureGuideButton(
                                    featureTitle = "Voice Over & Auto-Ducking",
                                    onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_VOICE_OVER },
                                    testTag = "btn_feature_guide_voice_over"
                                )
                            }

                            OutlinedButton(
                                onClick = { showVoPickerDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_vo_workspace_pick_audio")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(pickedVoTitle ?: "Browse Audio File (Music or Voice Clip)", fontWeight = FontWeight.Bold)
                            }

                            if (pickedVoPcm != null) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC,
                                        onClick = { sourceChoice = VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC },
                                        label = { Text("As Background Music") },
                                        modifier = Modifier.testTag("chip_vo_workspace_role_music")
                                    )
                                    FilterChip(
                                        selected = sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP,
                                        onClick = { sourceChoice = VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP },
                                        label = { Text("Insert Voice Clip") },
                                        modifier = Modifier.testTag("chip_vo_workspace_role_clip")
                                    )
                                }
                            }

                            val voClip = clipboardPcm
                            OutlinedButton(
                                onClick = { sourceChoice = VoiceOverSourceChoice.CLIPBOARD_CLIP },
                                enabled = voClip != null && voClip.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_vo_workspace_use_clipboard")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (voClip != null && voClip.isNotEmpty()) {
                                        if (sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP) "Using Clipboard Audio (Selected)"
                                        else "Use Clipboard Audio (${"%.1f".format(voClip.size.toFloat() / sampleRate)}s)"
                                    } else {
                                        "Clipboard Empty (Copy audio first or Browse above)"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text("Voice-Over Presets", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AudioEffectsProEngine.VoiceOverPreset.values().forEach { preset ->
                                    FilterChip(
                                        selected = selectedPreset == preset,
                                        onClick = {
                                            selectedPreset = preset
                                            voiceGain = preset.voiceGain
                                            musicVolume = preset.musicVolume
                                            duckFactor = preset.duckFactor
                                            autoDuckEnabled = preset.autoDuckEnabled
                                        },
                                        label = { Text(preset.displayName, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.testTag("chip_vo_workspace_preset_${preset.name}")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Ducking", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Automatically dips background music during speech",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoDuckEnabled,
                                    onCheckedChange = { autoDuckEnabled = it },
                                    modifier = Modifier.testTag("switch_vo_workspace_auto_duck")
                                )
                            }

                            if (autoDuckEnabled) {
                                AccessibleEffectSlider(
                                    title = "Background Duck Level",
                                    value = duckFactor,
                                    onValueChange = { duckFactor = it },
                                    valueRange = 0.10f..0.85f,
                                    step = 0.05f,
                                    displayValue = "${(duckFactor * 100).toInt()}%",
                                    spokenDescription = "Background volume dipped to ${(duckFactor * 100).toInt()} percent during speech",
                                    testTag = "slider_vo_workspace_duck"
                                )
                            }

                            AccessibleEffectSlider(
                                title = "Voice Gain",
                                value = voiceGain,
                                onValueChange = { voiceGain = it },
                                valueRange = 0.70f..1.80f,
                                step = 0.05f,
                                displayValue = "${(voiceGain * 100).toInt()}%",
                                spokenDescription = "Voice gain ${(voiceGain * 100).toInt()} percent",
                                testTag = "slider_vo_workspace_gain"
                            )

                            if (sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC) {
                                AccessibleEffectSlider(
                                    title = "Background Music Volume",
                                    value = musicVolume,
                                    onValueChange = { musicVolume = it },
                                    valueRange = 0.10f..1.00f,
                                    step = 0.05f,
                                    displayValue = "${(musicVolume * 100).toInt()}%",
                                    spokenDescription = "Background music volume ${(musicVolume * 100).toInt()} percent",
                                    testTag = "slider_vo_workspace_music_vol"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Loop Music to Match Voice", fontWeight = FontWeight.Bold)
                                    Switch(
                                        checked = loopMusic,
                                        onCheckedChange = { loopMusic = it },
                                        modifier = Modifier.testTag("switch_vo_workspace_loop")
                                    )
                                }
                            }

                            if (pickedVoPcm != null || (voClip != null && voClip.isNotEmpty() && sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP)) {
                                MixSyncOffsetControl(
                                    offsetMs = syncOffsetMs,
                                    onOffsetChange = { syncOffsetMs = it },
                                    baseStartMs = if (sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC) 0L else currentPositionMs,
                                    title = "Added Audio Sync Timing"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (workingPcm.isEmpty()) {
                                        Toast.makeText(context, "Track is empty.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val curPos = playerManager.currentPositionMs.value
                                        scope.launch {
                                            try {
                                                playerManager.stop()
                                                val preview = withContext(Dispatchers.Default) {
                                                    buildVoiceOverResult(workingPcm, curPos)
                                                }
                                                playerManager.loadPcmData(preview, sampleRate)
                                                playerManager.play()
                                            } catch (t: Throwable) {
                                                if (t is CancellationException) throw t
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_vo_workspace_preview")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Preview Voice Over", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val curPos = playerManager.currentPositionMs.value
                                    pushUndoAndApply("Applied ${selectedPreset.displayName} Voice Over") { pcm ->
                                        buildVoiceOverResult(pcm, curPos)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_vo_workspace_apply")
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Apply Voice Over", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AudioFeatureTool.AUDIO_GENERATORS_TOOLS -> {
                    var toolsProCategoryIndex by remember(session.projectId, tool) { mutableIntStateOf(0) }
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("card_workspace_audio_tools"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Tone Generator, Silence, Cues & Stats",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                FeatureGuideButton(
                                    featureTitle = "Tone, Silence, Cues & Stats",
                                    onClick = { activeWorkspaceGuideTopic = FeatureGuideTopic.AUDIO_CUE_POINTS },
                                    testTag = "btn_feature_guide_audio_tools"
                                )
                            }

                            Button(
                                onClick = { showToneGeneratorDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_tools_tone_generator")
                            ) {
                                Icon(Icons.Default.Waves, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Synthesize Tone / Noise", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showInsertSilenceDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_tools_insert_silence")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Insert Digital Silence at Playhead", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showCuePointsDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_tools_cue_points")
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cue Points & Bookmarks (${cuePoints.size})", fontWeight = FontWeight.Bold)
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val posMs = playerManager.currentPositionMs.value
                                        val cueNum = cuePoints.size + 1
                                        val newCue = CuePoint(
                                            positionMs = posMs,
                                            name = "Cue $cueNum (${AccessibilityHelper.formatTimeMMSS(posMs)})"
                                        )
                                        cuePoints = (cuePoints + newCue).sortedBy { it.positionMs }
                                        localModified = true
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                                        AccessibilityHelper.announceForAccessibility(context, "Dropped cue point $cueNum")
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_tools_drop_cue")
                                ) { Text("Drop Cue Here", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = {
                                        val posMs = playerManager.currentPositionMs.value
                                        cuePoints.filter { it.positionMs < posMs - 150L }.maxByOrNull { it.positionMs }?.let { prev ->
                                            playerManager.seekTo(prev.positionMs)
                                            AccessibilityHelper.announceForAccessibility(context, "Jumped to ${prev.name}")
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_tools_prev_cue")
                                ) { Text("Prev Cue", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = {
                                        val posMs = playerManager.currentPositionMs.value
                                        cuePoints.filter { it.positionMs > posMs + 150L }.minByOrNull { it.positionMs }?.let { next ->
                                            playerManager.seekTo(next.positionMs)
                                            AccessibilityHelper.announceForAccessibility(context, "Jumped to ${next.name}")
                                        }
                                    },
                                    modifier = Modifier.height(48.dp).testTag("btn_tools_next_cue")
                                ) { Text("Next Cue", fontWeight = FontWeight.Bold) }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (workingPcm.isEmpty()) {
                                        Toast.makeText(context, "Track is empty.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        currentAudioStats = StudioAudioProcessor.computeStatistics(workingPcm)
                                        showStatsDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_tools_audio_stats")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Peak Level & RMS Statistics", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AccessibleProSuiteSection(
                        selectedCategoryIndex = toolsProCategoryIndex,
                        onSelectCategoryIndex = { idx ->
                            toolsProCategoryIndex = idx
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                        },
                        hasClipboard = clipboardPcm != null && clipboardPcm!!.isNotEmpty(),
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        currentPositionMs = playerManager.currentPositionMs.value,
                        currentSampleRate = sampleRate,
                        currentPlaybackRate = livePlaybackRate,
                        clipboardPcm = clipboardPcm,
                        onApplyPcmTransform = { msg, fn ->
                            pushUndoAndApply(msg, transform = fn)
                        },
                        onSetSampleRate = { sr -> playerManager.setWorkingSampleRate(sr) },
                        onSetPlaybackRate = { rate -> playerManager.setPlaybackRate(rate) },
                        onOpenEchoReverbTab = {},
                        onOpenNoiseTab = {},
                        onOpenEqTab = {},
                        onOpenGainDialog = { showGainDialog = true }
                    )
                }

                AudioFeatureTool.FULL_DAW_STUDIO -> Unit
            }
        }
    }

    if (showInsertSilenceDialog) {
        val silenceInsertPosMs = playerManager.currentPositionMs.value
        StudioInsertSilenceDialog(
            currentPositionMs = silenceInsertPosMs,
            onDismiss = { showInsertSilenceDialog = false },
            onConfirmInsert = { seconds ->
                showInsertSilenceDialog = false
                pushUndoAndApply("Inserted $seconds seconds of silence", allowEmptyInput = true) { pcm ->
                    StudioAudioProcessor.insertSilence(pcm, silenceInsertPosMs, seconds)
                }
            }
        )
    }

    if (showToneGeneratorDialog) {
        StudioToneGeneratorDialog(
            onDismiss = { showToneGeneratorDialog = false },
            onConfirmGenerate = { type, freq, durationSec ->
                showToneGeneratorDialog = false
                pushUndoAndApply("Synthesized $type (${durationSec}s)", allowEmptyInput = true) { _ ->
                    StudioAudioProcessor.generateTone(type, freq, durationSec)
                }
                workingTitle = "Generated: $type (${durationSec}s)"
                workingBaseName = null
            }
        )
    }

    if (showCuePointsDialog) {
        val cueDialogPosMs by playerManager.currentPositionMs.collectAsState()
        StudioCuePointsDialog(
            cuePoints = cuePoints,
            currentMs = cueDialogPosMs,
            onDropCue = {
                val posMs = playerManager.currentPositionMs.value
                val cueNum = cuePoints.size + 1
                val newCue = CuePoint(
                    positionMs = posMs,
                    name = "Cue $cueNum (${AccessibilityHelper.formatTimeMMSS(posMs)})"
                )
                cuePoints = (cuePoints + newCue).sortedBy { it.positionMs }
                localModified = true
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(context, "Dropped cue point $cueNum")
            },
            onJumpPrevCue = {
                val posMs = playerManager.currentPositionMs.value
                cuePoints.filter { it.positionMs < posMs - 150L }.maxByOrNull { it.positionMs }?.let { prev ->
                    playerManager.seekTo(prev.positionMs)
                }
            },
            onJumpNextCue = {
                val posMs = playerManager.currentPositionMs.value
                cuePoints.filter { it.positionMs > posMs + 150L }.minByOrNull { it.positionMs }?.let { next ->
                    playerManager.seekTo(next.positionMs)
                }
            },
            onSeekToCue = { cue -> playerManager.seekTo(cue.positionMs) },
            onDeleteCue = { cue ->
                cuePoints = cuePoints.filter { it.id != cue.id }
                localModified = true
            },
            onDismiss = { showCuePointsDialog = false }
        )
    }

    if (showGainDialog) {
        StudioGainDialog(
            onDismiss = { showGainDialog = false },
            onConfirmGain = { gainDb ->
                showGainDialog = false
                val hasSelection = trimEndMs > trimStartMs && trimEndMs < durationMs
                val s = trimStartMs
                val e = trimEndMs
                pushUndoAndApply("Applied %+.1f dB gain".format(gainDb)) { pcm ->
                    if (hasSelection) StudioAudioProcessor.applyGainDb(pcm, gainDb, s, e)
                    else StudioAudioProcessor.applyGainDb(pcm, gainDb)
                }
            }
        )
    }

    if (showStatsDialog && currentAudioStats != null) {
        StudioAudioStatsDialog(
            stats = currentAudioStats!!,
            onDismiss = { showStatsDialog = false }
        )
    }

    activeWorkspaceGuideTopic?.let { topic ->
        SingleFeatureGuideDialog(
            topic = topic,
            onDismiss = { activeWorkspaceGuideTopic = null }
        )
    }
}

@Composable
fun WorkspaceHeaderBar(
    title: String,
    subtitle: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean = false,
    onRedo: () -> Unit = {},
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

            if (canRedo) {
                IconButton(
                    onClick = onRedo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_workspace_redo")
                        .semantics { contentDescription = "Redo last undone change in workspace" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
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
