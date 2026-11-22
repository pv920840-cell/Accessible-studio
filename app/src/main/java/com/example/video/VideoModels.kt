package com.example.video

import android.net.Uri
import java.util.UUID

enum class VideoToolSection(val title: String, val spokenSummary: String) {
    TIMELINE("Timeline", "Timeline trimming, middle cut, merge clips, slideshow and snapshot highlights"),
    AUDIO_MUSIC("Audio & Mic", "Narration recorder, multi-music ducking, sound effects, audio manager, noise reducer, and audio extraction"),
    OVERLAYS("Overlays", "Text overlay studio with ticker and typing effects, blur mosaic editor, image overlay, subtitles, filters and VFX"),
    TRANSFORM("Transform", "Speed changer with pitch lock, resize resolution, aspect ratio, rotate flip, and reverse video"),
    TOOLS("Convert & Meta", "Compressor, format converter, video to GIF, frame extractor, silence remover, and metadata editor")
}

enum class VideoStudioFeatureButton(
    val section: VideoToolSection,
    val title: String,
    val tag: String
) {
    TRIMMER(VideoToolSection.TIMELINE, "Video Trimmer", "trimmer"),
    CUTTER(VideoToolSection.TIMELINE, "Non-Destructive Cutter", "cutter"),
    SNAPSHOT_GLIMPSE(VideoToolSection.TIMELINE, "Snapshot & Glimpse", "snapshot_glimpse"),
    MERGER(VideoToolSection.TIMELINE, "Video Merger", "merger"),
    SLIDESHOW_BLANK(VideoToolSection.TIMELINE, "Slideshow & Blank Clip", "slideshow_blank"),

    NARRATION(VideoToolSection.AUDIO_MUSIC, "Narration Recorder", "narration"),
    MULTI_MUSIC(VideoToolSection.AUDIO_MUSIC, "Multi Music & Ducking", "multi_music"),
    SOUND_EFFECTS(VideoToolSection.AUDIO_MUSIC, "Sound Effects & Freeze", "sound_effects"),
    AUDIO_MANAGER(VideoToolSection.AUDIO_MUSIC, "Mute / Replace Audio", "audio_manager"),
    NOISE_REDUCER(VideoToolSection.AUDIO_MUSIC, "Adaptive Noise Reducer", "noise_reducer"),
    VIDEO_TO_AUDIO(VideoToolSection.AUDIO_MUSIC, "Video to Audio", "video_to_audio"),

    TEXT_OVERLAY(VideoToolSection.OVERLAYS, "Text Overlay Studio", "text_overlay"),
    BLUR_MOSAIC(VideoToolSection.OVERLAYS, "Blur & Mosaic", "blur_mosaic"),
    IMAGE_OVERLAY(VideoToolSection.OVERLAYS, "Image Overlay", "image_overlay"),
    SUBTITLES(VideoToolSection.OVERLAYS, "Burn Subtitles", "subtitles"),
    COLOR_FILTERS(VideoToolSection.OVERLAYS, "Color Filters", "color_filters"),
    VFX_EFFECTS(VideoToolSection.OVERLAYS, "VFX Effects", "vfx_effects"),

    SPEED_CHANGER(VideoToolSection.TRANSFORM, "Speed Changer", "speed_changer"),
    RESIZE_RESOLUTION(VideoToolSection.TRANSFORM, "Resize & Resolution", "resize_resolution"),
    ASPECT_RATIO(VideoToolSection.TRANSFORM, "Aspect Ratio", "aspect_ratio"),
    ROTATE_FLIP_REVERSE(VideoToolSection.TRANSFORM, "Rotate, Flip & Reverse", "rotate_flip_reverse"),

    SILENCE_REMOVER(VideoToolSection.TOOLS, "Silence Remover", "silence_remover"),
    VIDEO_COMPRESSOR(VideoToolSection.TOOLS, "Video Compressor", "video_compressor"),
    FORMAT_CONVERTER(VideoToolSection.TOOLS, "Format Converter", "format_converter"),
    GIF_FRAME_EXTRACTOR(VideoToolSection.TOOLS, "GIF & Frame Extractor", "gif_frame_extractor"),
    METADATA_TAGS(VideoToolSection.TOOLS, "Metadata & Tags", "metadata_tags");

    companion object {
        fun forSection(section: VideoToolSection): List<VideoStudioFeatureButton> =
            entries.filter { it.section == section }
    }
}

enum class SoundEffectPreset(val displayName: String, val freqHz: Float) {
    CHIME("Crystal Chime", 880f),
    POP("Bubble Pop", 520f),
    SWOOSH("Whoosh Transition", 240f),
    BELL("Studio Bell", 1046f),
    BEEP("Notification Beep", 740f),
    DRUM_HIT("Punchy Drum", 110f),
    CUSTOM_FILE("Imported Audio SFX", 0f)
}

data class SoundEffectOverlay(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val preset: SoundEffectPreset = SoundEffectPreset.CHIME,
    val customUri: Uri? = null,
    val timestampMs: Long = 0L,
    val durationMs: Long = 1500L,
    val volume: Float = 0.9f,
    val freezeFrameAtTimestamp: Boolean = false
)

data class NarrationSegment(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val startMs: Long = 0L,
    val pcmSamples: ShortArray = ShortArray(0),
    val durationMs: Long = 0L
)

data class MusicLayerTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val uri: Uri,
    val startMs: Long = 0L,
    val volume: Float = 0.6f,
    val loop: Boolean = true,
    val durationMs: Long = 0L
)

enum class TextAnimationStyle(val label: String) {
    STATIC("Static Subtitle"),
    SCROLLING_TICKER("Scrolling News Ticker"),
    TYPING_ANIMATION("Typing Animation")
}

data class BlurRegionConfig(
    val enabled: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 4000L,
    val intensity: Float = 0.6f,
    val isMosaic: Boolean = true
)

data class ImageOverlayConfig(
    val uri: Uri? = null,
    val displayName: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 4000L,
    val scalePercent: Float = 0.35f,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val mismatchWarning: String? = null
)

data class SnapshotGlimpseItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val startMs: Long,
    val endMs: Long
)

data class VideoCutSegment(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

enum class CompressorQuality(val label: String, val crfLabel: String, val bitrateBps: Int) {
    HIGH("High Quality", "CRF 18 • Sharp", 4_000_000),
    MEDIUM("Balanced", "CRF 23 • Standard", 1_800_000),
    LOW("Small Size", "CRF 28 • Compact", 800_000),
    CUSTOM("Custom Bitrate", "Custom CRF", 1_500_000)
}

enum class VideoResolutionPreset(val label: String, val targetWidth: Int, val targetHeight: Int) {
    ORIGINAL("Original", 0, 0),
    RES_1080P("1080p Full HD", 1920, 1080),
    RES_720P("720p HD", 1280, 720),
    RES_480P("480p SD", 854, 480),
    RES_360P("360p Compact", 640, 360),
    CUSTOM("Custom", 1280, 720)
}

enum class AspectRatioFitMode(val label: String) {
    LETTERBOX("Letterbox (Fit)"),
    CENTER_CROP("Crop (Fill)")
}

enum class VideoRotation(val label: String, val degrees: Int) {
    DEG_0("0° Normal", 0),
    DEG_90("90° CW", 90),
    DEG_180("180° Flip", 180),
    DEG_270("270° CCW", 270)
}

enum class VideoFormatOption(val extension: String, val label: String, val mimeType: String) {
    MP4("mp4", "MP4 (H.264)", "video/mp4"),
    MKV("mkv", "MKV (Matroska)", "video/x-matroska"),
    MOV("mov", "MOV (QuickTime)", "video/quicktime"),
    AVI("avi", "AVI (Legacy)", "video/x-msvideo"),
    WEBM("webm", "WEBM (Web)", "video/webm")
}

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class VideoMetadataDetails(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 30,
    val bitrateKbps: Int = 0,
    val videoCodec: String = "H.264 / AVC",
    val audioCodec: String = "AAC",
    val audioChannels: Int = 1,
    val fileSizeBytes: Long = 0L,
    val hasAudio: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "Accessible Studio",
    val genre: String = "Video"
)
