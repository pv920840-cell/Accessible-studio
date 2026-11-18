package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioEditSettings
import com.example.audio.AudioEffectsProEngine
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.CuePoint
import com.example.audio.EqPreset
import com.example.audio.ScrubDirection
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.audio.VocalIsolationProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.ui.components.AccessibleAudioWaveform
import com.example.ui.components.AccessibleEffectSlider
import com.example.ui.components.AccessibleExportDialog
import com.example.ui.components.AccessibleProSuiteSection
import com.example.ui.components.AccessibleTransportControls
import com.example.ui.components.AccessibleTrimSection
import com.example.ui.components.AudioControlDeckCard
import com.example.ui.components.AudioOpenDialog
import com.example.ui.components.AudioProjectTab
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.AudioVisualMode
import com.example.ui.components.OpenProjectsTabBar
import com.example.ui.components.StudioAudioPropertiesDialog
import com.example.ui.components.StudioAudioStatsDialog
import com.example.ui.components.StudioBlankProjectDialog
import com.example.ui.components.StudioCuePointsDialog
import com.example.ui.components.StudioEffectsRackDialog
import com.example.ui.components.StudioGainDialog
import com.example.ui.components.StudioInsertSilenceDialog
import com.example.ui.components.StudioMixAudioDialog
import com.example.ui.components.StudioSpecifyViewDialog
import com.example.ui.components.StudioToneGeneratorDialog
import com.example.ui.components.StudioVocalKaraokeDialog
import com.example.ui.components.StudioVoiceOverDialog
import com.example.ui.components.VoiceOverSourceChoice
import com.example.ui.components.WaveformViewportMath
import com.example.ui.components.WaveformViewportSnapshot
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioStudioScreen(
    mediaRepository: MediaRepository,
    hapticsEnabled: Boolean,
    defaultExportFormat: String = "WAV",
    followCursor: Boolean = true,
    onNavigateToExports: () -> Unit,
    pendingCommand: AudioStudioCommand? = null,
    onCommandHandled: () -> Unit = {},
    onOpenHelpRequested: () -> Unit = {},
    initialPcm: ShortArray? = null,
    initialTitle: String? = null,
    initialBaseName: String? = null,
    initialSourceUri: Uri? = null,
    initialTrimStartMs: Long? = null,
    initialTrimEndMs: Long? = null,
    onSyncToProjectHub: ((String, String?, ShortArray, Int) -> Unit)? = null,
    onSyncTrimToProjectHub: ((Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighContrast = LocalHighContrast.current

    val audioProcessor = remember { WavAudioProcessor(context) }
    val playerManager = remember { AudioPlayerManager(context) }
    val recorderManager = remember { AudioRecorderManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }

    // Multi-Document Workspace & Track Data (Clean untitled starter project, no demo melody)
    val openProjects = remember { mutableStateListOf<AudioProjectTab>() }
    var activeProjectIndex by remember { mutableIntStateOf(0) }
    var mainTrackTitle by remember { mutableStateOf(initialTitle?.takeIf { it.isNotBlank() } ?: "Untitled Project") }
    var openedFileBaseName by remember { mutableStateOf<String?>(initialBaseName) }
    var currentTrackSourceUri by remember { mutableStateOf<Uri?>(initialSourceUri) }
    var rawOriginalPcm by remember { mutableStateOf<ShortArray?>(null) }
    val workingSampleRate by playerManager.sampleRate.collectAsState()

    // Trim State (declared before LaunchedEffect so initial seed updates resolve cleanly)
    var trimStartMs by remember { mutableLongStateOf(initialTrimStartMs ?: 0L) }
    var trimEndMs by remember { mutableLongStateOf(initialTrimEndMs ?: 5000L) }

    LaunchedEffect(Unit) {
        if (rawOriginalPcm == null) {
            val seedPcm = initialPcm ?: ShortArray(5 * workingSampleRate)
            val seedTitle = initialTitle?.takeIf { it.isNotBlank() } ?: "Untitled Project"
            rawOriginalPcm = seedPcm
            mainTrackTitle = seedTitle
            playerManager.loadPcmData(seedPcm, workingSampleRate)
            val seedDurMs = ((seedPcm.size.toDouble() / workingSampleRate) * 1000.0).toLong()
            val resolvedStart = (initialTrimStartMs ?: 0L).coerceIn(0L, seedDurMs)
            val resolvedEnd = (initialTrimEndMs?.takeIf { it > resolvedStart } ?: seedDurMs).coerceIn(resolvedStart, seedDurMs)
            trimStartMs = resolvedStart
            trimEndMs = resolvedEnd
            if (openProjects.isEmpty()) {
                openProjects.add(
                    AudioProjectTab(
                        title = seedTitle,
                        baseFileName = initialBaseName,
                        pcm = seedPcm,
                        sampleRate = workingSampleRate,
                        trimStartMs = resolvedStart,
                        trimEndMs = resolvedEnd,
                        currentPositionMs = 0L,
                        cuePoints = emptyList(),
                        isBlankProject = initialPcm == null,
                        sourceUri = initialSourceUri
                    )
                )
            }
        }
    }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val livePlaybackRate by playerManager.playbackRate.collectAsState()
    val liveVolumeLevel by playerManager.volumeLevel.collectAsState()
    val activeScrubDirection by playerManager.scrubDirection.collectAsState()
    var scrubSpeed by remember { mutableFloatStateOf(3.5f) }
    val savedExports by mediaRepository.exportedFiles.collectAsState()

    // View State
    var viewWindowStartMs by remember { mutableLongStateOf(0L) }
    var viewWindowSpanMs by remember { mutableStateOf<Long?>(null) }
    var verticalZoom by remember { mutableFloatStateOf(1.0f) }
    val viewHistoryStack = remember { mutableStateListOf<WaveformViewportSnapshot>() }
    var showSpecifyViewDialog by remember { mutableStateOf(false) }
    var visualMode by remember { mutableStateOf(AudioVisualMode.WAVEFORM) }
    var isLooping by remember { mutableStateOf(false) }

    val pushViewHistory: () -> Unit = {
        val snap = WaveformViewportSnapshot(viewWindowStartMs, viewWindowSpanMs, verticalZoom)
        if (viewHistoryStack.lastOrNull() != snap) {
            if (viewHistoryStack.size >= 20) viewHistoryStack.removeAt(0)
            viewHistoryStack.add(snap)
        }
    }

    val resetViewToAll: (Boolean) -> Unit = { recordHistory ->
        if (recordHistory) pushViewHistory()
        viewWindowStartMs = 0L
        viewWindowSpanMs = null
        if (!recordHistory) verticalZoom = 1.0f
    }

    val applyTimeSpanWindow: (Long, Long) -> Unit = { targetSpanMs, anchorMs ->
        val safeDur = durationMs.coerceAtLeast(100L)
        pushViewHistory()
        val clampedSpan = targetSpanMs.coerceIn(50L, safeDur)
        if (clampedSpan >= safeDur) {
            viewWindowStartMs = 0L
            viewWindowSpanMs = null
        } else {
            val maxStart = (safeDur - clampedSpan).coerceAtLeast(0L)
            viewWindowSpanMs = clampedSpan
            viewWindowStartMs = (anchorMs - clampedSpan / 2L).coerceIn(0L, maxStart)
        }
    }

    LaunchedEffect(currentPositionMs, followCursor) {
        val span = viewWindowSpanMs
        if (followCursor && span != null && durationMs > 0L) {
            if (currentPositionMs < viewWindowStartMs || currentPositionMs > viewWindowStartMs + span) {
                val maxStart = (durationMs - span).coerceAtLeast(0L)
                viewWindowStartMs = (currentPositionMs - span / 2L).coerceIn(0L, maxStart)
            }
        }
    }

    LaunchedEffect(durationMs) {
        if (durationMs <= 0L) {
            trimStartMs = 0L
            trimEndMs = 0L
        } else {
            if (trimStartMs > durationMs) trimStartMs = 0L
            if (trimEndMs > durationMs) trimEndMs = durationMs
        }
    }

    LaunchedEffect(currentPositionMs, isPlaying, isLooping) {
        if (isPlaying && isLooping && durationMs > 0 && currentPositionMs >= durationMs - 120L) {
            playerManager.seekTo(0L)
            playerManager.play()
        }
    }

    // Effects & DSP Settings (Managed cleanly in Effect/Tools Modals)
    var masterVolume by remember { mutableFloatStateOf(1.0f) }
    var fadeInMs by remember { mutableLongStateOf(500L) }
    var fadeOutMs by remember { mutableLongStateOf(1000L) }
    var selectedEq by remember { mutableStateOf(EqPreset.NORMAL) }
    var echoEnabled by remember { mutableStateOf(false) }
    var echoDelayMs by remember { mutableLongStateOf(280L) }
    var echoFeedback by remember { mutableFloatStateOf(0.45f) }
    var echoMix by remember { mutableFloatStateOf(0.35f) }
    var reverbEnabled by remember { mutableStateOf(false) }
    var reverbRoomSize by remember { mutableFloatStateOf(0.65f) }
    var reverbDamping by remember { mutableFloatStateOf(0.40f) }
    var reverbMix by remember { mutableFloatStateOf(0.35f) }
    var noiseReductionEnabled by remember { mutableStateOf(false) }
    var noiseReductionIntensity by remember { mutableFloatStateOf(0.60f) }
    var noiseGateEnabled by remember { mutableStateOf(false) }
    var noiseGateThreshold by remember { mutableFloatStateOf(0.08f) }
    var bassBoost by remember { mutableFloatStateOf(0.0f) }
    var trebleBoost by remember { mutableFloatStateOf(0.0f) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var isReversed by remember { mutableStateOf(false) }
    var studioFxSettings by remember { mutableStateOf(StudioFxSettings()) }

    // Cue Points & Clipboard
    var cuePoints by remember { mutableStateOf(listOf<CuePoint>()) }
    var clipboardPcm by remember { mutableStateOf<ShortArray?>(null) }

    // Modal Visibility Flags
    var showOpenAudioDialog by remember { mutableStateOf(false) }
    var openInNewTab by remember { mutableStateOf(true) }
    var isImportingAudio by remember { mutableStateOf(false) }
    var importingMessage by remember { mutableStateOf("") }
    val importMutex = remember { Mutex() }
    var showBlankProjectDialog by remember { mutableStateOf(false) }
    var showMixAudioDialog by remember { mutableStateOf(false) }
    var pickedMixTitle by remember { mutableStateOf<String?>(null) }
    var pickedMixPcm by remember { mutableStateOf<ShortArray?>(null) }
    var showSilenceDialog by remember { mutableStateOf(false) }
    var showToneDialog by remember { mutableStateOf(false) }
    var showGainDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var currentAudioStats by remember { mutableStateOf<StudioAudioProcessor.AudioStats?>(null) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showCuePointsDialog by remember { mutableStateOf(false) }
    var showVoiceOverDialog by remember { mutableStateOf(false) }
    var showVocalKaraokeDialog by remember { mutableStateOf(false) }
    var pickedVoiceOverTitle by remember { mutableStateOf<String?>(null) }
    var pickedVoiceOverPcm by remember { mutableStateOf<ShortArray?>(null) }
    var pickedVoiceOverDurMs by remember { mutableLongStateOf(0L) }
    var isDecodingVoiceOverFile by remember { mutableStateOf(false) }
    var showEffectsRackDialog by remember { mutableStateOf(false) }
    var effectsCategoryIndex by remember { mutableIntStateOf(0) }
    var showProSuiteDialog by remember { mutableIntStateOf(0) }.let { mutableStateOf(false) }
    var proSuiteCategoryIndex by remember { mutableIntStateOf(0) }
    var isApplyingProFx by remember { mutableStateOf(false) }

    // Mic & Anti-Crackle Recording Parameters
    var showMicAdvancedSettings by remember { mutableStateOf(false) }
    var recordSelectionMode by remember { mutableStateOf(false) }
    var recentOpenedTitles by remember { mutableStateOf(listOf<String>()) }
    var micVolume by remember { mutableFloatStateOf(1.0f) }
    var micAutoGain by remember { mutableStateOf(true) }
    var micBufferSize by remember { mutableIntStateOf(2048) }
    val micAmplitude by recorderManager.amplitude.collectAsState()
    val isTooLoud by recorderManager.isTooLoudWarning.collectAsState()
    val isRecording by recorderManager.isRecording.collectAsState()
    val recordedDurationMs by recorderManager.recordedDurationMs.collectAsState()

    // Export State
    var showExportDialog by remember { mutableStateOf(false) }
    var exportTargetFormat by remember { mutableStateOf(defaultExportFormat) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportedFileResult by remember { mutableStateOf<File?>(null) }

    // Undo / Redo PCM History Stacks
    val undoStack = remember { mutableStateListOf<ShortArray>() }
    val redoStack = remember { mutableStateListOf<ShortArray>() }

    val saveUndoSnapshot: () -> Unit = {
        rawOriginalPcm?.let { current ->
            try {
                val maxTotalSamples = 40_000_000L
                while (undoStack.size >= 6 || (undoStack.isNotEmpty() && undoStack.sumOf { it.size.toLong() } + current.size.toLong() > maxTotalSamples)) {
                    undoStack.removeAt(0)
                }
                if (current.size.toLong() <= maxTotalSamples) {
                    undoStack.add(current.clone())
                }
                redoStack.clear()
            } catch (_: OutOfMemoryError) {
                undoStack.clear()
                redoStack.clear()
            }
        }
    }

    fun syncActiveProjectTab() {
        val currentPcm = rawOriginalPcm ?: return
        if (activeProjectIndex in openProjects.indices) {
            val old = openProjects[activeProjectIndex]
            openProjects[activeProjectIndex] = old.copy(
                title = mainTrackTitle,
                baseFileName = openedFileBaseName,
                pcm = currentPcm,
                sampleRate = playerManager.sampleRate.value,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                currentPositionMs = currentPositionMs,
                cuePoints = cuePoints,
                isBlankProject = openedFileBaseName == null && (mainTrackTitle.startsWith("Untitled") || mainTrackTitle.startsWith("Blank")),
                sourceUri = currentTrackSourceUri
            )
        }
        onSyncToProjectHub?.invoke(mainTrackTitle, openedFileBaseName, currentPcm, playerManager.sampleRate.value)
    }

    fun ensurePcmReadyForPlayback(): Boolean {
        val pcm = rawOriginalPcm
        if (pcm == null || pcm.isEmpty()) return false
        val expectedSr = openProjects.getOrNull(activeProjectIndex)?.sampleRate ?: workingSampleRate
        if (!playerManager.hasPcmData()) {
            playerManager.loadPcmData(pcm, expectedSr)
        }
        return playerManager.durationMs.value > 0L
    }

    fun openExportFlow(targetFormat: String) {
        val pcm = rawOriginalPcm
        if (pcm == null || pcm.isEmpty()) {
            Toast.makeText(context, "Track is empty (0:00). Paste, mix, or record audio before saving.", Toast.LENGTH_SHORT).show()
            AccessibilityHelper.announceForAccessibility(context, "Track is empty. Add or record audio before saving.")
            return
        }
        exportedFileResult = null
        exportTargetFormat = targetFormat
        showExportDialog = true
    }

    fun safePlaySelection(loopSegment: Boolean) {
        if (!ensurePcmReadyForPlayback()) {
            Toast.makeText(context, "Audio canvas is empty. Paste, mix, or record audio first.", Toast.LENGTH_SHORT).show()
            AccessibilityHelper.announceForAccessibility(context, "Audio canvas is empty.")
            return
        }
        val activeDur = playerManager.durationMs.value.coerceAtLeast(durationMs)
        val start = trimStartMs.coerceIn(0L, activeDur)
        val rawEnd = trimEndMs.coerceIn(start, activeDur)
        val end = if (rawEnd > start) rawEnd else activeDur
        playerManager.stop()
        playerManager.seekTo(start)
        playerManager.play(startFromMs = start, endLimitMs = end, loopSegment = loopSegment)
    }

    fun setTrimStartSafe(candidateMs: Long) {
        if (durationMs <= 0L) {
            trimStartMs = 0L
            return
        }
        val minGap = minOf(200L, durationMs)
        val maxBound = (trimEndMs.coerceIn(0L, durationMs) - minGap).coerceAtLeast(0L)
        trimStartMs = candidateMs.coerceIn(0L, maxBound)
        syncActiveProjectTab()
        onSyncTrimToProjectHub?.invoke(trimStartMs, trimEndMs)
    }

    fun setTrimEndSafe(candidateMs: Long) {
        if (durationMs <= 0L) {
            trimEndMs = 0L
            return
        }
        val minGap = minOf(200L, durationMs)
        val minBound = (trimStartMs.coerceIn(0L, durationMs) + minGap).coerceAtMost(durationMs)
        trimEndMs = candidateMs.coerceIn(minBound, durationMs)
        syncActiveProjectTab()
        onSyncTrimToProjectHub?.invoke(trimStartMs, trimEndMs)
    }

    fun switchToProjectTab(targetIdx: Int) {
        if (targetIdx !in openProjects.indices || targetIdx == activeProjectIndex) return
        playerManager.stop()
        syncActiveProjectTab()
        val target = openProjects[targetIdx]
        activeProjectIndex = targetIdx
        rawOriginalPcm = target.pcm
        mainTrackTitle = target.title
        openedFileBaseName = target.baseFileName
        currentTrackSourceUri = target.sourceUri
        playerManager.loadPcmData(target.pcm, target.sampleRate)
        val totalMs = playerManager.durationMs.value
        val restoredStart = target.trimStartMs.coerceIn(0L, totalMs)
        val restoredEnd = target.trimEndMs.coerceIn(restoredStart, totalMs)
        trimStartMs = if (totalMs > 0L && restoredEnd <= restoredStart) 0L else restoredStart
        trimEndMs = if (totalMs > 0L && restoredEnd <= restoredStart) totalMs else restoredEnd
        cuePoints = target.cuePoints
        undoStack.clear()
        redoStack.clear()
        viewHistoryStack.clear()
        resetViewToAll(false)
        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
        AccessibilityHelper.announceForAccessibility(
            context,
            "Switched to ${target.title}, duration ${AccessibilityHelper.formatTimeSpoken(totalMs)}"
        )
    }

    fun closeProjectTab(closeIdx: Int) {
        if (openProjects.size <= 1 || closeIdx !in openProjects.indices) return
        playerManager.stop()
        syncActiveProjectTab()
        val removed = openProjects.removeAt(closeIdx)
        val newActiveIdx = when {
            activeProjectIndex == closeIdx -> closeIdx.coerceAtMost(openProjects.lastIndex)
            activeProjectIndex > closeIdx -> activeProjectIndex - 1
            else -> activeProjectIndex
        }
        val target = openProjects[newActiveIdx]
        activeProjectIndex = newActiveIdx
        rawOriginalPcm = target.pcm
        mainTrackTitle = target.title
        openedFileBaseName = target.baseFileName
        currentTrackSourceUri = target.sourceUri
        playerManager.loadPcmData(target.pcm, target.sampleRate)
        val totalMs = playerManager.durationMs.value
        val restoredStart = target.trimStartMs.coerceIn(0L, totalMs)
        val restoredEnd = target.trimEndMs.coerceIn(restoredStart, totalMs)
        trimStartMs = if (totalMs > 0L && restoredEnd <= restoredStart) 0L else restoredStart
        trimEndMs = if (totalMs > 0L && restoredEnd <= restoredStart) totalMs else restoredEnd
        cuePoints = target.cuePoints
        undoStack.clear()
        redoStack.clear()
        viewHistoryStack.clear()
        resetViewToAll(false)
        AccessibilityHelper.announceForAccessibility(context, "Closed ${removed.title}. Active track: ${target.title}")
    }

    val runProPcmTransform: (String, Boolean, Boolean, (ShortArray) -> ShortArray) -> Unit = { announcement, resetTrimStart, preserveSelection, transform ->
        val current = rawOriginalPcm
        if (current != null && !isApplyingProFx) {
            isApplyingProFx = true
            val prevPos = currentPositionMs
            val prevTrimEnd = trimEndMs
            scope.launch {
                try {
                    playerManager.stop()
                    saveUndoSnapshot()
                    val updated = withContext(Dispatchers.Default) { transform(current) }
                    rawOriginalPcm = updated
                    playerManager.loadPcmData(updated, workingSampleRate)
                    val newDurMs = playerManager.durationMs.value
                    trimStartMs = if (resetTrimStart || updated.isEmpty()) 0L else trimStartMs.coerceIn(0L, newDurMs)
                    trimEndMs = when {
                        updated.isEmpty() -> 0L
                        preserveSelection -> prevTrimEnd.coerceIn(trimStartMs, newDurMs)
                        else -> newDurMs
                    }
                    if (preserveSelection && prevPos in 1L..newDurMs) {
                        playerManager.seekTo(prevPos)
                    }
                    syncActiveProjectTab()
                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                    AccessibilityHelper.announceForAccessibility(context, announcement)
                    Toast.makeText(context, announcement, Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Toast.makeText(
                        context,
                        "Effect error: ${t.localizedMessage ?: "Not enough memory for this track"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    isApplyingProFx = false
                }
            }
        }
    }

    fun applyTransform(
        announcement: String,
        resetTrimStart: Boolean = false,
        preserveSelection: Boolean = false,
        transform: (ShortArray) -> ShortArray
    ) {
        runProPcmTransform(announcement, resetTrimStart, preserveSelection, transform)
    }

    fun currentSettings(): AudioEditSettings {
        return AudioEditSettings(
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            masterVolume = masterVolume,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            speedMultiplier = speedMultiplier,
            eqPreset = selectedEq,
            echoEnabled = echoEnabled,
            echoDelayMs = echoDelayMs,
            echoFeedback = echoFeedback,
            echoMix = echoMix,
            reverbEnabled = reverbEnabled,
            reverbRoomSize = reverbRoomSize,
            reverbDamping = reverbDamping,
            reverbMix = reverbMix,
            noiseReductionEnabled = noiseReductionEnabled,
            noiseReductionIntensity = noiseReductionIntensity,
            noiseGateEnabled = noiseGateEnabled,
            noiseGateThreshold = noiseGateThreshold,
            bassBoost = bassBoost,
            trebleBoost = trebleBoost,
            isReversed = isReversed,
            studioFx = studioFxSettings
        )
    }

    fun applyDspAndAudition() {
        val orig = rawOriginalPcm
        if (orig == null || orig.isEmpty()) {
            Toast.makeText(context, "Track is empty. Add or record audio to audition effects.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isApplyingProFx) return
        val snapshotSettings = currentSettings()
        isApplyingProFx = true
        scope.launch {
            try {
                playerManager.stop()
                val processed = withContext(Dispatchers.Default) {
                    audioProcessor.processPcm(orig, snapshotSettings)
                }
                if (processed.isNotEmpty()) {
                    playerManager.loadPcmData(processed, workingSampleRate)
                    playerManager.play()
                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                    AccessibilityHelper.announceForAccessibility(context, "Applied live effects preview. Playing updated audio.")
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Toast.makeText(
                    context,
                    "Preview error: ${t.localizedMessage ?: "Audio processing failed"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isApplyingProFx = false
            }
        }
    }

    fun resetToEmptyProject() {
        playerManager.stop()
        playerManager.seekTo(0L)
        val blank = ShortArray(5 * StudioAudioProcessor.SAMPLE_RATE)
        rawOriginalPcm = blank
        mainTrackTitle = "Untitled Project"
        openedFileBaseName = null
        currentTrackSourceUri = null
        playerManager.loadPcmData(blank, StudioAudioProcessor.SAMPLE_RATE)
        trimStartMs = 0L
        trimEndMs = 5000L
        viewHistoryStack.clear()
        resetViewToAll(false)
        cuePoints = emptyList()
        undoStack.clear()
        redoStack.clear()
        syncActiveProjectTab()
        AccessibilityHelper.announceForAccessibility(context, "Reset workspace to empty untitled project")
    }

    fun loadImportedAudioUris(uris: List<Uri>, explicitTitle: String? = null, forceNewTab: Boolean? = null) {
        if (uris.isEmpty()) return
        scope.launch {
            importMutex.withLock {
                isImportingAudio = true
                var importedCount = 0
                var lastDecodedName = ""
                var lastDurMs = 0L
                try {
                    playerManager.stop()
                    uris.forEachIndexed { index, uri ->
                        val fallbackTitle = if (uris.size == 1) explicitTitle else null
                        val nameHint = fallbackTitle?.takeIf { it.isNotBlank() }
                            ?: audioProcessor.queryAudioDisplayName(uri)
                            ?: "Imported Audio ${index + 1}"
                        importingMessage = if (uris.size > 1) {
                            "Decoding file ${index + 1} of ${uris.size}: $nameHint..."
                        } else {
                            "Decoding $nameHint..."
                        }

                        val pcm = audioProcessor.decodeUriToPcm(uri)
                        if (pcm.isNotEmpty()) {
                            val resolvedBase = StudioPreferences.extractBaseFileName(nameHint)
                                .takeIf { it.isNotBlank() }
                            val activeTab = openProjects.getOrNull(activeProjectIndex)
                            val isStarterBlankUntouched = activeTab != null &&
                                activeTab.isBlankProject &&
                                openedFileBaseName == null &&
                                undoStack.isEmpty()
                            val shouldOpenNewTab = (forceNewTab ?: openInNewTab) && !isStarterBlankUntouched

                            val decodedRate = StudioAudioProcessor.SAMPLE_RATE
                            val durMs = ((pcm.size.toDouble() / decodedRate) * 1000.0).toLong()

                            if (shouldOpenNewTab) {
                                syncActiveProjectTab()
                                val newTab = AudioProjectTab(
                                    title = nameHint,
                                    baseFileName = resolvedBase,
                                    pcm = pcm,
                                    sampleRate = decodedRate,
                                    trimStartMs = 0L,
                                    trimEndMs = durMs,
                                    currentPositionMs = 0L,
                                    cuePoints = emptyList(),
                                    isBlankProject = false,
                                    sourceUri = uri
                                )
                                openProjects.add(newTab)
                                activeProjectIndex = openProjects.lastIndex
                                undoStack.clear()
                                redoStack.clear()
                            } else if (!isStarterBlankUntouched) {
                                saveUndoSnapshot()
                            }

                            rawOriginalPcm = pcm
                            mainTrackTitle = nameHint
                            openedFileBaseName = resolvedBase
                            currentTrackSourceUri = uri
                            cuePoints = emptyList()
                            recentOpenedTitles = (listOf(nameHint) + recentOpenedTitles).distinct().take(6)
                            playerManager.loadPcmData(pcm, decodedRate)
                            val loadedDurMs = playerManager.durationMs.value
                            trimStartMs = 0L
                            trimEndMs = loadedDurMs
                            viewHistoryStack.clear()
                            resetViewToAll(false)
                            syncActiveProjectTab()
                            importedCount++
                            lastDecodedName = nameHint
                            lastDurMs = loadedDurMs
                        }
                    }

                    if (importedCount > 0) {
                        showOpenAudioDialog = false
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        val msg = if (importedCount > 1) {
                            "Opened $importedCount audio files in tabs. Active: $lastDecodedName"
                        } else {
                            "$lastDecodedName imported (${AccessibilityHelper.formatTimeVisual(lastDurMs)}). Ready to play."
                        }
                        AccessibilityHelper.announceForAccessibility(context, msg)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not decode selected audio file(s).", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    Toast.makeText(context, "Audio import error: ${e.localizedMessage ?: "File cannot be opened"}", Toast.LENGTH_LONG).show()
                } finally {
                    isImportingAudio = false
                    importingMessage = ""
                }
            }
        }
    }

    fun loadImportedAudioUri(uri: Uri, explicitTitle: String? = null, forceNewTab: Boolean? = null) {
        loadImportedAudioUris(listOf(uri), explicitTitle = explicitTitle, forceNewTab = forceNewTab)
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadImportedAudioUri(uri)
    }

    val osDocumentAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            loadImportedAudioUri(uri)
        }
    }

    val multiAudioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
            }
            loadImportedAudioUris(uris)
        }
    }

    val mixFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val pcm = audioProcessor.decodeUriToPcm(uri)
                    if (pcm.isNotEmpty()) {
                        val name = audioProcessor.queryAudioDisplayName(uri) ?: "Second Audio File"
                        pickedMixTitle = name
                        pickedMixPcm = pcm
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Loaded $name ready to mix")
                    } else {
                        Toast.makeText(context, "Could not decode selected mix file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    Toast.makeText(context, "Mix file decode error: ${e.localizedMessage ?: "Cannot read file"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val voiceOverFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            isDecodingVoiceOverFile = true
            scope.launch {
                try {
                    val pcm = audioProcessor.decodeUriToPcm(uri)
                    if (pcm.isNotEmpty()) {
                        val name = audioProcessor.queryAudioDisplayName(uri) ?: "Music Track"
                        pickedVoiceOverTitle = name
                        pickedVoiceOverPcm = pcm
                        pickedVoiceOverDurMs = ((pcm.size.toDouble() / workingSampleRate.coerceAtLeast(8000)) * 1000.0).toLong()
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Added $name to Voice Over")
                    } else {
                        Toast.makeText(context, "Could not decode selected audio file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Throwable) {
                    Toast.makeText(context, "Voice Over audio error: ${e.localizedMessage ?: "Cannot read file"}", Toast.LENGTH_LONG).show()
                } finally {
                    isDecodingVoiceOverFile = false
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recorderManager.bufferSizeOption = micBufferSize
            recorderManager.autoGainEnabled = micAutoGain
            recorderManager.micGainVolume = micVolume
            recorderManager.startRecording(
                onSuccess = {
                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.HEAVY_CLICK)
                    AccessibilityHelper.announceForAccessibility(context, "Voice recording started.")
                },
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    fun startMicRecording(selectionMode: Boolean) {
        recordSelectionMode = selectionMode
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recorderManager.bufferSizeOption = micBufferSize
            recorderManager.autoGainEnabled = micAutoGain
            recorderManager.micGainVolume = micVolume
            recorderManager.startRecording(
                onSuccess = {
                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.HEAVY_CLICK)
                    AccessibilityHelper.announceForAccessibility(context, if (selectionMode) "Record Selection punch-in started" else "Record New started")
                },
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopMicRecording() {
        val recordedPcm = recorderManager.stopRecording()
        if (recordedPcm != null && recordedPcm.isNotEmpty()) {
            val cleanedPcm = StudioAudioProcessor.gentleCleanup(recordedPcm)
            val existing = rawOriginalPcm
            if (recordSelectionMode && existing != null && trimEndMs > trimStartMs) {
                saveUndoSnapshot()
                val sIdx = ((trimStartMs.toDouble() / 1000.0) * workingSampleRate).toInt().coerceIn(0, existing.size)
                val eIdx = ((trimEndMs.toDouble() / 1000.0) * workingSampleRate).toInt().coerceIn(sIdx, existing.size)
                val merged = ShortArray(sIdx + cleanedPcm.size + (existing.size - eIdx))
                System.arraycopy(existing, 0, merged, 0, sIdx)
                System.arraycopy(cleanedPcm, 0, merged, sIdx, cleanedPcm.size)
                System.arraycopy(existing, eIdx, merged, sIdx + cleanedPcm.size, existing.size - eIdx)
                rawOriginalPcm = merged
                playerManager.loadPcmData(merged, StudioAudioProcessor.SAMPLE_RATE)
                val newDur = playerManager.durationMs.value
                trimStartMs = trimStartMs.coerceIn(0L, newDur)
                trimEndMs = newDur
                syncActiveProjectTab()
                AccessibilityHelper.announceForAccessibility(context, "Recorded selection spliced into track.")
            } else {
                saveUndoSnapshot()
                rawOriginalPcm = cleanedPcm
                mainTrackTitle = "Recording (${AccessibilityHelper.formatTimeVisual(recordedDurationMs)})"
                openedFileBaseName = null
                playerManager.loadPcmData(cleanedPcm, StudioAudioProcessor.SAMPLE_RATE)
                trimStartMs = 0L
                trimEndMs = playerManager.durationMs.value
                syncActiveProjectTab()
                AccessibilityHelper.announceForAccessibility(context, "Recorded audio cleanly.")
            }
            recordSelectionMode = false
            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
        }
    }

    // Handle Top Dropdown Menu Commands
    LaunchedEffect(pendingCommand) {
        val cmd = pendingCommand ?: return@LaunchedEffect
        val current = rawOriginalPcm
        when (cmd) {
            AudioStudioCommand.NewBlankProject -> showBlankProjectDialog = true
            AudioStudioCommand.OpenAudioModal -> showOpenAudioDialog = true
            AudioStudioCommand.SaveWav -> openExportFlow(defaultExportFormat)
            AudioStudioCommand.SaveAsMp3 -> {
                openExportFlow(if (defaultExportFormat == "WAV") "MP3" else defaultExportFormat)
            }
            AudioStudioCommand.ExportFile -> openExportFlow(defaultExportFormat)
            AudioStudioCommand.OpenProperties -> showPropertiesDialog = true
            AudioStudioCommand.CloseProject -> resetToEmptyProject()

            AudioStudioCommand.Undo -> {
                if (undoStack.isNotEmpty()) {
                    val prev = undoStack.removeAt(undoStack.size - 1)
                    try {
                        current?.let { redoStack.add(it.clone()) }
                    } catch (_: OutOfMemoryError) {
                        redoStack.clear()
                    }
                    rawOriginalPcm = prev
                    playerManager.loadPcmData(prev, workingSampleRate)
                    val newDurMs = playerManager.durationMs.value
                    trimStartMs = if (prev.isEmpty()) 0L else trimStartMs.coerceIn(0L, newDurMs)
                    trimEndMs = if (prev.isEmpty()) 0L else newDurMs
                    syncActiveProjectTab()
                    AccessibilityHelper.announceForAccessibility(context, "Undid last edit")
                }
            }
            AudioStudioCommand.Redo -> {
                if (redoStack.isNotEmpty()) {
                    val next = redoStack.removeAt(redoStack.size - 1)
                    try {
                        current?.let { undoStack.add(it.clone()) }
                    } catch (_: OutOfMemoryError) {
                        undoStack.clear()
                    }
                    rawOriginalPcm = next
                    playerManager.loadPcmData(next, workingSampleRate)
                    val newDurMs = playerManager.durationMs.value
                    trimStartMs = if (next.isEmpty()) 0L else trimStartMs.coerceIn(0L, newDurMs)
                    trimEndMs = if (next.isEmpty()) 0L else newDurMs
                    syncActiveProjectTab()
                    AccessibilityHelper.announceForAccessibility(context, "Redid edit")
                }
            }
            AudioStudioCommand.CutSelection -> {
                if (current != null && current.isNotEmpty() && trimEndMs > trimStartMs) {
                    val sMs = trimStartMs
                    val eMs = trimEndMs
                    applyTransform("Cut selection to clipboard") { pcm ->
                        val cutClip = StudioAudioProcessor.cropToSelection(pcm, sMs, eMs)
                        if (cutClip.isNotEmpty()) clipboardPcm = cutClip
                        StudioAudioProcessor.deleteSelection(pcm, sMs, eMs)
                    }
                } else {
                    Toast.makeText(context, "Select audio range first (or tap Select All).", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.CopySelection -> {
                if (current != null && current.isNotEmpty() && trimEndMs > trimStartMs) {
                    val copied = StudioAudioProcessor.cropToSelection(current, trimStartMs, trimEndMs)
                    if (copied.isNotEmpty()) {
                        clipboardPcm = copied
                        val sec = copied.size.toFloat() / workingSampleRate
                        val msg = "Copied %.1f seconds to clipboard".format(sec)
                        AccessibilityHelper.announceForAccessibility(context, msg)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Select audio range first (or tap Select All).", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.MuteSelection -> {
                if (current != null && current.isNotEmpty() && trimEndMs > trimStartMs) {
                    val start = trimStartMs
                    val end = trimEndMs
                    applyTransform(
                        announcement = "Muted selection (${AccessibilityHelper.formatTimeVisual(start)} to ${AccessibilityHelper.formatTimeVisual(end)})",
                        preserveSelection = true
                    ) { pcm ->
                        StudioAudioProcessor.muteSelection(pcm, start, end, workingSampleRate)
                    }
                } else {
                    Toast.makeText(context, "Select audio range between Start and End markers first.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.DeleteSelection -> {
                if (current != null && current.isNotEmpty() && trimEndMs > trimStartMs) {
                    val startToDelete = trimStartMs
                    val endToDelete = trimEndMs
                    val deletingEntireTrack = startToDelete <= 50L && endToDelete >= (durationMs - 50L)
                    applyTransform(
                        if (deletingEntireTrack) {
                            "Deleted entire track. Canvas is now empty (0:00) and ready to paste."
                        } else {
                            "Deleted selection from track"
                        }
                    ) { pcm ->
                        if (deletingEntireTrack) {
                            ShortArray(0)
                        } else {
                            StudioAudioProcessor.deleteSelection(pcm, startToDelete, endToDelete)
                        }
                    }
                } else {
                    Toast.makeText(context, "Select audio range first (or tap Select All).", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.PasteAtCursor -> {
                val clip = clipboardPcm
                if (current != null && clip != null && clip.isNotEmpty()) {
                    applyTransform("Pasted clipboard audio at cursor") { pcm ->
                        val pos = if (pcm.isEmpty()) 0 else ((currentPositionMs.toDouble() / 1000.0) * workingSampleRate).toInt().coerceIn(0, pcm.size)
                        ShortArray(pcm.size + clip.size).also { out ->
                            System.arraycopy(pcm, 0, out, 0, pos)
                            System.arraycopy(clip, 0, out, pos, clip.size)
                            System.arraycopy(pcm, pos, out, pos + clip.size, pcm.size - pos)
                        }
                    }
                } else {
                    Toast.makeText(context, "Clipboard is empty. Copy or Cut audio first.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.PasteAtEnd -> {
                val clip = clipboardPcm
                if (current != null && clip != null && clip.isNotEmpty()) {
                    applyTransform("Pasted clipboard at end") { pcm ->
                        ShortArray(pcm.size + clip.size).also { out ->
                            System.arraycopy(pcm, 0, out, 0, pcm.size)
                            System.arraycopy(clip, 0, out, pcm.size, clip.size)
                        }
                    }
                } else {
                    Toast.makeText(context, "Clipboard is empty. Copy or Cut audio first.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.MixPaste -> {
                val clip = clipboardPcm
                if (current != null && clip != null && clip.isNotEmpty()) {
                    applyTransform("Mix pasted clipboard at cursor") { pcm ->
                        StudioAudioProcessor.mixAudioTracks(
                            main = pcm,
                            overlay = clip,
                            startMs = currentPositionMs,
                            mainVolume = 1.0f,
                            overlayVolume = 0.85f,
                            applyPeakLimiter = true,
                            sampleRate = workingSampleRate
                        )
                    }
                } else {
                    Toast.makeText(context, "Clipboard is empty. Copy or Cut audio first.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.OpenMixAudioDialog -> showMixAudioDialog = true
            AudioStudioCommand.OpenSilenceDialog -> showSilenceDialog = true
            AudioStudioCommand.SelectAll -> {
                trimStartMs = 0L; trimEndMs = durationMs
                syncActiveProjectTab()
                onSyncTrimToProjectHub?.invoke(trimStartMs, trimEndMs)
                AccessibilityHelper.announceForAccessibility(context, "Selected entire track")
            }

            AudioStudioCommand.SetSelStart -> {
                setTrimStartSafe(currentPositionMs)
                AccessibilityHelper.announceForAccessibility(context, "Trim start set")
            }
            AudioStudioCommand.SetSelEnd -> {
                setTrimEndSafe(currentPositionMs)
                AccessibilityHelper.announceForAccessibility(context, "Trim end set")
            }
            AudioStudioCommand.CropSelection -> {
                if (current != null && current.isNotEmpty() && trimEndMs > trimStartMs) {
                    val start = trimStartMs
                    val end = trimEndMs
                    applyTransform("Cropped audio to selection", resetTrimStart = true) { pcm ->
                        StudioAudioProcessor.cropToSelection(pcm, start, end)
                    }
                } else {
                    Toast.makeText(context, "Select audio range first.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.OpenCuePointsDialog -> showCuePointsDialog = true

            // Repair / Cleaning
            AudioStudioCommand.RemoveHum60Hz -> applyTransform("Removed 60 Hz mains hum") { StudioAudioProcessor.removeMainsHum(it, false) }
            AudioStudioCommand.HighPassFilter -> applyTransform("Applied High-Pass filter (300 Hz)") { StudioAudioProcessor.applyHighPassFilter(it, 300f) }
            AudioStudioCommand.LowPassFilter -> applyTransform("Applied Low-Pass filter (3 kHz)") { StudioAudioProcessor.applyLowPassFilter(it, 3000f) }
            AudioStudioCommand.BandPassFilter -> applyTransform("Applied Band-Pass voice filter") { StudioAudioProcessor.applyBandPassFilter(it, 1800f, 3000f) }
            AudioStudioCommand.RemoveDcOffset -> applyTransform("Removed DC Offset") { StudioAudioProcessor.removeDcOffset(it) }
            AudioStudioCommand.DeClick -> applyTransform("Applied De-Click filter") { pcm ->
                StudioAudioProcessor.applyTruePeakLimiter(StudioAudioProcessor.deClickDeCrackle(pcm), -1.0f)
            }

            // Dynamics
            AudioStudioCommand.ApplyAutoGain -> applyTransform("Applied Auto Gain leveling") { StudioAudioProcessor.applyAutoGain(it) }
            AudioStudioCommand.ApplyCompressor -> applyTransform("Applied Dynamics Compressor") { pcm ->
                val compressed = StudioAudioProcessor.applyCompressor(
                    pcm,
                    studioFxSettings.compressorThresholdDb,
                    studioFxSettings.compressorRatio,
                    studioFxSettings.compressorMakeupGainDb,
                    peakLimiter = false
                )
                if (studioFxSettings.peakLimiterEnabled) {
                    StudioAudioProcessor.applyTruePeakLimiter(compressed, studioFxSettings.peakLimiterCeilingDb)
                } else {
                    compressed
                }
            }
            AudioStudioCommand.ApplyExpander -> applyTransform("Applied Dynamic Expander") { StudioAudioProcessor.applyExpander(it, -28f, 2.0f) }
            AudioStudioCommand.NormalizePeak -> applyTransform("Normalized Peak (-0.1 dB)") { StudioAudioProcessor.normalize(it) }
            AudioStudioCommand.NormalizeRms -> applyTransform("Normalized RMS (-16 dBFS)") { StudioAudioProcessor.normalizeRms(it, -16f) }
            AudioStudioCommand.FadeIn -> applyTransform("Applied Fade In to selection") { StudioAudioProcessor.fadeInSelection(it, trimStartMs, trimEndMs) }
            AudioStudioCommand.FadeOut -> applyTransform("Applied Fade Out to selection") { StudioAudioProcessor.fadeOutSelection(it, trimStartMs, trimEndMs) }
            AudioStudioCommand.OpenGainDialog -> showGainDialog = true

            // Creative DSP
            AudioStudioCommand.ApplyMechanize -> applyTransform("Applied Mechanize robot ring mod") { StudioAudioProcessor.applyMechanize(it, 120f, 0.85f) }
            AudioStudioCommand.ReverseTrack -> applyTransform("Reversed audio track") { StudioAudioProcessor.reverseAudio(it) }

            // View (Time Zoom, Selection, Specify, Previous, Preset, Follow Cursor, Go To, Amplitude Zoom)
            AudioStudioCommand.ViewAll,
            AudioStudioCommand.ZoomReset -> {
                resetViewToAll(true)
                AccessibilityHelper.announceForAccessibility(context, "Viewing entire track")
            }
            AudioStudioCommand.ViewSelection -> {
                if (trimEndMs > trimStartMs && durationMs > 0L) {
                    pushViewHistory()
                    val selSpan = (trimEndMs - trimStartMs).coerceIn(50L, durationMs)
                    viewWindowStartMs = trimStartMs.coerceIn(0L, (durationMs - selSpan).coerceAtLeast(0L))
                    viewWindowSpanMs = if (selSpan >= durationMs) null else selSpan
                    AccessibilityHelper.announceForAccessibility(context, "Zoomed to selection")
                } else {
                    Toast.makeText(context, "Select an audio range first.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.OpenSpecifyViewDialog -> {
                showSpecifyViewDialog = true
            }
            AudioStudioCommand.ViewPrevious -> {
                if (viewHistoryStack.isNotEmpty()) {
                    val prev = viewHistoryStack.removeAt(viewHistoryStack.lastIndex)
                    viewWindowStartMs = prev.startMs
                    viewWindowSpanMs = prev.spanMs
                    verticalZoom = prev.verticalZoom
                    AccessibilityHelper.announceForAccessibility(context, "Restored previous view")
                } else {
                    Toast.makeText(context, "No previous view in history.", Toast.LENGTH_SHORT).show()
                }
            }
            AudioStudioCommand.ViewPresetDefault -> {
                val safeDur = durationMs.coerceAtLeast(100L)
                applyTimeSpanWindow(minOf(10_000L, safeDur), currentPositionMs)
                AccessibilityHelper.announceForAccessibility(context, "Applied default 10 second preset view")
            }
            AudioStudioCommand.ToggleFollowCursor -> {
                val stateLabel = if (followCursor) "Follow Cursor enabled" else "Follow Cursor paused"
                AccessibilityHelper.announceForAccessibility(context, stateLabel)
                Toast.makeText(context, stateLabel, Toast.LENGTH_SHORT).show()
            }
            AudioStudioCommand.ZoomIn -> {
                val resolved = WaveformViewportMath.resolveWindow(durationMs, viewWindowStartMs, viewWindowSpanMs, currentPositionMs, followCursor)
                applyTimeSpanWindow((resolved.spanMs / 2L).coerceAtLeast(50L), currentPositionMs)
                AccessibilityHelper.announceForAccessibility(context, "Zoomed in")
            }
            AudioStudioCommand.ZoomOut -> {
                val safeDur = durationMs.coerceAtLeast(100L)
                val resolved = WaveformViewportMath.resolveWindow(safeDur, viewWindowStartMs, viewWindowSpanMs, currentPositionMs, followCursor)
                val nextSpan = resolved.spanMs * 2L
                if (nextSpan >= safeDur) {
                    resetViewToAll(true)
                    AccessibilityHelper.announceForAccessibility(context, "Zoomed out to fit all")
                } else {
                    applyTimeSpanWindow(nextSpan, currentPositionMs)
                    AccessibilityHelper.announceForAccessibility(context, "Zoomed out")
                }
            }
            AudioStudioCommand.Zoom10To1 -> {
                val safeDur = durationMs.coerceAtLeast(100L)
                applyTimeSpanWindow((safeDur / 10L).coerceAtLeast(100L), currentPositionMs)
                AccessibilityHelper.announceForAccessibility(context, "Time zoom 10 to 1")
            }
            AudioStudioCommand.Zoom1To1 -> {
                val safeDur = durationMs.coerceAtLeast(100L)
                applyTimeSpanWindow(minOf(1_000L, safeDur), currentPositionMs)
                AccessibilityHelper.announceForAccessibility(context, "Time zoom 1 to 1")
            }
            is AudioStudioCommand.ViewTimeSpan -> {
                val safeDur = durationMs.coerceAtLeast(100L)
                if (cmd.spanMs >= safeDur) {
                    resetViewToAll(true)
                } else {
                    applyTimeSpanWindow(cmd.spanMs, currentPositionMs)
                }
                AccessibilityHelper.announceForAccessibility(
                    context,
                    "View span set to ${WaveformViewportMath.formatWindowLabel(minOf(cmd.spanMs, safeDur), safeDur)}"
                )
            }
            AudioStudioCommand.GoToSelectionStart -> {
                val target = trimStartMs.coerceIn(0L, durationMs)
                playerManager.seekTo(target)
                viewWindowSpanMs?.let { span ->
                    viewWindowStartMs = (target - span / 2L).coerceIn(0L, (durationMs - span).coerceAtLeast(0L))
                }
                AccessibilityHelper.announceForAccessibility(context, "Moved cursor to selection start ${AccessibilityHelper.formatTimeSpoken(target)}")
            }
            AudioStudioCommand.GoToSelectionFinish -> {
                val target = trimEndMs.coerceIn(0L, durationMs)
                playerManager.seekTo(target)
                viewWindowSpanMs?.let { span ->
                    viewWindowStartMs = (target - span / 2L).coerceIn(0L, (durationMs - span).coerceAtLeast(0L))
                }
                AccessibilityHelper.announceForAccessibility(context, "Moved cursor to selection finish ${AccessibilityHelper.formatTimeSpoken(target)}")
            }
            AudioStudioCommand.GoToBeginning -> {
                playerManager.seekTo(0L)
                viewWindowStartMs = 0L
                AccessibilityHelper.announceForAccessibility(context, "Moved cursor to beginning of track")
            }
            AudioStudioCommand.GoToEnd -> {
                playerManager.seekTo(durationMs)
                viewWindowSpanMs?.let { span ->
                    viewWindowStartMs = (durationMs - span).coerceAtLeast(0L)
                }
                AccessibilityHelper.announceForAccessibility(context, "Moved cursor to end of track")
            }
            AudioStudioCommand.VerticalZoomIn -> {
                pushViewHistory()
                verticalZoom = (verticalZoom * 1.5f).coerceAtMost(8.0f)
                AccessibilityHelper.announceForAccessibility(context, "Amplitude vertical zoom ${"%.1f".format(verticalZoom)}x")
            }
            AudioStudioCommand.VerticalZoomOut -> {
                pushViewHistory()
                verticalZoom = (verticalZoom / 1.5f).coerceAtLeast(0.25f)
                AccessibilityHelper.announceForAccessibility(context, "Amplitude vertical zoom ${"%.1f".format(verticalZoom)}x")
            }
            AudioStudioCommand.VerticalZoomAll -> {
                pushViewHistory()
                verticalZoom = 1.0f
                AccessibilityHelper.announceForAccessibility(context, "Amplitude vertical zoom reset to 1.0x")
            }
            AudioStudioCommand.ToggleVisualMode -> {
                val modes = AudioVisualMode.values()
                visualMode = modes[(visualMode.ordinal + 1) % modes.size]
                AccessibilityHelper.announceForAccessibility(context, "Visual display switched to ${visualMode.displayName}")
            }

            // Tools & Modals
            AudioStudioCommand.OpenVoiceOverDialog -> showVoiceOverDialog = true
            AudioStudioCommand.OpenVocalKaraokeDialog -> showVocalKaraokeDialog = true
            AudioStudioCommand.OpenToneGenerator -> showToneDialog = true
            AudioStudioCommand.OpenStatsDialog -> {
                current?.let {
                    currentAudioStats = StudioAudioProcessor.computeStatistics(it)
                    showStatsDialog = true
                }
            }
            is AudioStudioCommand.OpenEffectsTab -> {
                effectsCategoryIndex = cmd.tabIndex.coerceIn(0, 4)
                showEffectsRackDialog = true
            }
            is AudioStudioCommand.OpenProSuiteTab -> {
                proSuiteCategoryIndex = cmd.tabIndex.coerceIn(0, 3)
                showProSuiteDialog = true
            }

            // Transport
            AudioStudioCommand.Play1Selection -> safePlaySelection(loopSegment = false)
            AudioStudioCommand.Play2Continuous -> {
                if (!ensurePcmReadyForPlayback()) {
                    Toast.makeText(context, "Audio canvas is empty. Paste, mix, or record audio first.", Toast.LENGTH_SHORT).show()
                } else {
                    playerManager.play(startFromMs = currentPositionMs, endLimitMs = null)
                }
            }
            AudioStudioCommand.Play3Loop -> safePlaySelection(loopSegment = true)
            AudioStudioCommand.StopPlayback -> {
                playerManager.stop(); playerManager.seekTo(0L)
            }
            AudioStudioCommand.PlayPause -> {
                if (isPlaying) {
                    playerManager.pause()
                } else if (!ensurePcmReadyForPlayback()) {
                    Toast.makeText(context, "Audio canvas is empty. Paste, mix, or record audio first.", Toast.LENGTH_SHORT).show()
                } else {
                    playerManager.play()
                }
            }
            AudioStudioCommand.Rewind -> {
                if (current == null || current.isEmpty() || durationMs <= 0L) {
                    Toast.makeText(context, "Audio canvas is empty.", Toast.LENGTH_SHORT).show()
                } else {
                    playerManager.toggleScrub(ScrubDirection.REWIND)
                }
            }
            AudioStudioCommand.FastForward -> {
                if (current == null || current.isEmpty() || durationMs <= 0L) {
                    Toast.makeText(context, "Audio canvas is empty.", Toast.LENGTH_SHORT).show()
                } else {
                    playerManager.toggleScrub(ScrubDirection.FORWARD)
                }
            }
            AudioStudioCommand.RecordNew -> startMicRecording(selectionMode = false)
            AudioStudioCommand.RecordSelection -> startMicRecording(selectionMode = true)
        }
        onCommandHandled()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. MINIMAL DAW STATUS HEADER (Track Title + Playhead + Duration + Sample Rate)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audio_track_header_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = mainTrackTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { showOpenAudioDialog = true },
                            modifier = Modifier.size(48.dp).testTag("btn_header_open_audio")
                                .semantics { contentDescription = "Open or import audio file" }
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { openExportFlow(defaultExportFormat) },
                            modifier = Modifier.size(48.dp).testTag("btn_header_save_audio")
                                .semantics { contentDescription = "Save or export audio" }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Compact Minimal Status Indicators: Playhead | Duration | Sample Rate | Window/Zoom
                val currentViewport = WaveformViewportMath.resolveWindow(
                    durationMs = durationMs,
                    viewStartMs = viewWindowStartMs,
                    viewSpanMs = viewWindowSpanMs,
                    currentMs = currentPositionMs,
                    followCursor = followCursor
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("time_readout_card")
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Playhead ${AccessibilityHelper.formatTimeSpoken(currentPositionMs)} of ${AccessibilityHelper.formatTimeSpoken(durationMs)}, sample rate $workingSampleRate Hertz"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusMetricCell("PLAYHEAD", AccessibilityHelper.formatTimeVisual(currentPositionMs), highlight = true)
                    StatusMetricCell("DURATION", AccessibilityHelper.formatTimeVisual(durationMs), highlight = false)
                    StatusMetricCell("SAMPLE RATE", "${workingSampleRate / 1000.0} kHz", highlight = false)
                    StatusMetricCell("ZOOM", WaveformViewportMath.formatWindowLabel(currentViewport.spanMs, durationMs), highlight = false)
                }
            }
        }

        // Open Project Tabs & Quick Blank/Mix Actions
        if (openProjects.isNotEmpty()) {
            OpenProjectsTabBar(
                projects = openProjects,
                activeIndex = activeProjectIndex,
                onSelectProject = { idx -> switchToProjectTab(idx) },
                onCloseProject = { idx -> closeProjectTab(idx) },
                onNewBlankProject = { showBlankProjectDialog = true },
                onOpenMixDialog = { showMixAudioDialog = true }
            )
        }

        AnimatedVisibility(visible = isImportingAudio) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("card_studio_importing_status"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = importingMessage.ifBlank { "Importing and decoding audio..." },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 2. CENTER WORKSPACE: INTERACTIVE WAVEFORM & ACCESSIBLE TRIM HANDLES
        AccessibleAudioWaveform(
            currentMs = currentPositionMs,
            durationMs = durationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            samples = rawOriginalPcm,
            visualMode = visualMode,
            windowStartMsOverride = viewWindowStartMs,
            windowSpanMsOverride = viewWindowSpanMs,
            verticalZoom = verticalZoom,
            followCursor = followCursor,
            onVerticalZoomChange = { verticalZoom = it },
            onZoomInAction = {
                val resolved = WaveformViewportMath.resolveWindow(durationMs, viewWindowStartMs, viewWindowSpanMs, currentPositionMs, followCursor)
                applyTimeSpanWindow((resolved.spanMs / 2L).coerceAtLeast(50L), currentPositionMs)
            },
            onZoomOutAction = {
                val safeDur = durationMs.coerceAtLeast(100L)
                val resolved = WaveformViewportMath.resolveWindow(safeDur, viewWindowStartMs, viewWindowSpanMs, currentPositionMs, followCursor)
                val nextSpan = resolved.spanMs * 2L
                if (nextSpan >= safeDur) resetViewToAll(true) else applyTimeSpanWindow(nextSpan, currentPositionMs)
            },
            onZoomFitAction = {
                pushViewHistory()
                viewWindowStartMs = 0L
                viewWindowSpanMs = null
                verticalZoom = 1.0f
            },
            onToggleVisualMode = {
                val modes = AudioVisualMode.values()
                visualMode = modes[(visualMode.ordinal + 1) % modes.size]
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                AccessibilityHelper.announceForAccessibility(context, "Visual display switched to ${visualMode.displayName}")
            },
            onSeek = { seekPos ->
                playerManager.seekTo(seekPos)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
            }
        )

        AccessibleTrimSection(
            currentMs = currentPositionMs,
            durationMs = durationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            onSetTrimStart = { pos ->
                setTrimStartSafe(pos)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.DOUBLE_CLICK)
                AccessibilityHelper.announceForAccessibility(context, "Trim start set to ${AccessibilityHelper.formatTimeSpoken(trimStartMs)}")
            },
            onSetTrimEnd = { pos ->
                setTrimEndSafe(pos)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.DOUBLE_CLICK)
                AccessibilityHelper.announceForAccessibility(context, "Trim end set to ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}")
            },
            onFineTuneStart = { delta ->
                setTrimStartSafe(trimStartMs + delta)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
            },
            onFineTuneEnd = { delta ->
                setTrimEndSafe(trimEndMs + delta)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
            },
            onPreviewTrim = {
                safePlaySelection(loopSegment = false)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
            }
        )

        // 3. BOTTOM DOCK: TRANSPORT, SPEED/VOLUME & COMPACT MIC RECORDING PANEL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_bottom_transport_dock"),
            colors = CardDefaults.cardColors(
                containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccessibleTransportControls(
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) {
                            playerManager.pause()
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            AccessibilityHelper.announceForAccessibility(context, "Audio paused")
                        } else if (!ensurePcmReadyForPlayback()) {
                            Toast.makeText(context, "Audio canvas is empty. Paste, mix, or record audio first.", Toast.LENGTH_SHORT).show()
                        } else {
                            playerManager.play()
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            AccessibilityHelper.announceForAccessibility(context, "Audio playing")
                        }
                    },
                    onStop = {
                        playerManager.stop()
                        playerManager.seekTo(0L)
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                        AccessibilityHelper.announceForAccessibility(context, "Stopped")
                    },
                    onRewind5s = {
                        if (ensurePcmReadyForPlayback()) {
                            val newPos = (currentPositionMs - 5000L).coerceAtLeast(0L)
                            playerManager.seekTo(newPos)
                            if (!isPlaying) playerManager.play(startFromMs = newPos)
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                    },
                    onForward5s = {
                        if (ensurePcmReadyForPlayback()) {
                            val newPos = (currentPositionMs + 5000L).coerceAtMost(durationMs)
                            playerManager.seekTo(newPos)
                            if (!isPlaying) playerManager.play(startFromMs = newPos)
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                    },
                    isLooping = isLooping,
                    onToggleLoop = {
                        isLooping = !isLooping
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                        AccessibilityHelper.announceForAccessibility(context, if (isLooping) "Repeat Loop On" else "Repeat Loop Off")
                    },
                    onNudge = { deltaMs ->
                        if (ensurePcmReadyForPlayback()) {
                            val newPos = (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
                            playerManager.seekTo(newPos)
                            if (!isPlaying) playerManager.play(startFromMs = newPos)
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                    },
                    activeScrubDirection = activeScrubDirection,
                    scrubSpeed = scrubSpeed,
                    onToggleScrubRewind = {
                        if (!ensurePcmReadyForPlayback()) {
                            Toast.makeText(context, "Audio canvas is empty.", Toast.LENGTH_SHORT).show()
                        } else {
                            playerManager.toggleScrub(ScrubDirection.REWIND)
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                    },
                    onToggleScrubForward = {
                        if (!ensurePcmReadyForPlayback()) {
                            Toast.makeText(context, "Audio canvas is empty.", Toast.LENGTH_SHORT).show()
                        } else {
                            playerManager.toggleScrub(ScrubDirection.FORWARD)
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                    },
                    onJumpStart = {
                        playerManager.seekTo(0L)
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.HEAVY_CLICK)
                    },
                    onJumpEnd = {
                        playerManager.seekTo(durationMs)
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.HEAVY_CLICK)
                    },
                    onPlaySelection = { safePlaySelection(loopSegment = false) },
                    onPlayContinuous = {
                        if (!ensurePcmReadyForPlayback()) {
                            Toast.makeText(context, "Audio canvas is empty. Paste, mix, or record audio first.", Toast.LENGTH_SHORT).show()
                        } else {
                            playerManager.play(startFromMs = currentPositionMs, endLimitMs = null)
                        }
                    },
                    onPlayLoop = { safePlaySelection(loopSegment = true) }
                )

                AudioControlDeckCard(
                    playbackRate = livePlaybackRate,
                    masterVolume = liveVolumeLevel,
                    sampleRateHz = workingSampleRate,
                    visualMode = visualMode,
                    onPlaybackRateChange = { r ->
                        playerManager.setPlaybackRate(r)
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                    },
                    onMasterVolumeChange = { v -> playerManager.setVolumeLevel(v) }
                )

                // Dedicated Compact Microphone Recording & Auto-Gain Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_mic_settings"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRecording) {
                            Button(
                                onClick = { stopMicRecording() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_stop_record"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${if (recordSelectionMode) "Stop Punch-In" else "Stop Recording"} (${AccessibilityHelper.formatTimeVisual(recordedDurationMs)})",
                                    fontWeight = FontWeight.Bold
                                )
                            }

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
                                    progress = { micAmplitude },
                                    modifier = Modifier.weight(1f).height(8.dp),
                                    color = if (isTooLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { startMicRecording(selectionMode = false) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_record_audio"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Record", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { startMicRecording(selectionMode = true) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_record_selection")
                                        .semantics { contentDescription = "Record Selection punch-in over trim range" }
                                ) {
                                    Text("Punch-In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                IconButton(
                                    onClick = { showMicAdvancedSettings = !showMicAdvancedSettings },
                                    modifier = Modifier.size(48.dp).testTag("btn_toggle_mic_advanced")
                                        .semantics { contentDescription = if (showMicAdvancedSettings) "Hide microphone settings" else "Show microphone buffer and volume settings" }
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-Gain & Anti-Crackle",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = micAutoGain,
                                onCheckedChange = {
                                    micAutoGain = it
                                    recorderManager.autoGainEnabled = it
                                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                },
                                modifier = Modifier.testTag("switch_mic_auto_gain")
                            )
                        }

                        AnimatedVisibility(visible = showMicAdvancedSettings) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Buffer:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                                    listOf(1024, 2048, 4096).forEach { size ->
                                        FilterChip(
                                            selected = micBufferSize == size,
                                            onClick = {
                                                micBufferSize = size
                                                recorderManager.bufferSizeOption = size
                                            },
                                            label = { Text(if (size == 2048) "$size (Default)" else "$size", fontSize = 11.sp) },
                                            modifier = Modifier.testTag("chip_buffer_$size")
                                        )
                                    }
                                }

                                AccessibleEffectSlider(
                                    title = "Mic Gain",
                                    value = micVolume,
                                    onValueChange = {
                                        micVolume = it
                                        recorderManager.micGainVolume = it
                                    },
                                    valueRange = 0.2f..2.0f,
                                    step = 0.1f,
                                    displayValue = "${(micVolume * 100).toInt()}%",
                                    spokenDescription = "Microphone volume ${(micVolume * 100).toInt()} percent",
                                    testTag = "slider_mic_volume"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }

    // MODALS TRIGGERED FROM TOP DROPDOWN MENUS
    if (showOpenAudioDialog) {
        AudioOpenDialog(
            recentFiles = recentOpenedTitles,
            savedExports = savedExports,
            openInNewTab = openInNewTab,
            onOpenInNewTabChange = { openInNewTab = it },
            isImporting = isImportingAudio,
            importingMessage = importingMessage,
            onImportMultipleFiles = { multiAudioPickerLauncher.launch(arrayOf("audio/*")) },
            onImportFromOs = { osDocumentAudioLauncher.launch(arrayOf("audio/*")) },
            onImportFile = { audioPickerLauncher.launch("audio/*") },
            onBrowseOsFolder = { osDocumentAudioLauncher.launch(arrayOf("*/*")) },
            onOpenExportedFile = { exported ->
                loadImportedAudioUri(Uri.fromFile(File(exported.filePath)), explicitTitle = exported.title)
            },
            onClearRecent = { recentOpenedTitles = emptyList() },
            onGoHome = {
                showOpenAudioDialog = false
                resetToEmptyProject()
            },
            onHelp = {
                showOpenAudioDialog = false
                onOpenHelpRequested()
            },
            onDismiss = { showOpenAudioDialog = false }
        )
    }

    if (showExportDialog) {
        val sessionTimeStamp = remember(showExportDialog) {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }
        val resolvedDefaultFileName = openedFileBaseName?.takeIf { it.isNotBlank() } ?: "AudioStudio_$sessionTimeStamp"
        AccessibleExportDialog(
            defaultFileName = resolvedDefaultFileName,
            isVideo = false,
            isExporting = isExporting,
            exportProgress = exportProgress,
            exportedFile = exportedFileResult,
            initialFormat = exportTargetFormat,
            onDismiss = { showExportDialog = false; isExporting = false },
            onConfirmExport = { chosenName, format ->
                val mainPcm = rawOriginalPcm
                if (mainPcm == null || mainPcm.isEmpty()) {
                    Toast.makeText(context, "Track is empty (0:00). Paste, mix, or record audio first.", Toast.LENGTH_SHORT).show()
                    return@AccessibleExportDialog
                }
                isExporting = true
                exportProgress = 0.05f
                scope.launch {
                    try {
                        val file = audioProcessor.exportSingleAudioFile(
                            mainPcm = mainPcm,
                            bgTrackConfig = null,
                            bgPcm = null,
                            settings = currentSettings(),
                            outputFileName = chosenName,
                            format = format,
                            onProgress = { p -> exportProgress = p }
                        )
                        exportedFileResult = file
                        openedFileBaseName = StudioPreferences.extractBaseFileName(file.name).takeIf { it.isNotBlank() }
                        mainTrackTitle = file.name
                        syncActiveProjectTab()
                        isExporting = false
                        mediaRepository.refreshExports()
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Export finished! Audio saved as ${file.name}")
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        isExporting = false
                        Toast.makeText(context, "Export error: ${t.localizedMessage ?: "Storage or processing error"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onPlayExported = { file ->
                try {
                    playerManager.loadUri(Uri.fromFile(file))
                    playerManager.play()
                    showExportDialog = false
                } catch (t: Throwable) {
                    Toast.makeText(context, "Playback error: ${t.localizedMessage ?: "Cannot play file"}", Toast.LENGTH_LONG).show()
                }
            },
            onShareExported = { file ->
                try {
                    val media = mediaRepository.exportedFiles.value.find { it.filePath == file.absolutePath }
                    if (media != null) {
                        mediaRepository.shareExport(media)
                    } else {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Share Exported Audio").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "Could not share file: ${t.localizedMessage ?: "File location restricted"}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showSilenceDialog) {
        StudioInsertSilenceDialog(
            currentPositionMs = currentPositionMs,
            onDismiss = { showSilenceDialog = false },
            onConfirmInsert = { seconds ->
                showSilenceDialog = false
                applyTransform("Inserted $seconds seconds of silence") { pcm ->
                    StudioAudioProcessor.insertSilence(pcm, currentPositionMs, seconds)
                }
            }
        )
    }

    if (showToneDialog) {
        StudioToneGeneratorDialog(
            onDismiss = { showToneDialog = false },
            onConfirmGenerate = { type, freq, durationSec ->
                showToneDialog = false
                val tonePcm = StudioAudioProcessor.generateTone(type, freq, durationSec)
                saveUndoSnapshot()
                rawOriginalPcm = tonePcm
                mainTrackTitle = "Generated: $type (${durationSec}s)"
                openedFileBaseName = null
                playerManager.loadPcmData(tonePcm, StudioAudioProcessor.SAMPLE_RATE)
                trimStartMs = 0L
                trimEndMs = playerManager.durationMs.value
                syncActiveProjectTab()
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(context, "Synthesized $type waveform")
            }
        )
    }

    if (showBlankProjectDialog) {
        StudioBlankProjectDialog(
            onDismiss = { showBlankProjectDialog = false },
            onConfirmCreate = { durationSec ->
                showBlankProjectDialog = false
                playerManager.stop()
                syncActiveProjectTab()
                val silenceSamples = ShortArray(durationSec * workingSampleRate)
                val blankNum = openProjects.count { it.isBlankProject } + 1
                val blankTitle = if (durationSec == 0) "Blank Project $blankNum (0s)" else "Blank Project $blankNum (${durationSec}s)"
                val activeTab = openProjects.getOrNull(activeProjectIndex)
                val replaceStarter = openProjects.size == 1 &&
                    activeTab != null &&
                    activeTab.isBlankProject &&
                    openedFileBaseName == null &&
                    undoStack.isEmpty()

                if (!replaceStarter) {
                    openProjects.add(
                        AudioProjectTab(
                            title = blankTitle,
                            baseFileName = null,
                            pcm = silenceSamples,
                            sampleRate = workingSampleRate,
                            trimStartMs = 0L,
                            trimEndMs = durationSec * 1000L,
                            currentPositionMs = 0L,
                            cuePoints = emptyList(),
                            isBlankProject = true
                        )
                    )
                    activeProjectIndex = openProjects.lastIndex
                }

                rawOriginalPcm = silenceSamples
                mainTrackTitle = blankTitle
                openedFileBaseName = null
                playerManager.loadPcmData(silenceSamples, workingSampleRate)
                trimStartMs = 0L
                trimEndMs = durationSec * 1000L
                viewHistoryStack.clear()
                resetViewToAll(false)
                cuePoints = emptyList()
                undoStack.clear()
                redoStack.clear()
                syncActiveProjectTab()
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(
                    context,
                    if (durationSec == 0) "Opened new empty blank project tab ready to paste" else "Opened new blank $durationSec second project tab"
                )
            }
        )
    }

    if (showMixAudioDialog) {
        syncActiveProjectTab()
        val otherTabs = openProjects.filterIndexed { idx, tab -> idx != activeProjectIndex && tab.pcm.isNotEmpty() }
        StudioMixAudioDialog(
            currentTrackTitle = mainTrackTitle,
            clipboardPcm = clipboardPcm,
            otherProjects = otherTabs,
            pickedMixTitle = pickedMixTitle,
            pickedMixPcm = pickedMixPcm,
            sampleRate = workingSampleRate,
            currentCursorMs = currentPositionMs,
            onPickFileToMix = { mixFilePickerLauncher.launch(arrayOf("audio/*")) },
            onConfirmMix = { overlayPcm, sourceName, startMs, mainVol, overlayVol, applyLimiter ->
                showMixAudioDialog = false
                applyTransform("Mixed $sourceName into $mainTrackTitle") { pcm ->
                    StudioAudioProcessor.mixAudioTracks(
                        main = pcm,
                        overlay = overlayPcm,
                        startMs = startMs,
                        mainVolume = mainVol,
                        overlayVolume = overlayVol,
                        applyPeakLimiter = applyLimiter,
                        sampleRate = workingSampleRate
                    )
                }
            },
            onDismiss = { showMixAudioDialog = false }
        )
    }

    if (showGainDialog) {
        StudioGainDialog(
            onDismiss = { showGainDialog = false },
            onConfirmGain = { gainDb ->
                showGainDialog = false
                val hasSelection = trimEndMs > trimStartMs && trimEndMs < durationMs
                applyTransform("Applied %+.1f dB gain".format(gainDb)) { pcm ->
                    if (hasSelection) StudioAudioProcessor.applyGainDb(pcm, gainDb, trimStartMs, trimEndMs)
                    else StudioAudioProcessor.applyGainDb(pcm, gainDb)
                }
            }
        )
    }

    if (showStatsDialog && currentAudioStats != null) {
        StudioAudioStatsDialog(stats = currentAudioStats!!, onDismiss = { showStatsDialog = false })
    }

    if (showPropertiesDialog) {
        val pcm = rawOriginalPcm
        val fileSizeBytes = if (pcm != null) (pcm.size * 2L) + 44L else 0L
        StudioAudioPropertiesDialog(
            fileName = mainTrackTitle,
            durationMs = durationMs,
            sampleRate = workingSampleRate,
            channels = "Mono (1 Channel)",
            format = "16-bit Signed Linear PCM WAV",
            fileSizeBytes = fileSizeBytes,
            onDismiss = { showPropertiesDialog = false }
        )
    }

    if (showVoiceOverDialog) {
        StudioVoiceOverDialog(
            hasClipboard = clipboardPcm != null && clipboardPcm!!.isNotEmpty(),
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            currentPositionMs = currentPositionMs,
            pickedAudioTitle = pickedVoiceOverTitle,
            pickedAudioDurationMs = pickedVoiceOverDurMs,
            isDecodingAudio = isDecodingVoiceOverFile,
            onPickAudioFile = { voiceOverFilePickerLauncher.launch(arrayOf("audio/*")) },
            onClearPickedAudio = {
                pickedVoiceOverTitle = null
                pickedVoiceOverPcm = null
                pickedVoiceOverDurMs = 0L
            },
            onApplyVoiceOver = { cfg ->
                showVoiceOverDialog = false
                val pickedPcm = pickedVoiceOverPcm
                val clip = clipboardPcm
                when {
                    cfg.sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC && pickedPcm != null && pickedPcm.isNotEmpty() -> {
                        val modeLabel = if (cfg.autoDuckEnabled) "Auto-Ducking" else "Steady"
                        applyTransform("Applied ${cfg.preset.displayName} Voice Over ($modeLabel)") { pcm ->
                            AudioEffectsProEngine.applySmartMusicBedAutoDuck(
                                voiceTrack = pcm,
                                musicPcm = pickedPcm,
                                duckLevel = cfg.duckFactor,
                                voiceGain = cfg.voiceGain,
                                musicBaseVolume = cfg.musicVolume,
                                autoDuckEnabled = cfg.autoDuckEnabled,
                                loopMusic = cfg.loopMusic,
                                sampleRate = workingSampleRate
                            )
                        }
                    }
                    cfg.sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP && pickedPcm != null && pickedPcm.isNotEmpty() -> {
                        applyTransform("Applied ${cfg.preset.displayName} Voice Over") { pcm ->
                            AudioEffectsProEngine.applyVoiceOverDuck(
                                mainTrack = pcm,
                                voiceClip = pickedPcm,
                                insertAtMs = currentPositionMs,
                                musicDuckFactor = if (cfg.autoDuckEnabled) cfg.duckFactor else 1.0f,
                                voiceGain = cfg.voiceGain,
                                sampleRate = workingSampleRate
                            )
                        }
                    }
                    cfg.sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP && clip != null && clip.isNotEmpty() -> {
                        applyTransform("Applied ${cfg.preset.displayName} Voice Over (Clipboard)") { pcm ->
                            AudioEffectsProEngine.applyVoiceOverDuck(
                                mainTrack = pcm,
                                voiceClip = clip,
                                insertAtMs = currentPositionMs,
                                musicDuckFactor = if (cfg.autoDuckEnabled) cfg.duckFactor else 1.0f,
                                voiceGain = cfg.voiceGain,
                                sampleRate = workingSampleRate
                            )
                        }
                    }
                    else -> {
                        applyTransform("Applied Auto-Ducking dip over selection") { pcm ->
                            AudioEffectsProEngine.applyShapeVolume(
                                pcm,
                                trimStartMs,
                                trimEndMs,
                                AudioEffectsProEngine.VolumeShapeType.DUCK_VALLEY,
                                depth = (1f - cfg.duckFactor).coerceIn(0.1f, 0.95f),
                                sampleRate = workingSampleRate
                            )
                        }
                    }
                }
            },
            onDismiss = { showVoiceOverDialog = false }
        )
    }

    if (showVocalKaraokeDialog) {
        StudioVocalKaraokeDialog(
            currentTrackTitle = mainTrackTitle,
            currentTrackPcm = rawOriginalPcm ?: ShortArray(0),
            sampleRate = workingSampleRate,
            initialStereoSourceUri = currentTrackSourceUri,
            audioProcessor = audioProcessor,
            playerManager = playerManager,
            mediaRepository = mediaRepository,
            defaultExportFormat = defaultExportFormat,
            hapticsEnabled = hapticsEnabled,
            onApplySingleStem = { label, stemPcm ->
                saveUndoSnapshot()
                rawOriginalPcm = stemPcm
                currentTrackSourceUri = null
                try {
                    playerManager.loadPcmData(stemPcm, workingSampleRate)
                } catch (_: Throwable) {}
                val newDurMs = ((stemPcm.size.toDouble() / workingSampleRate) * 1000.0).toLong()
                trimStartMs = 0L
                trimEndMs = newDurMs
                syncActiveProjectTab()
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(context, label)
                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            },
            onOpenBothStemsAsTabs = { vocalsTitle, vocalsPcm, karaokeTitle, karaokePcm ->
                syncActiveProjectTab()
                val vDur = ((vocalsPcm.size.toDouble() / workingSampleRate) * 1000.0).toLong()
                val kDur = ((karaokePcm.size.toDouble() / workingSampleRate) * 1000.0).toLong()
                val vocalsTab = AudioProjectTab(
                    title = vocalsTitle,
                    baseFileName = StudioPreferences.extractBaseFileName(vocalsTitle).takeIf { it.isNotBlank() },
                    pcm = vocalsPcm,
                    sampleRate = workingSampleRate,
                    trimStartMs = 0L,
                    trimEndMs = vDur,
                    currentPositionMs = 0L,
                    cuePoints = emptyList(),
                    isBlankProject = false,
                    sourceUri = null
                )
                val karaokeTab = AudioProjectTab(
                    title = karaokeTitle,
                    baseFileName = StudioPreferences.extractBaseFileName(karaokeTitle).takeIf { it.isNotBlank() },
                    pcm = karaokePcm,
                    sampleRate = workingSampleRate,
                    trimStartMs = 0L,
                    trimEndMs = kDur,
                    currentPositionMs = 0L,
                    cuePoints = emptyList(),
                    isBlankProject = false,
                    sourceUri = null
                )
                openProjects.add(vocalsTab)
                openProjects.add(karaokeTab)
                activeProjectIndex = openProjects.lastIndex
                rawOriginalPcm = karaokePcm
                mainTrackTitle = karaokeTitle
                openedFileBaseName = karaokeTab.baseFileName
                currentTrackSourceUri = null
                try {
                    playerManager.loadPcmData(karaokePcm, workingSampleRate)
                } catch (_: Throwable) {}
                trimStartMs = 0L
                trimEndMs = kDur
                viewHistoryStack.clear()
                resetViewToAll(false)
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                val msg = "Created separate Vocals and Karaoke project tabs."
                AccessibilityHelper.announceForAccessibility(context, msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showVocalKaraokeDialog = false }
        )
    }

    if (showCuePointsDialog) {
        StudioCuePointsDialog(
            cuePoints = cuePoints,
            currentMs = currentPositionMs,
            onDropCue = {
                val cueNum = cuePoints.size + 1
                val newCue = CuePoint(positionMs = currentPositionMs, name = "Cue $cueNum (${AccessibilityHelper.formatTimeMMSS(currentPositionMs)})")
                cuePoints = (cuePoints + newCue).sortedBy { it.positionMs }
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(context, "Dropped cue point $cueNum")
            },
            onJumpPrevCue = {
                cuePoints.filter { it.positionMs < currentPositionMs - 150L }.maxByOrNull { it.positionMs }?.let { prev ->
                    playerManager.seekTo(prev.positionMs)
                }
            },
            onJumpNextCue = {
                cuePoints.filter { it.positionMs > currentPositionMs + 150L }.minByOrNull { it.positionMs }?.let { next ->
                    playerManager.seekTo(next.positionMs)
                }
            },
            onSeekToCue = { cue -> playerManager.seekTo(cue.positionMs) },
            onDeleteCue = { cue -> cuePoints = cuePoints.filter { it.id != cue.id } },
            onDismiss = { showCuePointsDialog = false }
        )
    }

    if (showEffectsRackDialog) {
        StudioEffectsRackDialog(
            selectedTabIndex = effectsCategoryIndex,
            onSelectTabIndex = { effectsCategoryIndex = it },
            echoEnabled = echoEnabled,
            onEchoEnabledChange = { echoEnabled = it },
            echoDelayMs = echoDelayMs,
            onEchoDelayChange = { echoDelayMs = it },
            echoFeedback = echoFeedback,
            onEchoFeedbackChange = { echoFeedback = it },
            echoMix = echoMix,
            onEchoMixChange = { echoMix = it },
            reverbEnabled = reverbEnabled,
            onReverbEnabledChange = { reverbEnabled = it },
            reverbRoomSize = reverbRoomSize,
            onReverbRoomSizeChange = { reverbRoomSize = it },
            reverbDamping = reverbDamping,
            onReverbDampingChange = { reverbDamping = it },
            reverbMix = reverbMix,
            onReverbMixChange = { reverbMix = it },
            noiseReductionEnabled = noiseReductionEnabled,
            onNoiseReductionEnabledChange = { noiseReductionEnabled = it },
            noiseReductionIntensity = noiseReductionIntensity,
            onNoiseReductionIntensityChange = { noiseReductionIntensity = it },
            noiseGateEnabled = noiseGateEnabled,
            onNoiseGateEnabledChange = { noiseGateEnabled = it },
            noiseGateThreshold = noiseGateThreshold,
            onNoiseGateThresholdChange = { noiseGateThreshold = it },
            bassBoost = bassBoost,
            onBassBoostChange = { bassBoost = it },
            trebleBoost = trebleBoost,
            onTrebleBoostChange = { trebleBoost = it },
            selectedEq = selectedEq,
            onSelectEq = { selectedEq = it },
            speedMultiplier = speedMultiplier,
            onSpeedMultiplierChange = { speedMultiplier = it },
            isReversed = isReversed,
            onReversedChange = { isReversed = it },
            studioFxSettings = studioFxSettings,
            onStudioFxSettingsChange = { studioFxSettings = it },
            onAudition = { applyDspAndAudition() },
            onApplyDeClick = {
                applyTransform("Applied De-Click filter with peak limiter") { pcm ->
                    StudioAudioProcessor.applyTruePeakLimiter(StudioAudioProcessor.deClickDeCrackle(pcm), -1.0f)
                }
            },
            onApplyVocalIsolation = { preset, subStr, humStr, clarityStr ->
                applyTransform("Isolated spoken audio (${preset.displayName})") { pcm ->
                    VocalIsolationProcessor.isolateSpokenAudio(
                        samples = pcm,
                        subtractionStrength = subStr,
                        humRemovalStrength = humStr,
                        vocalClarityBoost = clarityStr,
                        spectralFloorOverride = if (kotlin.math.abs(subStr - preset.subtractionStrength) < 0.02f) preset.spectralFloor else null,
                        vocalBandEmphasisOverride = if (kotlin.math.abs(subStr - preset.subtractionStrength) < 0.02f) preset.vocalBandEmphasis else null,
                        pauseGateFloorOverride = if (kotlin.math.abs(subStr - preset.subtractionStrength) < 0.02f) preset.pauseGateFloor else null,
                        highPassHz = if (kotlin.math.abs(subStr - preset.subtractionStrength) < 0.02f) preset.highPassHz else null,
                        sampleRate = workingSampleRate
                    )
                }
            },
            onDismiss = { showEffectsRackDialog = false }
        )
    }

    if (showProSuiteDialog) {
        Dialog(
            onDismissRequest = { showProSuiteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { showProSuiteDialog = false }
            val categoryTitles = listOf("Creative DSP Suite", "Channels Suite", "Filter Suite", "Volume & Dynamics")
            val currentTitle = categoryTitles[proSuiteCategoryIndex.coerceIn(0, 3)]
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 640.dp)
                    .testTag("dialog_pro_dsp_suite"),
                shape = RoundedCornerShape(20.dp),
                color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background,
                border = if (isHighContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                        IconButton(
                            onClick = { showProSuiteDialog = false },
                            modifier = Modifier.size(48.dp).testTag("btn_close_pro_suite_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close $currentTitle")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                    ) {
                        AccessibleProSuiteSection(
                            selectedCategoryIndex = proSuiteCategoryIndex,
                            onSelectCategoryIndex = { idx ->
                                proSuiteCategoryIndex = idx
                                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                            },
                            hasClipboard = clipboardPcm != null && clipboardPcm!!.isNotEmpty(),
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            currentPositionMs = currentPositionMs,
                            currentSampleRate = workingSampleRate,
                            currentPlaybackRate = livePlaybackRate,
                            clipboardPcm = clipboardPcm,
                            onApplyPcmTransform = { msg, fn -> applyTransform(msg, resetTrimStart = false, transform = fn) },
                            onSetSampleRate = { sr -> playerManager.setWorkingSampleRate(sr) },
                            onSetPlaybackRate = { rate -> playerManager.setPlaybackRate(rate) },
                            onOpenEchoReverbTab = { showProSuiteDialog = false; effectsCategoryIndex = 0; showEffectsRackDialog = true },
                            onOpenNoiseTab = { showProSuiteDialog = false; effectsCategoryIndex = 1; showEffectsRackDialog = true },
                            onOpenEqTab = { showProSuiteDialog = false; effectsCategoryIndex = 2; showEffectsRackDialog = true },
                            onOpenGainDialog = { showProSuiteDialog = false; showGainDialog = true },
                            onApplyEcho = {
                                applyTransform("Applied Echo (${echoDelayMs}ms)") { pcm ->
                                    audioProcessor.applyEcho(pcm, echoDelayMs, echoFeedback, echoMix)
                                }
                            },
                            onApplyReverb = {
                                applyTransform("Applied Acoustic Reverb") { pcm ->
                                    audioProcessor.applyReverb(pcm, reverbRoomSize, reverbDamping, reverbMix)
                                }
                            },
                            onApplyNoiseGate = {
                                applyTransform("Applied Noise Gate") { pcm ->
                                    audioProcessor.applyNoiseGate(pcm, noiseGateThreshold)
                                }
                            },
                            onApplyNoiseReduction = {
                                applyTransform("Applied Noise Reduction") { pcm ->
                                    audioProcessor.applyNoiseReduction(pcm, noiseReductionIntensity)
                                }
                            },
                            onApplyEqualizer = {
                                applyTransform("Applied 7-Band Vocal Clarity EQ") { pcm ->
                                    StudioAudioProcessor.apply7BandEq(pcm, -1f, 0f, 1.5f, 3f, 3f, 2f, 1f)
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showProSuiteDialog = false },
                            modifier = Modifier.height(48.dp).testTag("btn_done_pro_suite_dialog")
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSpecifyViewDialog) {
        val currentWin = WaveformViewportMath.resolveWindow(
            durationMs = durationMs,
            viewStartMs = viewWindowStartMs,
            viewSpanMs = viewWindowSpanMs,
            currentMs = currentPositionMs,
            followCursor = followCursor
        )
        StudioSpecifyViewDialog(
            durationMs = durationMs,
            currentStartMs = currentWin.startMs,
            currentEndMs = currentWin.endMs,
            currentVerticalZoom = verticalZoom,
            onConfirmView = { sMs, eMs, vZoom ->
                showSpecifyViewDialog = false
                pushViewHistory()
                val safeDur = durationMs.coerceAtLeast(100L)
                val newSpan = (eMs - sMs).coerceIn(50L, safeDur)
                viewWindowStartMs = sMs.coerceIn(0L, (safeDur - newSpan).coerceAtLeast(0L))
                viewWindowSpanMs = if (newSpan >= safeDur) null else newSpan
                verticalZoom = vZoom.coerceIn(0.25f, 8.0f)
                AccessibilityHelper.announceForAccessibility(
                    context,
                    "View set from ${AccessibilityHelper.formatTimeSpoken(viewWindowStartMs)} to ${AccessibilityHelper.formatTimeSpoken(viewWindowStartMs + newSpan)}"
                )
            },
            onDismiss = { showSpecifyViewDialog = false }
        )
    }
}

@Composable
private fun StatusMetricCell(label: String, value: String, highlight: Boolean) {
    val isHighContrast = LocalHighContrast.current
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            color = when {
                isHighContrast && highlight -> MaterialTheme.colorScheme.secondary
                isHighContrast -> Color.White
                highlight -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
