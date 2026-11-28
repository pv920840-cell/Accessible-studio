package com.example.ui.studio

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.content.FileProvider
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioExportOptions
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.data.StudioProjectStorage
import com.example.data.StudioSettingsState
import com.example.storage.StorageExportManager
import com.example.ui.components.AccessibleAudioPickerDialog
import com.example.ui.components.AccessibleExportDialog
import com.example.ui.components.AccessibleVideoPickerDialog
import com.example.ui.screens.ExportedFilesScreen
import com.example.ui.theme.LocalHighContrast
import com.example.video.CompressorQuality
import com.example.video.FullRenderRequest
import com.example.video.MultiMusicPreviewController
import com.example.video.VideoAspectRatio
import com.example.video.VideoAudioMixer
import com.example.video.VideoAudioOption
import com.example.video.VideoCutMath
import com.example.video.VideoEditSettings
import com.example.video.VideoFilter
import com.example.video.VideoFormatOption
import com.example.video.VideoPlayerManager
import com.example.video.VideoProcessor
import com.example.video.VideoRenderEngine
import com.example.video.VideoResolutionPreset
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
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioRootCoordinator(
    mediaRepository: MediaRepository,
    isHighContrast: Boolean,
    hapticsEnabled: Boolean,
    defaultExportFormat: String,
    followCursor: Boolean,
    onToggleHighContrast: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenBatchProcessing: () -> Unit,
    studioSettings: StudioSettingsState = StudioSettingsState()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useHighContrast = LocalHighContrast.current

    // Existing Backend Managers (Completely Unmodified)
    val audioProcessor = remember { WavAudioProcessor(context) }
    val audioPlayer = remember { AudioPlayerManager(context) }
    val recorderManager = remember { AudioRecorderManager(context) }
    val videoProcessor = remember { VideoProcessor(context) }
    val videoRenderEngine = remember { VideoRenderEngine(context) }
    val videoToolkit = remember { VideoToolkitProcessor(context) }
    val videoAudioMixer = remember { VideoAudioMixer(context) }
    val videoPlayer = remember { VideoPlayerManager(context) }
    val videoMultiMusicPreview = remember { MultiMusicPreviewController(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
            videoMultiMusicPreview.stopAll()
            videoPlayer.release()
            if (recorderManager.isRecording.value) recorderManager.stopRecording()
        }
    }

    LaunchedEffect(Unit) {
        mediaRepository.refreshExports()
    }

    LaunchedEffect(studioSettings) {
        recorderManager.applySettings(studioSettings)
    }

    val exportedMedia by mediaRepository.exportedFiles.collectAsState()
    val sessionProjects = remember {
        mutableStateListOf<StudioProjectSummary>().apply {
            addAll(StudioProjectStorage.loadSavedProjects(context))
        }
    }

    val allProjects = remember(sessionProjects.toList(), exportedMedia) {
        val fromExports = exportedMedia.map { media ->
            StudioProjectSummary(
                id = media.id,
                title = media.title,
                mediaType = if (media.isVideo) ProjectMediaType.VIDEO else ProjectMediaType.AUDIO,
                sourceUri = media.contentUri ?: Uri.fromFile(File(media.filePath)),
                filePath = media.filePath,
                durationMillis = media.durationMillis,
                fileSizeBytes = media.fileSizeBytes,
                lastModifiedMillis = media.createdAtMillis,
                statusBadge = "Exported"
            )
        }
        (sessionProjects + fromExports).distinctBy { it.id }.sortedByDescending { it.lastModifiedMillis }
    }

    var destination by remember { mutableStateOf(StudioRootDestination.HOME) }
    var activeProject by remember { mutableStateOf<StudioProjectSummary?>(null) }
    var activeAudioSession by remember { mutableStateOf<AudioProjectSessionState?>(null) }
    var activeVideoSession by remember { mutableStateOf<VideoProjectSessionState?>(null) }
    var activeFeatureWorkspace by remember { mutableStateOf<ActiveFeatureWorkspace?>(null) }
    var pendingAudioToolAfterLoad by remember { mutableStateOf<AudioFeatureTool?>(null) }
    var pendingVideoToolAfterLoad by remember { mutableStateOf<VideoFeatureTool?>(null) }
    var pendingVideoFeatureAfterLoad by remember { mutableStateOf<VideoStudioFeatureButton?>(null) }
    var showAudioToolSourcePrompt by remember { mutableStateOf<AudioFeatureTool?>(null) }
    var showVideoToolSourcePrompt by remember { mutableStateOf<Pair<VideoFeatureTool, VideoStudioFeatureButton?>?>(null) }
    var createBackDestination by remember { mutableStateOf(StudioRootDestination.HOME) }

    // Step 2 Create Flow State
    var createSelectedType by remember { mutableStateOf<ProjectMediaType?>(ProjectMediaType.AUDIO) }
    var createNameDraft by remember { mutableStateOf("") }
    var isCreateLoading by remember { mutableStateOf(false) }
    var createLoadingMsg by remember { mutableStateOf("") }
    var showCreateAudioPickerDialog by remember { mutableStateOf(false) }
    var showCreateVideoPickerDialog by remember { mutableStateOf(false) }

    // Hub Export Modal State
    var showHubExportDialog by remember { mutableStateOf(false) }
    var isHubExporting by remember { mutableStateOf(false) }
    var hubExportProgress by remember { mutableFloatStateOf(0f) }
    var hubExportedFile by remember { mutableStateOf<File?>(null) }

    val audioIsPlaying by audioPlayer.isPlaying.collectAsState()
    val audioDurationMs by audioPlayer.durationMs.collectAsState()
    val videoIsPlaying by videoPlayer.isPlaying.collectAsState()
    val videoDurationMs by videoPlayer.durationMs.collectAsState()
    var hubBaseSpeechBins by remember(activeVideoSession?.sourceUri) { mutableStateOf(BooleanArray(0)) }

    LaunchedEffect(activeVideoSession?.sourceUri) {
        val uri = activeVideoSession?.sourceUri ?: return@LaunchedEffect
        try {
            val pcm = withContext(Dispatchers.IO) { videoAudioMixer.decodeUriToPcm(uri) }
            hubBaseSpeechBins = MultiMusicPreviewController.computeSpeechActivityBins(pcm)
        } catch (_: Throwable) {
            hubBaseSpeechBins = BooleanArray(0)
        }
    }

    LaunchedEffect(
        destination,
        videoIsPlaying,
        activeVideoSession,
        hubBaseSpeechBins
    ) {
        val vs = activeVideoSession
        if (destination == StudioRootDestination.PROJECT_HUB && vs != null) {
            fun syncNow() {
                val posMs = videoPlayer.currentPositionMs.value
                videoMultiMusicPreview.syncTimelinePlayback(
                    isVideoPlaying = videoIsPlaying,
                    videoPositionMs = posMs,
                    musicTracks = vs.musicTracks,
                    smartDucking = vs.smartDucking,
                    narrations = vs.narrations,
                    audioOption = vs.audioOption,
                    baseAudioActiveAtPlayhead = MultiMusicPreviewController.isBinActive(hubBaseSpeechBins, posMs)
                )
            }
            syncNow()
            while (isActive && videoIsPlaying) {
                delay(200L)
                syncNow()
            }
        }
    }

    fun upsertSessionProject(summary: StudioProjectSummary) {
        val idx = sessionProjects.indexOfFirst { it.id == summary.id }
        if (idx >= 0) sessionProjects[idx] = summary else sessionProjects.add(0, summary)
        activeProject = summary
    }

    fun openAudioProjectIntoHub(
        project: StudioProjectSummary,
        preloadedPcm: ShortArray? = null,
        targetAudioTool: AudioFeatureTool? = null
    ) {
        scope.launch {
            try {
                audioPlayer.stop()
                videoMultiMusicPreview.stopAll()
                videoPlayer.pause()
                val pcm = preloadedPcm ?: run {
                    val uri = project.sourceUri ?: project.filePath?.let { Uri.fromFile(File(it)) }
                    if (uri != null) audioProcessor.decodeUriToPcm(uri) else ShortArray(0)
                }
                val sr = StudioAudioProcessor.SAMPLE_RATE
                val durMs = ((pcm.size.toDouble() / sr) * 1000.0).toLong()
                val baseName = StudioPreferences.extractBaseFileName(project.title).takeIf { it.isNotBlank() }
                audioPlayer.loadPcmData(pcm, sr)
                activeAudioSession = AudioProjectSessionState(
                    projectId = project.id,
                    title = project.title,
                    baseFileName = baseName,
                    pcm = pcm,
                    sampleRate = sr,
                    trimStartMs = 0L,
                    trimEndMs = durMs,
                    hasUnsavedChanges = false
                )
                activeVideoSession = null
                upsertSessionProject(project.copy(durationMillis = durMs))
                if (targetAudioTool != null) {
                    activeFeatureWorkspace = ActiveFeatureWorkspace.AudioTool(targetAudioTool)
                    destination = StudioRootDestination.FEATURE_WORKSPACE
                } else {
                    destination = StudioRootDestination.PROJECT_HUB
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Toast.makeText(
                    context,
                    "Could not open audio project: ${t.localizedMessage ?: "Unsupported or unreadable file"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun openVideoProjectIntoHub(
        project: StudioProjectSummary,
        targetVideoTool: VideoFeatureTool? = null,
        targetVideoFeature: VideoStudioFeatureButton? = null
    ) {
        scope.launch {
            try {
                audioPlayer.stop()
                videoMultiMusicPreview.stopAll()
                val uri = project.sourceUri ?: project.filePath?.let { Uri.fromFile(File(it)) }
                val baseName = StudioPreferences.extractBaseFileName(project.title).takeIf { it.isNotBlank() }
                val meta = if (uri != null) videoToolkit.inspectDetailedMetadata(uri) else com.example.video.VideoMetadataDetails()
                val resolvedDur = meta.durationMs.takeIf { it > 0L } ?: project.durationMillis
                if (uri != null) {
                    videoPlayer.loadUri(uri) {}
                }
                activeVideoSession = VideoProjectSessionState(
                    projectId = project.id,
                    title = project.title,
                    baseFileName = baseName,
                    sourceUri = uri,
                    durationMs = resolvedDur,
                    metadata = meta,
                    trimStartMs = 0L,
                    trimEndMs = resolvedDur.coerceAtLeast(1000L)
                )
                activeAudioSession = null
                upsertSessionProject(project.copy(durationMillis = resolvedDur))
                if (targetVideoFeature != null) {
                    activeFeatureWorkspace = ActiveFeatureWorkspace.VideoTool(targetVideoFeature.toParentTool(), targetVideoFeature)
                    destination = StudioRootDestination.FEATURE_WORKSPACE
                } else if (targetVideoTool != null) {
                    activeFeatureWorkspace = ActiveFeatureWorkspace.VideoTool(targetVideoTool)
                    destination = StudioRootDestination.FEATURE_WORKSPACE
                } else {
                    destination = StudioRootDestination.PROJECT_HUB
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Toast.makeText(
                    context,
                    "Could not open video project: ${t.localizedMessage ?: "Unsupported or unreadable file"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun saveCurrentAudioProject() {
        val proj = activeProject ?: return
        val aSession = activeAudioSession
        val stampNow = System.currentTimeMillis()
        if (aSession != null) {
            activeAudioSession = aSession.copy(hasUnsavedChanges = false)
        }
        val optimistic = proj.copy(
            lastModifiedMillis = stampNow,
            statusBadge = "Saved"
        )
        upsertSessionProject(optimistic)
        scope.launch {
            val savedSummary = withContext(Dispatchers.IO) {
                StudioProjectStorage.saveAudioProject(
                    context = context,
                    project = optimistic,
                    pcm = aSession?.pcm ?: ShortArray(0),
                    sampleRate = aSession?.sampleRate ?: StudioAudioProcessor.SAMPLE_RATE,
                    writeWav = { file, samples, sr, ch ->
                        audioProcessor.writeWavFile(file, samples, sr, ch)
                    }
                )
            }
            upsertSessionProject(savedSummary)
        }
        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
        AccessibilityHelper.announceForAccessibility(context, "Project saved")
        Toast.makeText(context, "Project saved", Toast.LENGTH_SHORT).show()
    }

    fun saveCurrentVideoProject() {
        val proj = activeProject ?: return
        val vSession = activeVideoSession
        val stampNow = System.currentTimeMillis()
        if (vSession != null) {
            activeVideoSession = vSession.copy(hasUnsavedChanges = false)
        }
        val optimistic = proj.copy(
            sourceUri = vSession?.sourceUri ?: proj.sourceUri,
            durationMillis = (vSession?.durationMs ?: 0L).takeIf { it > 0L } ?: proj.durationMillis,
            lastModifiedMillis = stampNow,
            statusBadge = "Saved"
        )
        upsertSessionProject(optimistic)
        scope.launch {
            val savedSummary = withContext(Dispatchers.IO) {
                StudioProjectStorage.saveVideoProject(
                    context = context,
                    project = optimistic,
                    sourceUri = vSession?.sourceUri,
                    durationMs = vSession?.durationMs ?: optimistic.durationMillis
                )
            }
            upsertSessionProject(savedSummary)
        }
        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
        AccessibilityHelper.announceForAccessibility(context, "Project saved")
        Toast.makeText(context, "Project saved", Toast.LENGTH_SHORT).show()
    }

    fun startBlankAudioProject(targetAudioTool: AudioFeatureTool? = null) {
        val blank = ShortArray(5 * StudioAudioProcessor.SAMPLE_RATE)
        val title = createNameDraft.trim().ifBlank { "Blank Audio Project" }
        val summary = StudioProjectSummary(
            id = "audio_blank_${UUID.randomUUID()}",
            title = title,
            mediaType = ProjectMediaType.AUDIO,
            sourceUri = null,
            filePath = null,
            durationMillis = 5000L,
            fileSizeBytes = blank.size * 2L,
            lastModifiedMillis = System.currentTimeMillis(),
            statusBadge = "Blank"
        )
        createNameDraft = ""
        openAudioProjectIntoHub(summary, preloadedPcm = blank, targetAudioTool = targetAudioTool)
    }

    fun startDemoVideoProject(
        targetVideoTool: VideoFeatureTool? = null,
        targetVideoFeature: VideoStudioFeatureButton? = null
    ) {
        scope.launch {
            try {
                isCreateLoading = true
                createLoadingMsg = "Generating demo video clip..."
                val demoFile = videoProcessor.generateDemoVideoClip(settings = VideoEditSettings(), durationSec = 6) {}
                isCreateLoading = false
                createLoadingMsg = ""
                val title = createNameDraft.trim().ifBlank { "Demo Video Project" }
                val summary = StudioProjectSummary(
                    id = "video_demo_${UUID.randomUUID()}",
                    title = title,
                    mediaType = ProjectMediaType.VIDEO,
                    sourceUri = Uri.fromFile(demoFile),
                    filePath = demoFile.absolutePath,
                    durationMillis = 6000L,
                    fileSizeBytes = demoFile.length(),
                    lastModifiedMillis = System.currentTimeMillis(),
                    statusBadge = "Demo"
                )
                createNameDraft = ""
                openVideoProjectIntoHub(
                    project = summary,
                    targetVideoTool = targetVideoTool,
                    targetVideoFeature = targetVideoFeature
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                isCreateLoading = false
                createLoadingMsg = ""
                Toast.makeText(
                    context,
                    "Could not create demo video: ${t.localizedMessage ?: "Encoder unavailable"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun handleSelectedCreateAudioUri(uri: Uri, explicitTitle: String? = null) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}
        val nextTool = pendingAudioToolAfterLoad
        pendingAudioToolAfterLoad = null
        scope.launch {
            try {
                isCreateLoading = true
                val displayName = explicitTitle?.takeIf { it.isNotBlank() }
                    ?: audioProcessor.queryAudioDisplayName(uri)
                    ?: "Imported Audio"
                createLoadingMsg = "Decoding $displayName..."
                val pcm = audioProcessor.decodeUriToPcm(uri)
                isCreateLoading = false
                createLoadingMsg = ""
                if (pcm.isNotEmpty()) {
                    val chosenTitle = createNameDraft.trim().ifBlank { displayName }
                    val durMs = ((pcm.size.toDouble() / StudioAudioProcessor.SAMPLE_RATE) * 1000.0).toLong()
                    val summary = StudioProjectSummary(
                        id = "audio_${UUID.randomUUID()}",
                        title = chosenTitle,
                        mediaType = ProjectMediaType.AUDIO,
                        sourceUri = uri,
                        filePath = null,
                        durationMillis = durMs,
                        fileSizeBytes = pcm.size * 2L,
                        lastModifiedMillis = System.currentTimeMillis(),
                        statusBadge = "Active"
                    )
                    createNameDraft = ""
                    openAudioProjectIntoHub(summary, preloadedPcm = pcm, targetAudioTool = nextTool)
                } else {
                    Toast.makeText(context, "Could not decode selected audio file.", Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                isCreateLoading = false
                createLoadingMsg = ""
                Toast.makeText(
                    context,
                    "Could not import audio: ${t.localizedMessage ?: "Out of memory or unsupported format"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Audio & Video pickers are presented cleanly via AccessibleAudioPickerDialog & AccessibleVideoPickerDialog (no Navigate up button, zero permissions)

    // Top-Level Back Navigation
    if (destination != StudioRootDestination.HOME && destination != StudioRootDestination.FEATURE_WORKSPACE) {
        BackHandler {
            audioPlayer.stop()
            videoMultiMusicPreview.stopAll()
            videoPlayer.pause()
            destination = StudioRootDestination.HOME
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (destination == StudioRootDestination.HOME || destination == StudioRootDestination.EXPORTS) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (destination == StudioRootDestination.EXPORTS) {
                                IconButton(
                                    onClick = { destination = StudioRootDestination.HOME },
                                    modifier = Modifier.size(48.dp).testTag("btn_exports_back_home")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Column {
                                Text(
                                    text = if (destination == StudioRootDestination.EXPORTS) "Exported Files" else "Accessible Studio",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "v${com.example.BuildConfig.VERSION_NAME} Offline Audio & Video Studio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleHighContrast,
                            modifier = Modifier.size(48.dp).testTag("btn_top_contrast_toggle")
                                .semantics { contentDescription = if (useHighContrast) "Turn off High Contrast" else "Turn on High Contrast" }
                        ) {
                            Icon(Icons.Default.InvertColors, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (useHighContrast) Color.Black else MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (destination) {
                StudioRootDestination.HOME -> {
                    StudioHomeScreen(
                        projects = allProjects,
                        onCreateNewProject = {
                            pendingAudioToolAfterLoad = null
                            pendingVideoToolAfterLoad = null
                            pendingVideoFeatureAfterLoad = null
                            createBackDestination = StudioRootDestination.HOME
                            createNameDraft = ""
                            createSelectedType = ProjectMediaType.AUDIO
                            destination = StudioRootDestination.CREATE_PROJECT
                        },
                        onOpenExistingProject = { project ->
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            if (project.mediaType == ProjectMediaType.VIDEO) {
                                openVideoProjectIntoHub(project)
                            } else {
                                openAudioProjectIntoHub(project)
                            }
                        },
                        onOpenExports = { destination = StudioRootDestination.EXPORTS },
                        onOpenAbout = onOpenAbout,
                        onOpenSettings = onOpenSettings,
                        onOpenWhatsNew = onOpenWhatsNew,
                        onOpenHelp = onOpenHelp,
                        onOpenAudioEditor = {
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            destination = StudioRootDestination.AUDIO_EDITOR
                        },
                        onOpenVideoEditor = {
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            destination = StudioRootDestination.VIDEO_EDITOR
                        },
                        hasActiveProject = activeProject != null,
                        activeProjectTitle = activeProject?.title,
                        onSaveActiveProject = {
                            val proj = activeProject
                            if (proj?.mediaType == ProjectMediaType.VIDEO) {
                                saveCurrentVideoProject()
                            } else {
                                saveCurrentAudioProject()
                            }
                        },
                        onResumeActiveProject = {
                            if (activeProject != null) {
                                destination = StudioRootDestination.PROJECT_HUB
                            }
                        }
                    )
                }

                StudioRootDestination.AUDIO_EDITOR -> {
                    val activeAudioSummary = activeProject?.takeIf { it.mediaType == ProjectMediaType.AUDIO }
                    val audioProjects = allProjects.filter { it.mediaType == ProjectMediaType.AUDIO }
                    AudioEditorScreen(
                        activeAudioProject = activeAudioSummary,
                        hasUnsavedChanges = activeAudioSession?.hasUnsavedChanges ?: false,
                        audioProjects = audioProjects,
                        onBack = { destination = StudioRootDestination.HOME },
                        onCreateOrPickAudioProject = {
                            pendingAudioToolAfterLoad = null
                            createBackDestination = StudioRootDestination.AUDIO_EDITOR
                            createNameDraft = ""
                            createSelectedType = ProjectMediaType.AUDIO
                            destination = StudioRootDestination.CREATE_PROJECT
                        },
                        onOpenExistingAudioProject = { proj ->
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            openAudioProjectIntoHub(proj)
                        },
                        onOpenAudioTool = { tool ->
                            audioPlayer.stop()
                            if (activeAudioSession != null && activeProject?.mediaType == ProjectMediaType.AUDIO) {
                                activeFeatureWorkspace = ActiveFeatureWorkspace.AudioTool(tool)
                                destination = StudioRootDestination.FEATURE_WORKSPACE
                            } else {
                                showAudioToolSourcePrompt = tool
                            }
                        },
                        onOpenBatchProcessing = onOpenBatchProcessing,
                        onSaveProject = { saveCurrentAudioProject() },
                        onExportAudio = {
                            val aSession = activeAudioSession
                            if (aSession == null || aSession.pcm.isEmpty()) {
                                Toast.makeText(context, "Audio track is empty. Record or import audio first.", Toast.LENGTH_SHORT).show()
                            } else {
                                hubExportedFile = null
                                showHubExportDialog = true
                            }
                        }
                    )
                }

                StudioRootDestination.VIDEO_EDITOR -> {
                    val activeVideoSummary = activeProject?.takeIf { it.mediaType == ProjectMediaType.VIDEO }
                    val videoProjects = allProjects.filter { it.mediaType == ProjectMediaType.VIDEO }
                    VideoEditorScreen(
                        activeVideoProject = activeVideoSummary,
                        hasUnsavedChanges = activeVideoSession?.hasUnsavedChanges ?: false,
                        videoProjects = videoProjects,
                        onBack = { destination = StudioRootDestination.HOME },
                        onCreateOrPickVideoProject = {
                            pendingVideoToolAfterLoad = null
                            pendingVideoFeatureAfterLoad = null
                            createBackDestination = StudioRootDestination.VIDEO_EDITOR
                            createNameDraft = ""
                            createSelectedType = ProjectMediaType.VIDEO
                            destination = StudioRootDestination.CREATE_PROJECT
                        },
                        onOpenExistingVideoProject = { proj ->
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                            openVideoProjectIntoHub(proj)
                        },
                        onOpenVideoTool = { tool ->
                            videoPlayer.pause()
                            videoMultiMusicPreview.stopAll()
                            if (activeVideoSession != null && activeProject?.mediaType == ProjectMediaType.VIDEO) {
                                activeFeatureWorkspace = ActiveFeatureWorkspace.VideoTool(tool)
                                destination = StudioRootDestination.FEATURE_WORKSPACE
                            } else {
                                showVideoToolSourcePrompt = tool to null
                            }
                        },
                        onOpenVideoFeature = { feat ->
                            videoPlayer.pause()
                            videoMultiMusicPreview.stopAll()
                            if (activeVideoSession != null && activeProject?.mediaType == ProjectMediaType.VIDEO) {
                                activeFeatureWorkspace = ActiveFeatureWorkspace.VideoTool(feat.toParentTool(), feat)
                                destination = StudioRootDestination.FEATURE_WORKSPACE
                            } else {
                                showVideoToolSourcePrompt = feat.toParentTool() to feat
                            }
                        },
                        onSaveProject = { saveCurrentVideoProject() },
                        onExportVideo = {
                            videoPlayer.pause()
                            videoMultiMusicPreview.stopAll()
                            hubExportedFile = null
                            showHubExportDialog = true
                        }
                    )
                }

                StudioRootDestination.CREATE_PROJECT -> {
                    CreateProjectScreen(
                        selectedType = createSelectedType,
                        projectNameDraft = createNameDraft,
                        isLoadingMedia = isCreateLoading,
                        loadingMessage = createLoadingMsg,
                        onSelectType = { createSelectedType = it },
                        onProjectNameChange = { createNameDraft = it },
                        onBrowseAndSelectFile = {
                            when (createSelectedType) {
                                ProjectMediaType.AUDIO -> showCreateAudioPickerDialog = true
                                ProjectMediaType.VIDEO -> showCreateVideoPickerDialog = true
                                null -> Unit
                            }
                        },
                        onStartQuickBlankOrDemo = {
                            when (createSelectedType) {
                                ProjectMediaType.AUDIO -> {
                                    val target = pendingAudioToolAfterLoad
                                    pendingAudioToolAfterLoad = null
                                    startBlankAudioProject(targetAudioTool = target)
                                }
                                ProjectMediaType.VIDEO -> {
                                    val target = pendingVideoToolAfterLoad
                                    val targetFeat = pendingVideoFeatureAfterLoad
                                    pendingVideoToolAfterLoad = null
                                    pendingVideoFeatureAfterLoad = null
                                    startDemoVideoProject(targetVideoTool = target, targetVideoFeature = targetFeat)
                                }
                                null -> Unit
                            }
                        },
                        onBack = {
                            pendingAudioToolAfterLoad = null
                            pendingVideoToolAfterLoad = null
                            pendingVideoFeatureAfterLoad = null
                            destination = createBackDestination
                        }
                    )
                }

                StudioRootDestination.PROJECT_HUB -> {
                    val proj = activeProject
                    if (proj == null) {
                        destination = StudioRootDestination.HOME
                    } else if (proj.mediaType == ProjectMediaType.VIDEO) {
                        val vSession = activeVideoSession
                        val dur = (vSession?.durationMs ?: 0L).coerceAtLeast(videoDurationMs)
                        ProjectHubScreen(
                            project = proj,
                            durationMs = dur,
                            currentPositionMs = 0L,
                            positionFlow = videoPlayer.currentPositionMs,
                            isPlaying = videoIsPlaying,
                            hasUnsavedChanges = vSession?.hasUnsavedChanges ?: false,
                            onTogglePreviewPlay = {
                                if (videoIsPlaying) videoPlayer.pause() else videoPlayer.play()
                            },
                            onOpenAudioTool = {},
                            onOpenVideoTool = { tool ->
                                videoPlayer.pause()
                                videoMultiMusicPreview.stopSoloPreview()
                                videoMultiMusicPreview.pauseTimelineTracks()
                                activeFeatureWorkspace = ActiveFeatureWorkspace.VideoTool(tool)
                                destination = StudioRootDestination.FEATURE_WORKSPACE
                            },
                            onOpenVideoFeature = { feat ->
                                videoPlayer.pause()
                                videoMultiMusicPreview.stopSoloPreview()
                                videoMultiMusicPreview.pauseTimelineTracks()
                                activeFeatureWorkspace = ActiveFeatureWorkspace.VideoTool(feat.toParentTool(), feat)
                                destination = StudioRootDestination.FEATURE_WORKSPACE
                            },
                            onSaveProject = { saveCurrentVideoProject() },
                            onExportProject = {
                                videoPlayer.pause()
                                videoMultiMusicPreview.stopSoloPreview()
                                videoMultiMusicPreview.pauseTimelineTracks()
                                hubExportedFile = null
                                showHubExportDialog = true
                            },
                            onBackToHome = {
                                videoMultiMusicPreview.stopAll()
                                videoPlayer.pause()
                                destination = StudioRootDestination.HOME
                            }
                        )
                    } else {
                        val aSession = activeAudioSession
                        val dur = audioDurationMs.takeIf { it > 0L } ?: (aSession?.let { ((it.pcm.size.toDouble() / it.sampleRate) * 1000.0).toLong() } ?: 0L)
                        ProjectHubScreen(
                            project = proj,
                            durationMs = dur,
                            currentPositionMs = 0L,
                            positionFlow = audioPlayer.currentPositionMs,
                            isPlaying = audioIsPlaying,
                            hasUnsavedChanges = aSession?.hasUnsavedChanges ?: false,
                            onTogglePreviewPlay = {
                                if (audioIsPlaying) {
                                    audioPlayer.pause()
                                } else if (aSession != null && aSession.pcm.isNotEmpty()) {
                                    if (!audioPlayer.hasPcmData()) audioPlayer.loadPcmData(aSession.pcm, aSession.sampleRate)
                                    audioPlayer.play()
                                }
                            },
                            onOpenAudioTool = { tool ->
                                audioPlayer.stop()
                                activeFeatureWorkspace = ActiveFeatureWorkspace.AudioTool(tool)
                                destination = StudioRootDestination.FEATURE_WORKSPACE
                            },
                            onOpenVideoTool = {},
                            onOpenBatchProcessing = onOpenBatchProcessing,
                            onSaveProject = { saveCurrentAudioProject() },
                            onExportProject = {
                                if (aSession == null || aSession.pcm.isEmpty()) {
                                    Toast.makeText(context, "Audio track is empty. Record or import audio first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    hubExportedFile = null
                                    showHubExportDialog = true
                                }
                            },
                            onBackToHome = {
                                audioPlayer.stop()
                                destination = StudioRootDestination.HOME
                            }
                        )
                    }
                }

                StudioRootDestination.FEATURE_WORKSPACE -> {
                    when (val ws = activeFeatureWorkspace) {
                        is ActiveFeatureWorkspace.AudioTool -> {
                            val aSession = activeAudioSession
                            if (aSession == null) {
                                destination = StudioRootDestination.PROJECT_HUB
                            } else {
                                AudioFeatureWorkspaceScreen(
                                    tool = ws.tool,
                                    session = aSession,
                                    projectSourceUri = activeProject?.sourceUri,
                                    audioProcessor = audioProcessor,
                                    playerManager = audioPlayer,
                                    recorderManager = recorderManager,
                                    mediaRepository = mediaRepository,
                                    hapticsEnabled = hapticsEnabled,
                                    isHighContrast = isHighContrast,
                                    defaultExportFormat = defaultExportFormat,
                                    followCursor = followCursor,
                                    onToggleHighContrast = onToggleHighContrast,
                                    onOpenAbout = onOpenAbout,
                                    onOpenSettings = onOpenSettings,
                                    onOpenHelp = onOpenHelp,
                                    onOpenWhatsNew = onOpenWhatsNew,
                                    onOpenBatchProcessing = onOpenBatchProcessing,
                                    visualModeEnabled = studioSettings.visualModeEnabled,
                                    showZoomControls = studioSettings.showZoomControls,
                                    showWaveformDisplay = studioSettings.showWaveformDisplay,
                                    recordingSettings = studioSettings,
                                    onSaveAndReturn = { updated ->
                                        try {
                                            activeAudioSession = updated
                                            val safeSr = updated.sampleRate.coerceAtLeast(8000)
                                            val newDur = ((updated.pcm.size.toDouble() / safeSr) * 1000.0).toLong()
                                            activeProject?.let { cur ->
                                                upsertSessionProject(
                                                    cur.copy(
                                                        title = updated.title,
                                                        durationMillis = newDur,
                                                        lastModifiedMillis = System.currentTimeMillis(),
                                                        statusBadge = if (updated.hasUnsavedChanges) "Edited" else cur.statusBadge
                                                    )
                                                )
                                            }
                                            try {
                                                audioPlayer.loadPcmData(updated.pcm, safeSr)
                                            } catch (_: Throwable) {}
                                            destination = StudioRootDestination.PROJECT_HUB
                                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                                            Toast.makeText(context, "Saved workspace progress", Toast.LENGTH_SHORT).show()
                                        } catch (t: Throwable) {
                                            destination = StudioRootDestination.PROJECT_HUB
                                            Toast.makeText(
                                                context,
                                                "Save warning: ${t.localizedMessage ?: "Audio reload failed"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    onCancelBack = {
                                        try {
                                            aSession.let { audioPlayer.loadPcmData(it.pcm, it.sampleRate.coerceAtLeast(8000)) }
                                        } catch (_: Throwable) {}
                                        destination = StudioRootDestination.PROJECT_HUB
                                    }
                                )
                            }
                        }

                        is ActiveFeatureWorkspace.VideoTool -> {
                            val vSession = activeVideoSession
                            if (vSession == null) {
                                destination = StudioRootDestination.PROJECT_HUB
                            } else {
                                VideoFeatureWorkspaceScreen(
                                    tool = ws.tool,
                                    session = vSession,
                                    videoPlayer = videoPlayer,
                                    renderEngine = videoRenderEngine,
                                    toolkit = videoToolkit,
                                    audioMixer = videoAudioMixer,
                                    recorder = recorderManager,
                                    mediaRepository = mediaRepository,
                                    hapticsEnabled = hapticsEnabled,
                                    onSaveAndReturn = { updated ->
                                        activeVideoSession = updated
                                        activeProject?.let { cur ->
                                            upsertSessionProject(
                                                cur.copy(
                                                    title = updated.title,
                                                    sourceUri = updated.sourceUri,
                                                    durationMillis = updated.durationMs,
                                                    lastModifiedMillis = System.currentTimeMillis(),
                                                    statusBadge = if (updated.hasUnsavedChanges) "Edited" else cur.statusBadge
                                                )
                                            )
                                        }
                                        destination = StudioRootDestination.PROJECT_HUB
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                                        Toast.makeText(context, "Saved video workspace progress", Toast.LENGTH_SHORT).show()
                                    },
                                    onCancelBack = {
                                        destination = StudioRootDestination.PROJECT_HUB
                                    },
                                    onNavigateToExports = {
                                        destination = StudioRootDestination.EXPORTS
                                    },
                                    multiMusicPreview = videoMultiMusicPreview,
                                    initialFeature = ws.initialFeature,
                                    onApplyEdits = { updated ->
                                        activeVideoSession = updated
                                        activeProject?.let { cur ->
                                            upsertSessionProject(
                                                cur.copy(
                                                    title = updated.title,
                                                    sourceUri = updated.sourceUri,
                                                    durationMillis = updated.durationMs,
                                                    lastModifiedMillis = System.currentTimeMillis(),
                                                    statusBadge = if (updated.hasUnsavedChanges) "Edited" else cur.statusBadge
                                                )
                                            )
                                        }
                                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                                    }
                                )
                            }
                        }

                        null -> destination = StudioRootDestination.PROJECT_HUB
                    }
                }

                StudioRootDestination.EXPORTS -> {
                    ExportedFilesScreen(
                        mediaRepository = mediaRepository,
                        hapticsEnabled = hapticsEnabled,
                        onNavigateToAudio = {
                            createSelectedType = ProjectMediaType.AUDIO
                            destination = StudioRootDestination.CREATE_PROJECT
                        },
                        onNavigateToVideo = {
                            createSelectedType = ProjectMediaType.VIDEO
                            destination = StudioRootDestination.CREATE_PROJECT
                        }
                    )
                }
            }
        }
    }

    // Export Dialog from Project Hub
    if (showHubExportDialog) {
        val proj = activeProject
        val isVideo = proj?.mediaType == ProjectMediaType.VIDEO
        val stamp = remember(showHubExportDialog) { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        val defaultName = if (isVideo) {
            activeVideoSession?.baseFileName ?: "VideoStudio_$stamp"
        } else {
            activeAudioSession?.baseFileName ?: "AudioStudio_$stamp"
        }

        AccessibleExportDialog(
            defaultFileName = defaultName,
            isVideo = isVideo,
            isExporting = isHubExporting,
            exportProgress = hubExportProgress,
            exportedFile = hubExportedFile,
            initialFormat = defaultExportFormat,
            initialVideoFormat = activeVideoSession?.outputFormat ?: VideoFormatOption.MP4,
            initialVideoQualityPreset = activeVideoSession?.resolutionPreset ?: VideoResolutionPreset.ORIGINAL,
            initialCompressorQuality = activeVideoSession?.compressorQuality ?: CompressorQuality.HIGH,
            onDismiss = { showHubExportDialog = false; isHubExporting = false },
            onConfirmVideoExport = { chosenName, vidFmt, vidRes, vidQual ->
                val vs = activeVideoSession
                if (vs == null) {
                    Toast.makeText(context, "No active video project to export.", Toast.LENGTH_LONG).show()
                    return@AccessibleExportDialog
                }
                val updatedVs = vs.copy(
                    outputFormat = vidFmt,
                    resolutionPreset = vidRes,
                    compressorQuality = vidQual
                )
                activeVideoSession = updatedVs
                isHubExporting = true
                hubExportProgress = 0.05f
                scope.launch {
                    try {
                        val sourceUri = updatedVs.sourceUri ?: throw IllegalStateException("Please select or create a video clip before exporting.")
                        val keepRanges = VideoCutMath.computeMultiTrimKeepRanges(
                            multiTrimClips = updatedVs.multiTrimClips,
                            cutSegments = updatedVs.cutSegments,
                            fallbackStartMs = updatedVs.trimStartMs,
                            fallbackEndMs = updatedVs.trimEndMs
                        )
                        val canUseStreamCopy = updatedVs.streamCopyTrim &&
                            updatedVs.selectedSpeed == 1.0f &&
                            !updatedVs.reverseVideo &&
                            updatedVs.selectedFilter == VideoFilter.NORMAL &&
                            updatedVs.selectedVfx == VideoVfx.NONE &&
                            updatedVs.aspectRatio == VideoAspectRatio.ORIGINAL &&
                            updatedVs.resolutionPreset == VideoResolutionPreset.ORIGINAL &&
                            updatedVs.rotation == VideoRotation.DEG_0 &&
                            !updatedVs.flipH &&
                            !updatedVs.flipV &&
                            updatedVs.textOverlay.isBlank() &&
                            !updatedVs.blurConfig.enabled &&
                            updatedVs.imageOverlay.uri == null &&
                            updatedVs.subtitleCues.isEmpty() &&
                            updatedVs.soundEffects.isEmpty() &&
                            updatedVs.narrations.isEmpty() &&
                            updatedVs.musicTracks.isEmpty() &&
                            updatedVs.audioOption == VideoAudioOption.ORIGINAL_AUDIO &&
                            updatedVs.audioVolume == 1.0f &&
                            !updatedVs.noiseReduceEnabled

                        val outFile = if (canUseStreamCopy) {
                            videoToolkit.exportStreamCopyRanges(
                                sourceUri = sourceUri,
                                keepRangesMs = keepRanges,
                                outputFileName = chosenName,
                                formatOption = updatedVs.outputFormat,
                                onProgress = { hubExportProgress = it }
                            )
                        } else {
                            val req = FullRenderRequest(
                                sourceUri = sourceUri,
                                keepRangesMs = keepRanges,
                                speed = updatedVs.selectedSpeed,
                                pitchCorrect = updatedVs.pitchCorrect,
                                reverseVideo = updatedVs.reverseVideo,
                                filter = updatedVs.selectedFilter,
                                vfx = updatedVs.selectedVfx,
                                aspectRatio = updatedVs.aspectRatio,
                                aspectFitMode = updatedVs.aspectFitMode,
                                resolutionPreset = updatedVs.resolutionPreset,
                                customWidth = updatedVs.customWidth,
                                customHeight = updatedVs.customHeight,
                                rotation = updatedVs.rotation,
                                flipHorizontal = updatedVs.flipH,
                                flipVertical = updatedVs.flipV,
                                compressorQuality = updatedVs.compressorQuality,
                                customBitrateBps = (updatedVs.customBitrateMbps * 1_000_000f).toInt(),
                                textOverlay = updatedVs.textOverlay,
                                textPosition = updatedVs.textPosition,
                                textAnimStyle = updatedVs.textAnimStyle,
                                blurConfig = updatedVs.blurConfig,
                                imageOverlay = updatedVs.imageOverlay,
                                subtitleCues = updatedVs.subtitleCues,
                                soundEffects = updatedVs.soundEffects,
                                narrations = updatedVs.narrations,
                                musicTracks = updatedVs.musicTracks,
                                smartDucking = updatedVs.smartDucking,
                                audioOption = updatedVs.audioOption,
                                replacedAudioUri = updatedVs.replacedAudioUri,
                                audioVolume = updatedVs.audioVolume,
                                noiseReductionEnabled = updatedVs.noiseReduceEnabled,
                                noiseReductionStrength = updatedVs.noiseReduceStrength,
                                outputFormat = updatedVs.outputFormat,
                                metadataTitle = updatedVs.metadata.title,
                                metadataArtist = updatedVs.metadata.artist,
                                metadataAlbum = updatedVs.metadata.album
                            )
                            videoRenderEngine.renderVideo(req, chosenName) { hubExportProgress = it }
                        }
                        hubExportedFile = outFile
                        isHubExporting = false
                        mediaRepository.refreshExports()
                        activeProject?.let { cur ->
                            upsertSessionProject(cur.copy(title = outFile.name, filePath = outFile.absolutePath, statusBadge = "Exported"))
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Export finished. Saved ${outFile.name}")
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        isHubExporting = false
                        Toast.makeText(context, "Export error: ${t.localizedMessage ?: "Storage or encoding error"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onConfirmExport = { chosenName, format ->
                isHubExporting = true
                hubExportProgress = 0.05f
                scope.launch {
                    try {
                        val outFile = if (isVideo) {
                            val vs = activeVideoSession ?: throw IllegalStateException("No active video project to export.")
                            val sourceUri = vs.sourceUri ?: throw IllegalStateException("Please select or create a video clip before exporting.")
                            val keepRanges = VideoCutMath.computeMultiTrimKeepRanges(
                                multiTrimClips = vs.multiTrimClips,
                                cutSegments = vs.cutSegments,
                                fallbackStartMs = vs.trimStartMs,
                                fallbackEndMs = vs.trimEndMs
                            )
                            val canUseStreamCopy = vs.streamCopyTrim &&
                                vs.selectedSpeed == 1.0f &&
                                !vs.reverseVideo &&
                                vs.selectedFilter == VideoFilter.NORMAL &&
                                vs.selectedVfx == VideoVfx.NONE &&
                                vs.aspectRatio == VideoAspectRatio.ORIGINAL &&
                                vs.resolutionPreset == VideoResolutionPreset.ORIGINAL &&
                                vs.rotation == VideoRotation.DEG_0 &&
                                !vs.flipH &&
                                !vs.flipV &&
                                vs.textOverlay.isBlank() &&
                                !vs.blurConfig.enabled &&
                                vs.imageOverlay.uri == null &&
                                vs.subtitleCues.isEmpty() &&
                                vs.soundEffects.isEmpty() &&
                                vs.narrations.isEmpty() &&
                                vs.musicTracks.isEmpty() &&
                                vs.audioOption == VideoAudioOption.ORIGINAL_AUDIO &&
                                vs.audioVolume == 1.0f &&
                                !vs.noiseReduceEnabled

                            if (canUseStreamCopy) {
                                videoToolkit.exportStreamCopyRanges(
                                    sourceUri = sourceUri,
                                    keepRangesMs = keepRanges,
                                    outputFileName = chosenName,
                                    formatOption = vs.outputFormat,
                                    onProgress = { hubExportProgress = it }
                                )
                            } else {
                                val req = FullRenderRequest(
                                    sourceUri = sourceUri,
                                    keepRangesMs = keepRanges,
                                    speed = vs.selectedSpeed,
                                    pitchCorrect = vs.pitchCorrect,
                                    reverseVideo = vs.reverseVideo,
                                    filter = vs.selectedFilter,
                                    vfx = vs.selectedVfx,
                                    aspectRatio = vs.aspectRatio,
                                    aspectFitMode = vs.aspectFitMode,
                                    resolutionPreset = vs.resolutionPreset,
                                    customWidth = vs.customWidth,
                                    customHeight = vs.customHeight,
                                    rotation = vs.rotation,
                                    flipHorizontal = vs.flipH,
                                    flipVertical = vs.flipV,
                                    compressorQuality = vs.compressorQuality,
                                    customBitrateBps = (vs.customBitrateMbps * 1_000_000f).toInt(),
                                    textOverlay = vs.textOverlay,
                                    textPosition = vs.textPosition,
                                    textAnimStyle = vs.textAnimStyle,
                                    blurConfig = vs.blurConfig,
                                    imageOverlay = vs.imageOverlay,
                                    subtitleCues = vs.subtitleCues,
                                    soundEffects = vs.soundEffects,
                                    narrations = vs.narrations,
                                    musicTracks = vs.musicTracks,
                                    smartDucking = vs.smartDucking,
                                    audioOption = vs.audioOption,
                                    replacedAudioUri = vs.replacedAudioUri,
                                    audioVolume = vs.audioVolume,
                                    noiseReductionEnabled = vs.noiseReduceEnabled,
                                    noiseReductionStrength = vs.noiseReduceStrength,
                                    outputFormat = vs.outputFormat,
                                    metadataTitle = vs.metadata.title,
                                    metadataArtist = vs.metadata.artist,
                                    metadataAlbum = vs.metadata.album
                                )
                                videoRenderEngine.renderVideo(req, chosenName) { hubExportProgress = it }
                            }
                        } else {
                            val asess = activeAudioSession ?: throw IllegalStateException("No active audio project to export.")
                            if (asess.pcm.isEmpty()) throw IllegalStateException("Audio track is empty.")
                            audioProcessor.exportSingleAudioFile(
                                mainPcm = asess.pcm,
                                bgTrackConfig = null,
                                bgPcm = null,
                                settings = asess.editSettings.copy(
                                    trimStartMs = asess.trimStartMs,
                                    trimEndMs = asess.trimEndMs,
                                    eqPreset = asess.selectedEq
                                ),
                                outputFileName = chosenName,
                                format = format,
                                onProgress = { hubExportProgress = it }
                            )
                        }
                        hubExportedFile = outFile
                        isHubExporting = false
                        mediaRepository.refreshExports()
                        activeProject?.let { cur ->
                            upsertSessionProject(cur.copy(title = outFile.name, filePath = outFile.absolutePath, statusBadge = "Exported"))
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Export finished. Saved ${outFile.name}")
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        isHubExporting = false
                        Toast.makeText(context, "Export error: ${t.localizedMessage ?: "Storage or encoding error"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onConfirmAudioExportExtended = { chosenName, format, exportOptions ->
                isHubExporting = true
                hubExportProgress = 0.05f
                scope.launch {
                    try {
                        val asess = activeAudioSession ?: throw IllegalStateException("No active audio project to export.")
                        if (asess.pcm.isEmpty()) throw IllegalStateException("Audio track is empty.")
                        val outFile = audioProcessor.exportSingleAudioFile(
                            mainPcm = asess.pcm,
                            bgTrackConfig = null,
                            bgPcm = null,
                            settings = asess.editSettings.copy(
                                trimStartMs = asess.trimStartMs,
                                trimEndMs = asess.trimEndMs,
                                eqPreset = asess.selectedEq
                            ),
                            outputFileName = chosenName,
                            format = format,
                            exportOptions = exportOptions,
                            onProgress = { hubExportProgress = it }
                        )
                        hubExportedFile = outFile
                        isHubExporting = false
                        mediaRepository.refreshExports()
                        activeProject?.let { cur ->
                            upsertSessionProject(cur.copy(title = outFile.name, filePath = outFile.absolutePath, statusBadge = "Exported"))
                        }
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Export finished. Saved ${outFile.name}")
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        isHubExporting = false
                        Toast.makeText(context, "Export error: ${t.localizedMessage ?: "Storage or encoding error"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSaveAsRingtone = { file ->
                scope.launch {
                    val savedRingtone = withContext(Dispatchers.IO) {
                        StorageExportManager.saveAudioToRingtones(context, file, file.name)
                    }
                    if (savedRingtone != null) {
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        AccessibilityHelper.announceForAccessibility(context, "Saved to Ringtones folder")
                        Toast.makeText(context, "Saved to Ringtones/${file.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Could not save ringtone file.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPlayExported = { file ->
                try {
                    if (isVideo) {
                        videoPlayer.loadUri(Uri.fromFile(file)) { videoPlayer.play() }
                    } else {
                        audioPlayer.loadUri(Uri.fromFile(file))
                        audioPlayer.play()
                    }
                    showHubExportDialog = false
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
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = if (isVideo) "video/*" else "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Share Exported File").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "Could not share file: ${t.localizedMessage ?: "File location restricted"}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    showAudioToolSourcePrompt?.let { promptTool ->
        AudioToolSourcePromptDialog(
            selectedTool = promptTool,
            onPickAudioFile = {
                pendingAudioToolAfterLoad = promptTool
                showAudioToolSourcePrompt = null
                showCreateAudioPickerDialog = true
            },
            onStartBlankAudio = {
                showAudioToolSourcePrompt = null
                pendingAudioToolAfterLoad = null
                startBlankAudioProject(targetAudioTool = promptTool)
            },
            onDismiss = { showAudioToolSourcePrompt = null }
        )
    }

    showVideoToolSourcePrompt?.let { (promptTool, promptFeat) ->
        VideoToolSourcePromptDialog(
            selectedTool = promptTool,
            selectedFeatureTitle = promptFeat?.title,
            onPickVideoFile = {
                pendingVideoToolAfterLoad = promptTool
                pendingVideoFeatureAfterLoad = promptFeat
                showVideoToolSourcePrompt = null
                showCreateVideoPickerDialog = true
            },
            onStartDemoVideo = {
                showVideoToolSourcePrompt = null
                pendingVideoToolAfterLoad = null
                pendingVideoFeatureAfterLoad = null
                startDemoVideoProject(targetVideoTool = promptTool, targetVideoFeature = promptFeat)
            },
            onDismiss = { showVideoToolSourcePrompt = null }
        )
    }

    if (showCreateAudioPickerDialog) {
        AccessibleAudioPickerDialog(
            exportedAudios = exportedMedia,
            onSelectAudio = { uri, displayName ->
                showCreateAudioPickerDialog = false
                handleSelectedCreateAudioUri(uri, displayName)
            },
            onDismiss = {
                pendingAudioToolAfterLoad = null
                showCreateAudioPickerDialog = false
            }
        )
    }

    if (showCreateVideoPickerDialog) {
        AccessibleVideoPickerDialog(
            exportedVideos = exportedMedia,
            onSelectVideo = { uri, displayName ->
                showCreateVideoPickerDialog = false
                val nextVideoTool = pendingVideoToolAfterLoad
                val nextVideoFeat = pendingVideoFeatureAfterLoad
                pendingVideoToolAfterLoad = null
                pendingVideoFeatureAfterLoad = null
                val chosenTitle = createNameDraft.trim().ifBlank { displayName.ifBlank { "Video Project" } }
                val summary = StudioProjectSummary(
                    id = "video_${UUID.randomUUID()}",
                    title = chosenTitle,
                    mediaType = ProjectMediaType.VIDEO,
                    sourceUri = uri,
                    filePath = null,
                    durationMillis = 0L,
                    fileSizeBytes = 0L,
                    lastModifiedMillis = System.currentTimeMillis(),
                    statusBadge = "Active"
                )
                createNameDraft = ""
                openVideoProjectIntoHub(
                    project = summary,
                    targetVideoTool = nextVideoTool,
                    targetVideoFeature = nextVideoFeat
                )
            },
            onDismiss = {
                pendingVideoToolAfterLoad = null
                pendingVideoFeatureAfterLoad = null
                showCreateVideoPickerDialog = false
            }
        )
    }
}
