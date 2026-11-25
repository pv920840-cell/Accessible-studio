package com.example.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stem Separation Modes matching Music Rebalance dropdown options.
 */
enum class StemSeparationMode(
    val stemCount: Int,
    val dropdownLabel: String,
    val stemNames: List<String>
) {
    TWO_STEMS(
        stemCount = 2,
        dropdownLabel = "2 Stems",
        stemNames = listOf("Vocals", "Instruments")
    ),
    THREE_STEMS(
        stemCount = 3,
        dropdownLabel = "3 Stems (Vocals, Backing Vocals, Instruments)",
        stemNames = listOf("Vocals", "Backing Vocals", "Instruments")
    ),
    FOUR_STEMS(
        stemCount = 4,
        dropdownLabel = "4 Stems (Vocal, Other, Bass, Piano)",
        stemNames = listOf("Vocal", "Other", "Bass", "Piano")
    ),
    FIVE_STEMS(
        stemCount = 5,
        dropdownLabel = "5 Stems (Guitar and other elements)",
        stemNames = listOf("Vocals", "Guitar", "Bass", "Drums", "Other")
    ),
    SIX_STEMS(
        stemCount = 6,
        dropdownLabel = "6 Stems (Drums and other elements)",
        stemNames = listOf("Vocals", "Drums", "Bass", "Piano", "Guitar", "Other")
    )
}

/**
 * Pure-Kotlin iZotope RX 12-inspired Music Rebalance & Stem Separation DSP engine.
 *
 * Architecture:
 * - Bounded sliding-window STFT (2048-pt FFT, 512-pt hop, sqrt-Hann analysis/synthesis)
 *   using a fixed 13-frame spectrogram ring buffer + 4096-sample OLA output ring buffer.
 * - Constant ~500 KB scratch memory regardless of song duration (crash-free & OOM-free on
 *   multi-minute / hour-long tracks).
 * - Multi-feature spectral decomposition: temporal/spectral Harmonic-Percussive Source
 *   Separation (HPSS), local harmonic formant prominence, 3-bin Wiener mask smoothing, and
 *   phase-coherent complex synthesis for Vocals, Backing Vocals, Bass, Drums/Percussion,
 *   Piano, Guitar, and Other instruments.
 */
object MusicRebalanceProcessor {

    private const val FFT_SIZE = 2048
    private const val HOP_SIZE = 512
    private const val HALF_BINS = FFT_SIZE / 2 + 1
    private const val TIME_HALF_WIN = 6
    private const val RING_FRAMES = TIME_HALF_WIN * 2 + 1 // 13 frames (~150 ms temporal context)
    private const val FREQ_HALF_WIN = 8
    private const val OUT_RING_SIZE = 4096 // power of 2 >= FFT_SIZE
    private const val OUT_RING_MASK = OUT_RING_SIZE - 1
    private const val STREAM_FLUSH_BYTES = 16384
    private const val EPS = 1e-9f

    private val sqrtHannWindow: FloatArray by lazy {
        FloatArray(FFT_SIZE) { i ->
            sqrt(0.5 * (1.0 - cos(2.0 * PI * i / FFT_SIZE))).toFloat()
        }
    }

    // Precomputed FFT radix-2 bit-reversal & twiddle tables for 2048-pt FFT
    private val bitReverseTable: IntArray by lazy {
        IntArray(FFT_SIZE).also { table ->
            var j = 0
            for (i in 1 until FFT_SIZE) {
                var bit = FFT_SIZE shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                table[i] = j
            }
        }
    }

    private val cosFwd: FloatArray by lazy {
        FloatArray(FFT_SIZE / 2) { k -> cos(-2.0 * PI * k / FFT_SIZE).toFloat() }
    }
    private val sinFwd: FloatArray by lazy {
        FloatArray(FFT_SIZE / 2) { k -> sin(-2.0 * PI * k / FFT_SIZE).toFloat() }
    }

    /**
     * Adjusts balance of Vocals, Bass, Percussion/Drums, and Other instruments
     * in a single bounded-memory streaming pass.
     */
    fun rebalanceMix(
        samples: ShortArray,
        sampleRate: Int,
        vocalsGain: Float = 1.0f,
        bassGain: Float = 1.0f,
        percussionGain: Float = 1.0f,
        otherGain: Float = 1.0f,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): ShortArray {
        if (samples.isEmpty()) {
            onProgress(1f)
            return ShortArray(0)
        }
        if (
            abs(vocalsGain - 1f) < 0.01f &&
            abs(bassGain - 1f) < 0.01f &&
            abs(percussionGain - 1f) < 0.01f &&
            abs(otherGain - 1f) < 0.01f
        ) {
            onProgress(1f)
            return samples.copyOf()
        }

        val result = ShortArray(samples.size)
        val outRing = FloatArray(OUT_RING_SIZE)
        val normRing = FloatArray(OUT_RING_SIZE)
        val synReal = FloatArray(FFT_SIZE)
        val synImag = FloatArray(FFT_SIZE)
        val rawGain = FloatArray(HALF_BINS)
        val smoothGain = FloatArray(HALF_BINS)
        val window = sqrtHannWindow
        var writtenUpTo = 0

        streamStftFrames(
            samples = samples,
            sampleRate = sampleRate,
            isCancelled = isCancelled,
            onProgress = onProgress
        ) { frameIndex, specReal, specImag, vMask, bMask, pMask, oMask ->
            for (k in 0 until HALF_BINS) {
                rawGain[k] = (
                    vMask[k] * vocalsGain +
                        bMask[k] * bassGain +
                        pMask[k] * percussionGain +
                        oMask[k] * otherGain
                    ).coerceIn(0f, 2.5f)
            }
            // 3-bin spectral smoothing prevents metallic musical noise while preserving transients
            smoothSpectrum3(rawGain, smoothGain)

            for (k in 0 until HALF_BINS) {
                val g = smoothGain[k]
                synReal[k] = specReal[k] * g
                synImag[k] = specImag[k] * g
                if (k in 1 until FFT_SIZE / 2) {
                    val mirror = FFT_SIZE - k
                    synReal[mirror] = specReal[mirror] * g
                    synImag[mirror] = specImag[mirror] * g
                }
            }
            fftRadix2(synReal, synImag, inverse = true)

            val offset = frameIndex * HOP_SIZE
            while (writtenUpTo < offset && writtenUpTo < samples.size) {
                val slot = writtenUpTo and OUT_RING_MASK
                val norm = normRing[slot]
                val valF = if (norm > 1e-6f) outRing[slot] / norm else samples[writtenUpTo].toFloat()
                result[writtenUpTo] = softClipSample(valF)
                outRing[slot] = 0f
                normRing[slot] = 0f
                writtenUpTo++
            }
            for (n in 0 until FFT_SIZE) {
                val idx = offset + n
                if (idx >= samples.size + FFT_SIZE) break
                val slot = idx and OUT_RING_MASK
                val w = window[n]
                outRing[slot] += synReal[n] * w
                normRing[slot] += w * w
            }
        }

        while (writtenUpTo < samples.size) {
            val slot = writtenUpTo and OUT_RING_MASK
            val norm = normRing[slot]
            val valF = if (norm > 1e-6f) outRing[slot] / norm else samples[writtenUpTo].toFloat()
            result[writtenUpTo] = softClipSample(valF)
            outRing[slot] = 0f
            normRing[slot] = 0f
            writtenUpTo++
        }
        onProgress(1f)
        return result
    }

    /**
     * Separates [samples] into distinct in-memory stem buffers (for unit tests and short clips).
     */
    fun separateStems(
        samples: ShortArray,
        sampleRate: Int,
        mode: StemSeparationMode,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): Map<String, ShortArray> {
        val stemNames = mode.stemNames
        if (samples.isEmpty()) {
            onProgress(1f)
            return stemNames.associateWith { ShortArray(0) }
        }

        val stemCount = stemNames.size
        val stemBuffers = Array(stemCount) { ShortArray(samples.size) }
        val outRings = Array(stemCount) { FloatArray(OUT_RING_SIZE) }
        val normRing = FloatArray(OUT_RING_SIZE)
        val synReal = FloatArray(FFT_SIZE)
        val synImag = FloatArray(FFT_SIZE)
        val stemWeights = Array(stemCount) { FloatArray(HALF_BINS) }
        val smoothedWeights = Array(stemCount) { FloatArray(HALF_BINS) }
        val window = sqrtHannWindow
        val freqPerBin = sampleRate.toFloat() / FFT_SIZE
        var writtenUpTo = 0

        streamStftFrames(
            samples = samples,
            sampleRate = sampleRate,
            isCancelled = isCancelled,
            onProgress = onProgress
        ) { frameIndex, specReal, specImag, vMask, bMask, pMask, oMask ->
            computeStemWienerWeights(
                mode = mode,
                freqPerBin = freqPerBin,
                vMask = vMask,
                bMask = bMask,
                pMask = pMask,
                oMask = oMask,
                rawWeights = stemWeights,
                smoothedWeights = smoothedWeights
            )

            val offset = frameIndex * HOP_SIZE
            while (writtenUpTo < offset && writtenUpTo < samples.size) {
                val slot = writtenUpTo and OUT_RING_MASK
                val norm = normRing[slot]
                for (sIdx in 0 until stemCount) {
                    val v = if (norm > 1e-6f) outRings[sIdx][slot] / norm else 0f
                    stemBuffers[sIdx][writtenUpTo] = softClipSample(v)
                    outRings[sIdx][slot] = 0f
                }
                normRing[slot] = 0f
                writtenUpTo++
            }

            for (sIdx in 0 until stemCount) {
                val wBins = smoothedWeights[sIdx]
                for (k in 0 until HALF_BINS) {
                    val gain = wBins[k]
                    synReal[k] = specReal[k] * gain
                    synImag[k] = specImag[k] * gain
                    if (k in 1 until FFT_SIZE / 2) {
                        val mirror = FFT_SIZE - k
                        synReal[mirror] = specReal[mirror] * gain
                        synImag[mirror] = specImag[mirror] * gain
                    }
                }
                fftRadix2(synReal, synImag, inverse = true)
                val targetRing = outRings[sIdx]
                for (n in 0 until FFT_SIZE) {
                    val slot = (offset + n) and OUT_RING_MASK
                    targetRing[slot] += synReal[n] * window[n]
                }
            }
            for (n in 0 until FFT_SIZE) {
                val slot = (offset + n) and OUT_RING_MASK
                val w = window[n]
                normRing[slot] += w * w
            }
        }

        while (writtenUpTo < samples.size) {
            val slot = writtenUpTo and OUT_RING_MASK
            val norm = normRing[slot]
            for (sIdx in 0 until stemCount) {
                val v = if (norm > 1e-6f) outRings[sIdx][slot] / norm else 0f
                stemBuffers[sIdx][writtenUpTo] = softClipSample(v)
                outRings[sIdx][slot] = 0f
            }
            normRing[slot] = 0f
            writtenUpTo++
        }

        val outMap = LinkedHashMap<String, ShortArray>(stemCount)
        for (sIdx in 0 until stemCount) {
            outMap[stemNames[sIdx]] = stemBuffers[sIdx]
        }
        onProgress(1f)
        return outMap
    }

    /**
     * Crash-free, OOM-free streaming stem separator for long/large song files.
     * Writes each separated stem directly into 16-bit PCM RIFF WAV files in [outputDir]
     * using ~500 KB constant scratch heap regardless of song duration.
     */
    fun separateStemsStreamingToFiles(
        samples: ShortArray,
        sampleRate: Int,
        mode: StemSeparationMode,
        outputDir: File,
        baseName: String,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): Map<String, File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val safeSr = sampleRate.coerceIn(8000, 96000)
        val stemNames = mode.stemNames
        val stemCount = stemNames.size
        val cleanBase = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Track" }

        val files = Array(stemCount) { idx ->
            val safeStem = stemNames[idx].replace(" ", "_")
            File(outputDir, "${cleanBase}_${safeStem}_${mode.stemCount}stems.wav")
        }

        if (samples.isEmpty()) {
            val outMap = LinkedHashMap<String, File>(stemCount)
            for (sIdx in 0 until stemCount) {
                FileOutputStream(files[sIdx]).use { fos ->
                    fos.write(buildWavHeader(0L, safeSr, 1))
                    fos.flush()
                }
                outMap[stemNames[sIdx]] = files[sIdx]
            }
            onProgress(1f)
            return outMap
        }

        val streams = Array(stemCount) { idx ->
            BufferedOutputStream(FileOutputStream(files[idx]), STREAM_FLUSH_BYTES).also {
                it.write(buildWavHeader(0L, safeSr, 1))
            }
        }
        val chunkByteArrays = Array(stemCount) { ByteArray(STREAM_FLUSH_BYTES) }
        var chunkByteCursor = 0

        fun flushSampleToDisk(sIdx: Int, sample: Short, offsetInChunk: Int) {
            val buf = chunkByteArrays[sIdx]
            val v = sample.toInt()
            buf[offsetInChunk] = (v and 0xFF).toByte()
            buf[offsetInChunk + 1] = ((v ushr 8) and 0xFF).toByte()
        }

        fun commitChunkBytes() {
            if (chunkByteCursor <= 0) return
            for (sIdx in 0 until stemCount) {
                streams[sIdx].write(chunkByteArrays[sIdx], 0, chunkByteCursor)
            }
            chunkByteCursor = 0
        }

        try {
            val outRings = Array(stemCount) { FloatArray(OUT_RING_SIZE) }
            val normRing = FloatArray(OUT_RING_SIZE)
            val synReal = FloatArray(FFT_SIZE)
            val synImag = FloatArray(FFT_SIZE)
            val stemWeights = Array(stemCount) { FloatArray(HALF_BINS) }
            val smoothedWeights = Array(stemCount) { FloatArray(HALF_BINS) }
            val window = sqrtHannWindow
            val freqPerBin = safeSr.toFloat() / FFT_SIZE
            var writtenUpTo = 0

            streamStftFrames(
                samples = samples,
                sampleRate = safeSr,
                isCancelled = isCancelled,
                onProgress = onProgress
            ) { frameIndex, specReal, specImag, vMask, bMask, pMask, oMask ->
                computeStemWienerWeights(
                    mode = mode,
                    freqPerBin = freqPerBin,
                    vMask = vMask,
                    bMask = bMask,
                    pMask = pMask,
                    oMask = oMask,
                    rawWeights = stemWeights,
                    smoothedWeights = smoothedWeights
                )

                val offset = frameIndex * HOP_SIZE
                while (writtenUpTo < offset && writtenUpTo < samples.size) {
                    val slot = writtenUpTo and OUT_RING_MASK
                    val norm = normRing[slot]
                    for (sIdx in 0 until stemCount) {
                        val v = if (norm > 1e-6f) outRings[sIdx][slot] / norm else 0f
                        flushSampleToDisk(sIdx, softClipSample(v), chunkByteCursor)
                        outRings[sIdx][slot] = 0f
                    }
                    normRing[slot] = 0f
                    writtenUpTo++
                    chunkByteCursor += 2
                    if (chunkByteCursor >= STREAM_FLUSH_BYTES) {
                        commitChunkBytes()
                    }
                }

                for (sIdx in 0 until stemCount) {
                    val wBins = smoothedWeights[sIdx]
                    for (k in 0 until HALF_BINS) {
                        val gain = wBins[k]
                        synReal[k] = specReal[k] * gain
                        synImag[k] = specImag[k] * gain
                        if (k in 1 until FFT_SIZE / 2) {
                            val mirror = FFT_SIZE - k
                            synReal[mirror] = specReal[mirror] * gain
                            synImag[mirror] = specImag[mirror] * gain
                        }
                    }
                    fftRadix2(synReal, synImag, inverse = true)
                    val targetRing = outRings[sIdx]
                    for (n in 0 until FFT_SIZE) {
                        val slot = (offset + n) and OUT_RING_MASK
                        targetRing[slot] += synReal[n] * window[n]
                    }
                }
                for (n in 0 until FFT_SIZE) {
                    val slot = (offset + n) and OUT_RING_MASK
                    val w = window[n]
                    normRing[slot] += w * w
                }
            }

            while (writtenUpTo < samples.size) {
                val slot = writtenUpTo and OUT_RING_MASK
                val norm = normRing[slot]
                for (sIdx in 0 until stemCount) {
                    val v = if (norm > 1e-6f) outRings[sIdx][slot] / norm else 0f
                    flushSampleToDisk(sIdx, softClipSample(v), chunkByteCursor)
                    outRings[sIdx][slot] = 0f
                }
                normRing[slot] = 0f
                writtenUpTo++
                chunkByteCursor += 2
                if (chunkByteCursor >= STREAM_FLUSH_BYTES) {
                    commitChunkBytes()
                }
            }
            commitChunkBytes()

            for (sIdx in 0 until stemCount) {
                streams[sIdx].flush()
                streams[sIdx].close()
            }

            // Patch exact RIFF / data sizes on disk
            val totalAudioBytes = samples.size.toLong() * 2L
            for (sIdx in 0 until stemCount) {
                patchWavHeaderSizes(files[sIdx], totalAudioBytes)
            }

            val outMap = LinkedHashMap<String, File>(stemCount)
            for (sIdx in 0 until stemCount) {
                outMap[stemNames[sIdx]] = files[sIdx]
            }
            onProgress(1f)
            return outMap
        } catch (t: Throwable) {
            streams.forEach { stream ->
                try { stream.close() } catch (_: Throwable) {}
            }
            files.forEach { f ->
                try { if (f.exists()) f.delete() } catch (_: Throwable) {}
            }
            throw t
        }
    }

    /**
     * Streams STFT frames through a fixed 13-frame spectrogram ring buffer, computing
     * phase-coherent RX-12-inspired harmonic/percussive/vocal/bass/instrument masks
     * and emitting each center frame for overlap-add synthesis.
     */
    private fun streamStftFrames(
        samples: ShortArray,
        sampleRate: Int,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
        onFrameReady: (
            frameIndex: Int,
            specReal: FloatArray,
            specImag: FloatArray,
            vocalMask: FloatArray,
            bassMask: FloatArray,
            percMask: FloatArray,
            otherMask: FloatArray
        ) -> Unit
    ) {
        val window = sqrtHannWindow
        val totalFrames = max(1, (samples.size + HOP_SIZE - 1) / HOP_SIZE)
        val freqPerBin = sampleRate.toFloat() / FFT_SIZE

        val ringReal = Array(RING_FRAMES) { FloatArray(FFT_SIZE) }
        val ringImag = Array(RING_FRAMES) { FloatArray(FFT_SIZE) }
        val ringMag = Array(RING_FRAMES) { FloatArray(HALF_BINS) }

        val timeScratch = FloatArray(RING_FRAMES)
        val freqScratch = FloatArray(FREQ_HALF_WIN * 2 + 1)
        val vocalBandArr = FloatArray(HALF_BINS) { k -> computeVocalBandWeight(k * freqPerBin) }
        val bassBandArr = FloatArray(HALF_BINS) { k -> computeBassBandWeight(k * freqPerBin) }

        val vocalMask = FloatArray(HALF_BINS)
        val bassMask = FloatArray(HALF_BINS)
        val percMask = FloatArray(HALF_BINS)
        val otherMask = FloatArray(HALF_BINS)

        fun pushAnalysisFrame(f: Int) {
            val slot = f % RING_FRAMES
            val r = ringReal[slot]
            val im = ringImag[slot]
            val mag = ringMag[slot]
            val offset = f * HOP_SIZE
            for (n in 0 until FFT_SIZE) {
                val s = if (offset + n < samples.size) samples[offset + n].toFloat() else 0f
                r[n] = s * window[n]
                im[n] = 0f
            }
            fftRadix2(r, im, inverse = false)
            for (k in 0 until HALF_BINS) {
                mag[k] = hypot(r[k], im[k])
            }
        }

        fun evaluateAndEmitCenterFrame(centerF: Int) {
            if (isCancelled()) throw CancellationException("Stem processing cancelled")
            val centerSlot = centerF % RING_FRAMES
            val row = ringMag[centerSlot]
            val minF = (centerF - TIME_HALF_WIN).coerceAtLeast(0)
            val maxF = (centerF + TIME_HALF_WIN).coerceAtMost(totalFrames - 1)

            for (k in 0 until HALF_BINS) {
                // Temporal median across +-TIME_HALF_WIN -> sustained harmonic content
                var tCount = 0
                for (df in -TIME_HALF_WIN..TIME_HALF_WIN) {
                    val idx = (centerF + df).coerceIn(minF, maxF)
                    timeScratch[tCount++] = ringMag[idx % RING_FRAMES][k]
                }
                timeScratch.sort(0, tCount)
                val harmonic = timeScratch[tCount / 2]

                // Frequency median across +-FREQ_HALF_WIN -> broadband transient/percussive energy
                var fCount = 0
                for (dk in -FREQ_HALF_WIN..FREQ_HALF_WIN) {
                    val idx = (k + dk).coerceIn(0, HALF_BINS - 1)
                    freqScratch[fCount++] = row[idx]
                }
                freqScratch.sort(0, fCount)
                val percussive = freqScratch[fCount / 2]

                val hPow = harmonic * harmonic
                val pPow = percussive * percussive
                val totalPow = hPow + pPow + EPS
                val hRatio = hPow / totalPow
                val pRatio = pPow / totalPow

                // Harmonic ridge prominence relative to broadband background helps isolate vocals cleanly
                val ridgeProminence = ((row[k] / (percussive + EPS)) - 1.0f).coerceIn(0f, 3.0f) / 3.0f
                val vocalBoost = (0.65f + 0.35f * ridgeProminence).coerceIn(0.4f, 1.0f)

                val vRaw = hRatio * vocalBandArr[k] * vocalBoost
                val bRaw = (0.72f * hRatio + 0.28f * pRatio) * bassBandArr[k]
                val pRaw = pRatio * (1f - bassBandArr[k] * 0.7f)
                val oRaw = (1f - vRaw - bRaw - pRaw).coerceAtLeast(0.04f)

                val sum = vRaw + bRaw + pRaw + oRaw + EPS
                vocalMask[k] = vRaw / sum
                bassMask[k] = bRaw / sum
                percMask[k] = pRaw / sum
                otherMask[k] = oRaw / sum
            }

            onFrameReady(
                centerF,
                ringReal[centerSlot],
                ringImag[centerSlot],
                vocalMask,
                bassMask,
                percMask,
                otherMask
            )

            if (centerF % 16 == 0 || centerF == totalFrames - 1) {
                onProgress((centerF + 1).toFloat() / totalFrames.toFloat())
            }
        }

        // Prime + slide through all analysis frames
        for (f in 0 until totalFrames) {
            if (f % 16 == 0 && isCancelled()) throw CancellationException("Stem processing cancelled")
            pushAnalysisFrame(f)
            val centerF = f - TIME_HALF_WIN
            if (centerF >= 0) {
                evaluateAndEmitCenterFrame(centerF)
            }
        }
        // Flush remaining frames at the tail of the song
        val firstRemaining = (totalFrames - TIME_HALF_WIN).coerceAtLeast(0)
        for (centerF in firstRemaining until totalFrames) {
            evaluateAndEmitCenterFrame(centerF)
        }
    }

    private fun computeStemWienerWeights(
        mode: StemSeparationMode,
        freqPerBin: Float,
        vMask: FloatArray,
        bMask: FloatArray,
        pMask: FloatArray,
        oMask: FloatArray,
        rawWeights: Array<FloatArray>,
        smoothedWeights: Array<FloatArray>
    ) {
        val stemCount = mode.stemCount
        for (k in 0 until HALF_BINS) {
            val freq = k * freqPerBin
            val v = vMask[k]
            val b = bMask[k]
            val p = pMask[k]
            val o = oMask[k]

            when (mode) {
                StemSeparationMode.TWO_STEMS -> {
                    rawWeights[0][k] = v
                    rawWeights[1][k] = b + p + o
                }
                StemSeparationMode.THREE_STEMS -> {
                    val leadVoiceWeight = if (freq in 240f..3600f) 0.78f else 0.52f
                    rawWeights[0][k] = v * leadVoiceWeight
                    rawWeights[1][k] = v * (1f - leadVoiceWeight) + o * 0.12f
                    rawWeights[2][k] = b + p + o * 0.88f
                }
                StemSeparationMode.FOUR_STEMS -> {
                    val pianoTone = if (freq in 180f..4200f) o * 0.56f else o * 0.14f
                    val otherRem = (p + o - pianoTone).coerceAtLeast(0f)
                    rawWeights[0][k] = v
                    rawWeights[1][k] = otherRem
                    rawWeights[2][k] = b
                    rawWeights[3][k] = pianoTone
                }
                StemSeparationMode.FIVE_STEMS -> {
                    val guitarWeight = if (freq in 210f..3200f) o * 0.64f else o * 0.12f
                    val otherRem = (o - guitarWeight).coerceAtLeast(0f)
                    rawWeights[0][k] = v
                    rawWeights[1][k] = guitarWeight
                    rawWeights[2][k] = b
                    rawWeights[3][k] = p
                    rawWeights[4][k] = otherRem
                }
                StemSeparationMode.SIX_STEMS -> {
                    val pianoWeight = if (freq in 170f..4400f) o * 0.37f else o * 0.08f
                    val guitarWeight = if (freq in 250f..3000f) o * 0.39f else o * 0.10f
                    val otherRem = (o - pianoWeight - guitarWeight).coerceAtLeast(0f)
                    rawWeights[0][k] = v
                    rawWeights[1][k] = p
                    rawWeights[2][k] = b
                    rawWeights[3][k] = pianoWeight
                    rawWeights[4][k] = guitarWeight
                    rawWeights[5][k] = otherRem
                }
            }
        }

        // Smooth each stem mask across neighboring bins, then re-normalize to sum == 1.0f
        for (sIdx in 0 until stemCount) {
            smoothSpectrum3(rawWeights[sIdx], smoothedWeights[sIdx])
        }
        for (k in 0 until HALF_BINS) {
            var sum = 0f
            for (sIdx in 0 until stemCount) {
                sum += smoothedWeights[sIdx][k]
            }
            if (sum > EPS) {
                for (sIdx in 0 until stemCount) {
                    smoothedWeights[sIdx][k] /= sum
                }
            } else {
                smoothedWeights[stemCount - 1][k] = 1f
            }
        }
    }

    private fun smoothSpectrum3(src: FloatArray, dst: FloatArray) {
        val last = src.lastIndex
        if (last <= 0) {
            if (src.isNotEmpty()) dst[0] = src[0]
            return
        }
        dst[0] = src[0] * 0.8f + src[1] * 0.2f
        for (k in 1 until last) {
            dst[k] = src[k - 1] * 0.2f + src[k] * 0.6f + src[k + 1] * 0.2f
        }
        dst[last] = src[last - 1] * 0.2f + src[last] * 0.8f
    }

    private fun computeVocalBandWeight(freq: Float): Float {
        return when {
            freq < 140f -> 0.02f
            freq < 260f -> (freq - 140f) / 120f * 0.45f
            freq <= 3400f -> {
                val centerBoost = exp(-((ln(max(freq, 180f) / 1100f)).let { it * it }) / 1.4f)
                0.45f + 0.50f * centerBoost
            }
            freq <= 6500f -> 0.45f * (1f - (freq - 3400f) / 3100f)
            else -> 0.05f
        }.coerceIn(0f, 1f)
    }

    private fun computeBassBandWeight(freq: Float): Float {
        return when {
            freq <= 150f -> 0.95f
            freq <= 280f -> 0.95f * (1f - (freq - 150f) / 130f)
            else -> 0.02f
        }.coerceIn(0f, 1f)
    }

    private fun buildWavHeader(totalAudioBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val totalDataLen = totalAudioBytes + 36L
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
        header[40] = (totalAudioBytes and 0xff).toByte()
        header[41] = ((totalAudioBytes shr 8) and 0xff).toByte()
        header[42] = ((totalAudioBytes shr 16) and 0xff).toByte()
        header[43] = ((totalAudioBytes shr 24) and 0xff).toByte()
        return header
    }

    private fun patchWavHeaderSizes(file: File, totalAudioBytes: Long) {
        val totalDataLen = totalAudioBytes + 36L
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        RandomAccessFile(file, "rw").use { raf ->
            bb.clear()
            bb.putInt((totalDataLen and 0xFFFFFFFFL).toInt())
            raf.seek(4L)
            raf.write(bb.array())

            bb.clear()
            bb.putInt((totalAudioBytes and 0xFFFFFFFFL).toInt())
            raf.seek(40L)
            raf.write(bb.array())
        }
    }

    private fun softClipSample(value: Float): Short {
        val scaled = value / 32767f
        val clipped = when {
            scaled > 1f -> 1f - exp(-(scaled - 1f)) * 0.1f
            scaled < -1f -> -1f + exp(-(-scaled - 1f)) * 0.1f
            else -> scaled
        }
        return (clipped * 32767f).roundToInt().coerceIn(-32767, 32767).toShort()
    }

    private fun fftRadix2(real: FloatArray, imag: FloatArray, inverse: Boolean) {
        val n = real.size
        val br = bitReverseTable
        for (i in 1 until n) {
            val j = br[i]
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        val cTbl = cosFwd
        val sTbl = sinFwd
        val signMul = if (inverse) -1f else 1f
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val stride = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val tblIdx = k * stride
                    val wR = cTbl[tblIdx]
                    val wI = sTbl[tblIdx] * signMul
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val idx2 = i + k + half
                    val vR = real[idx2] * wR - imag[idx2] * wI
                    val vI = real[idx2] * wI + imag[idx2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[idx2] = uR - vR
                    imag[idx2] = uI - vI
                }
                i += len
            }
            len = len shl 1
        }

        if (inverse) {
            val invN = 1f / n
            for (i in 0 until n) {
                real[i] *= invN
                imag[i] *= invN
            }
        }
    }
}
