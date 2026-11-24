package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import com.example.storage.StorageExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * High-level on-device toolkit for Video Studio operations:
 * - Detailed Metadata Inspector & Tag Updater
 * - Image Overlay Aspect Ratio Mismatch Detector
 * - .SRT / .VTT Subtitle File Parser
 * - Single & Batch Frame Extractor (JPEG)
 * - Video to Animated GIF Converter
 * - Fast Stream-Copy Trimmer, Video Cutter (middle delete), & Format Converter
 * - Silence Remover (detect silent audio gaps & jump-cut)
 * - Video Merger (multi-clip concatenation)
 * - Slideshow Creator & Blank Clip Generator
 * - Snapshot & Glimpse Highlight Merger / Prepend
 */
class VideoToolkitProcessor(private val context: Context) {

    private val audioMixer = VideoAudioMixer(context)

    suspend fun inspectDetailedMetadata(uri: Uri): VideoMetadataDetails = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.equals("yes", true) == true
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Accessible Studio"
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Video"

            var vCodec = "H.264 / AVC"
            var aCodec = "AAC"
            var fps = 30
            var channels = 1
            try {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        vCodec = mime.removePrefix("video/").uppercase()
                        if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            fps = f.getInteger(MediaFormat.KEY_FRAME_RATE).coerceAtLeast(1)
                        }
                    } else if (mime.startsWith("audio/")) {
                        aCodec = mime.removePrefix("audio/").uppercase()
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                }
            } catch (_: Exception) {}

            var fileSize = 0L
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    fileSize = it.length.coerceAtLeast(0L)
                }
            } catch (_: Exception) {}

            VideoMetadataDetails(
                durationMs = durationMs,
                width = width,
                height = height,
                fps = fps,
                bitrateKbps = bitrateBps / 1000,
                videoCodec = vCodec,
                audioCodec = aCodec,
                audioChannels = channels,
                fileSizeBytes = fileSize,
                hasAudio = hasAudio,
                title = title,
                artist = artist,
                album = album,
                genre = genre
            )
        } catch (_: Exception) {
            VideoMetadataDetails()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Checks whether a picked overlay image matches the current video's aspect ratio;
     * returns (imgWidth, imgHeight, optionalMismatchWarning).
     */
    suspend fun inspectOverlayImage(imageUri: Uri, videoW: Int, videoH: Int): Triple<Int, Int, String?> =
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                val iw = opts.outWidth.coerceAtLeast(1)
                val ih = opts.outHeight.coerceAtLeast(1)
                val imgRatio = iw.toFloat() / ih.toFloat()
                val vidRatio = videoW.toFloat() / videoH.toFloat().coerceAtLeast(1f)
                val diff = abs(imgRatio - vidRatio) / vidRatio
                val warning = if (diff > 0.25f) {
                    "Aspect Ratio Mismatch Alert: Image (${iw}x${ih}) differs from Video (${videoW}x${videoH}). Scaled proportionally to avoid stretching."
                } else null
                Triple(iw, ih, warning)
            } catch (_: Exception) {
                Triple(0, 0, null)
            }
        }

    suspend fun parseSubtitleUri(uri: Uri): List<SubtitleCue> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        parseSubtitleText(text)
    }

    fun parseSubtitleText(raw: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split("\n\n")
        val timeRegex = Regex("""(\d{1,2}:)?\d{2}:\d{2}[.,]\d{3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[.,]\d{3}""")

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeLineIdx = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeLineIdx >= 0) {
                val parts = lines[timeLineIdx].split("-->")
                if (parts.size == 2) {
                    val startMs = parseSrtTimestamp(parts[0].trim())
                    val endMs = parseSrtTimestamp(parts[1].trim().split(" ").first())
                    val caption = lines.drop(timeLineIdx + 1).joinToString(" ")
                    if (caption.isNotBlank() && endMs > startMs) {
                        cues.add(SubtitleCue(startMs, endMs, caption))
                    }
                }
            }
        }
        return cues
    }

    private fun parseSrtTimestamp(ts: String): Long {
        val clean = ts.replace(',', '.')
        val segs = clean.split(":")
        return try {
            when (segs.size) {
                3 -> {
                    val h = segs[0].toLong()
                    val m = segs[1].toLong()
                    val sParts = segs[2].split(".")
                    val s = sParts[0].toLong()
                    val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    (h * 3600 + m * 60 + s) * 1000L + ms
                }
                2 -> {
                    val m = segs[0].toLong()
                    val sParts = segs[1].split(".")
                    val s = sParts[0].toLong()
                    val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    (m * 60 + s) * 1000L + ms
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun extractSingleFrame(videoUri: Uri, timestampMs: Long, outputName: String): File =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val bmp = try {
                retriever.setDataSource(context, videoUri)
                retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }

            val targetName = if (outputName.endsWith(".jpg", true)) outputName else "$outputName.jpg"
            val tempFile = File(context.cacheDir, targetName)
            FileOutputStream(tempFile).use { out ->
                (bmp ?: Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)).compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bmp?.recycle()

            val saved = StorageExportManager.saveImageOrGifFile(context, tempFile, targetName, "image/jpeg")
            tempFile.delete()
            saved
        }

    suspend fun extractBatchFrames(
        videoUri: Uri,
        intervalSec: Int,
        baseName: String,
        onProgress: (Float) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val exported = mutableListOf<File>()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
            val stepMs = (intervalSec.coerceIn(1, 60) * 1000L)
            val total = ((durationMs / stepMs) + 1).toInt().coerceAtLeast(1)

            var idx = 0
            var t = 0L
            while (t <= durationMs) {
                val bmp = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val fileName = "${baseName}_frame_${String.format("%03d", idx + 1)}.jpg"
                    val temp = File(context.cacheDir, fileName)
                    FileOutputStream(temp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    bmp.recycle()
                    exported.add(StorageExportManager.saveImageOrGifFile(context, temp, fileName, "image/jpeg"))
                    temp.delete()
                }
                idx++
                onProgress((idx.toFloat() / total).coerceIn(0.05f, 1f))
                t += stepMs
            }
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
        onProgress(1.0f)
        exported
    }

    suspend fun exportVideoToGif(
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        fps: Int = 8,
        outputName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        val retriever = MediaMetadataRetriever()
        val duration = (endMs - startMs).coerceIn(500L, 12000L)
        val frameStepMs = (1000L / fps.coerceIn(4, 15)).coerceAtLeast(66L)
        val frames = mutableListOf<Bitmap>()

        try {
            retriever.setDataSource(context, videoUri)
            var t = startMs
            while (t <= startMs + duration && frames.size < 80) {
                val raw = retriever.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val targetW = 360
                    val targetH = ((raw.height.toFloat() / raw.width.toFloat().coerceAtLeast(1f)) * targetW).toInt().coerceIn(120, 480)
                    val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                    raw.recycle()
                    frames.add(scaled)
                }
                t += frameStepMs
                onProgress(0.1f + (frames.size / 80f) * 0.45f)
            }
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }

        val targetName = if (outputName.endsWith(".gif", true)) outputName else "$outputName.gif"
        val tempGif = File(context.cacheDir, targetName)
        FileOutputStream(tempGif).use { fos ->
            AnimatedGifEncoder(delayMs = frameStepMs.toInt(), loopCount = 0).encode(frames, fos)
        }
        frames.forEach { it.recycle() }
        onProgress(0.9f)

        val saved = StorageExportManager.saveImageOrGifFile(context, tempGif, targetName, "image/gif")
        tempGif.delete()
        onProgress(1.0f)
        saved
    }

    /**
     * Generates a blank studio clip (optional title text) with silent or clean duration.
     */
    suspend fun generateBlankClip(
        durationSec: Int = 5,
        width: Int = 1280,
        height: Int = 720,
        titleText: String = "Blank Studio Clip",
        fileName: String = "blank_clip_${System.currentTimeMillis()}.mp4"
    ): File = withContext(Dispatchers.IO) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "DemoMedia")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        val fps = 20
        val totalFrames = durationSec.coerceIn(1, 30) * fps

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        for (i in 0 until totalFrames) {
            val c = surface.lockHardwareCanvas()
            c.drawColor(Color.parseColor("#0B1120"))
            if (titleText.isNotBlank()) c.drawText(titleText, width / 2f, height / 2f, tp)
            surface.unlockCanvasAndPost(c)

            var idx = codec.dequeueOutputBuffer(info, 0)
            while (idx >= 0 || idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (idx >= 0) {
                    val buf = codec.getOutputBuffer(idx)
                    if (buf != null && started && info.size > 0) {
                        info.presentationTimeUs = (i * 1_000_000L) / fps
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                }
                idx = codec.dequeueOutputBuffer(info, 0)
            }
        }
        codec.signalEndOfInputStream()
        var eos = false
        while (!eos) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx >= 0) {
                codec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
            } else if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
        }
        try { codec.stop(); codec.release(); surface.release() } catch (_: Exception) {}
        try { if (started) muxer.stop(); muxer.release() } catch (_: Exception) {}
        outFile
    }

    /**
     * Slideshow Creator: Builds a smooth MP4 slideshow from multiple selected photos + optional music.
     */
    suspend fun createSlideshowVideo(
        imageUris: List<Uri>,
        secondsPerSlide: Int,
        musicUri: Uri?,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val w = 1280
        val h = 720
        val fps = 20
        val framesPerSlide = secondsPerSlide.coerceIn(1, 15) * fps
        val totalFrames = imageUris.size * framesPerSlide

        val videoTemp = File(context.cacheDir, "slideshow_vid_${System.currentTimeMillis()}.mp4")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(videoTemp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var globalFrame = 0

        for ((imgIdx, uri) in imageUris.withIndex()) {
            val bmp = try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }

            for (f in 0 until framesPerSlide) {
                val c = surface.lockHardwareCanvas()
                c.drawColor(Color.BLACK)
                if (bmp != null) {
                    val scale = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                    val dw = bmp.width * scale
                    val dh = bmp.height * scale
                    val left = (w - dw) / 2f
                    val top = (h - dh) / 2f
                    c.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), null)
                }
                surface.unlockCanvasAndPost(c)

                var idx = codec.dequeueOutputBuffer(info, 0)
                while (idx >= 0 || idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    } else if (idx >= 0) {
                        val buf = codec.getOutputBuffer(idx)
                        if (buf != null && started && info.size > 0) {
                            info.presentationTimeUs = (globalFrame * 1_000_000L) / fps
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                    }
                    idx = codec.dequeueOutputBuffer(info, 0)
                }
                globalFrame++
            }
            bmp?.recycle()
            onProgress(0.1f + ((imgIdx + 1f) / imageUris.size) * 0.7f)
        }
        codec.signalEndOfInputStream()
        var eos = false
        while (!eos) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx >= 0) {
                codec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
            } else if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
        }
        try { codec.stop(); codec.release(); surface.release() } catch (_: Exception) {}
        try { if (started) muxer.stop(); muxer.release() } catch (_: Exception) {}

        val cleanName = if (outputFileName.endsWith(".mp4", true)) outputFileName else "$outputFileName.mp4"
        var finalSource = videoTemp

        if (musicUri != null) {
            val totalMs = (imageUris.size * secondsPerSlide) * 1000L
            val pcm = audioMixer.buildMixedMasterPcm(
                basePcm = ShortArray(0),
                targetDurationMs = totalMs,
                audioOption = VideoAudioOption.MUTE,
                replacedAudioUri = null,
                baseVolume = 1f,
                noiseReduceEnabled = false,
                noiseReduceIntensity = 0f,
                musicTracks = listOf(MusicLayerTrack(title = "Slideshow Music", uri = musicUri, volume = 0.7f, loop = true)),
                smartDuckingEnabled = false,
                narrations = emptyList(),
                soundEffects = emptyList(),
                speed = 1f,
                keepPitch = true,
                reverse = false
            )
            val m4a = File(context.cacheDir, "slideshow_aud_${System.currentTimeMillis()}.m4a")
            if (audioMixer.encodePcmToM4a(pcm, m4a)) {
                val muxed = File(context.cacheDir, "slideshow_mix_${System.currentTimeMillis()}.mp4")
                muxSimple(videoTemp, m4a, muxed)
                finalSource = muxed
                m4a.delete()
            }
        }

        val saved = StorageExportManager.saveVideoFile(context, finalSource, cleanName, "video/mp4")
        try { videoTemp.delete(); if (finalSource !== videoTemp) finalSource.delete() } catch (_: Exception) {}
        onProgress(1.0f)
        saved
    }

    /**
     * Ultra-fast Stream Copy for Trimmer (no re-encode), Video Cutter (multi-range keep),
     * Format Converter, or Silence Remover jump-cuts.
     */
    suspend fun exportStreamCopyRanges(
        sourceUri: Uri,
        keepRangesMs: List<Pair<Long, Long>>,
        outputFileName: String,
        formatOption: VideoFormatOption = VideoFormatOption.MP4,
        muteAudio: Boolean = false,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        val cleanName = "${outputFileName.substringBeforeLast('.')}.${formatOption.extension}"
        val tempOut = File(context.cacheDir, "stream_$cleanName")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(context, sourceUri, null)

            var vTrack = -1
            var aTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && vTrack < 0) vTrack = i
                else if (mime.startsWith("audio/") && aTrack < 0 && !muteAudio) aTrack = i
            }

            val muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            val activeMuxer = MediaMuxer(tempOut.absolutePath, muxerFormat).also { muxer = it }
            val muxV = if (vTrack >= 0) activeMuxer.addTrack(extractor.getTrackFormat(vTrack)) else -1
            val muxA = if (aTrack >= 0) activeMuxer.addTrack(extractor.getTrackFormat(aTrack)) else -1
            activeMuxer.start()
            muxerStarted = true

            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            var ptsBaseOffsetUs = 0L

            for ((rangeIdx, range) in keepRangesMs.withIndex()) {
                val startUs = range.first * 1000L
                val endUs = range.second * 1000L
                val rangeDurUs = (endUs - startUs).coerceAtLeast(10_000L)

                if (vTrack >= 0) {
                    extractor.selectTrack(vTrack)
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    while (true) {
                        val sampleTime = extractor.sampleTime
                        if (sampleTime < 0 || sampleTime > endUs) break
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) break
                        info.set(0, size, ptsBaseOffsetUs + (sampleTime - startUs).coerceAtLeast(0L), mapExtractorFlagsToBufferFlags(extractor.sampleFlags))
                        activeMuxer.writeSampleData(muxV, buf, info)
                        extractor.advance()
                    }
                    extractor.unselectTrack(vTrack)
                }

                if (aTrack >= 0) {
                    extractor.selectTrack(aTrack)
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    while (true) {
                        val sampleTime = extractor.sampleTime
                        if (sampleTime < 0 || sampleTime > endUs) break
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) break
                        info.set(0, size, ptsBaseOffsetUs + (sampleTime - startUs).coerceAtLeast(0L), mapExtractorFlagsToBufferFlags(extractor.sampleFlags))
                        activeMuxer.writeSampleData(muxA, buf, info)
                        extractor.advance()
                    }
                    extractor.unselectTrack(aTrack)
                }
                ptsBaseOffsetUs += rangeDurUs
                onProgress(0.15f + ((rangeIdx + 1f) / keepRangesMs.size) * 0.75f)
            }
        } finally {
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }

        val saved = StorageExportManager.saveVideoFile(context, tempOut, cleanName, formatOption.mimeType)
        tempOut.delete()
        onProgress(1.0f)
        saved
    }

    /**
     * Video Merger / Snapshot Highlight Merge: Concatenates multiple video URIs sequentially
     * using Canvas + H.264 normalized timeline so mismatched resolutions/codecs merge cleanly.
     */
    suspend fun mergeVideoClips(
        clipUris: List<Uri>,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (clipUris.size == 1) {
            val meta = inspectDetailedMetadata(clipUris.first())
            return@withContext exportStreamCopyRanges(clipUris.first(), listOf(0L to meta.durationMs), outputFileName, onProgress = onProgress)
        }
        val w = 1280
        val h = 720
        val fps = 20
        val vidTemp = File(context.cacheDir, "merge_vid_${System.currentTimeMillis()}.mp4")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(vidTemp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIdx = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        val audioChunks = mutableListOf<ShortArray>()
        var globalFrame = 0

        for ((clipIdx, uri) in clipUris.withIndex()) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L
            val clipFrames = ((durMs / 1000.0) * fps).toInt().coerceIn(5, 1200)
            val pcm = audioMixer.decodeUriToPcm(uri)
            val targetClipSamples = ((durMs / 1000.0) * audioMixer.sampleRate).toInt().coerceAtLeast(100)
            val normalizedPcm = ShortArray(targetClipSamples)
            for (s in 0 until minOf(targetClipSamples, pcm.size)) normalizedPcm[s] = pcm[s]
            audioChunks.add(normalizedPcm)

            for (f in 0 until clipFrames) {
                val tsUs = (f * 1_000_000L) / fps
                val bmp = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val c = surface.lockHardwareCanvas()
                c.drawColor(Color.BLACK)
                if (bmp != null) {
                    val scale = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                    val dw = bmp.width * scale
                    val dh = bmp.height * scale
                    c.drawBitmap(bmp, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), null)
                    bmp.recycle()
                }
                surface.unlockCanvasAndPost(c)

                var idx = codec.dequeueOutputBuffer(info, 0)
                while (idx >= 0 || idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                        trackIdx = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    } else if (idx >= 0) {
                        val buf = codec.getOutputBuffer(idx)
                        if (buf != null && started && info.size > 0) {
                            info.presentationTimeUs = (globalFrame * 1_000_000L) / fps
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIdx, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                    }
                    idx = codec.dequeueOutputBuffer(info, 0)
                }
                globalFrame++
            }
            retriever.release()
            onProgress(0.1f + ((clipIdx + 1f) / clipUris.size) * 0.7f)
        }
        codec.signalEndOfInputStream()
        var eos = false
        while (!eos) {
            val idx = codec.dequeueOutputBuffer(info, 10000)
            if (idx >= 0) {
                codec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
            } else if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
        }
        try { codec.stop(); codec.release(); surface.release() } catch (_: Exception) {}
        try { if (started) muxer.stop(); muxer.release() } catch (_: Exception) {}

        val totalSamples = audioChunks.sumOf { it.size }
        val mergedPcm = ShortArray(totalSamples)
        var off = 0
        for (chunk in audioChunks) {
            System.arraycopy(chunk, 0, mergedPcm, off, chunk.size)
            off += chunk.size
        }

        val m4a = File(context.cacheDir, "merge_aud_${System.currentTimeMillis()}.m4a")
        val muxed = File(context.cacheDir, "merge_final_${System.currentTimeMillis()}.mp4")
        val finalSource = if (audioMixer.encodePcmToM4a(mergedPcm, m4a)) {
            muxSimple(vidTemp, m4a, muxed)
            m4a.delete()
            muxed
        } else vidTemp

        val cleanName = if (outputFileName.endsWith(".mp4", true)) outputFileName else "$outputFileName.mp4"
        val saved = StorageExportManager.saveVideoFile(context, finalSource, cleanName, "video/mp4")
        try { vidTemp.delete(); if (finalSource !== vidTemp) finalSource.delete() } catch (_: Exception) {}
        onProgress(1.0f)
        saved
    }

    private fun muxSimple(videoFile: File, audioFile: File, outFile: File) {
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
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) { vSrc = i; vDst = muxer.addTrack(f); break }
            }
            var aSrc = -1; var aDst = -1
            for (i in 0 until aExt.trackCount) {
                val f = aExt.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { aSrc = i; aDst = muxer.addTrack(f); break }
            }
            muxer.start(); started = true
            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            if (vSrc >= 0) {
                vExt.selectTrack(vSrc)
                while (true) {
                    val sz = vExt.readSampleData(buf, 0)
                    if (sz < 0) break
                    info.set(0, sz, vExt.sampleTime, mapExtractorFlagsToBufferFlags(vExt.sampleFlags))
                    muxer.writeSampleData(vDst, buf, info)
                    vExt.advance()
                }
            }
            if (aSrc >= 0) {
                aExt.selectTrack(aSrc)
                while (true) {
                    val sz = aExt.readSampleData(buf, 0)
                    if (sz < 0) break
                    info.set(0, sz, aExt.sampleTime, mapExtractorFlagsToBufferFlags(aExt.sampleFlags))
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
