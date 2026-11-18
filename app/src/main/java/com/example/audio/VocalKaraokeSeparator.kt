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
    val usedStereoSeparation: Boolean,
    val phaseCorrected: Boolean = false,
    val detectedCorrelation: Float = 1f,
    val appliedLagSamples: Int = 0
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

    fun hasStereoContentAligned(
        left: ShortArray,
        right: ShortArray,
        len: Int,
        lagSamples: Int,
        polarityInverted: Boolean
    ): Boolean {
        if (len < 64) return false
        val sign = if (polarityInverted) -1 else 1
        val step = max(1, len / 4096)
        var diffSum = 0L
        var sigSum = 0L
        var i = 0
        while (i < len) {
            val l = left[i].toInt()
            val r = right[(i + lagSamples).coerceIn(0, len - 1)].toInt() * sign
            diffSum += abs(l - r)
            sigSum += abs(l) + abs(r)
            i += step
        }
        return sigSum > 0L && (diffSum.toDouble() / sigSum.toDouble()) > 0.015
    }

    data class PhaseAlignmentInfo(
        val correlationBefore: Float,
        val polarityInverted: Boolean,
        val lagSamples: Int
    )

    internal fun analyzeAndAlignStereoPhase(
        left: ShortArray,
        right: ShortArray,
        len: Int,
        autoAlignPhase: Boolean,
        manualInvertRight: Boolean
    ): PhaseAlignmentInfo {
        val sampleCount = min(len, 16384)
        val stride = max(1, len / sampleCount)
        var sumLL = 0.0
        var sumRR = 0.0
        var sumLR = 0.0
        var i = 0
        while (i < len) {
            val l = left[i].toDouble()
            val r = right[i].toDouble()
            sumLL += l * l
            sumRR += r * r
            sumLR += l * r
            i += stride
        }
        val baseCorr = if (sumLL > 1e-6 && sumRR > 1e-6) {
            (sumLR / sqrt(sumLL * sumRR)).toFloat().coerceIn(-1f, 1f)
        } else 1f

        var bestLag = 0
        var polarityFlipped = manualInvertRight

        if (autoAlignPhase && !manualInvertRight) {
            // Check sub-millisecond stereo comb-filtering lag (-8..+8 samples) and polarity flip
            val probeStart = min(16, len / 4)
            val probeEnd = min(len - 16, probeStart + 8192)
            if (probeEnd > probeStart + 64) {
                var bestPosCorr = baseCorr
                var bestPosLag = 0
                var bestNegCorr = -baseCorr
                var bestNegLag = 0
                for (lag in -8..8) {
                    var sLL = 0.0
                    var sRR = 0.0
                    var sLR = 0.0
                    var idx = probeStart
                    while (idx < probeEnd) {
                        val l = left[idx].toDouble()
                        val r = right[(idx + lag).coerceIn(0, len - 1)].toDouble()
                        sLL += l * l
                        sRR += r * r
                        sLR += l * r
                        idx += 2
                    }
                    if (sLL > 1e-6 && sRR > 1e-6) {
                        val c = (sLR / sqrt(sLL * sRR)).toFloat()
                        if (c > bestPosCorr) {
                            bestPosCorr = c
                            bestPosLag = lag
                        }
                        if (-c > bestNegCorr) {
                            bestNegCorr = -c
                            bestNegLag = lag
                        }
                    }
                }
                if (bestPosCorr > baseCorr + 0.16f && bestPosLag != 0 && bestPosCorr >= bestNegCorr) {
                    bestLag = bestPosLag
                    polarityFlipped = false
                } else if ((bestNegCorr > baseCorr + 0.18f || baseCorr < -0.25f) && bestNegCorr > bestPosCorr && bestNegCorr > 0.25f) {
                    polarityFlipped = true
                    bestLag = if (bestNegLag != 0 && bestNegCorr > -baseCorr + 0.12f) bestNegLag else 0
                }
            } else if (baseCorr < -0.25f) {
                bestLag = 0
                polarityFlipped = true
            }
        }

        return PhaseAlignmentInfo(
            correlationBefore = baseCorr,
            polarityInverted = polarityFlipped,
            lagSamples = bestLag
        )
    }

    fun separateStereo(
        left: ShortArray,
        right: ShortArray,
        quality: VocalKaraokeQuality = VocalKaraokeQuality.BALANCED,
        separationStrength: Float = quality.separationStrength,
        bassPreserveHz: Float = quality.bassPreserveHz,
        vocalPresenceBoost: Float = quality.vocalPresenceBoost,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        autoAlignPhase: Boolean = true,
        manualInvertRight: Boolean = false,
        studioMasteringPolish: Boolean = true
    ): StemSeparationResult {
        val len = min(left.size, right.size)
        if (len < 128) {
            val safe = left.copyOf(len)
            return StemSeparationResult(safe, safe.clone(), usedStereoSeparation = false)
        }
        if (!hasStereoContent(left, right) && !manualInvertRight) {
            return separateMono(
                samples = if (left.size == len) left else left.copyOf(len),
                quality = quality,
                separationStrength = separationStrength,
                bassPreserveHz = bassPreserveHz,
                vocalPresenceBoost = vocalPresenceBoost,
                sampleRate = sampleRate,
                studioMasteringPolish = studioMasteringPolish
            )
        }

        val phaseInfo = analyzeAndAlignStereoPhase(
            left = left,
            right = right,
            len = len,
            autoAlignPhase = autoAlignPhase,
            manualInvertRight = manualInvertRight
        )

        // If polarity/delay correction turns an inverted dual-mono signal back into clean mono, run Mono HPSS
        if (!hasStereoContentAligned(left, right, len, phaseInfo.lagSamples, phaseInfo.polarityInverted)) {
            val monoResult = separateMono(
                samples = if (left.size == len) left else left.copyOf(len),
                quality = quality,
                separationStrength = separationStrength,
                bassPreserveHz = bassPreserveHz,
                vocalPresenceBoost = vocalPresenceBoost,
                sampleRate = sampleRate,
                studioMasteringPolish = studioMasteringPolish
            )
            return monoResult.copy(
                phaseCorrected = phaseInfo.polarityInverted || phaseInfo.lagSamples != 0,
                detectedCorrelation = phaseInfo.correlationBefore,
                appliedLagSamples = phaseInfo.lagSamples
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

        val lag = phaseInfo.lagSamples
        val rightSign = if (phaseInfo.polarityInverted) -1f else 1f

        // 2-hop sliding OLA accumulators (12 KB total instead of full-track FloatArrays)
        val vocalOutAccum = FloatArray(FFT_SIZE)
        val karaokeOutAccum = FloatArray(FFT_SIZE)
        val olaNorm = FloatArray(FFT_SIZE)

        val rawVocals = ShortArray(len)
        val rawKaraoke = ShortArray(len)

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
                val sIdx = offset + k - pad
                if (sIdx in 0 until len) {
                    reL[k] = (left[sIdx].toFloat() / 32767f) * w
                    val rIdx = (sIdx + lag).coerceIn(0, len - 1)
                    reR[k] = ((right[rIdx].toFloat() / 32767f) * rightSign) * w
                } else {
                    reL[k] = 0f
                    reR[k] = 0f
                }
                imL[k] = 0f
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

            // 5-bin spectral smoothing eliminates isolated tonal comb-filtering & ghost artifacts
            for (b in 0 until NUM_BINS) {
                val bM2 = rawCenterScore[max(0, b - 2)]
                val bM1 = rawCenterScore[max(0, b - 1)]
                val bC = rawCenterScore[b]
                val bP1 = rawCenterScore[min(NUM_BINS - 1, b + 1)]
                val bP2 = rawCenterScore[min(NUM_BINS - 1, b + 2)]
                smoothCenter[b] = 0.10f * bM2 + 0.22f * bM1 + 0.36f * bC + 0.22f * bP1 + 0.10f * bP2
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

                // 2) Karaoke Maker mask: mono-anchor sub-bass (< bassCut), cancel center vocals cleanly, widen stereo bed
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

                // Low-end phase integrity: anchor Side energy near mono below bassCut; widen Side above 280 Hz
                val sideWeight = when {
                    freq < bassCut -> 0.12f
                    freq < 280f -> 0.12f + (sideRestoreGain - 0.12f) * ((freq - bassCut) / (280f - bassCut).coerceAtLeast(1f))
                    else -> sideRestoreGain
                }
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

            for (k in 0 until FFT_SIZE) {
                val w = sqrtHannWindow[k]
                vocalOutAccum[k] += reV[k] * w
                karaokeOutAccum[k] += reK[k] * w
                olaNorm[k] += w * w
            }

            for (k in 0 until HOP_SIZE) {
                val sigIdx = offset + k - pad
                if (sigIdx in 0 until len) {
                    val norm = olaNorm[k].coerceAtLeast(1e-4f)
                    rawVocals[sigIdx] = ((vocalOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    rawKaraoke[sigIdx] = ((karaokeOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            System.arraycopy(vocalOutAccum, HOP_SIZE, vocalOutAccum, 0, HOP_SIZE)
            System.arraycopy(karaokeOutAccum, HOP_SIZE, karaokeOutAccum, 0, HOP_SIZE)
            System.arraycopy(olaNorm, HOP_SIZE, olaNorm, 0, HOP_SIZE)
            java.util.Arrays.fill(vocalOutAccum, HOP_SIZE, FFT_SIZE, 0f)
            java.util.Arrays.fill(karaokeOutAccum, HOP_SIZE, FFT_SIZE, 0f)
            java.util.Arrays.fill(olaNorm, HOP_SIZE, FFT_SIZE, 0f)
        }
        for (k in 0 until HOP_SIZE) {
            val sigIdx = frameCount * HOP_SIZE + k - pad
            if (sigIdx in 0 until len) {
                val norm = olaNorm[k].coerceAtLeast(1e-4f)
                rawVocals[sigIdx] = ((vocalOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                rawKaraoke[sigIdx] = ((karaokeOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            }
        }

        val polishedVocals = polishExtractedVocals(
            rawVocals = rawVocals,
            clarity = clarity,
            cleanDryVocals = studioMasteringPolish,
            sampleRate = sampleRate
        )
        val polishedKaraoke = polishKaraokeInstrumental(
            rawKaraoke = rawKaraoke,
            studioMasteringPolish = studioMasteringPolish
        )

        return StemSeparationResult(
            vocals = polishedVocals,
            karaoke = polishedKaraoke,
            usedStereoSeparation = true,
            phaseCorrected = phaseInfo.polarityInverted || phaseInfo.lagSamples != 0,
            detectedCorrelation = phaseInfo.correlationBefore,
            appliedLagSamples = phaseInfo.lagSamples
        )
    }

    fun separateMono(
        samples: ShortArray,
        quality: VocalKaraokeQuality = VocalKaraokeQuality.BALANCED,
        separationStrength: Float = quality.separationStrength,
        bassPreserveHz: Float = quality.bassPreserveHz,
        vocalPresenceBoost: Float = quality.vocalPresenceBoost,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        studioMasteringPolish: Boolean = true
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

        // O(1) STFT heap: 7-slot circular ring buffer (~84 KB regardless of song length)
        val ringSize = 7
        val ringMags = Array(ringSize) { FloatArray(NUM_BINS) }
        val ringRe = Array(ringSize) { FloatArray(FFT_SIZE) }
        val ringIm = Array(ringSize) { FloatArray(FFT_SIZE) }

        // 2-hop sliding OLA accumulators (12 KB total instead of full-track FloatArrays)
        val vocalOutAccum = FloatArray(FFT_SIZE)
        val karaokeOutAccum = FloatArray(FFT_SIZE)
        val olaNorm = FloatArray(FFT_SIZE)

        val rawVocals = ShortArray(samples.size)
        val rawKaraoke = ShortArray(samples.size)

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

        val totalSteps = frameCount + 3
        for (t in 0 until totalSteps) {
            if (t < frameCount) {
                val slot = t % ringSize
                val offset = t * HOP_SIZE
                val re = ringRe[slot]
                val im = ringIm[slot]
                for (k in 0 until FFT_SIZE) {
                    val sIdx = offset + k - pad
                    val sample = if (sIdx in samples.indices) samples[sIdx].toFloat() / 32767f else 0f
                    re[k] = sample * sqrtHannWindow[k]
                    im[k] = 0f
                }
                VocalIsolationProcessor.fft(re, im, inverse = false)
                val mags = ringMags[slot]
                for (b in 0 until NUM_BINS) {
                    mags[b] = sqrt(re[b] * re[b] + im[b] * im[b])
                }
            }

            val f = t - 3
            if (f < 0 || f >= frameCount) continue

            val curSlot = f % ringSize
            val mags = ringMags[curSlot]
            val re = ringRe[curSlot]
            val im = ringIm[curSlot]

            // 7-frame HPSS temporal harmonic support (~80 ms sustained voice vs brief transients)
            val fMin = max(0, f - 3)
            val fMax = min(frameCount - 1, f + 3)
            val fSpan = (fMax - fMin + 1).toFloat()

            for (b in 0 until NUM_BINS) {
                val freq = b * binHz
                var temporalSum = 0f
                for (fi in fMin..fMax) {
                    temporalSum += ringMags[fi % ringSize][b]
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
                vocalOutAccum[k] += reV[k] * w
                karaokeOutAccum[k] += reK[k] * w
                olaNorm[k] += w * w
            }

            for (k in 0 until HOP_SIZE) {
                val sigIdx = offset + k - pad
                if (sigIdx in rawVocals.indices) {
                    val norm = olaNorm[k].coerceAtLeast(1e-4f)
                    rawVocals[sigIdx] = ((vocalOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    rawKaraoke[sigIdx] = ((karaokeOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            System.arraycopy(vocalOutAccum, HOP_SIZE, vocalOutAccum, 0, HOP_SIZE)
            System.arraycopy(karaokeOutAccum, HOP_SIZE, karaokeOutAccum, 0, HOP_SIZE)
            System.arraycopy(olaNorm, HOP_SIZE, olaNorm, 0, HOP_SIZE)
            java.util.Arrays.fill(vocalOutAccum, HOP_SIZE, FFT_SIZE, 0f)
            java.util.Arrays.fill(karaokeOutAccum, HOP_SIZE, FFT_SIZE, 0f)
            java.util.Arrays.fill(olaNorm, HOP_SIZE, FFT_SIZE, 0f)
        }
        for (k in 0 until HOP_SIZE) {
            val sigIdx = frameCount * HOP_SIZE + k - pad
            if (sigIdx in rawVocals.indices) {
                val norm = olaNorm[k].coerceAtLeast(1e-4f)
                rawVocals[sigIdx] = ((vocalOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                rawKaraoke[sigIdx] = ((karaokeOutAccum[k] / norm) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            }
        }

        return StemSeparationResult(
            vocals = polishExtractedVocals(
                rawVocals = rawVocals,
                clarity = clarity,
                cleanDryVocals = studioMasteringPolish,
                sampleRate = sampleRate
            ),
            karaoke = polishKaraokeInstrumental(
                rawKaraoke = rawKaraoke,
                studioMasteringPolish = studioMasteringPolish
            ),
            usedStereoSeparation = false
        )
    }

    private fun polishExtractedVocals(
        rawVocals: ShortArray,
        clarity: Float,
        cleanDryVocals: Boolean,
        sampleRate: Int
    ): ShortArray {
        var work = StudioAudioProcessor.removeDcOffset(rawVocals)
        work = StudioAudioProcessor.applyHighPassFilter(work, 88f)
        // Only apply spectral hum/reverb cleanup when genuine ambient gaps exist so steady sung notes stay intact
        if (cleanDryVocals && work.size >= 2048 && VocalIsolationProcessor.hasDistinctQuietFloor(work)) {
            work = VocalIsolationProcessor.isolateSpokenAudio(
                samples = work,
                subtractionStrength = 0.42f,
                humRemovalStrength = 0.72f,
                vocalClarityBoost = 0.15f,
                spectralFloorOverride = 0.18f,
                vocalBandEmphasisOverride = 0.20f,
                pauseGateFloorOverride = 0.28f,
                highPassHz = 85f,
                sampleRate = sampleRate
            )
        }

        // Corrective EQ: trim boxy low-mids (250-400 Hz), tame harsh 2-4 kHz resonance & boost clear articulation
        if (cleanDryVocals || clarity > 0.04f) {
            work = StudioAudioProcessor.apply7BandEq(
                work,
                b60 = if (cleanDryVocals) -3.0f else -2.0f * clarity,
                b150 = if (cleanDryVocals) -0.8f else 0f,
                b400 = if (cleanDryVocals) -1.5f else -0.6f * clarity,
                b1k = 0.8f * clarity,
                b2_5k = (if (cleanDryVocals) -0.5f else 0f) + 1.5f * clarity,
                b6k = 1.1f * clarity,
                b15k = 0f
            )
        }

        if (cleanDryVocals) {
            work = StudioAudioProcessor.applyCompressor(
                input = work,
                thresholdDb = -16.0f,
                ratio = 2.4f,
                makeupGainDb = 0.8f,
                peakLimiter = true
            )
        }

        return StudioAudioProcessor.applyTruePeakLimiter(work, -0.8f)
    }

    private fun polishKaraokeInstrumental(
        rawKaraoke: ShortArray,
        studioMasteringPolish: Boolean
    ): ShortArray {
        var work = StudioAudioProcessor.removeDcOffset(rawKaraoke)
        if (!studioMasteringPolish || work.size < 256) {
            return StudioAudioProcessor.applyTruePeakLimiter(work, -0.5f)
        }
        // Corrective Instrumental EQ without boosting suppressed mid vocal residuals:
        // keep punchy bass/kick, clean 250-400 Hz boxiness, and dip 2-4 kHz residual harshness
        work = StudioAudioProcessor.apply7BandEq(
            work,
            b60 = 1.0f,
            b150 = 0.4f,
            b400 = -1.6f,
            b1k = 0.0f,
            b2_5k = -1.8f,
            b6k = 0.6f,
            b15k = 0.3f
        )
        work = StudioAudioProcessor.applyCompressor(
            input = work,
            thresholdDb = -10.0f,
            ratio = 1.8f,
            makeupGainDb = 0.0f,
            peakLimiter = true
        )
        return StudioAudioProcessor.applyTruePeakLimiter(work, -0.5f)
    }
}
