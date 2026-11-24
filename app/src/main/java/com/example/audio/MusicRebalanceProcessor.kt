package com.example.audio

import kotlin.math.PI
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
 * Pure-Kotlin iZotope RX-inspired Music Rebalance & Stem Separation DSP engine.
 *
 * Uses overlap-add STFT spectral decomposition combined with Harmonic-Percussive
 * Source Separation (median spectral smoothing across frequency vs time) and
 * perceptual frequency weighting to isolate/balance Vocals, Bass, Percussion/Drums,
 * Piano, Guitar, Backing Vocals, and Other instrumental energy completely on-device.
 */
object MusicRebalanceProcessor {

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 256
    private const val EPS = 1e-9f

    /**
     * Adjusts balance of Vocals, Bass, Percussion/Drums, and Other instruments.
     * Gains are linear multipliers (0.0f = muted/removed, 1.0f = original, up to 2.0f = boosted).
     */
    fun rebalanceMix(
        samples: ShortArray,
        sampleRate: Int,
        vocalsGain: Float = 1.0f,
        bassGain: Float = 1.0f,
        percussionGain: Float = 1.0f,
        otherGain: Float = 1.0f,
        onProgress: (Float) -> Unit = {}
    ): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)
        // Fast identity check
        if (
            kotlin.math.abs(vocalsGain - 1f) < 0.01f &&
            kotlin.math.abs(bassGain - 1f) < 0.01f &&
            kotlin.math.abs(percussionGain - 1f) < 0.01f &&
            kotlin.math.abs(otherGain - 1f) < 0.01f
        ) {
            onProgress(1f)
            return samples.copyOf()
        }

        val masks = computeSpectralComponentMasks(samples, sampleRate, onProgress)
        val window = hannWindow(FFT_SIZE)
        val totalFrames = masks.numFrames
        val output = FloatArray(samples.size + FFT_SIZE)
        val normAccum = FloatArray(samples.size + FFT_SIZE)

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        val halfBins = FFT_SIZE / 2 + 1

        for (f in 0 until totalFrames) {
            val offset = f * HOP_SIZE
            for (n in 0 until FFT_SIZE) {
                val s = if (offset + n < samples.size) samples[offset + n].toFloat() else 0f
                real[n] = s * window[n]
                imag[n] = 0f
            }
            fftRadix2(real, imag, inverse = false)

            val vMask = masks.vocalMask[f]
            val bMask = masks.bassMask[f]
            val pMask = masks.percMask[f]
            val oMask = masks.otherMask[f]

            for (k in 0 until halfBins) {
                val combinedGain = (
                    vMask[k] * vocalsGain +
                        bMask[k] * bassGain +
                        pMask[k] * percussionGain +
                        oMask[k] * otherGain
                    ).coerceIn(0f, 2.5f)
                real[k] *= combinedGain
                imag[k] *= combinedGain
                if (k in 1 until FFT_SIZE / 2) {
                    val mirror = FFT_SIZE - k
                    real[mirror] *= combinedGain
                    imag[mirror] *= combinedGain
                }
            }

            fftRadix2(real, imag, inverse = true)
            for (n in 0 until FFT_SIZE) {
                val w = window[n]
                output[offset + n] += real[n] * w
                normAccum[offset + n] += w * w
            }
            if (f % 32 == 0) {
                onProgress(0.55f + 0.45f * (f.toFloat() / max(1, totalFrames)))
            }
        }

        val result = ShortArray(samples.size)
        for (i in samples.indices) {
            val norm = normAccum[i]
            val valF = if (norm > 1e-6f) output[i] / norm else samples[i].toFloat()
            result[i] = softClipSample(valF)
        }
        onProgress(1f)
        return result
    }

    /**
     * Separates input PCM into distinct named stem buffers according to [mode].
     */
    fun separateStems(
        samples: ShortArray,
        sampleRate: Int,
        mode: StemSeparationMode,
        onProgress: (Float) -> Unit = {}
    ): Map<String, ShortArray> {
        if (samples.isEmpty()) {
            onProgress(1f)
            return mode.stemNames.associateWith { ShortArray(0) }
        }

        val masks = computeSpectralComponentMasks(samples, sampleRate) { p ->
            onProgress(p * 0.45f)
        }
        val window = hannWindow(FFT_SIZE)
        val totalFrames = masks.numFrames
        val halfBins = FFT_SIZE / 2 + 1
        val freqPerBin = sampleRate.toFloat() / FFT_SIZE

        val stemNames = mode.stemNames
        val stemAccum = Array(stemNames.size) { FloatArray(samples.size + FFT_SIZE) }
        val normAccum = FloatArray(samples.size + FFT_SIZE)

        val srcReal = FloatArray(FFT_SIZE)
        val srcImag = FloatArray(FFT_SIZE)
        val stemReal = FloatArray(FFT_SIZE)
        val stemImag = FloatArray(FFT_SIZE)

        for (f in 0 until totalFrames) {
            val offset = f * HOP_SIZE
            for (n in 0 until FFT_SIZE) {
                val s = if (offset + n < samples.size) samples[offset + n].toFloat() else 0f
                srcReal[n] = s * window[n]
                srcImag[n] = 0f
            }
            fftRadix2(srcReal, srcImag, inverse = false)

            val vMask = masks.vocalMask[f]
            val bMask = masks.bassMask[f]
            val pMask = masks.percMask[f]
            val oMask = masks.otherMask[f]

            // Build per-stem soft Wiener masks normalized to sum <= 1.0 per bin
            val binWeights = Array(stemNames.size) { FloatArray(halfBins) }
            for (k in 0 until halfBins) {
                val freq = k * freqPerBin
                val v = vMask[k]
                val b = bMask[k]
                val p = pMask[k]
                val o = oMask[k]

                when (mode) {
                    StemSeparationMode.TWO_STEMS -> {
                        // Vocals, Instruments
                        binWeights[0][k] = v
                        binWeights[1][k] = (b + p + o)
                    }
                    StemSeparationMode.THREE_STEMS -> {
                        // Vocals, Backing Vocals, Instruments
                        val leadVoiceWeight = if (freq in 250f..3600f) 0.78f else 0.55f
                        binWeights[0][k] = v * leadVoiceWeight
                        binWeights[1][k] = v * (1f - leadVoiceWeight) + o * 0.12f
                        binWeights[2][k] = b + p + o * 0.88f
                    }
                    StemSeparationMode.FOUR_STEMS -> {
                        // Vocal, Other, Bass, Piano
                        val pianoTone = if (freq in 180f..4200f) o * 0.55f else o * 0.15f
                        val otherRem = (p + o - pianoTone).coerceAtLeast(0f)
                        binWeights[0][k] = v
                        binWeights[1][k] = otherRem
                        binWeights[2][k] = b
                        binWeights[3][k] = pianoTone
                    }
                    StemSeparationMode.FIVE_STEMS -> {
                        // Vocals, Guitar, Bass, Drums, Other
                        val guitarWeight = if (freq in 220f..3200f) o * 0.62f else o * 0.12f
                        val otherRem = (o - guitarWeight).coerceAtLeast(0f)
                        binWeights[0][k] = v
                        binWeights[1][k] = guitarWeight
                        binWeights[2][k] = b
                        binWeights[3][k] = p
                        binWeights[4][k] = otherRem
                    }
                    StemSeparationMode.SIX_STEMS -> {
                        // Vocals, Drums, Bass, Piano, Guitar, Other
                        val pianoWeight = if (freq in 180f..4400f) o * 0.36f else o * 0.08f
                        val guitarWeight = if (freq in 260f..3000f) o * 0.38f else o * 0.10f
                        val otherRem = (o - pianoWeight - guitarWeight).coerceAtLeast(0f)
                        binWeights[0][k] = v
                        binWeights[1][k] = p
                        binWeights[2][k] = b
                        binWeights[3][k] = pianoWeight
                        binWeights[4][k] = guitarWeight
                        binWeights[5][k] = otherRem
                    }
                }

                // Normalize per bin so reconstructed sum approximates original
                var sum = 0f
                for (sIdx in stemNames.indices) sum += binWeights[sIdx][k]
                if (sum > EPS) {
                    for (sIdx in stemNames.indices) {
                        binWeights[sIdx][k] /= sum
                    }
                } else {
                    binWeights[stemNames.lastIndex][k] = 1f
                }
            }

            for (sIdx in stemNames.indices) {
                val wBins = binWeights[sIdx]
                for (k in 0 until halfBins) {
                    val gain = wBins[k]
                    stemReal[k] = srcReal[k] * gain
                    stemImag[k] = srcImag[k] * gain
                    if (k in 1 until FFT_SIZE / 2) {
                        val mirror = FFT_SIZE - k
                        stemReal[mirror] = srcReal[mirror] * gain
                        stemImag[mirror] = srcImag[mirror] * gain
                    }
                }
                fftRadix2(stemReal, stemImag, inverse = true)
                val target = stemAccum[sIdx]
                for (n in 0 until FFT_SIZE) {
                    target[offset + n] += stemReal[n] * window[n]
                }
            }
            for (n in 0 until FFT_SIZE) {
                normAccum[offset + n] += window[n] * window[n]
            }

            if (f % 24 == 0) {
                onProgress(0.45f + 0.55f * (f.toFloat() / max(1, totalFrames)))
            }
        }

        val outMap = LinkedHashMap<String, ShortArray>(stemNames.size)
        for (sIdx in stemNames.indices) {
            val acc = stemAccum[sIdx]
            val pcm = ShortArray(samples.size)
            for (i in samples.indices) {
                val norm = normAccum[i]
                val v = if (norm > 1e-6f) acc[i] / norm else 0f
                pcm[i] = softClipSample(v)
            }
            outMap[stemNames[sIdx]] = pcm
        }
        onProgress(1f)
        return outMap
    }

    private class SpectralComponentMasks(
        val numFrames: Int,
        val vocalMask: Array<FloatArray>,
        val bassMask: Array<FloatArray>,
        val percMask: Array<FloatArray>,
        val otherMask: Array<FloatArray>
    )

    private fun computeSpectralComponentMasks(
        samples: ShortArray,
        sampleRate: Int,
        onProgress: (Float) -> Unit
    ): SpectralComponentMasks {
        val window = hannWindow(FFT_SIZE)
        val numFrames = max(1, (samples.size + HOP_SIZE - 1) / HOP_SIZE)
        val halfBins = FFT_SIZE / 2 + 1
        val mags = Array(numFrames) { FloatArray(halfBins) }

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE
            for (n in 0 until FFT_SIZE) {
                val s = if (offset + n < samples.size) samples[offset + n].toFloat() else 0f
                real[n] = s * window[n]
                imag[n] = 0f
            }
            fftRadix2(real, imag, inverse = false)
            for (k in 0 until halfBins) {
                mags[f][k] = hypot(real[k], imag[k])
            }
            if (f % 32 == 0) {
                onProgress(0.25f * (f.toFloat() / numFrames))
            }
        }

        // Harmonic-Percussive median masks
        val timeHalfWin = 4
        val freqHalfWin = 4
        val timeScratch = FloatArray(timeHalfWin * 2 + 1)
        val freqScratch = FloatArray(freqHalfWin * 2 + 1)

        val vocalMask = Array(numFrames) { FloatArray(halfBins) }
        val bassMask = Array(numFrames) { FloatArray(halfBins) }
        val percMask = Array(numFrames) { FloatArray(halfBins) }
        val otherMask = Array(numFrames) { FloatArray(halfBins) }

        val freqPerBin = sampleRate.toFloat() / FFT_SIZE

        for (f in 0 until numFrames) {
            val row = mags[f]
            for (k in 0 until halfBins) {
                // Temporal median -> harmonic energy
                var tCount = 0
                for (df in -timeHalfWin..timeHalfWin) {
                    val idx = (f + df).coerceIn(0, numFrames - 1)
                    timeScratch[tCount++] = mags[idx][k]
                }
                timeScratch.sort(0, tCount)
                val harmonic = timeScratch[tCount / 2]

                // Frequency median -> percussive / transient energy
                var fCount = 0
                for (dk in -freqHalfWin..freqHalfWin) {
                    val idx = (k + dk).coerceIn(0, halfBins - 1)
                    freqScratch[fCount++] = row[idx]
                }
                freqScratch.sort(0, fCount)
                val percussive = freqScratch[fCount / 2]

                val hPow = harmonic * harmonic
                val pPow = percussive * percussive
                val totalPow = hPow + pPow + EPS
                val hRatio = hPow / totalPow
                val pRatio = pPow / totalPow

                val freq = k * freqPerBin

                // Vocal presence envelope (200 Hz .. 4200 Hz harmonic peaks, peaked around 850-2400 Hz)
                val vocalBand = when {
                    freq < 140f -> 0.02f
                    freq < 260f -> (freq - 140f) / 120f * 0.45f
                    freq <= 3400f -> {
                        val centerBoost = exp(-((ln(max(freq, 180f) / 1100f)).let { it * it }) / 1.4f)
                        0.45f + 0.50f * centerBoost
                    }
                    freq <= 6500f -> 0.45f * (1f - (freq - 3400f) / 3100f)
                    else -> 0.05f
                }.coerceIn(0f, 1f)

                // Bass envelope (< 250 Hz dominated)
                val bassBand = when {
                    freq <= 150f -> 0.95f
                    freq <= 280f -> 0.95f * (1f - (freq - 150f) / 130f)
                    else -> 0.02f
                }.coerceIn(0f, 1f)

                val vRaw = hRatio * vocalBand
                val bRaw = (0.7f * hRatio + 0.3f * pRatio) * bassBand
                val pRaw = pRatio * (1f - bassBand * 0.7f)
                val oRaw = (1f - vRaw - bRaw - pRaw).coerceAtLeast(0.05f)

                val sum = vRaw + bRaw + pRaw + oRaw + EPS
                vocalMask[f][k] = vRaw / sum
                bassMask[f][k] = bRaw / sum
                percMask[f][k] = pRaw / sum
                otherMask[f][k] = oRaw / sum
            }
            if (f % 32 == 0) {
                onProgress(0.25f + 0.30f * (f.toFloat() / numFrames))
            }
        }

        return SpectralComponentMasks(
            numFrames = numFrames,
            vocalMask = vocalMask,
            bassMask = bassMask,
            percMask = percMask,
            otherMask = otherMask
        )
    }

    private fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / size))).toFloat()
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
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        val sign = if (inverse) 1.0 else -1.0
        while (len <= n) {
            val angle = sign * 2.0 * PI / len
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val idx2 = i + k + half
                    val vR = real[idx2] * wR - imag[idx2] * wI
                    val vI = real[idx2] * wI + imag[idx2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[idx2] = uR - vR
                    imag[idx2] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
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
