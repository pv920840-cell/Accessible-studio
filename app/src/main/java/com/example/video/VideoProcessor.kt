package com.example.video

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sin

data class VideoEditSettings(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 8000L,
    val speed: Float = 1.0f,
    val filter: VideoFilter = VideoFilter.NORMAL,
    val vfx: VideoVfx = VideoVfx.NONE,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    val textOverlay: String = "",
    val textPosition: TextOverlayPosition = TextOverlayPosition.BOTTOM_SUBTITLE,
    val isMuted: Boolean = false,
    val audioOption: VideoAudioOption = VideoAudioOption.ORIGINAL_AUDIO,
    val audioVolume: Float = 1.0f,
    val audioEchoEnabled: Boolean = false,
    val audioReverbEnabled: Boolean = false
)

enum class VideoAudioOption(val label: String) {
    ORIGINAL_AUDIO("Keep Original Sound"),
    MUTE("Mute Audio (Silent Video)"),
    REPLACE_WITH_STUDIO_AUDIO("Replace with Studio Mixed Audio")
}

enum class VideoFilter(val displayName: String, val description: String) {
    NORMAL("Normal", "Original natural colors"),
    CINEMATIC("Cinematic 35mm", "Teal shadows & warm golden highlights"),
    CYBERPUNK("Cyberpunk Neon", "High-contrast electric cyan and magenta"),
    NOIR_BW("Film Noir B&W", "Dramatic high-contrast monochrome"),
    VINTAGE_90S("Vintage 90s", "Warm nostalgic retro VHS wash"),
    VIVID_WARM("Golden Sunset", "Rich amber and warm saturated sunset tones"),
    EMERALD("Emerald Indie", "Moody indie cinema green undertone")
}

enum class VideoVfx(val displayName: String) {
    NONE("No VFX Effect"),
    CINEMATIC_LETTERBOX("21:9 Cinematic Letterbox"),
    VIGNETTE("Dark Vignette Frame"),
    GLITCH_SCANLINES("Retro CRT Scanlines"),
    VHS_TIMESTAMP("Camcorder REC Watermark")
}

enum class VideoAspectRatio(val label: String, val ratio: Float) {
    ORIGINAL("Original", 1.0f),
    LANDSCAPE_16_9("16:9 Landscape (YouTube)", 16f / 9f),
    VERTICAL_9_16("9:16 Vertical (Reels / Shorts)", 9f / 16f),
    SQUARE_1_1("1:1 Square (Instagram)", 1.0f)
}

enum class TextOverlayPosition(val label: String) {
    BOTTOM_SUBTITLE("Bottom Subtitle"),
    CENTER_TITLE("Center Title"),
    TOP_HEADER("Top Header")
}

class VideoProcessor(private val context: Context) {

    /**
     * Inspects video duration, width, height, and audio presence
     */
    suspend fun inspectVideo(uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)

            val durationMs = durationStr?.toLongOrNull() ?: 5000L
            val width = widthStr?.toIntOrNull() ?: 1280
            val height = heightStr?.toIntOrNull() ?: 720
            val hasAudio = hasAudioStr != null && hasAudioStr.equals("yes", ignoreCase = true)

            VideoMetadata(
                durationMs = durationMs,
                width = width,
                height = height,
                hasAudio = hasAudio
            )
        } catch (_: Exception) {
            VideoMetadata(durationMs = 5000L, width = 1280, height = 720, hasAudio = false)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Exports a single edited video file with trim, speed multiplier, and audio options
     */
    suspend fun exportSingleVideoFile(
        sourceUri: Uri,
        settings: VideoEditSettings,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        val exportDir = com.example.storage.StorageExportManager.getVideoExportDirectory(context)

        val cleanName = if (outputFileName.endsWith(".mp4", true) || outputFileName.endsWith(".mkv", true)) {
            outputFileName
        } else {
            "$outputFileName.mp4"
        }
        val outputFile = File(exportDir, cleanName)
        if (outputFile.exists()) outputFile.delete()

        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(context, sourceUri, null)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") && videoTrackIndex < 0) {
                videoTrackIndex = i
                videoFormat = format
            } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                audioTrackIndex = i
                audioFormat = format
            }
        }

        if (videoTrackIndex < 0 || videoFormat == null) {
            videoExtractor.release()
            throw IllegalStateException("No valid video track found in selected media")
        }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerVideoTrack = muxer.addTrack(videoFormat)

        val shouldIncludeOriginalAudio = !settings.isMuted &&
                settings.audioOption == VideoAudioOption.ORIGINAL_AUDIO &&
                audioTrackIndex >= 0 &&
                audioFormat != null

        var muxerAudioTrack = -1
        if (shouldIncludeOriginalAudio) {
            muxerAudioTrack = muxer.addTrack(audioFormat!!)
        }

        muxer.start()
        onProgress(0.15f)

        val startUs = settings.trimStartMs * 1000L
        val endUs = settings.trimEndMs * 1000L
        val maxBufferSize = 2 * 1024 * 1024
        val buffer = ByteBuffer.allocate(maxBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        // 1. Process Video Track (with speed scaling on PTS)
        val speedFactor = settings.speed.coerceIn(0.25f, 3.0f)
        videoExtractor.selectTrack(videoTrackIndex)
        videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var firstVideoPtsUs = -1L
        while (true) {
            buffer.clear()
            val sampleSize = videoExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTimeUs = videoExtractor.sampleTime
            if (sampleTimeUs > endUs) break

            if (sampleTimeUs >= startUs) {
                if (firstVideoPtsUs < 0) {
                    firstVideoPtsUs = sampleTimeUs
                }
                val rawOffset = sampleTimeUs - firstVideoPtsUs
                val scaledOffset = (rawOffset.toDouble() / speedFactor.toDouble()).toLong()

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = scaledOffset.coerceAtLeast(0L)
                bufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)

                val progress = 0.15f + 0.65f * ((sampleTimeUs - startUs).toFloat() / (endUs - startUs).toFloat()).coerceIn(0f, 1f)
                onProgress(progress)
            }
            videoExtractor.advance()
        }

        // 2. Process Audio Track if included
        if (shouldIncludeOriginalAudio && muxerAudioTrack >= 0) {
            videoExtractor.unselectTrack(videoTrackIndex)
            videoExtractor.selectTrack(audioTrackIndex)
            videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            var firstAudioPtsUs = -1L
            while (true) {
                buffer.clear()
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = videoExtractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (firstAudioPtsUs < 0) {
                        firstAudioPtsUs = sampleTimeUs
                    }
                    val rawOffset = sampleTimeUs - firstAudioPtsUs
                    val scaledOffset = (rawOffset.toDouble() / speedFactor.toDouble()).toLong()

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = scaledOffset.coerceAtLeast(0L)
                    bufferInfo.flags = videoExtractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                }
                videoExtractor.advance()
            }
        }

        onProgress(0.95f)

        try {
            muxer.stop()
            muxer.release()
        } catch (_: Exception) {}

        try {
            videoExtractor.release()
        } catch (_: Exception) {}

        com.example.storage.StorageExportManager.saveVideoFile(
            context = context,
            sourceFile = outputFile,
            targetFileName = cleanName,
            mimeType = com.example.storage.StorageExportManager.getVideoMimeType(cleanName)
        )

        onProgress(1.0f)
        outputFile
    }

    /**
     * Generates a high-quality video clip featuring Accessible Studio styling:
     * filters, VFX letterboxing, text overlays, and visual effects!
     */
    suspend fun generateDemoVideoClip(
        fileName: String = "accessible_studio_demo.mp4",
        durationSec: Int = 8,
        settings: VideoEditSettings = VideoEditSettings(),
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.getExternalFilesDir(null), "DemoMedia")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, fileName)

        // Set dimensions based on Aspect Ratio
        val (width, height) = when (settings.aspectRatio) {
            VideoAspectRatio.VERTICAL_9_16 -> Pair(720, 1280)
            VideoAspectRatio.LANDSCAPE_16_9 -> Pair(1280, 720)
            VideoAspectRatio.SQUARE_1_1, VideoAspectRatio.ORIGINAL -> Pair(720, 720)
        }

        val fps = 30
        val totalFrames = durationSec * fps
        val bitRate = 2500000

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        // Filter Colors
        val (bgColor, accentColor, secondaryColor) = when (settings.filter) {
            VideoFilter.CINEMATIC -> Triple("#0B132B", "#F39C12", "#00B4D8")
            VideoFilter.CYBERPUNK -> Triple("#10002B", "#00F5D4", "#F72585")
            VideoFilter.NOIR_BW -> Triple("#111111", "#FFFFFF", "#888888")
            VideoFilter.VINTAGE_90S -> Triple("#2B1E16", "#FFB703", "#DDA15E")
            VideoFilter.VIVID_WARM -> Triple("#220901", "#FF5400", "#FFD166")
            VideoFilter.EMERALD -> Triple("#081C15", "#52B788", "#74C69D")
            VideoFilter.NORMAL -> Triple("#0F172A", "#00E5FF", "#FFD600")
        }

        val paintBg = Paint().apply { color = Color.parseColor(bgColor) }
        val paintAccent = Paint().apply {
            color = Color.parseColor(accentColor)
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
        }
        val paintWave = Paint().apply {
            color = Color.parseColor(secondaryColor)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        val paintSubText = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val bufferInfo = MediaCodec.BufferInfo()

        for (frame in 0 until totalFrames) {
            val canvas = surface.lockHardwareCanvas()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

            val timeSec = frame.toFloat() / fps.toFloat()
            val centerY = height * 0.40f

            // Dynamic Film Pulse Circle
            val circleRadius = (width * 0.18f) + 20f * sin(timeSec.toDouble() * 4.0).toFloat()
            canvas.drawCircle(width / 2f, centerY, circleRadius, paintAccent)

            // Dynamic Audio Waveform Bars
            val numBars = 14
            val barWidth = width * 0.035f
            val spacing = width * 0.02f
            val startX = (width - (numBars * barWidth + (numBars - 1) * spacing)) / 2f
            val waveCenterY = height * 0.65f

            for (b in 0 until numBars) {
                val h = (height * 0.06f) + (height * 0.12f) * abs(sin((timeSec * 3f + b * 0.4f).toDouble())).toFloat()
                val left = startX + b * (barWidth + spacing)
                val top = waveCenterY - h / 2f
                val right = left + barWidth
                val bottom = waveCenterY + h / 2f
                canvas.drawRoundRect(RectF(left, top, right, bottom), 10f, 10f, paintWave)
            }

            // Info Texts
            canvas.drawText("ACCESSIBLE STUDIO", width / 2f, height * 0.82f, paintText)
            canvas.drawText("${settings.filter.displayName} • ${String.format("%.1f", timeSec)}s / ${durationSec}s", width / 2f, height * 0.88f, paintSubText)

            // Custom User Text / Subtitle Overlay (Accessible Studio feature)
            if (settings.textOverlay.isNotBlank()) {
                val bannerPaint = Paint().apply {
                    color = Color.BLACK
                    alpha = 180
                }
                val overlayTextPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 38f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                val textY = when (settings.textPosition) {
                    TextOverlayPosition.TOP_HEADER -> height * 0.12f
                    TextOverlayPosition.CENTER_TITLE -> centerY
                    TextOverlayPosition.BOTTOM_SUBTITLE -> height * 0.76f
                }
                canvas.drawRect(0f, textY - 45f, width.toFloat(), textY + 25f, bannerPaint)
                canvas.drawText(settings.textOverlay, width / 2f, textY + 12f, overlayTextPaint)
            }

            // VFX Overlays
            when (settings.vfx) {
                VideoVfx.CINEMATIC_LETTERBOX -> {
                    val barH = height * 0.10f
                    val letterboxPaint = Paint().apply { color = Color.BLACK }
                    canvas.drawRect(0f, 0f, width.toFloat(), barH, letterboxPaint)
                    canvas.drawRect(0f, height - barH, width.toFloat(), height.toFloat(), letterboxPaint)
                }
                VideoVfx.GLITCH_SCANLINES -> {
                    val scanPaint = Paint().apply {
                        color = Color.BLACK
                        alpha = 60
                        strokeWidth = 3f
                    }
                    var y = 0f
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, scanPaint)
                        y += 8f
                    }
                }
                VideoVfx.VHS_TIMESTAMP -> {
                    val vhsPaint = Paint().apply {
                        color = Color.YELLOW
                        textSize = 28f
                        isFakeBoldText = true
                    }
                    val recPaint = Paint().apply { color = Color.RED }
                    if (frame % 30 < 20) {
                        canvas.drawCircle(45f, 45f, 12f, recPaint)
                    }
                    canvas.drawText("REC [SP]", 70f, 53f, vhsPaint)
                    canvas.drawText("NOV 13, 2026 00:${String.format("%02d", timeSec.toInt())}", 35f, height - 35f, vhsPaint)
                }
                VideoVfx.VIGNETTE, VideoVfx.NONE -> {}
            }

            surface.unlockCanvasAndPost(canvas)

            var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outIndex >= 0 || outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (muxerStarted && bufferInfo.size > 0) {
                    val encodedData = codec.getOutputBuffer(outIndex)
                    if (encodedData != null) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                } else {
                    codec.releaseOutputBuffer(outIndex, false)
                }
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

            onProgress((frame.toFloat() / totalFrames.toFloat()) * 0.9f)
        }

        codec.signalEndOfInputStream()

        var eos = false
        while (!eos) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex >= 0) {
                if (muxerStarted && bufferInfo.size > 0) {
                    val encodedData = codec.getOutputBuffer(outIndex)
                    if (encodedData != null) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    eos = true
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            }
        }

        try {
            codec.stop()
            codec.release()
            surface.release()
        } catch (_: Exception) {}

        try {
            if (muxerStarted) {
                muxer.stop()
                muxer.release()
            }
        } catch (_: Exception) {}

        onProgress(1.0f)
        file
    }
}

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean
)
