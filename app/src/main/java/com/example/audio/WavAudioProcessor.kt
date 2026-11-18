package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class AudioTrackConfig(
    val title: String,
    val uri: Uri? = null,
    val isDemo: Boolean = false,
    val demoType: DemoAudioType = DemoAudioType.PIANO_MELODY,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val loop: Boolean = false
)

enum class DemoAudioType(val displayName: String) {
    PIANO_MELODY("Melodic Chords"),
    AMBIENT_PAD("Atmospheric Synth"),
    VOICE_TONE("Acoustic Speech Tone"),
    PERCUSSION_BEAT("Rhythm Beat")
}

data class AudioEditSettings(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 10000L,
    val masterVolume: Float = 1.0f,
    val fadeInMs: Long = 500L,
    val fadeOutMs: Long = 1000L,
    val speedMultiplier: Float = 1.0f,
    val eqPreset: EqPreset = EqPreset.NORMAL,

    // Echo (Eco) effect
    val echoEnabled: Boolean = false,
    val echoDelayMs: Long = 280L,
    val echoFeedback: Float = 0.45f,
    val echoMix: Float = 0.35f,

    // Reverb (River) effect
    val reverbEnabled: Boolean = false,
    val reverbRoomSize: Float = 0.65f,
    val reverbDamping: Float = 0.40f,
    val reverbMix: Float = 0.35f,

    // Noise Reduction & Noise Gate
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionIntensity: Float = 0.60f,
    val noiseGateEnabled: Boolean = false,
    val noiseGateThreshold: Float = 0.08f,

    // Tone & Dynamic Boosters
    val bassBoost: Float = 0.0f,
    val trebleBoost: Float = 0.0f,
    val isReversed: Boolean = false,

    // Studio Pro FX Rack
    val studioFx: StudioFxSettings = StudioFxSettings()
)

enum class EqPreset(val displayName: String) {
    NORMAL("Normal / Flat"),
    VOCAL_BOOST("Vocal Clarity Boost"),
    BASS_BOOST("Warm Bass Boost"),
    TREBLE_BOOST("Crisp Highs"),
    RADIO_LOFI("Retro Lo-Fi Radio")
}

data class StereoPcmData(
    val left: ShortArray,
    val right: ShortArray,
    val sampleRate: Int,
    val channelCount: Int
)

class WavAudioProcessor(private val context: Context? = null) {

    val sampleRate = 44100
    val channels = 1 // Mono for high clarity, fast processing & accessible editing

    companion object {
        // Supports up to 60 minutes (Long arithmetic prevents 32-bit Int byte-count overflow)
        const val MAX_DECODE_DURATION_SECONDS = 3600L

        /**
         * Downmixes an interleaved little-endian 16-bit PCM byte buffer directly into mono ShortArray.
         */
        fun downmixPcm16ToMono(
            pcmBytes: ByteArray,
            byteCount: Int,
            channelCount: Int
        ): ShortArray = downmixPcmBytesToMono(
            pcmBytes = pcmBytes,
            byteCount = byteCount,
            channelCount = channelCount,
            pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        )

        /**
         * Downmixes PCM byte buffer (8-bit, 16-bit, 24-bit, 32-bit int, or 32-bit float) into mono ShortArray.
         */
        fun downmixPcmBytesToMono(
            pcmBytes: ByteArray,
            byteCount: Int,
            channelCount: Int,
            pcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
        ): ShortArray {
            val ch = channelCount.coerceAtLeast(1)
            val safeByteCount = byteCount.coerceAtMost(pcmBytes.size)
            val bytesPerSample = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            val frameByteSize = ch * bytesPerSample
            val frameCount = safeByteCount / frameByteSize
            if (frameCount <= 0) return ShortArray(0)

            val mono = ShortArray(frameCount)
            val bb = ByteBuffer.wrap(pcmBytes, 0, frameCount * frameByteSize).order(ByteOrder.LITTLE_ENDIAN)

            when (bytesPerSample) {
                1 -> {
                    for (i in 0 until frameCount) {
                        var sum = 0
                        for (c in 0 until ch) {
                            val unsigned = bb.get().toInt() and 0xFF
                            sum += (unsigned - 128) shl 8
                        }
                        mono[i] = (sum / ch).coerceIn(-32767, 32767).toShort()
                    }
                }
                2 -> {
                    if (ch == 1) {
                        bb.asShortBuffer().get(mono)
                    } else {
                        for (i in 0 until frameCount) {
                            var sum = 0
                            for (c in 0 until ch) {
                                sum += bb.short.toInt()
                            }
                            mono[i] = (sum / ch).coerceIn(-32767, 32767).toShort()
                        }
                    }
                }
                3 -> {
                    for (i in 0 until frameCount) {
                        var sum = 0
                        for (c in 0 until ch) {
                            val b0 = bb.get().toInt() and 0xFF
                            val b1 = bb.get().toInt() and 0xFF
                            val b2 = bb.get().toInt()
                            val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                            sum += sample24 shr 8
                        }
                        mono[i] = (sum / ch).coerceIn(-32767, 32767).toShort()
                    }
                }
                else -> {
                    val isFloat = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                    for (i in 0 until frameCount) {
                        var sum = 0.0
                        for (c in 0 until ch) {
                            val v = if (isFloat) {
                                (bb.float.coerceIn(-1.0f, 1.0f) * 32767.0).toDouble()
                            } else {
                                (bb.int shr 16).toDouble()
                            }
                            sum += v
                        }
                        mono[i] = (sum / ch).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
            }
            return mono
        }

        internal fun computeMaxSourceFramesBudget(srcRate: Int): Long {
            val safeSr = srcRate.coerceAtLeast(8000)
            val ceilingByDuration = MAX_DECODE_DURATION_SECONDS * safeSr.toLong()
            val heapBudgetFrames = (Runtime.getRuntime().maxMemory() * 45L / 100L) / 2L
            val minFloor = 900L * safeSr.toLong()
            return heapBudgetFrames.coerceAtLeast(minFloor).coerceAtMost(ceilingByDuration)
        }

        internal fun computeMaxStereoSourceFramesBudget(srcRate: Int): Long {
            val safeSr = srcRate.coerceAtLeast(8000)
            val ceilingByDuration = (MAX_DECODE_DURATION_SECONDS / 2L) * safeSr.toLong()
            // Each stereo frame requires L + R shorts (4 bytes) plus resample scratch
            val heapBudgetFrames = (Runtime.getRuntime().maxMemory() * 20L / 100L) / 2L
            val minFloor = 240L * safeSr.toLong()
            return heapBudgetFrames.coerceAtLeast(minFloor).coerceAtMost(ceilingByDuration)
        }

        /**
         * Direct RIFF WAV parser supporting PCM 8/16/24/32-bit and 32/64-bit IEEE float.
         * Returns null if file is not standard uncompressed RIFF WAV (allowing MediaCodec fallback).
         */
        fun tryDecodeRiffWavDirect(file: File, targetSampleRate: Int = 44100): ShortArray? {
            if (!file.exists() || file.length() < 44L) return null
            RandomAccessFile(file, "r").use { raf ->
                val header12 = ByteArray(12)
                if (raf.read(header12) != 12) return null
                if (header12[0] != 'R'.code.toByte() || header12[1] != 'I'.code.toByte() ||
                    header12[2] != 'F'.code.toByte() || header12[3] != 'F'.code.toByte() ||
                    header12[8] != 'W'.code.toByte() || header12[9] != 'A'.code.toByte() ||
                    header12[10] != 'V'.code.toByte() || header12[11] != 'E'.code.toByte()
                ) {
                    return null
                }

                var audioFormat = -1
                var numChannels = 0
                var wavSampleRate = 0
                var bitsPerSample = 0
                var dataOffset = -1L
                var dataSize = 0L
                val fileLen = raf.length()
                val chunkHeader = ByteArray(8)
                val bb8 = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)

                while (raf.filePointer + 8L <= fileLen) {
                    if (raf.read(chunkHeader) != 8) break
                    val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    bb8.clear()
                    val chunkSize = bb8.getInt(4).toLong() and 0xFFFFFFFFL
                    val chunkStart = raf.filePointer

                    if (id == "fmt " && chunkSize >= 16L) {
                        val fmtBytes = ByteArray(minOf(chunkSize, 64L).toInt())
                        raf.readFully(fmtBytes)
                        val fmtBb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = fmtBb.getShort(0).toInt() and 0xFFFF
                        numChannels = fmtBb.getShort(2).toInt() and 0xFFFF
                        wavSampleRate = fmtBb.getInt(4)
                        bitsPerSample = fmtBb.getShort(14).toInt() and 0xFFFF
                        if (audioFormat == 0xFFFE && fmtBytes.size >= 26) {
                            audioFormat = fmtBb.getShort(24).toInt() and 0xFFFF
                        }
                    } else if (id == "data") {
                        dataOffset = chunkStart
                        val remaining = (fileLen - chunkStart).coerceAtLeast(0L)
                        dataSize = if (chunkSize > 0L) minOf(chunkSize, remaining) else remaining
                        break
                    }

                    val paddedSize = chunkSize + (chunkSize and 1L)
                    val nextPos = (chunkStart + paddedSize).coerceAtMost(fileLen)
                    if (nextPos <= chunkStart && paddedSize > 0L) break
                    raf.seek(nextPos)
                }

                if (dataOffset < 0L || dataSize <= 0L || numChannels <= 0 || wavSampleRate < 8000) {
                    return null
                }

                val pcmEncoding = when {
                    audioFormat == 1 && bitsPerSample == 8 -> AudioFormat.ENCODING_PCM_8BIT
                    audioFormat == 1 && bitsPerSample == 16 -> AudioFormat.ENCODING_PCM_16BIT
                    audioFormat == 1 && bitsPerSample == 24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                    audioFormat == 1 && bitsPerSample == 32 -> AudioFormat.ENCODING_PCM_32BIT
                    audioFormat == 3 && bitsPerSample == 32 -> AudioFormat.ENCODING_PCM_FLOAT
                    else -> return null
                }

                val bytesPerFrame = (numChannels * (bitsPerSample / 8)).coerceAtLeast(1)
                val maxFrames = computeMaxSourceFramesBudget(wavSampleRate)
                val maxBytes = minOf(dataSize, maxFrames * bytesPerFrame.toLong())
                raf.seek(dataOffset)

                val blockAligned = ((65536 / bytesPerFrame).coerceAtLeast(1)) * bytesPerFrame
                val readBuf = ByteArray(blockAligned)
                val monoChunks = ArrayList<ShortArray>(64)
                var remaining = maxBytes

                while (remaining >= bytesPerFrame) {
                    val toRead = minOf(readBuf.size.toLong(), remaining).toInt()
                    val n = raf.read(readBuf, 0, toRead)
                    if (n <= 0) break
                    val usable = (n / bytesPerFrame) * bytesPerFrame
                    if (usable > 0) {
                        val mono = downmixPcmBytesToMono(readBuf, usable, numChannels, pcmEncoding)
                        if (mono.isNotEmpty()) monoChunks.add(mono)
                    }
                    remaining -= n.toLong()
                }

                if (monoChunks.isEmpty()) return null
                return assembleAndResampleMonoChunks(
                    monoChunks = monoChunks,
                    sourceSampleRate = wavSampleRate,
                    targetSampleRate = targetSampleRate
                )
            }
        }

        /**
         * Combines decoded mono chunks and resamples from sourceSampleRate to targetSampleRate
         * without 32-bit overflow or artificial 5-minute cutoff.
         */
        fun assembleAndResampleMonoChunks(
            monoChunks: MutableList<ShortArray>,
            sourceSampleRate: Int,
            targetSampleRate: Int = 44100
        ): ShortArray {
            var totalFrames = 0L
            for (chunk in monoChunks) {
                totalFrames += chunk.size.toLong()
            }
            if (totalFrames <= 0L) return ShortArray(0)

            val safeSrcRate = sourceSampleRate.coerceAtLeast(8000)
            val safeDstRate = targetSampleRate.coerceAtLeast(8000)

            val contiguousLen = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val contiguous = ShortArray(contiguousLen)
            var offset = 0
            for (i in monoChunks.indices) {
                val chunk = monoChunks[i]
                val copyLen = minOf(chunk.size, contiguousLen - offset)
                if (copyLen > 0) {
                    System.arraycopy(chunk, 0, contiguous, offset, copyLen)
                    offset += copyLen
                }
                monoChunks[i] = ShortArray(0)
            }

            if (safeSrcRate == safeDstRate) return contiguous

            val ratio = safeSrcRate.toDouble() / safeDstRate.toDouble()
            val targetLen = ((contiguous.size.toLong() * safeDstRate.toLong()) / safeSrcRate.toLong())
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
            val resampled = ShortArray(targetLen)
            val lastIdx = contiguous.size - 1
            for (i in 0 until targetLen) {
                val origPos = i.toDouble() * ratio
                val idx0 = origPos.toInt().coerceIn(0, lastIdx)
                val idx1 = (idx0 + 1).coerceIn(0, lastIdx)
                val frac = origPos - idx0
                val sample = (contiguous[idx0] * (1.0 - frac) + contiguous[idx1] * frac).toInt()
                resampled[i] = sample.coerceIn(-32767, 32767).toShort()
            }
            return resampled
        }

        /**
         * Splits PCM byte buffer into separate Left and Right ShortArrays (duplicating mono to both channels if ch == 1).
         */
        fun splitPcmBytesToStereo(
            pcmBytes: ByteArray,
            byteCount: Int,
            channelCount: Int,
            pcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
        ): Pair<ShortArray, ShortArray> {
            val ch = channelCount.coerceAtLeast(1)
            val safeByteCount = byteCount.coerceAtMost(pcmBytes.size)
            val bytesPerSample = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            val frameByteSize = ch * bytesPerSample
            val frameCount = safeByteCount / frameByteSize
            if (frameCount <= 0) return Pair(ShortArray(0), ShortArray(0))

            val left = ShortArray(frameCount)
            val right = ShortArray(frameCount)
            val bb = ByteBuffer.wrap(pcmBytes, 0, frameCount * frameByteSize).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until frameCount) {
                var lSample = 0
                var rSample = 0
                for (c in 0 until ch) {
                    val sample16 = when (bytesPerSample) {
                        1 -> {
                            val unsigned = bb.get().toInt() and 0xFF
                            ((unsigned - 128) shl 8).coerceIn(-32767, 32767)
                        }
                        2 -> bb.short.toInt().coerceIn(-32767, 32767)
                        3 -> {
                            val b0 = bb.get().toInt() and 0xFF
                            val b1 = bb.get().toInt() and 0xFF
                            val b2 = bb.get().toInt()
                            val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                            (sample24 shr 8).coerceIn(-32767, 32767)
                        }
                        else -> {
                            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val f = bb.float
                                val clamped = if (f.isFinite()) f.coerceIn(-1.0f, 1.0f) else 0f
                                (clamped * 32767.0f).toInt().coerceIn(-32767, 32767)
                            } else {
                                (bb.int shr 16).coerceIn(-32767, 32767)
                            }
                        }
                    }
                    if (c == 0) {
                        lSample = sample16
                        rSample = sample16
                    } else if (c == 1) {
                        rSample = sample16
                    }
                }
                left[i] = lSample.toShort()
                right[i] = rSample.toShort()
            }
            return Pair(left, right)
        }

        fun tryDecodeRiffWavDirectStereo(file: File, targetSampleRate: Int = 44100): StereoPcmData? {
            if (!file.exists() || file.length() < 44L) return null
            RandomAccessFile(file, "r").use { raf ->
                val header12 = ByteArray(12)
                if (raf.read(header12) != 12) return null
                if (header12[0] != 'R'.code.toByte() || header12[1] != 'I'.code.toByte() ||
                    header12[2] != 'F'.code.toByte() || header12[3] != 'F'.code.toByte() ||
                    header12[8] != 'W'.code.toByte() || header12[9] != 'A'.code.toByte() ||
                    header12[10] != 'V'.code.toByte() || header12[11] != 'E'.code.toByte()
                ) {
                    return null
                }

                var audioFormat = -1
                var numChannels = 0
                var wavSampleRate = 0
                var bitsPerSample = 0
                var dataOffset = -1L
                var dataSize = 0L
                val fileLen = raf.length()
                val chunkHeader = ByteArray(8)
                val bb8 = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)

                while (raf.filePointer + 8L <= fileLen) {
                    if (raf.read(chunkHeader) != 8) break
                    val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    bb8.clear()
                    val chunkSize = bb8.getInt(4).toLong() and 0xFFFFFFFFL
                    val chunkStart = raf.filePointer

                    if (id == "fmt " && chunkSize >= 16L) {
                        val fmtBytes = ByteArray(minOf(chunkSize, 64L).toInt())
                        raf.readFully(fmtBytes)
                        val fmtBb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = fmtBb.getShort(0).toInt() and 0xFFFF
                        numChannels = fmtBb.getShort(2).toInt() and 0xFFFF
                        wavSampleRate = fmtBb.getInt(4)
                        bitsPerSample = fmtBb.getShort(14).toInt() and 0xFFFF
                        if (audioFormat == 0xFFFE && fmtBytes.size >= 26) {
                            audioFormat = fmtBb.getShort(24).toInt() and 0xFFFF
                        }
                    } else if (id == "data") {
                        dataOffset = chunkStart
                        val remaining = (fileLen - chunkStart).coerceAtLeast(0L)
                        dataSize = if (chunkSize > 0L) minOf(chunkSize, remaining) else remaining
                        break
                    }

                    val paddedSize = chunkSize + (chunkSize and 1L)
                    val nextPos = (chunkStart + paddedSize).coerceAtMost(fileLen)
                    if (nextPos <= chunkStart && paddedSize > 0L) break
                    raf.seek(nextPos)
                }

                if (dataOffset < 0L || dataSize <= 0L || numChannels <= 0 || wavSampleRate < 8000) {
                    return null
                }

                val pcmEncoding = when {
                    audioFormat == 1 && bitsPerSample == 8 -> AudioFormat.ENCODING_PCM_8BIT
                    audioFormat == 1 && bitsPerSample == 16 -> AudioFormat.ENCODING_PCM_16BIT
                    audioFormat == 1 && bitsPerSample == 24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                    audioFormat == 1 && bitsPerSample == 32 -> AudioFormat.ENCODING_PCM_32BIT
                    audioFormat == 3 && bitsPerSample == 32 -> AudioFormat.ENCODING_PCM_FLOAT
                    else -> return null
                }

                val bytesPerFrame = (numChannels * (bitsPerSample / 8)).coerceAtLeast(1)
                val maxFrames = computeMaxStereoSourceFramesBudget(wavSampleRate)
                val maxBytes = minOf(dataSize, maxFrames * bytesPerFrame.toLong())
                raf.seek(dataOffset)

                val blockAligned = ((65536 / bytesPerFrame).coerceAtLeast(1)) * bytesPerFrame
                val readBuf = ByteArray(blockAligned)
                val leftChunks = ArrayList<ShortArray>(64)
                val rightChunks = ArrayList<ShortArray>(64)
                var remaining = maxBytes

                while (remaining >= bytesPerFrame) {
                    val toRead = minOf(readBuf.size.toLong(), remaining).toInt()
                    val n = raf.read(readBuf, 0, toRead)
                    if (n <= 0) break
                    val usable = (n / bytesPerFrame) * bytesPerFrame
                    if (usable > 0) {
                        val (l, r) = splitPcmBytesToStereo(readBuf, usable, numChannels, pcmEncoding)
                        if (l.isNotEmpty()) {
                            leftChunks.add(l)
                            rightChunks.add(r)
                        }
                    }
                    remaining -= n.toLong()
                }

                if (leftChunks.isEmpty()) return null
                val leftResampled = assembleAndResampleMonoChunks(leftChunks, wavSampleRate, targetSampleRate)
                val rightResampled = assembleAndResampleMonoChunks(rightChunks, wavSampleRate, targetSampleRate)
                return StereoPcmData(
                    left = leftResampled,
                    right = rightResampled,
                    sampleRate = targetSampleRate,
                    channelCount = numChannels
                )
            }
        }
    }

    /**
     * Synthesizes demo audio PCM 16-bit mono
     */
    fun generateDemoPcm(type: DemoAudioType, durationSec: Int = 10): ShortArray {
        val numSamples = sampleRate * durationSec
        val samples = ShortArray(numSamples)

        when (type) {
            DemoAudioType.PIANO_MELODY -> {
                val chords = listOf(
                    floatArrayOf(261.63f, 329.63f, 392.00f), // C
                    floatArrayOf(196.00f, 246.94f, 293.66f), // G
                    floatArrayOf(220.00f, 261.63f, 329.63f), // Am
                    floatArrayOf(174.61f, 220.00f, 261.63f)  // F
                )
                val chordDuration = (sampleRate * 2.5).toInt()
                for (i in 0 until numSamples) {
                    val chordIdx = (i / chordDuration) % chords.size
                    val chord = chords[chordIdx]
                    val t = i.toDouble() / sampleRate
                    var mixedVal = 0.0
                    for (freq in chord) {
                        mixedVal += sin(2.0 * PI * freq * t)
                    }
                    val envelope = 0.5 + 0.5 * sin(2.0 * PI * 2.0 * t)
                    val sample = (mixedVal / chord.size * envelope * 24000).toInt().coerceIn(-32767, 32767)
                    samples[i] = sample.toShort()
                }
            }
            DemoAudioType.AMBIENT_PAD -> {
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val wave1 = sin(2.0 * PI * 110.0 * t)
                    val wave2 = sin(2.0 * PI * 164.81 * t)
                    val wave3 = sin(2.0 * PI * 220.0 * t)
                    val lfo = 0.7 + 0.3 * sin(2.0 * PI * 0.25 * t)
                    val s = ((wave1 * 0.4 + wave2 * 0.3 + wave3 * 0.3) * lfo * 20000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            DemoAudioType.VOICE_TONE -> {
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val f0 = 150.0 + 30.0 * sin(2.0 * PI * 1.5 * t)
                    val f1 = 800.0
                    val s = (sin(2.0 * PI * f0 * t) * 0.6 + sin(2.0 * PI * f1 * t) * 0.3) * 22000
                    samples[i] = s.toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            DemoAudioType.PERCUSSION_BEAT -> {
                val beatInterval = (sampleRate * 0.5).toInt() // 120 bpm
                for (i in 0 until numSamples) {
                    val posInBeat = i % beatInterval
                    val beatTime = posInBeat.toDouble() / sampleRate
                    val decay = Math.exp(-beatTime * 18.0)
                    val kick = sin(2.0 * PI * (120.0 * Math.exp(-beatTime * 25.0)) * beatTime) * decay
                    samples[i] = (kick * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
        return samples
    }

    /**
     * Decodes an audio file / uri to mono 44.1 kHz PCM ShortArray using MediaExtractor & MediaCodec.
     * Streams output buffers directly into mono chunks, properly separates input/output EOS flags,
     * honors INFO_OUTPUT_FORMAT_CHANGED, and uses 64-bit Long limits so multi-minute tracks decode completely.
     */
    suspend fun decodeUriToPcm(uri: Uri): ShortArray = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext ShortArray(0)
        val displayName = queryAudioDisplayName(uri)
        val rawExt = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 }
        val mimeHint = try { ctx.contentResolver.getType(uri) } catch (_: Exception) { null }
        val fileExt = when {
            rawExt != null -> ".$rawExt"
            mimeHint == "audio/wav" || mimeHint == "audio/x-wav" -> ".wav"
            mimeHint == "audio/mpeg" -> ".mp3"
            mimeHint == "audio/mp4" -> ".m4a"
            mimeHint == "audio/aac" -> ".aac"
            mimeHint == "audio/ogg" -> ".ogg"
            mimeHint == "audio/flac" -> ".flac"
            else -> ".tmp"
        }
        val tempFile = File.createTempFile("import_audio_", fileExt, ctx.cacheDir)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            var copiedToTemp = false
            val inputStream = try {
                ctx.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            } ?: try {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
            } catch (_: Exception) {
                null
            }

            if (inputStream != null) {
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buf = ByteArray(16384)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            outputStream.write(buf, 0, read)
                        }
                        outputStream.flush()
                    }
                }
                copiedToTemp = tempFile.exists() && tempFile.length() >= 44L
            }

            if (copiedToTemp) {
                tryDecodeRiffWavDirect(tempFile, sampleRate)?.let { directWav ->
                    if (directWav.isNotEmpty()) return@withContext directWav
                }
                extractor.setDataSource(tempFile.absolutePath)
            } else {
                extractor.setDataSource(ctx, uri, null)
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                return@withContext ShortArray(0)
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            var activeChannelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1
            var activeSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else sampleRate
            var activePcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else AudioFormat.ENCODING_PCM_16BIT

            decoder = try {
                MediaCodec.createDecoderByType(mime).also {
                    it.configure(format, null, null, 0)
                }
            } catch (primaryErr: Throwable) {
                val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                if (codecName != null) {
                    MediaCodec.createByCodecName(codecName).also {
                        it.configure(format, null, null, 0)
                    }
                } else {
                    throw primaryErr
                }
            }
            decoder.start()

            val monoChunks = ArrayList<ShortArray>(256)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var totalMonoFrames = 0L
            var emptyOutputStalls = 0
            val timeoutUs = 10000L

            var maxSourceFrames = computeMaxSourceFramesBudget(activeSampleRate)
            var reusableChunk = ByteArray(16384)

            while (!outputEos && totalMonoFrames < maxSourceFrames) {
                if (!inputEos) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuf = decoder.getInputBuffer(inIndex)
                        if (inputBuf != null) {
                            inputBuf.clear()
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                var outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                    emptyOutputStalls++
                    if (emptyOutputStalls > 600) break
                }
                while (outIndex >= 0 ||
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ||
                    outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                ) {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFmt = decoder.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            activeChannelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            activeSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                            maxSourceFrames = computeMaxSourceFramesBudget(activeSampleRate)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            activePcmEncoding = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    } else if (outIndex >= 0) {
                        emptyOutputStalls = 0
                        val outBuf = decoder.getOutputBuffer(outIndex)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (outBuf != null && bufferInfo.size > 0 && !isConfig) {
                            if (reusableChunk.size < bufferInfo.size) {
                                reusableChunk = ByteArray(bufferInfo.size)
                            }
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(reusableChunk, 0, bufferInfo.size)

                            val mono = downmixPcmBytesToMono(
                                pcmBytes = reusableChunk,
                                byteCount = bufferInfo.size,
                                channelCount = activeChannelCount,
                                pcmEncoding = activePcmEncoding
                            )
                            if (mono.isNotEmpty()) {
                                monoChunks.add(mono)
                                totalMonoFrames += mono.size.toLong()
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                            break
                        }
                        if (totalMonoFrames >= maxSourceFrames) {
                            break
                        }
                    }
                    outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            if (monoChunks.isEmpty()) return@withContext ShortArray(0)
            assembleAndResampleMonoChunks(
                monoChunks = monoChunks,
                sourceSampleRate = activeSampleRate,
                targetSampleRate = sampleRate
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            e.printStackTrace()
            ShortArray(0)
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Decodes an audio URI preserving true Stereo Left & Right channels for Vocal / Karaoke Mid-Side separation.
     */
    suspend fun decodeUriToStereoPcm(uri: Uri): StereoPcmData? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        val displayName = queryAudioDisplayName(uri)
        val rawExt = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 }
        val mimeHint = try { ctx.contentResolver.getType(uri) } catch (_: Exception) { null }
        val fileExt = when {
            rawExt != null -> ".$rawExt"
            mimeHint == "audio/wav" || mimeHint == "audio/x-wav" -> ".wav"
            mimeHint == "audio/mpeg" -> ".mp3"
            mimeHint == "audio/mp4" || mimeHint == "audio/x-m4a" -> ".m4a"
            mimeHint == "audio/aac" -> ".aac"
            mimeHint == "audio/ogg" -> ".ogg"
            mimeHint == "audio/flac" -> ".flac"
            mimeHint == "audio/webm" -> ".webm"
            mimeHint == "audio/3gpp" -> ".3gp"
            mimeHint == "video/mp4" -> ".mp4"
            else -> ".tmp"
        }
        val tempFile = File.createTempFile("stereo_audio_", fileExt, ctx.cacheDir)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            val directPath = if (uri.scheme == "file") uri.path?.takeIf { it.isNotBlank() } else null
            val directLocalFile = directPath?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
            if (directLocalFile != null) {
                tryDecodeRiffWavDirectStereo(directLocalFile, sampleRate)?.let { directStereo ->
                    if (directStereo.left.isNotEmpty()) return@withContext directStereo
                }
                extractor.setDataSource(directLocalFile.absolutePath)
            } else {
                var copiedToTemp = false
                val inputStream = try {
                    ctx.contentResolver.openInputStream(uri)
                } catch (_: Exception) {
                    null
                } ?: try {
                    ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
                } catch (_: Exception) {
                    null
                }

                if (inputStream != null) {
                    inputStream.use { input ->
                        FileOutputStream(tempFile).use { outputStream ->
                            val buf = ByteArray(16384)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                outputStream.write(buf, 0, read)
                            }
                            outputStream.flush()
                        }
                    }
                    copiedToTemp = tempFile.exists() && tempFile.length() >= 44L
                }

                if (copiedToTemp) {
                    tryDecodeRiffWavDirectStereo(tempFile, sampleRate)?.let { directStereo ->
                        if (directStereo.left.isNotEmpty()) return@withContext directStereo
                    }
                    extractor.setDataSource(tempFile.absolutePath)
                } else {
                    extractor.setDataSource(ctx, uri, null)
                }
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            var activeChannelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1
            var activeSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else sampleRate
            var activePcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else AudioFormat.ENCODING_PCM_16BIT

            decoder = try {
                MediaCodec.createDecoderByType(mime).also { it.configure(format, null, null, 0) }
            } catch (primaryErr: Throwable) {
                val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                if (codecName != null) {
                    MediaCodec.createByCodecName(codecName).also { it.configure(format, null, null, 0) }
                } else {
                    throw primaryErr
                }
            }
            decoder.start()

            val leftChunks = ArrayList<ShortArray>(256)
            val rightChunks = ArrayList<ShortArray>(256)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var totalFrames = 0L
            var emptyOutputStalls = 0
            val timeoutUs = 10000L
            var maxSourceFrames = computeMaxStereoSourceFramesBudget(activeSampleRate)
            var reusableChunk = ByteArray(16384)

            while (!outputEos && totalFrames < maxSourceFrames) {
                if (!inputEos) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuf = decoder.getInputBuffer(inIndex)
                        if (inputBuf != null) {
                            inputBuf.clear()
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                var outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                    emptyOutputStalls++
                    if (emptyOutputStalls > 600) break
                }
                while (outIndex >= 0 ||
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ||
                    outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                ) {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFmt = decoder.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            activeChannelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            activeSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                            maxSourceFrames = computeMaxStereoSourceFramesBudget(activeSampleRate)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            activePcmEncoding = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    } else if (outIndex >= 0) {
                        emptyOutputStalls = 0
                        val outBuf = decoder.getOutputBuffer(outIndex)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (outBuf != null && bufferInfo.size > 0 && !isConfig) {
                            if (reusableChunk.size < bufferInfo.size) {
                                reusableChunk = ByteArray(bufferInfo.size)
                            }
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(reusableChunk, 0, bufferInfo.size)

                            val (l, r) = splitPcmBytesToStereo(
                                pcmBytes = reusableChunk,
                                byteCount = bufferInfo.size,
                                channelCount = activeChannelCount,
                                pcmEncoding = activePcmEncoding
                            )
                            if (l.isNotEmpty()) {
                                leftChunks.add(l)
                                rightChunks.add(r)
                                totalFrames += l.size.toLong()
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                            break
                        }
                        if (totalFrames >= maxSourceFrames) break
                    }
                    outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            if (leftChunks.isEmpty()) return@withContext null
            val leftResampled = assembleAndResampleMonoChunks(leftChunks, activeSampleRate, sampleRate)
            leftChunks.clear()
            val rightResampled = assembleAndResampleMonoChunks(rightChunks, activeSampleRate, sampleRate)
            rightChunks.clear()
            val matchedLen = minOf(leftResampled.size, rightResampled.size)
            StereoPcmData(
                left = if (leftResampled.size == matchedLen) leftResampled else leftResampled.copyOf(matchedLen),
                right = if (rightResampled.size == matchedLen) rightResampled else rightResampled.copyOf(matchedLen),
                sampleRate = sampleRate,
                channelCount = activeChannelCount
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
            try { if (tempFile.exists()) tempFile.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Resolves the display filename from a content or file Uri
     */
    fun queryAudioDisplayName(uri: Uri): String? {
        val ctx = context
        var name: String? = null
        if (ctx != null) {
            try {
                val cursor = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            name = it.getString(nameIdx)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (name.isNullOrBlank()) {
            val segment = uri.lastPathSegment?.substringAfterLast('/')?.trim()
            if (!segment.isNullOrBlank()) {
                name = if (segment.contains(':') && !segment.endsWith(':')) {
                    segment.substringAfterLast(':')
                } else {
                    segment
                }
            }
        }
        return name?.takeIf { it.isNotBlank() }
    }

    /**
     * Echo / Delay effect
     */
    fun applyEcho(input: ShortArray, delayMs: Long, feedback: Float, mix: Float): ShortArray {
        if (input.isEmpty()) return input
        val delaySamples = ((delayMs.toFloat() / 1000f) * sampleRate).toInt().coerceAtLeast(1)
        val output = ShortArray(input.size)
        val delayBuffer = FloatArray(delaySamples)
        var writeIndex = 0

        val dry = 1.0f - (mix * 0.5f)
        val wet = mix

        for (i in input.indices) {
            val inSample = input[i].toFloat()
            val delayedSample = delayBuffer[writeIndex]

            val outSample = (inSample * dry) + (delayedSample * wet)
            output[i] = outSample.toInt().coerceIn(-32767, 32767).toShort()

            delayBuffer[writeIndex] = inSample + (delayedSample * feedback.coerceIn(0.1f, 0.85f))
            writeIndex = (writeIndex + 1) % delaySamples
        }
        return output
    }

    /**
     * Reverb (River) effect using multi-comb filters and allpass diffusor
     */
    fun applyReverb(input: ShortArray, roomSize: Float, damping: Float, mix: Float): ShortArray {
        if (input.isEmpty()) return input
        val combDelays = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val combBuffers = Array(combDelays.size) { FloatArray(combDelays[it]) }
        val combIndices = IntArray(combDelays.size)
        val combFilters = FloatArray(combDelays.size)

        val allPassDelay = 556
        val allPassBuffer = FloatArray(allPassDelay)
        var allPassIndex = 0

        val output = ShortArray(input.size)
        val feedback = roomSize.coerceIn(0.1f, 0.95f)
        val damp = damping.coerceIn(0.05f, 0.8f)

        for (i in input.indices) {
            val inSample = input[i].toFloat()
            var combSum = 0f

            for (c in combDelays.indices) {
                val delay = combDelays[c]
                val idx = combIndices[c]
                val delayed = combBuffers[c][idx]

                combFilters[c] = (delayed * (1f - damp)) + (combFilters[c] * damp)
                combBuffers[c][idx] = inSample + (combFilters[c] * feedback)
                combIndices[c] = (idx + 1) % delay
                combSum += delayed
            }

            // Allpass filter for smooth diffusion
            val allpassIn = combSum * 0.125f
            val allpassDelayed = allPassBuffer[allPassIndex]
            val allpassOut = -allpassIn + allpassDelayed
            allPassBuffer[allPassIndex] = allpassIn + (allpassDelayed * 0.5f)
            allPassIndex = (allPassIndex + 1) % allPassDelay

            val finalSample = (inSample * (1f - mix * 0.5f)) + (allpassOut * mix * 1.5f)
            output[i] = finalSample.toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Noise Gate: Silences / attenuates signal below threshold
     */
    fun applyNoiseGate(input: ShortArray, threshold: Float): ShortArray {
        if (input.isEmpty()) return input
        val output = ShortArray(input.size)
        val threshInt = (threshold * 32767f).toInt().coerceAtLeast(300)
        val window = 220 // ~5ms
        var envelope = 0f

        for (i in input.indices) {
            val sampleAbs = abs(input[i].toInt()).toFloat()
            envelope = if (sampleAbs > envelope) {
                (envelope * 0.9f) + (sampleAbs * 0.1f) // fast attack
            } else {
                (envelope * 0.999f) + (sampleAbs * 0.001f) // gentle release
            }

            val gain = if (envelope < threshInt) {
                val ratio = (envelope / threshInt).coerceIn(0f, 1f)
                ratio * ratio * 0.2f // soft knee attenuation
            } else {
                1.0f
            }

            output[i] = (input[i] * gain).toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Smooth Natural Noise Reduction:
     * Eliminates hiss, air conditioning, and electrical noise without robotic artifacts or crackles.
     * Uses smooth envelope tracking with attack and release smoothing and soft-knee attenuation.
     */
    fun applyNoiseReduction(input: ShortArray, intensity: Float): ShortArray {
        if (input.isEmpty()) return input
        val output = ShortArray(input.size)
        val strength = intensity.coerceIn(0.1f, 1.0f)

        // Soft noise floor calibrated between 600 (Low) and 1800 (High)
        val noiseFloor = 600f + (1400f * strength)
        val maxAttenuation = (0.35f + 0.45f * strength) // Keeps natural vocal timbre intact

        var envelope = 0f
        val attackAlpha = 0.08f   // fast response to voice onset (no clipping speech)
        val releaseAlpha = 0.002f // smooth decay so background doesn't pump or crackle

        // Gentle sub-bass rumble filter (~70Hz)
        var prevIn = 0f
        var prevOut = 0f
        val hpAlpha = 0.97f

        for (i in input.indices) {
            val curr = input[i].toFloat()
            // 1. Rumble removal
            val hp = hpAlpha * (prevOut + curr - prevIn)
            prevIn = curr
            prevOut = hp

            val absVal = abs(hp)
            // 2. Continuous envelope
            envelope = if (absVal > envelope) {
                envelope * (1f - attackAlpha) + absVal * attackAlpha
            } else {
                envelope * (1f - releaseAlpha) + absVal * releaseAlpha
            }

            // 3. Smooth soft-knee gain calculation (never abrupt 0/1 switching)
            val gain = if (envelope < noiseFloor) {
                val ratio = (envelope / noiseFloor).coerceIn(0f, 1f)
                // Smooth polynomial curve (hermite interpolation)
                val smoothRatio = ratio * ratio * (3f - 2f * ratio)
                (1f - maxAttenuation) + (maxAttenuation * smoothRatio)
            } else {
                1.0f
            }

            val filtered = hp * gain
            output[i] = filtered.toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Bass and Treble Booster
     */
    fun applyBassTreble(input: ShortArray, bassBoost: Float, trebleBoost: Float): ShortArray {
        if (bassBoost <= 0f && trebleBoost <= 0f) return input
        val output = ShortArray(input.size)
        var lowPass = 0f

        for (i in input.indices) {
            val current = input[i].toFloat()
            // Simple 1-pole lowpass for bass extraction (~250Hz)
            lowPass += 0.04f * (current - lowPass)
            val highPass = current - lowPass

            val boosted = current + (lowPass * bassBoost * 1.5f) + (highPass * trebleBoost * 1.5f)
            output[i] = boosted.toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Speed Changer via Linear Resampling
     */
    fun applySpeed(input: ShortArray, speed: Float): ShortArray {
        if (input.isEmpty() || abs(speed - 1.0f) < 0.05f) return input
        val newLength = (input.size / speed).toInt().coerceAtLeast(1)
        val output = ShortArray(newLength)

        for (i in 0 until newLength) {
            val origPos = i * speed
            val index0 = origPos.toInt().coerceIn(0, input.size - 1)
            val index1 = (index0 + 1).coerceIn(0, input.size - 1)
            val frac = origPos - index0

            val sample = (input[index0] * (1.0 - frac) + input[index1] * frac).toInt()
            output[i] = sample.coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Reverse audio samples
     */
    fun applyReverse(input: ShortArray): ShortArray {
        val output = ShortArray(input.size)
        val n = input.size
        for (i in 0 until n) {
            output[i] = input[n - 1 - i]
        }
        return output
    }

    /**
     * Full DSP processing pipeline on PCM data
     */
    fun processPcm(rawPcm: ShortArray, settings: AudioEditSettings): ShortArray {
        if (rawPcm.isEmpty()) return rawPcm
        var pcm = rawPcm

        // 1. Noise Gate
        if (settings.noiseGateEnabled) {
            pcm = applyNoiseGate(pcm, settings.noiseGateThreshold)
        }

        // 2. Noise Reduction
        if (settings.noiseReductionEnabled) {
            pcm = applyNoiseReduction(pcm, settings.noiseReductionIntensity)
        }

        // 3. Bass & Treble booster
        if (settings.bassBoost > 0f || settings.trebleBoost > 0f) {
            pcm = applyBassTreble(pcm, settings.bassBoost, settings.trebleBoost)
        }

        // 4. Equalizer preset
        if (settings.eqPreset != EqPreset.NORMAL) {
            val eqAdjusted = ShortArray(pcm.size)
            for (i in pcm.indices) {
                var s = pcm[i].toFloat()
                when (settings.eqPreset) {
                    EqPreset.VOCAL_BOOST -> if (i % 2 == 0) s *= 1.25f
                    EqPreset.BASS_BOOST -> if (i % 4 == 0) s *= 1.35f
                    EqPreset.TREBLE_BOOST -> if (i % 3 == 0) s *= 1.30f
                    EqPreset.RADIO_LOFI -> {
                        s = (s * 0.7f) + (sin(i.toDouble() * 0.05).toFloat() * 1200f)
                    }
                    EqPreset.NORMAL -> {}
                }
                eqAdjusted[i] = s.toInt().coerceIn(-32767, 32767).toShort()
            }
            pcm = eqAdjusted
        }

        // 5. Echo (Eco)
        if (settings.echoEnabled) {
            pcm = applyEcho(pcm, settings.echoDelayMs, settings.echoFeedback, settings.echoMix)
        }

        // 6. Reverb (River)
        if (settings.reverbEnabled) {
            pcm = applyReverb(pcm, settings.reverbRoomSize, settings.reverbDamping, settings.reverbMix)
        }

        // 7. Speed multiplier
        if (abs(settings.speedMultiplier - 1.0f) > 0.05f) {
            pcm = applySpeed(pcm, settings.speedMultiplier)
        }

        // 8. Reverse
        if (settings.isReversed) {
            pcm = applyReverse(pcm)
        }

        // 9. Studio Pro FX Rack (Flanger, Chorus, Distortion, Compressor & Limiter, 7-Band Graphic EQ, Pitch Shift, Phase Invert)
        pcm = StudioAudioProcessor.applyStudioRack(pcm, settings.studioFx)

        return pcm
    }

    /**
     * Renders, edits, trims, applies effects, mixes all tracks, and exports into ONE single .wav file
     */
    suspend fun exportSingleAudioFile(
        mainPcm: ShortArray,
        bgTrackConfig: AudioTrackConfig?,
        bgPcm: ShortArray?,
        settings: AudioEditSettings,
        outputFileName: String,
        format: String = "WAV",
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.05f)

        if (mainPcm.isEmpty()) {
            throw IllegalStateException("Cannot export an empty audio canvas. Paste, mix, or record audio first.")
        }

        // 1. Calculate trim range safely using Double precision for multi-minute tracks
        var startSample = ((settings.trimStartMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(0, mainPcm.size)
        var endSample = ((settings.trimEndMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(startSample, mainPcm.size)
        if (endSample <= startSample) {
            startSample = 0
            endSample = mainPcm.size
        }
        val trimmedLength = endSample - startSample

        val trimmedMain = ShortArray(trimmedLength)
        System.arraycopy(mainPcm, startSample, trimmedMain, 0, trimmedLength)
        onProgress(0.2f)

        // 2. Apply complete DSP processing (Noise Gate, Reduction, EQ, Echo, Reverb, Speed, Reverse)
        val processedMain = processPcm(trimmedMain, settings)
        onProgress(0.5f)

        // 3. Prepare background music if enabled
        val finalLength = processedMain.size
        val mixedSamples = ShortArray(finalLength)
        val bgVolume = if (bgTrackConfig != null && !bgTrackConfig.isMuted) bgTrackConfig.volume else 0f
        val hasBg = bgVolume > 0f && bgPcm != null && bgPcm.isNotEmpty()

        val fadeInSamples = ((settings.fadeInMs.toFloat() / 1000f) * sampleRate).toInt().coerceIn(0, finalLength / 2)
        val fadeOutSamples = ((settings.fadeOutMs.toFloat() / 1000f) * sampleRate).toInt().coerceIn(0, finalLength / 2)
        val fadeOutStart = finalLength - fadeOutSamples

        onProgress(0.7f)

        for (i in 0 until finalLength) {
            var sample = processedMain[i].toFloat() * settings.masterVolume

            // Fade in / Fade out
            if (fadeInSamples > 0 && i < fadeInSamples) {
                sample *= (i.toFloat() / fadeInSamples)
            } else if (fadeOutSamples > 0 && i >= fadeOutStart) {
                sample *= ((finalLength - i).toFloat() / fadeOutSamples)
            }

            // Mix background music
            var bgSample = 0f
            if (hasBg && bgPcm != null) {
                val bgIndex = if (bgTrackConfig?.loop == true) i % bgPcm.size else i
                if (bgIndex < bgPcm.size) {
                    bgSample = bgPcm[bgIndex].toFloat() * bgVolume
                }
            }

            val mixed = (sample + bgSample).toInt().coerceIn(-32767, 32767)
            mixedSamples[i] = mixed.toShort()
        }

        onProgress(0.85f)

        // 4. Write audio file directly to Internal Storage / Music / Audio Studio
        val extension = when (format.uppercase()) {
            "MP3" -> "mp3"
            "FLAC" -> "flac"
            "M4A" -> "m4a"
            "AAC" -> "aac"
            else -> "wav"
        }
        val cleanBase = com.example.data.StudioPreferences.extractBaseFileName(outputFileName)
            .ifBlank { "AudioStudio_${System.currentTimeMillis()}" }
        val cleanName = "$cleanBase.$extension"

        val finalDestFile = if (context != null) {
            val tempFile = File(context.cacheDir, "export_tmp_${System.currentTimeMillis()}.wav")
            try {
                writeWavFile(tempFile, mixedSamples, sampleRate, channels)
                com.example.storage.StorageExportManager.saveAudioFile(
                    context = context,
                    sourceFile = tempFile,
                    targetFileName = cleanName,
                    mimeType = com.example.storage.StorageExportManager.getAudioMimeType(cleanName)
                )
            } finally {
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}
            }
        } else {
            val musicPublic = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), com.example.storage.StorageExportManager.AUDIO_SUBDIRECTORY)
            if (!musicPublic.exists()) musicPublic.mkdirs()
            File(musicPublic, cleanName).also { writeWavFile(it, mixedSamples, sampleRate, channels) }
        }
        onProgress(1.0f)

        finalDestFile
    }

    /**
     * Writes ShortArray PCM samples into standard RIFF WAV format
     */
    fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val totalAudioLen = samples.size.toLong() * 2L
        val totalDataLen = totalAudioLen + 36L
        val byteRate = (16L * sampleRate * channels) / 8L

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate.toLong() and 0xff).toByte()
        header[25] = ((sampleRate.toLong() shr 8) and 0xff).toByte()
        header[26] = ((sampleRate.toLong() shr 16) and 0xff).toByte()
        header[27] = ((sampleRate.toLong() shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val chunkSamples = 16384
            val chunkBytes = ByteArray(chunkSamples * 2)
            val chunkBuf = ByteBuffer.wrap(chunkBytes).order(ByteOrder.LITTLE_ENDIAN)
            var idx = 0
            while (idx < samples.size) {
                val count = minOf(chunkSamples, samples.size - idx)
                chunkBuf.clear()
                for (k in 0 until count) {
                    chunkBuf.putShort(samples[idx + k])
                }
                fos.write(chunkBytes, 0, count * 2)
                idx += count
            }
            fos.flush()
        }
    }
}
