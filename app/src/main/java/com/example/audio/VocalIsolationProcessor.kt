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

    internal fun hasDistinctQuietFloor(samples: ShortArray): Boolean {
        if (samples.size < 2048) return false
        val windows = 24
        val step = max(1, samples.size / windows)
        var minRms = Double.MAX_VALUE
        var maxRms = 0.0
        for (w in 0 until windows) {
            val start = w * step
            val end = min(samples.size, start + step)
            if (end <= start) continue
            var sumSq = 0.0
            for (i in start until end) {
                val s = samples[i].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / (end - start).toDouble())
            if (rms < minRms) minRms = rms
            if (rms > maxRms) maxRms = rms
        }
        return maxRms > 1.0 && (minRms / maxRms) < 0.45
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
        return isolateSpokenAudio(
            samples = samples,
            subtractionStrength = subtractionStrength,
            humRemovalStrength = humRemovalStrength,
            vocalClarityBoost = vocalClarityBoost,
            spectralFloorOverride = spectralFloorOverride,
            vocalBandEmphasisOverride = vocalBandEmphasisOverride,
            pauseGateFloorOverride = pauseGateFloorOverride,
            highPassHz = highPassHz,
            sampleRate = sampleRate,
            channels = 1
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
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        channels: Int = 1
    ): ShortArray {
        val minRequired = if (channels == 2) 256 else 128
        if (samples.size < minRequired) return samples

        val subStr = subtractionStrength.coerceIn(0.1f, 1.0f)
        val humStr = humRemovalStrength.coerceIn(0f, 1.0f)
        val clarityStr = vocalClarityBoost.coerceIn(0f, 1.0f)

        val spectralFloor = spectralFloorOverride ?: (0.28f - subStr * 0.255f).coerceIn(0.018f, 0.35f)
        val vocalEmphasis = vocalBandEmphasisOverride ?: (subStr * 0.72f).coerceIn(0.1f, 0.80f)
        val pauseFloor = pauseGateFloorOverride ?: (0.42f - subStr * 0.405f).coerceIn(0.01f, 0.5f)
        val hpCutoff = highPassHz ?: (65f + subStr * 30f)

        val config = StudioNoiseReductionConfig(
            strength = subStr,
            humRemovalStrength = humStr,
            spectralFloorOverride = spectralFloor,
            pauseAttenuationFloor = pauseFloor,
            lowCutHz = hpCutoff,
            lowCutAttenuation = (0.35f + vocalEmphasis * 0.50f).coerceIn(0.25f, 0.85f),
            preserveHighFrequencies = true,
            vocalClarityBoost = clarityStr
        )

        return StudioNoiseReducer.reduceNoise(
            samples = samples,
            sampleRate = sampleRate,
            channels = channels,
            config = config
        )
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
