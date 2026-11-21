package com.example.ui.screens

import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioRecorderManager
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.ui.components.AccessibleExportDialog
import com.example.ui.components.AccessibleTimeReadout
import com.example.ui.components.AccessibleTransportControls
import com.example.ui.components.AccessibleTrimSection
import com.example.ui.components.VideoAudioMusicPanel
import com.example.ui.components.VideoConvertAndMetaPanel
import com.example.ui.components.VideoOverlaysVfxPanel
import com.example.ui.components.VideoTimelineToolsPanel
import com.example.ui.components.VideoTransformPanel
import com.example.ui.theme.LocalHighContrast
import com.example.video.AspectRatioFitMode
import com.example.video.BlurRegionConfig
import com.example.video.CompressorQuality
import com.example.video.FullRenderRequest
import com.example.video.ImageOverlayConfig
import com.example.video.MultiMusicPreviewController
import com.example.video.MusicLayerTrack
import com.example.video.NarrationSegment
import com.example.video.SnapshotGlimpseItem
import com.example.video.SoundEffectOverlay
import com.example.video.SubtitleCue
import com.example.video.TextAnimationStyle
import com.example.video.TextOverlayPosition
import com.example.video.VideoAspectRatio
import com.example.video.VideoAudioMixer
import com.example.video.VideoAudioOption
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment
import com.example.video.VideoEditSettings
import com.example.video.VideoFilter
import com.example.video.VideoFormatOption
import com.example.video.VideoMetadataDetails
import com.example.video.VideoPlayerManager
import com.example.video.VideoProcessor
import com.example.video.VideoRenderEngine
import com.example.video.VideoResolutionPreset
import com.example.video.VideoRotation
import com.example.video.VideoToolSection
import com.example.video.VideoToolkitProcessor
import com.example.video.VideoVfx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoStudioScreen(
    mediaRepository: MediaRepository,
    hapticsEnabled: Boolean,
    onNavigateToExports: () -> Unit,
    initialVideoUri: Uri? = null,
    initialVideoTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighContrast = LocalHighContrast.current

    val videoProcessor = remember { VideoProcessor(context) }
    val renderEngine = remember { VideoRenderEngine(context) }
    val toolkit = remember { VideoToolkitProcessor(context) }
    val audioMixer = remember { VideoAudioMixer(context) }
    val videoPlayer = remember { VideoPlayerManager(context) }
    val recorder = remember { AudioRecorderManager(context) }
    val multiMusicPreview = remember { MultiMusicPreviewController(context) }
    val previewingMusicTrackId by multiMusicPreview.previewingTrackId.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            multiMusicPreview.stopAll()
            videoPlayer.release()
            if (recorder.isRecording.value) recorder.stopRecording()
        }
    }

    var videoTitle by remember { mutableStateOf(initialVideoTitle?.takeIf { it.isNotBlank() } ?: "Demo: Accessible Studio Clip") }
    var openedVideoBaseName by remember { mutableStateOf<String?>(null) }
    var currentVideoUri by remember { mutableStateOf<Uri?>(initialVideoUri) }
    var videoMeta by remember { mutableStateOf(VideoMetadataDetails()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var taskProgress by remember { mutableFloatStateOf(0f) }

    val isPlaying by videoPlayer.isPlaying.collectAsState()
    val currentPositionMs by videoPlayer.currentPositionMs.collectAsState()
    val durationMs by videoPlayer.durationMs.collectAsState()
    val playerAspectRatio by videoPlayer.videoAspectRatio.collectAsState()

    val isRecordingNarration by recorder.isRecording.collectAsState()
    val recordingDurMs by recorder.recordedDurationMs.collectAsState()
    val recordingAmp by recorder.amplitude.collectAsState()
    var narrationStartMs by remember { mutableLongStateOf(0L) }

    // Section Selection
    var activeSection by remember { mutableStateOf(VideoToolSection.TIMELINE) }

    // Timeline & Trimming
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(6000L) }
    var streamCopyTrim by remember { mutableStateOf(false) }
    var cutSegments by remember { mutableStateOf<List<VideoCutSegment>>(emptyList()) }
    var mergeClipUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var slideshowImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var slideshowSecPerSlide by remember { mutableIntStateOf(3) }
    var slideshowMusicUri by remember { mutableStateOf<Uri?>(null) }
    var snapshots by remember { mutableStateOf<List<SnapshotGlimpseItem>>(emptyList()) }

    // Audio, Narration, Music, SFX
    var narrations by remember { mutableStateOf<List<NarrationSegment>>(emptyList()) }
    var musicTracks by remember { mutableStateOf<List<MusicLayerTrack>>(emptyList()) }
    var smartDucking by remember { mutableStateOf(true) }
    var defaultLoopMusicTracks by remember { mutableStateOf(true) }
    var baseSpeechBins by remember(currentVideoUri) { mutableStateOf(BooleanArray(0)) }
    var soundEffects by remember { mutableStateOf<List<SoundEffectOverlay>>(emptyList()) }
    var audioOption by remember { mutableStateOf(VideoAudioOption.ORIGINAL_AUDIO) }
    var replacedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioVolume by remember { mutableFloatStateOf(1.0f) }
    var noiseReduceEnabled by remember { mutableStateOf(false) }
    var noiseReduceStrength by remember { mutableFloatStateOf(0.75f) }

    // Overlays, Blur, Subtitles, Filters, VFX
    var textOverlay by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf(TextOverlayPosition.BOTTOM_SUBTITLE) }
    var textAnimStyle by remember { mutableStateOf(TextAnimationStyle.STATIC) }
    var blurConfig by remember { mutableStateOf(BlurRegionConfig()) }
    var imageOverlay by remember { mutableStateOf(ImageOverlayConfig()) }
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(VideoFilter.NORMAL) }
    var selectedVfx by remember { mutableStateOf(VideoVfx.NONE) }

    // Transform, Speed, Convert & Metadata
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var pitchCorrect by remember { mutableStateOf(true) }
    var resolutionPreset by remember { mutableStateOf(VideoResolutionPreset.ORIGINAL) }
    var customWidth by remember { mutableIntStateOf(1280) }
    var customHeight by remember { mutableIntStateOf(720) }
    var lockAspectRatio by remember { mutableStateOf(true) }
    var aspectRatio by remember { mutableStateOf(VideoAspectRatio.ORIGINAL) }
    var aspectFitMode by remember { mutableStateOf(AspectRatioFitMode.LETTERBOX) }
    var rotation by remember { mutableStateOf(VideoRotation.DEG_0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var reverseVideo by remember { mutableStateOf(false) }
    var compressorQuality by remember { mutableStateOf(CompressorQuality.HIGH) }
    var customBitrateMbps by remember { mutableFloatStateOf(2.0f) }
    var outputFormat by remember { mutableStateOf(VideoFormatOption.MP4) }

    // Export Dialog
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportedFileResult by remember { mutableStateOf<File?>(null) }
    var surfaceCreated by remember { mutableStateOf(false) }

    LaunchedEffect(durationMs) {
        if (durationMs > 0) {
            if (trimEndMs > durationMs || trimEndMs <= 0L) trimEndMs = durationMs
            if (blurConfig.endMs > durationMs) blurConfig = blurConfig.copy(endMs = durationMs)
            if (imageOverlay.endMs > durationMs) imageOverlay = imageOverlay.copy(endMs = durationMs)
        }
    }

    LaunchedEffect(currentVideoUri) {
        currentVideoUri?.let { uri ->
            videoMeta = toolkit.inspectDetailedMetadata(uri)
            try {
                val pcm = withContext(Dispatchers.IO) { audioMixer.decodeUriToPcm(uri) }
                baseSpeechBins = MultiMusicPreviewController.computeSpeechActivityBins(pcm)
            } catch (_: Throwable) {
                baseSpeechBins = BooleanArray(0)
            }
        }
    }

    LaunchedEffect(
        isPlaying,
        currentPositionMs,
        musicTracks,
        smartDucking,
        narrations,
        audioOption,
        baseSpeechBins
    ) {
        multiMusicPreview.syncTimelinePlayback(
            isVideoPlaying = isPlaying,
            videoPositionMs = currentPositionMs,
            musicTracks = musicTracks,
            smartDucking = smartDucking,
            narrations = narrations,
            audioOption = audioOption,
            baseAudioActiveAtPlayhead = MultiMusicPreviewController.isBinActive(baseSpeechBins, currentPositionMs)
        )
    }

    LaunchedEffect(musicTracks) {
        multiMusicPreview.refreshTrackSettings(musicTracks)
    }

    fun buildRenderRequest(
        keepRanges: List<Pair<Long, Long>> = VideoCutMath.computeKeepRangesMs(trimStartMs, trimEndMs, cutSegments)
    ): FullRenderRequest {
        val uri = currentVideoUri ?: throw IllegalStateException("Select or create a video clip first.")
        return FullRenderRequest(
            sourceUri = uri,
            keepRangesMs = keepRanges,
            speed = selectedSpeed,
            pitchCorrect = pitchCorrect,
            reverseVideo = reverseVideo,
            filter = selectedFilter,
            vfx = selectedVfx,
            aspectRatio = aspectRatio,
            aspectFitMode = aspectFitMode,
            resolutionPreset = resolutionPreset,
            customWidth = customWidth,
            customHeight = customHeight,
            rotation = rotation,
            flipHorizontal = flipH,
            flipVertical = flipV,
            compressorQuality = compressorQuality,
            customBitrateBps = (customBitrateMbps * 1_000_000f).toInt(),
            textOverlay = textOverlay,
            textPosition = textPosition,
            textAnimStyle = textAnimStyle,
            blurConfig = blurConfig,
            imageOverlay = imageOverlay,
            subtitleCues = subtitleCues,
            soundEffects = soundEffects,
            narrations = narrations,
            musicTracks = musicTracks,
            smartDucking = smartDucking,
            audioOption = audioOption,
            replacedAudioUri = replacedAudioUri,
            audioVolume = audioVolume,
            noiseReductionEnabled = noiseReduceEnabled,
            noiseReductionStrength = noiseReduceStrength,
            outputFormat = outputFormat,
            metadataTitle = videoMeta.title,
            metadataArtist = videoMeta.artist,
            metadataAlbum = videoMeta.album
        )
    }

    fun loadUriIntoStudio(uri: Uri, title: String) {
        currentVideoUri = uri
        videoTitle = title
        videoPlayer.loadUri(uri) {
            trimStartMs = 0L
            trimEndMs = videoPlayer.durationMs.value.coerceAtLeast(1000L)
        }
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
                    if (out.name.endsWith(".mp4", true) || out.name.endsWith(".mkv", true) || out.name.endsWith(".mov", true)) {
                        exportedFileResult = out
                        showExportDialog = true
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                statusMessage = null
                Toast.makeText(context, "Error: ${t.localizedMessage ?: "Video operation failed"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun loadCustomDemoClip() {
        runAsyncTask("Rendering Accessible Studio Demo Clip...") { prog ->
            val settings = VideoEditSettings(
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                speed = selectedSpeed,
                filter = selectedFilter,
                vfx = selectedVfx,
                aspectRatio = aspectRatio,
                textOverlay = textOverlay,
                textPosition = textPosition,
                isMuted = audioOption == VideoAudioOption.MUTE,
                audioOption = audioOption,
                audioVolume = audioVolume
            )
            val demoFile = videoProcessor.generateDemoVideoClip(settings = settings, durationSec = 8, onProgress = prog)
            loadUriIntoStudio(Uri.fromFile(demoFile), "Demo: ${selectedFilter.displayName}")
            null
        }
    }

    LaunchedEffect(surfaceCreated) {
        if (surfaceCreated) {
            val seedUri = initialVideoUri
            if (currentVideoUri == null) {
                loadCustomDemoClip()
            } else if (seedUri != null && currentVideoUri == seedUri && durationMs <= 0L) {
                loadUriIntoStudio(seedUri, videoTitle)
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            var displayName: String? = null
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) displayName = c.getString(idx)
                    }
                }
            } catch (_: Exception) {}
            if (displayName.isNullOrBlank()) {
                displayName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            }
            val resolvedTitle = displayName?.takeIf { it.isNotBlank() } ?: "Imported Video Clip"
            openedVideoBaseName = StudioPreferences.extractBaseFileName(displayName).takeIf { it.isNotBlank() }
            loadUriIntoStudio(uri, resolvedTitle)
        }
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // On-device Privacy Assurance Banner
        Surface(
            color = if (isHighContrast) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth().testTag("banner_on_device_privacy")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    text = "On-device processing. Your videos never leave your device.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Video Header & Primary Actions
        Card(
            modifier = Modifier.fillMaxWidth().testTag("video_track_header_card"),
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface),
            border = BorderStroke(if (isHighContrast) 2.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Column {
                            Text("VIDEO STUDIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(videoTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                    }

                    Button(
                        onClick = { exportedFileResult = null; showExportDialog = true },
                        modifier = Modifier.height(48.dp).testTag("btn_video_export_single_file")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export", fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_select_video"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Video", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { loadCustomDemoClip() },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_load_demo_video")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Demo Clip", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Processing Progress Banner
        if (statusMessage != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$statusMessage (${(taskProgress * 100).toInt()}%)", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { taskProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Live Video Preview Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .testTag("video_preview_surface"),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) { videoPlayer.attachSurface(holder); surfaceCreated = true }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceCreated = false }
                        })
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(playerAspectRatio.coerceIn(0.5f, 2.5f))
                    .graphicsLayer {
                        rotationZ = rotation.degrees.toFloat()
                        scaleX = if (flipH) -1f else 1f
                        scaleY = if (flipV) -1f else 1f
                    }
            )

            if (textOverlay.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(
                            when (textPosition) {
                                TextOverlayPosition.TOP_HEADER -> Alignment.TopCenter
                                TextOverlayPosition.CENTER_TITLE -> Alignment.Center
                                TextOverlayPosition.BOTTOM_SUBTITLE -> Alignment.BottomCenter
                            }
                        )
                        .padding(10.dp)
                ) {
                    Text(textOverlay, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            ) {
                Text(
                    text = "${AccessibilityHelper.formatTimeVisual(currentPositionMs)} / ${AccessibilityHelper.formatTimeVisual(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Playhead & Transport + Trimmer Handles
        AccessibleTimeReadout(currentMs = currentPositionMs, durationMs = durationMs, label = "Video Playhead")
        AccessibleTransportControls(
            isPlaying = isPlaying,
            onPlayPause = { if (isPlaying) videoPlayer.pause() else videoPlayer.play() },
            onNudge = { d -> videoPlayer.seekTo((currentPositionMs + d).coerceIn(0L, durationMs)) },
            onJumpStart = { videoPlayer.seekTo(0L) },
            onJumpEnd = { videoPlayer.seekTo(durationMs) },
            onPlaySelection = {
                if (durationMs > 0L) {
                    val start = trimStartMs.coerceIn(0L, durationMs)
                    val end = trimEndMs.coerceIn(start, durationMs).let { if (it <= start) durationMs else it }
                    videoPlayer.seekTo(start)
                    videoPlayer.play(startMs = start, endLimitMs = end)
                }
            },
            onPlayContinuous = {
                if (durationMs > 0L) videoPlayer.play(startMs = currentPositionMs, endLimitMs = null)
            }
        )

        AccessibleTrimSection(
            currentMs = currentPositionMs,
            durationMs = durationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            onSetTrimStart = {
                val maxS = (trimEndMs - 500L.coerceAtMost(durationMs)).coerceIn(0L, durationMs)
                trimStartMs = it.coerceIn(0L, maxS)
            },
            onSetTrimEnd = {
                val minE = (trimStartMs + 500L.coerceAtMost(durationMs)).coerceIn(0L, durationMs)
                trimEndMs = it.coerceIn(minE, durationMs)
            },
            onFineTuneStart = { d ->
                val maxS = (trimEndMs - 500L.coerceAtMost(durationMs)).coerceIn(0L, durationMs)
                trimStartMs = (trimStartMs + d).coerceIn(0L, maxS)
            },
            onFineTuneEnd = { d ->
                val minE = (trimStartMs + 500L.coerceAtMost(durationMs)).coerceIn(0L, durationMs)
                trimEndMs = (trimEndMs + d).coerceIn(minE, durationMs)
            },
            onPreviewTrim = {
                if (durationMs > 0L) {
                    val start = trimStartMs.coerceIn(0L, durationMs)
                    val end = trimEndMs.coerceIn(start, durationMs).let { if (it <= start) durationMs else it }
                    videoPlayer.seekTo(start)
                    videoPlayer.play(startMs = start, endLimitMs = end)
                }
            }
        )

        // Clean Section Tabs for Video Toolkit
        val sections = VideoToolSection.values()
        ScrollableTabRow(
            selectedTabIndex = sections.indexOf(activeSection),
            edgePadding = 4.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).testTag("video_tool_sections_tabs")
        ) {
            sections.forEach { sec ->
                Tab(
                    selected = activeSection == sec,
                    onClick = {
                        activeSection = sec
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.TICK)
                        AccessibilityHelper.announceForAccessibility(context, sec.spokenSummary)
                    },
                    text = { Text(sec.title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }
        }

        when (activeSection) {
            VideoToolSection.TIMELINE -> VideoTimelineToolsPanel(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                streamCopyTrim = streamCopyTrim,
                onUpdateTrimRange = { s, e -> trimStartMs = s; trimEndMs = e },
                onToggleStreamCopyTrim = { streamCopyTrim = it },
                onExportTrimmed = {
                    val uri = currentVideoUri ?: return@VideoTimelineToolsPanel
                    val keepRanges = VideoCutMath.computeKeepRangesMs(trimStartMs, trimEndMs, cutSegments)
                    runAsyncTask("Trimming Video...") { prog ->
                        if (streamCopyTrim) {
                            toolkit.exportStreamCopyRanges(uri, keepRanges, "Trim_$timestamp", outputFormat, onProgress = prog)
                        } else {
                            renderEngine.renderVideo(buildRenderRequest(keepRanges), "Trim_$timestamp", prog)
                        }
                    }
                },
                onCutMiddleSegment = null,
                videoFileName = videoTitle,
                videoMetadata = videoMeta,
                isPlaying = isPlaying,
                onPlayPause = { if (isPlaying) videoPlayer.pause() else videoPlayer.play() },
                onSeekTo = { videoPlayer.seekTo(it) },
                onReplayFromStart = {
                    videoPlayer.seekTo(0L)
                    videoPlayer.play(startMs = 0L, endLimitMs = null)
                },
                cutSegments = cutSegments,
                onAddCutSegment = { s, e -> cutSegments = cutSegments + VideoCutSegment(startMs = s, endMs = e) },
                onRemoveCutSegment = { id -> cutSegments = cutSegments.filterNot { it.id == id } },
                onClearCutSegments = { cutSegments = emptyList() },
                showHeaderMetadataAndScrubber = true,
                showTrimmerExportButton = true,
                mergeClipUris = mergeClipUris,
                onAddMergeClips = { uris -> mergeClipUris = mergeClipUris + uris },
                onMoveMergeClip = { from, to ->
                    val list = mergeClipUris.toMutableList()
                    val item = list.removeAt(from)
                    list.add(to, item)
                    mergeClipUris = list
                },
                onRemoveMergeClip = { idx -> mergeClipUris = mergeClipUris.filterIndexed { i, _ -> i != idx } },
                onExecuteMergeClips = {
                    runAsyncTask("Merging ${mergeClipUris.size} Video Clips...") { prog ->
                        toolkit.mergeVideoClips(mergeClipUris, "Merged_$timestamp", prog)
                    }
                },
                slideshowImages = slideshowImages,
                slideshowSecPerSlide = slideshowSecPerSlide,
                slideshowMusicUri = slideshowMusicUri,
                onPickSlideshowImages = { slideshowImages = it },
                onChangeSlideshowSec = { slideshowSecPerSlide = it },
                onPickSlideshowMusic = { slideshowMusicUri = it },
                onCreateSlideshow = {
                    runAsyncTask("Building Photo Slideshow Video...") { prog ->
                        toolkit.createSlideshowVideo(slideshowImages, slideshowSecPerSlide, slideshowMusicUri, "Slideshow_$timestamp", prog)
                    }
                },
                onCreateBlankClip = {
                    runAsyncTask("Generating Blank Studio Clip...") { _ ->
                        val blank = toolkit.generateBlankClip(durationSec = 5, titleText = textOverlay.ifBlank { "Blank Studio Clip" })
                        loadUriIntoStudio(Uri.fromFile(blank), "Blank Studio Clip")
                        null
                    }
                },
                snapshots = snapshots,
                onCaptureSnapshot = {
                    val s = currentPositionMs
                    val e = (s + 2000L).coerceAtMost(durationMs.coerceAtLeast(2000L))
                    snapshots = snapshots + SnapshotGlimpseItem(label = "Glimpse #${snapshots.size + 1}", startMs = s, endMs = e)
                },
                onRemoveSnapshot = { id -> snapshots = snapshots.filterNot { it.id == id } },
                onExportSnapshots = { prepend ->
                    val uri = currentVideoUri ?: return@VideoTimelineToolsPanel
                    val ranges = snapshots.map { it.startMs to it.endMs } + if (prepend) listOf(0L to durationMs) else emptyList()
                    runAsyncTask(if (prepend) "Prepending Highlights..." else "Extracting Glimpses...") { prog ->
                        toolkit.exportStreamCopyRanges(uri, ranges, "Highlights_$timestamp", outputFormat, onProgress = prog)
                    }
                }
            )

            VideoToolSection.AUDIO_MUSIC -> VideoAudioMusicPanel(
                currentPositionMs = currentPositionMs,
                isRecordingNarration = isRecordingNarration,
                recordingDurationMs = recordingDurMs,
                recordingAmplitude = recordingAmp,
                narrations = narrations,
                onToggleRecordNarration = {
                    if (isRecordingNarration) {
                        val pcm = recorder.stopRecording()
                        if (pcm != null && pcm.isNotEmpty()) {
                            narrations = narrations + NarrationSegment(
                                label = "Narration #${narrations.size + 1}",
                                startMs = narrationStartMs,
                                pcmSamples = pcm,
                                durationMs = (pcm.size * 1000L) / 44100L
                            )
                        }
                    } else {
                        narrationStartMs = currentPositionMs
                        recorder.startRecording(onSuccess = {}, onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() })
                    }
                },
                onDeleteNarration = { id -> narrations = narrations.filterNot { it.id == id } },
                musicTracks = musicTracks,
                smartDucking = smartDucking,
                onAddMusicTrack = { uri ->
                    val fallbackTitle = "Music Track #${musicTracks.size + 1}"
                    scope.launch {
                        val (resolvedTitle, durMs) = withContext(Dispatchers.IO) {
                            MultiMusicPreviewController.inspectMusicTrackInfo(context, uri, fallbackTitle)
                        }
                        musicTracks = musicTracks + MusicLayerTrack(
                            title = resolvedTitle,
                            uri = uri,
                            startMs = 0L,
                            volume = 0.55f,
                            loop = defaultLoopMusicTracks,
                            durationMs = durMs
                        )
                    }
                },
                onUpdateMusicVolume = { id, vol -> musicTracks = musicTracks.map { if (it.id == id) it.copy(volume = vol) else it } },
                onRemoveMusicTrack = { id -> musicTracks = musicTracks.filterNot { it.id == id } },
                onToggleSmartDucking = { smartDucking = it },
                onToggleMusicLoop = { id, loopOn ->
                    musicTracks = musicTracks.map { if (it.id == id) it.copy(loop = loopOn) else it }
                },
                defaultLoopNewTracks = defaultLoopMusicTracks,
                onToggleDefaultLoopNewTracks = { loopOn ->
                    defaultLoopMusicTracks = loopOn
                    musicTracks = musicTracks.map { it.copy(loop = loopOn) }
                },
                previewingMusicTrackId = previewingMusicTrackId,
                onTogglePreviewMusicTrack = { track ->
                    if (isPlaying) videoPlayer.pause()
                    multiMusicPreview.toggleSoloPreview(track)
                },
                videoDurationMs = durationMs,
                soundEffects = soundEffects,
                onAddSoundEffect = { preset, uri, dur, freeze ->
                    soundEffects = soundEffects + SoundEffectOverlay(
                        name = preset.displayName,
                        preset = preset,
                        customUri = uri,
                        timestampMs = currentPositionMs,
                        durationMs = dur,
                        freezeFrameAtTimestamp = freeze
                    )
                },
                onRemoveSoundEffect = { id -> soundEffects = soundEffects.filterNot { it.id == id } },
                audioOption = audioOption,
                replacedAudioUri = replacedAudioUri,
                audioVolume = audioVolume,
                onSelectAudioOption = { opt ->
                    audioOption = opt
                    videoPlayer.setMute(opt == VideoAudioOption.MUTE)
                },
                onPickReplacementAudio = { replacedAudioUri = it },
                onChangeAudioVolume = { audioVolume = it },
                noiseReduceEnabled = noiseReduceEnabled,
                noiseReduceStrength = noiseReduceStrength,
                onToggleNoiseReduce = { noiseReduceEnabled = it },
                onChangeNoiseReduceStrength = { noiseReduceStrength = it },
                onExtractVideoToAudio = { fmt ->
                    val uri = currentVideoUri ?: return@VideoAudioMusicPanel
                    runAsyncTask("Extracting $fmt Audio Track...") { prog ->
                        val f = audioMixer.extractAudioFromVideo(uri, fmt, "ExtractedAudio_$timestamp", noiseReduceEnabled, prog)
                        Toast.makeText(context, "Saved audio: Music/Audio Studio/${f.name}", Toast.LENGTH_LONG).show()
                        null
                    }
                }
            )

            VideoToolSection.OVERLAYS -> VideoOverlaysVfxPanel(
                durationMs = durationMs,
                textOverlay = textOverlay,
                textPosition = textPosition,
                textAnimStyle = textAnimStyle,
                onChangeTextOverlay = { textOverlay = it },
                onSelectTextPosition = { textPosition = it },
                onSelectTextAnimStyle = { textAnimStyle = it },
                blurConfig = blurConfig,
                onUpdateBlurConfig = { blurConfig = it },
                imageOverlay = imageOverlay,
                onPickOverlayImage = { uri ->
                    scope.launch {
                        try {
                            val (iw, ih, warning) = toolkit.inspectOverlayImage(uri, videoMeta.width.coerceAtLeast(1), videoMeta.height.coerceAtLeast(1))
                            imageOverlay = imageOverlay.copy(uri = uri, imageWidth = iw, imageHeight = ih, mismatchWarning = warning, endMs = durationMs.coerceAtLeast(3000L))
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            Toast.makeText(context, "Overlay image error: ${t.localizedMessage ?: "Unsupported image"}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onUpdateImageOverlay = { imageOverlay = it },
                subtitleCues = subtitleCues,
                onImportSubtitleFile = { uri ->
                    scope.launch {
                        try {
                            subtitleCues = toolkit.parseSubtitleUri(uri)
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            Toast.makeText(context, "Subtitle import error: ${t.localizedMessage ?: "Unsupported file"}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLoadInlineSrtText = { raw -> subtitleCues = toolkit.parseSubtitleText(raw) },
                onClearSubtitles = { subtitleCues = emptyList() },
                selectedFilter = selectedFilter,
                selectedVfx = selectedVfx,
                onSelectFilter = { selectedFilter = it },
                onSelectVfx = { selectedVfx = it }
            )

            VideoToolSection.TRANSFORM -> VideoTransformPanel(
                speed = selectedSpeed,
                pitchCorrect = pitchCorrect,
                onChangeSpeed = { selectedSpeed = it },
                onTogglePitchCorrect = { pitchCorrect = it },
                resolutionPreset = resolutionPreset,
                customWidth = customWidth,
                customHeight = customHeight,
                lockAspectRatio = lockAspectRatio,
                onSelectResolution = { resolutionPreset = it },
                onChangeCustomDimensions = { w, h -> customWidth = w; customHeight = h },
                onToggleLockAspect = { lockAspectRatio = it },
                aspectRatio = aspectRatio,
                aspectFitMode = aspectFitMode,
                onSelectAspectRatio = { aspectRatio = it },
                onSelectAspectFitMode = { aspectFitMode = it },
                rotation = rotation,
                flipHorizontal = flipH,
                flipVertical = flipV,
                onSelectRotation = { rotation = it },
                onToggleFlipH = { flipH = it },
                onToggleFlipV = { flipV = it },
                reverseVideo = reverseVideo,
                onToggleReverseVideo = { reverseVideo = it }
            )

            VideoToolSection.TOOLS -> VideoConvertAndMetaPanel(
                metadata = videoMeta,
                compressorQuality = compressorQuality,
                customBitrateMbps = customBitrateMbps,
                onSelectCompressor = { compressorQuality = it },
                onChangeCustomBitrateMbps = { customBitrateMbps = it },
                onCompressAndExport = {
                    if (currentVideoUri == null) return@VideoConvertAndMetaPanel
                    runAsyncTask("Compressing Video (${compressorQuality.label})...") { prog ->
                        renderEngine.renderVideo(buildRenderRequest(), "Compressed_$timestamp", prog)
                    }
                },
                outputFormat = outputFormat,
                onSelectOutputFormat = { outputFormat = it },
                onConvertFormatNow = {
                    val uri = currentVideoUri ?: return@VideoConvertAndMetaPanel
                    runAsyncTask("Converting to .${outputFormat.extension.uppercase()}...") { prog ->
                        toolkit.exportStreamCopyRanges(uri, listOf(0L to durationMs), "Converted_$timestamp", outputFormat, onProgress = prog)
                    }
                },
                onRemoveSilenceNow = { threshDb ->
                    val uri = currentVideoUri ?: return@VideoConvertAndMetaPanel
                    runAsyncTask("Detecting & Jump-Cutting Silence...") { prog ->
                        val pcm = audioMixer.decodeUriToPcm(uri)
                        val audible = audioMixer.detectAudibleSegments(pcm, threshDb, 450L)
                        toolkit.exportStreamCopyRanges(uri, audible, "NoSilence_$timestamp", outputFormat, onProgress = prog)
                    }
                },
                onExportGifNow = { fps ->
                    val uri = currentVideoUri ?: return@VideoConvertAndMetaPanel
                    runAsyncTask("Encoding Animated GIF...") { prog ->
                        val gif = toolkit.exportVideoToGif(uri, trimStartMs, trimEndMs, fps, "Clip_$timestamp.gif", prog)
                        Toast.makeText(context, "Saved GIF to Pictures/Accessible Studio/${gif.name}", Toast.LENGTH_LONG).show()
                        null
                    }
                },
                onExtractFrameAtPlayhead = {
                    val uri = currentVideoUri ?: return@VideoConvertAndMetaPanel
                    runAsyncTask("Capturing Frame JPEG...") { _ ->
                        val jpg = toolkit.extractSingleFrame(uri, currentPositionMs, "Frame_${timestamp}.jpg")
                        Toast.makeText(context, "Saved Frame: Pictures/Accessible Studio/${jpg.name}", Toast.LENGTH_LONG).show()
                        null
                    }
                },
                onBatchExtractFrames = { intervalSec ->
                    val uri = currentVideoUri ?: return@VideoConvertAndMetaPanel
                    runAsyncTask("Batch Extracting Frames Every ${intervalSec}s...") { prog ->
                        val list = toolkit.extractBatchFrames(uri, intervalSec, "Batch_$timestamp", prog)
                        Toast.makeText(context, "Extracted ${list.size} JPEGs to Pictures/Accessible Studio", Toast.LENGTH_LONG).show()
                        null
                    }
                },
                onSaveMetadataTags = { title, artist, album, genre ->
                    videoMeta = videoMeta.copy(title = title, artist = artist, album = album, genre = genre)
                    val uri = currentVideoUri ?: return@VideoConvertAndMetaPanel
                    runAsyncTask("Saving Video Metadata Tags...") { prog ->
                        toolkit.exportStreamCopyRanges(uri, listOf(0L to durationMs), title.ifBlank { "Tagged_$timestamp" }, outputFormat, onProgress = prog)
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showExportDialog) {
        AccessibleExportDialog(
            defaultFileName = openedVideoBaseName?.takeIf { it.isNotBlank() } ?: "VideoStudio_$timestamp",
            isVideo = true,
            isExporting = isExporting,
            exportProgress = exportProgress,
            exportedFile = exportedFileResult,
            onDismiss = { showExportDialog = false; isExporting = false },
            onConfirmExport = { chosenName, _ ->
                if (currentVideoUri == null) return@AccessibleExportDialog
                isExporting = true
                exportProgress = 0.05f
                scope.launch {
                    try {
                        val file = renderEngine.renderVideo(
                            req = buildRenderRequest(),
                            outputFileName = chosenName,
                            onProgress = { exportProgress = it }
                        )
                        exportedFileResult = file
                        openedVideoBaseName = StudioPreferences.extractBaseFileName(file.name).takeIf { it.isNotBlank() }
                        isExporting = false
                        mediaRepository.refreshExports()
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        isExporting = false
                        Toast.makeText(context, "Export error: ${t.localizedMessage ?: "Encoding or storage error"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onPlayExported = { file ->
                try {
                    videoPlayer.loadUri(Uri.fromFile(file)) { videoPlayer.play() }
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
                            type = "video/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Share Exported Video").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "Could not share video: ${t.localizedMessage ?: "File location restricted"}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
