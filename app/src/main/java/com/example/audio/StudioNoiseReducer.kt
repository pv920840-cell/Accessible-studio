package com.example.audio

import kotlin.math.max

/**
 * Legacy compatibility configuration adapter delegating to [AdaptiveSpectralDenoiser]
 * and [SpectralDenoiseParams].
 */
data class StudioNoiseReductionConfig(
    val strength: Float = 0.60f,
    val humRemovalStrength: Float = 0.75f,
    val fanMotorRemovalStrength: Float = 0.78f,
    val spectralFloorOverride: Float? = null,
    val pauseAttenuationFloor: Float = 0.25f,
    val lowCutHz: Float = 62f,
    val lowCutAttenuation: Float = 0.55f,
    val preserveHighFrequencies: Boolean = true,
    val vocalClarityBoost: Float = 0.0f,
    val studioMaxClean: Boolean = false,
    val customNoisePrintPcm: ShortArray? = null,
    val deHumTargetHz: FloatArray = floatArrayOf(50f, 60f, 100f, 120f, 150f, 180f, 240f, 300f)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StudioNoiseReductionConfig) return false
        return strength == other.strength &&
            humRemovalStrength == other.humRemovalStrength &&
            fanMotorRemovalStrength == other.fanMotorRemovalStrength &&
            spectralFloorOverride == other.spectralFloorOverride &&
            pauseAttenuationFloor == other.pauseAttenuationFloor &&
            lowCutHz == other.lowCutHz &&
            lowCutAttenuation == other.lowCutAttenuation &&
            preserveHighFrequencies == other.preserveHighFrequencies &&
            vocalClarityBoost == other.vocalClarityBoost &&
            studioMaxClean == other.studioMaxClean &&
            customNoisePrintPcm.contentEquals(other.customNoisePrintPcm) &&
            deHumTargetHz.contentEquals(other.deHumTargetHz)
    }

    override fun hashCode(): Int {
        var result = strength.hashCode()
        result = 31 * result + humRemovalStrength.hashCode()
        result = 31 * result + fanMotorRemovalStrength.hashCode()
        result = 31 * result + (spectralFloorOverride?.hashCode() ?: 0)
        result = 31 * result + pauseAttenuationFloor.hashCode()
        result = 31 * result + lowCutHz.hashCode()
        result = 31 * result + lowCutAttenuation.hashCode()
        result = 31 * result + preserveHighFrequencies.hashCode()
        result = 31 * result + vocalClarityBoost.hashCode()
        result = 31 * result + studioMaxClean.hashCode()
        result = 31 * result + (customNoisePrintPcm?.contentHashCode() ?: 0)
        result = 31 * result + deHumTargetHz.contentHashCode()
        return result
    }
}

/**
 * Compatibility facade forwarding all offline noise reduction calls to the new
 * iZotope RX–inspired [AdaptiveSpectralDenoiser] DSP engine.
 */
object StudioNoiseReducer {

    fun reduceNoise(
        samples: ShortArray,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        channels: Int = 1,
        config: StudioNoiseReductionConfig = StudioNoiseReductionConfig()
    ): ShortArray {
        val sr = sampleRate.coerceIn(8000, 96000)
        val s = config.strength.coerceIn(0f, 1f)
        if (samples.isEmpty() || s < 0.02f) return samples

        val baseParams = SpectralDenoiseParams.fromLegacyIntensity(
            intensity = if (config.studioMaxClean) 1.0f else s,
            voiceFocus = config.humRemovalStrength >= 0.25f || config.fanMotorRemovalStrength >= 0.25f || config.lowCutHz >= 50f
        )
        val fftSize = if (sr > 56_000) 4096 else 2048
        val profile = config.customNoisePrintPcm?.let { printPcm ->
            if (printPcm.size >= 256) AdaptiveSpectralDenoiser.captureNoiseProfile(printPcm, sr, fftSize) else null
        }

        val cleaned = AdaptiveSpectralDenoiser.processOffline(
            samples = samples,
            sampleRate = sr,
            channels = channels.coerceIn(1, 2),
            params = baseParams.copy(fftSize = fftSize),
            noiseProfile = profile
        )

        if (config.vocalClarityBoost > 0.05f && !config.studioMaxClean && baseParams.reductionDb < 14f) {
            return StudioAudioProcessor.applyTruePeakLimiter(
                AdaptiveSpectralDenoiser.applyVocalClarityPolish(
                    cleaned,
                    max(0f, config.vocalClarityBoost * 0.45f),
                    sr
                ),
                -0.8f
            )
        }
        return cleaned
    }
}
