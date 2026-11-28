package com.example.ui.studio

import android.net.Uri
import com.example.audio.AudioEditSettings
import com.example.audio.EqPreset
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.video.AspectRatioFitMode
import com.example.video.AutoSplitMode
import com.example.video.BlurRegionConfig
import com.example.video.CompressorQuality
import com.example.video.ImageOverlayConfig
import com.example.video.MusicLayerTrack
import com.example.video.NarrationSegment
import com.example.video.SnapshotGlimpseItem
import com.example.video.SoundEffectOverlay
import com.example.video.SubtitleCue
import com.example.video.TextAnimationStyle
import com.example.video.TextOverlayPosition
import com.example.video.VideoAspectRatio
import com.example.video.VideoAudioOption
import com.example.video.VideoCutSegment
import com.example.video.VideoFilter
import com.example.video.VideoFormatOption
import com.example.video.VideoMetadataDetails
import com.example.video.VideoResolutionPreset
import com.example.video.VideoRotation
import com.example.video.VideoStudioFeatureButton
import com.example.video.VideoToolSection
import com.example.video.VideoVfx

enum class ProjectMediaType(val label: String) {
    AUDIO("Audio"),
    VIDEO("Video")
}

enum class StudioRootDestination {
    HOME,
    AUDIO_EDITOR,
    VIDEO_EDITOR,
    CREATE_PROJECT,
    PROJECT_HUB,
    FEATURE_WORKSPACE,
    EXPORTS
}

enum class AudioFeatureTool(
    val title: String,
    val subtitle: String,
    val tag: String
) {
    TRIM_CUT(
        title = "Trim",
        subtitle = "Set start/end markers, crop, cut, copy, paste, mute, silence & loop preview",
        tag = "hub_feature_audio_trim"
    ),
    MIX_TRACKS(
        title = "Mix",
        subtitle = "Blend another audio track or clipboard with volume balance & peak limiter",
        tag = "hub_feature_audio_mix"
    ),
    VOICE_OVER(
        title = "Voice Over & Auto-Ducking",
        subtitle = "Blend voice & background music with smart auto-ducking & sync",
        tag = "hub_feature_audio_voice_over"
    ),
    EFFECTS_CLEANING(
        title = "Effects",
        subtitle = "Speed, Pitch, Time Stretch, EQ, De-Noise, Filters, Channels, Compressor, Echo, Reverb & Pro DSP",
        tag = "hub_feature_audio_effects"
    ),
    MUSIC_REBALANCE(
        title = "Music Rebalance & Stems",
        subtitle = "Balance vocals, bass, percussion & instruments or separate 2–6 stems",
        tag = "hub_feature_audio_music_rebalance"
    ),
    RECORD_VOICE(
        title = "Voice Recorder",
        subtitle = "Record replacement, punch-in over selection, append to end & mic gain",
        tag = "hub_feature_audio_record"
    ),
    TONE_GENERATOR(
        title = "Tone & Noise Generator",
        subtitle = "Synthesize sine, square tones or noise at any frequency and duration",
        tag = "hub_feature_audio_tone_generator"
    ),
    INSERT_SILENCE(
        title = "Insert Silence",
        subtitle = "Insert clean digital silence at the current playhead position",
        tag = "hub_feature_audio_insert_silence"
    ),
    CUE_POINTS(
        title = "Cue Points & Bookmarks",
        subtitle = "Drop timestamped cue markers and jump Previous or Next Cue",
        tag = "hub_feature_audio_cue_points"
    ),
    AUDIO_STATS(
        title = "Peak & RMS Stats",
        subtitle = "Analyze peak dBFS, RMS loudness, headroom & clipping safety",
        tag = "hub_feature_audio_stats"
    ),
    FULL_DAW_STUDIO(
        title = "Full DAW Timeline",
        subtitle = "Open complete multi-tab workstation & File/Edit/Effect/View/Tools/Help menus",
        tag = "hub_feature_audio_full"
    )
}

fun accessibleAudioTools(): List<AudioFeatureTool> = listOf(
    AudioFeatureTool.TRIM_CUT,
    AudioFeatureTool.MIX_TRACKS,
    AudioFeatureTool.VOICE_OVER,
    AudioFeatureTool.EFFECTS_CLEANING,
    AudioFeatureTool.MUSIC_REBALANCE,
    AudioFeatureTool.RECORD_VOICE,
    AudioFeatureTool.TONE_GENERATOR,
    AudioFeatureTool.INSERT_SILENCE,
    AudioFeatureTool.CUE_POINTS,
    AudioFeatureTool.AUDIO_STATS
)

enum class VideoFeatureTool(
    val title: String,
    val subtitle: String,
    val tag: String
) {
    TRIM_TIMELINE(
        title = "Trim & Timeline",
        subtitle = "Markers, stream-copy trim, cut middle, merge & snapshots",
        tag = "hub_feature_video_trim"
    ),
    AUDIO_MUSIC(
        title = "Audio & Narration",
        subtitle = "Record narration, music layers, ducking, mute or replace audio",
        tag = "hub_feature_video_audio"
    ),
    OVERLAYS_VFX(
        title = "Overlays & VFX",
        subtitle = "Text titles, blur mosaic, image overlay, subtitles & filters",
        tag = "hub_feature_video_overlays"
    ),
    TRANSFORM_SPEED(
        title = "Transform & Speed",
        subtitle = "Speed multiplier, pitch lock, rotate, flip, aspect & reverse",
        tag = "hub_feature_video_transform"
    ),
    CONVERT_TOOLS(
        title = "Convert & Tools",
        subtitle = "Compress MP4, convert format, remove silence, GIF & frames",
        tag = "hub_feature_video_tools"
    ),
    FULL_VIDEO_STUDIO(
        title = "Full Video Workspace",
        subtitle = "Open complete preview & all video editing sections together",
        tag = "hub_feature_video_full"
    )
}

fun accessibleVideoTools(): List<VideoFeatureTool> = listOf(
    VideoFeatureTool.TRIM_TIMELINE,
    VideoFeatureTool.AUDIO_MUSIC,
    VideoFeatureTool.OVERLAYS_VFX,
    VideoFeatureTool.TRANSFORM_SPEED,
    VideoFeatureTool.CONVERT_TOOLS
)

sealed class ActiveFeatureWorkspace {
    data class AudioTool(val tool: AudioFeatureTool) : ActiveFeatureWorkspace()
    data class VideoTool(
        val tool: VideoFeatureTool,
        val initialFeature: VideoStudioFeatureButton? = null
    ) : ActiveFeatureWorkspace()
}

fun VideoStudioFeatureButton.toParentTool(): VideoFeatureTool = when (section) {
    VideoToolSection.TIMELINE -> VideoFeatureTool.TRIM_TIMELINE
    VideoToolSection.AUDIO_MUSIC -> VideoFeatureTool.AUDIO_MUSIC
    VideoToolSection.OVERLAYS -> VideoFeatureTool.OVERLAYS_VFX
    VideoToolSection.TRANSFORM -> VideoFeatureTool.TRANSFORM_SPEED
    VideoToolSection.TOOLS -> VideoFeatureTool.CONVERT_TOOLS
}

data class StudioProjectSummary(
    val id: String,
    val title: String,
    val mediaType: ProjectMediaType,
    val sourceUri: Uri?,
    val filePath: String?,
    val durationMillis: Long,
    val fileSizeBytes: Long,
    val lastModifiedMillis: Long,
    val statusBadge: String = "Ready"
)

data class AudioProjectSessionState(
    val projectId: String,
    val title: String,
    val baseFileName: String? = null,
    val pcm: ShortArray = ShortArray(0),
    val sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val editSettings: AudioEditSettings = AudioEditSettings(),
    val fxSettings: StudioFxSettings = StudioFxSettings(),
    val selectedEq: EqPreset = EqPreset.NORMAL,
    val appliedQuickEffects: List<String> = emptyList(),
    val clipboardPcm: ShortArray? = null,
    val cuePoints: List<com.example.audio.CuePoint> = emptyList(),
    val hasUnsavedChanges: Boolean = false
)

data class VideoProjectSessionState(
    val projectId: String,
    val title: String,
    val baseFileName: String? = null,
    val sourceUri: Uri? = null,
    val durationMs: Long = 0L,
    val metadata: VideoMetadataDetails = VideoMetadataDetails(),
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 6000L,
    val streamCopyTrim: Boolean = false,
    val cutSegments: List<VideoCutSegment> = emptyList(),
    val multiTrimClips: List<VideoCutSegment> = emptyList(),
    val splitPointsMs: List<Long> = emptyList(),
    val autoSplitMode: AutoSplitMode = AutoSplitMode.BY_LENGTH,
    val autoSplitIntervalMs: Long = 30_000L,
    val autoSplitFileCount: Int = 2,
    val autoSplitTargetSizeMb: Float = 25f,
    val mergeClipUris: List<Uri> = emptyList(),
    val slideshowImages: List<Uri> = emptyList(),
    val slideshowSecPerSlide: Int = 3,
    val slideshowMusicUri: Uri? = null,
    val snapshots: List<SnapshotGlimpseItem> = emptyList(),
    val narrations: List<NarrationSegment> = emptyList(),
    val musicTracks: List<MusicLayerTrack> = emptyList(),
    val smartDucking: Boolean = true,
    val defaultLoopMusicTracks: Boolean = true,
    val soundEffects: List<SoundEffectOverlay> = emptyList(),
    val audioOption: VideoAudioOption = VideoAudioOption.ORIGINAL_AUDIO,
    val replacedAudioUri: Uri? = null,
    val audioVolume: Float = 1.0f,
    val noiseReduceEnabled: Boolean = false,
    val noiseReduceStrength: Float = 0.75f,
    val textOverlay: String = "",
    val textPosition: TextOverlayPosition = TextOverlayPosition.BOTTOM_SUBTITLE,
    val textAnimStyle: TextAnimationStyle = TextAnimationStyle.STATIC,
    val blurConfig: BlurRegionConfig = BlurRegionConfig(),
    val imageOverlay: ImageOverlayConfig = ImageOverlayConfig(),
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val selectedFilter: VideoFilter = VideoFilter.NORMAL,
    val selectedVfx: VideoVfx = VideoVfx.NONE,
    val selectedSpeed: Float = 1.0f,
    val pitchCorrect: Boolean = true,
    val resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.ORIGINAL,
    val customWidth: Int = 1280,
    val customHeight: Int = 720,
    val lockAspectRatio: Boolean = true,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    val aspectFitMode: AspectRatioFitMode = AspectRatioFitMode.LETTERBOX,
    val rotation: VideoRotation = VideoRotation.DEG_0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val reverseVideo: Boolean = false,
    val compressorQuality: CompressorQuality = CompressorQuality.HIGH,
    val customBitrateMbps: Float = 2.0f,
    val outputFormat: VideoFormatOption = VideoFormatOption.MP4,
    val hasUnsavedChanges: Boolean = false
)
