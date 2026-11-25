package com.example.ui.studio

import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioRecorderManager
import com.example.data.MediaRepository
import com.example.ui.components.AccessiblePreviewPlaybackSlider
import com.example.ui.components.AccessibleTimeReadout
import com.example.ui.components.ActiveEffectChipData
import com.example.ui.components.ActiveEffectsLongPressBar
import com.example.ui.components.StudioFeatureGuideDialog
import com.example.ui.components.VideoAudioMusicPanel
import com.example.ui.components.VideoConvertAndMetaPanel
import com.example.ui.components.VideoOverlaysVfxPanel
import com.example.ui.components.VideoTimelineScrubber
import com.example.ui.components.VideoTimelineToolsPanel
import com.example.ui.components.VideoTransformPanel
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.LocalHighContrast
import com.example.video.FullRenderRequest
import com.example.video.MultiMusicPreviewController
import com.example.video.MusicLayerTrack
import com.example.video.NarrationSegment
import com.example.video.SnapshotGlimpseItem
import com.example.video.SoundEffectOverlay
import com.example.video.VideoAudioMixer
import com.example.video.VideoAudioOption
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment
import com.example.video.VideoFilter
import com.example.video.VideoPlayerManager
import com.example.video.VideoRenderEngine
import com.example.video.VideoRotation
import com.example.video.VideoStudioFeatureButton
import com.example.video.VideoToolkitProcessor
import com.example.video.VideoVfx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoFeatureWorkspaceScreen(
    tool: VideoFeatureTool,
    session: VideoProjectSessionState,
    videoPlayer: VideoPlayerManager,
    renderEngine: VideoRenderEngine,
    toolkit: VideoToolkitProcessor,
    audioMixer: VideoAudioMixer,
    recorder: AudioRecorderManager,
    mediaRepository: MediaRepository,
    hapticsEnabled: Boolean,
    onSaveAndReturn: (VideoProjectSessionState) -> Unit,
    onCancelBack: () -> Unit,
    onNavigateToExports: () -> Unit,
    multiMusicPreview: MultiMusicPreviewController? = null,
    modifier: Modifier = Modifier,
    initialFeature: VideoStudioFeatureButton? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighContrast = LocalHighContrast.current

    val ownsMusicPreview = multiMusicPreview == null
    val activeMusicPreview = multiMusicPreview ?: remember(context) { MultiMusicPreviewController(context) }
    val previewingMusicTrackId by activeMusicPreview.previewingTrackId.collectAsState()

    DisposableEffect(activeMusicPreview, ownsMusicPreview) {
        onDispose {
            if (ownsMusicPreview) {
                activeMusicPreview.stopAll()
            } else {
                activeMusicPreview.stopSoloPreview()
                activeMusicPreview.pauseTimelineTracks()
            }
        }
    }

    var draft by remember(session.projectId, tool) { mutableStateOf(session) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var taskProgress by remember { mutableFloatStateOf(0f) }
    var narrationStartMs by remember { mutableLongStateOf(0L) }
    var baseSpeechBins by remember(draft.sourceUri) { mutableStateOf(BooleanArray(0)) }
    var showFeatureGuide by remember { mutableStateOf(false) }

    val isPlaying by videoPlayer.isPlaying.collectAsState()
    val durationMs by videoPlayer.durationMs.collectAsState()
    val isPreparedDuration by videoPlayer.isPreparedDuration.collectAsState()
    val playerAspectRatio by videoPlayer.videoAspectRatio.collectAsState()
    val isRecordingNarration by recorder.isRecording.collectAsState()
    val recordingDurMs by recorder.recordedDurationMs.collectAsState()
    val recordingAmp by recorder.amplitude.collectAsState()

    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }

    LaunchedEffect(draft.sourceUri) {
        val uri = draft.sourceUri ?: return@LaunchedEffect
        try {
            val pcm = withContext(Dispatchers.IO) { audioMixer.decodeUriToPcm(uri) }
            baseSpeechBins = MultiMusicPreviewController.computeSpeechActivityBins(pcm)
        } catch (_: Throwable) {
            baseSpeechBins = BooleanArray(0)
        }
    }

    LaunchedEffect(
        isPlaying,
        draft.musicTracks,
        draft.smartDucking,
        draft.narrations,
        draft.audioOption,
        baseSpeechBins
    ) {
        fun syncNow() {
            val posMs = videoPlayer.currentPositionMs.value
            activeMusicPreview.syncTimelinePlayback(
                isVideoPlaying = isPlaying,
                videoPositionMs = posMs,
                musicTracks = draft.musicTracks,
                smartDucking = draft.smartDucking,
                narrations = draft.narrations,
                audioOption = draft.audioOption,
                baseAudioActiveAtPlayhead = MultiMusicPreviewController.isBinActive(baseSpeechBins, posMs)
            )
        }
        syncNow()
        while (isActive && isPlaying) {
            delay(200L)
            syncNow()
        }
    }

    LaunchedEffect(draft.musicTracks) {
        activeMusicPreview.refreshTrackSettings(draft.musicTracks)
    }

    LaunchedEffect(draft.sourceUri) {
        val uri = draft.sourceUri
        if (uri != null && (draft.metadata.durationMs <= 0L || draft.metadata.width <= 0)) {
            try {
                val meta = toolkit.inspectDetailedMetadata(uri)
                val resolvedDur = meta.durationMs.takeIf { it > 0L } ?: draft.durationMs
                draft = draft.copy(
                    metadata = meta,
                    durationMs = resolvedDur,
                    trimEndMs = if (draft.trimEndMs <= 0L || draft.trimEndMs > resolvedDur) resolvedDur else draft.trimEndMs
                )
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(isPreparedDuration, durationMs) {
        if (isPreparedDuration && durationMs > 0L) {
            val updatedEnd = if (draft.trimEndMs <= 0L || draft.trimEndMs > durationMs) durationMs else draft.trimEndMs
            if (draft.durationMs != durationMs || draft.trimEndMs != updatedEnd) {
                draft = draft.copy(durationMs = durationMs, trimEndMs = updatedEnd)
            }
        }
    }

    fun buildRenderRequest(
        keepRanges: List<Pair<Long, Long>> = VideoCutMath.computeMultiTrimKeepRanges(
            multiTrimClips = draft.multiTrimClips,
            cutSegments = draft.cutSegments,
            fallbackStartMs = draft.trimStartMs,
            fallbackEndMs = draft.trimEndMs
        )
    ): FullRenderRequest {
        val uri = draft.sourceUri ?: throw IllegalStateException("Select or create a video clip first.")
        return FullRenderRequest(
            sourceUri = uri,
            keepRangesMs = keepRanges,
            speed = draft.selectedSpeed,
            pitchCorrect = draft.pitchCorrect,
            reverseVideo = draft.reverseVideo,
            filter = draft.selectedFilter,
            vfx = draft.selectedVfx,
            aspectRatio = draft.aspectRatio,
            aspectFitMode = draft.aspectFitMode,
            resolutionPreset = draft.resolutionPreset,
            customWidth = draft.customWidth,
            customHeight = draft.customHeight,
            rotation = draft.rotation,
            flipHorizontal = draft.flipH,
            flipVertical = draft.flipV,
            compressorQuality = draft.compressorQuality,
            customBitrateBps = (draft.customBitrateMbps * 1_000_000f).toInt(),
            textOverlay = draft.textOverlay,
            textPosition = draft.textPosition,
            textAnimStyle = draft.textAnimStyle,
            blurConfig = draft.blurConfig,
            imageOverlay = draft.imageOverlay,
            subtitleCues = draft.subtitleCues,
            soundEffects = draft.soundEffects,
            narrations = draft.narrations,
            musicTracks = draft.musicTracks,
            smartDucking = draft.smartDucking,
            audioOption = draft.audioOption,
            replacedAudioUri = draft.replacedAudioUri,
            audioVolume = draft.audioVolume,
            noiseReductionEnabled = draft.noiseReduceEnabled,
            noiseReductionStrength = draft.noiseReduceStrength,
            outputFormat = draft.outputFormat,
            metadataTitle = draft.metadata.title,
            metadataArtist = draft.metadata.artist,
            metadataAlbum = draft.metadata.album
        )
    }

    fun runAsyncTask(label: String, block: suspend (onProgress: (Float) -> Unit) -> File?) {
        statusMessage = label
        taskProgress = 0.05f
        scope.launch {
            try {
                val out = block { p -> taskProgress = p }
                statusMessage = null
                mediaRepository.refreshExports()
                if (out != null) {
                    if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                    Toast.makeText(context, "Saved: ${out.name}", Toast.LENGTH_LONG).show()
                    AccessibilityHelper.announceForAccessibility(context, "Completed $label. Saved ${out.name}")
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                statusMessage = null
                Toast.makeText(context, "Error: ${t.localizedMessage ?: "Processing failed"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler {
        videoPlayer.pause()
        activeMusicPreview.stopSoloPreview()
        activeMusicPreview.pauseTimelineTracks()
        onCancelBack()
    }

    if (tool == VideoFeatureTool.FULL_VIDEO_STUDIO) {
        Column(modifier = modifier.fillMaxSize()) {
            WorkspaceHeaderBar(
                title = initialFeature?.title ?: tool.title,
                subtitle = draft.title,
                canUndo = false,
                onUndo = {},
                onBack = {
                    videoPlayer.pause()
                    activeMusicPreview.stopSoloPreview()
                    activeMusicPreview.pauseTimelineTracks()
                    onCancelBack()
                },
                onSave = {
                    videoPlayer.pause()
                    activeMusicPreview.stopSoloPreview()
                    activeMusicPreview.pauseTimelineTracks()
                    onSaveAndReturn(draft.copy(hasUnsavedChanges = true))
                },
                onHelp = { showFeatureGuide = true }
            )
            VideoStudioScreen(
                mediaRepository = mediaRepository,
                hapticsEnabled = hapticsEnabled,
                onNavigateToExports = onNavigateToExports,
                initialVideoUri = draft.sourceUri,
                initialVideoTitle = draft.title,
                modifier = Modifier.weight(1f),
                initialFeature = initialFeature
            )
        }
        return
    }

    val effectiveDuration = if (isPreparedDuration && durationMs > 0L) {
        durationMs
    } else {
        draft.durationMs.takeIf { it > 0L } ?: durationMs
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WorkspaceHeaderBar(
            title = initialFeature?.title ?: tool.title,
            subtitle = draft.title,
            canUndo = false,
            onUndo = {},
            onBack = {
                videoPlayer.pause()
                activeMusicPreview.stopSoloPreview()
                activeMusicPreview.pauseTimelineTracks()
                onCancelBack()
            },
            onSave = {
                videoPlayer.pause()
                activeMusicPreview.stopSoloPreview()
                activeMusicPreview.pauseTimelineTracks()
                onSaveAndReturn(draft.copy(durationMs = effectiveDuration, hasUnsavedChanges = true))
            },
            onHelp = { showFeatureGuide = true }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 780.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (statusMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$statusMessage (${(taskProgress * 100).toInt()}%)", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { taskProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Compact Live Video Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .testTag("workspace_video_preview"),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    videoPlayer.attachSurface(holder)
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {}
                            })
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(playerAspectRatio.coerceIn(0.5f, 2.5f))
                        .graphicsLayer {
                            rotationZ = draft.rotation.degrees.toFloat()
                            scaleX = if (draft.flipH) -1f else 1f
                            scaleY = if (draft.flipV) -1f else 1f
                        }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (isPlaying) videoPlayer.pause() else videoPlayer.play() },
                    modifier = Modifier.height(48.dp).testTag("btn_video_workspace_play")
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPlaying) "Pause" else "Play", fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.weight(1f)) {
                    AccessibleTimeReadout(
                        currentMs = 0L,
                        durationMs = effectiveDuration,
                        label = "Playhead",
                        isPlaying = isPlaying,
                        positionFlow = videoPlayer.currentPositionMs
                    )
                }
            }

            val livePosForSlider by videoPlayer.currentPositionMs.collectAsState()
            AccessiblePreviewPlaybackSlider(
                currentPositionMs = livePosForSlider,
                durationMs = effectiveDuration,
                onSeekTo = { videoPlayer.seekTo(it) },
                isPlaying = isPlaying,
                onTogglePlayPause = { if (isPlaying) videoPlayer.pause() else videoPlayer.play() },
                onScrubPreviewPulse = { videoPlayer.scrubPreviewAt(it) },
                testTag = "slider_video_workspace_preview"
            )

            VideoTimelineScrubber(
                currentPositionMs = 0L,
                durationMs = effectiveDuration,
                trimStartMs = draft.trimStartMs,
                trimEndMs = draft.trimEndMs,
                cutSegments = draft.cutSegments,
                onSeekTo = { videoPlayer.seekTo(it) },
                isPlaying = isPlaying,
                positionFlow = videoPlayer.currentPositionMs
            )

            val activeVideoEffects = buildList {
                if (draft.selectedFilter != VideoFilter.NORMAL) {
                    add(ActiveEffectChipData("vfilter", "Filter: ${draft.selectedFilter.displayName}"))
                }
                if (draft.selectedVfx != VideoVfx.NONE) {
                    add(ActiveEffectChipData("vvfx", "VFX: ${draft.selectedVfx.displayName}"))
                }
                if (draft.blurConfig.enabled) {
                    add(ActiveEffectChipData("vblur", if (draft.blurConfig.isMosaic) "Mosaic Blur" else "Gaussian Blur"))
                }
                if (draft.textOverlay.isNotBlank()) {
                    add(ActiveEffectChipData("vtext", "Text Overlay"))
                }
                if (draft.imageOverlay.uri != null) {
                    add(ActiveEffectChipData("vimage", "Image Overlay"))
                }
                if (draft.subtitleCues.isNotEmpty()) {
                    add(ActiveEffectChipData("vsubs", "Subtitles (${draft.subtitleCues.size})"))
                }
                if (draft.selectedSpeed != 1.0f) {
                    add(ActiveEffectChipData("vspeed", "Speed ${draft.selectedSpeed}x"))
                }
                if (draft.reverseVideo) {
                    add(ActiveEffectChipData("vreverse", "Reverse Video"))
                }
                if (draft.rotation != VideoRotation.DEG_0 || draft.flipH || draft.flipV) {
                    add(ActiveEffectChipData("vtransform", "Rotate / Flip"))
                }
                if (draft.noiseReduceEnabled) {
                    add(ActiveEffectChipData("vdenoise", "Audio De-Noise"))
                }
            }

            ActiveEffectsLongPressBar(
                effects = activeVideoEffects,
                onRemoveEffect = { chip ->
                    draft = when (chip.id) {
                        "vfilter" -> draft.copy(selectedFilter = VideoFilter.NORMAL, hasUnsavedChanges = true)
                        "vvfx" -> draft.copy(selectedVfx = VideoVfx.NONE, hasUnsavedChanges = true)
                        "vblur" -> draft.copy(blurConfig = draft.blurConfig.copy(enabled = false), hasUnsavedChanges = true)
                        "vtext" -> draft.copy(textOverlay = "", hasUnsavedChanges = true)
                        "vimage" -> draft.copy(imageOverlay = draft.imageOverlay.copy(uri = null), hasUnsavedChanges = true)
                        "vsubs" -> draft.copy(subtitleCues = emptyList(), hasUnsavedChanges = true)
                        "vspeed" -> draft.copy(selectedSpeed = 1.0f, hasUnsavedChanges = true)
                        "vreverse" -> draft.copy(reverseVideo = false, hasUnsavedChanges = true)
                        "vtransform" -> draft.copy(rotation = VideoRotation.DEG_0, flipH = false, flipV = false, hasUnsavedChanges = true)
                        "vdenoise" -> draft.copy(noiseReduceEnabled = false, hasUnsavedChanges = true)
                        else -> draft
                    }
                    AccessibilityHelper.announceForAccessibility(context, "Removed ${chip.label}")
                }
            )

            when (tool) {
                VideoFeatureTool.TRIM_TIMELINE -> VideoTimelineToolsPanel(
                    currentPositionMs = 0L,
                    durationMs = effectiveDuration,
                    trimStartMs = draft.trimStartMs,
                    trimEndMs = draft.trimEndMs,
                    streamCopyTrim = draft.streamCopyTrim,
                    positionFlow = videoPlayer.currentPositionMs,
                    currentPositionMsProvider = { videoPlayer.currentPositionMs.value },
                    onUpdateTrimRange = { s, e -> draft = draft.copy(trimStartMs = s, trimEndMs = e, hasUnsavedChanges = true) },
                    onToggleStreamCopyTrim = { draft = draft.copy(streamCopyTrim = it, hasUnsavedChanges = true) },
                    onExportTrimmed = null,
                    onCutMiddleSegment = null,
                    videoFileName = draft.title,
                    videoMetadata = draft.metadata,
                    isPlaying = isPlaying,
                    onPlayPause = { if (isPlaying) videoPlayer.pause() else videoPlayer.play() },
                    onSeekTo = { videoPlayer.seekTo(it) },
                    onReplayFromStart = {
                        videoPlayer.seekTo(0L)
                        videoPlayer.play(startMs = 0L, endLimitMs = null)
                    },
                    cutSegments = draft.cutSegments,
                    onAddCutSegment = { s, e ->
                        draft = draft.copy(
                            cutSegments = draft.cutSegments + VideoCutSegment(startMs = s, endMs = e),
                            hasUnsavedChanges = true
                        )
                    },
                    onRemoveCutSegment = { id ->
                        draft = draft.copy(
                            cutSegments = draft.cutSegments.filterNot { it.id == id },
                            hasUnsavedChanges = true
                        )
                    },
                    onClearCutSegments = {
                        draft = draft.copy(cutSegments = emptyList(), hasUnsavedChanges = true)
                    },
                    showHeaderMetadataAndScrubber = false,
                    showTrimmerExportButton = false,
                    mergeClipUris = draft.mergeClipUris,
                    onAddMergeClips = { uris -> draft = draft.copy(mergeClipUris = draft.mergeClipUris + uris, hasUnsavedChanges = true) },
                    onMoveMergeClip = { from, to ->
                        val list = draft.mergeClipUris.toMutableList()
                        val item = list.removeAt(from)
                        list.add(to, item)
                        draft = draft.copy(mergeClipUris = list, hasUnsavedChanges = true)
                    },
                    onRemoveMergeClip = { idx -> draft = draft.copy(mergeClipUris = draft.mergeClipUris.filterIndexed { i, _ -> i != idx }, hasUnsavedChanges = true) },
                    onExecuteMergeClips = {
                        runAsyncTask("Merging ${draft.mergeClipUris.size} Video Clips...") { prog ->
                            toolkit.mergeVideoClips(draft.mergeClipUris, "Merged_$timestamp", prog)
                        }
                    },
                    slideshowImages = draft.slideshowImages,
                    slideshowSecPerSlide = draft.slideshowSecPerSlide,
                    slideshowMusicUri = draft.slideshowMusicUri,
                    onPickSlideshowImages = { draft = draft.copy(slideshowImages = it, hasUnsavedChanges = true) },
                    onChangeSlideshowSec = { draft = draft.copy(slideshowSecPerSlide = it, hasUnsavedChanges = true) },
                    onPickSlideshowMusic = { draft = draft.copy(slideshowMusicUri = it, hasUnsavedChanges = true) },
                    onCreateSlideshow = {
                        runAsyncTask("Building Photo Slideshow Video...") { prog ->
                            toolkit.createSlideshowVideo(draft.slideshowImages, draft.slideshowSecPerSlide, draft.slideshowMusicUri, "Slideshow_$timestamp", prog)
                        }
                    },
                    onCreateBlankClip = {
                        runAsyncTask("Generating Blank Studio Clip...") { _ ->
                            val blank = toolkit.generateBlankClip(durationSec = 5, titleText = draft.textOverlay.ifBlank { "Blank Studio Clip" })
                            val uri = Uri.fromFile(blank)
                            draft = draft.copy(sourceUri = uri, title = "Blank Studio Clip", hasUnsavedChanges = true)
                            videoPlayer.loadUri(uri) {}
                            null
                        }
                    },
                    snapshots = draft.snapshots,
                    onCaptureSnapshot = {
                        val s = videoPlayer.currentPositionMs.value
                        val e = (s + 2000L).coerceAtMost(effectiveDuration.coerceAtLeast(2000L))
                        draft = draft.copy(
                            snapshots = draft.snapshots + SnapshotGlimpseItem(label = "Glimpse #${draft.snapshots.size + 1}", startMs = s, endMs = e),
                            hasUnsavedChanges = true
                        )
                    },
                    onRemoveSnapshot = { id -> draft = draft.copy(snapshots = draft.snapshots.filterNot { it.id == id }, hasUnsavedChanges = true) },
                    onExportSnapshots = { prepend ->
                        val uri = draft.sourceUri ?: return@VideoTimelineToolsPanel
                        val ranges = draft.snapshots.map { it.startMs to it.endMs } + if (prepend) listOf(0L to effectiveDuration) else emptyList()
                        runAsyncTask(if (prepend) "Prepending Highlights..." else "Extracting Glimpses...") { prog ->
                            toolkit.exportStreamCopyRanges(uri, ranges, "Highlights_$timestamp", draft.outputFormat, onProgress = prog)
                        }
                    },
                    initialFocusedFeature = initialFeature,
                    multiTrimClips = draft.multiTrimClips,
                    onUpdateMultiTrimClips = { draft = draft.copy(multiTrimClips = it, hasUnsavedChanges = true) },
                    onUpdateCutSegments = { draft = draft.copy(cutSegments = it, hasUnsavedChanges = true) },
                    splitPointsMs = draft.splitPointsMs,
                    onUpdateSplitPoints = { draft = draft.copy(splitPointsMs = it, hasUnsavedChanges = true) },
                    autoSplitMode = draft.autoSplitMode,
                    autoSplitIntervalMs = draft.autoSplitIntervalMs,
                    autoSplitFileCount = draft.autoSplitFileCount,
                    autoSplitTargetSizeMb = draft.autoSplitTargetSizeMb,
                    onUpdateAutoSplitConfig = { mode, intervalMs, count, sizeMb ->
                        draft = draft.copy(
                            autoSplitMode = mode,
                            autoSplitIntervalMs = intervalMs,
                            autoSplitFileCount = count,
                            autoSplitTargetSizeMb = sizeMb,
                            hasUnsavedChanges = true
                        )
                    },
                    onPlayRange = { startMs, endMs ->
                        videoPlayer.seekTo(startMs)
                        videoPlayer.play(startMs = startMs, endLimitMs = endMs)
                    },
                    onOpenFeatureGuide = { showFeatureGuide = true }
                )

                VideoFeatureTool.AUDIO_MUSIC -> VideoAudioMusicPanel(
                    currentPositionMs = 0L,
                    isRecordingNarration = isRecordingNarration,
                    recordingDurationMs = recordingDurMs,
                    recordingAmplitude = recordingAmp,
                    narrations = draft.narrations,
                    isPlaying = isPlaying,
                    positionFlow = videoPlayer.currentPositionMs,
                    onToggleRecordNarration = {
                        if (isRecordingNarration) {
                            val pcm = recorder.stopRecording()
                            if (pcm != null && pcm.isNotEmpty()) {
                                draft = draft.copy(
                                    narrations = draft.narrations + NarrationSegment(
                                        label = "Narration #${draft.narrations.size + 1}",
                                        startMs = narrationStartMs,
                                        pcmSamples = pcm,
                                        durationMs = (pcm.size * 1000L) / 44100L
                                    ),
                                    hasUnsavedChanges = true
                                )
                            }
                        } else {
                            narrationStartMs = videoPlayer.currentPositionMs.value
                            recorder.startRecording(onSuccess = {}, onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() })
                        }
                    },
                    onDeleteNarration = { id -> draft = draft.copy(narrations = draft.narrations.filterNot { it.id == id }, hasUnsavedChanges = true) },
                    musicTracks = draft.musicTracks,
                    smartDucking = draft.smartDucking,
                    onAddMusicTrack = { uri ->
                        val fallbackTitle = "Music Track #${draft.musicTracks.size + 1}"
                        scope.launch {
                            val (resolvedTitle, durMs) = withContext(Dispatchers.IO) {
                                MultiMusicPreviewController.inspectMusicTrackInfo(context, uri, fallbackTitle)
                            }
                            draft = draft.copy(
                                musicTracks = draft.musicTracks + MusicLayerTrack(
                                    title = resolvedTitle,
                                    uri = uri,
                                    startMs = 0L,
                                    volume = 0.55f,
                                    loop = draft.defaultLoopMusicTracks,
                                    durationMs = durMs
                                ),
                                hasUnsavedChanges = true
                            )
                        }
                    },
                    onUpdateMusicVolume = { id, vol -> draft = draft.copy(musicTracks = draft.musicTracks.map { if (it.id == id) it.copy(volume = vol) else it }, hasUnsavedChanges = true) },
                    onRemoveMusicTrack = { id -> draft = draft.copy(musicTracks = draft.musicTracks.filterNot { it.id == id }, hasUnsavedChanges = true) },
                    onToggleSmartDucking = { draft = draft.copy(smartDucking = it, hasUnsavedChanges = true) },
                    onToggleMusicLoop = { id, loopOn ->
                        draft = draft.copy(
                            musicTracks = draft.musicTracks.map { if (it.id == id) it.copy(loop = loopOn) else it },
                            hasUnsavedChanges = true
                        )
                    },
                    defaultLoopNewTracks = draft.defaultLoopMusicTracks,
                    onToggleDefaultLoopNewTracks = { loopOn ->
                        draft = draft.copy(
                            defaultLoopMusicTracks = loopOn,
                            musicTracks = draft.musicTracks.map { it.copy(loop = loopOn) },
                            hasUnsavedChanges = true
                        )
                    },
                    previewingMusicTrackId = previewingMusicTrackId,
                    onTogglePreviewMusicTrack = { track ->
                        if (isPlaying) videoPlayer.pause()
                        activeMusicPreview.toggleSoloPreview(track)
                    },
                    videoDurationMs = effectiveDuration,
                    soundEffects = draft.soundEffects,
                    onAddSoundEffect = { preset, uri, dur, freeze ->
                        draft = draft.copy(
                            soundEffects = draft.soundEffects + SoundEffectOverlay(
                                name = preset.displayName,
                                preset = preset,
                                customUri = uri,
                                timestampMs = videoPlayer.currentPositionMs.value,
                                durationMs = dur,
                                freezeFrameAtTimestamp = freeze
                            ),
                            hasUnsavedChanges = true
                        )
                    },
                    onRemoveSoundEffect = { id -> draft = draft.copy(soundEffects = draft.soundEffects.filterNot { it.id == id }, hasUnsavedChanges = true) },
                    audioOption = draft.audioOption,
                    replacedAudioUri = draft.replacedAudioUri,
                    audioVolume = draft.audioVolume,
                    onSelectAudioOption = { opt ->
                        draft = draft.copy(audioOption = opt, hasUnsavedChanges = true)
                        videoPlayer.setMute(opt == VideoAudioOption.MUTE)
                    },
                    onPickReplacementAudio = { draft = draft.copy(replacedAudioUri = it, hasUnsavedChanges = true) },
                    onChangeAudioVolume = { draft = draft.copy(audioVolume = it, hasUnsavedChanges = true) },
                    noiseReduceEnabled = draft.noiseReduceEnabled,
                    noiseReduceStrength = draft.noiseReduceStrength,
                    onToggleNoiseReduce = { draft = draft.copy(noiseReduceEnabled = it, hasUnsavedChanges = true) },
                    onChangeNoiseReduceStrength = { draft = draft.copy(noiseReduceStrength = it, hasUnsavedChanges = true) },
                    onExtractVideoToAudio = { fmt ->
                        val uri = draft.sourceUri ?: return@VideoAudioMusicPanel
                        runAsyncTask("Extracting $fmt Audio Track...") { prog ->
                            val f = audioMixer.extractAudioFromVideo(uri, fmt, "ExtractedAudio_$timestamp", draft.noiseReduceEnabled, prog)
                            Toast.makeText(context, "Saved audio: Music/Audio Studio/${f.name}", Toast.LENGTH_LONG).show()
                            null
                        }
                    },
                    initialFocusedFeature = initialFeature
                )

                VideoFeatureTool.OVERLAYS_VFX -> VideoOverlaysVfxPanel(
                    durationMs = effectiveDuration,
                    textOverlay = draft.textOverlay,
                    textPosition = draft.textPosition,
                    textAnimStyle = draft.textAnimStyle,
                    onChangeTextOverlay = { draft = draft.copy(textOverlay = it, hasUnsavedChanges = true) },
                    onSelectTextPosition = { draft = draft.copy(textPosition = it, hasUnsavedChanges = true) },
                    onSelectTextAnimStyle = { draft = draft.copy(textAnimStyle = it, hasUnsavedChanges = true) },
                    blurConfig = draft.blurConfig,
                    onUpdateBlurConfig = { draft = draft.copy(blurConfig = it, hasUnsavedChanges = true) },
                    imageOverlay = draft.imageOverlay,
                    onPickOverlayImage = { uri ->
                        scope.launch {
                            try {
                                val (iw, ih, warning) = toolkit.inspectOverlayImage(uri, draft.metadata.width.coerceAtLeast(1), draft.metadata.height.coerceAtLeast(1))
                                draft = draft.copy(
                                    imageOverlay = draft.imageOverlay.copy(uri = uri, imageWidth = iw, imageHeight = ih, mismatchWarning = warning, endMs = effectiveDuration.coerceAtLeast(3000L)),
                                    hasUnsavedChanges = true
                                )
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                Toast.makeText(context, "Could not inspect overlay image: ${t.localizedMessage ?: "Invalid image"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onUpdateImageOverlay = { draft = draft.copy(imageOverlay = it, hasUnsavedChanges = true) },
                    subtitleCues = draft.subtitleCues,
                    onImportSubtitleFile = { uri ->
                        scope.launch {
                            try {
                                draft = draft.copy(subtitleCues = toolkit.parseSubtitleUri(uri), hasUnsavedChanges = true)
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                Toast.makeText(context, "Could not import subtitles: ${t.localizedMessage ?: "Unsupported file"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onLoadInlineSrtText = { raw -> draft = draft.copy(subtitleCues = toolkit.parseSubtitleText(raw), hasUnsavedChanges = true) },
                    onClearSubtitles = { draft = draft.copy(subtitleCues = emptyList(), hasUnsavedChanges = true) },
                    selectedFilter = draft.selectedFilter,
                    selectedVfx = draft.selectedVfx,
                    onSelectFilter = { draft = draft.copy(selectedFilter = it, hasUnsavedChanges = true) },
                    onSelectVfx = { draft = draft.copy(selectedVfx = it, hasUnsavedChanges = true) },
                    initialFocusedFeature = initialFeature
                )

                VideoFeatureTool.TRANSFORM_SPEED -> VideoTransformPanel(
                    speed = draft.selectedSpeed,
                    pitchCorrect = draft.pitchCorrect,
                    onChangeSpeed = { draft = draft.copy(selectedSpeed = it, hasUnsavedChanges = true) },
                    onTogglePitchCorrect = { draft = draft.copy(pitchCorrect = it, hasUnsavedChanges = true) },
                    resolutionPreset = draft.resolutionPreset,
                    customWidth = draft.customWidth,
                    customHeight = draft.customHeight,
                    lockAspectRatio = draft.lockAspectRatio,
                    onSelectResolution = { draft = draft.copy(resolutionPreset = it, hasUnsavedChanges = true) },
                    onChangeCustomDimensions = { w, h -> draft = draft.copy(customWidth = w, customHeight = h, hasUnsavedChanges = true) },
                    onToggleLockAspect = { draft = draft.copy(lockAspectRatio = it, hasUnsavedChanges = true) },
                    aspectRatio = draft.aspectRatio,
                    aspectFitMode = draft.aspectFitMode,
                    onSelectAspectRatio = { draft = draft.copy(aspectRatio = it, hasUnsavedChanges = true) },
                    onSelectAspectFitMode = { draft = draft.copy(aspectFitMode = it, hasUnsavedChanges = true) },
                    rotation = draft.rotation,
                    flipHorizontal = draft.flipH,
                    flipVertical = draft.flipV,
                    onSelectRotation = { draft = draft.copy(rotation = it, hasUnsavedChanges = true) },
                    onToggleFlipH = { draft = draft.copy(flipH = it, hasUnsavedChanges = true) },
                    onToggleFlipV = { draft = draft.copy(flipV = it, hasUnsavedChanges = true) },
                    reverseVideo = draft.reverseVideo,
                    onToggleReverseVideo = { draft = draft.copy(reverseVideo = it, hasUnsavedChanges = true) },
                    initialFocusedFeature = initialFeature
                )

                VideoFeatureTool.CONVERT_TOOLS -> VideoConvertAndMetaPanel(
                    metadata = draft.metadata,
                    compressorQuality = draft.compressorQuality,
                    customBitrateMbps = draft.customBitrateMbps,
                    onSelectCompressor = { draft = draft.copy(compressorQuality = it, hasUnsavedChanges = true) },
                    onChangeCustomBitrateMbps = { draft = draft.copy(customBitrateMbps = it, hasUnsavedChanges = true) },
                    onCompressAndExport = {
                        if (draft.sourceUri == null) return@VideoConvertAndMetaPanel
                        runAsyncTask("Compressing Video (${draft.compressorQuality.label})...") { prog ->
                            renderEngine.renderVideo(buildRenderRequest(), "Compressed_$timestamp", prog)
                        }
                    },
                    outputFormat = draft.outputFormat,
                    onSelectOutputFormat = { draft = draft.copy(outputFormat = it, hasUnsavedChanges = true) },
                    onConvertFormatNow = {
                        val uri = draft.sourceUri ?: return@VideoConvertAndMetaPanel
                        runAsyncTask("Converting to .${draft.outputFormat.extension.uppercase()}...") { prog ->
                            toolkit.exportStreamCopyRanges(uri, listOf(0L to effectiveDuration), "Converted_$timestamp", draft.outputFormat, onProgress = prog)
                        }
                    },
                    onRemoveSilenceNow = { threshDb ->
                        val uri = draft.sourceUri ?: return@VideoConvertAndMetaPanel
                        runAsyncTask("Detecting & Jump-Cutting Silence...") { prog ->
                            val pcm = audioMixer.decodeUriToPcm(uri)
                            val audible = audioMixer.detectAudibleSegments(pcm, threshDb, 450L)
                            toolkit.exportStreamCopyRanges(uri, audible, "NoSilence_$timestamp", draft.outputFormat, onProgress = prog)
                        }
                    },
                    onExportGifNow = { fps ->
                        val uri = draft.sourceUri ?: return@VideoConvertAndMetaPanel
                        runAsyncTask("Encoding Animated GIF...") { prog ->
                            val gif = toolkit.exportVideoToGif(uri, draft.trimStartMs, draft.trimEndMs, fps, "Clip_$timestamp.gif", prog)
                            Toast.makeText(context, "Saved GIF to Pictures/Accessible Studio/${gif.name}", Toast.LENGTH_LONG).show()
                            null
                        }
                    },
                    onExtractFrameAtPlayhead = {
                        val uri = draft.sourceUri ?: return@VideoConvertAndMetaPanel
                        runAsyncTask("Capturing Frame JPEG...") { _ ->
                            val jpg = toolkit.extractSingleFrame(uri, videoPlayer.currentPositionMs.value, "Frame_${timestamp}.jpg")
                            Toast.makeText(context, "Saved Frame: Pictures/Accessible Studio/${jpg.name}", Toast.LENGTH_LONG).show()
                            null
                        }
                    },
                    onBatchExtractFrames = { intervalSec ->
                        val uri = draft.sourceUri ?: return@VideoConvertAndMetaPanel
                        runAsyncTask("Batch Extracting Frames Every ${intervalSec}s...") { prog ->
                            val list = toolkit.extractBatchFrames(uri, intervalSec, "Batch_$timestamp", prog)
                            Toast.makeText(context, "Extracted ${list.size} JPEGs to Pictures/Accessible Studio", Toast.LENGTH_LONG).show()
                            null
                        }
                    },
                    onSaveMetadataTags = { title, artist, album, genre ->
                        draft = draft.copy(
                            metadata = draft.metadata.copy(title = title, artist = artist, album = album, genre = genre),
                            hasUnsavedChanges = true
                        )
                    },
                    initialFocusedFeature = initialFeature
                )

                VideoFeatureTool.FULL_VIDEO_STUDIO -> Unit
            }
        }
    }

    if (showFeatureGuide) {
        StudioFeatureGuideDialog(onDismiss = { showFeatureGuide = false })
    }
}
