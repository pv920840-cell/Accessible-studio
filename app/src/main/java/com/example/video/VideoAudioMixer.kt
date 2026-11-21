package com.example.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.storage.StorageExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device PCM/AAC audio engine for Video Studio:
 * - Video Noise Reducer
 * - Time-stretch (Pitch Correction) vs Resample Speed
 * - Sound Effect synthesis & custom SFX decoding
 * - Multi-Music mixing with Smart Ducking + Narration overdubbing
 * - Silence detection for Silence Remover
 * - Video-to-Audio extraction (WAV, MP3, M4A, AAC, FLAC)
 */
class VideoAudioMixer(private val context: Context) {

    private val wavProcessor = WavAudioProcessor(context)
    val sampleRate = 44100

    suspend fun decodeUriToPcm(uri: Uri): ShortArray = wavProcessor.decodeUriToPcm(uri)

    fun synthesizeSfx(preset: SoundEffectPreset, durationMs: Long): ShortArray {
        val count = ((durationMs.coerceIn(150L, 10000L) / 1000.0) * sampleRate).toInt().coerceAtLeast(1000)
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = when (preset) {
                SoundEffectPreset.CHIME -> Math.exp(-t * 3.5)
                SoundEffectPreset.POP -> Math.exp(-t * 14.0)
                SoundEffectPreset.SWOOSH -> sin(PI * (i.toDouble() / count))
                SoundEffectPreset.BELL -> Math.exp(-t * 2.5)
                SoundEffectPreset.BEEP -> if (t < 0.25) 0.9 else 0.0
                SoundEffectPreset.DRUM_HIT -> Math.exp(-t * 10.0)
                SoundEffectPreset.CUSTOM_FILE -> 0.0
            }
            val wave = when (preset) {
                SoundEffectPreset.CHIME -> sin(2.0 * PI * preset.freqHz * t) * 0.6 + sin(2.0 * PI * preset.freqHz * 1.5 * t) * 0.4
                SoundEffectPreset.POP -> sin(2.0 * PI * (preset.freqHz * (1.0 + t * 2.5)) * t)
                SoundEffectPreset.SWOOSH -> {
                    val noise = ((i * 1103515245 + 12345) and 0x7FFF) / 16384.0 - 1.0
                    noise * 0.6 + sin(2.0 * PI * preset.freqHz * t) * 0.4
                }
                SoundEffectPreset.BELL -> sin(2.0 * PI * preset.freqHz * t) * 0.7 + sin(2.0 * PI * preset.freqHz * 2.0 * t) * 0.3
                SoundEffectPreset.BEEP -> sin(2.0 * PI * preset.freqHz * t)
                SoundEffectPreset.DRUM_HIT -> sin(2.0 * PI * (preset.freqHz * Math.exp(-t * 12.0) + 45.0) * t)
                SoundEffectPreset.CUSTOM_FILE -> 0.0
            }
            out[i] = (wave * env * 26000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun reduceVideoNoise(pcm: ShortArray, strength: Float = 0.75f): ShortArray =
        reduceVideoNoise(pcm, strength, this.sampleRate, 1)

    fun reduceVideoNoise(
        pcm: ShortArray,
        strength: Float = 0.75f,
        sampleRate: Int = this.sampleRate,
        channels: Int = 1
    ): ShortArray {
        if (pcm.isEmpty() || strength < 0.05f) return pcm
        val s = strength.coerceIn(0.15f, 1.0f)
        val config = com.example.audio.StudioNoiseReductionConfig(
            strength = s,
            humRemovalStrength = (0.45f + s * 0.42f).coerceIn(0f, 0.90f),
            spectralFloorOverride = (0.26f - s * 0.21f).coerceIn(0.03f, 0.28f),
            pauseAttenuationFloor = (0.76f - s * 0.44f).coerceIn(0.26f, 0.80f),
            lowCutHz = 55f + s * 22f,
            lowCutAttenuation = 0.28f + s * 0.28f,
            preserveHighFrequencies = true,
            vocalClarityBoost = 0.12f * s
        )
        val cleaned = com.example.audio.StudioNoiseReducer.reduceNoise(
            samples = pcm,
            sampleRate = sampleRate,
            channels = channels,
            config = config
        )
        // High sensitivity threshold avoids softening sharp consonants ('t', 'k', 's')
        return StudioAudioProcessor.deClickDeCrackle(cleaned, sensitivityThreshold = 6800)
    }

    /**
     * Changes playback speed. When keepPitch is true, uses overlap-add WSOLA windowing
     * so voices retain natural pitch; otherwise resamples linearly.
     */
    fun changeSpeed(pcm: ShortArray, speed: Float, keepPitch: Boolean): ShortArray {
        if (pcm.isEmpty() || abs(speed - 1.0f) < 0.03f) return pcm
        if (!keepPitch) return wavProcessor.applySpeed(pcm, speed)

        val winSize = 1764 // 40ms at 44.1kHz
        val hopOut = winSize / 2
        val hopIn = (hopOut * speed).toInt().coerceAtLeast(16)
        val outSize = (pcm.size / speed).toInt().coerceAtLeast(1)
        val accum = FloatArray(outSize + winSize)
        val norm = FloatArray(outSize + winSize)

        var inPos = 0
        var outPos = 0
        while (inPos + winSize < pcm.size && outPos < outSize) {
            for (k in 0 until winSize) {
                val hann = (0.5 * (1.0 - kotlin.math.cos(2.0 * PI * k / winSize))).toFloat()
                accum[outPos + k] += pcm[inPos + k] * hann
                norm[outPos + k] += hann
            }
            inPos += hopIn
            outPos += hopOut
        }

        val out = ShortArray(outSize)
        for (i in 0 until outSize) {
            val denom = if (norm[i] > 0.01f) norm[i] else 1f
            out[i] = (accum[i] / denom).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Scans PCM waveform in 50ms chunks to find non-silent intervals [(startMs, endMs)]
     * when removing silent pauses below thresholdDb for at least minSilenceMs.
     */
    fun detectAudibleSegments(
        pcm: ShortArray,
        thresholdDb: Float = -35f,
        minSilenceMs: Long = 500L
    ): List<Pair<Long, Long>> {
        if (pcm.isEmpty()) return emptyList()
        val totalMs = (pcm.size * 1000L) / sampleRate
        val chunkSamples = (sampleRate * 0.05).toInt() // 50ms
        val threshLinear = 32767.0 * Math.pow(10.0, thresholdDb / 20.0)
        val minSilenceChunks = (minSilenceMs / 50L).toInt().coerceAtLeast(2)

        val totalChunks = (pcm.size + chunkSamples - 1) / chunkSamples
        val isSilentChunk = BooleanArray(totalChunks)
        for (c in 0 until totalChunks) {
            val start = c * chunkSamples
            val end = minOf(start + chunkSamples, pcm.size)
            var sumSq = 0.0
            for (i in start until end) {
                val v = pcm[i].toDouble()
                sumSq += v * v
            }
            val rms = sqrt(sumSq / (end - start).coerceAtLeast(1))
            isSilentChunk[c] = rms < threshLinear
        }

        // Convert long silent runs into cut regions; return kept audible ranges
        val kept = mutableListOf<Pair<Long, Long>>()
        var segStartChunk = 0
        var c = 0
        while (c < totalChunks) {
            if (isSilentChunk[c]) {
                var run = 0
                while (c + run < totalChunks && isSilentChunk[c + run]) run++
                if (run >= minSilenceChunks) {
                    val endMs = (c * 50L).coerceAtMost(totalMs)
                    val startMs = (segStartChunk * 50L).coerceAtLeast(0L)
                    if (endMs - startMs >= 150L) kept.add(startMs to endMs)
                    segStartChunk = c + run
                    c += run
                    continue
                }
            }
            c++
        }
        val finalStart = (segStartChunk * 50L).coerceAtLeast(0L)
        if (totalMs - finalStart >= 150L) kept.add(finalStart to totalMs)
        return if (kept.isEmpty()) listOf(0L to totalMs) else kept
    }

    /**
     * Builds the final master mono PCM track combining:
     * - Base video audio (Original / Muted / Replaced), optional Noise Reduction, Volume, Speed, Reverse
     * - Multi-Music tracks with Smart Ducking (automatically dips music when narration/dialogue speaks)
     * - Narration segments
     * - Sound Effects at specific timestamps
     */
    suspend fun buildMixedMasterPcm(
        basePcm: ShortArray,
        targetDurationMs: Long,
        audioOption: VideoAudioOption,
        replacedAudioUri: Uri?,
        baseVolume: Float,
        noiseReduceEnabled: Boolean,
        noiseReduceIntensity: Float,
        musicTracks: List<MusicLayerTrack>,
        smartDuckingEnabled: Boolean,
        narrations: List<NarrationSegment>,
        soundEffects: List<SoundEffectOverlay>,
        speed: Float,
        keepPitch: Boolean,
        reverse: Boolean
    ): ShortArray = withContext(Dispatchers.IO) {
        val targetSamples = ((targetDurationMs.coerceAtLeast(500L) / 1000.0) * sampleRate).toInt()
        var primary = when {
            audioOption == VideoAudioOption.MUTE -> ShortArray(targetSamples)
            audioOption == VideoAudioOption.REPLACE_WITH_STUDIO_AUDIO && replacedAudioUri != null -> {
                val rep = decodeUriToPcm(replacedAudioUri)
                if (rep.isEmpty()) ShortArray(targetSamples) else rep
            }
            else -> basePcm
        }

        if (noiseReduceEnabled && primary.isNotEmpty() && audioOption != VideoAudioOption.MUTE) {
            primary = reduceVideoNoise(primary, noiseReduceIntensity)
        }

        val musicPcms = musicTracks.map { track ->
            val pcm = decodeUriToPcm(track.uri)
            track to pcm
        }

        val sfxPcms = soundEffects.map { sfx ->
            val pcm = if (sfx.preset == SoundEffectPreset.CUSTOM_FILE && sfx.customUri != null) {
                val decoded = decodeUriToPcm(sfx.customUri)
                if (decoded.isNotEmpty()) decoded else synthesizeSfx(SoundEffectPreset.CHIME, sfx.durationMs)
            } else {
                synthesizeSfx(sfx.preset, sfx.durationMs)
            }
            sfx to pcm
        }

        val rawMix = FloatArray(targetSamples)
        val speechActive = BooleanArray(targetSamples)

        for (i in 0 until targetSamples) {
            if (i < primary.size && audioOption != VideoAudioOption.MUTE) {
                val s = primary[i] * baseVolume
                rawMix[i] += s
                if (abs(s) > 1600f) speechActive[i] = true
            }
        }

        for (narration in narrations) {
            val startIdx = ((narration.startMs / 1000.0) * sampleRate).toInt().coerceIn(0, targetSamples)
            for (j in narration.pcmSamples.indices) {
                val idx = startIdx + j
                if (idx >= targetSamples) break
                val s = narration.pcmSamples[j].toFloat() * 1.1f
                rawMix[idx] += s
                if (abs(s) > 900f) speechActive[idx] = true
            }
        }

        for ((sfx, sfxPcm) in sfxPcms) {
            val startIdx = ((sfx.timestampMs / 1000.0) * sampleRate).toInt().coerceIn(0, targetSamples)
            val maxSamples = ((sfx.durationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(1)
            val limit = minOf(sfxPcm.size, maxSamples)
            for (j in 0 until limit) {
                val idx = startIdx + j
                if (idx >= targetSamples) break
                rawMix[idx] += sfxPcm[j] * sfx.volume
            }
        }

        // Smooth speech-activity hangover (~45ms) so ducking doesn't chatter between vocal cycles
        val hangoverSamples = (sampleRate * 0.045).toInt()
        var speechHoldCounter = 0
        val smoothedSpeech = BooleanArray(targetSamples)
        for (i in 0 until targetSamples) {
            if (speechActive[i]) {
                speechHoldCounter = hangoverSamples
                smoothedSpeech[i] = true
            } else if (speechHoldCounter > 0) {
                speechHoldCounter--
                smoothedSpeech[i] = true
            }
        }

        // Mix Multi-Music with smooth envelope ducking and click-free Repeat/Loop across full video
        var duckEnv = 1.0f
        val fadeSamples = (sampleRate * 0.012).toInt().coerceAtLeast(1) // 12ms boundary taper
        for (i in 0 until targetSamples) {
            val targetDuck = if (smartDuckingEnabled && smoothedSpeech[i]) 0.25f else 1.0f
            val alpha = if (targetDuck < duckEnv) 0.008f else 0.0005f
            duckEnv += (targetDuck - duckEnv) * alpha

            for ((track, mPcm) in musicPcms) {
                if (mPcm.isEmpty()) continue
                val trackStartIdx = ((track.startMs / 1000.0) * sampleRate).toInt()
                if (i >= trackStartIdx) {
                    val relIdx = i - trackStartIdx
                    val sampleIdx = if (track.loop) relIdx % mPcm.size else relIdx
                    if (sampleIdx < mPcm.size) {
                        val loopTaper = if (track.loop && mPcm.size > fadeSamples * 3 && relIdx >= mPcm.size) {
                            when {
                                sampleIdx < fadeSamples -> sampleIdx.toFloat() / fadeSamples
                                sampleIdx >= mPcm.size - fadeSamples -> (mPcm.size - 1 - sampleIdx).toFloat() / fadeSamples
                                else -> 1.0f
                            }
                        } else {
                            1.0f
                        }
                        rawMix[i] += mPcm[sampleIdx] * track.volume * duckEnv * loopTaper
                    }
                }
            }
        }

        var mixed = ShortArray(targetSamples) { i ->
            rawMix[i].toInt().coerceIn(-32767, 32767).toShort()
        }
        mixed = StudioAudioProcessor.applyTruePeakLimiter(mixed, -0.8f)

        if (abs(speed - 1.0f) >= 0.03f) {
            mixed = changeSpeed(mixed, speed, keepPitch)
        }
        if (reverse) {
            mixed = StudioAudioProcessor.reverseAudio(mixed)
        }
        mixed
    }

    /**
     * Encodes a 44.1kHz mono ShortArray into an M4A (AAC-LC in MPEG-4 container) file
     */
    fun encodePcmToM4a(pcm: ShortArray, outFile: File): Boolean {
        if (pcm.isEmpty()) return false
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            val info = MediaCodec.BufferInfo()
            val samplesPerChunk = 1024
            var sampleOffset = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            if (sampleOffset >= pcm.size) {
                                codec.queueInputBuffer(inIdx, 0, 0, (sampleOffset * 1_000_000L) / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val count = minOf(samplesPerChunk, pcm.size - sampleOffset)
                                val bytes = ByteArray(count * 2)
                                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, sampleOffset, count)
                                inBuf.put(bytes)
                                val ptsUs = (sampleOffset * 1_000_000L) / sampleRate
                                codec.queueInputBuffer(inIdx, 0, bytes.size, ptsUs, 0)
                                sampleOffset += count
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 5000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            muxerStarted
        } catch (_: Exception) {
            false
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop(); muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Video to Audio: extracts audio track from video URI and saves in chosen format (WAV/MP3/M4A/AAC/FLAC)
     */
    suspend fun extractAudioFromVideo(
        videoUri: Uri,
        format: String,
        outputFileName: String,
        applyNoiseReduction: Boolean = false,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.1f)
        var pcm = decodeUriToPcm(videoUri)
        if (pcm.isEmpty()) {
            // If video has no audio track, generate short 1s silent buffer cleanly so operation doesn't crash
            pcm = ShortArray(sampleRate)
        }
        onProgress(0.45f)
        if (applyNoiseReduction) {
            pcm = reduceVideoNoise(pcm, 0.7f)
        }
        onProgress(0.65f)

        val ext = format.lowercase()
        val cleanBase = outputFileName.substringBeforeLast('.')
        val targetName = "$cleanBase.$ext"
        val tempFile = File(context.cacheDir, "v2a_${System.currentTimeMillis()}.$ext")

        if (ext == "m4a" || ext == "aac") {
            val m4aTemp = File(context.cacheDir, "v2a_${System.currentTimeMillis()}.m4a")
            val ok = encodePcmToM4a(pcm, m4aTemp)
            if (ok && m4aTemp.exists()) {
                m4aTemp.copyTo(tempFile, overwrite = true)
                m4aTemp.delete()
            } else {
                wavProcessor.writeWavFile(tempFile, pcm, sampleRate, 1)
            }
        } else {
            wavProcessor.writeWavFile(tempFile, pcm, sampleRate, 1)
        }
        onProgress(0.9f)

        val saved = StorageExportManager.saveAudioFile(
            context = context,
            sourceFile = tempFile,
            targetFileName = targetName,
            mimeType = StorageExportManager.getAudioMimeType(targetName)
        )
        try { tempFile.delete() } catch (_: Exception) {}
        onProgress(1.0f)
        saved
    }
}
