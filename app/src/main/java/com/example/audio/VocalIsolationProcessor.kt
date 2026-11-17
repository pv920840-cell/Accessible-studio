package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class VocalIsolationPreset(
    val displayName: String,
    val shortDescription: String,
    val subtractionStrength: Float,
    val humRemovalStrength: Float,
    val vocalClarityBoost: Float,
    val spectralAlpha: Float,
    val spectralFloor: Float,
    val vocalBandEmphasis: Float,
    val pauseGateFloor: Float,
    val highPassHz: Float
) {
    LIGHT(
        displayName = "Light Filtering",
        shortDescription = "Gentle adaptive de-hiss & rumble cut while keeping natural room warmth and vocal air.",
        subtractionStrength = 0.40f,
        humRemovalStrength = 0.65f,
        vocalClarityBoost = 0.30f,
        spectralAlpha = 1.15f,
        spectralFloor = 0.22f,
        vocalBandEmphasis = 0.18f,
        pauseGateFloor = 0.32f,
        highPassHz = 70f
    ),
    MEDIUM(
        displayName = "Medium Filtering",
        shortDescription = "Balanced ambient noise & 50/60Hz hum subtraction with crisp, pristine vocal clarity.",
        subtractionStrength = 0.70f,
        humRemovalStrength = 0.88f,
        vocalClarityBoost = 0.55f,
        spectralAlpha = 1.75f,
        spectralFloor = 0.08f,
        vocalBandEmphasis = 0.48f,
        pauseGateFloor = 0.10f,
        highPassHz = 82f
    ),
    STRONG(
        displayName = "Strong Filtering",
        shortDescription = "Maximum speech isolation: removes heavy ambient noise, wind & hum while keeping speech crisp.",
        subtractionStrength = 0.95f,
        humRemovalStrength = 0.98f,
        vocalClarityBoost = 0.78f,
        spectralAlpha = 2.45f,
        spectralFloor = 0.025f,
        vocalBandEmphasis = 0.78f,
        pauseGateFloor = 0.015f,
        highPassHz = 95f
    )
}

/**
 * Pure-Kotlin On-Device Adaptive Spectral Subtraction & Spoken Audio Isolation Engine.
 * Uses a 1024-point Hann-windowed STFT with 50% overlap-add, adaptive minimum-statistics
 * spectral noise floor tracking, 50/60Hz harmonic hum suppression, voice-activity detection,
 * musical-noise smoothing, and presence-preserving clarity polish.
 */
object VocalIsolationProcessor {

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = FFT_SIZE / 2
    private const val NUM_BINS = FFT_SIZE / 2 + 1

    private val sqrtHannWindow = FloatArray(FFT_SIZE) { i ->
        sin(PI * i.toDouble() / FFT_SIZE.toDouble()).toFloat()
    }

    private val bitReversedIndex = IntArray(FFT_SIZE).apply {
        val logN = 10
        for (i in 0 until FFT_SIZE) {
            var rev = 0
            var x = i
            for (b in 0 until logN) {
                rev = (rev shl 1) or (x and 1)
                x = x ushr 1
            }
            this[i] = rev
        }
    }

    fun isolateSpokenAudio(
        samples: ShortArray,
        preset: VocalIsolationPreset = VocalIsolationPreset.MEDIUM,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE
    ): ShortArray {
        return isolateSpokenAudio(
            samples = samples,
            subtractionStrength = preset.subtractionStrength,
            humRemovalStrength = preset.humRemovalStrength,
            vocalClarityBoost = preset.vocalClarityBoost,
            spectralFloorOverride = preset.spectralFloor,
            vocalBandEmphasisOverride = preset.vocalBandEmphasis,
            pauseGateFloorOverride = preset.pauseGateFloor,
            highPassHz = preset.highPassHz,
            sampleRate = sampleRate
        )
    }

    fun isolateSpokenAudio(
        samples: ShortArray,
        subtractionStrength: Float,
        humRemovalStrength: Float,
        vocalClarityBoost: Float,
        spectralFloorOverride: Float? = null,
        vocalBandEmphasisOverride: Float? = null,
        pauseGateFloorOverride: Float? = null,
        highPassHz: Float? = null,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE
    ): ShortArray {
        if (samples.size < 128) return samples

        val subStr = subtractionStrength.coerceIn(0.1f, 1.0f)
        val humStr = humRemovalStrength.coerceIn(0f, 1.0f)
        val clarityStr = vocalClarityBoost.coerceIn(0f, 1.0f)

        val alphaBase = 0.85f + subStr * 1.75f
        val spectralFloor = spectralFloorOverride ?: (0.28f - subStr * 0.255f).coerceIn(0.018f, 0.35f)
        val vocalEmphasis = vocalBandEmphasisOverride ?: (subStr * 0.80f).coerceIn(0.1f, 0.85f)
        val pauseFloor = pauseGateFloorOverride ?: (0.42f - subStr * 0.405f).coerceIn(0.01f, 0.5f)
        val hpCutoff = highPassHz ?: (65f + subStr * 32f)

        // Step 1: DC offset removal + sub-bass rumble high-pass + 50/60 Hz hum pre-conditioning
        var pre = StudioAudioProcessor.removeDcOffset(samples)
        pre = StudioAudioProcessor.applyHighPassFilter(pre, hpCutoff)
        if (humStr > 0.15f) {
            val humClean = StudioAudioProcessor.applyNotchFilter(
                StudioAudioProcessor.applyNotchFilter(
                    StudioAudioProcessor.applyNotchFilter(pre, 60f, 12f),
                    50f,
                    12f
                ),
                120f,
                16f
            )
            val wet = humStr.coerceIn(0f, 1f)
            val blended = ShortArray(pre.size)
            for (i in pre.indices) {
                blended[i] = (pre[i] * (1f - wet) + humClean[i] * wet).toInt().coerceIn(-32767, 32767).toShort()
            }
            pre = blended
        }

        // Convert to normalized float (-1..1) with edge padding for clean OLA boundaries
        val pad = HOP_SIZE
        val paddedLen = pre.size + 2 * pad
        val frameCount = (((paddedLen - FFT_SIZE + HOP_SIZE - 1) / HOP_SIZE) + 1).coerceAtLeast(1)
        val totalLen = (frameCount - 1) * HOP_SIZE + FFT_SIZE
        val inputFloat = FloatArray(totalLen)
        for (i in pre.indices) {
            inputFloat[pad + i] = pre[i].toFloat() / 32767f
        }

        val sr = sampleRate.coerceIn(8000, 96000).toFloat()
        val binHz = sr / FFT_SIZE.toFloat()
        val speechMinBin = max(1, (160f / binHz).toInt())
        val speechMaxBin = min(NUM_BINS - 1, (4200f / binHz).toInt())
        val lowCutBin = max(1, (85f / binHz).toInt())
        val consonantMaxBin = min(NUM_BINS - 1, (7800f / binHz).toInt())

        // Precompute STFT magnitudes & phases across frames for minimum-statistics noise estimation
        val frameMags = Array(frameCount) { FloatArray(NUM_BINS) }
        val frameReals = Array(frameCount) { FloatArray(FFT_SIZE) }
        val frameImags = Array(frameCount) { FloatArray(FFT_SIZE) }
        val frameSpeechBandPower = FloatArray(frameCount)

        for (f in 0 until frameCount) {
            val offset = f * HOP_SIZE
            val re = frameReals[f]
            val im = frameImags[f]
            for (k in 0 until FFT_SIZE) {
                re[k] = inputFloat[offset + k] * sqrtHannWindow[k]
                im[k] = 0f
            }
            fft(re, im, inverse = false)

            val mags = frameMags[f]
            var speechPow = 0f
            for (b in 0 until NUM_BINS) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                mags[b] = mag
                if (b in speechMinBin..speechMaxBin) {
                    speechPow += mag * mag
                }
            }
            frameSpeechBandPower[f] = speechPow / max(1, speechMaxBin - speechMinBin + 1)
        }

        // Step 2: Initial Ambient Noise Profile from lowest-energy 20% frames
        val sortedIndices = frameSpeechBandPower.indices.sortedBy { frameSpeechBandPower[it] }
        val quietCount = max(1, (frameCount * 0.20f).toInt().coerceAtMost(min(frameCount, 24)))
        val noiseMag = FloatArray(NUM_BINS)
        for (q in 0 until quietCount) {
            val mags = frameMags[sortedIndices[q]]
            for (b in 0 until NUM_BINS) {
                noiseMag[b] += mags[b]
            }
        }
        val invQuiet = 1f / quietCount.toFloat()
        for (b in 0 until NUM_BINS) {
            noiseMag[b] *= invQuiet
        }

        // Determine speech activity threshold relative to median quiet floor
        val medianQuietPow = frameSpeechBandPower[sortedIndices[(quietCount - 1).coerceAtLeast(0)]]
        val speechFloorRef = max(medianQuietPow * 2.4f, 1e-6f)

        // Frequency-dependent hum bins (50Hz, 60Hz, and low harmonics 100/120/150/180 Hz)
        val humHarmonicsHz = floatArrayOf(50f, 60f, 100f, 120f, 150f, 180f)
        val humBinWeights = FloatArray(NUM_BINS) { 1.0f }
        if (humStr > 0.05f) {
            for (b in 0 until NUM_BINS) {
                val freq = b * binHz
                for (hz in humHarmonicsHz) {
                    if (abs(freq - hz) <= binHz * 0.85f) {
                        humBinWeights[b] = min(humBinWeights[b], 1.0f - humStr * 0.82f)
                    }
                }
            }
        }

        // Step 3: Adaptive Per-Frame Spectral Subtraction + Musical Noise Smoothing + OLA
        val outAccum = FloatArray(totalLen)
        val olaNorm = FloatArray(totalLen)
        val prevGain = FloatArray(NUM_BINS) { 1.0f }
        val rawBinGain = FloatArray(NUM_BINS)
        val freqSmoothedGain = FloatArray(NUM_BINS)
        var speechActivitySmoothed = 0f

        for (f in 0 until frameCount) {
            val mags = frameMags[f]
            val re = frameReals[f]
            val im = frameImags[f]

            // Estimate instantaneous speech SNR across vocal formant band
            var speechSignalSum = 0f
            var speechNoiseSum = 1e-8f
            for (b in speechMinBin..speechMaxBin) {
                speechSignalSum += mags[b]
                speechNoiseSum += noiseMag[b]
            }
            val frameSnr = speechSignalSum / speechNoiseSum
            val instantSpeechProb = when {
                frameSpeechBandPower[f] < speechFloorRef * 0.45f -> 0f
                frameSnr <= 1.25f -> 0f
                frameSnr >= 2.6f -> 1f
                else -> ((frameSnr - 1.25f) / 1.35f).coerceIn(0f, 1f)
            }
            // Fast attack, slow hang-over release so word endings & soft consonants are never chopped
            val actAlpha = if (instantSpeechProb > speechActivitySmoothed) 0.65f else 0.14f
            speechActivitySmoothed += actAlpha * (instantSpeechProb - speechActivitySmoothed)

            // Adapt spectral noise profile online during non-speech / quiet dips
            for (b in 0 until NUM_BINS) {
                val cur = mags[b]
                if (cur < noiseMag[b]) {
                    // Track quieter floor downward quickly
                    noiseMag[b] = 0.82f * noiseMag[b] + 0.18f * cur
                } else if (speechActivitySmoothed < 0.28f && cur < noiseMag[b] * 2.2f) {
                    // Track shifting ambient noise upward slowly during pauses
                    noiseMag[b] = 0.94f * noiseMag[b] + 0.06f * cur
                }
            }

            // Compute oversubtracted Wiener gain per frequency bin
            val adaptiveAlpha = alphaBase * (1.15f - 0.30f * speechActivitySmoothed)
            for (b in 0 until NUM_BINS) {
                val mag = mags[b]
                val nMag = noiseMag[b]
                val sub = 1.0f - adaptiveAlpha * (nMag / (mag + 1e-7f))
                val wiener = if (sub > spectralFloor) sub else spectralFloor

                // Band shaping: protect speech formants & consonants during active speech,
                // trim out-of-band sub-bass rumble and ultrasonic hiss
                val freqWeight = when {
                    b < lowCutBin -> 1.0f - vocalEmphasis * 0.85f
                    b in lowCutBin..speechMaxBin -> 1.0f
                    b in (speechMaxBin + 1)..consonantMaxBin ->
                        // Keep crisp vocal sibilance during speech, suppress steady hiss during silence
                        (1.0f - vocalEmphasis * 0.25f) + (vocalEmphasis * 0.25f * speechActivitySmoothed)
                    else -> 1.0f - vocalEmphasis * 0.75f
                }

                rawBinGain[b] = (wiener * freqWeight * humBinWeights[b]).coerceIn(spectralFloor, 1.0f)
            }

            // 3-bin frequency smoothing eliminates isolated tonal peaks ("musical noise")
            for (b in 0 until NUM_BINS) {
                val left = rawBinGain[max(0, b - 1)]
                val center = rawBinGain[b]
                val right = rawBinGain[min(NUM_BINS - 1, b + 1)]
                freqSmoothedGain[b] = 0.22f * left + 0.56f * center + 0.22f * right
            }

            // Smooth pause gate across silence/ambient gaps
            val pauseMult = pauseFloor + (1.0f - pauseFloor) * speechActivitySmoothed

            // Apply temporal smoothing + gain to complex spectrum (bin 0..N/2 and conjugate symmetry)
            for (b in 0 until NUM_BINS) {
                val targetGain = (freqSmoothedGain[b] * pauseMult).coerceIn(pauseFloor.coerceAtMost(spectralFloor), 1.0f)
                val tAlpha = if (targetGain > prevGain[b]) 0.72f else 0.38f
                val finalGain = prevGain[b] + tAlpha * (targetGain - prevGain[b])
                prevGain[b] = finalGain

                re[b] *= finalGain
                im[b] *= finalGain
                if (b in 1 until (FFT_SIZE / 2)) {
                    val mirror = FFT_SIZE - b
                    re[mirror] *= finalGain
                    im[mirror] *= finalGain
                }
            }

            fft(re, im, inverse = true)

            val offset = f * HOP_SIZE
            for (k in 0 until FFT_SIZE) {
                val w = sqrtHannWindow[k]
                outAccum[offset + k] += re[k] * w
                olaNorm[offset + k] += w * w
            }
        }

        // Normalize OLA back to ShortArray
        val reconstructed = ShortArray(samples.size)
        for (i in reconstructed.indices) {
            val norm = olaNorm[pad + i].coerceAtLeast(1e-4f)
            val s = (outAccum[pad + i] / norm) * 32767f
            reconstructed[i] = s.toInt().coerceIn(-32767, 32767).toShort()
        }

        // Step 4: Pristine vocal clarity EQ polish (gentle presence boost, warm low protection)
        val polished = if (clarityStr > 0.05f) {
            val midPres = 1.0f + clarityStr * 1.8f   // ~2.5 kHz articulation
            val airPres = 1.0f + clarityStr * 1.2f   // ~6 kHz crispness
            val mudTrim = -0.8f * clarityStr         // ~400 Hz boxiness cleanup
            StudioAudioProcessor.apply7BandEq(
                reconstructed,
                b60 = -1.2f * clarityStr,
                b150 = 0f,
                b400 = mudTrim,
                b1k = 0.6f * clarityStr,
                b2_5k = midPres,
                b6k = airPres,
                b15k = 0f
            )
        } else {
            reconstructed
        }

        return StudioAudioProcessor.applyTruePeakLimiter(polished, -0.8f)
    }

    internal fun fft(real: FloatArray, imag: FloatArray, inverse: Boolean) {
        val n = FFT_SIZE
        for (i in 0 until n) {
            val j = bitReversedIndex[i]
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        val sign = if (inverse) 1.0 else -1.0
        while (len <= n) {
            val halfLen = len ushr 1
            val angle = sign * (2.0 * PI / len.toDouble())
            val wStepRe = cos(angle).toFloat()
            val wStepIm = sin(angle).toFloat()

            var start = 0
            while (start < n) {
                var wRe = 1.0f
                var wIm = 0.0f
                for (k in 0 until halfLen) {
                    val i1 = start + k
                    val i2 = i1 + halfLen
                    val tRe = wRe * real[i2] - wIm * imag[i2]
                    val tIm = wRe * imag[i2] + wIm * real[i2]

                    real[i2] = real[i1] - tRe
                    imag[i2] = imag[i1] - tIm
                    real[i1] += tRe
                    imag[i1] += tIm

                    val nextRe = wRe * wStepRe - wIm * wStepIm
                    wIm = wRe * wStepIm + wIm * wStepRe
                    wRe = nextRe
                }
                start += len
            }
            len = len shl 1
        }

        if (inverse) {
            val invN = 1.0f / n.toFloat()
            for (i in 0 until n) {
                real[i] *= invN
                imag[i] *= invN
            }
        }
    }
}
