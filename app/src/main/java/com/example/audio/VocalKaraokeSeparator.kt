package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class VocalKaraokeMode(
    val displayName: String,
    val shortDescription: String
) {
    EXTRACT_VOCALS(
        displayName = "Vocal Extractor",
        shortDescription = "Extract singing & lead vocals only (removes instruments, bass & drums)"
    ),
    KARAOKE_MAKER(
        displayName = "Karaoke Maker",
        shortDescription = "Remove lead vocals cleanly while keeping bass, beat & backing music"
    ),
    DUAL_STEMS(
        displayName = "Both Stems",
        shortDescription = "Generate both isolated Vocals and Karaoke Instrumental tracks together"
    )
}

enum class VocalKaraokeQuality(
    val displayName: String,
    val shortDescription: String,
    val separationStrength: Float,
    val bassPreserveHz: Float,
    val vocalPresenceBoost: Float
) {
    GENTLE(
        displayName = "Gentle",
        shortDescription = "Smooth natural separation with minimal artifacts and full warmth.",
        separationStrength = 0.62f,
        bassPreserveHz = 185f,
        vocalPresenceBoost = 0.25f
    ),
    BALANCED(
        displayName = "Balanced",
        shortDescription = "Studio vocal & karaoke split preserving punchy bass and crisp vocals.",
        separationStrength = 0.82f,
        bassPreserveHz = 165f,
        vocalPresenceBoost = 0.45f
    ),
    DEEP_ISOLATION(
        displayName = "Deep Isolation",
        shortDescription = "Maximum lead vocal extraction and deep center vocal cancellation for karaoke.",
        separationStrength = 0.96f,
        bassPreserveHz = 145f,
        vocalPresenceBoost = 0.65f
    )
}

data class StemSeparationResult(
    val vocals: ShortArray,
    val karaoke: ShortArray,
    val usedStereoSeparation: Boolean
)

/**
 * Pure-Kotlin Offline Vocal Extractor & Karaoke Maker DSP Engine.
 *
 * - Stereo songs: 1024-point Hann STFT Mid/Side phase-coherence separation with
 *   frequency-selective sub-bass preservation (< bassPreserveHz) so Karaoke keeps kick & bass
 *   while Vocals isolates center-panned singing formants cleanly.
 * - Mono tracks: STFT vocal-formant harmonic continuity masking to separate lead voice from
 *   sub-bass & upper accompaniment.
 */
object VocalKaraokeSeparator {

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = FFT_SIZE / 2
    private const val NUM_BINS = FFT_SIZE / 2 + 1

    private val sqrtHannWindow = FloatArray(FFT_SIZE) { i ->
        sin(PI * i.toDouble() / FFT_SIZE.toDouble()).toFloat()
    }

    fun hasStereoContent(left: ShortArray, right: ShortArray): Boolean {
        val len = min(left.size, right.size)
        if (len < 64) return false
        val step = max(1, len / 4096)
        var diffSum = 0L
        var sigSum = 0L
        var i = 0
        while (i < len) {
            diffSum += abs(left[i].toInt() - right[i].toInt())
            sigSum += abs(left[i].toInt()) + abs(right[i].toInt())
            i += step
        }
        return sigSum > 0L && (diffSum.toDouble() / sigSum.toDouble()) > 0.015
    }

    fun separateStereo(
        left: ShortArray,
        right: ShortArray,
        quality: VocalKaraokeQuality = VocalKaraokeQuality.BALANCED,
        separationStrength: Float = quality.separationStrength,
        bassPreserveHz: Float = quality.bassPreserveHz,
        vocalPresenceBoost: Float = quality.vocalPresenceBoost,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE
    ): StemSeparationResult {
        val len = min(left.size, right.size)
        if (len < 128) {
            val safe = left.copyOf(len)
            return StemSeparationResult(safe, safe.clone(), usedStereoSeparation = false)
        }
        if (!hasStereoContent(left, right)) {
            return separateMono(
                samples = left.copyOf(len),
                quality = quality,
                separationStrength = separationStrength,
                bassPreserveHz = bassPreserveHz,
                vocalPresenceBoost = vocalPresenceBoost,
                sampleRate = sampleRate
            )
        }

        val str = separationStrength.coerceIn(0.2f, 1.0f)
        val bassCut = bassPreserveHz.coerceIn(70f, 280f)
        val clarity = vocalPresenceBoost.coerceIn(0f, 1.0f)
        val sr = sampleRate.coerceIn(8000, 96000).toFloat()
        val binHz = sr / FFT_SIZE.toFloat()

        val pad = HOP_SIZE
        val paddedLen = len + 2 * pad
        val frameCount = (((paddedLen - FFT_SIZE + HOP_SIZE - 1) / HOP_SIZE) + 1).coerceAtLeast(1)
        val totalLen = (frameCount - 1) * HOP_SIZE + FFT_SIZE

        val inL = FloatArray(totalLen)
        val inR = FloatArray(totalLen)
        for (i in 0 until len) {
            inL[pad + i] = left[i].toFloat() / 32767f
            inR[pad + i] = right[i].toFloat() / 32767f
        }

        val vocalOutAccum = FloatArray(totalLen)
        val karaokeOutAccum = FloatArray(totalLen)
        val olaNorm = FloatArray(totalLen)

        val reL = FloatArray(FFT_SIZE)
        val imL = FloatArray(FFT_SIZE)
        val reR = FloatArray(FFT_SIZE)
        val imR = FloatArray(FFT_SIZE)
        val reV = FloatArray(FFT_SIZE)
        val imV = FloatArray(FFT_SIZE)
        val reK = FloatArray(FFT_SIZE)
        val imK = FloatArray(FFT_SIZE)

        val rawCenterScore = FloatArray(NUM_BINS)
        val smoothCenter = FloatArray(NUM_BINS)
        val prevVocalMask = FloatArray(NUM_BINS)
        val prevKaraokeMidKeep = FloatArray(NUM_BINS) { 1.0f }

        val vocalFloor = (0.14f - str * 0.125f).coerceIn(0.012f, 0.25f)
        val karaokeFloor = (0.28f - str * 0.26f).coerceIn(0.015f, 0.35f)
        val vocalGamma = 1.15f + str * 1.45f
        val sideRestoreGain = 1.0f + str * 0.38f

        for (f in 0 until frameCount) {
            val offset = f * HOP_SIZE
            for (k in 0 until FFT_SIZE) {
                val w = sqrtHannWindow[k]
                reL[k] = inL[offset + k] * w
                imL[k] = 0f
                reR[k] = inR[offset + k] * w
                imR[k] = 0f
            }
            VocalIsolationProcessor.fft(reL, imL, inverse = false)
            VocalIsolationProcessor.fft(reR, imR, inverse = false)

            for (b in 0 until NUM_BINS) {
                val midRe = 0.5f * (reL[b] + reR[b])
                val midIm = 0.5f * (imL[b] + imR[b])
                val sideRe = 0.5f * (reL[b] - reR[b])
                val sideIm = 0.5f * (imL[b] - imR[b])

                val midPow = midRe * midRe + midIm * midIm
                val sidePow = sideRe * sideRe + sideIm * sideIm
                val powL = reL[b] * reL[b] + imL[b] * imL[b]
                val powR = reR[b] * reR[b] + imR[b] * imR[b]
                val cross = reL[b] * reR[b] + imL[b] * imR[b]
                val phaseAlign = if (cross > 0f) {
                    (cross / (sqrt(powL * powR) + 1e-9f)).coerceIn(0f, 1f)
                } else 0f

                val centerRatio = midPow / (midPow + sidePow + 1e-9f)
                val excessMidRatio = ((midPow - 0.65f * sidePow) / (midPow + sidePow + 1e-9f)).coerceIn(0f, 1f)
                val stereoCoherence = (0.30f + 0.70f * phaseAlign)
                val combinedScore = (0.40f * centerRatio + 0.60f * excessMidRatio) * stereoCoherence * 1.38f
                rawCenterScore[b] = combinedScore.coerceIn(0f, 1f)
            }

            // 3-bin spectral smoothing prevents isolated tonal chirps
            for (b in 0 until NUM_BINS) {
                val leftBin = rawCenterScore[max(0, b - 1)]
                val centerBin = rawCenterScore[b]
                val rightBin = rawCenterScore[min(NUM_BINS - 1, b + 1)]
                smoothCenter[b] = 0.22f * leftBin + 0.56f * centerBin + 0.22f * rightBin
            }

            for (b in 0 until NUM_BINS) {
                val freq = b * binHz
                val midRe = 0.5f * (reL[b] + reR[b])
                val midIm = 0.5f * (imL[b] + imR[b])
                val sideRe = 0.5f * (reL[b] - reR[b])
                val sideIm = 0.5f * (imL[b] - imR[b])

                // 1) Vocal Extractor mask: isolate center vocal formants (105 Hz - 8200 Hz)
                val vocalBandWeight = when {
                    freq < 95f -> 0.02f
                    freq < 185f -> 0.02f + 0.98f * ((freq - 95f) / 90f)
                    freq <= 4600f -> 1.0f
                    freq <= 8200f -> 1.0f - 0.75f * ((freq - 4600f) / 3600f)
                    else -> 0.10f
                }
                val targetVocalGain = ((smoothCenter[b].pow(vocalGamma)) * vocalBandWeight)
                    .coerceIn(vocalFloor, 1.0f)
                val vAlpha = if (targetVocalGain > prevVocalMask[b]) 0.78f else 0.44f
                val vGain = prevVocalMask[b] + vAlpha * (targetVocalGain - prevVocalMask[b])
                prevVocalMask[b] = vGain

                val vRe = midRe * vGain
                val vIm = midIm * vGain

                // 2) Karaoke Maker mask: preserve sub-bass (< bassCut), strongly cancel center vocal band, boost stereo Side bed
                val suppressionBandWeight = when {
                    freq < bassCut -> 0.0f
                    freq < bassCut + 95f -> (freq - bassCut) / 95f
                    freq <= 6400f -> 1.0f
                    freq <= 9500f -> 1.0f - 0.60f * ((freq - 6400f) / 3100f)
                    else -> 0.25f
                }
                val aggressiveKaraokeFloor = (0.14f - str * 0.132f).coerceIn(0.008f, karaokeFloor)
                val vocalSuppression = (str * 1.15f * smoothCenter[b].pow(0.85f) * suppressionBandWeight).coerceIn(0f, 1f)
                val targetMidKeep = (1.0f - vocalSuppression).coerceIn(aggressiveKaraokeFloor, 1.0f)
                val kAlpha = if (targetMidKeep < prevKaraokeMidKeep[b]) 0.86f else 0.52f
                val kMidKeep = prevKaraokeMidKeep[b] + kAlpha * (targetMidKeep - prevKaraokeMidKeep[b])
                prevKaraokeMidKeep[b] = kMidKeep

                val sideWeight = if (freq < bassCut) 0.85f else sideRestoreGain
                val kRe = midRe * kMidKeep + sideRe * sideWeight
                val kIm = midIm * kMidKeep + sideIm * sideWeight

                reV[b] = vRe
                imV[b] = vIm
                reK[b] = kRe
                imK[b] = kIm
                if (b in 1 until (FFT_SIZE / 2)) {
                    val mirror = FFT_SIZE - b
                    reV[mirror] = vRe
                    imV[mirror] = -vIm
                    reK[mirror] = kRe
                    imK[mirror] = -kIm
                }
            }

            VocalIsolationProcessor.fft(reV, imV, inverse = true)
            VocalIsolationProcessor.fft(reK, imK, inverse = true)

            val offsetOut = f * HOP_SIZE
            for (k in 0 until FFT_SIZE) {
                val w = sqrtHannWindow[k]
                vocalOutAccum[offsetOut + k] += reV[k] * w
                karaokeOutAccum[offsetOut + k] += reK[k] * w
                olaNorm[offsetOut + k] += w * w
            }
        }

        val rawVocals = ShortArray(len)
        val rawKaraoke = ShortArray(len)
        for (i in 0 until len) {
            val norm = olaNorm[pad + i].coerceAtLeast(1e-4f)
            rawVocals[i] = ((vocalOutAccum[pad + i] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            rawKaraoke[i] = ((karaokeOutAccum[pad + i] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }

        val polishedVocals = polishExtractedVocals(rawVocals, clarity)
        val polishedKaraoke = StudioAudioProcessor.applyTruePeakLimiter(
            StudioAudioProcessor.removeDcOffset(rawKaraoke),
            -0.5f
        )

        return StemSeparationResult(
            vocals = polishedVocals,
            karaoke = polishedKaraoke,
            usedStereoSeparation = true
        )
    }

    fun separateMono(
        samples: ShortArray,
        quality: VocalKaraokeQuality = VocalKaraokeQuality.BALANCED,
        separationStrength: Float = quality.separationStrength,
        bassPreserveHz: Float = quality.bassPreserveHz,
        vocalPresenceBoost: Float = quality.vocalPresenceBoost,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE
    ): StemSeparationResult {
        if (samples.size < 128) {
            return StemSeparationResult(samples.clone(), samples.clone(), usedStereoSeparation = false)
        }

        val str = separationStrength.coerceIn(0.2f, 1.0f)
        val bassCut = bassPreserveHz.coerceIn(70f, 280f)
        val clarity = vocalPresenceBoost.coerceIn(0f, 1.0f)
        val sr = sampleRate.coerceIn(8000, 96000).toFloat()
        val binHz = sr / FFT_SIZE.toFloat()

        val pad = HOP_SIZE
        val paddedLen = samples.size + 2 * pad
        val frameCount = (((paddedLen - FFT_SIZE + HOP_SIZE - 1) / HOP_SIZE) + 1).coerceAtLeast(1)
        val totalLen = (frameCount - 1) * HOP_SIZE + FFT_SIZE

        val inputFloat = FloatArray(totalLen)
        for (i in samples.indices) {
            inputFloat[pad + i] = samples[i].toFloat() / 32767f
        }

        val frameMags = Array(frameCount) { FloatArray(NUM_BINS) }
        val frameRe = Array(frameCount) { FloatArray(FFT_SIZE) }
        val frameIm = Array(frameCount) { FloatArray(FFT_SIZE) }

        for (f in 0 until frameCount) {
            val offset = f * HOP_SIZE
            val re = frameRe[f]
            val im = frameIm[f]
            for (k in 0 until FFT_SIZE) {
                re[k] = inputFloat[offset + k] * sqrtHannWindow[k]
                im[k] = 0f
            }
            VocalIsolationProcessor.fft(re, im, inverse = false)
            val mags = frameMags[f]
            for (b in 0 until NUM_BINS) {
                mags[b] = sqrt(re[b] * re[b] + im[b] * im[b])
            }
        }

        val vocalOutAccum = FloatArray(totalLen)
        val karaokeOutAccum = FloatArray(totalLen)
        val olaNorm = FloatArray(totalLen)

        val reV = FloatArray(FFT_SIZE)
        val imV = FloatArray(FFT_SIZE)
        val reK = FloatArray(FFT_SIZE)
        val imK = FloatArray(FFT_SIZE)

        val vocalMinBin = max(1, (140f / binHz).toInt())
        val vocalMaxBin = min(NUM_BINS - 1, (4400f / binHz).toInt())
        val vocalFloor = (0.12f - str * 0.105f).coerceIn(0.012f, 0.22f)
        val karaokeFloor = (0.20f - str * 0.16f).coerceIn(0.035f, 0.35f)
        val prevVGain = FloatArray(NUM_BINS)
        val prevKGain = FloatArray(NUM_BINS) { 1.0f }

        for (f in 0 until frameCount) {
            val mags = frameMags[f]
            val re = frameRe[f]
            val im = frameIm[f]

            // 7-frame HPSS temporal harmonic support (~80 ms sustained voice vs brief transients)
            val fMin = max(0, f - 3)
            val fMax = min(frameCount - 1, f + 3)
            val fSpan = (fMax - fMin + 1).toFloat()

            for (b in 0 until NUM_BINS) {
                val freq = b * binHz
                var temporalSum = 0f
                for (fi in fMin..fMax) {
                    temporalSum += frameMags[fi][b]
                }
                val harmonicEnergy = temporalSum / fSpan

                // 9-bin local percussive/broadband spectral envelope (~387 Hz wide neighborhood)
                val bMin = max(0, b - 4)
                val bMax = min(NUM_BINS - 1, b + 4)
                var specSum = 0f
                for (bi in bMin..bMax) {
                    specSum += mags[bi]
                }
                val percussiveEnv = specSum / (bMax - bMin + 1).toFloat()

                // HPSS Wiener ratio normalized so broadband/flat signal scores near 0, tonal voice harmonics score near 1
                val h2 = harmonicEnergy * harmonicEnergy
                val p2 = percussiveEnv * percussiveEnv
                val rawHpss = (h2 / (h2 + 1.15f * p2 + 1e-9f)).coerceIn(0f, 1f)
                val tonalPeakScore = ((rawHpss - 0.36f) / 0.54f).coerceIn(0f, 1f)

                val formantBand = when {
                    freq < 105f -> 0.02f
                    freq < 210f -> 0.02f + 0.98f * ((freq - 105f) / 105f)
                    b in vocalMinBin..vocalMaxBin -> 1.0f
                    freq <= 7600f -> 1.0f - 0.78f * ((freq - 4400f) / 3200f)
                    else -> 0.05f
                }
                val vocalScore = (tonalPeakScore * formantBand).coerceIn(0f, 1f)
                val targetVGain = (vocalFloor + (1.0f - vocalFloor) * vocalScore.pow(0.75f + str * 0.9f))
                    .coerceIn(vocalFloor, 1.0f)
                val vAlpha = if (targetVGain > prevVGain[b]) 0.72f else 0.45f
                val vGain = prevVGain[b] + vAlpha * (targetVGain - prevVGain[b])
                prevVGain[b] = vGain

                val karaokeSuppressionBand = when {
                    freq < bassCut -> 0.0f
                    freq < bassCut + 95f -> (freq - bassCut) / 95f
                    freq <= 4600f -> 1.0f
                    freq <= 6800f -> 1.0f - 0.72f * ((freq - 4600f) / 2200f)
                    else -> 0.12f
                }
                // Suppress sustained lead vocal harmonics aggressively while preserving sub-bass & percussive transients
                val targetKGain = (1.0f - str * 1.08f * vocalScore * karaokeSuppressionBand)
                    .coerceIn(karaokeFloor, 1.0f)
                val kAlpha = if (targetKGain < prevKGain[b]) 0.80f else 0.50f
                val kGain = prevKGain[b] + kAlpha * (targetKGain - prevKGain[b])
                prevKGain[b] = kGain

                reV[b] = re[b] * vGain
                imV[b] = im[b] * vGain
                reK[b] = re[b] * kGain
                imK[b] = im[b] * kGain
                if (b in 1 until (FFT_SIZE / 2)) {
                    val mirror = FFT_SIZE - b
                    reV[mirror] = reV[b]
                    imV[mirror] = -imV[b]
                    reK[mirror] = reK[b]
                    imK[mirror] = -imK[b]
                }
            }

            VocalIsolationProcessor.fft(reV, imV, inverse = true)
            VocalIsolationProcessor.fft(reK, imK, inverse = true)

            val offset = f * HOP_SIZE
            for (k in 0 until FFT_SIZE) {
                val w = sqrtHannWindow[k]
                vocalOutAccum[offset + k] += reV[k] * w
                karaokeOutAccum[offset + k] += reK[k] * w
                olaNorm[offset + k] += w * w
            }
        }

        val rawVocals = ShortArray(samples.size)
        val rawKaraoke = ShortArray(samples.size)
        for (i in samples.indices) {
            val norm = olaNorm[pad + i].coerceAtLeast(1e-4f)
            rawVocals[i] = ((vocalOutAccum[pad + i] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            rawKaraoke[i] = ((karaokeOutAccum[pad + i] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }

        return StemSeparationResult(
            vocals = polishExtractedVocals(rawVocals, clarity),
            karaoke = StudioAudioProcessor.applyTruePeakLimiter(
                StudioAudioProcessor.removeDcOffset(rawKaraoke),
                -0.5f
            ),
            usedStereoSeparation = false
        )
    }

    private fun polishExtractedVocals(rawVocals: ShortArray, clarity: Float): ShortArray {
        val hp = StudioAudioProcessor.applyHighPassFilter(
            StudioAudioProcessor.removeDcOffset(rawVocals),
            85f
        )
        val eq = if (clarity > 0.04f) {
            StudioAudioProcessor.apply7BandEq(
                hp,
                b60 = -2.0f * clarity,
                b150 = 0f,
                b400 = -0.6f * clarity,
                b1k = 0.8f * clarity,
                b2_5k = 1.8f * clarity,
                b6k = 1.2f * clarity,
                b15k = 0f
            )
        } else {
            hp
        }
        return StudioAudioProcessor.applyTruePeakLimiter(eq, -0.8f)
    }
}
