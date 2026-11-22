package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.storage.StorageExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Parameters passed into the full Canvas + H.264 + AAC on-device Video Render Engine.
 */
data class FullRenderRequest(
    val sourceUri: Uri,
    val keepRangesMs: List<Pair<Long, Long>> = emptyList(),
    val speed: Float = 1.0f,
    val pitchCorrect: Boolean = true,
    val reverseVideo: Boolean = false,
    val filter: VideoFilter = VideoFilter.NORMAL,
    val vfx: VideoVfx = VideoVfx.NONE,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    val aspectFitMode: AspectRatioFitMode = AspectRatioFitMode.LETTERBOX,
    val resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.ORIGINAL,
    val customWidth: Int = 1280,
    val customHeight: Int = 720,
    val rotation: VideoRotation = VideoRotation.DEG_0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val compressorQuality: CompressorQuality = CompressorQuality.HIGH,
    val customBitrateBps: Int = 2_000_000,
    val textOverlay: String = "",
    val textPosition: TextOverlayPosition = TextOverlayPosition.BOTTOM_SUBTITLE,
    val textAnimStyle: TextAnimationStyle = TextAnimationStyle.STATIC,
    val blurConfig: BlurRegionConfig = BlurRegionConfig(),
    val imageOverlay: ImageOverlayConfig = ImageOverlayConfig(),
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val soundEffects: List<SoundEffectOverlay> = emptyList(),
    val narrations: List<NarrationSegment> = emptyList(),
    val musicTracks: List<MusicLayerTrack> = emptyList(),
    val smartDucking: Boolean = true,
    val audioOption: VideoAudioOption = VideoAudioOption.ORIGINAL_AUDIO,
    val replacedAudioUri: Uri? = null,
    val audioVolume: Float = 1.0f,
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionStrength: Float = 0.7f,
    val noiseDenoiseParams: com.example.audio.SpectralDenoiseParams? = null,
    val outputFormat: VideoFormatOption = VideoFormatOption.MP4,
    val metadataTitle: String = "",
    val metadataArtist: String = "",
    val metadataAlbum: String = "Accessible Studio"
)

/**
 * Full-featured on-device Canvas H.264 + AAC renderer.
 * Draws per-frame filters, blur/mosaic, overlays, scrolling tickers, typing animations,
 * subtitles, rotations/flips, letterbox/crop aspect ratios, freeze-frames, reverse playback,
 * and mixes multi-track audio before muxing into a clean single output file.
 */
class VideoRenderEngine(private val context: Context) {

    private val audioMixer = VideoAudioMixer(context)

    private fun evenDim(value: Int): Int = (value.coerceIn(160, 1920) / 2) * 2

    fun computeOutputDimensions(srcW: Int, srcH: Int, req: FullRenderRequest): Pair<Int, Int> {
        val rotSwap = req.rotation == VideoRotation.DEG_90 || req.rotation == VideoRotation.DEG_270
        val baseW = if (rotSwap) srcH else srcW
        val baseH = if (rotSwap) srcW else srcH

        var w = when (req.resolutionPreset) {
            VideoResolutionPreset.ORIGINAL -> baseW
            VideoResolutionPreset.RES_1080P -> 1920
            VideoResolutionPreset.RES_720P -> 1280
            VideoResolutionPreset.RES_480P -> 854
            VideoResolutionPreset.RES_360P -> 640
            VideoResolutionPreset.CUSTOM -> req.customWidth
        }
        var h = when (req.resolutionPreset) {
            VideoResolutionPreset.ORIGINAL -> baseH
            VideoResolutionPreset.CUSTOM -> req.customHeight
            else -> {
                val ratio = baseH.toFloat() / baseW.toFloat().coerceAtLeast(1f)
                (w * ratio).roundToInt()
            }
        }

        if (req.aspectRatio != VideoAspectRatio.ORIGINAL) {
            val maxSide = maxOf(w, h).coerceIn(360, 1280)
            when (req.aspectRatio) {
                VideoAspectRatio.LANDSCAPE_16_9 -> { w = maxSide; h = (maxSide * 9f / 16f).roundToInt() }
                VideoAspectRatio.VERTICAL_9_16 -> { h = maxSide; w = (maxSide * 9f / 16f).roundToInt() }
                VideoAspectRatio.SQUARE_1_1 -> { val sq = minOf(maxSide, 720); w = sq; h = sq }
                VideoAspectRatio.ORIGINAL -> {}
            }
        }
        return evenDim(w) to evenDim(h)
    }

    suspend fun renderVideo(
        req: FullRenderRequest,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.04f)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, req.sourceUri)

        val srcDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
        val srcW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val srcH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val rotMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

        val ranges = if (req.keepRangesMs.isNotEmpty()) {
            req.keepRangesMs
        } else {
            listOf(0L to srcDurationMs)
        }

        val (outW, outH) = computeOutputDimensions(
            if (rotMeta == 90 || rotMeta == 270) srcH else srcW,
            if (rotMeta == 90 || rotMeta == 270) srcW else srcH,
            req
        )

        val fps = 20
        val totalKeepMs = ranges.sumOf { (it.second - it.first).coerceAtLeast(100L) }
        val freezeExtraMs = req.soundEffects.filter { it.freezeFrameAtTimestamp }.sumOf { it.durationMs }
        val scaledDurationMs = ((totalKeepMs + freezeExtraMs) / req.speed.coerceIn(0.25f, 4.0f)).toLong().coerceAtLeast(400L)
        val totalOutFrames = ((scaledDurationMs / 1000.0) * fps).toInt().coerceIn(8, 3600)

        // Decode overlay image if provided
        val overlayBitmap: Bitmap? = req.imageOverlay.uri?.let { imgUri ->
            try {
                context.contentResolver.openInputStream(imgUri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }

        // Build mixed master audio & encode to temp M4A
        onProgress(0.10f)
        val rawBaseAudio = if (req.audioOption == VideoAudioOption.MUTE) {
            ShortArray(0)
        } else {
            audioMixer.decodeUriToPcm(req.sourceUri)
        }
        val slicedBaseAudio = slicePcmByRanges(rawBaseAudio, ranges, audioMixer.sampleRate)
        val mixedPcm = audioMixer.buildMixedMasterPcm(
            basePcm = slicedBaseAudio,
            targetDurationMs = totalKeepMs + freezeExtraMs,
            audioOption = req.audioOption,
            replacedAudioUri = req.replacedAudioUri,
            baseVolume = req.audioVolume,
            noiseReduceEnabled = req.noiseReductionEnabled,
            noiseReduceIntensity = req.noiseReductionStrength,
            musicTracks = req.musicTracks,
            smartDuckingEnabled = req.smartDucking,
            narrations = req.narrations,
            soundEffects = req.soundEffects,
            speed = req.speed,
            keepPitch = req.pitchCorrect,
            reverse = req.reverseVideo,
            noiseDenoiseParams = req.noiseDenoiseParams
        )
        val tempAudioFile = File(context.cacheDir, "render_aud_${System.currentTimeMillis()}.m4a")
        val hasEncodedAudio = audioMixer.encodePcmToM4a(mixedPcm, tempAudioFile)
        onProgress(0.22f)

        val tempVideoFile = File(context.cacheDir, "render_vid_${System.currentTimeMillis()}.mp4")
        val bitRate = when (req.compressorQuality) {
            CompressorQuality.CUSTOM -> req.customBitrateBps.coerceIn(250_000, 12_000_000)
            else -> req.compressorQuality.bitrateBps
        }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val videoOnlyMuxer = MediaMuxer(tempVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val filterPaint = buildFilterPaint(req.filter)

        try {
            for (frameIdx in 0 until totalOutFrames) {
                val outTimeMs = (frameIdx * 1000L) / fps
                val timelinePosMs = ((outTimeMs * req.speed).toLong()).coerceIn(0L, totalKeepMs)
                val effectivePosMs = if (req.reverseVideo) (totalKeepMs - timelinePosMs).coerceAtLeast(0L) else timelinePosMs
                val sourceTimestampMs = mapToSourceTimestamp(effectivePosMs, ranges, req.soundEffects)

                val frameBitmap = retriever.getFrameAtTime(sourceTimestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val canvas = surface.lockHardwareCanvas()
                canvas.drawColor(Color.BLACK)

                if (frameBitmap != null) {
                    val drawnBmp = if (req.blurConfig.enabled && outTimeMs in req.blurConfig.startMs..req.blurConfig.endMs) {
                        applyMosaicOrBlur(frameBitmap, req.blurConfig.intensity, req.blurConfig.isMosaic)
                    } else {
                        frameBitmap
                    }
                    drawTransformedFrame(canvas, drawnBmp, outW, outH, req, filterPaint)
                    if (drawnBmp !== frameBitmap) drawnBmp.recycle()
                    frameBitmap.recycle()
                }

                // Image overlay within configured duration
                if (overlayBitmap != null && outTimeMs in req.imageOverlay.startMs..req.imageOverlay.endMs) {
                    val targetImgW = outW * req.imageOverlay.scalePercent.coerceIn(0.15f, 0.9f)
                    val targetImgH = targetImgW * (overlayBitmap.height.toFloat() / overlayBitmap.width.toFloat().coerceAtLeast(1f))
                    val left = outW - targetImgW - (outW * 0.04f)
                    val top = outH * 0.06f
                    canvas.drawBitmap(overlayBitmap, null, RectF(left, top, left + targetImgW, top + targetImgH), null)
                }

                // Visual VFX overlays
                drawVfxOverlay(canvas, outW, outH, req.vfx, frameIdx, outTimeMs)

                // Text Overlay Studio (Static, Scrolling News Ticker, Typing Animation)
                if (req.textOverlay.isNotBlank()) {
                    drawAnimatedTextOverlay(canvas, outW, outH, req.textOverlay, req.textPosition, req.textAnimStyle, frameIdx, totalOutFrames)
                }

                // Burn Subtitles (.srt / .vtt)
                val activeCue = req.subtitleCues.firstOrNull { outTimeMs in it.startMs..it.endMs }
                if (activeCue != null) {
                    drawSubtitleCue(canvas, outW, outH, activeCue.text)
                }

                surface.unlockCanvasAndPost(canvas)

                var outIdx = codec.dequeueOutputBuffer(bufferInfo, 0)
                while (outIdx >= 0 || outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                        trackIndex = videoOnlyMuxer.addTrack(codec.outputFormat)
                        videoOnlyMuxer.start()
                        muxerStarted = true
                    } else if (outIdx >= 0) {
                        val enc = codec.getOutputBuffer(outIdx)
                        if (enc != null && muxerStarted && bufferInfo.size > 0) {
                            bufferInfo.presentationTimeUs = (frameIdx * 1_000_000L) / fps
                            enc.position(bufferInfo.offset)
                            enc.limit(bufferInfo.offset + bufferInfo.size)
                            videoOnlyMuxer.writeSampleData(trackIndex, enc, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                    outIdx = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (frameIdx % 5 == 0) {
                    onProgress(0.22f + (frameIdx.toFloat() / totalOutFrames) * 0.65f)
                }
            }

            codec.signalEndOfInputStream()
            var eos = false
            while (!eos) {
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIdx >= 0) {
                    if (muxerStarted && bufferInfo.size > 0) {
                        val enc = codec.getOutputBuffer(outIdx)
                        if (enc != null) {
                            enc.position(bufferInfo.offset)
                            enc.limit(bufferInfo.offset + bufferInfo.size)
                            videoOnlyMuxer.writeSampleData(trackIndex, enc, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    trackIndex = videoOnlyMuxer.addTrack(codec.outputFormat)
                    videoOnlyMuxer.start()
                    muxerStarted = true
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { overlayBitmap?.recycle() } catch (_: Exception) {}
            try { codec.stop(); codec.release(); surface.release() } catch (_: Exception) {}
            try { if (muxerStarted) videoOnlyMuxer.stop(); videoOnlyMuxer.release() } catch (_: Exception) {}
        }

        onProgress(0.90f)
        val finalBaseName = outputFileName.substringBeforeLast('.')
        val finalName = "$finalBaseName.${req.outputFormat.extension}"
        val finalTemp = File(context.cacheDir, "final_$finalName")

        if (hasEncodedAudio && tempAudioFile.exists()) {
            muxVideoAndAudio(tempVideoFile, tempAudioFile, finalTemp)
        } else {
            tempVideoFile.copyTo(finalTemp, overwrite = true)
        }

        val savedFile = StorageExportManager.saveVideoFile(
            context = context,
            sourceFile = finalTemp,
            targetFileName = finalName,
            mimeType = req.outputFormat.mimeType,
            titleTag = req.metadataTitle.ifBlank { finalBaseName },
            artistTag = req.metadataArtist.ifBlank { "Accessible Studio" },
            albumTag = req.metadataAlbum.ifBlank { "Accessible Studio" }
        )

        try { tempVideoFile.delete(); tempAudioFile.delete(); finalTemp.delete() } catch (_: Exception) {}
        onProgress(1.0f)
        savedFile
    }

    private fun slicePcmByRanges(pcm: ShortArray, ranges: List<Pair<Long, Long>>, sampleRate: Int): ShortArray {
        if (pcm.isEmpty()) return pcm
        val chunks = mutableListOf<ShortArray>()
        var total = 0
        for ((sMs, eMs) in ranges) {
            val sIdx = ((sMs / 1000.0) * sampleRate).toInt().coerceIn(0, pcm.size)
            val eIdx = ((eMs / 1000.0) * sampleRate).toInt().coerceIn(sIdx, pcm.size)
            if (eIdx > sIdx) {
                val slice = pcm.copyOfRange(sIdx, eIdx)
                chunks.add(slice)
                total += slice.size
            }
        }
        val out = ShortArray(total)
        var offset = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, offset, c.size)
            offset += c.size
        }
        return out
    }

    private fun mapToSourceTimestamp(
        timelineMs: Long,
        ranges: List<Pair<Long, Long>>,
        sfxList: List<SoundEffectOverlay>
    ): Long {
        var remaining = timelineMs
        // Subtract freeze-frame pauses if timelineMs falls inside one
        for (sfx in sfxList.filter { it.freezeFrameAtTimestamp }) {
            if (remaining > sfx.timestampMs) {
                remaining = if (remaining < sfx.timestampMs + sfx.durationMs) {
                    sfx.timestampMs
                } else {
                    remaining - sfx.durationMs
                }
            }
        }
        for ((start, end) in ranges) {
            val len = (end - start).coerceAtLeast(1L)
            if (remaining <= len) return start + remaining
            remaining -= len
        }
        return ranges.lastOrNull()?.second ?: timelineMs
    }

    private fun buildFilterPaint(filter: VideoFilter): Paint? {
        if (filter == VideoFilter.NORMAL) return null
        val cm = ColorMatrix()
        when (filter) {
            VideoFilter.NOIR_BW -> cm.setSaturation(0f)
            VideoFilter.VINTAGE_90S -> {
                cm.setSaturation(0.65f)
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.1f, 0.1f, 0f, 0f, 15f,
                    0f, 1.0f, 0.05f, 0f, 8f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            VideoFilter.CINEMATIC -> cm.postConcat(ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.02f, 0.05f, 0f, 0f,
                0f, 0.05f, 1.12f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )))
            VideoFilter.CYBERPUNK -> {
                cm.setSaturation(1.45f)
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0.15f, 0f, 0f,
                    0f, 1.0f, 0.2f, 0f, 0f,
                    0.15f, 0f, 1.25f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            VideoFilter.VIVID_WARM -> cm.postConcat(ColorMatrix(floatArrayOf(
                1.22f, 0.08f, 0f, 0f, 12f,
                0.04f, 1.08f, 0f, 0f, 5f,
                0f, 0f, 0.85f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
            VideoFilter.EMERALD -> cm.postConcat(ColorMatrix(floatArrayOf(
                0.88f, 0f, 0f, 0f, 0f,
                0f, 1.18f, 0.05f, 0f, 8f,
                0f, 0.06f, 0.96f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
            VideoFilter.NORMAL -> {}
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
    }

    private fun applyMosaicOrBlur(src: Bitmap, intensity: Float, isMosaic: Boolean): Bitmap {
        val factor = (6 + (intensity * 26)).toInt().coerceIn(4, 36)
        val tinyW = (src.width / factor).coerceAtLeast(8)
        val tinyH = (src.height / factor).coerceAtLeast(8)
        val down = Bitmap.createScaledBitmap(src, tinyW, tinyH, !isMosaic)
        val up = Bitmap.createScaledBitmap(down, src.width, src.height, !isMosaic)
        down.recycle()
        return up
    }

    private fun drawTransformedFrame(
        canvas: Canvas,
        bmp: Bitmap,
        outW: Int,
        outH: Int,
        req: FullRenderRequest,
        paint: Paint?
    ) {
        val matrix = Matrix()
        matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        if (req.rotation.degrees != 0) matrix.postRotate(req.rotation.degrees.toFloat())
        val sxFlip = if (req.flipHorizontal) -1f else 1f
        val syFlip = if (req.flipVertical) -1f else 1f
        matrix.postScale(sxFlip, syFlip)

        val swap = req.rotation == VideoRotation.DEG_90 || req.rotation == VideoRotation.DEG_270
        val effW = if (swap) bmp.height.toFloat() else bmp.width.toFloat()
        val effH = if (swap) bmp.width.toFloat() else bmp.height.toFloat()

        val scale = if (req.aspectFitMode == AspectRatioFitMode.CENTER_CROP) {
            maxOf(outW / effW, outH / effH)
        } else {
            minOf(outW / effW, outH / effH)
        }
        matrix.postScale(scale, scale)
        matrix.postTranslate(outW / 2f, outH / 2f)
        canvas.drawBitmap(bmp, matrix, paint)
    }

    private fun drawVfxOverlay(canvas: Canvas, w: Int, h: Int, vfx: VideoVfx, frameIdx: Int, timeMs: Long) {
        when (vfx) {
            VideoVfx.CINEMATIC_LETTERBOX -> {
                val bar = h * 0.11f
                val p = Paint().apply { color = Color.BLACK }
                canvas.drawRect(0f, 0f, w.toFloat(), bar, p)
                canvas.drawRect(0f, h - bar, w.toFloat(), h.toFloat(), p)
            }
            VideoVfx.VIGNETTE -> {
                val p = Paint().apply {
                    color = Color.BLACK
                    alpha = 110
                    style = Paint.Style.STROKE
                    strokeWidth = minOf(w, h) * 0.14f
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            VideoVfx.GLITCH_SCANLINES -> {
                val p = Paint().apply { color = Color.BLACK; alpha = 65; strokeWidth = 2f }
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w.toFloat(), y, p)
                    y += 8f
                }
            }
            VideoVfx.VHS_TIMESTAMP -> {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; textSize = h * 0.04f; isFakeBoldText = true }
                if (frameIdx % 20 < 12) canvas.drawCircle(w * 0.06f, h * 0.08f, 10f, Paint().apply { color = Color.RED })
                canvas.drawText("REC  ${timeMs / 1000}s", w * 0.1f, h * 0.09f, tp)
            }
            VideoVfx.NONE -> {}
        }
    }

    private fun drawAnimatedTextOverlay(
        canvas: Canvas,
        w: Int,
        h: Int,
        text: String,
        pos: TextOverlayPosition,
        style: TextAnimationStyle,
        frameIdx: Int,
        totalFrames: Int
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (h * 0.052f).coerceIn(24f, 64f)
            isFakeBoldText = true
        }
        val bgPaint = Paint().apply { color = Color.BLACK; alpha = 175 }
        val y = when (pos) {
            TextOverlayPosition.TOP_HEADER -> h * 0.14f
            TextOverlayPosition.CENTER_TITLE -> h * 0.52f
            TextOverlayPosition.BOTTOM_SUBTITLE -> h * 0.86f
        }
        canvas.drawRect(0f, y - textPaint.textSize * 1.15f, w.toFloat(), y + textPaint.textSize * 0.45f, bgPaint)

        when (style) {
            TextAnimationStyle.STATIC -> {
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, w / 2f, y, textPaint)
            }
            TextAnimationStyle.SCROLLING_TICKER -> {
                textPaint.textAlign = Paint.Align.LEFT
                val textWidth = textPaint.measureText(text)
                val progress = (frameIdx % 120) / 120f
                val x = w - (w + textWidth) * progress
                canvas.drawText(text, x, y, textPaint)
            }
            TextAnimationStyle.TYPING_ANIMATION -> {
                textPaint.textAlign = Paint.Align.CENTER
                val chars = ((frameIdx.toFloat() / maxOf(totalFrames / 2, 1)) * text.length).toInt().coerceIn(1, text.length)
                val cursor = if (frameIdx % 10 < 5) "_" else ""
                canvas.drawText(text.substring(0, chars) + cursor, w / 2f, y, textPaint)
            }
        }
    }

    private fun drawSubtitleCue(canvas: Canvas, w: Int, h: Int, text: String) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            textSize = (h * 0.045f).coerceIn(22f, 54f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val bg = Paint().apply { color = Color.BLACK; alpha = 190 }
        val y = h * 0.93f
        val pad = tp.textSize * 0.6f
        canvas.drawRect(w * 0.05f, y - tp.textSize - pad * 0.4f, w * 0.95f, y + pad * 0.5f, bg)
        canvas.drawText(text, w / 2f, y, tp)
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outFile: File) {
        val vExt = MediaExtractor()
        val aExt = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            vExt.setDataSource(videoFile.absolutePath)
            aExt.setDataSource(audioFile.absolutePath)
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var vSrc = -1; var vDst = -1
            for (i in 0 until vExt.trackCount) {
                val f = vExt.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    vSrc = i; vDst = muxer.addTrack(f); break
                }
            }
            var aSrc = -1; var aDst = -1
            for (i in 0 until aExt.trackCount) {
                val f = aExt.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    aSrc = i; aDst = muxer.addTrack(f); break
                }
            }
            if (vSrc < 0) {
                videoFile.copyTo(outFile, overwrite = true)
                return
            }
            muxer.start()
            started = true
            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            vExt.selectTrack(vSrc)
            while (true) {
                val size = vExt.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, vExt.sampleTime, vExt.sampleFlags)
                muxer.writeSampleData(vDst, buf, info)
                vExt.advance()
            }

            if (aSrc >= 0 && aDst >= 0) {
                aExt.selectTrack(aSrc)
                while (true) {
                    val size = aExt.readSampleData(buf, 0)
                    if (size < 0) break
                    info.set(0, size, aExt.sampleTime, aExt.sampleFlags)
                    muxer.writeSampleData(aDst, buf, info)
                    aExt.advance()
                }
            }
        } catch (_: Exception) {
            videoFile.copyTo(outFile, overwrite = true)
        } finally {
            try { vExt.release() } catch (_: Exception) {}
            try { aExt.release() } catch (_: Exception) {}
            try { if (started) muxer?.stop(); muxer?.release() } catch (_: Exception) {}
        }
    }
}
